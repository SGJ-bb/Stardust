package com.aicompanion.ui.nickname

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicompanion.theme.StradustTheme
import com.aicompanion.ui.NicknameEntry
import com.aicompanion.ui.NicknameManager
import com.aicompanion.ui.components.ButtonVariant
import com.aicompanion.ui.components.StradustButton
import com.aicompanion.ui.components.StradustCard
import com.aicompanion.ui.components.StradustInput
import com.aicompanion.ui.components.StradustTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 昵称管理界面
 *
 * 功能：
 * - 手动昵称设置（输入框 + 保存/清除按钮）
 * - 发现的昵称列表（昵称/来源/时间，可删除）
 * - 活跃昵称列表显示
 */
@Composable
fun NicknameScreen(onBackClick: () -> Unit = {}) {
    val context = LocalContext.current
    val manager = remember { NicknameManager(context) }
    var refreshTick by remember { mutableStateOf(0) }
    var inputText by remember { mutableStateOf("") }
    // 手动添加昵称对话框状态
    var showAddDialog by remember { mutableStateOf(false) }
    var dialogNickname by remember { mutableStateOf("") }
    var dialogSource by remember { mutableStateOf("") }

    val manualNickname by produceState("", refreshTick) {
        value = withContext(Dispatchers.IO) { manager.getManualNickname() }
    }
    val isManualSet by produceState(false, refreshTick) {
        value = withContext(Dispatchers.IO) { manager.isManualSet() }
    }
    val discovered by produceState<List<NicknameEntry>>(emptyList(), refreshTick) {
        value = withContext(Dispatchers.IO) { manager.getAllDiscovered() }
    }
    val active by produceState<List<String>>(emptyList(), refreshTick) {
        value = withContext(Dispatchers.IO) { manager.getActiveNicknames() }
    }

    Box(modifier = Modifier.fillMaxSize().background(StradustTheme.colors.background)) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { StradustTopBar(title = "昵称管理", onBackClick = onBackClick) }
            item { Spacer(Modifier.height(16.dp)) }

            // 手动昵称设置区域
            item {
                StradustCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(
                        text = "手动昵称",
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "设置后将以此昵称作为对用户的称呼",
                        color = StradustTheme.colors.textMuted,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    StradustInput(
                        value = inputText,
                        onValueChange = { inputText = it },
                        hint = if (isManualSet) "当前：$manualNickname" else "输入昵称",
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StradustButton(
                            text = "保存",
                            onClick = {
                                manager.setManualNickname(inputText.trim())
                                inputText = ""
                                refreshTick++
                            },
                            modifier = Modifier.weight(1f),
                            enabled = inputText.isNotBlank(),
                        )
                        StradustButton(
                            text = "清除",
                            onClick = {
                                manager.setManualNickname("")
                                inputText = ""
                                refreshTick++
                            },
                            variant = ButtonVariant.OUTLINED,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (isManualSet) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "当前昵称：$manualNickname",
                            color = StradustTheme.colors.tertiary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }

            // 活跃昵称列表
            item {
                StradustCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(
                        text = "活跃昵称",
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (active.isEmpty()) {
                        Text(
                            text = "暂无活跃昵称",
                            color = StradustTheme.colors.textMuted,
                            fontSize = 13.sp,
                        )
                    } else {
                        active.forEachIndexed { index, name ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "${index + 1}.",
                                    color = StradustTheme.colors.textMuted,
                                    fontSize = 13.sp,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = name,
                                    color = StradustTheme.colors.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }

            // 发现的昵称列表
            item {
                StradustCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "发现的昵称",
                            color = StradustTheme.colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "共 ${discovered.size} 条",
                            color = StradustTheme.colors.textMuted,
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    // 手动添加昵称按钮（始终可见）
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = StradustTheme.colors.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "手动添加",
                            color = StradustTheme.colors.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    dialogNickname = ""
                                    dialogSource = ""
                                    showAddDialog = true
                                }
                                .padding(vertical = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (discovered.isEmpty()) {
                        Text(
                            text = "暂无发现的昵称",
                            color = StradustTheme.colors.textMuted,
                            fontSize = 13.sp,
                        )
                    } else {
                        discovered.forEach { entry ->
                            NicknameEntryRow(
                                entry = entry,
                                onDelete = {
                                    manager.removeDiscovered(entry.nickname)
                                    refreshTick++
                                },
                            )
                            if (entry != discovered.last()) {
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        StradustButton(
                            text = "全部清除",
                            onClick = {
                                manager.clearAll()
                                refreshTick++
                            },
                            variant = ButtonVariant.OUTLINED,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }

        // 手动添加昵称对话框
        if (showAddDialog) {
            AddNicknameDialog(
                nickname = dialogNickname,
                source = dialogSource,
                onNicknameChange = { dialogNickname = it },
                onSourceChange = { dialogSource = it },
                onConfirm = {
                    // 昵称非空才添加，来源为空时默认 "manual"
                    if (dialogNickname.isNotBlank()) {
                        manager.addDiscovered(
                            dialogNickname.trim(),
                            if (dialogSource.isBlank()) "manual" else dialogSource.trim(),
                        )
                        showAddDialog = false
                        refreshTick++
                    }
                },
                onDismiss = { showAddDialog = false },
            )
        }
    }
}

@Composable
private fun NicknameEntryRow(entry: NicknameEntry, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                StradustTheme.colors.surfaceContainerLow.copy(alpha = 0.4f),
                RoundedCornerShape(12.dp),
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.nickname,
                color = StradustTheme.colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Row {
                Text(
                    text = "来源：${entry.source}",
                    color = StradustTheme.colors.textMuted,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = formatNicknameTime(entry.timestamp),
                    color = StradustTheme.colors.textMuted,
                    fontSize = 11.sp,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onDelete() }
                .padding(6.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "删除",
                tint = StradustTheme.colors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun formatNicknameTime(timestamp: Long): String {
    if (timestamp <= 0L) return "—"
    return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA)
        .format(java.util.Date(timestamp))
}

/**
 * 手动添加昵称对话框
 *
 * 字段：
 * - nickname：昵称（必填）
 * - source：来源（可选，留空时默认 "manual"）
 */
@Composable
private fun AddNicknameDialog(
    nickname: String,
    source: String,
    onNicknameChange: (String) -> Unit,
    onSourceChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "手动添加昵称",
                color = StradustTheme.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(modifier = Modifier.imePadding()) {
                // 昵称输入
                StradustInput(
                    value = nickname,
                    onValueChange = onNicknameChange,
                    hint = "昵称（必填）",
                )
                Spacer(Modifier.height(12.dp))
                // 来源输入
                StradustInput(
                    value = source,
                    onValueChange = onSourceChange,
                    hint = "来源（可选，默认 manual）",
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
