package com.aicompanion.rag

import android.content.Context
import com.aicompanion.util.AppLogger
import org.json.JSONObject
import java.io.InputStream

/**
 * XLM-RoBERTa Tokenizer
 *
 * 用于bge-reranker-base模型(XLM-RoBERTa架构)。
 * 与BertTokenizer不同,XLM-RoBERTa使用SentencePiece分词,
 * 特殊token为 <s>, </s>, <pad> 而非 [CLS], [SEP], [PAD]。
 *
 * 此实现从tokenizer.json读取词表和配置,实现简化版的SentencePiece分词。
 *
 * 注意: 这是简化实现,完整SentencePiece实现需要更复杂的BPE算法。
 * 对于中文文本,使用字符级+双字符级分词作为近似方案。
 */
class XLMRobertaTokenizer(private val context: Context) {

    companion object {
        private const val TAG = "XLMRobertaTokenizer"
        private const val TOKENIZER_FILE = "tokenizer.json"
        private const val ASSETS_DIR = "models/bge-reranker-base"

        // XLM-RoBERTa特殊token
        const val BOS_TOKEN = "<s>"       // 0
        const val PAD_TOKEN = "<pad>"     // 1
        const val EOS_TOKEN = "</s>"      // 2
        const val UNK_TOKEN = "<unk>"     // 3

        const val BOS_ID = 0
        const val PAD_ID = 1
        const val EOS_ID = 2
        const val UNK_ID = 3

        const val MAX_SEQ_LEN = 514  // XLM-RoBERTa最大位置514
    }

    private val vocab = mutableMapOf<String, Int>()
    private val idToToken = mutableMapOf<Int, String>()
    private var isLoaded = false

