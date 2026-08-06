package com.aicompanion.rag

/**
 * 文本分块器
 *
 * 除了按段落/句子切分长文本外,还会从每个分块中提取 [[链接文本]] 形式的超链接,
 * 用于树结构 RAG 的上下文扩展(检索时沿链接跳转到相关分块)。
 *
 * 链接格式: [[任意文本]] (不支持嵌套),与 Obsidian Wikilink 语法兼容。
 * 链接文本本身会保留在分块内容中(供 LLM 阅读),同时被记录到 chunk.links。
 */
class TextChunker(
    private val maxChars: Int = RagConfig.chunkMaxChars,
    private val overlapChars: Int = RagConfig.chunkOverlapChars
) {

    data class Chunk(
        val index: Int,
        val text: String,
        val sourceField: String = "",
        /** 从该分块中提取到的 [[链接]] 文本列表(已去重) */
        val links: List<String> = emptyList()
    )

    /**
     * [[链接]] 提取正则
     * - 匹配 [[后跟非 ] 的字符,以 ]] 结尾
     * - 支持 [[目标|别名]] 形式,提取时取"目标"部分
     */
    private val linkPattern = Regex("\\[\\[([^\\]|\\]]+)(?:\\|[^\\]]+)?\\]\\]")

    /**
     * 从文本中提取所有 [[链接]] 的目标文本(去重,保持顺序)
     */
    private fun extractLinks(text: String): List<String> {
        return linkPattern.findAll(text)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
    }

    fun chunkPersona(fields: Map<String, String>): List<Chunk> {
        val chunks = mutableListOf<Chunk>()
        var globalIndex = 0

        for ((fieldName, text) in fields) {
            if (text.isBlank()) continue

            if (text.length <= maxChars) {
                val trimmed = text.trim()
                chunks.add(Chunk(globalIndex++, trimmed, fieldName, extractLinks(trimmed)))
            } else {
                val subChunks = splitLongText(text.trim(), fieldName, globalIndex)
                chunks.addAll(subChunks)
                globalIndex += subChunks.size
            }
        }

        return chunks
    }

    fun chunkText(text: String, sourceLabel: String = ""): List<Chunk> {
        if (text.isBlank()) return emptyList()
        if (text.length <= maxChars) {
            val trimmed = text.trim()
            return listOf(Chunk(0, trimmed, sourceLabel, extractLinks(trimmed)))
        }

        return splitLongText(text.trim(), sourceLabel, 0)
    }

    private fun splitLongText(text: String, fieldName: String, startIndex: Int): List<Chunk> {
        val chunks = mutableListOf<Chunk>()
        val paragraphs = text.split(Regex("\\n+")).filter { it.isNotBlank() }

        var current = StringBuilder()
        var localIndex = startIndex

        for (para in paragraphs) {
            if (current.length + para.length > maxChars && current.isNotEmpty()) {
                val chunkText = current.toString().trim()
                chunks.add(Chunk(localIndex++, chunkText, fieldName, extractLinks(chunkText)))
                val overlap = buildOverlap(current.toString(), overlapChars)
                current = StringBuilder(overlap)
            }

            if (para.length > maxChars) {
                if (current.isNotEmpty()) {
                    val chunkText = current.toString().trim()
                    chunks.add(Chunk(localIndex++, chunkText, fieldName, extractLinks(chunkText)))
                    current = StringBuilder()
                }
                val subChunks = splitLongSentence(para, fieldName, localIndex)
                chunks.addAll(subChunks)
                localIndex += subChunks.size
                current = StringBuilder(buildOverlap(subChunks.last().text, overlapChars))
            } else {
                if (current.isNotEmpty()) current.append("\n")
                current.append(para)
            }
        }

        if (current.isNotBlank()) {
            val chunkText = current.toString().trim()
            chunks.add(Chunk(localIndex++, chunkText, fieldName, extractLinks(chunkText)))
        }

        return chunks
    }

    private fun splitLongSentence(text: String, fieldName: String, startIndex: Int): List<Chunk> {
        val chunks = mutableListOf<Chunk>()
        val sentences = text.split(Regex("(?<=[。！？；])|(?<=[，,])")).filter { it.isNotBlank() }

        var current = StringBuilder()
        var idx = startIndex

        for (sentence in sentences) {
            if (current.length + sentence.length > maxChars && current.isNotEmpty()) {
                val chunkText = current.toString().trim()
                chunks.add(Chunk(idx++, chunkText, fieldName, extractLinks(chunkText)))
                val overlap = buildOverlap(current.toString(), overlapChars / 2)
                current = StringBuilder(overlap)
            }
            current.append(sentence)
        }

        if (current.isNotBlank()) {
            val chunkText = current.toString().trim()
            chunks.add(Chunk(idx++, chunkText, fieldName, extractLinks(chunkText)))
        }

        if (chunks.isEmpty()) {
            // 确保 step > 0,避免死循环(当 maxChars <= overlapChars 时)
            val step = (maxChars - overlapChars).coerceAtLeast(1)
            var pos = 0
            while (pos < text.length) {
                val end = (pos + maxChars).coerceAtMost(text.length)
                val chunkText = text.substring(pos, end)
                chunks.add(Chunk(idx++, chunkText, fieldName, extractLinks(chunkText)))
                pos += step
            }
        }

        return chunks
    }

    private fun buildOverlap(text: String, chars: Int): String {
        if (text.length <= chars) return ""
        val start = text.length - chars
        val boundary = text.indexOfAny(charArrayOf('。', '！', '？', '\n', '；'), start)
        return if (boundary >= 0 && boundary < text.length - 5) {
            text.substring(boundary + 1)
        } else {
            text.substring(start)
        }
    }
}
