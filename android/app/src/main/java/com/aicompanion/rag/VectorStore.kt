package com.aicompanion.rag

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class VectorStore(private val context: Context, private val storeName: String = "default") {

    data class VectorEntry(
        val id: Int,
        val text: String,
        val vector: FloatArray,
        val sourceField: String = ""
    )

    private val entries = mutableListOf<VectorEntry>()
    private val prefs = context.getSharedPreferences("rag_vector_$storeName", Context.MODE_PRIVATE)

    fun add(id: Int, text: String, vector: FloatArray, sourceField: String = "") {
        entries.removeAll { it.id == id }
        entries.add(VectorEntry(id, text, vector.copyOf(), sourceField))
    }

    fun addAll(chunks: List<TextChunker.Chunk>, vectors: List<FloatArray>) {
        entries.clear()
        for (i in chunks.indices) {
            entries.add(VectorEntry(
                id = chunks[i].index,
                text = chunks[i].text,
                vector = vectors[i].copyOf(),
                sourceField = chunks[i].sourceField
            ))
        }
    }

    fun search(queryVector: FloatArray, topK: Int = 3, minSimilarity: Float = 0.12f): List<Pair<VectorEntry, Float>> {
        if (queryVector.isEmpty() || entries.isEmpty()) return emptyList()

        val results = entries.map { entry ->
            entry to cosineSimilarity(queryVector, entry.vector)
        }.filter { it.second >= minSimilarity }
            .sortedByDescending { it.second }
            .take(topK)

        return results
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB)
        return if (denom > 0) dot / denom else 0f
    }

    fun size(): Int = entries.size

    fun getAllTexts(): List<String> = entries.map { it.text }

    fun save() {
        try {
            val arr = JSONArray()
            for (entry in entries) {
                val obj = JSONObject()
                obj.put("id", entry.id)
                obj.put("text", entry.text)
                obj.put("source", entry.sourceField)
                val vecArr = JSONArray()
                for (v in entry.vector) vecArr.put(v.toDouble())
                obj.put("vector", vecArr)
                arr.put(obj)
            }
            prefs.edit().putString("entries", arr.toString()).apply()
        } catch (_: Exception) {}
    }

    fun load(): Boolean {
        try {
            val json = prefs.getString("entries", null) ?: return false
            val arr = JSONArray(json)
            entries.clear()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val vecArr = obj.getJSONArray("vector")
                val vec = FloatArray(vecArr.length())
                for (j in 0 until vecArr.length()) vec[j] = vecArr.getDouble(j).toFloat()
                entries.add(VectorEntry(
                    id = obj.getInt("id"),
                    text = obj.getString("text"),
                    vector = vec,
                    sourceField = obj.optString("source", "")
                ))
            }
            return entries.isNotEmpty()
        } catch (_: Exception) {
            return false
        }
    }

    fun clear() {
        entries.clear()
        prefs.edit().clear().apply()
    }
}