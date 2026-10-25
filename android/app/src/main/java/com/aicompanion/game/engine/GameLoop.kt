package com.aicompanion.game.engine

import android.os.Handler
import android.os.Looper
import com.aicompanion.game.logic.TimeSystem
import com.aicompanion.game.npc.NpcBehavior
import com.aicompanion.game.npc.NpcScheduleSystem
import com.aicompanion.game.npc.NpcState
import com.aicompanion.game.npc.ScheduleEntry
import com.aicompanion.game.world.MapCell
import com.aicompanion.game.world.PixelPos

/** NPC 渲染所需的确定性位置包(不含任何 LLM 内容)。 */
data class NpcMovePacket(
    val id: String,
    val x: Float,
    val y: Float,
    val flipLeft: Boolean,
    val moving: Boolean
)

/**
 * M1 确定性游戏循环: 时钟推进 + NPC 按日程移动。
 * 每 tick 把结果经 bridge 下发前端渲染(前端不自己计算, 只跟随)。
 * 纯本地、零网络、零 LLM——正是"先证明确定性地基"要求的形态。
 */
class GameLoop(
    private val onTick: (TimeSystem, List<NpcMovePacket>) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val time = TimeSystem()

    private val schedule = NpcScheduleSystem(
        listOf(
            ScheduleEntry(TimeSystem.DayPhase.MORNING, MapCell(22, 19), NpcBehavior.WALK),
            ScheduleEntry(TimeSystem.DayPhase.AFTERNOON, MapCell(26, 21), NpcBehavior.IDLE),
            ScheduleEntry(TimeSystem.DayPhase.EVENING, MapCell(24, 20), NpcBehavior.WALK)
        )
    )
    private val emily = NpcState("emily", MapCell(22, 19), NpcBehavior.WALK)

    private var emilyX = 720f
    private var emilyY = 620f
    private var running = false

    private val tickMs = 100L
    private val speedPerTick = 5f

    fun start() { if (running) return; running = true; post() }

    fun stop() { running = false; handler.removeCallbacksAndMessages(null) }

    private fun post() = handler.postDelayed(::tick, tickMs)

    private fun tick() {
        if (!running) return
        time.tick()

        // 依据当前时段日程决定去向
        val dest = schedule.targetFor(time)?.dest?.toPixel() ?: emily.cell.toPixel()
        val dx = dest.x - emilyX
        val dy = dest.y - emilyY
        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
        val moving = dist > 2f
        if (moving) {
            val step = kotlin.math.min(speedPerTick, dist)
            emilyX += dx / dist * step
            emilyY += dy / dist * step
        }
        emily.cell = PixelPos(emilyX, emilyY).toCell()

        onTick(time, listOf(NpcMovePacket("emily", emilyX, emilyY, dx < 0f, moving)))
        post()
    }
}