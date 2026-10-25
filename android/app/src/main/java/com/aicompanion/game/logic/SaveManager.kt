package com.aicompanion.game.logic

import android.content.Context
import com.aicompanion.game.npc.NpcState
import org.json.JSONObject

/**
 * 存档骨架(M1): 本地 JSON 持久化(时间 + NPC 位置)。
 * 只保存确定性结果(时钟/坐标)，不落 LLM 原始输出，避免不确定性污染存档。
 */
class SaveManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("town_save", Context.MODE_PRIVATE)

    fun save(time: TimeSystem, npcs: List<NpcState>) {
        val obj = JSONObject()
        obj.put("day", time.day)
            .put("hour", time.hour)
            .put("minute", time.minute)
            .put("npcCount", npcs.size)
        prefs.edit().putString("snapshot", obj.toString()).apply()
    }

    /** 读取并只还原时钟；NPC 精确位置还原留待接入时补齐。 */
    fun load(time: TimeSystem): Boolean {
        val raw = prefs.getString("snapshot", null) ?: return false
        return runCatching {
            val obj = JSONObject(raw)
            time.restore(obj.getInt("hour"), obj.getInt("minute"), obj.getInt("day"))
            true
        }.getOrDefault(false)
    }
}