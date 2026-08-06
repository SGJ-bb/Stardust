package com.aicompanion.rag

import android.content.Context
import android.util.Log
import com.aicompanion.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * 角色 RAG 管理器
 *
 * 在传统向量检索的基础上,增加了树结构 RAG 功能:
 * 当 [RagConfig.treeRagEnabled] 为 true 时,检索到初始分块后,会沿分块中的
 * [[超链接]] 递归扩展相关分块(最大深度 [RagConfig.treeRagMaxDepth]),
 * 将这些"邻居"分块作为额外上下文提供给 LLM。
 *
 * 这模仿了 Obsidian 的双向链接机制: 知识不是孤立的,而是通过超链接相互关联,
 * 检索时可以"跳读"到相关主题,形成树状/图状的上下文扩展。
 */
class PersonaRagManager(private val context: Context, private val personaId: String = "default") {

    companion object {
        private const val TAG = "PersonaRagManager"
        /** 树扩展时每层最多引入的分块数量,避免上下文爆炸 */
        private const val MAX_EXPANSION_PER_DEPTH = 3
        /** 树扩展时总共最多引入的分块数量 */
        private const val MAX_EXPANSION_TOTAL = 8
    }

    private var embedder: RagEmbedder = TfidfEmbedder()
    private var lastEmbedderType: String = "tfidf"
    private val store = VectorStore(context, "persona_$personaId")
    private val rwLock = ReentrantReadWriteLock()
    @Volatile private var reranker: OnnxReranker? = null
    @Volatile private var rerankerInitFailed = false
    /** 上次构建索引时使用的分块参数,用于检测参数变更 */
    @Volatile private var lastChunkMaxChars: Int = -1
    @Volatile private var lastChunkOverlapChars: Int = -1

    @Volatile private var personaHash: String = ""
    @Volatile private var isIndexed = false

    @Synchronized
    private fun resolveEmbedder(): RagEmbedder {
        val currentType = when (RagConfig.embeddingMode) {
            "cloud" -> if (RagConfig.cloudEmbeddingUrl.isNotBlank()
                && RagConfig.cloudEmbeddingApiKey.isNotBlank()) "cloud" else "tfidf"
            "local" -> {
                val onnxEmb = embedder as? OnnxEmbedder
                if (onnxEmb != null && onnxEmb.isModelReady()) "local" else "tfidf"
            }
            else -> "tfidf"
        }

        val current = embedder
        if (lastEmbedderType == currentType && current != null) return current

        @Suppress("SENSELESS_COMPARISON")
        val e = when (currentType) {
            "cloud" -> CloudEmbedder(
                RagConfig.cloudEmbeddingUrl,
                RagConfig.cloudEmbeddingApiKey,
                RagConfig.cloudEmbeddingModel
            )
            "local" -> OnnxEmbedder(context)
            else -> TfidfEmbedder()
        }

        if (lastEmbedderType != "" && lastEmbedderType != currentType) {
            // 关闭旧的 OnnxEmbedder session 释放内存
            (current as? OnnxEmbedder)?.release()
            isIndexed = false
            personaHash = ""
            AppLogger.i(TAG, "Embedder type changed ($lastEmbedderType -> $currentType), forcing index rebuild")
        }

        embedder = e
        lastEmbedderType = currentType
        return e
    }

