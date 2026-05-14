/** 日记管理器: 根据聊天记录AI自动生成每日总结, 支持多种触发模式(手动/每小时/每2小时/每50条/每日22点) */
package com.aicompanion.diary

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class DiaryManager(private val context: Context) {

    companion object {
        const val CURRENT_VERSION = 2
        val APP_VERSION = "1.0.0"
        private const val INDEX_FILE = "diary_index.json"
        private const val TAG = "DiaryManager"
    }

    private val diaryDir = File(context.filesDir, "diaries")
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val fullDateFormat = SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINESE)

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
        } catch (_: Exception) {}
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
            } catch (_: Exception) {}
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

    fun getDiariesByMood(mood: String): List<DiaryEntry> {
        return getAllDiaries().filter { it.mood == mood }
    }

    fun generateDailyDiary(chatTexts: List<String>, affectionLevel: Int) {
        val today = dateFormat.format(Date())
        if (getDiaryByDate(today) != null) return

        val combined = chatTexts.joinToString(" | ")
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
        val fullContent = "【$titleDate】\n情绪：$moodEmoji\n\n今天和主人一起度过了一段时光，虽然只是平凡的日常，但每一刻都值得珍惜。陪伴是最长情的告白，我会一直在这里。\n\n---\n💡 *每一天都是独一无二的礼物，今天是属于你的那一份*"
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
    }

    fun saveLlmDiary(llmContent: String, chatTexts: List<String>, affectionLevel: Int) {
        val today = dateFormat.format(Date())
        if (getDiaryByDate(today) != null) return

        val combined = chatTexts.joinToString(" | ")
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
    }

    fun updateOrGenerateDailyDiary(chatTexts: List<String>, affectionLevel: Int) {
        val today = dateFormat.format(Date())
        val existing = getDiaryByDate(today)

        if (existing != null) {
            val combined = chatTexts.joinToString(" | ")
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
            val tip = "每一天都是独一无二的礼物，今天是属于你的那一份"
            val newSection = "\n\n--- ${timeStr} 追加 ---\n新对话摘要：${combined}\n\n💡 *$tip*"

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
    }

    fun appendLlmDiaryUpdate(llmUpdateContent: String, chatTexts: List<String>, affectionLevel: Int) {
        val today = dateFormat.format(Date())
        val existing = getDiaryByDate(today) ?: return

        val combined = chatTexts.joinToString(" | ")
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
        sb.appendLine("# 星尘日记")
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
            android.util.Log.e(TAG, "shareExport failed: ${e.message}", e)
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
            } catch (_: Exception) {}
        }
        writeIndex(index)
    }
}