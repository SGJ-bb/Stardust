package com.aicompanion.sticker

import android.content.Context
import com.aicompanion.network.ApiClient
import com.aicompanion.rag.VectorStore
import com.aicompanion.util.AppLogger
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class StickerManager(private val context: Context) {
    companion object {
        private const val TAG = "StickerManager"
        private const val STICKER_DIR = "stickers"
        private const val INDEX_FILE = "sticker_index.json"
        private const val BUILTIN_DIR = "builtin_stickers"
        private const val BUILTIN_META = "builtin_metadata.json"
        private const val BUILTIN_EMBEDDINGS = "builtin_embeddings.bin"
    }

    private val stickerDir = File(context.filesDir, STICKER_DIR).apply { mkdirs() }
    private val builtinDir = File(stickerDir, BUILTIN_DIR).apply { mkdirs() }
    private val indexFile = File(stickerDir, INDEX_FILE)
    private val userStickers = mutableListOf<Sticker>()
    private val builtinStickers = mutableListOf<Sticker>()
    private val builtinEmbeddings = mutableMapOf<String, FloatArray>()
    private var builtinDim = 0
    private val userVectorStore = VectorStore(context, "sticker_user")

    fun loadStickers() {
        loadBuiltinStickers()
        loadUserStickers()
    }

    private var builtinLoaded = false

    private fun loadBuiltinStickers() {
        if (builtinLoaded) return
        builtinStickers.clear()
        builtinEmbeddings.clear()
        try {
            val metaJson = context.assets.open("$BUILTIN_DIR/$BUILTIN_META").bufferedReader().use { it.readText() }
            val meta = JSONObject(metaJson)
            builtinDim = meta.optInt("dim", 0)
            val arr = meta.optJSONArray("stickers") ?: return
            val assetFiles = try { context.assets.list(BUILTIN_DIR)?.toSet() } catch (_: Exception) { null }
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.getString("id")
                val name = id.removePrefix("builtin_")
                val filename = assetFiles?.find { it.startsWith(name + ".") && it != BUILTIN_META && it != BUILTIN_EMBEDDINGS } ?: ""
                val destFile = File(builtinDir, filename)
                if (filename.isNotBlank() && !destFile.exists()) {
                    try {
                        context.assets.open("$BUILTIN_DIR/$filename").use { input ->
                            destFile.outputStream().use { output -> input.copyTo(output) }
                        }
                    } catch (_: Exception) {}
                }
                builtinStickers.add(Sticker(
                    id = id,
                    filePath = if (destFile.exists()) destFile.absolutePath else "",
                    description = obj.optString("description", ""),
                    emotion = obj.optString("emotion", ""),
                    tags = obj.optJSONArray("tags")?.let { ta ->
                        (0 until ta.length()).map { ta.getString(it) }
                    } ?: emptyList(),
                    owner = "builtin",
                    createdAt = 0L
                ))
            }
            loadBuiltinEmbeddings()
            builtinLoaded = true
        } catch (e: Exception) {
            AppLogger.e(TAG, "loadBuiltinStickers failed: ${e.message}")
        }
    }

    private fun loadBuiltinEmbeddings() {
        if (builtinDim <= 0) return
        try {
            val hasBin = try {
                context.assets.list(BUILTIN_DIR)?.contains(BUILTIN_EMBEDDINGS) == true
            } catch (_: Exception) { false }
            if (hasBin) {
                context.assets.open("$BUILTIN_DIR/$BUILTIN_EMBEDDINGS").use { input ->
                    val header = ByteArray(8)
                    input.read(header)
                    val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                    val dim = buf.int
                    val count = buf.int
                    for (i in 0 until count) {
                        val vecBytes = ByteArray(dim * 4)
                        input.read(vecBytes)
                        val vecBuf = ByteBuffer.wrap(vecBytes).order(ByteOrder.LITTLE_ENDIAN)
                        val vec = FloatArray(dim)
                        for (j in 0 until dim) vec[j] = vecBuf.float
                        if (i < builtinStickers.size) {
                            builtinEmbeddings[builtinStickers[i].id] = vec
                        }
                    }
                }
                AppLogger.d(TAG, "Loaded ${builtinEmbeddings.size} builtin embeddings, dim=$builtinDim")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "loadBuiltinEmbeddings failed: ${e.message}")
        }
    }

    private fun loadUserStickers() {
        userStickers.clear()
        if (indexFile.exists()) {
            try {
                val json = JSONObject(indexFile.readText())
                val arr = json.optJSONArray("stickers") ?: return
                for (i in 0 until arr.length()) {
                    userStickers.add(Sticker.fromJson(arr.getJSONObject(i)))
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "loadUserStickers failed: ${e.message}")
            }
        }
        userVectorStore.load()
    }

    fun saveIndex() {
        try {
            val json = JSONObject().apply {
                put("stickers", JSONArray().apply {
                    userStickers.forEach { put(it.toJson()) }
                })
            }
            indexFile.writeText(json.toString())
        } catch (e: Exception) {
            AppLogger.e(TAG, "saveIndex failed: ${e.message}")
        }
    }

    fun addSticker(sourcePath: String, description: String, emotion: String, tags: List<String>, owner: String, embedding: FloatArray? = null): Sticker {
        val id = UUID.randomUUID().toString()
        val ext = sourcePath.substringAfterLast(".", "png")
        val destFile = File(stickerDir, "$id.$ext")
        File(sourcePath).inputStream().use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        }
        val sticker = Sticker(
            id = id,
            filePath = destFile.absolutePath,
            description = description,
            emotion = emotion,
            tags = tags,
            owner = owner,
            embedding = embedding
        )
        userStickers.add(sticker)
        if (embedding != null) {
            userVectorStore.add(userStickers.size - 1, "$description $emotion ${tags.joinToString(" ")}", embedding)
            userVectorStore.save()
        }
        saveIndex()
        AppLogger.d(TAG, "User sticker added: $id, emotion=$emotion")
        return sticker
    }

    fun deleteSticker(id: String) {
        if (id.startsWith("builtin_")) return
        val idx = userStickers.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val sticker = userStickers.removeAt(idx)
            File(sticker.filePath).delete()
            saveIndex()
            userVectorStore.clear()
            userStickers.forEachIndexed { i, s ->
                s.embedding?.let { userVectorStore.add(i, "${s.description} ${s.emotion} ${s.tags.joinToString(" ")}", it) }
            }
            userVectorStore.save()
        }
    }

    fun getAllStickers(): List<Sticker> = builtinStickers + userStickers

    fun getBuiltinStickers(): List<Sticker> = builtinStickers.toList()

    fun getUserStickers(): List<Sticker> = userStickers.toList()

    fun getStickersByOwner(owner: String): List<Sticker> = when (owner) {
        "builtin" -> builtinStickers
        else -> userStickers.filter { it.owner == owner }
    }

    suspend fun searchStickersByEmotion(query: String, apiClient: ApiClient?, topK: Int = 3): List<Sticker> {
        if (apiClient == null) return searchStickersByKeyword(query)
        return try {
            val queryVec = apiClient.getEmbedding(query) ?: return searchStickersByKeyword(query)
            val results = mutableListOf<Pair<Sticker, Float>>()
            for ((id, vec) in builtinEmbeddings) {
                if (vec.size != queryVec.size) continue
                val sim = cosineSimilarity(queryVec, vec)
                if (sim >= 0.3f) {
                    val sticker = builtinStickers.find { it.id == id } ?: continue
                    results.add(sticker to sim)
                }
            }
            val userResults = userVectorStore.search(queryVec, topK, 0.3f)
            for ((entry, score) in userResults) {
                userStickers.getOrNull(entry.id)?.let { results.add(it to score) }
            }
            results.sortedByDescending { it.second }.take(topK).map { it.first }
        } catch (e: Exception) {
            AppLogger.e(TAG, "searchStickersByEmotion failed: ${e.message}")
            searchStickersByKeyword(query)
        }
    }

    fun searchStickersByKeyword(keyword: String): List<Sticker> {
        val kw = keyword.lowercase()
        val builtin = builtinStickers.filter {
            it.description.lowercase().contains(kw) ||
            it.emotion.lowercase().contains(kw) ||
            it.tags.any { t -> t.lowercase().contains(kw) } ||
            it.id.removePrefix("builtin_").lowercase().contains(kw)
        }
        val user = userStickers.filter {
            it.description.lowercase().contains(kw) ||
            it.emotion.lowercase().contains(kw) ||
            it.tags.any { t -> t.lowercase().contains(kw) }
        }
        return builtin + user
    }

    fun getStickerPath(id: String): String? {
        if (id.startsWith("builtin_")) {
            return builtinStickers.find { it.id == id }?.filePath
        }
        return userStickers.find { it.id == id }?.filePath
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
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
}
