/** 唤醒词 → sherpa KWS 关键词行：中文转拼音（声母 + 带调韵母）。 */
package com.aicompanion.voice.wake

import android.content.Context
import com.aicompanion.util.AppLogger

/**
 * 中文唤醒词转换器。
 *
 * 模型（sherpa-onnx-kws-zipformer-wenetspeech-3.3M）的建模单元是「声母 + 带调韵母」，
 * 所以「你好星尘」要写成 `n ǐ h ǎo x īng ch én`，再按 sherpa 的关键词行语法附加
 * 提权系数 `:score`、触发阈值 `#threshold` 和显示名 `@原文`。
 *
 * 汉字→拼音表来自 assets/kws/pinyin.tsv（由 tools/gen_pinyin_table.py 生成，
 * 只保留声母/韵母都能在 tokens.txt 里查到的字，保证拼出来的行一定合法）。
 *
 * 也支持直接写拼音（声调用数字或符号都行），用于多音字需要人工纠正的场合：
 *   `你好星尘`  → n ǐ h ǎo x īng ch én
 *   `ni3 hao3` → n ǐ h ǎo
 */
object PinyinConverter {

    private const val TAG = "PinyinConverter"
    private const val TABLE_ASSET = "kws/pinyin.tsv"

    /** 唤醒词之间可用这些分隔符 */
    private val WORD_SEPARATORS = Regex("[,，;；、\\n\\r]+")

    private val table = HashMap<Char, String>(24576)

    @Volatile
    private var loaded = false

    private val INITIALS3 = arrayOf("zh", "ch", "sh")
    private const val INITIALS1 = "bpmfdtnlgkhjqxrzcsyw"

    private val TONE_MARKS = mapOf(
        'a' to "āáǎà",
        'e' to "ēéěè",
        'i' to "īíǐì",
        'o' to "ōóǒò",
        'u' to "ūúǔù",
        'ü' to "ǖǘǚǜ",
    )

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val t0 = System.currentTimeMillis()
            try {
                context.assets.open(TABLE_ASSET).bufferedReader().useLines { lines ->
                    for (line in lines) {
                        val tab = line.indexOf('\t')
                        if (tab != 1 || line.length < 3) continue
                        table[line[0]] = line.substring(tab + 1).trim()
                    }
                }
                loaded = table.isNotEmpty()
            } catch (e: Exception) {
                AppLogger.e(TAG, "拼音表加载失败: ${e.message}", e)
            }
            AppLogger.i(TAG, "拼音表 ${table.size} 字，用时 ${System.currentTimeMillis() - t0}ms")
        }
    }

    fun isReady(): Boolean = loaded

    /**
     * 把一整段（可能含多个唤醒词，用逗号/换行分隔）转成多行关键词。
     * @return 每个唤醒词一行；无法转换的词会被跳过。
     */
    fun toKeywordLines(
        context: Context,
        raw: String,
        boost: Float = 3.0f,
        threshold: Float = 0.25f,
    ): List<String> {
        ensureLoaded(context)
        val out = ArrayList<String>()
        for (word in raw.split(WORD_SEPARATORS)) {
            val w = word.trim()
            if (w.isEmpty()) continue
            val tokens = wordToTokens(w)
            if (tokens.isEmpty()) {
                AppLogger.w(TAG, "唤醒词「$w」无法转成拼音，已跳过")
                continue
            }
            out.add(formatLine(tokens, w, boost, threshold))
        }
        return out
    }

    /** 单个词 → token 列表 */
    fun wordToTokens(word: String): List<String> {
        // 纯拼音输入（字母/声调符号/数字/空格）：直接按拼音解析
        if (word.any { it.isDigit() } ||
            word.all { it.isLetter() && (it.code < 128 || it in "āáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜü") || it == ' ' }
        ) {
            val fromPinyin = pinyinToTokens(word)
            if (fromPinyin.isNotEmpty()) return fromPinyin
        }
        val tokens = ArrayList<String>()
        for (ch in word) {
            val syl = table[ch] ?: continue
            val (ini, fin) = splitSyllable(syl)
            if (ini.isNotEmpty()) tokens.add(ini)
            if (fin.isNotEmpty()) tokens.add(fin)
        }
        return tokens
    }

    private fun formatLine(tokens: List<String>, display: String, boost: Float, threshold: Float): String {
        val sb = StringBuilder()
        sb.append(tokens.joinToString(" "))
        sb.append(" :").append(trimFloat(boost))
        sb.append(" #").append(trimFloat(threshold))
        sb.append(" @").append(display)
        return sb.toString()
    }

    private fun trimFloat(v: Float): String {
        // 3.00 -> "3"、0.25 -> "0.25"（早先 dropLast 会留下 "3." 这种带点的写法）
        val s = String.format("%.2f", v).trimEnd('0').trimEnd('.')
        return s.ifEmpty { "0" }
    }

    /** "ni3 hao3" / "nǐ hǎo" / "nv3" → [n, ǐ, h, ǎo] */
    fun pinyinToTokens(spec: String): List<String> {
        val tokens = ArrayList<String>()
        for (raw in spec.trim().split(Regex("\\s+"))) {
            if (raw.isEmpty()) continue
            val m = Regex("^([a-zA-ZüÜvV]+)([0-5])?$").find(raw)
            val syl = if (m != null) {
                val base = m.groupValues[1].lowercase().replace('v', 'ü')
                val tone = m.groupValues[2].toIntOrNull() ?: 0
                applyTone(base, tone)
            } else {
                raw.lowercase()
            }
            val (ini, fin) = splitSyllable(syl)
            if (ini.isNotEmpty()) tokens.add(ini)
            if (fin.isNotEmpty()) tokens.add(fin)
        }
        return tokens
    }

    /** 给无调音节加声调符号：ni + 3 → nǐ */
    fun applyTone(base: String, tone: Int): String {
        if (tone !in 1..4) return base
        val idx = toneIndex(base)
        if (idx < 0) return base
        val ch = base[idx]
        val marks = TONE_MARKS[ch] ?: return base
        return base.substring(0, idx) + marks[tone - 1] + base.substring(idx + 1)
    }

    /** 标调位置：a 优先，其次 o、e，iu→u、ui→i，否则最后一个元音 */
    private fun toneIndex(s: String): Int {
        val a = s.indexOf('a'); if (a >= 0) return a
        val o = s.indexOf('o'); if (o >= 0) return o
        val e = s.indexOf('e'); if (e >= 0) return e
        if (s.endsWith("iu")) return s.length - 1
        if (s.endsWith("ui")) return s.length - 2
        for (i in s.indices.reversed()) if (s[i] in "iuü") return i
        return -1
    }

    /** 'nǐ' -> ('n','ǐ')；'zhǎng' -> ('zh','ǎng')；'ér' -> ('','ér') */
    fun splitSyllable(syl: String): Pair<String, String> {
        for (p in INITIALS3) if (syl.startsWith(p) && syl.length > p.length) return p to syl.substring(p.length)
        if (syl.isNotEmpty() && INITIALS1.indexOf(syl[0]) >= 0 && syl.length > 1) {
            return syl.substring(0, 1) to syl.substring(1)
        }
        return "" to syl
    }
}
