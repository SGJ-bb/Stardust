package com.aicompanion.rag

import java.io.InputStream
import java.util.regex.Pattern

data class TokenizerResult(
    val inputIds: LongArray,
    val attentionMask: LongArray,
    val tokenTypeIds: LongArray
)

class BertTokenizer(vocabStream: InputStream) {

    private val vocab: Map<String, Int>
    private val invVocab: Map<Int, String>

    companion object {
        const val CLS_TOKEN = "[CLS]"
        const val SEP_TOKEN = "[SEP]"
        const val UNK_TOKEN = "[UNK]"
        const val PAD_TOKEN = "[PAD]"
        const val CLS_ID = 101
        const val SEP_ID = 102
        const val UNK_ID = 100
        const val PAD_ID = 0
        const val MAX_SEQ_LEN = 512

        private val PUNCT_PATTERN = Pattern.compile(
            """[!\"#$%&'()*+,\-./:;<=>?@\[\\\]^_`{|}~]"""
        )
    }

    init {
        val vocabMap = mutableMapOf<String, Int>()
        vocabStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            var idx = 0
            for (rawLine in lines) {
                // 去除行尾 \r（Windows 换行符 \r\n 被 useLines 拆分后可能残留 \r）
                val line = rawLine.trimEnd('\r')
                // 不 trim 首部空格：BERT vocab 中某些 token 可能包含前导空格（如 " ##ing"）
                // 行号即 token ID，空行也要递增 idx
                if (line.isNotEmpty()) {
                    vocabMap[line] = idx
                }
                idx++
            }
        }
        vocab = vocabMap
        invVocab = vocabMap.entries.associate { it.value to it.key }
    }

    fun tokenize(text: String): List<String> {
        val basicTokens = basicTokenize(text)
        return basicTokens.flatMap { wordPieceTokenize(it) }
    }

    fun encode(text: String): TokenizerResult {
        val tokens = tokenize(text)
        // 截断到 MAX_SEQ_LEN - 2 (留位置给 CLS 和 SEP)
        val truncated = tokens.take(MAX_SEQ_LEN - 2)

        val ids = mutableListOf<Int>()
        ids.add(CLS_ID)
        for (token in truncated) {
            ids.add(vocab[token] ?: UNK_ID)
        }
        ids.add(SEP_ID)

        val seqLen = ids.size
        return TokenizerResult(
            inputIds = ids.map { it.toLong() }.toLongArray(),
            attentionMask = LongArray(seqLen) { 1L },
            tokenTypeIds = LongArray(seqLen) { 0L }
        )
    }

    /**
     * 对query-document对进行编码(Cross-Encoder格式)
     * 格式: [CLS] query [SEP] document [SEP]
     * tokenTypeIds: 0 for query, 1 for document
     *
     * @param query 查询文本
     * @param document 文档文本
     * @param maxLen 最大序列长度
     * @return 编码结果
     */
    fun encodePair(query: String, document: String, maxLen: Int = MAX_SEQ_LEN): TokenizerResult {
        val queryTokens = tokenize(query)
        val docTokens = tokenize(document)

        // 分配token预算: query和document各占一半(减去特殊token)
        val budget = maxLen - 3  // [CLS] + [SEP] + [SEP]
        val queryBudget = budget / 2
        val docBudget = budget - queryBudget

        val queryTruncated = queryTokens.take(queryBudget)
        val docTruncated = docTokens.take(docBudget)

        val ids = mutableListOf<Int>()
        val tokenTypes = mutableListOf<Int>()

        // [CLS] query [SEP]
        ids.add(CLS_ID)
        tokenTypes.add(0)
        for (token in queryTruncated) {
            ids.add(vocab[token] ?: UNK_ID)
            tokenTypes.add(0)
        }
        ids.add(SEP_ID)
        tokenTypes.add(0)

        // document [SEP]
        for (token in docTruncated) {
            ids.add(vocab[token] ?: UNK_ID)
            tokenTypes.add(1)
        }
        ids.add(SEP_ID)
        tokenTypes.add(1)

        val seqLen = ids.size
        return TokenizerResult(
            inputIds = ids.map { it.toLong() }.toLongArray(),
            attentionMask = LongArray(seqLen) { 1L },
            tokenTypeIds = tokenTypes.map { it.toLong() }.toLongArray()
        )
    }

    private fun basicTokenize(text: String): List<String> {
        var cleaned = cleanText(text)
        cleaned = tokenizeChineseChars(cleaned)
        val tokens = whitespaceTokenize(cleaned)
        return tokens.flatMap { splitOnPunctuation(it) }
    }

    private fun cleanText(text: String): String {
        val sb = StringBuilder()
        for (ch in text) {
            val code = ch.code
            if (code == 0 || code == 0xFFFD || isControl(ch)) continue
            if (isWhitespace(ch)) {
                sb.append(' ')
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun tokenizeChineseChars(text: String): String {
        val sb = StringBuilder()
        for (ch in text) {
            if (isCJK(ch)) {
                sb.append(' ')
                sb.append(ch)
                sb.append(' ')
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun whitespaceTokenize(text: String): List<String> {
        return text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    }

    private fun splitOnPunctuation(text: String): List<String> {
        val tokens = mutableListOf<String>()
        var current = StringBuilder()
        for (ch in text.lowercase()) {
            if (isPunctuation(ch)) {
                if (current.isNotEmpty()) {
                    tokens.add(current.toString())
                    current = StringBuilder()
                }
                tokens.add(ch.toString())
            } else {
                current.append(ch)
            }
        }
        if (current.isNotEmpty()) {
            tokens.add(current.toString())
        }
        return tokens
    }

    private fun wordPieceTokenize(token: String): List<String> {
        if (token.length > 200) {
            return listOf(UNK_TOKEN)
        }
        val tokens = mutableListOf<String>()
        var start = 0
        while (start < token.length) {
            var end = token.length
            var found = false
            while (start < end) {
                val substr = if (start > 0) "##" + token.substring(start, end) else token.substring(start, end)
                if (vocab.containsKey(substr)) {
                    tokens.add(substr)
                    found = true
                    break
                }
                end--
            }
            if (!found) {
                tokens.add(UNK_TOKEN)
                start++
            } else {
                start = end
            }
        }
        return tokens
    }

    private fun isCJK(ch: Char): Boolean {
        val code = ch.code
        return (code in 0x4E00..0x9FFF) ||      // CJK Unified Ideographs
               (code in 0x3400..0x4DBF) ||      // CJK Unified Ideographs Extension A
               (code in 0x20000..0x2A6DF) ||    // CJK Unified Ideographs Extension B
               (code in 0xF900..0xFAFF) ||      // CJK Compatibility Ideographs
               (code in 0x2F800..0x2FA1F) ||    // CJK Compatibility Ideographs Supplement
               (code in 0x3000..0x303F) ||      // CJK Symbols and Punctuation
               (code in 0x3040..0x309F) ||      // Hiragana
               (code in 0x30A0..0x30FF) ||      // Katakana
               (code in 0xAC00..0xD7AF)         // Hangul Syllables
    }

    private fun isPunctuation(ch: Char): Boolean {
        val code = ch.code
        if ((code in 33..47) || (code in 58..64) ||
            (code in 91..96) || (code in 123..126)) return true
        return PUNCT_PATTERN.matcher(ch.toString()).find()
    }

    private fun isControl(ch: Char): Boolean {
        if (ch == '\t' || ch == '\n' || ch == '\r') return false
        val type = Character.getType(ch)
        return type == Character.FORMAT.toInt() ||
               type == Character.CONTROL.toInt() ||
               type == Character.PRIVATE_USE.toInt() ||
               type == Character.SURROGATE.toInt()
    }

    private fun isWhitespace(ch: Char): Boolean {
        if (ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r') return true
        return Character.isWhitespace(ch)
    }
}
