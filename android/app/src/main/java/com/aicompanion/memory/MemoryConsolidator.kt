package com.aicompanion.memory

import com.aicompanion.network.ApiClient
import com.aicompanion.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.UUID

/**
 * 记忆巩固器：把记忆从"记得细节"逐步压成"只记得要点"再到"形成印象"。
 *
 * 对应人脑的 systems consolidation——海马体先快速记下具体情景，
 * 随着时间推移反复重放，皮层抽出其中稳定不变的规律，
 * 最后留下的不是"哪天发生了什么"，而是"这个人是什么样的"。
 *
 * 两个阶段，都是等记忆自然变老后触发（不是按容量、不是按轮次）：
 *
 * ```
 * EPISODIC（0-7天，细节）  --7天-->  CONSOLIDATED（7-30天，要点）  --30天-->  SEMANTIC（稳定事实）
 * ```
 *
 * 三条设计原则：
 * 1. **压缩时不编造**：prompt 明确禁止补充原文没有的内容，宁可少写。
 * 2. **归档不删除**：原文标记 archived，仍可检索溯源，只是不再主动注入。
 * 3. **失败不改数据**：AI 调用失败就原样返回，绝不在解析失败时清空记忆。
 */
class MemoryConsolidator(private val pool: MemoryPool) {

    companion object {
        private const val TAG = "MemoryConsolidator"
        /** 单次最多处理多少条（控制 prompt 大小与耗时） */
        private const val BATCH_EPISODIC = 15
        private const val BATCH_SEMANTIC = 20
    }

    data class Report(
        val promoted: Int = 0,
        val abstracted: Int = 0,
        val archived: Int = 0,
        val note: String = "",
    ) {
        val changed: Boolean get() = promoted > 0 || abstracted > 0
    }

    /**
     * 检查并执行该做的巩固。没有到龄期的记忆就直接返回，不会调 AI。
     */
    suspend fun consolidateIfDue(client: ApiClient): Report = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val dueEpisodic = TieredMemory.pendingEpisodic(pool.allForTiering(), now)
        val dueSemantic = TieredMemory.pendingConsolidated(pool.allForTiering(), now)

        if (dueEpisodic.isEmpty() && dueSemantic.isEmpty()) {
            return@withContext Report(note = "还没有需要巩固的记忆（未到龄期）")
        }

        var promoted = 0
        var abstracted = 0
        var archived = 0
        val notes = mutableListOf<String>()

        // ---- 阶段一：7 天以上的情景记忆 → 要点 ----
        if (dueEpisodic.isNotEmpty()) {
            val batch = dueEpisodic.take(BATCH_EPISODIC)
            val r = promoteToConsolidated(client, batch)
            promoted += r.first
            archived += r.second
            if (r.first > 0) notes.add("${batch.size} 条细节压成 ${r.first} 条要点")
        }

        // ---- 阶段二：30 天以上的要点 → 稳定事实 ----
        val stillDue = TieredMemory.pendingConsolidated(pool.allForTiering(), now)
        if (stillDue.isNotEmpty()) {
            val batch = stillDue.take(BATCH_SEMANTIC)
            val r = abstractToSemantic(client, batch)
            abstracted += r.first
            archived += r.second
            if (r.first > 0) notes.add("${batch.size} 条要点抽象成 ${r.first} 条长期印象")
        }

