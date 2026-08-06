package com.aicompanion.ui.character

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicompanion.character.CharacterCardManager
import com.aicompanion.models.WorldInfo
import com.aicompanion.models.WorldInfoEntry
import com.aicompanion.network.ApiClient
import com.aicompanion.settings.SettingsManager
import com.aicompanion.theme.StradustTheme
import com.aicompanion.ui.components.ButtonSize
import com.aicompanion.ui.components.ButtonVariant
import com.aicompanion.ui.components.StradustButton
import com.aicompanion.ui.components.StradustCard
import com.aicompanion.ui.components.StradustInput
import com.aicompanion.ui.components.StradustTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 世界书独立页面
 *
 * 功能：
 * - 世界书列表（所有已创建的世界书，可展开查看条目）
 * - 添加/编辑/删除世界书
 * - 添加/编辑/删除世界书条目
 * - AI辅助生成条目内容
 */
@Composable
fun WorldBookScreen(onBackClick: () -> Unit = {}) {
    val context = LocalContext.current
    val manager = remember { CharacterCardManager(context) }
    var refreshTick by remember { mutableStateOf(0) }

    // 对话框状态
    var showAddWorldDialog by remember { mutableStateOf(false) }
    var editingWorldInfo by remember { mutableStateOf<WorldInfo?>(null) }
    var entryDialogOpen by remember { mutableStateOf(false) }
    var entryDialogWorldId by remember { mutableStateOf("") }
    var editingEntry by remember { mutableStateOf<WorldInfoEntry?>(null) }

    // 删除确认状态
    var showDeleteWorldConfirm by remember { mutableStateOf(false) }
    var worldToDelete by remember { mutableStateOf<WorldInfo?>(null) }
    var showDeleteEntryConfirm by remember { mutableStateOf(false) }
    var entryToDeleteInfo by remember { mutableStateOf<Pair<String, String>?>(null) } // (worldId, entryId)

    val worldInfos = remember(refreshTick) { manager.getAllWorldInfos() }

    Box(modifier = Modifier.fillMaxSize().background(StradustTheme.colors.background)) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { StradustTopBar(title = "世界书", onBackClick = onBackClick) }
            item { Spacer(Modifier.height(12.dp)) }

            // 顶部操作栏
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "世界书列表",
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f),
                    )
                    StradustButton(
                        text = "添加世界书",
                        onClick = {
                            editingWorldInfo = null
                            showAddWorldDialog = true
                        },
                        size = ButtonSize.SMALL,
                    )
                }
            }

            item { Spacer(Modifier.height(12.dp)) }

            if (worldInfos.isEmpty()) {
                item {
                    StradustCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Text(
                            text = "暂无世界书，点击上方「添加世界书」创建",
                            color = StradustTheme.colors.textMuted,
                            fontSize = 13.sp,
                        )
                    }
                }
            }

            items(worldInfos.size, key = { worldInfos[it].id }) { index ->
                val wi = worldInfos[index]
                WorldBookItem(
                    worldInfo = wi,
                    onEdit = { editingWorldInfo = wi },
                    onDelete = {
                        worldToDelete = wi
                        showDeleteWorldConfirm = true
                    },
                    onAddEntry = {
                        entryDialogWorldId = wi.id
                        editingEntry = null
                        entryDialogOpen = true
                    },
                    onEditEntry = { entry ->
                        entryDialogWorldId = wi.id
                        editingEntry = entry
                        entryDialogOpen = true
                    },
                    onDeleteEntry = { eid ->
                        entryToDeleteInfo = Pair(wi.id, eid)
                        showDeleteEntryConfirm = true
                    },
                )
                Spacer(Modifier.height(8.dp))
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    // 添加/编辑世界书对话框
    if (showAddWorldDialog || editingWorldInfo != null) {
        WorldBookEditDialog(
            initialWorldInfo = editingWorldInfo,
            onDismiss = {
                showAddWorldDialog = false
                editingWorldInfo = null
            },
            onConfirm = { wi ->
                if (editingWorldInfo != null) {
                    manager.updateWorldInfo(wi)
                } else {
                    manager.addWorldInfo(wi)
                }
                refreshTick++
                showAddWorldDialog = false
                editingWorldInfo = null
            },
        )
    }

    // 世界书条目 添加/编辑 对话框
    if (entryDialogOpen) {
        EntryEditDialog(
            initialEntry = editingEntry,
            onDismiss = {
                entryDialogOpen = false
                editingEntry = null
                entryDialogWorldId = ""
            },
            onConfirm = { entry ->
                if (editingEntry != null) {
                    val wi = worldInfos.find { it.id == entryDialogWorldId }
                    if (wi != null) {
                        val updatedEntries = wi.entries.map { if (it.id == entry.id) entry else it }
                        manager.updateWorldInfo(wi.copy(entries = updatedEntries))
                    }
                } else {
                    manager.addEntryToWorldInfo(entryDialogWorldId, entry)
                }
                refreshTick++
                entryDialogOpen = false
                editingEntry = null
                entryDialogWorldId = ""
            },
        )
    }

    // 删除世界书确认弹窗
    if (showDeleteWorldConfirm && worldToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteWorldConfirm = false
                worldToDelete = null
            },
            title = { Text("删除世界书") },
            text = { Text("确定要删除世界书「${worldToDelete!!.name.ifBlank { "未命名" }}」吗？所有条目将一并删除") },
            confirmButton = {
                TextButton(
                    onClick = {
                        manager.deleteWorldInfo(worldToDelete!!.id)
                        refreshTick++
                        showDeleteWorldConfirm = false
                        worldToDelete = null
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteWorldConfirm = false
                        worldToDelete = null
                    },
                ) { Text("取消") }
            },
        )
    }

    // 删除条目确认弹窗
    if (showDeleteEntryConfirm && entryToDeleteInfo != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteEntryConfirm = false
                entryToDeleteInfo = null
            },
            title = { Text("删除条目") },
            text = { Text("确定要删除此条目吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        manager.removeEntryFromWorldInfo(entryToDeleteInfo!!.first, entryToDeleteInfo!!.second)
                        refreshTick++
                        showDeleteEntryConfirm = false
                        entryToDeleteInfo = null
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteEntryConfirm = false
                        entryToDeleteInfo = null
                    },
                ) { Text("取消") }
            },
        )
    }
}

