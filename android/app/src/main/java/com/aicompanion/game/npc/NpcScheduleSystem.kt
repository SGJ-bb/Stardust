package com.aicompanion.game.npc

import com.aicompanion.game.logic.TimeSystem
import com.aicompanion.game.world.MapCell

/** 日程条目: 时段 → 目标格子 + 行为。 */
data class ScheduleEntry(
    val phase: TimeSystem.DayPhase,
    val dest: MapCell,
    val behavior: NpcBehavior
)

/**
 * 日程系统(M1): 查表得到该 NPC 当前时段应前往的格子与行为。
 * MVP 用简单线性移动承载，复杂寻路可后续接 A*。
 */
class NpcScheduleSystem(private val schedule: List<ScheduleEntry>) {

    fun targetFor(time: TimeSystem): ScheduleEntry? =
        schedule.firstOrNull { it.phase == time.phase }
}