        Report(promoted, abstracted, archived, notes.joinToString("；"))
    }

    /** 返回 (新增条数, 归档条数) */
    private fun promoteToConsolidated(
        client: ApiClient,
        batch: List<MemoryEntry>,
    ): Pair<Int, Int> {
        val source = batch.joinToString("\n") { e ->
            "· ${dayLabel(e.timestamp)}｜${e.toStructuredText()}"
        }

        val prompt = """
你是记忆巩固助手。下面是一个人和 AI 伴侣在较早时候（一周以上）的对话记忆。
人的记忆会随时间丢失细节、只留下要点，请模拟这个过程。

【压缩规则】
- 同一件事被多次提到，合并成一条
- 必须保留：答应过的事、做过的决定、情绪明显转折、新出现的人、明确的时间约定
- 可以丢弃：寒暄、重复、没有结论的闲聊、当时就被否定的想法
- 严禁编造原文没有的信息，不确定就写得更笼统
- 每条 40-120 字，用第一人称的旁观口吻记录（"他说……""约好……"）

【输出】
只输出 JSON 数组，不要解释：
[{"content":"要点内容","category":"事件|约定|情绪|日常","importance":0.0-1.0}]
""".trimIndent()

        val text = runCatching { client.sendSimplePrompt(prompt, source)?.text }
            .onFailure { AppLogger.e(TAG, "巩固阶段一调用失败: ${it.message}") }
            .getOrNull()

        if (text.isNullOrBlank()) return 0 to 0

        val parsed = parseItems(text)
        if (parsed.isEmpty()) {
            AppLogger.w(TAG, "巩固阶段一解析为空，原记忆保持不变：${text.take(120)}")
            return 0 to 0
        }

        // 新条目时间取这批里最晚的一条：避免刚生成又立刻"到龄"
        val repTs = batch.maxOf { it.timestamp }
        val ids = batch.map { it.id }
        val newEntries = parsed.map { (content, category, importance) ->
            MemoryEntry(
                id = UUID.randomUUID().toString().take(8),
                content = content,
                category = category,
                timestamp = repTs,
                tier = MemoryTier.CONSOLIDATED,
                importance = importance,
                strength = 1.0f,
                derivedFrom = ids,
            )
        }
        pool.applyConsolidation(ids, newEntries)
        AppLogger.i(TAG, "阶段一：${batch.size} → ${newEntries.size} 条要点（归档 ${ids.size}）")
        return newEntries.size to ids.size
    }

    /** 返回 (新增条数, 归档条数) */
    private fun abstractToSemantic(
        client: ApiClient,
        batch: List<MemoryEntry>,
    ): Pair<Int, Int> {
        val source = batch.joinToString("\n") { "· ${dayLabel(it.timestamp)}｜${it.content}" }

        val prompt = """
下面是一个人和 AI 伴侣相处一个多月以来的记忆要点。
请像"相处久了的人"那样，从中抽象出**关于这个人的稳定印象**——不是某天发生了什么，
而是长期成立的结论。

【抽取维度】
- 偏好：喜欢/讨厌什么、口味、兴趣
- 习惯：作息、说话方式、常见情绪状态
- 关系：重要的人、称呼、关系变化
- 敏感点：不能提的事、容易不高兴的话题
- 长期目标：在准备什么、担心什么

【规则】
- 只写长期成立的，不写具体某天发生的事
- 每条一句话，30-80 字
- 同类合并，最多 8 条
- 严禁编造原文没有的内容
- 如果这批内容里抽不出稳定事实，就返回空数组 []

【输出】
只输出 JSON 数组：
[{"content":"一句话结论","category":"偏好|习惯|关系|禁忌|目标","importance":0.0-1.0}]
""".trimIndent()

        val text = runCatching { client.sendSimplePrompt(prompt, source)?.text }
            .onFailure { AppLogger.e(TAG, "巩固阶段二调用失败: ${it.message}") }
            .getOrNull()

        if (text.isNullOrBlank()) return 0 to 0

        val parsed = parseItems(text)
        val ids = batch.map { it.id }

        // 抽不出稳定事实也要归档——否则这批要点会永远卡在 CONSOLIDATED 反复被送进 AI
        if (parsed.isEmpty()) {
            pool.applyConsolidation(ids, emptyList())
            AppLogger.i(TAG, "阶段二：这批看不出稳定事实，仅归档 ${ids.size} 条")
            return 0 to ids.size
        }

        val repTs = batch.maxOf { it.timestamp }
        val newEntries = parsed.map { (content, category, importance) ->
            MemoryEntry(
                id = UUID.randomUUID().toString().take(8),
                content = content,
                category = category,
                timestamp = repTs,
                tier = MemoryTier.SEMANTIC,
                importance = maxOf(importance, 0.85f),
                strength = 1.0f,
                derivedFrom = ids,
            )
        }
        pool.applyConsolidation(ids, newEntries)
        AppLogger.i(TAG, "阶段二：${batch.size} → ${newEntries.size} 条长期印象")
        return newEntries.size to ids.size
    }

    /** 解析 [{content, category, importance}]，容错到能接受裸数组或代码块 */
    private fun parseItems(text: String): List<Triple<String, String, Float>> {
        val out = mutableListOf<Triple<String, String, Float>>()
        try {
            val cleaned = text.replace(Regex("```(?:json)?\\s*"), "").replace("```", "").trim()
            val s = cleaned.indexOf('[')
            val e = cleaned.lastIndexOf(']')
            if (s < 0 || e <= s) return emptyList()
            val arr = JSONArray(cleaned.substring(s, e + 1))
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val content = o.optString("content", "").trim()
                if (content.length < 4) continue
                out.add(
                    Triple(
                        content,
                        o.optString("category", "事件"),
                        o.optDouble("importance", 0.6).toFloat().coerceIn(0.2f, 1f),
                    )
                )
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "解析巩固结果失败: ${e.message}")
        }
        return out
    }

    private fun dayLabel(ts: Long): String {
        val d = (System.currentTimeMillis() - ts) / (24L * 3600 * 1000)
        return when {
            d <= 0 -> "今天"
            d == 1L -> "昨天"
            d <= 6 -> "${d}天前"
            d <= 13 -> "上周"
            d <= 29 -> "${d / 7}周前"
            else -> "上个月"
        }
    }
}
