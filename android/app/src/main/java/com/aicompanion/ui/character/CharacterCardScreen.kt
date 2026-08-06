package com.aicompanion.ui.character

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicompanion.character.CharacterCardManager
import com.aicompanion.data.PersonaDataRepository
import com.aicompanion.models.CharacterCard
import com.aicompanion.models.UserPersona
import com.aicompanion.models.WorldInfo
import com.aicompanion.theme.StradustTheme
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.aicompanion.ui.components.ButtonSize
import com.aicompanion.ui.components.ButtonVariant
import com.aicompanion.ui.components.StradustButton
import com.aicompanion.ui.components.StradustCard
import com.aicompanion.ui.components.StradustInput
import com.aicompanion.ui.components.StradustTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider

private enum class CharacterTab(val label: String) {
    CHARACTER("角色设定"),
    PERSONA("我的设定"),
}

/**
 * 角色设定页面（重写版）
 *
 * 功能：
 * - Tab 切换：角色设定 / 我的设定
 * - 角色设定：AI角色卡片列表 + 创建/编辑/删除/导入/导出 + 世界书匹配
 * - 我的设定：用户 Persona 信息编辑
 */
@Composable
fun CharacterCardScreen(
    onBackClick: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    startInCreateMode: Boolean = false,
) {
    val context = LocalContext.current
    val manager = remember { CharacterCardManager(context) }
    val dataRepo = remember { PersonaDataRepository(context) }
    val scope = rememberCoroutineScope()
    var refreshTick by remember { mutableStateOf(0) }
    var selectedTab by remember { mutableStateOf(CharacterTab.CHARACTER) }

    // 角色卡：对话框状态
    var showAddCardDialog by remember { mutableStateOf(false) }
    var editingCard by remember { mutableStateOf<CharacterCard?>(null) }
    var showEditorPage by remember { mutableStateOf(startInCreateMode) }  // 支持直接进入创建模式
    var showImportDialog by remember { mutableStateOf(false) }
    var importDialogKey by remember { mutableStateOf(0) }
    var exportFilePath by remember { mutableStateOf<String?>(null) }
    var isExporting by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var cardToDelete by remember { mutableStateOf<CharacterCard?>(null) }

    // 预计算各 Tab 数据
    val cards = remember(refreshTick) { manager.getAllCards() }
    val worldInfos = remember(refreshTick) { manager.getAllWorldInfos() }
    val persona = remember(refreshTick) { manager.getUserPersona() }

    Box(modifier = Modifier.fillMaxSize().background(StradustTheme.colors.background)) {
        if (showEditorPage) {
            // ===== 全屏编辑页面 =====
            CardEditorPage(
                initialCard = editingCard,
                worldInfos = worldInfos,
                onBack = {
                    showEditorPage = false
                    editingCard = null
                    showAddCardDialog = false
                },
                onSave = { card ->
                    if (editingCard != null) {
                        manager.updateCard(card)
                    } else {
                        manager.addCard(card)
                    }
                    refreshTick++
                    showEditorPage = false
                    editingCard = null
                    showAddCardDialog = false
                },
            )
        } else {
            // ===== 角色列表页面 =====
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item { StradustTopBar(title = "角色设定", onBackClick = onBackClick) }
                item { Spacer(Modifier.height(12.dp)) }

                // Tab 切换栏
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CharacterTab.entries.forEach { tab ->
                            val isSelected = tab == selectedTab
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) StradustTheme.colors.primary
                                        else StradustTheme.colors.surfaceContainerLow,
                                        RoundedCornerShape(12.dp),
                                    )
                                    .clickable { selectedTab = tab }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = tab.label,
                                    color = if (isSelected) StradustTheme.colors.onPrimary
                                    else StradustTheme.colors.textSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }

                when (selectedTab) {
                    CharacterTab.CHARACTER -> characterContent(
                        cards = cards,
                        worldInfos = worldInfos,
                        onAdd = {
                            editingCard = null
                            showEditorPage = true
                        },
                        onImport = { importDialogKey++; showImportDialog = true },
                        onEdit = { card -> editingCard = card; showEditorPage = true },
                        onDelete = { card -> cardToDelete = card; showDeleteConfirmDialog = true },
                        onExport = { id ->
                            isExporting = true
                            scope.launch(Dispatchers.IO) {
                                val card = cards.find { it.id == id }
                                val safeName = (card?.name ?: "persona").replace(Regex("[^a-zA-Z0-9_\\-\\u4e00-\\u9fa5]"), "_")
                                val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                                val destFile = File(exportDir, "${safeName}_${System.currentTimeMillis()}.json")
                                val ok = dataRepo.exportToFile(id, destFile)
                                withContext(Dispatchers.Main) {
                                    isExporting = false
                                    if (ok) {
                                        exportFilePath = destFile.absolutePath
                                    } else {
                                        Toast.makeText(context, "导出失败", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        onNavigate = onNavigate,
                    )
                    CharacterTab.PERSONA -> personaContent(
                        persona = persona,
                        onSave = { updated -> manager.saveUserPersona(updated); refreshTick++; Toast.makeText(context, "我的设定已保存", Toast.LENGTH_SHORT).show() },
                    )
                }

                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }

    // 导入角色卡对话框
    if (showImportDialog) {
        key(importDialogKey) {
            ImportCardDialog(
                onDismiss = { showImportDialog = false },
                onConfirm = { json ->
                    scope.launch(Dispatchers.IO) {
                        val jsonObj = try { org.json.JSONObject(json) } catch (_: Exception) { null }
                        val newId = if (jsonObj != null) dataRepo.importPersona(jsonObj) else null
                        withContext(Dispatchers.Main) {
                            if (newId != null) {
                                refreshTick++
                                showImportDialog = false
                                Toast.makeText(context, "角色导入成功", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "导入失败，请检查文件格式", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
            )
        }
    }

    // 导出文件完成对话框
    if (exportFilePath != null) {
        ExportFileDialog(
            filePath = exportFilePath!!,
            onDismiss = { exportFilePath = null },
        )
    }

    // 导出中loading
    if (isExporting) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("导出中") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("正在导出角色完整数据...", fontSize = 14.sp)
                }
            },
            confirmButton = {},
        )
    }

    // 删除角色卡确认弹窗
    if (showDeleteConfirmDialog && cardToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmDialog = false
                cardToDelete = null
            },
            title = { Text("删除角色") },
            text = { Text("确定要删除角色「${cardToDelete!!.name.ifBlank { "未命名" }}」吗？此操作不可撤销") },
            confirmButton = {
                TextButton(
                    onClick = {
                        manager.deleteCard(cardToDelete!!.id)
                        refreshTick++
                        showDeleteConfirmDialog = false
                        cardToDelete = null
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        cardToDelete = null
                    },
                ) { Text("取消") }
            },
        )
    }
}

// ===== 角色设定 Tab =====

private fun LazyListScope.characterContent(
    cards: List<CharacterCard>,
    worldInfos: List<WorldInfo>,
    onAdd: () -> Unit,
    onImport: () -> Unit,
    onEdit: (CharacterCard) -> Unit,
    onDelete: (CharacterCard) -> Unit,
    onExport: (String) -> Unit,
    onNavigate: (String) -> Unit = {},
) {
    // 操作按钮行
    item {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StradustButton(
                text = "创建角色",
                onClick = onAdd,
                size = ButtonSize.SMALL,
                modifier = Modifier.weight(1f),
            )
            StradustButton(
                text = "导入",
                onClick = onImport,
                variant = ButtonVariant.OUTLINED,
                size = ButtonSize.SMALL,
                modifier = Modifier.weight(1f),
            )
        }
    }

    item { Spacer(Modifier.height(12.dp)) }

    // 角色卡片列表
    items(cards.size, key = { cards[it].id }) { index ->
        val card = cards[index]
        // 查找关联的世界书名称
        val matchedWorldName = if (card.worldInfoId.isNotBlank()) {
            worldInfos.find { it.id == card.worldInfoId }?.name?.ifBlank { "未命名" } ?: "未知"
        } else {
            null
        }
        CharacterCardItem(
            card = card,
            matchedWorldName = matchedWorldName,
            onEdit = { onEdit(card) },
            onDelete = { onDelete(card) },
            onExport = { onExport(card.id) },
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CharacterCardItem(
    card: CharacterCard,
    matchedWorldName: String?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
) {
    StradustCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = card.name.ifBlank { "未命名" },
                        color = StradustTheme.colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (card.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = card.description,
                        color = StradustTheme.colors.textMuted,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (card.personality.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "性格：${card.personality}",
                        color = StradustTheme.colors.textMuted,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (card.firstMes.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "开场白：${card.firstMes}",
                        color = StradustTheme.colors.textMuted,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // 关联世界书标签
                if (matchedWorldName != null) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    StradustTheme.colors.primaryContainer,
                                    RoundedCornerShape(4.dp),
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "世界书：$matchedWorldName",
                                color = StradustTheme.colors.onPrimaryContainer,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
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

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StradustButton(
                text = "编辑",
                onClick = onEdit,
                variant = ButtonVariant.OUTLINED,
                size = ButtonSize.SMALL,
                modifier = Modifier.weight(1f),
            )
            StradustButton(
                text = "导出",
                onClick = onExport,
                variant = ButtonVariant.OUTLINED,
                size = ButtonSize.SMALL,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ===== 我的设定 Tab =====

private fun LazyListScope.personaContent(
    persona: UserPersona,
    onSave: (UserPersona) -> Unit,
) {
    item {
        var name by remember(persona) { mutableStateOf(persona.name) }
        var description by remember(persona) { mutableStateOf(persona.description) }
        var personality by remember(persona) { mutableStateOf(persona.personality) }
        var appearance by remember(persona) { mutableStateOf(persona.appearance) }

        StradustCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                text = "我的设定",
                color = StradustTheme.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "编辑你在对话中的角色信息，AI 将据此了解你",
                color = StradustTheme.colors.textMuted,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(12.dp))

            SectionLabel("名字")
            Spacer(Modifier.height(4.dp))
            StradustInput(
                value = name,
                onValueChange = { name = it },
                hint = "你的名字或昵称",
            )

            Spacer(Modifier.height(12.dp))
            SectionLabel("自我介绍")
            Spacer(Modifier.height(4.dp))
            StradustInput(
                value = description,
                onValueChange = { description = it },
                hint = "描述一下你自己",
                maxLines = 3,
            )

            Spacer(Modifier.height(12.dp))
            SectionLabel("性格特点")
            Spacer(Modifier.height(4.dp))
            StradustInput(
                value = personality,
                onValueChange = { personality = it },
                hint = "如：温柔、内向、幽默等",
                maxLines = 3,
            )

            Spacer(Modifier.height(12.dp))
            SectionLabel("外貌特征")
            Spacer(Modifier.height(4.dp))
            StradustInput(
                value = appearance,
                onValueChange = { appearance = it },
                hint = "如：黑发、蓝眼、戴眼镜等",
                maxLines = 3,
            )

            Spacer(Modifier.height(16.dp))
            StradustButton(
                text = "保存",
                onClick = {
                    val updated = UserPersona(
                        id = persona.id,
                        name = name.trim(),
                        description = description.trim(),
                        personality = personality.trim(),
                        appearance = appearance.trim(),
                        isActive = true,
                    )
                    onSave(updated)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ===== 通用小组件 =====

/** 区块小标题 */
@Composable
private fun SectionLabel(text: String) {
    Text(text = text, color = StradustTheme.colors.textSecondary, fontSize = 12.sp)
}

// ===== 对话框 =====

/**
 * 角色卡 创建/编辑 全屏页面
 *
 * 包含完整的角色属性编辑和世界书匹配：
 * - 基本信息区：名字、描述、性格、开场白
 * - 世界书匹配区：下拉选择关联世界书
 * - 高级字段区（可折叠）：场景设定、对话示例、系统提示词等
 */
@Composable
private fun CardEditorPage(
    initialCard: CharacterCard?,
    worldInfos: List<WorldInfo>,
    onBack: () -> Unit,
    onSave: (CharacterCard) -> Unit,
) {
    val isEdit = initialCard != null
    val context = LocalContext.current

    // 基本字段
    var name by remember { mutableStateOf(initialCard?.name ?: "") }
    var description by remember { mutableStateOf(initialCard?.description ?: "") }
    var personality by remember { mutableStateOf(initialCard?.personality ?: "") }
    var firstMes by remember { mutableStateOf(initialCard?.firstMes ?: "") }
    // 高级字段
    var scenario by remember { mutableStateOf(initialCard?.scenario ?: "") }
    var mesExample by remember { mutableStateOf(initialCard?.mesExample ?: "") }
    var creatorNotes by remember { mutableStateOf(initialCard?.creatorNotes ?: "") }
    var systemPrompt by remember { mutableStateOf(initialCard?.systemPrompt ?: "") }
    var postHistoryInstructions by remember { mutableStateOf(initialCard?.postHistoryInstructions ?: "") }
    var alternateGreetings by remember { mutableStateOf(initialCard?.alternateGreetings ?: emptyList()) }
    var tags by remember { mutableStateOf(initialCard?.tags ?: emptyList()) }
    var creator by remember { mutableStateOf(initialCard?.creator ?: "") }
    var characterVersion by remember { mutableStateOf(initialCard?.characterVersion ?: "1.0") }
    var avatarPath by remember { mutableStateOf(initialCard?.avatarPath ?: "") }
    var worldInfoId by remember { mutableStateOf(initialCard?.worldInfoId ?: "") }

    var showAdvanced by remember { mutableStateOf(false) }
    var newTagInput by remember { mutableStateOf("") }
    var worldDropdownExpanded by remember { mutableStateOf(false) }

    // AI辅助生成状态
    var showAiDialog by remember { mutableStateOf(false) }
    var aiKeywords by remember { mutableStateOf("") }
    var isAiGenerating by remember { mutableStateOf(false) }
    val aiScope = rememberCoroutineScope()

    // 头像选择器：复制图片到本地文件，避免 content URI 失效
    val avatarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            val cardId = initialCard?.id ?: "draft_${System.currentTimeMillis()}"
            val path = com.aicompanion.util.AvatarManager.saveAvatarFromUri(
                context, uri, "ai", cardId
            )
            if (path != null) {
                avatarPath = path
            } else {
                Toast.makeText(context, "头像保存失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val selectedWorldName = worldInfos.find { it.id == worldInfoId }?.name?.ifBlank { "未命名" } ?: "无"

    Column(modifier = Modifier.fillMaxSize().background(StradustTheme.colors.background)) {
        StradustTopBar(
            title = if (isEdit) "编辑角色" else "创建角色",
            onBackClick = onBack,
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            // === 提示文案 ===
            item {
                Text(
                    text = "只需填写名字即可创建，其余信息可稍后编辑",
                    color = StradustTheme.colors.textMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            // === 基本信息 ===
            item { Spacer(Modifier.height(12.dp)) }
            item {
                SectionLabel("名字")
                Spacer(Modifier.height(4.dp))
                StradustInput(value = name, onValueChange = { name = it }, hint = "角色名字")
            }
            // AI 辅助生成按钮
            item { Spacer(Modifier.height(8.dp)) }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StradustButton(
                        text = if (isAiGenerating) "AI生成中..." else "AI辅助生成",
                        onClick = {
                            val apiClient = com.aicompanion.AppContainer.apiClient
                            if (apiClient == null) {
                                Toast.makeText(context, "API未配置，请先在设置中配置API地址和密钥", Toast.LENGTH_LONG).show()
                            } else {
                                showAiDialog = true
                            }
                        },
                        variant = ButtonVariant.OUTLINED,
                        size = ButtonSize.SMALL,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI",
                        tint = StradustTheme.colors.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
            item {
                SectionLabel("描述")
                Spacer(Modifier.height(4.dp))
                StradustInput(value = description, onValueChange = { description = it }, hint = "角色描述", maxLines = 3)
            }
            item { Spacer(Modifier.height(8.dp)) }
            item {
                SectionLabel("性格")
                Spacer(Modifier.height(4.dp))
                StradustInput(value = personality, onValueChange = { personality = it }, hint = "性格特征", maxLines = 3)
            }
            item { Spacer(Modifier.height(8.dp)) }
            item {
                SectionLabel("开场白")
                Spacer(Modifier.height(4.dp))
                StradustInput(value = firstMes, onValueChange = { firstMes = it }, hint = "第一条消息", maxLines = 3)
            }

            // === 世界书匹配区 ===
            item { Spacer(Modifier.height(16.dp)) }
            item {
                Text(
                    text = "关联世界书",
                    color = StradustTheme.colors.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "选择一个世界书，该角色的对话将自动触发世界书中的条目",
                    color = StradustTheme.colors.textMuted,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(6.dp))
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                StradustTheme.colors.surfaceContainerHigh,
                                RoundedCornerShape(8.dp),
                            )
                            .clickable { worldDropdownExpanded = true }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = selectedWorldName,
                            color = StradustTheme.colors.textPrimary,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = "展开",
                            tint = StradustTheme.colors.textSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = worldDropdownExpanded,
                        onDismissRequest = { worldDropdownExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("无") },
                            onClick = { worldInfoId = ""; worldDropdownExpanded = false },
                        )
                        worldInfos.forEach { wi ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(wi.name.ifBlank { "未命名" })
                                        Text(
                                            text = "${wi.entries.size} 条词条",
                                            color = StradustTheme.colors.textMuted,
                                            fontSize = 11.sp,
                                        )
                                    }
                                },
                                onClick = { worldInfoId = wi.id; worldDropdownExpanded = false },
                            )
                        }
                    }
                }
            }

            // === 高级字段折叠区 ===
            item { Spacer(Modifier.height(12.dp)) }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showAdvanced = !showAdvanced }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "高级字段",
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
            }

            item {
                AnimatedVisibility(visible = showAdvanced) {
                    Column {
                        // scenario
                        SectionLabel("场景设定 (scenario)")
                        Spacer(Modifier.height(4.dp))
                        StradustInput(value = scenario, onValueChange = { scenario = it }, hint = "场景设定", maxLines = 4)
                        Spacer(Modifier.height(8.dp))
                        // mesExample
                        SectionLabel("对话示例 (mes_example)")
                        Spacer(Modifier.height(4.dp))
                        StradustInput(value = mesExample, onValueChange = { mesExample = it }, hint = "对话示例", maxLines = 4)
                        Spacer(Modifier.height(8.dp))
                        // creatorNotes
                        SectionLabel("创作者备注 (creator_notes)")
                        Spacer(Modifier.height(4.dp))
                        StradustInput(value = creatorNotes, onValueChange = { creatorNotes = it }, hint = "创作者备注", maxLines = 3)
                        Spacer(Modifier.height(8.dp))
                        // systemPrompt
                        SectionLabel("系统提示词 (system_prompt)")
                        Spacer(Modifier.height(4.dp))
                        StradustInput(value = systemPrompt, onValueChange = { systemPrompt = it }, hint = "系统提示词", maxLines = 5)
                        Spacer(Modifier.height(8.dp))
                        // postHistoryInstructions
                        SectionLabel("历史后指令 (post_history_instructions)")
                        Spacer(Modifier.height(4.dp))
                        StradustInput(value = postHistoryInstructions, onValueChange = { postHistoryInstructions = it }, hint = "历史后指令", maxLines = 4)
                        Spacer(Modifier.height(8.dp))

                        // alternateGreetings 列表编辑器
                        SectionLabel("备选开场白")
                        Spacer(Modifier.height(4.dp))
                        alternateGreetings.forEachIndexed { idx, greeting ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StradustInput(
                                    value = greeting,
                                    onValueChange = { v ->
                                        alternateGreetings = alternateGreetings.toMutableList().also { it[idx] = v }
                                    },
                                    hint = "备选开场白 $idx",
                                    maxLines = 2,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            alternateGreetings = alternateGreetings.filterIndexed { i, _ -> i != idx }
                                        }
                                        .padding(6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "删除",
                                        tint = StradustTheme.colors.textSecondary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                        StradustButton(
                            text = "+ 添加开场白",
                            onClick = { alternateGreetings = alternateGreetings + "" },
                            variant = ButtonVariant.OUTLINED,
                            size = ButtonSize.SMALL,
                        )

                        Spacer(Modifier.height(8.dp))
                        // tags Chip 输入器
                        SectionLabel("标签")
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StradustInput(
                                value = newTagInput,
                                onValueChange = { newTagInput = it },
                                hint = "输入标签后点击添加",
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            StradustButton(
                                text = "添加",
                                onClick = {
                                    val t = newTagInput.trim()
                                    if (t.isNotEmpty() && t !in tags) {
                                        tags = tags + t
                                        newTagInput = ""
                                    }
                                },
                                size = ButtonSize.SMALL,
                            )
                        }
                        if (tags.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            tags.forEach { tag ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                StradustTheme.colors.primaryContainer,
                                                RoundedCornerShape(6.dp),
                                            )
                                            .padding(horizontal = 8.dp, vertical = 3.dp),
                                    ) {
                                        Text(
                                            text = tag,
                                            color = StradustTheme.colors.onPrimaryContainer,
                                            fontSize = 11.sp,
                                        )
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { tags = tags - tag }
                                            .padding(2.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "移除标签",
                                            tint = StradustTheme.colors.textSecondary,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        // creator
                        SectionLabel("创作者")
                        Spacer(Modifier.height(4.dp))
                        StradustInput(value = creator, onValueChange = { creator = it }, hint = "创作者")
                        Spacer(Modifier.height(8.dp))
                        // characterVersion
                        SectionLabel("角色版本")
                        Spacer(Modifier.height(4.dp))
                        StradustInput(value = characterVersion, onValueChange = { characterVersion = it }, hint = "版本号")
                        Spacer(Modifier.height(8.dp))
                        // avatarPath
                        SectionLabel("头像")
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StradustButton(
                                text = "选择头像",
                                onClick = {
                                    avatarLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                },
                                variant = ButtonVariant.OUTLINED,
                                size = ButtonSize.SMALL,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (avatarPath.isNotBlank()) "已选择" else "未选择",
                                color = StradustTheme.colors.textMuted,
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }

        // 保存按钮
        Box(modifier = Modifier.padding(16.dp)) {
            StradustButton(
                text = if (isEdit) "保存" else "创建",
                onClick = {
                    if (name.isBlank()) {
                        Toast.makeText(context, "请输入角色名字", Toast.LENGTH_SHORT).show()
                        return@StradustButton
                    }
                    val card = CharacterCard(
                            id = initialCard?.id ?: "",
                            name = name.trim(),
                            description = description.trim(),
                            personality = personality.trim(),
                            firstMes = firstMes.trim(),
                            scenario = scenario.trim(),
                            mesExample = mesExample.trim(),
                            creatorNotes = creatorNotes.trim(),
                            systemPrompt = systemPrompt.trim(),
                            postHistoryInstructions = postHistoryInstructions.trim(),
                            alternateGreetings = alternateGreetings.map { it.trim() }.filter { it.isNotEmpty() },
                            tags = tags,
                            creator = creator.trim(),
                            characterVersion = characterVersion.trim().ifBlank { "1.0" },
                            avatarPath = avatarPath.trim(),
                            isActive = false,
                            createdAt = initialCard?.createdAt ?: System.currentTimeMillis(),
                            worldInfoId = worldInfoId,
                        )
                        onSave(card)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    // AI辅助生成对话框
    if (showAiDialog) {
        AlertDialog(
            onDismissRequest = { if (!isAiGenerating) showAiDialog = false },
            title = { Text("AI辅助生成角色") },
            text = {
                Column {
                    Text("输入角色关键词，AI将自动生成角色设定", fontSize = 12.sp, color = StradustTheme.colors.textMuted)
                    Spacer(Modifier.height(8.dp))
                    StradustInput(
                        value = aiKeywords,
                        onValueChange = { aiKeywords = it },
                        hint = "如：傲娇少女、温柔姐姐、冷酷杀手",
                        singleLine = true,
                    )
                    if (isAiGenerating) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("正在生成...", fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (aiKeywords.isBlank()) {
                            Toast.makeText(context, "请输入关键词", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        val apiClient = com.aicompanion.AppContainer.apiClient ?: run {
                            Toast.makeText(context, "API未配置", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        isAiGenerating = true
                        aiScope.launch {
                            val aiSystemPrompt = "你是一个AI角色设定生成助手。请严格按照用户的要求生成角色设定，只返回纯JSON，不要包含markdown标记或任何额外说明文字。"
                            val userPrompt = buildString {
                                append("请根据以下关键词生成AI角色设定，以JSON格式返回，字段包括：\n")
                                append("name(名字), description(简短描述), personality(性格), firstMes(开场白), scenario(场景), mesExample(对话示例)\n")
                                append("关键词：$aiKeywords")
                            }
                            val response = withContext(Dispatchers.IO) {
                                try {
                                    apiClient.sendSimplePrompt(aiSystemPrompt, userPrompt)
                                } catch (e: Exception) {
                                    com.aicompanion.util.AppLogger.e("CharAI", "AI生成异常: ${e.message}", e)
                                    null
                                }
                            }
                            isAiGenerating = false
                            val rawText = response?.text
                            if (rawText.isNullOrBlank()) {
                                Toast.makeText(context, "AI生成失败，请检查API配置或网络", Toast.LENGTH_LONG).show()
                                return@launch
                            }
                            // 解析JSON并填充表单（清理markdown标记和多余文本）
                            try {
                                val cleanedJson = rawText.trim().let { txt ->
                                    // 尝试从 markdown 代码块中提取
                                    val jsonContent = if (txt.contains("```")) {
                                        val afterOpening = txt.substringAfter("```")
                                        // 跳过可选的 "json" 语言标记
                                        val afterLang = if (afterOpening.startsWith("json")) afterOpening.substringAfter("json") else afterOpening
                                        // 取闭合 ``` 之前的内容
                                        afterLang.substringBefore("```")
                                    } else {
                                        txt
                                    }
                                    jsonContent.trim().let { inner ->
                                        // 提取第一个 { 到最后一个 } 之间的内容
                                        val start = inner.indexOf('{')
                                        val end = inner.lastIndexOf('}')
                                        if (start >= 0 && end > start) inner.substring(start, end + 1) else inner
                                    }
                                }
                                val json = org.json.JSONObject(cleanedJson)
                                name = json.optString("name", name)
                                description = json.optString("description", description)
                                personality = json.optString("personality", personality)
                                firstMes = json.optString("firstMes", json.optString("greeting", firstMes))
                                scenario = json.optString("scenario", scenario)
                                mesExample = json.optString("mesExample", json.optString("mes_example", mesExample))
                                showAiDialog = false
                                aiKeywords = ""
                                Toast.makeText(context, "AI生成成功", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                com.aicompanion.util.AppLogger.e("CharAI", "JSON解析失败: ${e.message}\n原始响应: $rawText", e)
                                Toast.makeText(context, "解析AI响应失败，请重试", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !isAiGenerating,
                ) { Text("生成") }
            },
            dismissButton = {
                TextButton(onClick = { if (!isAiGenerating) showAiDialog = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ImportCardDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val context = LocalContext.current
    var json by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                try {
                    val fileJson = context.contentResolver.openInputStream(it)?.bufferedReader()?.readText()
                    if (fileJson != null) {
                        withContext(Dispatchers.Main) {
                            onConfirm(fileJson)
                            // 若 onConfirm 未关闭对话框则说明导入失败
                            error = true
                        }
                    }
                } catch (_: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "读取文件失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入角色卡") },
        text = {
            Column {
                // 从文件导入按钮
                StradustButton(
                    text = "从文件导入 (JSON/PNG)",
                    onClick = { filePickerLauncher.launch(arrayOf("application/json", "image/png")) },
                    variant = ButtonVariant.TONAL,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))

                // 分隔线
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    Text(
                        "或",
                        color = StradustTheme.colors.textMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "粘贴角色卡 JSON",
                    color = StradustTheme.colors.textSecondary,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(4.dp))
                StradustInput(
                    value = json,
                    onValueChange = { json = it; error = false },
                    hint = "{ \"name\": ... }",
                    maxLines = 8,
                )
                if (error) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "导入失败，请检查 JSON 格式",
                        color = StradustTheme.colors.error,
                        fontSize = 11.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (json.isNotBlank()) {
                        onConfirm(json.trim())
                        error = true // 若 onConfirm 未关闭对话框则说明失败
                    }
                },
            ) { Text("导入") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
/** 导出文件完成对话框：显示文件路径并提供分享功能 */
private fun ExportFileDialog(filePath: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val file = File(filePath)
    val fileName = file.name
    val fileSizeKb = file.length() / 1024

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出成功") },
        text = {
            Column {
                Text(
                    text = "角色完整数据已导出",
                    color = StradustTheme.colors.textPrimary,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "文件：$fileName",
                    color = StradustTheme.colors.textSecondary,
                    fontSize = 12.sp,
                )
                Text(
                    text = "大小：${fileSizeKb} KB",
                    color = StradustTheme.colors.textSecondary,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "包含：人格设定、角色卡、聊天记录、日记、好感度、成就、里程碑、时光胶囊、收藏、昵称、统计、头像",
                    color = StradustTheme.colors.textMuted,
                    fontSize = 11.sp,
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    try {
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "分享角色卡"))
                    } catch (e: Exception) {
                        Toast.makeText(context, "分享失败：${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("分享") }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
    )
}
