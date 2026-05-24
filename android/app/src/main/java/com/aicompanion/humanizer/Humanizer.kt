package com.aicompanion.humanizer

import kotlin.random.Random
import com.aicompanion.util.AppLogger

class Humanizer {

    companion object {
        private val THINKING_PREFIXES = listOf(
            "嗯…", "让我想想…", "emmm…", "唔…", "这个嘛…"
        )

        private val TYPO_PATTERNS = listOf(
            Triple("明天早上", "哦不，是明天下午", 0.04f),
            Triple("我觉得", "不对，我重新想一下…", 0.03f),
            Triple("那个", "呃不对", 0.04f),
            Triple("大概", "哦等一下，确切说是", 0.03f)
        )

        private val IDLE_INSERTIONS = listOf(
            "说起来…你今天过得怎么样呀？",
            "诶，我突然想到一个事…",
            "噗", "😂", "(´･ω･`)", "✨",
            "对了对了！",
            "啊对了～"
        )

        private val SENTENCE_ENDINGS = listOf("。", "！", "～", "…", "呢", "哦", "呐", "啦")
    }

    data class HumanizedChunk(
        val text: String,
        val delayMs: Long,
        val isThinking: Boolean = false
    )

    fun humanize(rawText: String, isComplexQuestion: Boolean = false): List<HumanizedChunk> {
        val chunks = mutableListOf<HumanizedChunk>()

        if (rawText.isBlank()) {
            AppLogger.w("Humanizer", "humanize: blank input, len=${rawText.length}")
            return chunks
        }

        // Step 1: Parse out <pause=Xms> tags
        val (processedText, pauseSegments) = parsePauseTags(rawText)

        // Step 2: Complex question → thinking prefix
        if (isComplexQuestion && Random.nextFloat() < 0.6f) {
            val prefix = THINKING_PREFIXES.random()
            chunks.add(HumanizedChunk(prefix, 800 + Random.nextLong(600), isThinking = true))
        }

        // Step 3: Split processed text into sentences
        val sentences = splitSentences(processedText)

        // Step 4: Occasionally add typos + corrections
        val typoChunks = if (sentences.size >= 2 && Random.nextFloat() < 0.06f) {
            maybeInjectTypo(sentences)
        } else {
            sentences
        }

        // Step 5: Build chunks with natural delays
        for (i in typoChunks.indices) {
            val sentence = typoChunks[i]
            val isLast = i == typoChunks.lastIndex
            val isShort = sentence.length < 8

            // Use <pause> timing if available, otherwise natural delay
            val delayMs = pauseSegments.getOrElse(i) {
                when {
                    isShort -> 400L + Random.nextLong(300)
                    isLast -> 300L + Random.nextLong(200)
                    sentence.endsWith("…") -> 900L + Random.nextLong(400)
                    sentences.size == 1 -> 0L
                    else -> 600L + Random.nextLong(500)
                }
            }

            // For the first chunk after thinking, no additional delay
            val actualDelay = if (chunks.isEmpty() || chunks.last().isThinking) 200L + Random.nextLong(200) else delayMs

            chunks.add(HumanizedChunk(sentence, actualDelay))
        }

        // Step 6: Occasionally (rarely) add an idle insertion at the end
        if (Random.nextFloat() < 0.03f) {
            val idle = IDLE_INSERTIONS.random()
            chunks.add(HumanizedChunk(idle, 1500L + Random.nextLong(800)))
        }

        if (chunks.isEmpty()) {
            AppLogger.w("Humanizer", "humanize: empty result, rawLen=${rawText.length}")
        }

        return chunks
    }

    private fun splitSentences(text: String): List<String> {
        val result = mutableListOf<String>()

        // Split on sentence-ending punctuation but keep it attached
        val pattern = Regex("""(.+?[。！？\n～…])(?=\s*[^\s]|\s*$)""")
        val matches = pattern.findAll(text).toList()

        if (matches.isNotEmpty()) {
            for (match in matches) {
                val s = match.groupValues[1].trim()
                if (s.isNotBlank()) result.add(s)
            }
        }

        // Fallback: long comma splitting
        if (result.isEmpty() && text.length > 20) {
            val segments = text.split(Regex("(?<=，)|(?<=,)")).toMutableList()
            // Merge segments into groups of reasonable length
            val merged = mutableListOf<String>()
            var current = ""
            for (seg in segments) {
                if (current.length + seg.length < 15) {
                    current += seg
                } else {
                    if (current.isNotBlank()) merged.add(current.trim())
                    current = seg
                }
            }
            if (current.isNotBlank()) merged.add(current.trim())
            return merged
        }

        if (result.isEmpty()) {
            AppLogger.w("Humanizer", "splitSentences: empty, textLen=${text.length}")
            result.add(text)
        }
        return result
    }

    private fun parsePauseTags(text: String): Pair<String, Map<Int, Long>> {
        val pausePattern = Regex("""<pause=(\d+)ms>""")
        val pauses = mutableMapOf<Int, Long>()
        var sentenceIndex = 0
        var cleaned = text

        pausePattern.findAll(text).forEach { match ->
            pauses[sentenceIndex] = match.groupValues[1].toLongOrNull() ?: 500
            sentenceIndex++
            cleaned = cleaned.replaceFirst(match.value, "")
        }

        return cleaned to pauses
    }

    private fun maybeInjectTypo(sentences: List<String>): List<String> {
        val index = Random.nextInt(0, sentences.size.coerceAtMost(2))
        val sentence = sentences[index]
        val (wrong, correct, _) = TYPO_PATTERNS.random()

        val modified = sentence.replaceFirst(wrong, "${wrong}${correct}对吧？")
        return sentences.toMutableList().also { it[index] = modified }
    }

    fun isComplexQuestion(text: String): Boolean {
        val complexKeywords = listOf(
            "为什么", "怎么", "如何", "能不能", "可以吗",
            "是什么", "区别", "比较", "推荐", "建议",
            "分析", "解释", "原理", "方法", "步骤"
        )
        val lower = text.lowercase()
        val matchCount = complexKeywords.count { lower.contains(it) }
        return matchCount >= 1 && text.length > 10
    }

    fun getThinkingPrefix(): String = THINKING_PREFIXES.random()

    fun getRandomIdleMsg(): String = IDLE_INSERTIONS.random()
}