    fun currentHash(): String = personaHash

    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    suspend fun buildIndex(personaFields: Map<String, String>): Boolean = withContext(Dispatchers.IO) {
        try {
            val newHash = sha256(personaFields.entries
                .sortedBy { it.key }
                .joinToString("\u0000") { "${it.key}=${it.value}" }
            )
            // 检测分块参数是否变更,变更则强制重建
            val currentMaxChars = RagConfig.chunkMaxChars
            val currentOverlapChars = RagConfig.chunkOverlapChars
            val chunkParamsChanged = lastChunkMaxChars != currentMaxChars || lastChunkOverlapChars != currentOverlapChars

            if (newHash == personaHash && isIndexed && !chunkParamsChanged) return@withContext true

            rwLock.writeLock().lock()
            try {
                // 分块参数变更时跳过缓存,强制重建
                if (!chunkParamsChanged && store.load() && store.size() > 0) {
                    val storedHash = context.getSharedPreferences("rag_vector_persona", Context.MODE_PRIVATE)
                        .getString("persona_hash", "")
                    if (storedHash == newHash) {
                        personaHash = newHash
                        isIndexed = true
                        lastChunkMaxChars = currentMaxChars
                        lastChunkOverlapChars = currentOverlapChars
                        val emb = resolveEmbedder()
                        if (emb is TfidfEmbedder) {
                            emb.buildVocabulary(store.getAllTexts())
                        }
                        return@withContext true
                    }
                }

                // 每次构建索引时使用最新的分块参数
                val chunker = TextChunker(
                    maxChars = currentMaxChars,
                    overlapChars = currentOverlapChars
                )
                val chunks = chunker.chunkPersona(personaFields)
                if (chunks.isEmpty()) return@withContext false

                val emb = resolveEmbedder()
                val chunkTexts = chunks.map { it.text }
                if (emb is TfidfEmbedder) {
                    emb.buildVocabulary(chunkTexts)
                }
                val vectors = emb.embed(chunkTexts)
                store.addAll(chunks, vectors)
                store.save()

                context.getSharedPreferences("rag_vector_persona", Context.MODE_PRIVATE)
                    .edit().putString("persona_hash", newHash).apply()

                personaHash = newHash
                isIndexed = true
                lastChunkMaxChars = currentMaxChars
                lastChunkOverlapChars = currentOverlapChars
                val linkCount = chunks.sumOf { it.links.size }
                Log.d(TAG, "Index built: ${chunks.size} chunks, $linkCount links extracted")
                true
            } finally {
                rwLock.writeLock().unlock()
            }
        } catch (e: Exception) {
            com.aicompanion.util.AppLogger.e(TAG, "[RAG-Index] 构建角色知识库索引失败: ${e.javaClass.simpleName}: ${e.message} | personaId=$personaId", e)
            false
        }
    }

    /**
     * 异步检索
     *
     * 流程:
     * 1. 向量检索获取候选分块
     * 2. 若启用重排序([RagConfig.rerankerEnabled])且模型就绪,对候选进行二次排序
     * 3. 若启用树结构RAG([RagConfig.treeRagEnabled]),沿 [[超链接]] 扩展相关分块
     *
     * @param query 用户查询
     * @param topK 返回的初始检索结果数
     * @return 检索结果文本列表(初始结果在前,扩展结果在后)
     */
    suspend fun retrieve(query: String, topK: Int = RagConfig.personaTopK): List<String> = withContext(Dispatchers.IO) {
        if (!isIndexed || query.isBlank()) return@withContext emptyList()

        try {
            rwLock.readLock().lock()
            try {
                val queryVec = resolveEmbedder().embedSingle(query)
                // 重排序: 先取更多候选
                val candidateK = if (RagConfig.rerankerEnabled) {
                    maxOf(topK, RagConfig.rerankerTopKBefore)
                } else {
                    topK
                }
                val initialResults = store.search(queryVec, candidateK, RagConfig.minSimilarity)

                if (initialResults.isEmpty()) return@withContext emptyList()

                // 重排序: 用 Cross-Encoder 重新评分
                val rankedEntries = if (RagConfig.rerankerEnabled) {
                    rerankEntries(query, initialResults, topK)
                } else {
                    initialResults.take(topK).map { it.first }
                }

                // 树结构 RAG: 沿链接扩展
                if (RagConfig.treeRagEnabled) {
                    val expanded = expandByLinks(rankedEntries)
                    if (expanded.isNotEmpty()) {
                        AppLogger.d(TAG, "Tree RAG expanded ${expanded.size} additional chunks")
                    }
                    return@withContext rankedEntries.map { it.text } + expanded.map { it.text }
                }

                rankedEntries.map { it.text }
            } finally {
                rwLock.readLock().unlock()
            }
        } catch (e: Exception) {
            com.aicompanion.util.AppLogger.e(TAG, "[RAG-Retrieve] 异步检索失败: ${e.javaClass.simpleName}: ${e.message} | query='${query.take(50)}' topK=$topK", e)
            emptyList()
        }
    }

