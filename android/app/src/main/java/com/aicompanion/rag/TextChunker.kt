package com.aicompanion.rag

class TextChunker(
    private val maxChars: Int = RagConfig.chunkMaxChars,
    private val overlapChars: Int = RagConfig.chunkOverlapChars
) {

    data class Chunk(
        val index: Int,
        val text: String,
        val sourceField: String = ""
    )

    fun chunkPersona(fields: Map<String, String>): List<Chunk> {
        val chunks = mutableListOf<Chunk>()
        var globalIndex = 0

        for ((fieldName, text) in fields) {
            if (text.isBlank()) continue

            if (text.length <= maxChars) {
                chunks.add(Chunk(globalIndex++, text.trim(), fieldName))
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
        if (text.length <= maxChars) return listOf(Chunk(0, text.trim(), sourceLabel))

        return splitLongText(text.trim(), sourceLabel, 0)
    }

    private fun splitLongText(text: String, fieldName: String, startIndex: Int): List<Chunk> {
        val chunks = mutableListOf<Chunk>()
        val paragraphs = text.split(Regex("\\n+")).filter { it.isNotBlank() }

        var current = StringBuilder()
        var localIndex = startIndex

        for (para in paragraphs) {
            if (current.length + para.length > maxChars && current.isNotEmpty()) {
                chunks.add(Chunk(localIndex++, current.toString().trim(), fieldName))
                val overlap = buildOverlap(current.toString(), overlapChars)
                current = StringBuilder(overlap)
            }

            if (para.length > maxChars) {
                if (current.isNotEmpty()) {
                    chunks.add(Chunk(localIndex++, current.toString().trim(), fieldName))
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
            chunks.add(Chunk(localIndex++, current.toString().trim(), fieldName))
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
                chunks.add(Chunk(idx++, current.toString().trim(), fieldName))
                val overlap = buildOverlap(current.toString(), overlapChars / 2)
                current = StringBuilder(overlap)
            }
            current.append(sentence)
        }

        if (current.isNotBlank()) {
            chunks.add(Chunk(idx++, current.toString().trim(), fieldName))
        }

        if (chunks.isEmpty()) {
            val step = maxChars - overlapChars
            var pos = 0
            while (pos < text.length) {
                val end = (pos + maxChars).coerceAtMost(text.length)
                chunks.add(Chunk(idx++, text.substring(pos, end), fieldName))
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