package com.aicompanion.rag

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.concurrent.locks.ReentrantReadWriteLock

class PersonaRagManager(private val context: Context, private val personaId: String = "default") {

    companion object {
        private const val TAG = "PersonaRagManager"
    }

    private val chunker = TextChunker()
    private var embedder: RagEmbedder = TfidfEmbedder()
    private val store = VectorStore(context, "persona_$personaId")
    private val rwLock = ReentrantReadWriteLock()

    @Volatile private var personaHash: String = ""
    @Volatile private var isIndexed = false

    private fun resolveEmbedder(): RagEmbedder {
        if (RagConfig.useCloudEmbedding
            && RagConfig.cloudEmbeddingUrl.isNotBlank()
            && RagConfig.cloudEmbeddingApiKey.isNotBlank()
        ) {
            val current = embedder
            if (current is CloudEmbedder) return current
            val cloud = CloudEmbedder(
                RagConfig.cloudEmbeddingUrl,
                RagConfig.cloudEmbeddingApiKey,
                RagConfig.cloudEmbeddingModel
            )
            embedder = cloud
            return cloud
        }
        val current = embedder
        if (current is TfidfEmbedder) return current
        val tfidf = TfidfEmbedder()
        embedder = tfidf
        return tfidf
    }

    fun currentHash(): String = personaHash

    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    suspend fun buildIndex(personaFields: Map<String, String>): Boolean = withContext(Dispatchers.IO) {
        try {
            val newHash = sha256(personaFields.values.joinToString("|"))
            if (newHash == personaHash && isIndexed) return@withContext true

            rwLock.writeLock().lock()
            try {
                if (store.load() && store.size() > 0) {
                    val storedHash = context.getSharedPreferences("rag_vector_persona", Context.MODE_PRIVATE)
                        .getString("persona_hash", "")
                    if (storedHash == newHash) {
                        personaHash = newHash
                        isIndexed = true
                        val emb = resolveEmbedder()
                        if (emb is TfidfEmbedder) {
                            emb.buildVocabulary(store.getAllTexts())
                        }
                        return@withContext true
                    }
                }

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
                Log.d(TAG, "Index built: ${chunks.size} chunks")
                true
            } finally {
                rwLock.writeLock().unlock()
            }
        } catch (e: Exception) {
            com.aicompanion.util.AppLogger.e(TAG, "buildIndex failed: ${e.message}", e)
            false
        }
    }

    suspend fun retrieve(query: String, topK: Int = RagConfig.personaTopK): List<String> = withContext(Dispatchers.IO) {
        if (!isIndexed || query.isBlank()) return@withContext emptyList()

        try {
            rwLock.readLock().lock()
            try {
                val queryVec = resolveEmbedder().embedSingle(query)
                val results = store.search(queryVec, topK, RagConfig.minSimilarity)
                results.map { (entry, _) -> entry.text }
            } finally {
                rwLock.readLock().unlock()
            }
        } catch (e: Exception) {
            com.aicompanion.util.AppLogger.e(TAG, "retrieve failed: ${e.message}", e)
            emptyList()
        }
    }

    fun retrieveSync(query: String, topK: Int = RagConfig.personaTopK): List<String> {
        if (!isIndexed || query.isBlank()) return emptyList()
        return try {
            rwLock.readLock().lock()
            try {
                val emb = resolveEmbedder()
                val queryVec = if (emb is TfidfEmbedder) emb.embedSingleSync(query) else {
                    kotlinx.coroutines.runBlocking { emb.embedSingle(query) }
                }
                store.search(queryVec, topK, RagConfig.minSimilarity).map { (entry, _) -> entry.text }
            } finally {
                rwLock.readLock().unlock()
            }
        } catch (e: Exception) {
            com.aicompanion.util.AppLogger.e(TAG, "RAG同步检索失败: ${e.message}", e); emptyList()
        }
    }

    fun isReady(): Boolean = isIndexed

    fun getChunkCount(): Int = store.size()

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
}
