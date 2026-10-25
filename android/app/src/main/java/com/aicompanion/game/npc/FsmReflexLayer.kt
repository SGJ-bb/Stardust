package com.aicompanion.game.npc

import com.aicompanion.game.logic.TimeSystem

/**
 * 反射层(FSM + 极简 utility) —— 纯本地确定性决定 NPC 当下行为，零 LLM、零网络。
 * 属于三层大脑的 L1 反射层的一部分。
 */
object FsmReflexLayer {

    fun decide(time: TimeSystem, state: NpcState): NpcBehavior = when (time.phase) {
        TimeSystem.DayPhase.NIGHT -> NpcBehavior.SLEEP
        TimeSystem.DayPhase.MORNING -> if (state.cell.row < 10) NpcBehavior.WORK else NpcBehavior.WALK
        TimeSystem.DayPhase.AFTERNOON -> NpcBehavior.WALK
        TimeSystem.DayPhase.EVENING -> NpcBehavior.IDLE
    }
}