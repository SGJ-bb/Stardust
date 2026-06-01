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

    private data class IndexEntry(
        val id: String,
        val text: String,
        val vector: FloatArray,
        val metadata: Map<String, String> = emptyMap()
    )

    private fun getEmbedder(): RagEmbedder {
        val existing = embedder
        if (existing != null) return existing

        val e = if (RagConfig.useCloudEmbedding
            && RagConfig.cloudEmbeddingUrl.isNotBlank()
            && RagConfig.cloudEmbeddingApiKey.isNotBlank()
        ) {
            AppLogger.d("EmbeddingSearchCache", "Using cloud embedder for $cacheName")
            CloudEmbedder(
                RagConfig.cloudEmbeddingUrl,
                RagConfig.cloudEmbeddingApiKey,
                RagConfig.cloudEmbeddingModel
            )
        } else {
            TfidfEmbedder()
        }
        embedder = e
        return e
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
        synchronized(lock) {
            entries = cachedEntries
        }

        return entries.mapNotNull { entry ->
            if (entry.vector.isEmpty()) return@mapNotNull null
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
