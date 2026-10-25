package com.aicompanion.ui.personality

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicompanion.persona.PersonaManager
import com.aicompanion.personality.PersonalityProfile
import com.aicompanion.personality.PersonalityPrompt
import com.aicompanion.personality.PersonalityRuntime
import com.aicompanion.personality.PersonalityStore
import com.aicompanion.personality.Situation
import com.aicompanion.personality.Traits
import com.aicompanion.theme.StradustTheme
import com.aicompanion.ui.components.ButtonSize
import com.aicompanion.ui.components.ButtonVariant
import com.aicompanion.ui.components.CardVariant
import com.aicompanion.ui.components.StradustButton
import com.aicompanion.ui.components.StradustCard
import com.aicompanion.ui.components.StradustEmptyState
import com.aicompanion.ui.components.StradustTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 人格面板。
 *
 * 存在的意义是「让用户能看见这套系统在做什么」：
 * 现在 AI 的性格有五维数值、有 if-then 签名、有关系状态、还会随时间微调，
 * 这些如果只存在提示词里，用户体感仍然是"她怎么突然不一样了"。
 */
@Composable
fun PersonalityScreen(onBackClick: () -> Unit = {}) {
    val context = LocalContext.current
    val colors = StradustTheme.colors
    val scope = rememberCoroutineScope()

    var personaName by remember { mutableStateOf("") }
    var personaId by remember { mutableStateOf("") }
    var profile by remember { mutableStateOf<PersonalityProfile?>(null) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var driftBusy by remember { mutableStateOf(false) }
    var driftMsg by remember { mutableStateOf("") }
    var reloadTick by remember { mutableStateOf(0) }

    LaunchedEffect(reloadTick) {
        withContext(Dispatchers.IO) {
            val pm = PersonaManager(context)
            pm.load()
            val p = runCatching { pm.getActivePersona() }.getOrNull()
            personaId = p?.id ?: ""
            personaName = p?.name ?: ""
            // 没有画像时做一次迁移，保证面板不是空的
            profile = if (p != null) {
                PersonalityStore(context).loadOrMigrate(
                    p.id, p.name, p.personality, p.speechStyle,
                )
            } else null
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        StradustTopBar(title = "人格", subtitle = personaName.ifBlank { null }, onBackClick = onBackClick)

        if (profile == null) {
            StradustEmptyState(
                emoji = "🧠",
                title = "还没有人格画像",
                hint = "先在角色设定里填写性格描述，然后回到这里点「让 AI 建立画像」",
                modifier = Modifier.fillMaxSize(),
            )
            return@Column
        }

        val p = profile!!
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ===== 五维 =====
            item {
                StradustCard {
                    SectionLabel("倾向性特质", "跨情境的稳定倾向，变化以周/月计")
                    Spacer(Modifier.height(10.dp))
                    TraitBar("开放性", p.traits.openness)
                    TraitBar("尽责性", p.traits.conscientiousness)
                    TraitBar("外向性", p.traits.extraversion)
                    TraitBar("宜人性", p.traits.agreeableness)
                    TraitBar("神经质", p.traits.neuroticism)
                }
            }

            // ===== 表达方式 =====
            item {
                StradustCard {
                    SectionLabel("表达方式", "直接约束句式与语气，比形容词更好用")
                    Spacer(Modifier.height(10.dp))
                    ChipRow("话量", pct(p.traits.verbosity))
                    ChipRow("温度", pct(p.traits.warmth))
                    ChipRow("直给", pct(p.traits.directness))
                    ChipRow("玩笑", pct(p.traits.humor))
                    ChipRow("主动", pct(p.traits.initiative))
                }
            }

            // ===== 行为签名 =====
            if (p.signatures.isNotEmpty()) {
                item {
                    StradustCard {
                        SectionLabel(
                            "行为签名",
                            "「什么情况 → 怎么反应」。人格的一致性体现在这里，而不是处处一样",
                        )
                        Spacer(Modifier.height(10.dp))
                        p.signatures.sortedByDescending { it.intensity }.forEach { s ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Text(
                                    text = situationLabel(s.situation),
                                    color = colors.primary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.width(72.dp),
                                )
                                Text(
                                    text = s.response,
                                    color = colors.textSecondary,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "${(s.intensity * 100).toInt()}",
                                    color = colors.textMuted,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
            }

            // ===== 底线与口头禅 =====
            if (p.narrative.taboos.isNotEmpty() || p.narrative.catchphrases.isNotEmpty()) {
                item {
                    StradustCard(variant = CardVariant.OUTLINED) {
                        if (p.narrative.taboos.isNotEmpty()) {
                            SectionLabel("底线", "这些是硬约束，任何情况下都不会做")
                            Spacer(Modifier.height(6.dp))
                            p.narrative.taboos.forEach {
                                Text("· $it", color = colors.textSecondary, fontSize = 13.sp, lineHeight = 19.sp)
                            }
                        }
                        if (p.narrative.catchphrases.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            SectionLabel("口头禅", null)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = p.narrative.catchphrases.joinToString("　"),
                                color = colors.textSecondary,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }

            // ===== 关系与状态 =====
            item {
                StradustCard {
                    SectionLabel("此刻的状态", "心情变化快，关系变化慢，人格几乎不变")
                    Spacer(Modifier.height(10.dp))
                    ChipRow("信任", pct(p.relationship.trust))
                    ChipRow("亲密", pct(p.relationship.intimacy))
                    ChipRow("投入", pct(p.relationship.commitment))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "阶段：${stageLabel(p.relationship.stage.name)}　心情：${moodLabel(p.mood.valence)}",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                    )
                }
            }

            // ===== 一致性自检 =====
            item {
                StradustCard(variant = CardVariant.OUTLINED) {
                    SectionLabel(
                        "一致性自检",
                        "让 TA 用 10 道简版人格题给自己打分，再和画像比对。偏离超过 25% 说明人设已经开始走样。",
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StradustButton(
                            text = "测一次",
                            onClick = {
                                if (driftBusy) return@StradustButton
                                driftBusy = true
                                driftMsg = ""
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        val client = com.aicompanion.AppContainer.apiClient
                                            ?: return@withContext "没有可用的模型配置"
                                        val raw = runCatching {
                                            client.sendSimplePrompt(
                                                "你在扮演一个角色。请以这个角色自己的身份，安静地回答问题，" +
                                                    "只输出要求格式，不要解释。",
                                                com.aicompanion.personality.PersonalityAudit.ipipProbe(),
                                            )?.text
                                        }.getOrNull() ?: return@withContext "模型没有返回内容"
                                        val measured = com.aicompanion.personality.PersonalityAudit.parseIpip(raw)
                                        if (measured.isEmpty()) {
                                            return@withContext "回答格式没解析出来，稍后重试"
                                        }
                                        val d = com.aicompanion.personality.PersonalityAudit.driftScore(measured, p.traits)
                                        "偏离 ${(d * 100).toInt()}% · ${com.aicompanion.personality.PersonalityAudit.driftVerdict(d)}"
                                    }
                                    driftMsg = result
                                    driftBusy = false
                                }
                            },
                            size = ButtonSize.SMALL,
                            loading = driftBusy,
                            variant = ButtonVariant.TONAL,
                        )
                        if (driftMsg.isNotBlank()) {
                            Spacer(Modifier.width(12.dp))
                            Text(driftMsg, color = colors.textSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }

            // ===== 操作 =====
            item {
                StradustCard(variant = CardVariant.FILLED) {
                    SectionLabel(
                        "重建人格画像",
                        "用 AI 把角色设定重新结构化成三层画像与 if-then 签名。已有内容会合并，手改过的字段不会被覆盖。",
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StradustButton(
                            text = if (PersonalityRuntime.hasProfile(context, personaId)) "重新生成" else "生成画像",
                            onClick = {
                                if (busy) return@StradustButton
                                busy = true
                                msg = ""
                                scope.launch {
                                    val ok = withContext(Dispatchers.IO) {
                                        val client = com.aicompanion.AppContainer.apiClient
                                        val pm = PersonaManager(context).also { it.load() }
                                        val persona = runCatching { pm.getActivePersona() }.getOrNull()
                                        if (client == null || persona == null) {
                                            false
                                        } else {
                                            PersonalityRuntime.bootstrap(
                                                context = context,
                                                personaId = persona.id,
                                                name = persona.name,
                                                description = persona.prompt,
                                                personalityText = persona.personality,
                                                speechStyle = persona.speechStyle,
                                            ) { system, user ->
                                                runCatching {
                                                    client.sendSimplePrompt(system, user)?.text
                                                }.getOrNull()
                                            }
                                        }
                                    }
                                    msg = if (ok) "已生成，往下翻可以看到签名" else "生成失败：检查网络与 API 配置"
                                    busy = false
                                    reloadTick++
                                }
                            },
                            size = ButtonSize.SMALL,
                            loading = busy,
                        )
                        if (msg.isNotBlank()) {
                            Spacer(Modifier.width(12.dp))
                            Text(msg, color = colors.textMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(title: String, hint: String?) {
    val colors = StradustTheme.colors
    Column {
        Text(title, color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        if (!hint.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(hint, color = colors.textMuted, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}

/** 0..1 的横向条：比数字更快被读出来 */
@Composable
private fun TraitBar(label: String, value: Float) {
    val colors = StradustTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = colors.textSecondary, fontSize = 12.sp, modifier = Modifier.width(56.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .background(colors.surfaceContainerHigh, RoundedCornerShape(3.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(value.coerceIn(0f, 1f))
                    .height(6.dp)
                    .background(colors.primary, RoundedCornerShape(3.dp)),
            )
        }
        Text(
            text = pct(value),
            color = colors.textMuted,
            fontSize = 11.sp,
            modifier = Modifier.width(38.dp).padding(start = 8.dp),
        )
    }
}

@Composable
private fun ChipRow(label: String, value: String) {
    val colors = StradustTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = colors.textSecondary, fontSize = 12.sp, modifier = Modifier.width(56.dp))
        Box(
            modifier = Modifier
                .background(colors.surfaceContainerHigh, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 3.dp),
        ) {
            Text(value, color = colors.textPrimary, fontSize = 12.sp)
        }
    }
}

private fun pct(v: Float): String = "${(v.coerceIn(0f, 1f) * 100).toInt()}%"

private fun situationLabel(s: Situation): String = when (s) {
    Situation.PRAISE -> "被夸"
    Situation.CRITICISM -> "被批评"
    Situation.NEGLECT -> "被冷落"
    Situation.REQUEST_HELP -> "被求助"
    Situation.TEASE -> "被撒娇"
    Situation.CONFLICT -> "起争执"
    Situation.LATE_NIGHT -> "深夜"
    Situation.FAREWELL -> "告别"
    Situation.BOUNDARY -> "越界请求"
    Situation.GOOD_NEWS -> "好消息"
    Situation.BAD_NEWS -> "坏消息"
    Situation.ROUTINE -> "日常"
}

private fun stageLabel(name: String): String = when (name) {
    "STRANGER" -> "刚认识"
    "FAMILIAR" -> "算熟了"
    "CLOSE" -> "关系近"
    "DEPENDENT" -> "很依赖"
    else -> name
}

private fun moodLabel(valence: Float): String = when {
    valence > 0.35f -> "不错"
    valence > 0.1f -> "还行"
    valence > -0.2f -> "一般"
    valence > -0.5f -> "有点低"
    else -> "很差"
}

/** 供外部预览 Traits 的默认文案（避免未使用告警） */
internal fun Traits.summary(): String =
    "话量 ${pct(verbosity)} / 温度 ${pct(warmth)} / 直给 ${pct(directness)}"