    /**
     * 从tokenizer.json加载词表
     */
    fun load(): Boolean {
        return try {
            val inputStream = context.assets.open("$ASSETS_DIR/$TOKENIZER_FILE")
            inputStream.use { stream ->
                val json = JSONObject(stream.bufferedReader().use { it.readText() })

                // 解析model.vocab
                val model = json.optJSONObject("model")
                if (model != null) {
                    val vocabObj = model.optJSONObject("vocab")
                    if (vocabObj != null) {
                        val keys = vocabObj.keys()
                        while (keys.hasNext()) {
                            val token = keys.next()
                            val id = vocabObj.getInt(token)
                            vocab[token] = id
                            idToToken[id] = token
                        }
                    }
                }

                isLoaded = vocab.isNotEmpty()
                AppLogger.i(TAG, "XLM-RoBERTa tokenizer loaded: ${vocab.size} tokens")
                isLoaded
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to load XLM-RoBERTa tokenizer: ${e.message}")
            // 降级:尝试从vocab.txt加载
            loadFromVocabTxt()
        }
    }

    /**
     * 降级方案:从vocab.txt加载词表
     */
    private fun loadFromVocabTxt(): Boolean {
        return try {
            context.assets.open("$ASSETS_DIR/vocab.txt").use { stream ->
                stream.bufferedReader().useLines { lines ->
                    lines.forEachIndexed { index, line ->
                        val token = line.trim()
                        if (token.isNotEmpty()) {
                            vocab[token] = index
                            idToToken[index] = token
                        }
                    }
                }
            }
            isLoaded = vocab.isNotEmpty()
            AppLogger.i(TAG, "XLM-RoBERTa tokenizer loaded from vocab.txt: ${vocab.size} tokens")
            isLoaded
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to load vocab.txt: ${e.message}")
            false
        }
    }

    /**
     * 分词(简化版SentencePiece)
     *
     * 对于XLM-RoBERTa,使用以下策略:
     * 1. 西文: 按空格分割,添加▁前缀(SentencePiece格式)
     * 2. 中文: 字符级分割
     * 3. 查找词表,未找到则使用UNK
     */
    private fun tokenize(text: String): List<String> {
        val tokens = mutableListOf<String>()

        for (char in text) {
            when {
                char.isWhitespace() -> {
                    // 空格用▁表示(SentencePiece格式)
                    tokens.add("▁")
                }
                char.code in 0x4E00..0x9FFF -> {
                    // CJK字符:直接作为单独token
                    tokens.add(char.toString())
                }
                char.code in 0x3040..0x30FF -> {
                    // 日文假名:直接作为单独token
                    tokens.add(char.toString())
                }
                char.isLetter() || char.isDigit() -> {
                    // 西文字符:尝试组合成词
                    val lastToken = tokens.lastOrNull()
                    if (lastToken != null && lastToken.length == 1 &&
                        lastToken[0].isLetterOrDigit() && lastToken[0].code !in 0x4E00..0x9FFF) {
                        // 继续拼接当前词
                        tokens[tokens.lastIndex] = lastToken + char
                    } else {
                        tokens.add(char.toString())
                    }
                }
                else -> {
                    // 标点符号
                    tokens.add(char.toString())
                }
            }
        }

        // 为每个词添加▁前缀(如果前一个token是空格)
        val result = mutableListOf<String>()
        var prevWasSpace = true  // 句首视为空格
        for (token in tokens) {
            if (token == "▁") {
                prevWasSpace = true
            } else {
                if (prevWasSpace && token[0].code !in 0x4E00..0x9FFF) {
                    result.add("▁$token")
                } else {
                    result.add(token)
                }
                prevWasSpace = false
            }
        }

        return result
    }

    /**
     * 编码单个文本
     * 格式: <s> tokens </s>
     */
    fun encode(text: String): TokenizerResult {
        val tokens = tokenize(text).take(MAX_SEQ_LEN - 2)

        val ids = mutableListOf<Int>(BOS_ID)
        for (token in tokens) {
            ids.add(vocab[token] ?: vocab["▁$token"] ?: UNK_ID)
        }
        ids.add(EOS_ID)

        val seqLen = ids.size
        return TokenizerResult(
            inputIds = ids.map { it.toLong() }.toLongArray(),
            attentionMask = LongArray(seqLen) { 1L },
            tokenTypeIds = LongArray(seqLen) { 0L }  // XLM-RoBERTa不使用token_type_ids
        )
    }

    /**
     * 编码query-document对(Cross-Encoder格式)
     * 格式: <s> query </s></s> document </s>
     *
     * XLM-RoBERTa的pair编码与BERT不同:
     * - 使用双</s>分隔query和document
     * - 不使用token_type_ids区分
     */
    fun encodePair(query: String, document: String, maxLen: Int = MAX_SEQ_LEN): TokenizerResult {
        val queryTokens = tokenize(query)
        val docTokens = tokenize(document)

        // 分配token预算(减去4个特殊token: <s> </s> </s> </s>)
        val budget = maxLen - 4
        val queryBudget = budget / 2
        val docBudget = budget - queryBudget

        val queryTruncated = queryTokens.take(queryBudget)
        val docTruncated = docTokens.take(docBudget)

        val ids = mutableListOf<Int>()

        // <s> query </s></s> document </s>
        ids.add(BOS_ID)
        for (token in queryTruncated) {
            ids.add(vocab[token] ?: vocab["▁$token"] ?: UNK_ID)
        }
        ids.add(EOS_ID)
        ids.add(EOS_ID)  // 双</s>分隔
        for (token in docTruncated) {
            ids.add(vocab[token] ?: vocab["▁$token"] ?: UNK_ID)
        }
        ids.add(EOS_ID)

        val seqLen = ids.size
        return TokenizerResult(
            inputIds = ids.map { it.toLong() }.toLongArray(),
            attentionMask = LongArray(seqLen) { 1L },
            tokenTypeIds = LongArray(seqLen) { 0L }  // XLM-RoBERTa不使用token_type_ids
        )
    }

    fun isReady(): Boolean = isLoaded
}