package com.aicompanion.memory

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * 分层记忆：按人脑记忆巩固（systems consolidation）的方式组织。
 *
 * ## 三层怎么分工
 *
 * | 层 | 龄期 | 留什么 | 类比 |
 * |---|---|---|---|
 * | [MemoryTier.EPISODIC] | 0–7 天 | 原始细节，能逐字复述 | "昨天下午你说要去医院" |
 * | [MemoryTier.CONSOLIDATED] | 7–30 天 | 压成要点，细节模糊 | "上周他去医院检查了，结果还好" |
 * | [MemoryTier.SEMANTIC] | 30 天以上 | 抽象成稳定事实，不带时间 | "他怕打针" |
 *
 * ## 为什么要有强度（strength）
 *
 * 只按"多久以前"分层是不够的：一件三天前的小事会被记住，而半年前反复提起的事
 * 不该被当成模糊记忆。所以每条记忆带一个强度，按 Ebbinghaus 遗忘曲线衰减：
 *
 * ```
 * strength = exp(-elapsedDays / stability)
 * ```
 *
 * stability（稳定性）不是常数：**每被回忆一次就变长**（间隔重复效应，SM-2 的思路），
 * 重要度高的事衰减也更慢。这带来两个符合直觉的结果：
 * - 常被聊起的事，即使很久以前也保持高强度；
 * - 从没被提过的小事，一周后自然沉到低权重，让位给更有用的内容。
 *
 * ## 强度低不等于删掉
 *
 * 低强度只是"不优先注入上下文"，条目本身仍在库里可检索。
 * 人会忘细节但不会忘"有这么回事"，这里也是——原文归档，摘要留在上层。
 */
object TieredMemory {

    /** 超过这个龄期的情景记忆进入巩固流程 */
    const val EPISODIC_DAYS = 7

    /** 超过这个龄期的巩固记忆抽象为语义记忆 */
    const val SEMANTIC_DAYS = 30

    /** 强度低于此值就不主动注入（但仍可检索） */
    const val INJECT_MIN_STRENGTH = 0.15f

    private const val DAY_MS = 24L * 3600 * 1000

    /** 基础稳定性（天）：无回忆、无重要性加成时的"半衰感" */
    private const val BASE_STABILITY_DAYS = 4.0

    fun ageDays(entry: MemoryEntry, now: Long = System.currentTimeMillis()): Double =
        ((now - entry.timestamp).coerceAtLeast(0L)) / DAY_MS.toDouble()

    /**
     * 计算当前强度。
     *
     * stability 的三个来源：
     * 1. 基础值
     * 2. 回忆次数：每回忆一次 ×1.6（间隔重复，越用越牢）
     * 3. 重要性：importance 0~1 线性加成到最多 ×2.5
     */
    fun strengthOf(entry: MemoryEntry, now: Long = System.currentTimeMillis()): Float {
        // 语义层本身就是"已经沉淀下来的东西"，不按遗忘曲线打折
        if (entry.tier == MemoryTier.SEMANTIC) return max(entry.strength, 0.85f)

        val stability = BASE_STABILITY_DAYS *
            Math.pow(1.6, entry.recallCount.toDouble()).coerceAtMost(24.0) *
            (1.0 + 1.5 * entry.importance.coerceIn(0f, 1f))

        val elapsed = ageDays(entry, now)
        val decayed = exp(-elapsed / stability)
        return decayed.toFloat().coerceIn(0f, 1f)
    }

    /** 返回刷新过强度字段的副本（不落盘，由调用方决定何时保存） */
    fun refreshed(entry: MemoryEntry, now: Long = System.currentTimeMillis()): MemoryEntry =
        entry.copy(strength = strengthOf(entry, now))

    /**
     * 该被巩固的情景记忆：龄期超过 [EPISODIC_DAYS]，且还没被归档。
     * 按时间正序返回（先老后新），保证巩固顺序符合时间线。
     */
    fun pendingEpisodic(
        entries: List<MemoryEntry>,
        now: Long = System.currentTimeMillis(),
    ): List<MemoryEntry> = entries
        .filter {
            !it.archived &&
                it.tier == MemoryTier.EPISODIC &&
                ageDays(it, now) >= EPISODIC_DAYS
        }
        .sortedBy { it.timestamp }

    /** 该被抽象成语义记忆的巩固记忆 */
    fun pendingConsolidated(
        entries: List<MemoryEntry>,
        now: Long = System.currentTimeMillis(),
    ): List<MemoryEntry> = entries
        .filter {
            !it.archived &&
                it.tier == MemoryTier.CONSOLIDATED &&
                ageDays(it, now) >= SEMANTIC_DAYS
        }
        .sortedBy { it.timestamp }

