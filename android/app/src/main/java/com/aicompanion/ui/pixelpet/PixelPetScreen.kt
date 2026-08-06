package com.aicompanion.ui.pixelpet

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicompanion.pixelpet.DefaultRenderConfig
import com.aicompanion.pixelpet.LoopMode
import com.aicompanion.pixelpet.PetAction
import com.aicompanion.pixelpet.PixelPet
import com.aicompanion.pixelpet.PixelPetManager
import com.aicompanion.settings.ServicePresets
import com.aicompanion.theme.StradustTheme
import com.aicompanion.ui.components.ButtonSize
import com.aicompanion.ui.components.ButtonVariant
import com.aicompanion.ui.components.StradustButton
import com.aicompanion.ui.components.StradustCard
import com.aicompanion.ui.components.StradustInput
import com.aicompanion.ui.components.StradustTopBar
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * 像素宠物管理页
 *
 * 功能分区：
 * 1. 显示模式切换（Live2D / 像素宠物）
 * 2. 图片生成配置（API URL / Key / Model / Size / Steps / CFG Scale）
 * 3. 宠物列表（激活切换、删除、添加）
 * 4. 添加宠物对话框
 * 5. 当前激活宠物的动作列表（添加 / 删除动作）
 *
 * 后端 API：[PixelPetManager]（只调用，不修改）
 */