    fun retrieveSync(query: String, topK: Int = RagConfig.personaTopK): List<String> {
        if (!isIndexed || query.isBlank()) return emptyList()
        return try {
            val emb = resolveEmbedder()
            // 云端嵌入模式在同步方法中降级为TF-IDF,避免阻塞主线程
            // (CloudEmbedder.embedSingle 是挂起函数,runBlocking 会导致主线程卡顿)
            val queryVec = when (emb) {
                is TfidfEmbedder -> emb.embedSingleSync(query)
                is OnnxEmbedder -> emb.embedSingleSync(query)
                is CloudEmbedder -> {
                    AppLogger.w(TAG, "Cloud embedder not supported in sync mode, falling back to empty result")
                    FloatArray(0)
                }
                else -> FloatArray(0)
            }
            if (queryVec.isEmpty()) return emptyList()

            rwLock.readLock().lock()
            try {
                val candidateK = if (RagConfig.rerankerEnabled) {
                    maxOf(topK, RagConfig.rerankerTopKBefore)
                } else {
                    topK
                }
                val initialResults = store.search(queryVec, candidateK, RagConfig.minSimilarity)
                if (initialResults.isEmpty()) return emptyList()

                val rankedEntries = if (RagConfig.rerankerEnabled) {
                    rerankEntries(query, initialResults, topK)
                } else {
                    initialResults.take(topK).map { it.first }
                }

                if (RagConfig.treeRagEnabled) {
                    val expanded = expandByLinks(rankedEntries)
                    return rankedEntries.map { it.text } + expanded.map { it.text }
                }
                rankedEntries.map { it.text }
            } finally {
                rwLock.readLock().unlock()
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "[RAG-Retrieve] 同步检索失败: ${e.javaClass.simpleName}: ${e.message} | query='${query.take(50)}' topK=$topK", e); emptyList()
        }
    }

    /**
     * 用 OnnxReranker 对候选分块进行重排序
     *
     * 如果模型未就绪或初始化失败,降级为原始排序(只取 topK 个)。
     *
     * @param query 用户查询
     * @param candidates 候选分块(含相似度分数)
     * @param topK 返回的前K个结果
     * @return 重排序后的分块列表
     */
    private fun rerankEntries(
        query: String,
        candidates: List<Pair<VectorStore.VectorEntry, Float>>,
        topK: Int
    ): List<VectorStore.VectorEntry> {
        val topKAfter = topK.coerceAtMost(RagConfig.rerankerTopKAfter)
        if (candidates.isEmpty()) return emptyList()

        // 获取或初始化 reranker
        val rerankerInstance = getOrInitReranker()
        if (rerankerInstance == null) {
            AppLogger.w(TAG, "Reranker unavailable, falling back to vector similarity ranking")
            return candidates.take(topKAfter).map { it.first }
        }

        return try {
            val docs = candidates.map { it.first.text }
            val rankedIndices = rerankerInstance.rerankResults(query, docs, topKAfter)
            rankedIndices.map { idx -> candidates[idx].first }
        } catch (e: Exception) {
            AppLogger.e(TAG, "[RAG-Rerank] 重排序失败,降级为向量相似度排序: ${e.javaClass.simpleName}: ${e.message} | candidates=${candidates.size} topKAfter=$topKAfter")
            candidates.take(topKAfter).map { it.first }
        }
    }

    /**
     * 获取或初始化 OnnxReranker (懒加载)
     *
     * 失败后标记 rerankerInitFailed=true,本次会话不再重试。
     * 但当用户在设置中切换 rerankerEnabled 或调用 cleanup() 时会重置标记,
     * 允许重新尝试初始化(例如用户后来下载了模型)。
     */
    private fun getOrInitReranker(): OnnxReranker? {
        if (rerankerInitFailed) return null
        reranker?.let { if (it.isReady()) return it else it.release() }
        return try {
            val r = OnnxReranker(context)
            if (!r.isModelReady()) {
                AppLogger.w(TAG, "Reranker model not available (not packaged in APK or not downloaded)")
                rerankerInitFailed = true
                return null
            }
            if (!r.initialize()) {
                AppLogger.w(TAG, "Reranker initialization failed")
                rerankerInitFailed = true
                return null
            }
            reranker = r
            AppLogger.i(TAG, "Reranker initialized successfully")
            r
        } catch (e: Exception) {
            AppLogger.e(TAG, "[RAG-Rerank] 重排序模型初始化异常: ${e.javaClass.simpleName}: ${e.message} | 模型路径=${context.filesDir}/bge-reranker-base")
            rerankerInitFailed = true
            null
        }
    }

