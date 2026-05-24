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
        private const val INDEX_FILE = "diary_index.json"
        private const val TAG = "DiaryManager"
        private const val MIN_UPDATE_INTERVAL_MS = 30 * 60 * 1000L
    }

    private val diaryDir = File(File(context.filesDir, "diaries"), personaId).apply { mkdirs() }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val fullDateFormat = SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINESE)
    private var lastUpdateTime = 0L

    init {
        if (!diaryDir.exists()) diaryDir.mkdirs()
    }

    private fun getIndexFile(): File = File(diaryDir, INDEX_FILE)

    private fun readIndex(): JSONObject {
        val idxFile = getIndexFile()
        return if (idxFile.exists()) {
            try { JSONObject(idxFile.readText()) } catch (_: Exception) { JSONObject() }
        } else {
            JSONObject()
        }
    }

    private fun writeIndex(index: JSONObject) {
        try {
            getIndexFile().writeText(index.toString(2))
        } catch (e: Exception) { AppLogger.e("DiaryManager", "writeIndex: ${e.message}") }
    }

    private fun updateIndex(entry: DiaryEntry) {
        val index = readIndex()
        val idxObj = JSONObject().apply {
            put("title", entry.title)
            put("mood", entry.mood)
            put("mood_emoji", entry.moodEmoji)
            put("affection_level", entry.affectionLevel)
            put("message_count", entry.messageCount)
            put("created_at", entry.createdAt)
            put("version", entry.version)
        }
        index.put(entry.date, idxObj)
        writeIndex(index)
    }

    fun getAllDiaries(): List<DiaryEntry> {
        val diaries = mutableListOf<DiaryEntry>()
        diaryDir.listFiles()?.filter { it.isFile && it.extension == "json" && it.name != INDEX_FILE }?.forEach { file ->
            try {
                val json = JSONObject(file.readText())
                diaries.add(DiaryEntry.fromJson(json))
            } catch (e: Exception) { AppLogger.e("DiaryManager", "getAllDiaries: ${e.message}") }
        }
        return diaries.sortedByDescending { it.date }
    }

    fun getTodayDiary(): DiaryEntry? {
        val today = dateFormat.format(Date())
        return getDiaryByDate(today)
    }

    fun getDiaryByDate(date: String): DiaryEntry? {
        val file = File(diaryDir, "$date.json")
        if (!file.exists()) return null
        return try {
            DiaryEntry.fromJson(JSONObject(file.readText()))
        } catch (_: Exception) { null }
    }

    fun searchDiaries(query: String): List<DiaryEntry> {
        if (query.isEmpty()) return getAllDiaries()
        val lower = query.lowercase()
        return getAllDiaries().filter {
            it.title.lowercase().contains(lower) || it.content.lowercase().contains(lower)
        }
    }

    fun searchDiariesRag(query: String, topK: Int = 5): List<DiaryEntry> {
        val all = getAllDiaries()
        if (all.isEmpty() || query.isBlank()) return emptyList()

        val docs = all.map { it.title + " " + it.content }
        val embedder = com.aicompanion.rag.TfidfEmbedder()
        embedder.buildVocabulary(docs)
        val docVectors = embedder.embedSync(docs)
        val queryVec = embedder.embedSingleSync(query)

        val scored = docVectors.mapIndexed { i, vec -> i to cosineSimilarity(queryVec, vec) }
            .sortedByDescending { it.second }

        return scored.take(topK).filter { it.second > RagConfig.minSimilarity }.map { all[it.first] }
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

    fun getDiariesByMood(mood: String): List<DiaryEntry> {
        return getAllDiaries().filter { it.mood == mood }
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
        val existing = getDiaryByDate(today) ?: return 0
        return existing.content.split(Regex("""---\s*\d{1,2}:\d{2}\s*追加\s*---""")).size - 1
    }

    fun generateDailyDiary(chatTexts: List<String>, affectionLevel: Int) {
        val today = dateFormat.format(Date())
        if (getDiaryByDate(today) != null) return

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

        val file = File(diaryDir, "$today.json")
        file.writeText(entry.toJson().toString(2))
        updateIndex(entry)
        lastUpdateTime = System.currentTimeMillis()
    }

    fun saveLlmDiary(llmContent: String, chatTexts: List<String>, affectionLevel: Int) {
        val today = dateFormat.format(Date())
        if (getDiaryByDate(today) != null) return

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

        val file = File(diaryDir, "$today.json")
        file.writeText(entry.toJson().toString(2))
        updateIndex(entry)
        lastUpdateTime = System.currentTimeMillis()
    }

    fun updateOrGenerateDailyDiary(chatTexts: List<String>, affectionLevel: Int) {
        if (!canUpdateDiary()) return

        val today = dateFormat.format(Date())
        val existing = getDiaryByDate(today)

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

            val file = File(diaryDir, "$today.json")
            file.writeText(entry.toJson().toString(2))
            updateIndex(entry)
        } else {
            generateDailyDiary(chatTexts, affectionLevel)
        }
        lastUpdateTime = System.currentTimeMillis()
    }

    fun appendLlmDiaryUpdate(llmUpdateContent: String, chatTexts: List<String>, affectionLevel: Int) {
        val today = dateFormat.format(Date())
        val existing = getDiaryByDate(today) ?: return
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

        val file = File(diaryDir, "$today.json")
        file.writeText(entry.toJson().toString(2))
        updateIndex(entry)
        lastUpdateTime = System.currentTimeMillis()
    }

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
            Log.w(TAG, "generateLlmDiarySummary failed: ${e.message}")
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

    fun deleteDiary(date: String): Boolean {
        val file = File(diaryDir, "$date.json")
        val deleted = file.delete()
        if (deleted) {
            val index = readIndex()
            index.remove(date)
            writeIndex(index)
        }
        return deleted
    }

    fun getDiaryCount(): Int = diaryDir.listFiles()?.count { it.isFile && it.extension == "json" && it.name != INDEX_FILE } ?: 0

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
            Log.e(TAG, "shareExport failed: ${e.message}", e)
        }
    }

    data class ImportResult(
        val imported: Int,
        val skipped: Int,
        val errors: List<String>
    )

    fun importFromJson(jsonContent: String): ImportResult {
        val imported = mutableListOf<String>()
        val errors = mutableListOf<String>()

        try {
            val root = JSONObject(jsonContent)
            val diariesArray = root.optJSONArray("diaries")

            if (diariesArray != null) {
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

            rebuildIndex()
        } catch (e: Exception) {
            errors.add("JSON解析失败: ${e.message}")
        }

        return ImportResult(
            imported = imported.size,
            skipped = (imported.size + errors.size) - imported.size,
            errors = errors
        )
    }

    fun importDiary(entry: DiaryEntry): Boolean {
        val file = File(diaryDir, "${entry.date}.json")
        if (file.exists()) return false
        return try {
            file.writeText(entry.toJson().toString(2))
            true
        } catch (_: Exception) { false }
    }

    private fun rebuildIndex() {
        val index = JSONObject()
        diaryDir.listFiles()?.filter { it.isFile && it.extension == "json" && it.name != INDEX_FILE }?.forEach { file ->
            try {
                val json = JSONObject(file.readText())
                val date = json.optString("date", "")
                if (date.isNotBlank()) {
                    val idxObj = JSONObject().apply {
                        put("title", json.optString("title", ""))
                        put("mood", json.optString("mood", "normal"))
                        put("mood_emoji", json.optString("mood_emoji", "😊"))
                        put("affection_level", json.optInt("affection_level", 0))
                        put("message_count", json.optInt("message_count", 0))
                        put("created_at", json.optLong("created_at", 0))
                        put("version", json.optInt("version", 1))
                    }
                    index.put(date, idxObj)
                }
            } catch (e: Exception) { AppLogger.e("DiaryManager", "rebuildIndex: ${e.message}") }
        }
        writeIndex(index)
    }
}