    /**
     * 分层注入：把三层拼成一段给模型看的上下文。
     *
     * 预算分配（按"信息密度"倒挂）：
     * - 语义层 25%：条数少、价值高，固定全量
     * - 巩固层 35%：要点，中等细节
     * - 情景层 40%：最近的事，模型最容易用错也最需要精确
     */
    fun buildTieredBlock(
        entries: List<MemoryEntry>,
        maxChars: Int = 1200,
        now: Long = System.currentTimeMillis(),
    ): String {
        if (entries.isEmpty()) return ""

        val active = entries.filter { !it.archived }
        if (active.isEmpty()) return ""

        val semantic = active.filter { it.tier == MemoryTier.SEMANTIC }
            .sortedByDescending { it.importance }
        val consolidated = active.filter { it.tier == MemoryTier.CONSOLIDATED }
            .sortedByDescending { it.timestamp }
        val episodic = active.filter { it.tier == MemoryTier.EPISODIC }
            .sortedByDescending { it.timestamp }

        val sb = StringBuilder()

        // ---- 语义层：稳定事实，永远在场 ----
        if (semantic.isNotEmpty()) {
            sb.appendLine("【关于他/她的长期印象】（稳定事实，不是某天发生的事，可直接用）")
            var budget = (maxChars * 0.25).toInt().coerceAtLeast(120)
            for (e in semantic) {
                val line = "· ${e.content.trim()}"
                if (line.length > budget && sb.length > 60) break
                sb.appendLine(line)
                budget -= line.length
            }
        }

        // ---- 巩固层：要点 ----
        if (consolidated.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("【过去一段时间的要点】（细节已模糊，只有结论；不要编造过程细节）")
            var budget = (maxChars * 0.35).toInt().coerceAtLeast(150)
            for (e in consolidated) {
                val line = "· ${dayLabel(e.timestamp)}｜${e.content.trim()}"
                if (line.length > budget && sb.length > 100) break
                sb.appendLine(line)
                budget -= line.length
            }
        }

        // ---- 情景层：最近的事，带真实强度过滤 ----
        val recent = episodic.filter { strengthOf(it, now) >= INJECT_MIN_STRENGTH }
        if (recent.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("【最近发生的事】（细节清楚，可以具体引用）")
            var budget = (maxChars * 0.40).toInt().coerceAtLeast(180)
            for (e in recent) {
                val structured = e.toStructuredText()
                val body = if (structured.length > e.content.length + 8) structured else e.content
                val line = "· ${dayLabel(e.timestamp)}｜${body.trim()}"
                if (line.length > budget && sb.length > 150) break
                sb.appendLine(line)
                budget -= line.length
            }
        }

        return sb.toString().trimEnd()
    }

    private fun dayLabel(ts: Long): String {
        val days = (System.currentTimeMillis() - ts) / DAY_MS
        return when {
            days <= 0 -> "今天"
            days == 1L -> "昨天"
            days <= 6 -> "${days}天前"
            days <= 13 -> "上周"
            days <= 29 -> "${days / 7}周前"
            days <= 60 -> "上个月"
            else -> SimpleDateFormat("yyyy年M月", Locale.CHINA).format(Date(ts))
        }
    }

    /** 供 UI 显示：某条记忆现在还有多"清楚" */
    fun clarityLabel(entry: MemoryEntry, now: Long = System.currentTimeMillis()): String {
        val s = strengthOf(entry, now)
        return when {
            s >= 0.75f -> "清晰"
            s >= 0.5f -> "记得"
            s >= 0.3f -> "模糊"
            s >= INJECT_MIN_STRENGTH -> "依稀"
            else -> "淡忘"
        }
    }

    /** 回忆一次：强度回升（间隔重复） */
    fun onRecalled(entry: MemoryEntry, now: Long = System.currentTimeMillis()): MemoryEntry =
        entry.copy(
            recallCount = entry.recallCount + 1,
            lastRecallAt = now,
            strength = min(1.0f, strengthOf(entry, now) + 0.25f),
        )

    /** 统计：三层各多少条 */
    fun stats(entries: List<MemoryEntry>): String {
        val active = entries.filter { !it.archived }
        val e = active.count { it.tier == MemoryTier.EPISODIC }
        val c = active.count { it.tier == MemoryTier.CONSOLIDATED }
        val s = active.count { it.tier == MemoryTier.SEMANTIC }
        val archived = entries.count { it.archived }
        return "情景 $e · 要点 $c · 长期印象 $s" + if (archived > 0) " · 归档 $archived" else ""
    }
}
