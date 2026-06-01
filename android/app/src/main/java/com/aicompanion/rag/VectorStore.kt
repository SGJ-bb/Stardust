package com.aicompanion.rag

import android.content.Context
import com.aicompanion.util.AppLogger
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class VectorStore(private val context: Context, private val storeName: String = "default") {

    data class VectorEntry(
        val id: Int,
        val text: String,
        val vector: FloatArray,
        val sourceField: String = ""
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is VectorEntry) return false
            return id == other.id && text == other.text && vector.contentEquals(other.vector) && sourceField == other.sourceField
        }

        override fun hashCode(): Int {
            var result = id
            result = 31 * result + text.hashCode()
            result = 31 * result + vector.contentHashCode()
            result = 31 * result + sourceField.hashCode()
            return result
        }
    }

    private val entries = mutableListOf<VectorEntry>()
    private val storeFile = File(context.filesDir, "rag_vectors/$storeName.json")

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
            entry to VectorMath.cosineSimilarity(queryVector, entry.vector)
        }.filter { it.second >= minSimilarity }
            .sortedByDescending { it.second }
            .take(topK)

        return results
    }

    fun size(): Int = entries.size

    fun getAllTexts(): List<String> = entries.map { it.text }

    fun save(): Boolean {
        return try {
            storeFile.parentFile?.mkdirs()
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
            storeFile.writeText(arr.toString())
            true
        } catch (e: Exception) { com.aicompanion.util.AppLogger.e("VectorStore", "save: ${e.message}"); false }
    }

    fun load(): Boolean {
        try {
            if (!storeFile.exists()) {
                val prefs = context.getSharedPreferences("rag_vector_$storeName", Context.MODE_PRIVATE)
                val legacyJson = prefs.getString("entries", null)
                if (legacyJson != null) {
                    val arr = JSONArray(legacyJson)
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
                    if (save()) {
                        prefs.edit().clear().apply()
                    } else {
                        storeFile.delete()
                    }
                    return entries.isNotEmpty()
                }
                return false
            }
            val json = storeFile.readText()
            if (json.isBlank()) {
                storeFile.delete()
                return load()
            }
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
        } catch (e: Exception) {
            AppLogger.e("VectorStore", "load failed: ${e.message}")
            return false
        }
    }

    fun clear() {
        entries.clear()
        try { storeFile.delete() } catch (_: Exception) {}
    }
}
