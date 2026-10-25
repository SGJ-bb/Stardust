package com.aicompanion.game.npc

import com.aicompanion.game.world.MapCell

/** NPC 可处行为(M1)。 */
enum class NpcBehavior { IDLE, WALK, WORK, SLEEP }

/**
 * NPC 的确定性状态(M1) —— 可存档还原，决定结果不受 LLM 影响。
 */
data class NpcState(
    val id: String,
    var cell: MapCell,
    var behavior: NpcBehavior,
    var scheduleKey: String = "default"
) {
    var facingLeft: Boolean = false
}