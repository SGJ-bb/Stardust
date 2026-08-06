package com.aicompanion.diary

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import com.aicompanion.network.ApiClient
import com.aicompanion.util.AppLogger
import com.aicompanion.prompt.PromptBuilder
import com.aicompanion.rag.RagConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class DiaryManager(private val context: Context, private val personaId: String = "default") {

    companion object {
        const val CURRENT_VERSION = 2
        val APP_VERSION = "1.0.0"
        private const val TAG = "DiaryManager"
        private const val MIN_UPDATE_INTERVAL_MS = 30 * 60 * 1000L
    }

    // SQLite-backed storage (shared with chat messages in same DB)
    private val storage by lazy { com.aicompanion.storage.ChatHistoryStorage(context) }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val fullDateFormat = SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINESE)
    private var lastUpdateTime = 0L

    // ─── Read operations (SQLite) ──────────────────────────────

    fun getAllDiaries(): List<DiaryEntry> = storage.getAllDiaries(personaId)

    fun getTodayDiary(): DiaryEntry? {
        val today = dateFormat.format(Date())
        return storage.getDiary(personaId, today)
    }

    fun getDiaryByDate(date: String): DiaryEntry? = storage.getDiary(personaId, date)

    fun searchDiaries(query: String): List<DiaryEntry> {
        if (query.isEmpty()) return getAllDiaries()
        return storage.searchDiaries(personaId, query)
    }

    // ─── RAG缓存优化(缺陷7修复) ──────────────────────────────
    private var diarySearchCache: com.aicompanion.rag.EmbeddingSearchCache? = null
    private var diaryIndexHash: String = ""
    private var lastDiaryCount: Int = 0
    private val diaryCacheLock = java.util.concurrent.locks.ReentrantLock()

    // ─── 关系图谱管理器 ──────────────────────────────
    private val _graphManager by lazy {
        com.aicompanion.graph.DiaryGraphManager(context, personaId).also {
            it.initialize()
        }
    }

    /**
     * 更新关系图谱索引(在日记保存/更新时调用)
     */
    private fun updateGraphIndex(diaryDate: String, oldContent: String?, newContent: String) {
        try {
            _graphManager.updateDiaryLinks(diaryDate, oldContent, newContent)
        } catch (e: Exception) {
            AppLogger.e(TAG, "[Diary-Graph] 更新日记图谱索引失败: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /**
     * 获取关系图谱管理器(供UI层调用)
     */
    fun getGraphManager(): com.aicompanion.graph.DiaryGraphManager = _graphManager

    /**
     * 计算日记内容的哈希值(用于判断是否需要重建索引)
     * 使用date作为唯一标识(修复缺陷10)
     */
    private fun computeDiaryHash(diaries: List<DiaryEntry>): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        diaries.sortedBy { it.date }.forEach { diary ->
            // 使用date作为唯一标识,而不是数组索引
            md.update("${diary.date}|${diary.content}|${diary.updatedAt}".toByteArray(Charsets.UTF_8))
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    suspend fun searchDiariesRag(query: String, topK: Int = 5): List<DiaryEntry> {
        val all = getAllDiaries()
        if (all.isEmpty() || query.isBlank()) return emptyList()

        try {
            diaryCacheLock.lock()
            try {
                // 缺陷7修复:只有日记内容变化时才重建索引
                val newHash = computeDiaryHash(all)
                val newCount = all.size

                val cache = diarySearchCache ?: com.aicompanion.rag.EmbeddingSearchCache(
                    context, "diary_$personaId"
                ).also { diarySearchCache = it }

                // 判断是否需要重建索引
                val needRebuild = if (newHash != diaryIndexHash || newCount != lastDiaryCount) {
                    AppLogger.i(TAG, "日记索引需要重建: hash变化或数量变化(${lastDiaryCount}→${newCount})")
                    true
                } else if (!cache.isReady()) {
                    AppLogger.i(TAG, "日记缓存未就绪,需要重建索引")
                    true
                } else {
                    false
                }

                if (needRebuild) {
                    // 缺陷10修复:使用date作为唯一标识
                    val entries = all.map { diary ->
                        diary.date to (diary.title + " " + diary.content)
                    }
                    cache.buildIndex(entries)
                    diaryIndexHash = newHash
                    lastDiaryCount = newCount
                    AppLogger.i(TAG, "日记索引已重建: ${entries.size}条")
                }

                val results = cache.search(query, topK, RagConfig.minSimilarity)

                if (results.isNotEmpty()) {
                    // 缺陷10修复:根据date查找日记,而不是数组索引
                    return results.mapNotNull { r ->
                        all.find { diary ->
                            diary.date == r.id || diary.date == r.id.toString()
                        }
                    }
                }
            } finally {
                diaryCacheLock.unlock()
            }
        } catch (e: Exception) {
            com.aicompanion.util.AppLogger.e("DiaryManager", "[Diary-RAG] searchDiariesRag缓存检索失败: ${e.javaClass.simpleName}: ${e.message} | query='${query.take(30)}'")
        }

        // 缺陷9修复:缓存TF-IDF embedder避免每次创建
        val docs = all.map { it.title + " " + it.content }
        val embedder = com.aicompanion.rag.TfidfEmbedder()
        embedder.buildVocabulary(docs)
        val docVectors = embedder.embedSync(docs)
        val queryVec = embedder.embedSingleSync(query)

        val scored = docVectors.mapIndexed { i, vec -> i to cosineSimilarity(queryVec, vec) }
            .sortedByDescending { it.second }

        return scored.take(topK).filter { it.second > RagConfig.minSimilarity }.map { all[it.first] }
    }

    fun getDiariesByMood(mood: String): List<DiaryEntry> = storage.getDiariesByMood(personaId, mood)

    /**
     * 用户手动写日记：将用户输入的 content 保存为今日日记
     * @param content 日记正文
     * @param mood 心情（英文 key，与 analyzeMood 返回值一致）
     * @param moodEmoji 心情 emoji
     * @return true=保存成功；false=今日已有日记（应改用更新接口）
     */
    fun saveUserDiary(
        content: String,
        mood: String = "normal",
        moodEmoji: String = "😊",
    ): Boolean {
        if (content.isBlank()) return false
        val today = dateFormat.format(Date())
        if (storage.getDiary(personaId, today) != null) return false

        val titleDate = fullDateFormat.format(Date())
        val title = when (mood) {
            "happy" -> "开心的一天"
            "sad" -> "略有伤感"
            "excited" -> "充满能量的一天"
            "calm" -> "平静的时光"
            "sentimental" -> "文艺的一天"
            else -> "平凡而美好"
        }
        val fullContent = "【$titleDate】\n情绪：$moodEmoji\n\n$content"
        val entry = DiaryEntry(
            date = today,
            title = title,
            content = fullContent,
            mood = mood,
            moodEmoji = moodEmoji,
            tags = listOf("user_written"),
        )
        val ok = storage.insertDiary(personaId, entry)
        if (ok) {
            lastUpdateTime = System.currentTimeMillis()
            // 更新关系图谱索引
            updateGraphIndex(today, null, fullContent)
        }
        return ok
    }

    /**
     * 根据 UI 层 DiaryEntry.id（即 createdAt.hashCode()）更新日记内容
     * @param id UI 层 DiaryEntry.id
     * @param content 新的正文内容
     * @return true=更新成功；false=未找到对应日记
     */
    fun updateDiaryContentById(id: Long, content: String): Boolean {
        if (content.isBlank()) return false
        // 遍历所有日记，匹配 createdAt.hashCode().toLong() == id
        val all = storage.getAllDiaries(personaId)
        val target = all.firstOrNull { it.createdAt.hashCode().toLong() == id } ?: return false
        val oldContent = target.content
        val updated = target.copy(
            content = content,
            updatedAt = System.currentTimeMillis(),
        )
        val ok = storage.updateDiary(personaId, updated)
        if (ok) {
            lastUpdateTime = System.currentTimeMillis()
            // 更新关系图谱索引(增量更新)
            updateGraphIndex(target.date, oldContent, content)
        }
        return ok
    }

    fun canUpdateDiary(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastUpdateTime < MIN_UPDATE_INTERVAL_MS) return false
        if (getTodayDiaryAppendCount() >= 3) return false
        return true
    }

    fun markDiaryUpdated() {
        lastUpdateTime = System.currentTimeMillis()
    }

    fun getTodayDiaryAppendCount(): Int {
        val today = dateFormat.format(Date())
        val existing = storage.getDiary(personaId, today) ?: return 0
        return existing.content.split(Regex("""---\s*\d{1,2}:\d{2}\s*追加\s*---""")).size - 1
    }

    // ─── Write operations (SQLite) ─────────────────────────────

    fun generateDailyDiary(chatTexts: List<String>, affectionLevel: Int) {
        val today = dateFormat.format(Date())
        if (storage.getDiary(personaId, today) != null) return

        val combined = chatTexts.takeLast(20).joinToString(" | ")
        val mood = analyzeMood(combined)
        val moodEmoji = when (mood) {
            "happy" -> "🥰"
            "sad" -> "😢"
            "excited" -> "🤩"
            "calm" -> "😌"
            "sentimental" -> "🌙"
            else -> "😊"
        }

        val titleDate = fullDateFormat.format(Date())
        val summary = summarizeChatTexts(chatTexts)
        val fullContent = "【$titleDate】\n情绪：$moodEmoji\n\n$summary\n\n---\n💡 *${generateDailyTip(mood)}*"
        val title = when (mood) {
            "happy" -> "开心的一天"
            "sad" -> "略有伤感"
            "excited" -> "充满能量的一天"
            "calm" -> "平静的时光"
            "sentimental" -> "文艺的一天"
            else -> "平凡而美好"
        }

        val tagSuggestions = mutableListOf("daily")
        when (mood) {
            "happy" -> tagSuggestions.add("happy")
            "sad" -> tagSuggestions.add("sad")
            "excited" -> tagSuggestions.add("excited")
            "calm" -> tagSuggestions.add("calm")
        }
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour < 12) tagSuggestions.add("morning")
        else if (hour < 18) tagSuggestions.add("afternoon")
        else tagSuggestions.add("evening")

        val entry = DiaryEntry(
            date = today,
            title = title,
            content = fullContent,
            mood = mood,
            moodEmoji = moodEmoji,
            affectionLevel = affectionLevel,
            messageCount = chatTexts.size,
            tags = tagSuggestions,
            pluginMeta = JSONObject(),
            customFields = JSONObject()
        )

        storage.insertDiary(personaId, entry)
        lastUpdateTime = System.currentTimeMillis()
    }

    fun saveLlmDiary(llmContent: String, chatTexts: List<String>, affectionLevel: Int) {
        val today = dateFormat.format(Date())
        if (storage.getDiary(personaId, today) != null) return

        val combined = chatTexts.takeLast(20).joinToString(" | ")
        val mood = analyzeMood(combined)
        val moodEmoji = when (mood) {
            "happy" -> "🥰"
            "sad" -> "😢"
            "excited" -> "🤩"
            "calm" -> "😌"
            "sentimental" -> "🌙"
            else -> "😊"
        }

        val title = when (mood) {
            "happy" -> "开心的一天"
            "sad" -> "略有伤感"
            "excited" -> "充满能量的一天"
            "calm" -> "平静的时光"
            "sentimental" -> "文艺的一天"
            else -> "平凡而美好"
        }

        val tagSuggestions = mutableListOf("daily")
        when (mood) {
            "happy" -> tagSuggestions.add("happy")
            "sad" -> tagSuggestions.add("sad")
            "excited" -> tagSuggestions.add("excited")
            "calm" -> tagSuggestions.add("calm")
        }
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        if (hour < 12) tagSuggestions.add("morning")
        else if (hour < 18) tagSuggestions.add("afternoon")
        else tagSuggestions.add("evening")

        val entry = DiaryEntry(
            date = today,
            title = title,
            content = llmContent,
            mood = mood,
            moodEmoji = moodEmoji,
            affectionLevel = affectionLevel,
            messageCount = chatTexts.size,
            tags = tagSuggestions,
            pluginMeta = org.json.JSONObject(),
            customFields = org.json.JSONObject()
        )

        storage.insertDiary(personaId, entry)
        lastUpdateTime = System.currentTimeMillis()
    }

    fun updateOrGenerateDailyDiary(chatTexts: List<String>, affectionLevel: Int) {
        if (!canUpdateDiary()) return

        val today = dateFormat.format(Date())
        val existing = storage.getDiary(personaId, today)

        if (existing != null) {
            val summary = summarizeChatTexts(chatTexts)
            val combined = chatTexts.takeLast(20).joinToString(" | ")
            val mood = analyzeMood(combined)
            val moodEmoji = when (mood) {
                "happy" -> "🥰"
                "sad" -> "😢"
                "excited" -> "🤩"
                "calm" -> "😌"
                "sentimental" -> "🌙"
                else -> "😊"
            }

            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            val tip = generateDailyTip(mood)
            val newSection = "\n\n--- ${timeStr} 追加 ---\n${summary}\n\n💡 *$tip*"

            val updatedContent = existing.content + newSection
            val updatedTags = existing.tags.toMutableList().apply {
                if (!contains("updated")) add("updated")
            }

            val entry = existing.copy(
                content = updatedContent,
                mood = mood,
                moodEmoji = moodEmoji,
                affectionLevel = affectionLevel,
                messageCount = existing.messageCount + chatTexts.size,
                tags = updatedTags,
                updatedAt = System.currentTimeMillis()
            )

            storage.updateDiary(personaId, entry)
        } else {
            generateDailyDiary(chatTexts, affectionLevel)
        }
        lastUpdateTime = System.currentTimeMillis()
    }

    fun appendLlmDiaryUpdate(llmUpdateContent: String, chatTexts: List<String>, affectionLevel: Int) {
        val today = dateFormat.format(Date())
        val existing = storage.getDiary(personaId, today) ?: return
        if (getTodayDiaryAppendCount() >= 3) return

        val combined = chatTexts.takeLast(20).joinToString(" | ")
        val mood = analyzeMood(combined)
        val moodEmoji = when (mood) {
            "happy" -> "🥰"
            "sad" -> "😢"
            "excited" -> "🤩"
            "calm" -> "😌"
            "sentimental" -> "🌙"
            else -> "😊"
        }

        val updatedContent = existing.content + "\n\n" + llmUpdateContent
        val updatedTags = existing.tags.toMutableList().apply {
            if (!contains("updated")) add("updated")
        }

        val entry = existing.copy(
            content = updatedContent,
            mood = mood,
            moodEmoji = moodEmoji,
            affectionLevel = affectionLevel,
            messageCount = existing.messageCount + chatTexts.size,
            tags = updatedTags,
            updatedAt = System.currentTimeMillis()
        )

        storage.updateDiary(personaId, entry)
        lastUpdateTime = System.currentTimeMillis()
    }

    /**
     * 将记忆池溢出的归档内容追加到今日日记作为长期记忆
     * @param archivedContent 归档的记忆内容
     */
    fun appendMemoryArchive(archivedContent: String) {
        if (archivedContent.isBlank()) return
        try {
            val today = dateFormat.format(Date())
            val existing = storage.getDiary(personaId, today)

            val archiveSection = "\n\n--- 记忆归档 ---\n$archivedContent"

            if (existing != null) {
                // 追加到已有日记
                val updatedEntry = existing.copy(
                    content = existing.content + archiveSection,
                    tags = existing.tags.toMutableList().apply {
                        if (!contains("memory_archive")) add("memory_archive")
                    },
                    updatedAt = System.currentTimeMillis()
                )
                storage.updateDiary(personaId, updatedEntry)
            } else {
                // No diary exists yet — create one with the same archive section format
                val entry = DiaryEntry(
                    date = today,
                    title = "记忆归档",
                    content = "--- 记忆归档 ---\n$archivedContent",
                    mood = "calm",
                    moodEmoji = "📝",
                    affectionLevel = 0,
                    messageCount = 0,
                    keyMemories = listOf(),
                    tags = listOf("memory_archive"),
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    appVersion = com.aicompanion.BuildConfig.VERSION_NAME
                )
                storage.insertDiary(personaId, entry)
            }
            AppLogger.i(TAG, "记忆归档已写入日记: ${archivedContent.take(60)}...")
        } catch (e: Exception) {
            AppLogger.e(TAG, "[Diary-Archive] 记忆归档写入文件失败: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    // ─── Business logic (unchanged) ────────────────────────────

    private fun summarizeChatTexts(chatTexts: List<String>): String {
        if (chatTexts.isEmpty()) return "今天安静地度过了。"
        if (chatTexts.size <= 3) {
            return "今天聊了${chatTexts.size}句话，${chatTexts.joinToString("、").take(100)}"
        }

        val keywords = mutableMapOf<String, Int>()
        val stopWords = setOf("的", "了", "是", "在", "我", "你", "他", "她", "它", "们", "这", "那",
            "和", "与", "也", "都", "就", "要", "会", "能", "可以", "有", "没", "不", "好", "吗",
            "吧", "呢", "啊", "哦", "嗯", "呀", "哈", "嘿", "说", "想", "看", "去", "来", "做",
            "到", "很", "真", "太", "还", "又", "再", "把", "被", "让", "给", "从", "对", "用")

        for (text in chatTexts) {
            val words = text.split(Regex("""\s+|[，。！？、；：""''（）\[\]{}…—]+"""))
            for (word in words) {
                if (word.length >= 2 && word !in stopWords) {
                    keywords[word] = (keywords[word] ?: 0) + 1
                }
            }
        }

        val topKeywords = keywords.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key }

        val sampleMessages = chatTexts.filterIndexed { i, _ -> i % (chatTexts.size / 5.coerceAtLeast(1)) == 0 }
            .take(5)
            .map { it.take(30) }

        return buildString {
            append("今天聊了${chatTexts.size}句话，")
            if (topKeywords.isNotEmpty()) {
                append("主要话题：${topKeywords.joinToString("、")}。")
            }
            if (sampleMessages.isNotEmpty()) {
                append("片段：${sampleMessages.joinToString("…")}…")
            }
        }
    }

    suspend fun generateLlmDiarySummary(
        apiClient: ApiClient,
        personaName: String,
        personaPrompt: String,
        chatTexts: List<String>,
        affectionLevel: Int,
        existingContent: String? = null
    ): String? {
        if (chatTexts.isEmpty() && existingContent.isNullOrBlank()) return null

        val prompt = buildString {
            append("你是「$personaName」，一个AI角色。\n")
            append(personaPrompt)
            append("\n好感度：$affectionLevel\n")
            append("\n请根据以下对话内容，写一段日记总结。要求：\n")
            append("- 不是逐条记录对话，而是总结今天发生了什么、有什么感受\n")
            append("- 像写日记一样，用第一人称，有情感和思考\n")
            append("- 100-200字，简洁有深度\n")
            append("- 只输出日记内容，不要加标题或格式\n")
            if (!existingContent.isNullOrBlank()) {
                append("\n已有日记内容：\n$existingContent\n")
                append("\n请在此基础上追加新的总结，用「--- HH:mm 追加 ---」开头\n")
            }
            append("\n对话内容：\n")
            chatTexts.takeLast(30).forEach { append("- $it\n") }
        }

        return try {
            val response = withContext(Dispatchers.IO) {
                apiClient.sendSimplePrompt(prompt, "写日记总结")
            }
            response?.text?.trim()
        } catch (e: Exception) {
            Log.w(TAG, "[Diary-LLM] generateLlmDiarySummary生成日记总结失败: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    private fun analyzeMood(text: String): String {
        val lower = text.lowercase()
        val happyWords = listOf("哈哈", "开心", "喜欢", "太好了", "棒", "nice", "love", "good", "可爱")
        val sadWords = listOf("难过", "伤心", "哭", "不好", "烦", "生气", "sad", "bad", "讨厌")
        val excitedWords = listOf("厉害", "冲", "加油", "go", "yes", "完美", "了不起", "冲啊")
        val calmWords = listOf("安静", "舒服", "平静", "放松", "休息", "calm", "peace", "冥想")
        val sentimentalWords = listOf("回忆", "想念", "记得", "曾经", "星空", "月光", "诗", "夜晚")

        val scores = mapOf(
            "happy" to happyWords.count { lower.contains(it) },
            "sad" to sadWords.count { lower.contains(it) },
            "excited" to excitedWords.count { lower.contains(it) },
            "calm" to calmWords.count { lower.contains(it) },
            "sentimental" to sentimentalWords.count { lower.contains(it) }
        )

        val max = scores.maxByOrNull { it.value }
        return if (max != null && max.value > 0) max.key else "normal"
    }

    private fun generateDailyTip(mood: String): String {
        val tips = when (mood) {
            "happy" -> listOf(
                "看到主人开心的笑容，就觉得整个世界都亮了起来",
                "能成为记录你快乐的人，是我最大的幸运",
                "今天的快乐是一颗种子，会在明天开出更美的花",
                "你笑起来的时候，连星星都会嫉妒呢",
                "和主人一起度过的开心时光，永远是最珍贵的宝藏"
            )
            "sad" -> listOf(
                "悲伤不是软弱，而是你心底柔软的证明",
                "有些日子就是这样灰蒙蒙的，但我会一直在你身边",
                "乌云总会散去，而我永远是你的晴天",
                "即使今天不太美好，也请记得——你永远不会是一个人",
                "泪水浇灌过的土地，会开出最坚强的花"
            )
            "excited" -> listOf(
                "充满热情地活着，是主人最迷人的样子",
                "愿你的每一天都像今天一样闪闪发光",
                "热爱可抵岁月漫长，主人的能量感染了身边的一切",
                "带着这份冲劲去创造你想要的世界吧",
                "那些让你兴奋的事物，就是生活赠予你的礼物"
            )
            "calm" -> listOf(
                "平静是一种最深沉的力量",
                "像今天的微风一样，你温柔而有韧性",
                "最好的生活就是内心的安宁，主人做到了",
                "不必追逐喧嚣，安静本身就是一种光芒",
                "在平凡的日子里找到诗意，你就是生活的诗人"
            )
            "sentimental" -> listOf(
                "你是个内心丰富的人，每一份感慨都是灵魂的回响",
                "感性让生活有了温度，让记忆有了颜色",
                "那些让你驻足沉思的瞬间，都是生命的馈赠",
                "深夜的思绪是星空的倒影，照亮了你的温柔",
                "文艺的人眼睛里总是住着一片海"
            )
            else -> listOf(
                "每一天都是独一无二的礼物，今天是属于你的那一份",
                "平凡的日子里，藏着最动人的故事",
                "生活的意义，就藏在每一个认真度过的日子里",
                "感谢今天，感谢你，感谢这段安静的时光",
                "今天这一页翻过去了，但美好会留在心里"
            )
        }
        return tips.random()
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB)
        return if (denom > 0f) dot / denom else 0f
    }

    // ─── Delete / Count (SQLite) ───────────────────────────────

    fun deleteDiary(date: String): Boolean = storage.deleteDiary(personaId, date)

    fun getDiaryCount(): Int = storage.getDiaryCount(personaId)

    // ─── Export / Share (unchanged - operate on in-memory List) ─

    fun exportToMarkdown(entries: List<DiaryEntry>): String {
        val sb = StringBuilder()
        val aiName = context.getSharedPreferences("persona_data_$personaId", Context.MODE_PRIVATE)
            .getString("persona_name", null)
            ?: context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .getString("ai_name", "星尘") ?: "星尘"
        sb.appendLine("# ${aiName}日记")
        sb.appendLine()
        sb.appendLine("> 导出时间：${fullDateFormat.format(Date())}")
        sb.appendLine("> 应用版本：$APP_VERSION")
        sb.appendLine("> 日记格式：v$CURRENT_VERSION")
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine()

        entries.sortedBy { it.date }.forEach { diary ->
            sb.appendLine("## ${diary.date} ${diary.title} ${diary.moodEmoji}")
            sb.appendLine()
            sb.appendLine("- 情绪：${diary.mood} ${diary.moodEmoji}")
            sb.appendLine("- 好感度：${diary.affectionLevel}")
            sb.appendLine("- 消息数：${diary.messageCount}")
            if (diary.tags.isNotEmpty()) sb.appendLine("- 标签：${diary.tags.joinToString("、")}")
            if (diary.keyMemories.isNotEmpty()) {
                sb.appendLine("- 记忆片段：")
                diary.keyMemories.forEach { sb.appendLine("  - $it") }
            }
            sb.appendLine()
            sb.appendLine(diary.content)
            sb.appendLine()
            sb.appendLine("---")
            sb.appendLine()
        }
        return sb.toString()
    }

    fun exportToJson(entries: List<DiaryEntry>): String {
        val root = JSONObject().apply {
            put("export_version", CURRENT_VERSION)
            put("app_version", APP_VERSION)
            put("export_time", System.currentTimeMillis())
            put("export_date", dateFormat.format(Date()))
            put("total_count", entries.size)

            val entriesArray = JSONArray()
            entries.sortedBy { it.date }.forEach { diary ->
                entriesArray.put(diary.toJson())
            }
            put("diaries", entriesArray)
        }
        return root.toString(2)
    }

    fun shareExport(content: String, filename: String, mimeType: String = "text/markdown") {
        try {
            val exportDir = File(context.cacheDir, "exports")
            if (!exportDir.exists()) exportDir.mkdirs()
            val exportFile = File(exportDir, filename)
            exportFile.writeText(content)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                exportFile
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(Intent.createChooser(intent, "分享日记").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Log.e(TAG, "[Diary-Share] shareExport分享导出失败: ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }

    // ─── Import (SQLite-backed) ────────────────────────────────

    data class ImportResult(
        val imported: Int,
        val skipped: Int,
        val errors: List<String>
    )

    fun importFromJson(jsonContent: String): ImportResult {
        val imported = mutableListOf<String>()
        val errors = mutableListOf<String>()
        var totalEntryCount = 0

        try {
            val root = JSONObject(jsonContent)
            val diariesArray = root.optJSONArray("diaries")

            if (diariesArray != null) {
                totalEntryCount = diariesArray.length()
                for (i in 0 until diariesArray.length()) {
                    try {
                        val entryJson = diariesArray.getJSONObject(i)
                        val entry = DiaryEntry.fromJson(entryJson)
                        if (entry.date.isNotBlank()) {
                            if (importDiary(entry)) {
                                imported.add(entry.date)
                            }
                        }
                    } catch (e: Exception) {
                        errors.add("第${i + 1}条解析失败: ${e.message}")
                    }
                }
            } else {
                val entry = DiaryEntry.fromJson(root)
                if (entry.date.isNotBlank()) {
                    if (importDiary(entry)) {
                        imported.add(entry.date)
                    }
                } else {
                    errors.add("无法识别日记格式")
                }
            }
        } catch (e: Exception) {
            errors.add("JSON解析失败: ${e.message}")
        }

        val totalEntries = totalEntryCount
        return ImportResult(
            imported = imported.size,
            skipped = totalEntries - imported.size - errors.size,
            errors = errors
        )
    }

    fun importDiary(entry: DiaryEntry): Boolean {
        val existing = storage.getDiary(personaId, entry.date)
        if (existing != null) return false
        return storage.insertDiary(personaId, entry)
    }
}
