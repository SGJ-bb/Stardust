package com.aicompanion.rag

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PersonaRagManager(private val context: Context) {

    companion object {
        private const val TAG = "PersonaRagManager"
    }

    private val chunker = TextChunker()
    private val embedder = TfidfEmbedder()
    private val store = VectorStore(context, "persona")

    private var personaHash: String = ""
    private var isIndexed = false

    fun currentHash(): String = personaHash

    suspend fun buildIndex(personaFields: Map<String, String>): Boolean = withContext(Dispatchers.IO) {
        try {
            val newHash = personaFields.values.joinToString("|").hashCode().toString()
            if (newHash == personaHash && isIndexed) return@withContext true

            if (store.load() && store.size() > 0) {
                val storedHash = context.getSharedPreferences("rag_vector_persona", Context.MODE_PRIVATE)
                    .getString("persona_hash", "")
                if (storedHash == newHash) {
                    personaHash = newHash
                    isIndexed = true
                    embedder.buildVocabulary(store.getAllTexts())
                    return@withContext true
                }
            }

            val chunks = chunker.chunkPersona(personaFields)
            if (chunks.isEmpty()) return@withContext false

            val chunkTexts = chunks.map { it.text }
            embedder.buildVocabulary(chunkTexts)
            val vectors = embedder.embed(chunkTexts)
            store.addAll(chunks, vectors)
            store.save()

            context.getSharedPreferences("rag_vector_persona", Context.MODE_PRIVATE)
                .edit().putString("persona_hash", newHash).apply()

            personaHash = newHash
            isIndexed = true
            Log.d(TAG, "Index built: ${chunks.size} chunks")
            true
        } catch (e: Exception) {
            Log.e(TAG, "buildIndex failed: ${e.message}")
            false
        }
    }

    suspend fun retrieve(query: String, topK: Int = RagConfig.personaTopK): List<String> = withContext(Dispatchers.IO) {
        if (!isIndexed || query.isBlank()) return@withContext emptyList()

        try {
            val queryVec = embedder.embedSingle(query)
            val results = store.search(queryVec, topK, RagConfig.minSimilarity)
            results.map { (entry, _) -> entry.text }
        } catch (e: Exception) {
            Log.e(TAG, "retrieve failed: ${e.message}")
            emptyList()
        }
    }

    fun retrieveSync(query: String, topK: Int = RagConfig.personaTopK): List<String> {
        if (!isIndexed || query.isBlank()) return emptyList()
        return try {
            val queryVec = embedder.embedSingleSync(query)
            store.search(queryVec, topK, RagConfig.minSimilarity).map { (entry, _) -> entry.text }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun isReady(): Boolean = isIndexed

    fun getChunkCount(): Int = store.size()

    fun clear() {
        store.clear()
        personaHash = ""
        isIndexed = false
    }
}