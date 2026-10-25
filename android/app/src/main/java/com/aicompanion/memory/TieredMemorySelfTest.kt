package com.aicompanion.memory

/**
 * 分层记忆自检：不需要设备、不需要 AI、不碰真实数据。
 *
 * 为什么值得写：遗忘曲线和分层注入是"看起来对、容易错"的东西——
 * 时间窗算错一天、强度公式符号反了，界面上完全看不出来，只会表现为
 * "AI 一周后还记得太清楚"或"刚说过就忘"。这些用断言钉住。
 */
object TieredMemorySelfTest {

    private const val DAY = 24L * 3600 * 1000

    data class Result(val name: String, val pass: Boolean, val detail: String)

    fun run(): List<Result> {
        val out = mutableListOf<Result>()
        val now = System.currentTimeMillis()

        fun entry(
            content: String,
            daysAgo: Double,
            tier: MemoryTier = MemoryTier.EPISODIC,
            importance: Float = 0.5f,
            recall: Int = 0,
        ) = MemoryEntry(
            id = content.take(4),
            content = content,
            timestamp = now - (daysAgo * DAY).toLong(),
            tier = tier,
            importance = importance,
            recallCount = recall,
        )

        // 1) 刚发生的事：强度接近 1
        run {
            val e = entry("今天刚说的话", 0.0)
            val s = TieredMemory.strengthOf(e, now)
            out.add(Result("新记忆强度≈1", s > 0.9f, "strength=$s"))
        }

        // 2) 8 天前、低重要、没被回忆过：强度显著下降
        run {
            val e = entry("八天前的小事", 8.0, importance = 0.3f)
            val s = TieredMemory.strengthOf(e, now)
            out.add(Result("8天后明显减弱", s < 0.45f, "strength=$s"))
        }

        // 3) 同样 8 天，但被反复回忆（recallCount=5）：强度仍然高
        //    这是"间隔重复"的核心：常用的事不会因为旧就消失
        run {
            val plain = entry("八天前没再提过", 8.0, importance = 0.3f, recall = 0)
            val recalled = entry("八天前常被提起", 8.0, importance = 0.3f, recall = 5)
            val a = TieredMemory.strengthOf(plain, now)
            val b = TieredMemory.strengthOf(recalled, now)
            out.add(Result("回忆强化有效", b > a * 1.5f, "未回忆=$a 回忆5次=$b"))
        }

        // 4) 重要性高的事衰减更慢
        run {
            val low = entry("无关紧要", 6.0, importance = 0.1f)
            val high = entry("很重要的事", 6.0, importance = 1.0f)
            val a = TieredMemory.strengthOf(low, now)
            val b = TieredMemory.strengthOf(high, now)
            out.add(Result("重要性减缓遗忘", b > a, "低=$a 高=$b"))
        }

        // 5) 语义层不受遗忘曲线影响（它已经是"沉淀下来的结论"）
        run {
            val sem = entry("他怕打针", 400.0, tier = MemoryTier.SEMANTIC, importance = 0.9f)
            val s = TieredMemory.strengthOf(sem, now)
            out.add(Result("长期印象不衰减", s >= 0.85f, "strength=$s"))
        }

        // 6) 到龄判定：6 天不算、8 天算
        run {
            val list = listOf(
                entry("六天前", 6.0),
                entry("八天前", 8.0),
                entry("已归档", 10.0).copy(archived = true),
            )
            val due = TieredMemory.pendingEpisodic(list, now)
            out.add(
                Result(
                    "7天阈值为界",
                    due.size == 1 && due[0].content == "八天前",
                    "命中=${due.map { it.content }}"
                )
            )
        }

        // 7) 语义化判定：32 天的要点会进阶段二，29 天不会
        run {
            val list = listOf(
                entry("29天前的要点", 29.0, tier = MemoryTier.CONSOLIDATED),
                entry("32天前的要点", 32.0, tier = MemoryTier.CONSOLIDATED),
            )
            val due = TieredMemory.pendingConsolidated(list, now)
            out.add(
                Result(
                    "30天阈值为界",
                    due.size == 1 && due[0].content == "32天前的要点",
                    "命中=${due.map { it.content }}"
                )
            )
        }

        // 8) 分层注入：三层各就各位，归档条目不出现
        run {
            val list = listOf(
                entry("他喜欢喝冰美式", 200.0, tier = MemoryTier.SEMANTIC, importance = 0.9f),
                entry("上周约好周末看电影", 10.0, tier = MemoryTier.CONSOLIDATED),
                entry("今天他加班到十点", 0.5),
                entry("这段应该被隐藏", 2.0).copy(archived = true),
            )
            val block = TieredMemory.buildTieredBlock(list, maxChars = 1200, now = now)
            val hasSem = block.contains("长期印象") && block.contains("冰美式")
            val hasCon = block.contains("过去一段时间的要点") && block.contains("看电影")
            val hasEpi = block.contains("最近发生的事") && block.contains("加班到十点")
            val hidden = !block.contains("这段应该被隐藏")
            out.add(
                Result(
                    "三层注入正确",
                    hasSem && hasCon && hasEpi && hidden,
                    "长期=$hasSem 要点=$hasCon 近期=$hasEpi 归档隐藏=$hidden"
                )
            )
        }

        // 9) 预算控制：给很小的预算时不炸、不超太多
        run {
            val list = (1..30).map { entry("第${it}条比较长的记忆内容用于测试预算控制是否正确生效", it * 0.2) }
            val block = TieredMemory.buildTieredBlock(list, maxChars = 200, now = now)
            out.add(Result("预算生效", block.length < 700, "预算200 → 实际${block.length}字"))
        }

        // 10) 回忆一次后强度回升、计数 +1
        run {
            val e = entry("被提到的旧事", 5.0, importance = 0.3f)
            val before = TieredMemory.strengthOf(e, now)
            val after = TieredMemory.strengthOf(TieredMemory.onRecalled(e, now), now)
            out.add(
                Result(
                    "回忆后强度回升",
                    after > before && TieredMemory.onRecalled(e).recallCount == 1,
                    "前=$before 后=$after"
                )
            )
        }

        // 11) 空列表不产生垃圾块
        run {
            val block = TieredMemory.buildTieredBlock(emptyList(), 500, now)
            out.add(Result("空记忆不注入", block.isBlank(), "len=${block.length}"))
        }

        return out
    }

    fun summary(results: List<Result>): String {
        val pass = results.count { it.pass }
        return buildString {
            appendLine("分层记忆自检：$pass/${results.size} 通过")
            results.forEach {
                appendLine("${if (it.pass) "✓" else "✗"} ${it.name} — ${it.detail}")
            }
        }.trimEnd()
    }
}