    /**
     * 重置 reranker 状态(允许重新尝试初始化)
     *
     * 当用户在设置中切换 rerankerEnabled 开关时调用,
     * 或者当用户手动触发"重试"时调用。
     */
    fun resetRerankerState() {
        reranker?.release()
        reranker = null
        rerankerInitFailed = false
        AppLogger.i(TAG, "Reranker state reset, will retry on next retrieve")
    }

    /**
     * 树结构 RAG 核心: 沿超链接扩展上下文
     *
     * 算法(广度优先):
     * 1. 从初始检索到的分块出发,收集它们的所有 links
     * 2. 在 store 中查找其他分块,这些分块的 links 包含相同的链接文本
     *    (即"共同引用了同一个主题"的分块视为相关)
     * 3. 对新找到的分块,继续递归扩展,直到达到最大深度或总量上限
     *
     * 注意: 我们采用"共同引用"而不是"被引用"作为关联关系,因为角色知识库中
     * 通常没有明确的"目标分块"概念,而是通过 [[主题]] 标签相互关联。
     * 这与 Obsidian 的"标签聚合"类似,但用 Wikilink 语法表达。
     *
     * @param initialChunks 初始检索到的分块
     * @return 扩展得到的额外分块列表(已排除初始分块,已去重)
     */
    private fun expandByLinks(initialChunks: List<VectorStore.VectorEntry>): List<VectorStore.VectorEntry> {
        val maxDepth = RagConfig.treeRagMaxDepth.coerceIn(1, 5)
        val result = LinkedHashMap<Int, VectorStore.VectorEntry>()
        val visited = mutableSetOf<Int>().apply { addAll(initialChunks.map { it.id }) }

        var currentLayer = initialChunks
        var depth = 0

        while (depth < maxDepth && result.size < MAX_EXPANSION_TOTAL) {
            // 收集当前层所有分块的链接文本
            val allLinks = currentLayer.flatMap { it.links }.distinct()
            if (allLinks.isEmpty()) break

            // 查找共同引用这些链接的其他分块
            val nextLayer = mutableListOf<VectorStore.VectorEntry>()
            val candidates = store.findByLinks(allLinks, excludeIds = visited)

            // 每层最多引入 MAX_EXPANSION_PER_DEPTH 个新分块,保持多样性
            for (candidate in candidates) {
                if (result.size >= MAX_EXPANSION_TOTAL) break
                if (nextLayer.size >= MAX_EXPANSION_PER_DEPTH) break
                if (candidate.id in visited) continue

                result[candidate.id] = candidate
                visited.add(candidate.id)
                nextLayer.add(candidate)
            }

            if (nextLayer.isEmpty()) break
            currentLayer = nextLayer
            depth++
        }

        return result.values.toList()
    }

    fun isReady(): Boolean = isIndexed

    fun getChunkCount(): Int = store.size()

    /** 获取所有分块中提取到的链接文本集合(用于调试/UI展示) */
    fun getAllLinks(): Set<String> = store.getAllLinks()

    fun clear() {
        rwLock.writeLock().lock()
        try {
            store.clear()
            personaHash = ""
            isIndexed = false
        } finally {
            rwLock.writeLock().unlock()
        }
    }

    fun cleanup() {
        try {
            (embedder as? OnnxEmbedder)?.release()
            embedder = TfidfEmbedder()
            lastEmbedderType = "tfidf"
            reranker?.release()
            reranker = null
            rerankerInitFailed = false
            clear()
        } catch (e: Exception) {
            AppLogger.e(TAG, "[RAG-Cleanup] 清理RAG资源失败: ${e.javaClass.simpleName}: ${e.message}")
        }
    }
}
