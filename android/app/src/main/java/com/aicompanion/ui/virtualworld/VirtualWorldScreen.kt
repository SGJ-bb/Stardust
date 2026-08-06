package com.aicompanion.ui.virtualworld

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicompanion.theme.StradustTheme
import com.aicompanion.ui.components.StradustButton
import com.aicompanion.ui.components.StradustCard
import com.aicompanion.ui.components.StradustInput
import com.aicompanion.ui.components.StradustTopBar
import com.aicompanion.virtualworld.StoryEvent
import com.aicompanion.virtualworld.VirtualWorldManager
import com.aicompanion.virtualworld.WorldConfig
import com.aicompanion.virtualworld.WorldState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 虚拟世界信息数据类（用于 AppHost 桥接） */
data class VirtualWorldInfo(
    val day: Int = 1,
    val hour: String = "08:00",
    val location: String = "起始之地",
    val weather: String = "晴朗",
    val mood: String = "平静",
    val isSimulating: Boolean = false,
    val events: List<StoryEvent> = emptyList(),
)

private enum class VwTab(val label: String, val icon: ImageVector) {
    WORLD("世界观", Icons.Default.Public),
    STORY("剧情推演", Icons.Default.Book),
    SYNC("同步状态", Icons.Default.Sync),
}

/**
 * 虚拟世界主界面（Compose 版本）
 *
 * 用户设定世界观 → AI 自动生成剧情 → 剧情同步到私聊和群聊
 *
 * 三个分区：
 * 1. 世界观设定：5 个文本编辑框 + AI 自动生成 + 角色选择 + 群聊模式 + 图片生成开关
 * 2. 剧情推演：开始/暂停/单次推演 + 故事事件流
 * 3. 同步状态：私聊记忆池/群聊消息流同步指示
 */
