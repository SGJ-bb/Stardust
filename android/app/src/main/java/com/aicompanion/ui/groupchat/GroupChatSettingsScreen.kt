package com.aicompanion.ui.groupchat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.aicompanion.groupchat.GroupChat
import com.aicompanion.groupchat.GroupChatManager
import com.aicompanion.models.CharacterCard
import com.aicompanion.character.CharacterCardManager
import com.aicompanion.theme.StradustTheme
import com.aicompanion.ui.components.ButtonVariant
import com.aicompanion.ui.components.StradustButton
import com.aicompanion.ui.components.StradustCard
import com.aicompanion.ui.components.StradustInput
import com.aicompanion.ui.components.StradustTopBar

/** 群聊成员区分色池（与 GroupChatScreen 保持一致） */
private val MEMBER_COLORS = listOf(
    0xFF9c7cff.toInt(), 0xFF64ffda.toInt(), 0xFF667eea.toInt(),
    0xFFffb347.toInt(), 0xFFe8a0bf.toInt(), 0xFF7fdbda.toInt(),
)

/** 发言模式选项 */
private data class SpeakModeOption(val value: String, val label: String, val desc: String)

private val SPEAK_MODES = listOf(
    SpeakModeOption("manual", "手动选择", "用户在聊天界面手动选择谁发言"),
    SpeakModeOption("ai_judge", "AI判定", "AI根据上下文判断每个成员是否说话"),
    SpeakModeOption("round_robin", "轮流发言", "所有成员依次全部发言"),
)

