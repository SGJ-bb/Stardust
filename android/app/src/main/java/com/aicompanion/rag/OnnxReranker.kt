package com.aicompanion.rag

import android.content.Context
import com.aicompanion.util.AppLogger
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.LongBuffer
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * ONNX重排序模型
 *
 * 使用bge-reranker-base对RAG检索结果进行二次排序。
 * Cross-Encoder架构,将query和document拼接后输入模型,
 * 输出相关性分数,比Bi-Encoder的余弦相似度更准确。
 *
 * 模型: bge-reranker-base (INT8量化版,约266MB)
 * 架构: BERT Cross-Encoder
 * 输入: [CLS] query [SEP] document [SEP]
 * 输出: relevance score (标量)
 */
class OnnxReranker(private val context: Context) {

    companion object {
        private const val TAG = "OnnxReranker"
        private const val MODEL_FILENAME = "model.onnx"
        private const val VOCAB_FILENAME = "vocab.txt"
        private const val ASSETS_MODEL_DIR = "models/bge-reranker-base"
        private const val MAX_SEQ_LEN = 512

        // 重排序默认参数
        const val DEFAULT_TOP_K_BEFORE_RERANK = 10  // 重排序前取的候选数量
        const val DEFAULT_TOP_K_AFTER_RERANK = 3    // 重排序后返回的结果数量
    }

    private val inferenceLock = ReentrantLock()
    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var tokenizer: XLMRobertaTokenizer? = null
    @Volatile private var isInitialized = false
    @Volatile private var initFailed = false

    // 模型文件目录(使用cacheDir存放从assets复制的模型)
    private val modelDir: File
        get() = File(context.cacheDir, "onnx_reranker")

    private val modelFile: File
        get() = File(modelDir, MODEL_FILENAME)

    private val vocabFile: File
        get() = File(modelDir, VOCAB_FILENAME)

    /**
     * 检查模型是否就绪
     */
    fun isModelReady(): Boolean {
        if (modelFile.exists() && modelFile.length() > 1_000_000L && vocabFile.exists()) {
            return true
        }
        return isModelInAssets()
    }