@Composable
fun PixelPetScreen(onBackClick: () -> Unit = {}) {
    val context = LocalContext.current
    val colors = StradustTheme.colors
    val manager = remember { PixelPetManager(context) }

    // 刷新计数器：任意数据变更后 +1，触发 remember 重新读取后端数据
    var refreshTick by remember { mutableStateOf(0) }

    val petMode = remember(refreshTick) { manager.getPetMode() }
    val pets = remember(refreshTick) { manager.loadPets() }
    val activePet = remember(refreshTick) { manager.getActivePet() }
    val actions = remember(refreshTick, activePet?.id) {
        activePet?.let { manager.getActionsForPet(it.id) } ?: emptyList()
    }

    // 图片生成配置表单：仅首次初始化，编辑过程不受 refreshTick 影响
    val initialConfig = remember { manager.getGenConfig() }
    var cfgApiUrl by remember { mutableStateOf(initialConfig.apiUrl) }
    var cfgApiKey by remember { mutableStateOf(initialConfig.apiKey) }
    var cfgModel by remember { mutableStateOf(initialConfig.model) }
    var cfgSize by remember { mutableStateOf(initialConfig.size) }
    var cfgSteps by remember { mutableStateOf(initialConfig.steps.toString()) }
    var cfgCfgScale by remember { mutableStateOf(initialConfig.cfgScale.toString()) }
    // 新增配置字段
    var cfgBatchSize by remember { mutableStateOf(initialConfig.batchSize) }
    var cfgProvider by remember { mutableStateOf(initialConfig.provider) }
    var cfgStylePrompt by remember { mutableStateOf(initialConfig.stylePrompt) }

    // 对话框与待删除项状态
    var showAddPetDialog by remember { mutableStateOf(false) }
    var showAddActionDialog by remember { mutableStateOf(false) }
    var pendingDeletePet by remember { mutableStateOf<PixelPet?>(null) }
    var pendingDeleteAction by remember { mutableStateOf<PetAction?>(null) }

    // 轻量状态提示（底部短暂浮层）
    var statusMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            StradustTopBar(title = "像素宠物", onBackClick = onBackClick)

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ===== 第一部分：模式切换 =====
                item {
                    StradustCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "显示模式",
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "选择桌面宠物的渲染方式",
                            color = colors.textMuted,
                            fontSize = 12.sp,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ModePill(
                                label = "Live2D 模式",
                                selected = petMode == "live2d",
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    manager.setPetMode("live2d")
                                    refreshTick++
                                    statusMessage = "已切换为 Live2D 模式"
                                },
                            )
                            ModePill(
                                label = "像素宠物模式",
                                selected = petMode == "pixel",
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    manager.setPetMode("pixel")
                                    refreshTick++
                                    statusMessage = "已切换为像素宠物模式"
                                },
                            )
                        }
                    }
                }

                // ===== 第二部分：图片生成配置 =====
                item {
                    StradustCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "图片生成配置",
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                        )
                        Spacer(Modifier.height(12.dp))

                        ConfigFieldLabel("API URL")
                        StradustInput(
                            value = cfgApiUrl,
                            onValueChange = { cfgApiUrl = it },
                            hint = "https://api.example.com/v1/images/generations",
                            singleLine = true,
                            imeAction = ImeAction.Done,
                        )
                        Spacer(Modifier.height(8.dp))

                        ConfigFieldLabel("API Key")
                        StradustInput(
                            value = cfgApiKey,
                            onValueChange = { cfgApiKey = it },
                            hint = "sk-...",
                            singleLine = true,
                            imeAction = ImeAction.Done,
                        )
                        Spacer(Modifier.height(8.dp))

                        ConfigFieldLabel("Model")
                        StradustInput(
                            value = cfgModel,
                            onValueChange = { cfgModel = it },
                            hint = "模型名称",
                            singleLine = true,
                            imeAction = ImeAction.Done,
                        )
                        Spacer(Modifier.height(8.dp))

                        ConfigFieldLabel("Size")
                        StradustInput(
                            value = cfgSize,
                            onValueChange = { cfgSize = it },
                            hint = "64x64",
                            singleLine = true,
                            imeAction = ImeAction.Done,
                        )
                        Spacer(Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                ConfigFieldLabel("Steps")
                                StradustInput(
                                    value = cfgSteps,
                                    onValueChange = { cfgSteps = it.filter { c -> c.isDigit() } },
                                    hint = "20",
                                    singleLine = true,
                                    imeAction = ImeAction.Done,
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                ConfigFieldLabel("CFG Scale")
                                StradustInput(
                                    value = cfgCfgScale,
                                    onValueChange = { cfgCfgScale = it },
                                    hint = "7.0",
                                    singleLine = true,
                                    imeAction = ImeAction.Done,
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))

                        // 提供商选择（复用 ServicePresets.imageGenPresets）
                        ConfigFieldLabel("提供商")
                        var providerExpanded by remember { mutableStateOf(false) }
                        val providerPresets = remember { ServicePresets.imageGenPresets }
                        val providerDisplayName = remember(cfgProvider) {
                            providerPresets.find { it.id == cfgProvider }?.displayName ?: cfgProvider
                        }
                        Box {
                            StradustInput(
                                value = providerDisplayName,
                                onValueChange = { cfgProvider = it },
                                hint = "选择或输入提供商",
                                singleLine = true,
                                imeAction = ImeAction.Done,
                                trailingIcon = {
                                    IconButton(onClick = { providerExpanded = true }) {
                                        Icon(
                                            imageVector = Icons.Default.Pets,
                                            contentDescription = "选择提供商",
                                            tint = colors.textSecondary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                },
                            )
                            DropdownMenu(
                                expanded = providerExpanded,
                                onDismissRequest = { providerExpanded = false },
                            ) {
                                providerPresets.forEach { preset ->
                                    DropdownMenuItem(
                                        text = { Text(preset.displayName) },
                                        onClick = {
                                            cfgProvider = preset.id
                                            // 自动填充 URL 和默认模型
                                            if (preset.url.isNotBlank()) cfgApiUrl = preset.url
                                            if (preset.defaultModel.isNotBlank()) cfgModel = preset.defaultModel
                                            providerExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))

                        // 批量大小 NumberStepper（1-4）
                        ConfigFieldLabel("批量大小：$cfgBatchSize")
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            IconButton(
                                onClick = { if (cfgBatchSize > 1) cfgBatchSize-- },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "减少", tint = colors.textPrimary)
                            }
                            Text(
                                text = cfgBatchSize.toString(),
                                color = colors.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            IconButton(
                                onClick = { if (cfgBatchSize < 4) cfgBatchSize++ },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "增加", tint = colors.textPrimary)
                            }
                        }
                        Spacer(Modifier.height(8.dp))

                        // 风格提示词
                        ConfigFieldLabel("风格提示词")
                        StradustInput(
                            value = cfgStylePrompt,
                            onValueChange = { cfgStylePrompt = it },
                            hint = "pixel art, 16-bit style, retro game sprite...",
                            maxLines = 3,
                            imeAction = ImeAction.Default,
                        )

                        Spacer(Modifier.height(12.dp))
                        StradustButton(
                            text = "保存配置",
                            onClick = {
                                val config = initialConfig.copy(
                                    apiUrl = cfgApiUrl.trim(),
                                    apiKey = cfgApiKey.trim(),
                                    model = cfgModel.trim(),
                                    size = cfgSize.trim(),
                                    steps = cfgSteps.toIntOrNull() ?: 20,
                                    cfgScale = cfgCfgScale.toFloatOrNull() ?: 7.0f,
                                    batchSize = cfgBatchSize,
                                    provider = cfgProvider.trim(),
                                    stylePrompt = cfgStylePrompt.trim(),
                                )
                                manager.saveGenConfig(config)
                                refreshTick++
                                statusMessage = "配置已保存"
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                // ===== 第三部分：宠物列表 =====
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "我的宠物",
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                        )
                        StradustButton(
                            text = "添加宠物",
                            onClick = { showAddPetDialog = true },
                            variant = ButtonVariant.TONAL,
                            size = ButtonSize.SMALL,
                        )
                    }
                }

                if (pets.isEmpty()) {
                    item {
                        EmptyHint(
                            icon = Icons.Default.Pets,
                            text = "还没有宠物，点击「添加宠物」创建一只吧",
                        )
                    }
                } else {
                    items(pets, key = { it.id }) { pet ->
                        PetCardItem(
                            pet = pet,
                            onClick = {
                                manager.setActivePet(pet)
                                refreshTick++
                                statusMessage = "已设为激活：${pet.name}"
                            },
                            onDelete = { pendingDeletePet = pet },
                        )
                    }
                }

                // ===== 第五部分：当前激活宠物的动作列表 =====
                if (activePet != null) {
                    val active = activePet
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "动作列表",
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                            )
                            StradustButton(
                                text = "添加动作",
                                onClick = { showAddActionDialog = true },
                                variant = ButtonVariant.TONAL,
                                size = ButtonSize.SMALL,
                            )
                        }
                    }

                    if (actions.isEmpty()) {
                        item {
                            EmptyHint(
                                icon = Icons.Default.Pets,
                                text = "暂无动作，点击「添加动作」为「${active.name}」创建动作",
                            )
                        }
                    } else {
                        items(actions, key = { it.id }) { action ->
                            ActionCardItem(
                                action = action,
                                onDelete = { pendingDeleteAction = action },
                            )
                        }
                    }
                }
            }
        }

        // 底部状态提示浮层
        statusMessage?.let { msg ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp, start = 16.dp, end = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceContainerHigh)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(text = msg, color = colors.textPrimary, fontSize = 13.sp)
            }
        }
    }

    // 状态提示 2 秒后自动消失
    LaunchedEffect(statusMessage) {
        if (statusMessage != null) {
            kotlinx.coroutines.delay(2000)
            statusMessage = null
        }
    }

    // ===== 添加宠物对话框 =====
    if (showAddPetDialog) {
        AddPetDialog(
            onDismiss = { showAddPetDialog = false },
            onConfirm = { name, description, basePrompt, negativePrompt,
                          referenceImagePath, spriteWidth, spriteHeight,
                          fps, scale, renderMode ->
                showAddPetDialog = false
                // createPet 不支持渲染参数，先创建再用 savePets 补充渲染配置
                val pet = manager.createPet(
                    name = name,
                    description = description.ifBlank { null },
                    referenceImagePath = referenceImagePath,
                    basePrompt = basePrompt,
                    negativePrompt = negativePrompt.ifBlank { null },
                )
                val updated = pet.copy(
                    spriteWidth = spriteWidth,
                    spriteHeight = spriteHeight,
                    fps = fps,
                    scale = scale,
                    renderMode = renderMode,
                )
                val updatedPets = manager.loadPets().map { if (it.id == pet.id) updated else it }
                manager.savePets(updatedPets)
                refreshTick++
                statusMessage = "宠物「$name」已创建"
            },
        )
    }

    // ===== 添加动作对话框 =====
    if (showAddActionDialog && activePet != null) {
        val petId = activePet.id
        AddActionDialog(
            onDismiss = { showAddActionDialog = false },
            onConfirm = { displayName, name, prompt, frameCount, loopMode,
                          frameDuration, triggerEvents ->
                showAddActionDialog = false
                // createAction 不支持 frameDuration/triggerEvents，先创建再用 saveAction 补充
                val action = manager.createAction(
                    petId = petId,
                    name = name,
                    displayName = displayName,
                    prompt = prompt,
                    frameCount = frameCount,
                    loopMode = loopMode,
                )
                val updated = action.copy(
                    frameDuration = frameDuration,
                    triggerEvents = triggerEvents.ifEmpty { null },
                )
                manager.saveAction(updated)
                refreshTick++
                statusMessage = "动作「$displayName」已创建"
            },
        )
    }

    // ===== 删除宠物确认 =====
    pendingDeletePet?.let { pet ->
        AlertDialog(
            onDismissRequest = { pendingDeletePet = null },
            title = { Text("删除宠物") },
            text = {
                Text(
                    text = "确定删除「${pet.name}」吗？关联的动作与帧图将被一并清除，此操作不可撤销。",
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeletePet = null
                    manager.deletePet(pet.id)
                    refreshTick++
                    statusMessage = "已删除「${pet.name}」"
                }) {
                    Text("删除", color = colors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletePet = null }) { Text("取消") }
            },
        )
    }

    // ===== 删除动作确认 =====
    pendingDeleteAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingDeleteAction = null },
            title = { Text("删除动作") },
            text = {
                Text(
                    text = "确定删除动作「${action.displayName}」吗？此操作不可撤销。",
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteAction = null
                    manager.deleteAction(action.petId, action.id)
                    refreshTick++
                    statusMessage = "已删除动作「${action.displayName}」"
                }) {
                    Text("删除", color = colors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteAction = null }) { Text("取消") }
            },
        )
    }
}

