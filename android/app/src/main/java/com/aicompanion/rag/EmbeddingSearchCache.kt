package com.aicompanion.rag

import android.content.Context
import com.aicompanion.util.AppLogger
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class EmbeddingSearchCache(
    private val context: Context,
    private val cacheName: String
) {
    private val lock = Any()
    private var embedder: RagEmbedder? = null
    private var cachedEntries: List<IndexEntry> = emptyList()
    private var contentHash: String = ""
    private var isIndexBuilt = false
    private var lastEmbedderType: String = ""

    private data class IndexEntry(
        val id: String,
        val text: String,
        val vector: FloatArray,
        val metadata: Map<String, String> = emptyMap()
    )

    private fun getEmbedder(): RagEmbedder {
        synchronized(lock) {
            val existing = embedder
            val currentType = when (RagConfig.embeddingMode) {
                "cloud" -> if (RagConfig.cloudEmbeddingUrl.isNotBlank()
                    && RagConfig.cloudEmbeddingApiKey.isNotBlank()) "cloud" else "tfidf"
                "local" -> {
                    val onnxEmb = existing as? OnnxEmbedder
                    if (onnxEmb != null && onnxEmb.isModelReady()) "local" else "tfidf"
                }
                else -> "tfidf"
            }

            if (existing != null && lastEmbedderType == currentType) return existing

            val e = when (currentType) {
                "cloud" -> {
                    AppLogger.d("EmbeddingSearchCache", "Using cloud embedder for $cacheName")
                    CloudEmbedder(
                        RagConfig.cloudEmbeddingUrl,
                        RagConfig.cloudEmbeddingApiKey,
                        RagConfig.cloudEmbeddingModel
                    )
                }
                "local" -> {
                    AppLogger.d("EmbeddingSearchCache", "Using local ONNX embedder for $cacheName")
                    OnnxEmbedder(context)
                }
                else -> {
                    AppLogger.d("EmbeddingSearchCache", "Using TF-IDF embedder for $cacheName")
                    TfidfEmbedder()
                }
            }

            if (lastEmbedderType != "" && lastEmbedderType != currentType) {
                // 关闭旧的 OnnxEmbedder session 释放内存
                (existing as? OnnxEmbedder)?.release()
                AppLogger.i("EmbeddingSearchCache", "Embedder type changed ($lastEmbedderType -> $currentType), forcing index rebuild")
                isIndexBuilt = false
                contentHash = ""
            }

            embedder = e
            lastEmbedderType = currentType
            return e
        }
    }

    private fun computeHash(texts: List<String>): String {
        val md = MessageDigest.getInstance("SHA-256")
        for (t in texts) {
            md.update(t.toByteArray(Charsets.UTF_8))
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    suspend fun buildIndex(entries: List<Pair<String, String>>, forceRebuild: Boolean = false) {
        val texts = entries.map { it.second }
        val newHash = computeHash(texts)

        synchronized(lock) {
            if (!forceRebuild && isIndexBuilt && newHash == contentHash) {
                return
            }
        }

        val emb = getEmbedder()

        if (emb is TfidfEmbedder) {
            emb.buildVocabulary(texts)
        }

        val vectors = emb.embed(texts)

        synchronized(lock) {
            cachedEntries = entries.mapIndexed { i, (id, text) ->
                IndexEntry(id, text, vectors.getOrElse(i) { FloatArray(0) })
            }
            contentHash = newHash
            isIndexBuilt = true
        }
        AppLogger.d("EmbeddingSearchCache", "Index built for $cacheName: ${entries.size} entries, hash=$newHash")
    }

    suspend fun search(query: String, topK: Int = 5, minSim: Float = RagConfig.minSimilarity): List<SearchResult> {
        synchronized(lock) {
            if (!isIndexBuilt || cachedEntries.isEmpty()) return emptyList()
        }

        val emb = getEmbedder()
        val queryVec = emb.embedSingle(query)
        if (queryVec.isEmpty()) return emptyList()

        val entries: List<IndexEntry>
        var dimensionMismatch = false
        synchronized(lock) {
            // getEmbedder() 可能因模式切换而重置 isIndexBuilt，此时索引已失效
            if (!isIndexBuilt) return emptyList()
            entries = cachedEntries
            
            // 缺陷11修复:检测维度不匹配,触发自动重建索引
            if (entries.isNotEmpty() && entries[0].vector.size != queryVec.size) {
                AppLogger.w("EmbeddingSearchCache", "维度不匹配检测: 索引维度=${entries[0].vector.size}, 查询维度=${queryVec.size}, 将触发索引重建")
                dimensionMismatch = true
                isIndexBuilt = false
                contentHash = ""
                return emptyList()
            }
        }

        // 维度不匹配时触发后台重建(异步)
        if (dimensionMismatch) {
            // 不阻塞当前查询,让下次查询时自动重建
            AppLogger.i("EmbeddingSearchCache", "维度不匹配,已标记需要重建索引")
        }

        return entries.mapNotNull { entry ->
            if (entry.vector.isEmpty()) return@mapNotNull null
            // 维度不匹配时跳过（嵌入模式切换后索引未重建）
            if (entry.vector.size != queryVec.size) return@mapNotNull null
            val sim = VectorMath.cosineSimilarity(queryVec, entry.vector)
            if (sim >= minSim) {
                SearchResult(entry.id, entry.text, sim, entry.metadata)
            } else null
        }.sortedByDescending { it.score }.take(topK)
    }

    fun isReady(): Boolean = synchronized(lock) { isIndexBuilt }

    data class SearchResult(
        val id: String,
        val text: String,
        val score: Float,
        val metadata: Map<String, String> = emptyMap()
    )
}
