package com.aicompanion.personality

import android.content.Context
import org.json.JSONObject

/**
 * 人格画像的持久化与迁移。
 *
 * 存储策略：每个角色一份 SharedPreferences（`personality_<personaId>`），
 * 内容是 [PersonalityProfile] 的 JSON。
 *
 * 迁移策略：首次读取时若没有结构化画像，用旧字段（personality / speechStyle）
 * 生成一份低置信度的降级画像（`rawText` 保留原文），
 * 之后的归纳与作者编辑都会在它之上合并。**旧用户不会丢性格**。
 */
class PersonalityStore(private val context: Context) {

    private fun prefs(personaId: String) =
        context.getSharedPreferences("personality_$personaId", Context.MODE_PRIVATE)

    fun load(personaId: String): PersonalityProfile? {
        val raw = prefs(personaId).getString(KEY_PROFILE, null) ?: return null
        return runCatching { PersonalityProfile.fromJson(JSONObject(raw)) }.getOrNull()
    }

    fun save(personaId: String, profile: PersonalityProfile) {
        prefs(personaId).edit().putString(KEY_PROFILE, profile.toJson().toString()).apply()
    }

    /**
     * 读画像；没有就用旧字段迁移一份出来并落盘。
     *
     * 注意：这里**不写回**旧字段，旧字段继续作为"作者原话"保留，
     * 在结构化画像内容为空时仍可作为兜底注入。
     */
    fun loadOrMigrate(
        personaId: String,
        name: String,
        personalityText: String,
        speechStyle: String,
    ): PersonalityProfile {
        load(personaId)?.let { return it }
        val migrated = PersonalityProfile.fromLegacy(name, personalityText, speechStyle)
        if (personalityText.isNotBlank() || speechStyle.isNotBlank()) {
            save(personaId, migrated)
        }
        return migrated
    }

    /** 合并一份新来源（作者编辑 / 对话归纳）并落盘 */
    fun merge(personaId: String, incoming: PersonalityProfile): PersonalityProfile {
        val base = load(personaId) ?: PersonalityProfile()
        val merged = PersonalityMerge.merge(base, incoming)
        save(personaId, merged)
        return merged
    }

    /**
     * 同步好感度 → 关系状态。
     *
     * 不在每轮都调，而是由上层在好感度变化后调用一次，
     * 避免把"关系"变成好感度的镜像（关系应比好感度更稳）。
     */
    fun syncAffection(personaId: String, affectionLevel: Int) {
        val p = load(personaId) ?: return
        val target = Relationship.fromAffection(affectionLevel)
        val updated = p.copy(
            relationship = PersonalityDynamics.evolveRelationship(p.relationship, target, alpha = 0.5f),
            updatedAt = System.currentTimeMillis(),
        )
        save(personaId, updated)
    }

    /** 记录一次情绪（瞬时，不落盘也无所谓，落盘是为了跨会话轻微延续） */
    fun updateMood(personaId: String, target: Mood) {
        val p = load(personaId) ?: return
        save(
            personaId,
            p.copy(
                mood = PersonalityDynamics.evolveMood(p.mood, target),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    /** 人格变化日志（做面板"最近变化"用，也可用于 30 天累计限幅） */
    fun history(personaId: String): List<PersonalityDynamics.TraitChange> {
        val raw = prefs(personaId).getString(KEY_HISTORY, null) ?: return emptyList()
        return runCatching {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                PersonalityDynamics.TraitChange(
                    field = o.optString("f"),
                    before = o.optDouble("b", 0.5).toFloat(),
                    after = o.optDouble("a", 0.5).toFloat(),
                    at = o.optLong("t", 0L),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun appendHistory(personaId: String, changes: List<PersonalityDynamics.TraitChange>) {
        if (changes.isEmpty()) return
        val list = history(personaId).toMutableList()
        list.addAll(changes)
        // 只保留 60 天内的记录，避免无限增长
        val cutoff = System.currentTimeMillis() - 60L * 24 * 3600 * 1000
        val kept = list.filter { it.at >= cutoff }
        val arr = org.json.JSONArray()
        kept.forEach {
            arr.put(
                JSONObject()
                    .put("f", it.field)
                    .put("b", it.before.toDouble())
                    .put("a", it.after.toDouble())
                    .put("t", it.at)
            )
        }
        prefs(personaId).edit().putString(KEY_HISTORY, arr.toString()).apply()
    }


    /**
     * 证据要跨轮累积，否则特质永远动不了。
     *
     * 之前每轮只把"当轮"的 1 条证据交给演化函数，权重 0.5 < 门槛 3，
     * 结果心情一直在变、签名计数在涨，唯独特质恒定 —— 看起来像"人格不会成长"。
     */
    fun evidences(personaId: String): List<PersonalityDynamics.TraitEvidence> {
        val raw = prefs(personaId).getString(KEY_EVIDENCE, null) ?: return emptyList()
        val cutoff = System.currentTimeMillis() - PersonalityDynamics.WINDOW_EVIDENCE_MS
        return runCatching {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val at = o.optLong("t", 0L)
                if (at < cutoff) return@mapNotNull null
                PersonalityDynamics.TraitEvidence(
                    field = o.optString("f"),
                    direction = o.optDouble("d", 0.0).toFloat(),
                    at = at,
                    weight = o.optDouble("w", 1.0).toFloat(),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun appendEvidences(personaId: String, list: List<PersonalityDynamics.TraitEvidence>) {
        if (list.isEmpty()) return
        val kept = evidences(personaId) + list
        val arr = org.json.JSONArray()
        kept.takeLast(400).forEach {
            arr.put(
                JSONObject()
                    .put("f", it.field)
                    .put("d", it.direction.toDouble())
                    .put("w", it.weight.toDouble())
                    .put("t", it.at)
            )
        }
        prefs(personaId).edit().putString(KEY_EVIDENCE, arr.toString()).apply()
    }

    private companion object {
        const val KEY_PROFILE = "profile_json"
        const val KEY_EVIDENCE = "trait_evidence"
        const val KEY_HISTORY = "trait_history"
    }
}