/**
 * 群聊设置页面
 *
 * 功能：
 * - 群名称编辑
 * - 发言模式选择（随机 / 轮流 / 响应式）
 * - 成员关系设定（多行文本）
 * - 成员列表显示与管理（添加 / 移除成员）
 * - 清空聊天记录（二次确认）
 * - 保存设置
 *
 * 后端：GroupChatManager.load/getGroup/updateGroup/clearMessages；
 *      成员名称与候选列表通过 CharacterCardManager 解析（与 HomeScreen 数据源一致）。
 *
 * @param groupId 群聊 ID
 * @param onBackClick 返回回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatSettingsScreen(
    groupId: String,
    onBackClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val colors = StradustTheme.colors

    val manager = remember { GroupChatManager(context) }
    val cardManager = remember { CharacterCardManager(context) }

    var group by remember { mutableStateOf<GroupChat?>(null) }
    var name by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf("") }
    var speakMode by remember { mutableStateOf("round_robin") }
    var saved by remember { mutableStateOf(false) }

    // 刷新计数器：清空记录等操作后用于触发状态刷新
    var refreshTick by remember { mutableIntStateOf(0) }
    // 是否已清空聊天记录（按钮文案反馈）
    var clearedFlag by remember { mutableStateOf(false) }

    // 添加成员对话框状态
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var allCards by remember { mutableStateOf<List<CharacterCard>>(emptyList()) }
    var selectedNewMemberIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    // 清空记录二次确认对话框
    var showClearDialog by remember { mutableStateOf(false) }

    // 初次加载群聊数据与全部角色卡（与 HomeScreen 数据源一致）
    LaunchedEffect(groupId) {
        val (loadedGroup, loadedCards) = withContext(Dispatchers.IO) {
            manager.load()
            val cards = cardManager.getAllCards()
            val g = manager.getGroup(groupId)
            Pair(g, cards)
        }
        allCards = loadedCards
        if (loadedGroup != null) {
            group = loadedGroup
            name = loadedGroup.name
            relationship = loadedGroup.relationshipSetting
            speakMode = loadedGroup.speakMode.ifBlank { "round_robin" }
        }
    }

    // 应用成员变更（添加 / 移除），本地同步 + 持久化
    fun applyMemberChange(newIds: List<String>) {
        val g = group ?: return
        val updated = g.copy(memberPersonaIds = newIds)
        manager.updateGroup(updated)
        group = updated
        saved = false
    }

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        StradustTopBar(title = "群聊设置", onBackClick = onBackClick)
        HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.3f), thickness = 0.5.dp)

        if (group == null) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text("群聊不存在", color = colors.textMuted, fontSize = 14.sp)
            }
        } else {
            val memberIds = group?.memberPersonaIds.orEmpty()

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ===== 群名称编辑 =====
                item {
                    StradustCard {
                        Text("群名称", color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        StradustInput(
                            value = name,
                            onValueChange = { name = it; saved = false },
                            hint = "输入群名称",
                            singleLine = true,
                        )
                    }
                }

                // ===== 发言模式选择 =====
                item {
                    StradustCard {
                        Text("发言模式", color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SPEAK_MODES.forEach { opt ->
                                FilterChip(
                                    selected = speakMode == opt.value,
                                    onClick = { speakMode = opt.value; saved = false },
                                    label = { Text(opt.label, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = colors.primary,
                                        selectedLabelColor = colors.onPrimary,
                                    ),
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = SPEAK_MODES.find { it.value == speakMode }?.desc ?: "",
                            color = colors.textMuted,
                            fontSize = 11.sp,
                        )
                    }
                }

                // ===== 成员关系设定 =====
                item {
                    StradustCard {
                        Text("成员关系设定", color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            text = "描述成员之间的关系，AI 会参考此设定演绎互动",
                            color = colors.textMuted,
                            fontSize = 11.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        StradustInput(
                            value = relationship,
                            onValueChange = { relationship = it; saved = false },
                            hint = "例如：A 和 B 是青梅竹马；C 性格内向；D 和 E 是恋人...",
                            maxLines = 5,
                        )
                    }
                }

                // ===== 成员列表显示与管理（添加 / 移除） =====
                item {
                    StradustCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Group,
                                contentDescription = null,
                                tint = colors.tertiary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "群成员（${memberIds.size}）",
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                            )
                            Spacer(Modifier.weight(1f))
                            // 添加成员按钮
                            IconButton(onClick = {
                                selectedNewMemberIds = emptySet()
                                showAddMemberDialog = true
                            }) {
                                Icon(
                                    Icons.Default.PersonAdd,
                                    contentDescription = "添加成员",
                                    tint = colors.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        if (memberIds.isEmpty()) {
                            Text("暂无成员，点击右上角图标添加", color = colors.textMuted, fontSize = 12.sp)
                        } else {
                            memberIds.forEachIndexed { idx, pid ->
                                val pname = allCards.find { it.id == pid }?.name ?: "未知成员"
                                val memberColor = Color(MEMBER_COLORS[idx % MEMBER_COLORS.size])
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 5.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(memberColor.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = pname.firstOrNull()?.toString() ?: "?",
                                            color = memberColor,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(pname, color = colors.textPrimary, fontSize = 13.sp)
                                    Spacer(Modifier.weight(1f))
                                    // 移除成员按钮
                                    IconButton(onClick = {
                                        val newIds = memberIds.filter { it != pid }
                                        applyMemberChange(newIds)
                                    }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "移除成员",
                                            tint = colors.textMuted,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ===== 保存按钮 =====
                item {
                    StradustButton(
                        text = if (saved) "已保存 ✓" else "保存设置",
                        onClick = {
                            val g = group ?: return@StradustButton
                            val updated = g.copy(
                                name = name.ifBlank { "未命名群" },
                                speakMode = speakMode,
                                relationshipSetting = relationship,
                            )
                            manager.updateGroup(updated)
                            group = updated
                            saved = true
                        },
                        variant = if (saved) ButtonVariant.TONAL else ButtonVariant.FILLED,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // ===== 清空聊天记录（危险操作） =====
                item {
                    StradustCard {
                        Text("危险操作", color = StradustTheme.colors.error, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "清空该群聊的所有聊天记录，此操作不可撤销",
                            color = colors.textMuted,
                            fontSize = 11.sp,
                        )
                        Spacer(Modifier.height(10.dp))
                        StradustButton(
                            text = if (clearedFlag) "已清空 ✓" else "清空聊天记录",
                            onClick = { showClearDialog = true },
                            variant = ButtonVariant.OUTLINED,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    // ===== 清空聊天记录二次确认对话框 =====
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空聊天记录", color = colors.textPrimary, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "确定要清空此群聊的所有聊天记录吗？此操作不可撤销。",
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    manager.clearMessages(groupId)
                    showClearDialog = false
                    clearedFlag = true
                    refreshTick++
                }) {
                    Text("清空", color = StradustTheme.colors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消", color = colors.textMuted)
                }
            },
        )
    }

    // ===== 添加成员对话框（多选角色卡，排除已是成员） =====
    if (showAddMemberDialog) {
        val existingIds = group?.memberPersonaIds?.toSet() ?: emptySet()
        val candidates = allCards.filter { it.id !in existingIds }

        AlertDialog(
            onDismissRequest = { showAddMemberDialog = false },
            title = { Text("添加成员", color = colors.textPrimary, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (candidates.isEmpty()) {
                        Text(
                            "没有可添加的角色，请先创建角色",
                            color = colors.textMuted,
                            fontSize = 12.sp,
                        )
                    } else {
                        candidates.forEach { p ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Checkbox(
                                    checked = p.id in selectedNewMemberIds,
                                    onCheckedChange = { checked ->
                                        selectedNewMemberIds = if (checked) {
                                            selectedNewMemberIds + p.id
                                        } else {
                                            selectedNewMemberIds - p.id
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = colors.primary,
                                        uncheckedColor = colors.textMuted,
                                    ),
                                )
                                Text(
                                    text = p.name,
                                    color = colors.textPrimary,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (selectedNewMemberIds.isNotEmpty()) {
                            val newIds = (group?.memberPersonaIds ?: emptyList()) + selectedNewMemberIds.toList()
                            applyMemberChange(newIds)
                        }
                        showAddMemberDialog = false
                    },
                    enabled = selectedNewMemberIds.isNotEmpty(),
                ) {
                    Text("添加", color = colors.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddMemberDialog = false }) {
                    Text("取消", color = colors.textMuted)
                }
            },
        )
    }
}