// ===== 世界书卡片组件 =====

@Composable
private fun WorldBookItem(
    worldInfo: WorldInfo,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddEntry: () -> Unit,
    onEditEntry: (WorldInfoEntry) -> Unit,
    onDeleteEntry: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    StradustCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        // 世界书标题行
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = worldInfo.name.ifBlank { "未命名世界书" },
                        color = StradustTheme.colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                StradustTheme.colors.primaryContainer,
                                RoundedCornerShape(6.dp),
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "${worldInfo.entries.size} 条",
                            color = StradustTheme.colors.onPrimaryContainer,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            // 编辑按钮
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onEdit() }
                    .padding(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "编辑世界书",
                    tint = StradustTheme.colors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
            // 删除按钮
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onDelete() }
                    .padding(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除世界书",
                    tint = StradustTheme.colors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
            // 展开/收起
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { expanded = !expanded }
                    .padding(6.dp),
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = StradustTheme.colors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // 条目列表（展开时显示）
        AnimatedVisibility(visible = expanded) {
            Column {
                if (worldInfo.entries.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "暂无条目，点击下方添加",
                        color = StradustTheme.colors.textMuted,
                        fontSize = 12.sp,
                    )
                } else {
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        userScrollEnabled = false,
                    ) {
                        items(
                            count = worldInfo.entries.size,
                            key = { worldInfo.entries[it].id },
                        ) { index ->
                            val entry = worldInfo.entries[index]
                            Row(verticalAlignment = Alignment.Top) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.comment.ifBlank { entry.key.ifBlank { "(无关键词)" } },
                                        color = StradustTheme.colors.textPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = entry.content.take(50),
                                        color = StradustTheme.colors.textMuted,
                                        fontSize = 11.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    // 启用/禁用/常驻 标签
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (entry.constant) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(
                                                        StradustTheme.colors.tertiary.copy(alpha = 0.15f),
                                                        RoundedCornerShape(4.dp),
                                                    )
                                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                                            ) {
                                                Text("常驻", color = StradustTheme.colors.tertiary, fontSize = 9.sp)
                                            }
                                        }
                                        if (!entry.enabled) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(
                                                        StradustTheme.colors.error.copy(alpha = 0.15f),
                                                        RoundedCornerShape(4.dp),
                                                    )
                                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                                            ) {
                                                Text("禁用", color = StradustTheme.colors.error, fontSize = 9.sp)
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onEditEntry(entry) }
                                        .padding(8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "编辑词条",
                                        tint = StradustTheme.colors.textSecondary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onDeleteEntry(entry.id) }
                                        .padding(8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "删除词条",
                                        tint = StradustTheme.colors.textSecondary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                StradustButton(
                    text = "添加词条",
                    onClick = onAddEntry,
                    variant = ButtonVariant.OUTLINED,
                    size = ButtonSize.SMALL,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ===== 世界书编辑对话框 =====

@Composable
private fun WorldBookEditDialog(
    initialWorldInfo: WorldInfo?,
    onDismiss: () -> Unit,
    onConfirm: (WorldInfo) -> Unit,
) {
    val isEdit = initialWorldInfo != null
    var name by remember { mutableStateOf(initialWorldInfo?.name ?: "") }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "编辑世界书" else "添加世界书") },
        text = {
            Column {
                SectionLabel("世界书名称")
                Spacer(Modifier.height(4.dp))
                StradustInput(value = name, onValueChange = { name = it }, hint = "输入世界书名称")
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isBlank()) {
                        Toast.makeText(context, "请输入世界书名称", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    val wi = WorldInfo(
                        id = initialWorldInfo?.id ?: "",
                        name = name.trim(),
                        entries = initialWorldInfo?.entries ?: emptyList(),
                        createdAt = initialWorldInfo?.createdAt ?: System.currentTimeMillis(),
                    )
                    onConfirm(wi)
                },
            ) { Text(if (isEdit) "保存" else "确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

// ===== 世界书条目编辑对话框 =====

@Composable
private fun EntryEditDialog(
    initialEntry: WorldInfoEntry?,
    onDismiss: () -> Unit,
    onConfirm: (WorldInfoEntry) -> Unit,
) {
    val isEdit = initialEntry != null
    var key by remember { mutableStateOf(initialEntry?.key ?: "") }
    var keySecondary by remember { mutableStateOf(initialEntry?.keySecondary ?: "") }
    var content by remember { mutableStateOf(initialEntry?.content ?: "") }
    var comment by remember { mutableStateOf(initialEntry?.comment ?: "") }
    var constant by remember { mutableStateOf(initialEntry?.constant ?: false) }
    var selective by remember { mutableStateOf(initialEntry?.selective ?: false) }
    var insertionOrder by remember { mutableStateOf((initialEntry?.insertionOrder ?: 100).toFloat()) }
    var enabled by remember { mutableStateOf(initialEntry?.enabled ?: true) }
    var position by remember { mutableStateOf(initialEntry?.position?.ifBlank { "before_char" } ?: "before_char") }

    // AI辅助生成相关状态
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showAiDialog by remember { mutableStateOf(false) }
    var aiKeywords by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "编辑词条" else "添加词条") },
        text = {
            Column(modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                WorldEntryFields(
                    key = key, onKeyChange = { key = it },
                    keySecondary = keySecondary, onKeySecondaryChange = { keySecondary = it },
                    content = content, onContentChange = { content = it },
                    comment = comment, onCommentChange = { comment = it },
                    constant = constant, onConstantChange = { constant = it },
                    selective = selective, onSelectiveChange = { selective = it },
                    insertionOrder = insertionOrder, onInsertionOrderChange = { insertionOrder = it },
                    enabled = enabled, onEnabledChange = { enabled = it },
                    position = position, onPositionChange = { position = it },
                )

                // AI辅助生成按钮
                Spacer(Modifier.height(12.dp))
                StradustButton(
                    text = if (isGenerating) "生成中..." else "AI辅助生成",
                    onClick = { showAiDialog = true },
                    variant = ButtonVariant.OUTLINED,
                    size = ButtonSize.SMALL,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isGenerating,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val entry = WorldInfoEntry(
                        id = initialEntry?.id ?: "",
                        key = key.trim(),
                        keySecondary = keySecondary.trim(),
                        content = content.trim(),
                        comment = comment.trim(),
                        constant = constant,
                        selective = selective,
                        insertionOrder = insertionOrder.toInt().coerceIn(0, 100),
                        enabled = enabled,
                        position = position,
                    )
                    onConfirm(entry)
                },
            ) { Text(if (isEdit) "保存" else "确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )

    // AI关键词输入对话框
    if (showAiDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isGenerating) showAiDialog = false
            },
            title = { Text("AI辅助生成") },
            text = {
                Column {
                    Text(
                        text = "请输入关键词描述，AI将根据关键词生成条目内容",
                        color = StradustTheme.colors.textSecondary,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    StradustInput(
                        value = aiKeywords,
                        onValueChange = { aiKeywords = it },
                        hint = "如：魔法学校的规则",
                        maxLines = 3,
                    )
                    if (isGenerating) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "正在生成，请稍候...",
                            color = StradustTheme.colors.primary,
                            fontSize = 11.sp,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (aiKeywords.isBlank()) {
                            Toast.makeText(context, "请输入关键词描述", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        val keywords = aiKeywords.trim()
                        isGenerating = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    val sm = SettingsManager(context)
                                    if (sm.chatApiUrl.isBlank()) {
                                        return@withContext null
                                    }
                                    val apiClient = ApiClient(
                                        chatApiUrl = sm.chatApiUrl,
                                        apiKey = sm.chatApiKey,
                                        modelName = sm.chatModel,
                                        temperature = sm.llmTemperature,
                                        topP = sm.llmTopP,
                                        frequencyPenalty = sm.llmFrequencyPenalty,
                                        presencePenalty = sm.llmPresencePenalty,
                                        maxTokens = sm.llmMaxTokens,
                                        providerId = sm.apiProvider,
                                    )
                                    val userContent = "请根据以下关键词生成世界书条目内容，输出格式为：关键词|内容。关键词：$keywords"
                                    val response = apiClient.sendSimplePrompt(
                                        systemPrompt = "生成世界书条目",
                                        userContent = userContent,
                                    )
                                    response?.text?.trim()?.ifBlank { null }
                                }.getOrNull()
                            }
                            isGenerating = false
                            if (result.isNullOrBlank()) {
                                Toast.makeText(context, "生成失败，请检查API配置", Toast.LENGTH_SHORT).show()
                            } else {
                                val separatorIndex = result.indexOf('|')
                                if (separatorIndex > 0 && separatorIndex < result.length - 1) {
                                    val generatedKey = result.substring(0, separatorIndex).trim()
                                    val generatedContent = result.substring(separatorIndex + 1).trim()
                                    key = generatedKey
                                    content = generatedContent
                                } else {
                                    content = result
                                }
                                showAiDialog = false
                                Toast.makeText(context, "生成成功", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !isGenerating,
                ) { Text(if (isGenerating) "生成中" else "生成") }
            },
            dismissButton = {
                TextButton(
                    onClick = { if (!isGenerating) showAiDialog = false },
                    enabled = !isGenerating,
                ) { Text("取消") }
            },
        )
    }
}

// ===== 条目字段编辑器 =====

@Composable
private fun WorldEntryFields(
    key: String,
    onKeyChange: (String) -> Unit,
    keySecondary: String,
    onKeySecondaryChange: (String) -> Unit,
    content: String,
    onContentChange: (String) -> Unit,
    comment: String,
    onCommentChange: (String) -> Unit,
    constant: Boolean,
    onConstantChange: (Boolean) -> Unit,
    selective: Boolean,
    onSelectiveChange: (Boolean) -> Unit,
    insertionOrder: Float,
    onInsertionOrderChange: (Float) -> Unit,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    position: String,
    onPositionChange: (String) -> Unit,
) {
    var positionExpanded by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }

    // 优先级三档映射
    val priorityOptions = listOf("高" to 80f, "中" to 50f, "低" to 20f)
    val currentPriorityLabel = priorityOptions.minByOrNull { kotlin.math.abs(it.second - insertionOrder) }?.first ?: "中"

    SectionLabel("关键词（对话中出现这些词时触发）")
    Spacer(Modifier.height(4.dp))
    StradustInput(value = key, onValueChange = onKeyChange, hint = "多个词用逗号隔开，如：学校,课堂,老师")
    Spacer(Modifier.height(8.dp))

    SectionLabel("内容（触发后发送给AI的背景信息）")
    Spacer(Modifier.height(4.dp))
    StradustInput(value = content, onValueChange = onContentChange, hint = "当关键词出现时，这段内容会自动加入对话背景", maxLines = 5)
    Spacer(Modifier.height(8.dp))

    SectionLabel("备注（仅自己可见，不影响AI）")
    Spacer(Modifier.height(4.dp))
    StradustInput(value = comment, onValueChange = onCommentChange, hint = "给自己看的说明")
    Spacer(Modifier.height(8.dp))

    // 常驻开关
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "常驻（始终发送给AI，不需要关键词）",
            color = StradustTheme.colors.textPrimary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = constant,
            onCheckedChange = { newConstant ->
                onConstantChange(newConstant)
                if (newConstant) onSelectiveChange(false)
            },
        )
    }

    // 选择性开关：仅非常驻时显示
    if (!constant) {
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "需要关键词才触发",
                color = StradustTheme.colors.textPrimary,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = selective, onCheckedChange = onSelectiveChange)
        }
    }

    Spacer(Modifier.height(4.dp))
    // 启用开关
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "启用此条目",
            color = StradustTheme.colors.textPrimary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
    }

    // 高级选项折叠区
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { showAdvanced = !showAdvanced }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "高级选项",
            color = StradustTheme.colors.primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (showAdvanced) "收起" else "展开",
            tint = StradustTheme.colors.primary,
            modifier = Modifier.size(18.dp),
        )
    }

    AnimatedVisibility(visible = showAdvanced) {
        Column {
            // 附加关键词
            Spacer(Modifier.height(8.dp))
            SectionLabel("附加关键词（可选，进一步限定触发条件）")
            Spacer(Modifier.height(4.dp))
            StradustInput(value = keySecondary, onValueChange = onKeySecondaryChange, hint = "可选，留空表示不限定")
            Spacer(Modifier.height(8.dp))

            // 优先级三档选择
            SectionLabel("优先级")
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                priorityOptions.forEach { (label, value) ->
                    val isSelected = label == currentPriorityLabel
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) StradustTheme.colors.primary
                                else StradustTheme.colors.surfaceContainerLow,
                                RoundedCornerShape(8.dp),
                            )
                            .clickable { onInsertionOrderChange(value) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) StradustTheme.colors.onPrimary
                            else StradustTheme.colors.textSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            // 插入位置下拉
            SectionLabel("插入位置")
            Spacer(Modifier.height(4.dp))
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            StradustTheme.colors.surfaceContainerHigh,
                            RoundedCornerShape(8.dp),
                        )
                        .clickable { positionExpanded = true }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (position == "after_char") "角色介绍之后" else "角色介绍之前",
                        color = StradustTheme.colors.textPrimary,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = "展开",
                        tint = StradustTheme.colors.textSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                DropdownMenu(
                    expanded = positionExpanded,
                    onDismissRequest = { positionExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("角色介绍之前") },
                        onClick = { onPositionChange("before_char"); positionExpanded = false },
                    )
                    DropdownMenuItem(
                        text = { Text("角色介绍之后") },
                        onClick = { onPositionChange("after_char"); positionExpanded = false },
                    )
                }
            }
        }
    }
}

/** 区块小标题 */
@Composable
private fun SectionLabel(text: String) {
    Text(text = text, color = StradustTheme.colors.textSecondary, fontSize = 12.sp)
}
