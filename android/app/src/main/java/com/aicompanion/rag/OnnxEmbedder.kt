package com.aicompanion.rag

import android.content.Context
import com.aicompanion.util.AppLogger
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.LongBuffer
import kotlin.math.sqrt

class OnnxEmbedder(private val context: Context) : RagEmbedder {

    companion object {
        private const val TAG = "OnnxEmbedder"
        private const val MODEL_FILENAME = "model.onnx"
        private const val VOCAB_FILENAME = "vocab.txt"
        private const val ASSETS_MODEL_DIR = "models/bge-small-zh-v1.5"
        const val EMBEDDING_DIM = 512
        private const val MAX_SEQ_LEN = 512
    }

    private val inferenceLock = Any()
    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var tokenizer: BertTokenizer? = null
    @Volatile private var isInitialized = false
    @Volatile private var initFailed = false

    // 使用cacheDir存放从assets复制的模型文件
    val modelDir: File
        get() = File(context.cacheDir, "onnx_model")

    val modelFile: File
        get() = File(modelDir, MODEL_FILENAME)

    val vocabFile: File
        get() = File(modelDir, VOCAB_FILENAME)

    fun isModelReady(): Boolean {
        // 检查cacheDir或assets中是否有模型
        if (modelFile.exists() && modelFile.length() > 1_000_000L && vocabFile.exists()) {
            return true
        }
        // 检查assets中是否有模型
        return isModelInAssets()
    }

    /**
     * 检查assets目录中是否有模型文件
     */
    private fun isModelInAssets(): Boolean {
        return try {
            val files = context.assets.list(ASSETS_MODEL_DIR) ?: emptyArray()
            files.contains(MODEL_FILENAME) && files.contains(VOCAB_FILENAME)
        } catch (e: Exception) {
            false
        }
    }

    fun getModelSizeMB(): Long {
        return if (modelFile.exists()) modelFile.length() / (1024 * 1024) else 0L
    }

