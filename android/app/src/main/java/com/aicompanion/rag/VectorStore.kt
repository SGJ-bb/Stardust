package com.aicompanion.rag

import android.content.Context
import com.aicompanion.util.AppLogger
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

/**
 * 向量存储
 *
 * 除了存储分块的文本和向量外,还存储分块中提取到的 [[超链接]] 文本,
 * 支持通过链接文本查找相关分块(用于树结构 RAG 的上下文扩展)。
 *
 * 持久化格式(JSON):
 * [{
 *   "id": 0,
 *   "text": "...",
 *   "source": "field_name",
 *   "vector": [0.1, 0.2, ...],
 *   "links": ["链接1", "链接2"]
 * }]
 *
 * 旧格式(无 links 字段)会自动兼容,links 默认为空数组。
 */
class VectorStore(private val context: Context, private val storeName: String = "default") {

    data class VectorEntry(
        val id: Int,
        val text: String,
        val vector: FloatArray,
        val sourceField: String = "",
        /** 该分块中提取到的 [[链接]] 文本列表 */
        val links: List<String> = emptyList()
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is VectorEntry) return false
            return id == other.id && text == other.text && vector.contentEquals(other.vector) && sourceField == other.sourceField && links == other.links
        }

        override fun hashCode(): Int {
            var result = id
            result = 31 * result + text.hashCode()
            result = 31 * result + vector.contentHashCode()
            result = 31 * result + sourceField.hashCode()
            result = 31 * result + links.hashCode()
            return result
        }
    }

    private val entries = mutableListOf<VectorEntry>()
    private val lock = ReentrantReadWriteLock()
    private val storeFile = File(context.filesDir, "rag_vectors/$storeName.json")

    fun add(id: Int, text: String, vector: FloatArray, sourceField: String = "", links: List<String> = emptyList()) {
        lock.writeLock().withLock {
            entries.removeAll { it.id == id }
            entries.add(VectorEntry(id, text, vector.copyOf(), sourceField, links.distinct()))
        }
    }

    fun addAll(chunks: List<TextChunker.Chunk>, vectors: List<FloatArray>) {
        lock.writeLock().withLock {
            entries.clear()
            for (i in chunks.indices) {
                val vec = if (i < vectors.size) vectors[i].copyOf() else FloatArray(0)
                entries.add(VectorEntry(
                    id = chunks[i].index,
                    text = chunks[i].text,
                    vector = vec,
                    sourceField = chunks[i].sourceField,
                    links = chunks[i].links
                ))
            }
        }
    }

    fun search(queryVector: FloatArray, topK: Int = 3, minSimilarity: Float = 0.12f): List<Pair<VectorEntry, Float>> {
        if (queryVector.isEmpty()) return emptyList()

        lock.readLock().withLock {
            if (entries.isEmpty()) return emptyList()

            val results = entries.map { entry ->
                entry to VectorMath.cosineSimilarity(queryVector, entry.vector)
            }.filter { it.second >= minSimilarity }
                .sortedByDescending { it.second }
                .take(topK)

            return results
        }
    }

    /**
     * 通过 id 查找分块
     */
    fun findById(id: Int): VectorEntry? = lock.readLock().withLock {
        entries.firstOrNull { it.id == id }
    }

    /**
     * 通过链接文本查找分块
     *
     * 返回所有 links 中包含指定链接文本的分块(精确匹配,区分大小写)。
     * 用于树结构 RAG 的上下文扩展: 当检索到某个分块时,通过其 links 找到其他相关分块。
     *
     * @param linkText 链接目标文本
     * @param excludeIds 需要排除的 id 列表(避免重复返回已检索到的分块)
     */
    fun findByLink(linkText: String, excludeIds: Set<Int> = emptySet()): List<VectorEntry> = lock.readLock().withLock {
        if (linkText.isBlank()) return@withLock emptyList()
        entries.filter { it.id !in excludeIds && it.links.any { link -> link == linkText } }
    }

    /**
     * 批量通过链接文本查找分块
     *
     * @param linkTexts 多个链接文本
     * @param excludeIds 需要排除的 id 列表
     * @return 去重后的分块列表(保持顺序)
     */
    fun findByLinks(linkTexts: Collection<String>, excludeIds: Set<Int> = emptySet()): List<VectorEntry> = lock.readLock().withLock {
        if (linkTexts.isEmpty()) return@withLock emptyList()
        val linkSet = linkTexts.filter { it.isNotBlank() }.toSet()
        if (linkSet.isEmpty()) return@withLock emptyList()
        val seen = mutableSetOf<Int>()
        entries.filter { entry ->
            entry.id !in excludeIds &&
            entry.id !in seen &&
            entry.links.any { it in linkSet }
        }.onEach { seen.add(it.id) }
    }

    /**
     * 获取所有分块的链接文本集合(用于调试和可视化)
     */
    fun getAllLinks(): Set<String> = lock.readLock().withLock {
        entries.flatMap { it.links }.toSet()
    }

    fun size(): Int = lock.readLock().withLock { entries.size }

    fun getAllTexts(): List<String> = lock.readLock().withLock { entries.map { it.text } }

    fun save(): Boolean {
        return lock.writeLock().withLock {
            try {
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
                    val linkArr = JSONArray()
                    for (l in entry.links) linkArr.put(l)
                    obj.put("links", linkArr)
                    arr.put(obj)
                }
                storeFile.writeText(arr.toString())
                true
            } catch (e: Exception) {
                com.aicompanion.util.AppLogger.e("VectorStore", "save: ${e.message}")
                false
            }
        }
    }

    fun load(): Boolean {
        return lock.writeLock().withLock {
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
                            val links = mutableListOf<String>()
                            if (obj.has("links")) {
                                val linkArr = obj.getJSONArray("links")
                                for (j in 0 until linkArr.length()) links.add(linkArr.getString(j))
                            }
                            entries.add(VectorEntry(
                                id = obj.getInt("id"),
                                text = obj.getString("text"),
                                vector = vec,
                                sourceField = obj.optString("source", ""),
                                links = links
                            ))
                        }
                        // 尝试迁移到新格式
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
                    entries.clear()
                    return false
                }
                val arr = JSONArray(json)
                entries.clear()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val vecArr = obj.getJSONArray("vector")
                    val vec = FloatArray(vecArr.length())
                    for (j in 0 until vecArr.length()) vec[j] = vecArr.getDouble(j).toFloat()
                    val links = mutableListOf<String>()
                    if (obj.has("links")) {
                        val linkArr = obj.getJSONArray("links")
                        for (j in 0 until linkArr.length()) links.add(linkArr.getString(j))
                    }
                    entries.add(VectorEntry(
                        id = obj.getInt("id"),
                        text = obj.getString("text"),
                        vector = vec,
                        sourceField = obj.optString("source", ""),
                        links = links
                    ))
                }
                return entries.isNotEmpty()
            } catch (e: Exception) {
                AppLogger.e("VectorStore", "load failed: ${e.message}")
                false
            }
        }
    }

    fun clear() {
        lock.writeLock().withLock {
            entries.clear()
            try { storeFile.delete() } catch (_: Exception) {}
        }
    }
}