// ════════════════════ 子组件 ════════════════════

/** 模式/循环模式选择胶囊按钮 */
@Composable
private fun ModePill(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = StradustTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) colors.primary else colors.surfaceContainerLow.copy(alpha = 0.6f),
                RoundedCornerShape(20.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) colors.onPrimary else colors.textSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** 配置字段小标题 */
@Composable
private fun ConfigFieldLabel(text: String) {
    Text(
        text = text,
        color = StradustTheme.colors.textSecondary,
        fontSize = 12.sp,
    )
    Spacer(Modifier.height(4.dp))
}

/** 对话框字段小标题 */
@Composable
private fun DialogLabel(text: String) {
    Text(
        text = text,
        color = StradustTheme.colors.textSecondary,
        fontSize = 12.sp,
    )
    Spacer(Modifier.height(4.dp))
}

/** 空状态提示 */
@Composable
private fun EmptyHint(icon: ImageVector, text: String) {
    val colors = StradustTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(8.dp))
        Text(text = text, color = colors.textMuted, fontSize = 13.sp)
    }
}

/** 宠物卡片项 */
@Composable
private fun PetCardItem(
    pet: PixelPet,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = StradustTheme.colors
    StradustCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (pet.isActive) Modifier.border(2.dp, colors.primary, RoundedCornerShape(16.dp))
                else Modifier
            ),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Pets,
                contentDescription = null,
                tint = if (pet.isActive) colors.primary else colors.textMuted,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = pet.name,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                    if (pet.isActive) {
                        Spacer(Modifier.width(6.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.primary.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(12.dp),
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                text = "已激活",
                                color = colors.primary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                if (!pet.description.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = pet.description,
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = pet.basePrompt,
                    color = colors.textMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除宠物",
                    tint = colors.textMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** 动作卡片项 */
@Composable
private fun ActionCardItem(
    action: PetAction,
    onDelete: () -> Unit,
) {
    val colors = StradustTheme.colors
    StradustCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = action.displayName,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    if (action.isBuiltin) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "内置",
                            color = colors.textMuted,
                            fontSize = 10.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.surfaceContainerHigh, RoundedCornerShape(8.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${action.frameCount} 帧 · ${loopModeLabel(action.loopMode)}",
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = action.prompt,
                    color = colors.textMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除动作",
                    tint = colors.textMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

// ════════════════════ 对话框 ════════════════════

/** 添加宠物对话框 */
@Composable
private fun AddPetDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String, basePrompt: String, negativePrompt: String,
                referenceImagePath: String?, spriteWidth: Int, spriteHeight: Int,
                fps: Int, scale: Float, renderMode: String) -> Unit,
) {
    val colors = StradustTheme.colors
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var basePrompt by remember { mutableStateOf("") }
    var negativePrompt by remember { mutableStateOf("") }
    // 渲染配置状态
    var referenceImagePath by remember { mutableStateOf<String?>(null) }
    var spriteWidth by remember { mutableStateOf(DefaultRenderConfig.SPRITE_WIDTH) }
    var spriteHeight by remember { mutableStateOf(DefaultRenderConfig.SPRITE_HEIGHT) }
    var fps by remember { mutableStateOf(DefaultRenderConfig.FPS) }
    var scale by remember { mutableStateOf(DefaultRenderConfig.SCALE) }
    var renderMode by remember { mutableStateOf("overlay") }
    // 下拉菜单展开状态
    var spriteWExpanded by remember { mutableStateOf(false) }
    var spriteHExpanded by remember { mutableStateOf(false) }
    var renderModeExpanded by remember { mutableStateOf(false) }

    // 参考图选择器
    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            // 将选中的图片复制到内部存储，保存文件路径
            try {
                val dest = File(context.filesDir, "pet_ref_${System.currentTimeMillis()}.png")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                }
                referenceImagePath = dest.absolutePath
            } catch (_: Exception) {
                // 复制失败则保存 Uri 字符串作为后备
                referenceImagePath = uri.toString()
            }
        }
    }

    val spriteSizeOptions = listOf(32, 64, 128, 256)
    val renderModeOptions = listOf("overlay" to "覆盖层", "fullscreen" to "全屏")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加宠物") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                DialogLabel("名称 *")
                StradustInput(
                    value = name,
                    onValueChange = { name = it },
                    hint = "给宠物起个名字",
                    singleLine = true,
                    imeAction = ImeAction.Done,
                )
                Spacer(Modifier.height(8.dp))

                DialogLabel("描述")
                StradustInput(
                    value = description,
                    onValueChange = { description = it },
                    hint = "可选，宠物描述",
                    maxLines = 3,
                    imeAction = ImeAction.Default,
                )
                Spacer(Modifier.height(8.dp))

                DialogLabel("基础提示词 *")
                StradustInput(
                    value = basePrompt,
                    onValueChange = { basePrompt = it },
                    hint = "像素角色描述，如：a cute white cat",
                    maxLines = 3,
                    imeAction = ImeAction.Default,
                )
                Spacer(Modifier.height(8.dp))

                DialogLabel("负面提示词")
                StradustInput(
                    value = negativePrompt,
                    onValueChange = { negativePrompt = it },
                    hint = "可选，如：blurry, lowres",
                    maxLines = 2,
                    imeAction = ImeAction.Default,
                )
                Spacer(Modifier.height(12.dp))

                // 参考图选择
                DialogLabel("参考图")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StradustButton(
                        text = if (referenceImagePath == null) "选择参考图" else "已选择",
                        onClick = { pickImageLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        variant = ButtonVariant.OUTLINED,
                        size = ButtonSize.SMALL,
                        modifier = Modifier.weight(1f),
                    )
                    if (referenceImagePath != null) {
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { referenceImagePath = null }) {
                            Text("清除", color = colors.error, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                // 精灵尺寸下拉
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        DialogLabel("精灵宽度")
                        Box {
                            StradustInput(
                                value = spriteWidth.toString(),
                                onValueChange = {},
                                hint = "",
                                singleLine = true,
                                imeAction = ImeAction.Done,
                                trailingIcon = {
                                    IconButton(onClick = { spriteWExpanded = true }) {
                                        Icon(Icons.Default.Pets, contentDescription = "选择宽度", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                                    }
                                },
                            )
                            DropdownMenu(expanded = spriteWExpanded, onDismissRequest = { spriteWExpanded = false }) {
                                spriteSizeOptions.forEach { size ->
                                    DropdownMenuItem(text = { Text("$size px") }, onClick = { spriteWidth = size; spriteWExpanded = false })
                                }
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        DialogLabel("精灵高度")
                        Box {
                            StradustInput(
                                value = spriteHeight.toString(),
                                onValueChange = {},
                                hint = "",
                                singleLine = true,
                                imeAction = ImeAction.Done,
                                trailingIcon = {
                                    IconButton(onClick = { spriteHExpanded = true }) {
                                        Icon(Icons.Default.Pets, contentDescription = "选择高度", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                                    }
                                },
                            )
                            DropdownMenu(expanded = spriteHExpanded, onDismissRequest = { spriteHExpanded = false }) {
                                spriteSizeOptions.forEach { size ->
                                    DropdownMenuItem(text = { Text("$size px") }, onClick = { spriteHeight = size; spriteHExpanded = false })
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                // 帧率 Slider（5-30，默认 8）
                DialogLabel("帧率：$fps")
                Slider(
                    value = fps.toFloat(),
                    onValueChange = { fps = it.roundToInt() },
                    valueRange = 5f..30f,
                    steps = 24,
                )
                Spacer(Modifier.height(8.dp))

                // 缩放 Slider（0.5-3.0，默认 3.0）
                DialogLabel("缩放：${"%.1f".format(scale)}")
                Slider(
                    value = scale,
                    onValueChange = { scale = it },
                    valueRange = 0.5f..3.0f,
                    steps = 24,
                )
                Spacer(Modifier.height(8.dp))

                // 渲染模式下拉
                DialogLabel("渲染模式")
                Box {
                    val renderModeLabel = renderModeOptions.find { it.first == renderMode }?.second ?: renderMode
                    StradustInput(
                        value = renderModeLabel,
                        onValueChange = {},
                        hint = "",
                        singleLine = true,
                        imeAction = ImeAction.Done,
                        trailingIcon = {
                            IconButton(onClick = { renderModeExpanded = true }) {
                                Icon(Icons.Default.Pets, contentDescription = "选择渲染模式", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                            }
                        },
                    )
                    DropdownMenu(expanded = renderModeExpanded, onDismissRequest = { renderModeExpanded = false }) {
                        renderModeOptions.forEach { (mode, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { renderMode = mode; renderModeExpanded = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(name, description, basePrompt, negativePrompt,
                        referenceImagePath, spriteWidth, spriteHeight, fps, scale, renderMode)
                },
                enabled = name.isNotBlank() && basePrompt.isNotBlank(),
            ) { Text("创建", color = colors.primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 添加动作对话框 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AddActionDialog(
    onDismiss: () -> Unit,
    onConfirm: (displayName: String, name: String, prompt: String, frameCount: Int, loopMode: LoopMode,
                frameDuration: Long, triggerEvents: List<String>) -> Unit,
) {
    val colors = StradustTheme.colors
    var displayName by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var frameCount by remember { mutableStateOf(4) }
    var loopMode by remember { mutableStateOf(LoopMode.LOOP) }
    // 新增字段
    var frameDuration by remember { mutableStateOf(100L) }
    val triggerEventOptions = listOf("TAP", "IDLE", "SCHEDULED", "GREETING", "BIRTHDAY")
    val selectedTriggers = remember { androidx.compose.runtime.mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加动作") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                DialogLabel("显示名称")
                StradustInput(
                    value = displayName,
                    onValueChange = { displayName = it },
                    hint = "如：待机",
                    singleLine = true,
                    imeAction = ImeAction.Done,
                )
                Spacer(Modifier.height(8.dp))

                DialogLabel("动作名称（英文）")
                StradustInput(
                    value = name,
                    onValueChange = { name = it.filter { c -> c.isLetterOrDigit() || c == '_' } },
                    hint = "如：idle",
                    singleLine = true,
                    imeAction = ImeAction.Done,
                )
                Spacer(Modifier.height(8.dp))

                DialogLabel("提示词")
                StradustInput(
                    value = prompt,
                    onValueChange = { prompt = it },
                    hint = "动作描述，如：standing still, breathing",
                    maxLines = 3,
                    imeAction = ImeAction.Default,
                )
                Spacer(Modifier.height(8.dp))

                DialogLabel("帧数：$frameCount")
                Slider(
                    value = frameCount.toFloat(),
                    onValueChange = { frameCount = it.roundToInt() },
                    valueRange = 1f..12f,
                    steps = 10,
                )
                Spacer(Modifier.height(8.dp))

                // 帧持续时间 NumberStepper（50-500ms，默认 100，步进 50）
                DialogLabel("帧持续时间：${frameDuration}ms")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconButton(
                        onClick = { if (frameDuration > 50) frameDuration -= 50 },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "减少", tint = colors.textPrimary)
                    }
                    Text(
                        text = "${frameDuration}ms",
                        color = colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    IconButton(
                        onClick = { if (frameDuration < 500) frameDuration += 50 },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "增加", tint = colors.textPrimary)
                    }
                }
                Spacer(Modifier.height(8.dp))

                DialogLabel("循环模式")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "循环" to LoopMode.LOOP,
                        "单次" to LoopMode.ONCE,
                        "往返" to LoopMode.PINGPONG,
                    ).forEach { (label, mode) ->
                        ModePill(
                            label = label,
                            selected = loopMode == mode,
                            modifier = Modifier.weight(1f),
                            onClick = { loopMode = mode },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))

                // 触发事件多选 ChipGroup
                DialogLabel("触发事件")
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    triggerEventOptions.forEach { event ->
                        FilterChip(
                            selected = event in selectedTriggers,
                            onClick = {
                                if (event in selectedTriggers) selectedTriggers.remove(event)
                                else selectedTriggers.add(event)
                            },
                            label = { Text(event, fontSize = 11.sp) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(displayName, name, prompt, frameCount, loopMode,
                        frameDuration, selectedTriggers.toList())
                },
                enabled = displayName.isNotBlank() && name.isNotBlank() && prompt.isNotBlank(),
            ) { Text("创建", color = colors.primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

// ════════════════════ 辅助 ════════════════════

/** 循环模式中文标签 */
private fun loopModeLabel(mode: LoopMode): String = when (mode) {
    LoopMode.LOOP -> "循环"
    LoopMode.ONCE -> "单次"
    LoopMode.PINGPONG -> "往返"
}