    /**
     * 从assets复制模型文件到cacheDir(如果cacheDir中没有)
     */
    private fun ensureModelFiles(): Boolean {
        // 如果cacheDir已有模型文件,直接使用
        if (modelFile.exists() && modelFile.length() > 1_000_000L && vocabFile.exists()) {
            return true
        }

        // 从assets复制
        return try {
            if (!isModelInAssets()) {
                AppLogger.e(TAG, "Model not found in assets or cache")
                return false
            }

            modelDir.mkdirs()
            val assetsFiles = context.assets.list(ASSETS_MODEL_DIR) ?: emptyArray()

            for (fileName in assetsFiles) {
                val assetPath = "$ASSETS_MODEL_DIR/$fileName"
                val outFile = File(modelDir, fileName)

                // 跳过已存在且大小相同的文件
                if (outFile.exists()) {
                    val assetSize = getAssetSize(assetPath)
                    if (outFile.length() == assetSize) continue
                }

                context.assets.open(assetPath).use { input ->
                    outFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                AppLogger.d(TAG, "Copied from assets: $fileName (${outFile.length() / 1024}KB)")
            }

            AppLogger.i(TAG, "Model files copied from assets to cache")
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to copy model from assets: ${e.message}")
            false
        }
    }

    /**
     * 获取assets文件大小
     */
    private fun getAssetSize(assetPath: String): Long {
        return try {
            context.assets.openFd(assetPath).use { fd ->
                fd.length
            }
        } catch (e: Exception) {
            0L
        }
    }

    @Synchronized
    fun initialize(): Boolean {
        if (isInitialized) return true
        // 如果之前失败，但模型文件现在已就绪，允许重试
        if (initFailed && isModelReady()) {
            initFailed = false
        }
        if (initFailed) return false

        try {
            // 确保模型文件存在(从assets复制)
            if (!ensureModelFiles()) {
                AppLogger.e(TAG, "Model files not available")
                initFailed = true
                return false
            }

            // 加载分词器
            tokenizer = vocabFile.inputStream().use { BertTokenizer(it) }
            AppLogger.i(TAG, "Tokenizer loaded, vocab size check passed")

            // 创建 ONNX Runtime 环境
            env = OrtEnvironment.getEnvironment()

            // 创建 Session
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
            AppLogger.i(TAG, "ONNX model loaded successfully, size=${getModelSizeMB()}MB")
            return true
        } catch (e: OutOfMemoryError) {
            AppLogger.e(TAG, "OOM loading ONNX model: ${e.message}")
            initFailed = true
            release()
            return false
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to initialize ONNX embedder: ${e.message}")
            initFailed = true
            release()
            return false
        }
    }

    @Synchronized
    fun release() {
        // 获取 inferenceLock 确保没有推理正在进行时才关闭 session
        synchronized(inferenceLock) {
            try {
                session?.close()
                session = null
                // 不关闭 env，它是共享的单例
                tokenizer = null
                isInitialized = false
                initFailed = false
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error releasing ONNX resources: ${e.message}")
            }
        }
    }

    override fun dimension(): Int = EMBEDDING_DIM

    override suspend fun embed(texts: List<String>): List<FloatArray> {
        if (!isInitialized && !initialize()) {
            AppLogger.e(TAG, "Not initialized, returning empty list")
            return emptyList()
        }
        return texts.map { embedSingleInternal(it) }
    }

    override suspend fun embedSingle(text: String): FloatArray {
        if (!isInitialized && !initialize()) {
            AppLogger.e(TAG, "Not initialized for embedSingle")
            return FloatArray(0)
        }
        return embedSingleInternal(text)
    }

    fun embedSingleSync(text: String): FloatArray {
        if (!isInitialized && !initialize()) {
            AppLogger.e(TAG, "Not initialized for embedSingleSync")
            return FloatArray(0)
        }
        return embedSingleInternal(text)
    }

    /**
     * 推理核心方法。使用 inferenceLock 保护，防止 release() 关闭正在使用的 session。
     * 所有错误路径统一返回 FloatArray(0)，与下游 isEmpty() 检查一致。
     */
    private fun embedSingleInternal(text: String): FloatArray {
        synchronized(inferenceLock) {
            val tok = tokenizer ?: return FloatArray(0)
            val sess = session ?: return FloatArray(0)
            val ortEnv = env ?: return FloatArray(0)

            var inputIdsTensor: OnnxTensor? = null
            var attentionMaskTensor: OnnxTensor? = null
            var tokenTypeIdsTensor: OnnxTensor? = null
            var output: OrtSession.Result? = null

            try {
                val encoded = tok.encode(text)
                val seqLen = encoded.inputIds.size

                val inputIdsBuffer = LongBuffer.wrap(encoded.inputIds)
                val attentionMaskBuffer = LongBuffer.wrap(encoded.attentionMask)
                val tokenTypeIdsBuffer = LongBuffer.wrap(encoded.tokenTypeIds)

                inputIdsTensor = OnnxTensor.createTensor(ortEnv, inputIdsBuffer, longArrayOf(1, seqLen.toLong()))
                attentionMaskTensor = OnnxTensor.createTensor(ortEnv, attentionMaskBuffer, longArrayOf(1, seqLen.toLong()))
                tokenTypeIdsTensor = OnnxTensor.createTensor(ortEnv, tokenTypeIdsBuffer, longArrayOf(1, seqLen.toLong()))

                val inputs = mapOf(
                    "input_ids" to inputIdsTensor,
                    "attention_mask" to attentionMaskTensor,
                    "token_type_ids" to tokenTypeIdsTensor
                )

                output = sess.run(inputs)
                @Suppress("UNCHECKED_CAST")
                val lastHiddenState = output[0].value as Array<FloatArray>

                // CLS pooling: 取第一个 token ([CLS]) 的向量
                val clsVector = lastHiddenState[0]

                // L2 归一化
                return l2Normalize(clsVector)
            } catch (e: OutOfMemoryError) {
                AppLogger.e(TAG, "OOM during inference: ${e.message}")
                return FloatArray(0)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Inference error: ${e.message}")
                return FloatArray(0)
            } finally {
                try { output?.close() } catch (_: Exception) {}
                try { inputIdsTensor?.close() } catch (_: Exception) {}
                try { attentionMaskTensor?.close() } catch (_: Exception) {}
                try { tokenTypeIdsTensor?.close() } catch (_: Exception) {}
            }
        }
    }

    private fun l2Normalize(vec: FloatArray): FloatArray {
        var normSq = 0f
        for (v in vec) normSq += v * v
        val norm = sqrt(normSq)
        if (norm < 1e-10f) return vec
        return FloatArray(vec.size) { i -> vec[i] / norm }
    }
}