@Composable
fun VirtualWorldScreen(
    isSimulating: Boolean = false,
    storyEvents: List<StoryEvent> = emptyList(),
    onToggleSimulation: () -> Unit = {},
    onBackClick: () -> Unit = {},
    /** 上传参考图入口回调（跳转到 VwImageUploadScreen） */
    onUploadImageClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vwm = remember { VirtualWorldManager(context) }

    var activeTab by rememberSaveable { mutableIntStateOf(VwTab.WORLD.ordinal) }
    var refreshTick by remember { mutableIntStateOf(0) }

    // 当前世界配置（每次保存后递增 refreshTick 触发重组）
    var config by remember(refreshTick) { mutableStateOf(vwm.config) }
    var state by remember(refreshTick) { mutableStateOf(vwm.state) }
    val events = remember(refreshTick, storyEvents) {
        if (storyEvents.isNotEmpty()) storyEvents else vwm.getStoryEvents()
    }

    // 自动生成 loading
    var isGenerating by remember { mutableStateOf(false) }
    var isSingleTicking by remember { mutableStateOf(false) }
    var toastMsg by remember { mutableStateOf<String?>(null) }

    // 同步状态
    var syncStatus by remember(refreshTick) { mutableStateOf(querySyncStatus(context, vwm)) }

    // Toast 显示
    LaunchedEffect(toastMsg) {
        if (toastMsg != null) {
            kotlinx.coroutines.delay(2000)
            toastMsg = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        StradustTheme.colors.background,
                        StradustTheme.colors.backgroundSecondary,
                    ),
                ),
            ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            StradustTopBar(
                title = "虚拟世界",
                subtitle = "第${state.dayCount}天 ${String.format("%02d", state.hourOfDay)}:${String.format("%02d", state.minuteOfHour)} · ${state.currentLocation}",
                onBackClick = onBackClick,
                actions = {
                    // 上传参考图入口
                    IconButton(onClick = onUploadImageClick) {
                        Icon(
                            Icons.Default.AddPhotoAlternate,
                            contentDescription = "上传参考图",
                            tint = StradustTheme.colors.textSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
            )

            // Tab 切换栏
            VwTabRow(activeTab = activeTab) { activeTab = it }

            when (activeTab) {
                VwTab.WORLD.ordinal -> WorldConfigSection(
                    config = config,
                    isGenerating = isGenerating,
                    onConfigChange = { config = it },
                    onSave = {
                        scope.launch {
                            vwm.config = config
                            refreshTick++
                            toastMsg = "世界观已保存"
                        }
                    },
                    onAutoGenerate = { keywords ->
                        scope.launch {
                            isGenerating = true
                            try {
                                val newConfig = withContext(Dispatchers.IO) {
                                    autoGenerateWorldLore(context, vwm, config, keywords)
                                }
                                if (newConfig != null) {
                                    config = newConfig
                                    vwm.config = newConfig
                                    refreshTick++
                                    toastMsg = "世界观已自动生成"
                                } else {
                                    toastMsg = "生成失败，请检查 API 配置"
                                }
                            } catch (e: Exception) {
                                toastMsg = "生成失败：${e.message}"
                            } finally {
                                isGenerating = false
                            }
                        }
                    },
                )
                VwTab.STORY.ordinal -> StorySection(
                    state = state,
                    isSimulating = isSimulating,
                    isSingleTicking = isSingleTicking,
                    events = events,
                    onToggleSimulation = {
                        vwm.isEnabled = !isSimulating
                        vwm.isRunning = !isSimulating
                        onToggleSimulation()
                        refreshTick++
                    },
                    onSingleTick = {
                        scope.launch {
                            isSingleTicking = true
                            try {
                                val ev = withContext(Dispatchers.IO) { vwm.runSimulationTick() }
                                if (ev != null) {
                                    refreshTick++
                                    toastMsg = "已推演一轮：第${ev.virtualDay}天 ${ev.virtualHour}:00"
                                    // 同步状态刷新
                                    syncStatus = querySyncStatus(context, vwm)
                                } else {
                                    toastMsg = "推演失败，请检查 API 配置"
                                }
                            } catch (e: Exception) {
                                toastMsg = "推演异常：${e.message}"
                            } finally {
                                isSingleTicking = false
                            }
                        }
                    },
                    onReset = {
                        scope.launch {
                            vwm.resetWorld()
                            refreshTick++
                            toastMsg = "已重置世界"
                        }
                    },
                )
                VwTab.SYNC.ordinal -> SyncSection(
                    syncStatus = syncStatus,
                    onRefresh = {
                        scope.launch {
                            syncStatus = querySyncStatus(context, vwm)
                            refreshTick++
                        }
                    },
                    onPublishLastToGroup = {
                        scope.launch {
                            val lastEvent = events.lastOrNull()
                            if (lastEvent != null) {
                                withContext(Dispatchers.IO) {
                                    publishEventToGroupChat(context, vwm, lastEvent)
                                }
                                syncStatus = querySyncStatus(context, vwm)
                                toastMsg = "已推送到群聊"
                            } else {
                                toastMsg = "暂无事件可推送"
                            }
                        }
                    },
                )
            }
        }

        // Toast 浮层
        toastMsg?.let { msg ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Surface(
                    color = StradustTheme.colors.surfaceContainerHigh,
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 6.dp,
                ) {
                    Text(
                        text = msg,
                        color = StradustTheme.colors.textPrimary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

// ===== Tab 栏 =====

@Composable
private fun VwTabRow(activeTab: Int, onTabSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        VwTab.entries.forEachIndexed { idx, tab ->
            val isActive = idx == activeTab
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isActive) StradustTheme.colors.primary
                        else StradustTheme.colors.surfaceContainerLow.copy(alpha = 0.5f),
                        RoundedCornerShape(20.dp),
                    )
                    .clickable { onTabSelect(idx) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Icon(
                    tab.icon,
                    contentDescription = tab.label,
                    modifier = Modifier.size(16.dp),
                    tint = if (isActive) StradustTheme.colors.onPrimary
                    else StradustTheme.colors.textSecondary,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = tab.label,
                    color = if (isActive) StradustTheme.colors.onPrimary
                    else StradustTheme.colors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

// ===== 世界观设定分区 =====

@Composable
private fun WorldConfigSection(
    config: WorldConfig,
    isGenerating: Boolean,
    onConfigChange: (WorldConfig) -> Unit,
    onSave: () -> Unit,
    onAutoGenerate: (String) -> Unit,
) {
    val context = LocalContext.current
    var keywords by remember { mutableStateOf("") }

    // 角色选择
    val personas by produceState(initialValue = emptyList<com.aicompanion.persona.Persona>()) {
        value = withContext(Dispatchers.IO) {
            com.aicompanion.persona.PersonaManager(context).also { it.load() }.getAllPersonas()
        }
    }
    var showPersonaPicker by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SectionHeader(
                icon = Icons.Default.Public,
                title = "世界观设定",
                subtitle = "设定背景/规则/关系/场景/风格，AI 会基于此生成剧情",
            )
        }

        // 5 个文本编辑框
        item {
            StradustCard {
                WorldConfigField(
                    label = "【世界背景】",
                    value = config.worldBackground,
                    placeholder = "描述世界的起源、时代、地理、文明...",
                    onValueChange = { onConfigChange(config.copy(worldBackground = it)) },
                )
                Spacer(Modifier.height(10.dp))
                WorldConfigField(
                    label = "【世界规则】",
                    value = config.worldRules,
                    placeholder = "魔法系统、科技水平、社会法则、特殊规则...",
                    onValueChange = { onConfigChange(config.copy(worldRules = it)) },
                )
                Spacer(Modifier.height(10.dp))
                WorldConfigField(
                    label = "【角色关系】",
                    value = config.worldRelations,
                    placeholder = "角色之间的关系、阵营、羁绊、矛盾...",
                    onValueChange = { onConfigChange(config.copy(worldRelations = it)) },
                )
                Spacer(Modifier.height(10.dp))
                WorldConfigField(
                    label = "【初始场景】",
                    value = config.worldScene,
                    placeholder = "故事开始的地点、氛围、时间...",
                    onValueChange = { onConfigChange(config.copy(worldScene = it)) },
                )
                Spacer(Modifier.height(10.dp))
                WorldConfigField(
                    label = "【叙事风格】",
                    value = config.worldStyle,
                    placeholder = "轻松治愈/暗黑悬疑/史诗奇幻/日常向...",
                    onValueChange = { onConfigChange(config.copy(worldStyle = it)) },
                )
            }
        }

        // AI 自动生成
        item {
            StradustCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = StradustTheme.colors.tertiary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "AI 自动生成世界观",
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                }
                Spacer(Modifier.height(8.dp))
                StradustInput(
                    value = keywords,
                    onValueChange = { keywords = it },
                    hint = "关键词（可选，如：末日、修真、星际...）",
                    singleLine = true,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        StradustButton(
                            text = if (isGenerating) "生成中..." else "自动生成",
                            onClick = { onAutoGenerate(keywords) },
                            enabled = !isGenerating,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        StradustButton(
                            text = "保存配置",
                            onClick = onSave,
                            variant = com.aicompanion.ui.components.ButtonVariant.OUTLINED,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (isGenerating) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = StradustTheme.colors.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "AI 正在构思世界观...",
                            color = StradustTheme.colors.textSecondary,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }

        // 参与角色
        item {
            StradustCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPersonaPicker = true },
                ) {
                    Icon(
                        Icons.Default.Group,
                        contentDescription = null,
                        tint = StradustTheme.colors.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "参与推演的角色",
                            color = StradustTheme.colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = if (config.memberPersonaIds.isEmpty()) "未选择（将使用默认角色）"
                            else "已选 ${config.memberPersonaIds.size} 个：${
                                personas.filter { it.id in config.memberPersonaIds }.joinToString("、") { it.name }
                            }",
                            color = StradustTheme.colors.textMuted,
                            fontSize = 11.sp,
                        )
                    }
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = "选择",
                        tint = StradustTheme.colors.textMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        // 推演参数
        item {
            StradustCard {
                Text(
                    text = "推演参数",
                    color = StradustTheme.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(10.dp))

                // 群聊模式开关
                SwitchRow(
                    title = "群聊推演模式",
                    subtitle = "多角色群像互动（关闭则为单人推演）",
                    checked = config.isGroupSimulation,
                    onCheckedChange = { onConfigChange(config.copy(isGroupSimulation = it)) },
                )
                Spacer(Modifier.height(6.dp))

                // 图片生成开关
                SwitchRow(
                    title = "剧情配图生成",
                    subtitle = "重大剧情节点自动生成场景配图",
                    checked = config.imageGenEnabled,
                    onCheckedChange = { onConfigChange(config.copy(imageGenEnabled = it)) },
                )
                Spacer(Modifier.height(10.dp))

                // 时间比率
                Text(
                    text = "时间比率：${config.timeRatio}x（虚拟时间流速 = 现实时间 × 比率）",
                    color = StradustTheme.colors.textSecondary,
                    fontSize = 12.sp,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                ) {
                    listOf(1, 2, 5, 10, 30, 60).forEach { ratio ->
                        val isSelected = config.timeRatio == ratio
                        Box(
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) StradustTheme.colors.primary
                                    else StradustTheme.colors.surfaceContainerLow,
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable { onConfigChange(config.copy(timeRatio = ratio)) }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = "${ratio}x",
                                color = if (isSelected) StradustTheme.colors.onPrimary
                                else StradustTheme.colors.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                // Tick 间隔
                Text(
                    text = "推演间隔：${config.tickIntervalMinutes} 分钟",
                    color = StradustTheme.colors.textSecondary,
                    fontSize = 12.sp,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                ) {
                    listOf(5, 15, 30, 60, 180, 360).forEach { minutes ->
                        val isSelected = config.tickIntervalMinutes == minutes
                        Box(
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) StradustTheme.colors.primary
                                    else StradustTheme.colors.surfaceContainerLow,
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable { onConfigChange(config.copy(tickIntervalMinutes = minutes)) }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = "${minutes}分",
                                color = if (isSelected) StradustTheme.colors.onPrimary
                                else StradustTheme.colors.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    // 角色选择对话框
    if (showPersonaPicker) {
        PersonaPickerDialog(
            personas = personas,
            selectedIds = config.memberPersonaIds,
            onDismiss = { showPersonaPicker = false },
            onConfirm = { ids ->
                onConfigChange(config.copy(memberPersonaIds = ids))
                showPersonaPicker = false
            },
        )
    }
}

@Composable
private fun WorldConfigField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
) {
    Column {
        Text(
            text = label,
            color = StradustTheme.colors.tertiary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        StradustInput(
            value = value,
            onValueChange = onValueChange,
            hint = placeholder,
            maxLines = 5,
        )
    }
}

@Composable
private fun PersonaPickerDialog(
    personas: List<com.aicompanion.persona.Persona>,
    selectedIds: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    var checked by remember { mutableStateOf(selectedIds.toSet()) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择参与推演的角色", fontWeight = FontWeight.SemiBold) },
        text = {
            if (personas.isEmpty()) {
                Text("暂无角色，请先在主页创建角色", color = StradustTheme.colors.textMuted, fontSize = 13.sp)
            } else {
                Column {
                    personas.forEach { p ->
                        val isChecked = p.id in checked
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    checked = if (isChecked) checked - p.id
                                    else checked + p.id
                                }
                                .padding(vertical = 6.dp),
                        ) {
                            Icon(
                                if (isChecked) Icons.Default.Check
                                else Icons.Default.Person,
                                contentDescription = null,
                                tint = if (isChecked) StradustTheme.colors.primary
                                else StradustTheme.colors.textMuted,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(p.name, color = StradustTheme.colors.textPrimary, fontSize = 14.sp)
                                Text(
                                    text = p.personality.takeIf { it.isNotBlank() } ?: "无性格描述",
                                    color = StradustTheme.colors.textMuted,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onConfirm(checked.toList()) }) {
                Text("确定")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

// ===== 剧情推演分区 =====

@Composable
private fun StorySection(
    state: WorldState,
    isSimulating: Boolean,
    isSingleTicking: Boolean,
    events: List<StoryEvent>,
    onToggleSimulation: () -> Unit,
    onSingleTick: () -> Unit,
    onReset: () -> Unit,
) {
    val reversedEvents = remember(events) { events.asReversed() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SectionHeader(
                icon = Icons.Default.Book,
                title = "剧情推演",
                subtitle = "AI 会根据世界观自动生成剧情，并同步到私聊和群聊",
            )
        }

        // 当前世界状态
        item {
            StradustCard {
                Text(
                    text = "当前世界状态",
                    color = StradustTheme.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    StatePill(icon = Icons.Default.CalendarToday, label = "第${state.dayCount}天")
                    StatePill(icon = Icons.Default.Schedule, label = "${String.format("%02d", state.hourOfDay)}:${String.format("%02d", state.minuteOfHour)}")
                    StatePill(icon = Icons.Default.LocationOn, label = state.currentLocation)
                    StatePill(icon = Icons.Default.WbSunny, label = state.currentWeather)
                    StatePill(icon = Icons.Default.Favorite, label = state.currentMood, highlight = true)
                }
            }
        }

        // 推演控制
        item {
            StradustCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isSimulating) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = if (isSimulating) StradustTheme.colors.error else StradustTheme.colors.tertiary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isSimulating) "推演进行中" else "推演已暂停",
                            color = StradustTheme.colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = if (isSimulating) "AI 正在自动生成剧情..."
                            else "点击开始让 AI 持续推演剧情",
                            color = StradustTheme.colors.textMuted,
                            fontSize = 11.sp,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        StradustButton(
                            text = if (isSimulating) "暂停推演" else "开始推演",
                            onClick = onToggleSimulation,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        StradustButton(
                            text = if (isSingleTicking) "推演中..." else "单次推演",
                            onClick = onSingleTick,
                            enabled = !isSingleTicking,
                            variant = com.aicompanion.ui.components.ButtonVariant.OUTLINED,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (isSingleTicking) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = StradustTheme.colors.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "AI 正在推演剧情...",
                            color = StradustTheme.colors.textSecondary,
                            fontSize = 12.sp,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    Box(modifier = Modifier.weight(1f)) {
                        StradustButton(
                            text = "重置世界",
                            onClick = onReset,
                            variant = com.aicompanion.ui.components.ButtonVariant.TONAL,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        // 故事事件流
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    tint = StradustTheme.colors.tertiary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "故事事件流（共 ${events.size} 条）",
                    color = StradustTheme.colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        if (events.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Book,
                            contentDescription = null,
                            tint = StradustTheme.colors.textDisabled,
                            modifier = Modifier.size(40.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "暂无事件记录",
                            color = StradustTheme.colors.textDisabled,
                            fontSize = 13.sp,
                        )
                        Text(
                            text = "点击「单次推演」或「开始推演」生成剧情",
                            color = StradustTheme.colors.textMuted,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        } else {
            items(reversedEvents, key = { it.id }) { event ->
                StoryEventCard(event = event)
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun StatePill(
    icon: ImageVector,
    label: String,
    highlight: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (highlight) StradustTheme.colors.primary.copy(alpha = 0.12f)
                else StradustTheme.colors.surfaceContainerLow.copy(alpha = 0.6f),
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = if (highlight) StradustTheme.colors.primary
            else StradustTheme.colors.textSecondary,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            color = if (highlight) StradustTheme.colors.primary
            else StradustTheme.colors.textSecondary,
            fontSize = 10.sp,
            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StoryEventCard(event: StoryEvent) {
    var showDetail by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDetail = true },
        shape = RoundedCornerShape(12.dp),
        color = StradustTheme.colors.surfaceContainerLow.copy(alpha = 0.5f),
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "第${event.virtualDay}天 ${String.format("%02d:%02d", event.virtualHour, event.virtualMinute)}",
                    color = StradustTheme.colors.textDisabled,
                    fontSize = 11.sp,
                )
                Text(
                    text = event.speakerName,
                    color = StradustTheme.colors.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = event.content,
                color = StradustTheme.colors.textSecondary,
                fontSize = 13.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp,
            )
            if (event.imageUrl.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "🖼️ 含场景配图",
                    color = StradustTheme.colors.tertiary,
                    fontSize = 10.sp,
                )
            }
            if (event.eventType.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = eventTypeLabel(event.eventType),
                    color = StradustTheme.colors.textDisabled,
                    fontSize = 10.sp,
                )
            }
        }
    }

    if (showDetail) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDetail = false },
            title = { Text("事件详情", fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Text(
                        text = "第${event.virtualDay}天 ${String.format("%02d:%02d", event.virtualHour, event.virtualMinute)}",
                        color = StradustTheme.colors.textMuted,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "说话人：${event.speakerName}",
                        color = StradustTheme.colors.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (event.summary.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text("摘要：${event.summary}", color = StradustTheme.colors.textSecondary, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(
                        color = StradustTheme.colors.outlineVariant.copy(alpha = 0.3f),
                        thickness = 0.5.dp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = event.content,
                        color = StradustTheme.colors.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showDetail = false }) { Text("关闭") }
            },
        )
    }
}

private fun eventTypeLabel(type: String): String = when (type) {
    "narrative" -> "📖 叙述"
    "action" -> "⚡ 行动"
    "group_narrative" -> "👥 群像"
    "discovery" -> "✨ 发现"
    "emotion" -> "💭 情感"
    "weather" -> "🌤 天气"
    "dialogue" -> "💬 对话"
    else -> "📝 $type"
}

// ===== 同步状态分区 =====

@Composable
private fun SyncSection(
    syncStatus: SyncStatus,
    onRefresh: () -> Unit,
    onPublishLastToGroup: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SectionHeader(
                icon = Icons.Default.Sync,
                title = "同步状态",
                subtitle = "剧情事件自动同步到私聊记忆池，并可选推送到群聊消息流",
            )
        }

        // 私聊同步
        item {
            StradustCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = if (syncStatus.privateSynced) StradustTheme.colors.tertiary else StradustTheme.colors.textMuted,
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "私聊记忆池",
                            color = StradustTheme.colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = if (syncStatus.privateSynced)
                                "已同步 ${syncStatus.privateSyncCount} 条事件"
                            else "暂无同步记录",
                            color = StradustTheme.colors.textMuted,
                            fontSize = 11.sp,
                        )
                    }
                    SyncBadge(synced = syncStatus.privateSynced)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "每轮推演后，事件会自动写入私聊记忆池，AI 在私聊中可回忆起虚拟世界的经历",
                    color = StradustTheme.colors.textMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
        }

        // 群聊同步
        item {
            StradustCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Forum,
                        contentDescription = null,
                        tint = if (syncStatus.groupSynced) StradustTheme.colors.tertiary else StradustTheme.colors.textMuted,
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "群聊消息流",
                            color = StradustTheme.colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = if (syncStatus.groupSynced)
                                "已推送 ${syncStatus.groupSyncCount} 条消息到群聊"
                            else "未绑定群聊（需在世界观中启用群聊模式并创建对应群聊）",
                            color = StradustTheme.colors.textMuted,
                            fontSize = 11.sp,
                        )
                    }
                    SyncBadge(synced = syncStatus.groupSynced)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        StradustButton(
                            text = "推送最近事件到群聊",
                            onClick = onPublishLastToGroup,
                            enabled = syncStatus.lastEventExists,
                            modifier = Modifier.fillMaxWidth(),
                            size = com.aicompanion.ui.components.ButtonSize.SMALL,
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        StradustButton(
                            text = "刷新状态",
                            onClick = onRefresh,
                            variant = com.aicompanion.ui.components.ButtonVariant.OUTLINED,
                            modifier = Modifier.fillMaxWidth(),
                            size = com.aicompanion.ui.components.ButtonSize.SMALL,
                        )
                    }
                }
            }
        }

        // VW 记忆池状态
        item {
            StradustCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Book,
                        contentDescription = null,
                        tint = StradustTheme.colors.tertiary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "虚拟世界专用记忆池",
                            color = StradustTheme.colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "已记录 ${syncStatus.vwMemoryCount} 条事件 · ${
                                if (syncStatus.vwMemoryNeedsConsolidate) "需要压缩" else "无需压缩"
                            }",
                            color = StradustTheme.colors.textMuted,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }

        // 最近同步时间
        if (syncStatus.lastSyncTimeMs > 0) {
            item {
                Text(
                    text = "最近同步：${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(syncStatus.lastSyncTimeMs))}",
                    color = StradustTheme.colors.textMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SyncBadge(synced: Boolean) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (synced) StradustTheme.colors.tertiary.copy(alpha = 0.15f)
                else StradustTheme.colors.surfaceContainerLow,
                CircleShape,
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = if (synced) "✓ 已同步" else "未同步",
            color = if (synced) StradustTheme.colors.tertiary else StradustTheme.colors.textMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ===== 通用组件 =====

@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    StradustTheme.colors.primary.copy(alpha = 0.12f),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = StradustTheme.colors.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                color = StradustTheme.colors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                color = StradustTheme.colors.textMuted,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = StradustTheme.colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                color = StradustTheme.colors.textMuted,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.width(8.dp))
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = StradustTheme.colors.onPrimary,
                checkedTrackColor = StradustTheme.colors.primary,
            ),
        )
    }
}

// ===== 业务辅助函数 =====

/** 同步状态汇总 */
data class SyncStatus(
    val privateSynced: Boolean = false,
    val privateSyncCount: Int = 0,
    val groupSynced: Boolean = false,
    val groupSyncCount: Int = 0,
    val vwMemoryCount: Int = 0,
    val vwMemoryNeedsConsolidate: Boolean = false,
    val lastSyncTimeMs: Long = 0L,
    val lastEventExists: Boolean = false,
)

private fun querySyncStatus(context: android.content.Context, vwm: VirtualWorldManager): SyncStatus {
    return try {
        val events = vwm.getStoryEvents()
        val vwPool = vwm.vwMemoryPool
        val vwCount = vwPool.getRecentSummaries(9999).size
        val needsConsolidate = vwPool.needsConsolidate()

        // 私聊同步：从 persona_data_{pid} SP 中读取虚拟世界事件计数（简化判断）
        val personaId = vwm.config.memberPersonaIds.firstOrNull() ?: "default"
        val chatPrefs = context.getSharedPreferences("memory_pool_${personaId}_private", android.content.Context.MODE_PRIVATE)
        val privateCount = chatPrefs.all.values.count { v ->
            (v is String) && v.contains("虚拟世界·第")
        }

        // 群聊同步：检查是否存在与 worldId 同名的群聊
        val worldId = vwm.currentWorldId
        val groupSyncCount = if (worldId.isBlank()) 0 else {
            try {
                val gcManager = com.aicompanion.groupchat.GroupChatManager(context)
                gcManager.load()
                val group = gcManager.getGroup(worldId)
                if (group != null) {
                    val msgs = gcManager.getMessages(worldId)
                    msgs.count { m -> !m.isUser && m.text.contains("[第") && m.text.contains("天") }
                } else 0
            } catch (_: Exception) { 0 }
        }
        val groupSynced = groupSyncCount > 0

        val lastSyncMs = events.maxOfOrNull { it.timestamp } ?: 0L

        SyncStatus(
            privateSynced = privateCount > 0,
            privateSyncCount = privateCount,
            groupSynced = groupSynced,
            groupSyncCount = groupSyncCount,
            vwMemoryCount = vwCount,
            vwMemoryNeedsConsolidate = needsConsolidate,
            lastSyncTimeMs = lastSyncMs,
            lastEventExists = events.isNotEmpty(),
        )
    } catch (_: Exception) {
        SyncStatus()
    }
}

/** 自动生成世界观 — 复用 PromptBuilder.buildAutoWorldLorePrompt */
private suspend fun autoGenerateWorldLore(
    context: android.content.Context,
    vwm: VirtualWorldManager,
    config: WorldConfig,
    keywords: String,
): WorldConfig? = withContext(Dispatchers.IO) {
    val sm = com.aicompanion.settings.SettingsManager(context)
    if (sm.chatApiUrl.isBlank()) return@withContext null

    val personaManager = com.aicompanion.persona.PersonaManager(context)
    personaManager.load()

    val personaDescs = config.memberPersonaIds.mapNotNull { pid ->
        val p = personaManager.getPersona(pid) ?: return@mapNotNull null
        val identity = com.aicompanion.prompt.PromptBuilder.buildIdentity(context, pid)
        val prefs = context.getSharedPreferences("persona_data_$pid", android.content.Context.MODE_PRIVATE)
        buildString {
            append("「${identity.name}」性格${identity.personality}。${identity.speechStyle}。")
            prefs.getString("persona_appearance", "")?.takeIf { it.isNotBlank() }?.let { append(" 外貌：$it。") }
            prefs.getString("persona_preferences", "")?.takeIf { it.isNotBlank() }?.let { append(" 喜好：$it。") }
            prefs.getString("world_setting", "")?.takeIf { it.isNotBlank() }?.let { append(" 世界观：$it。") }
            prefs.getString("world_relationship", "")?.takeIf { it.isNotBlank() }?.let { append(" 关系：$it。") }
        }
    }.joinToString("\n")

    val allPersonaDescs = if (personaDescs.isBlank()) {
        personaManager.getAllPersonas().take(5).mapNotNull { p ->
            val prefs = context.getSharedPreferences("persona_data_${p.id}", android.content.Context.MODE_PRIVATE)
            buildString {
                append("「${p.name}」性格${p.personality}。${p.speechStyle}。")
                prefs.getString("persona_appearance", "")?.takeIf { it.isNotBlank() }?.let { append(" 外貌：$it。") }
                prefs.getString("persona_preferences", "")?.takeIf { it.isNotBlank() }?.let { append(" 喜好：$it。") }
                prefs.getString("world_setting", "")?.takeIf { it.isNotBlank() }?.let { append(" 世界观：$it。") }
                prefs.getString("world_relationship", "")?.takeIf { it.isNotBlank() }?.let { append(" 关系：$it。") }
            }.takeIf { it.isNotBlank() }
        }.joinToString("\n")
    } else {
        personaDescs
    }

    val chatSummary = buildString {
        val worldId = vwm.currentWorldId
        if (worldId.isNotBlank()) {
            try {
                val gcManager = com.aicompanion.groupchat.GroupChatManager(context)
                gcManager.load()
                val msgs = gcManager.getMessages(worldId)
                append(msgs.takeLast(20).map { msg ->
                    if (msg.isUser) "用户：${msg.text}" else "${msg.senderName}：${msg.text}"
                }.joinToString("\n"))
            } catch (_: Exception) {}
        }
    }

    val client = com.aicompanion.network.ApiClient(
        sm.chatApiUrl, sm.chatApiKey, sm.chatModel,
        sm.llmTemperature, sm.llmTopP, sm.llmFrequencyPenalty, sm.llmPresencePenalty, sm.llmMaxTokens,
        sm.apiProvider
    )
    val prompt = com.aicompanion.prompt.PromptBuilder.buildAutoWorldLorePrompt(allPersonaDescs, chatSummary, keywords)

    val response = client.sendSimplePrompt(prompt, "生成世界观") ?: return@withContext null
    if (response.text.isBlank()) return@withContext null

    var text = response.text.trim()
        .replace(Regex("```(?:json)?\\s*"), "")
        .replace("```", "")
        .trim()
    val bracketStart = text.indexOf('{')
    val bracketEnd = text.lastIndexOf('}')
    if (bracketStart >= 0 && bracketEnd > bracketStart) {
        text = text.substring(bracketStart, bracketEnd + 1)
    }

    val json = org.json.JSONObject(text)
    config.copy(
        worldBackground = json.optString("worldBackground", config.worldBackground),
        worldRules = json.optString("worldRules", config.worldRules),
        worldRelations = json.optString("worldRelations", config.worldRelations),
        worldScene = json.optString("worldScene", config.worldScene),
        worldStyle = json.optString("worldStyle", config.worldStyle),
    )
}

/** 推送事件到群聊 */
private fun publishEventToGroupChat(
    context: android.content.Context,
    vwm: VirtualWorldManager,
    event: StoryEvent,
) {
    val worldId = vwm.currentWorldId
    if (worldId.isBlank()) return

    val gcManager = com.aicompanion.groupchat.GroupChatManager(context)
    gcManager.load()
    if (gcManager.getGroup(worldId) == null) return

    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    val personaManager = com.aicompanion.persona.PersonaManager(context)
    personaManager.load()
    val senderId = vwm.config.memberPersonaIds.firstOrNull { pid ->
        personaManager.getPersona(pid)?.name == event.speakerName
    } ?: "narrator"

    val displayText = buildString {
        append("[第${event.virtualDay}天${String.format("%02d", event.virtualHour)}:00] ")
        append(event.content)
    }

    val msg = com.aicompanion.groupchat.GroupMessage(
        senderPersonaId = senderId,
        senderName = event.speakerName,
        text = displayText,
        time = time,
        isUser = false,
        emotion = "neutral"
    )
    gcManager.addMessage(worldId, msg)
}