    private fun isModelInAssets(): Boolean {
        return try {
            val files = context.assets.list(ASSETS_MODEL_DIR) ?: emptyArray()
            files.contains(MODEL_FILENAME) && files.contains(VOCAB_FILENAME)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 从assets复制模型文件到cacheDir
     */
    private fun ensureModelFiles(): Boolean {
        if (modelFile.exists() && modelFile.length() > 1_000_000L && vocabFile.exists()) {
            return true
        }

        return try {
            if (!isModelInAssets()) {
                AppLogger.e(TAG, "Reranker model not found in assets or cache")
                return false
            }

            modelDir.mkdirs()
            val assetFiles = context.assets.list(ASSETS_MODEL_DIR) ?: emptyArray()

            for (fileName in assetFiles) {
                val assetPath = "$ASSETS_MODEL_DIR/$fileName"
                val outFile = File(modelDir, fileName)

                if (outFile.exists()) {
                    val assetSize = getAssetSize(assetPath)
                    if (outFile.length() == assetSize) continue
                }

                context.assets.open(assetPath).use { input ->
                    outFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                AppLogger.d(TAG, "Copied reranker from assets: $fileName (${outFile.length() / 1024}KB)")
            }

            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to copy reranker model: ${e.message}")
            false
        }
    }

    private fun getAssetSize(assetPath: String): Long {
        return try {
            context.assets.openFd(assetPath).use { it.length }
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * 初始化模型
     */
    @Synchronized
    fun initialize(): Boolean {
        if (isInitialized) return true
        if (initFailed && isModelReady()) {
            initFailed = false
        }
        if (initFailed) return false

        try {
            if (!ensureModelFiles()) {
                AppLogger.e(TAG, "Reranker model files not available")
                initFailed = true
                return false
            }

            // 使用XLM-RoBERTa tokenizer(从assets加载tokenizer.json)
            tokenizer = XLMRobertaTokenizer(context)
            if (!tokenizer!!.load()) {
                AppLogger.e(TAG, "Failed to load XLM-RoBERTa tokenizer")
                initFailed = true
                return false
            }
            AppLogger.i(TAG, "Reranker tokenizer loaded")

            env = OrtEnvironment.getEnvironment()

            val sessionOptions = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
                setInterOpNumThreads(1)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }

            try {
                session = env?.createSession(modelFile.absolutePath, sessionOptions)
            } finally {
                sessionOptions.close()
            }

            isInitialized = true
            AppLogger.i(TAG, "Reranker model initialized successfully (${modelFile.length() / 1024 / 1024}MB)")
            return true
        } catch (e: OutOfMemoryError) {
            AppLogger.e(TAG, "OOM initializing reranker: ${e.message}")
            initFailed = true
            return false
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to init reranker: ${e.message}")
            initFailed = true
            return false
        }
    }

    /**
     * 对单个query-document对计算相关性分数
     *
     * @param query 查询文本
     * @param document 文档文本
     * @return 相关性分数(越高越相关)
     */
    fun score(query: String, document: String): Float {
        if (!isInitialized || session == null || tokenizer == null) {
            AppLogger.w(TAG, "Reranker not initialized, returning 0")
            return 0f
        }

        return inferenceLock.withLock {
            try {
                // Cross-Encoder: <s> query </s></s> document </s>
                val tokens = tokenizer!!.encodePair(query, document, MAX_SEQ_LEN)

                val inputIds = tokens.inputIds
                val attentionMask = tokens.attentionMask
                val tokenTypeIds = tokens.tokenTypeIds

                val shape = longArrayOf(1, inputIds.size.toLong())

                val inputIdsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), shape)
                val attentionMaskTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(attentionMask), shape)
                val tokenTypeIdsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(tokenTypeIds), shape)

                // XLM-RoBERTa可能不接受token_type_ids,尝试两种输入方式
                val output = try {
                    // 先尝试包含token_type_ids
                    session!!.run(mapOf(
                        "input_ids" to inputIdsTensor,
                        "attention_mask" to attentionMaskTensor,
                        "token_type_ids" to tokenTypeIdsTensor
                    ))
                } catch (e: Exception) {
                    // 如果失败,只使用input_ids和attention_mask
                    inputIdsTensor.close()
                    attentionMaskTensor.close()
                    tokenTypeIdsTensor.close()

                    val retryInputIds = OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), shape)
                    val retryAttentionMask = OnnxTensor.createTensor(env, LongBuffer.wrap(attentionMask), shape)
                    try {
                        val retryResult = session!!.run(mapOf(
                            "input_ids" to retryInputIds,
                            "attention_mask" to retryAttentionMask
                        ))
                        retryResult
                    } finally {
                        retryInputIds.close()
                        retryAttentionMask.close()
                    }
                }

                val score = try {
                    // 输出形状: [1, 1] 或 [1]
                    val rawOutput = output[0].value
                    when (rawOutput) {
                        is FloatArray -> rawOutput[0]
                        is Array<*> -> (rawOutput[0] as FloatArray)[0]
                        else -> {
                            AppLogger.w(TAG, "Unexpected reranker output type: ${rawOutput?.javaClass}")
                            0f
                        }
                    }
                } finally {
                    output.close()
                    // tensor已在try-catch中处理close,这里安全关闭第一次创建的
                    try { inputIdsTensor.close() } catch (_: Exception) {}
                    try { attentionMaskTensor.close() } catch (_: Exception) {}
                    try { tokenTypeIdsTensor.close() } catch (_: Exception) {}
                }

                score
            } catch (e: Exception) {
                AppLogger.e(TAG, "Reranker scoring error: ${e.message}")
                0f
            }
        }
    }

    /**
     * 对多个query-document对进行批量评分
     *
     * @param query 查询文本
     * @param documents 文档列表
     * @return (文档索引, 分数) 列表,按分数降序排列
     */
    fun rerank(query: String, documents: List<String>): List<Pair<Int, Float>> {
        if (documents.isEmpty()) return emptyList()

        if (!isInitialized) {
            AppLogger.w(TAG, "Reranker not initialized, returning original order")
            return documents.indices.map { it to 0f }
        }

        val scores = mutableListOf<Pair<Int, Float>>()

        for ((index, doc) in documents.withIndex()) {
            val score = score(query, doc)
            scores.add(index to score)
        }

        // 按分数降序排列
        return scores.sortedByDescending { it.second }
    }

    /**
     * 对RAG检索结果进行重排序
     *
     * @param query 用户查询
     * @param candidates 候选结果列表(文本内容)
     * @param topK 返回的前K个结果
     * @return 重排序后的结果索引列表(按相关性降序)
     */
    fun rerankResults(
        query: String,
        candidates: List<String>,
        topK: Int = DEFAULT_TOP_K_AFTER_RERANK
    ): List<Int> {
        if (candidates.isEmpty()) return emptyList()

        val ranked = rerank(query, candidates)
        return ranked.take(topK).map { it.first }
    }

    /**
     * 释放资源
     */
    fun release() {
        inferenceLock.withLock {
            try {
                session?.close()
                session = null
                tokenizer = null
                isInitialized = false
                AppLogger.i(TAG, "Reranker released")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error releasing reranker: ${e.message}")
            }
        }
    }

    /**
     * 检查是否已初始化
     */
    fun isReady(): Boolean = isInitialized && !initFailed
}