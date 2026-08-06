package com.aicompanion.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aicompanion.theme.StradustTheme
import com.aicompanion.ui.components.StradustCard
import com.aicompanion.ui.components.StradustTopBar

/**
 * 角色卡片数据类
 *
 * 用于在首页展示每个 AI 角色（AI伴侣）的摘要信息
 */
data class PersonaCard(
    val id: String,
    val name: String,
    val avatarPath: String?,
    val description: String,
    val lastChatTime: String,
    val messageCount: Int,
    val affectionLevel: Int,
)

/**
 * 功能入口数据类
 *
 * 用于在首页功能板块展示功能入口（图标 + 标签 + 路由）
 */
data class FeatureEntry(
    val icon: ImageVector,
    val label: String,
    val route: String,
)

/**
 * 功能分组数据类
 *
 * 用于将侧边栏功能入口按语义分组展示，降低用户认知负担
 */
data class FeatureGroup(
    val title: String,
    val features: List<FeatureEntry>,
)

/** 日常功能入口 */
private val moments = FeatureEntry(Icons.Default.AutoAwesome, "动态", "moments")
private val diary = FeatureEntry(Icons.Default.EditNote, "日记", "diary")
private val album = FeatureEntry(Icons.Default.PhotoLibrary, "相册", "album")
private val achievement = FeatureEntry(Icons.Default.EmojiEvents, "成就", "achievement")
private val archive = FeatureEntry(Icons.Default.CreditCard, "角色设定", "character_card")
private val worldBook = FeatureEntry(Icons.Default.MenuBook, "世界书", "world_book")
private val virtualWorld = FeatureEntry(Icons.Default.Public, "虚拟世界", "virtual_world")
private val checkIn = FeatureEntry(Icons.Default.CheckCircle, "签到", "check_in")
private val groupChat = FeatureEntry(Icons.Default.Group, "群聊", "group_chat_list")
private val calendar = FeatureEntry(Icons.Default.CalendarToday, "日历", "calendar")
private val schedule = FeatureEntry(Icons.Default.Schedule, "日程", "schedule")
private val timeCapsule = FeatureEntry(Icons.Default.AccessTime, "时光胶囊", "time_capsule")
private val pixelPet = FeatureEntry(Icons.Default.Pets, "像素宠物", "pixel_pet")

/**
 * 首页功能入口分组列表
 *
 * 将13个功能入口按语义分为3组：日常、角色、探索
 */
val featureGroups = listOf(
    FeatureGroup("日常", listOf(moments, diary, album, checkIn, calendar, schedule)),
    FeatureGroup("角色", listOf(archive, worldBook, achievement)),
    FeatureGroup("探索", listOf(virtualWorld, groupChat, timeCapsule, pixelPet)),
)

/**
 * 主页屏幕 — 角色选择/对话列表页
 *
 * 布局：
 * - 最底层：壁纸背景（如有）
 * - 遮罩层：主题色半透明遮罩，保证 UI 可读性
 * - 主内容：顶部栏（汉堡菜单 + 标题 + 设置）+ 角色列表 + FAB
 * - 侧边栏：可收缩的功能入口面板（左侧滑出，覆盖在主内容之上）
 *
 * @param personas 角色卡片列表
 * @param wallpaperPath 壁纸路径（作为主页背景）
 * @param onPersonaClick 点击角色卡片的回调，传入角色 ID
 * @param onAddPersona 点击添加角色按钮的回调（创建新角色）
 * @param onMomentsClick 点击"动态"入口的回调
 * @param onDiaryClick 点击"日记"入口的回调，需传入角色 ID
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    personas: List<PersonaCard> = emptyList(),
    wallpaperPath: String? = null,
    onPersonaClick: (personaId: String) -> Unit = {},
    onAddPersona: () -> Unit = {},
    onMomentsClick: () -> Unit = {},
    onDiaryClick: (personaId: String) -> Unit = {},
    onAlbumClick: () -> Unit = {},
    onAchievementClick: () -> Unit = {},
    onVirtualWorldClick: () -> Unit = {},
    onCheckInClick: () -> Unit = {},
    onGroupChatClick: () -> Unit = {},
    /** 点击"查看全部"角色入口的回调 */
    onViewAllPersonas: () -> Unit = {},
    /** 通用导航回调，传入 StradustDestinations 路由常量（settings/character_card/calendar/schedule/time_capsule/pixel_pet 等） */
    onNavigate: (String) -> Unit = {},
) {
    val colors = StradustTheme.colors
    // 多角色选择对话框状态：选择日记目标角色
    var showPersonaPicker by remember { mutableStateOf(false) }
    // 侧边栏展开状态
    var sidebarExpanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // ===== 第 1 层：壁纸背景 =====
        if (!wallpaperPath.isNullOrBlank()) {
            AsyncImage(
                model = wallpaperPath,
                contentDescription = "背景壁纸",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            // 主题色半透明遮罩，保证上层 UI 可读性
            val overlayGradient = remember(colors) {
                Brush.verticalGradient(
                    colors = listOf(
                        colors.background.copy(alpha = 0.75f),
                        colors.background.copy(alpha = 0.85f),
                    ),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(overlayGradient),
            )
        } else {
            // 无壁纸时使用主题背景色
            Box(modifier = Modifier.fillMaxSize().background(colors.background))
        }

        // ===== 第 2 层：主内容 =====
        Column(modifier = Modifier.fillMaxSize()) {
            // ===== 顶部栏：汉堡菜单 + 标题 + 设置按钮 =====
            StradustTopBar(
                title = "星尘",
                subtitle = "今天想和谁聊聊？",
                actions = {
                    IconButton(onClick = { sidebarExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "功能入口",
                            tint = colors.textPrimary,
                        )
                    }
                    IconButton(onClick = { onNavigate("settings") }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "设置",
                            tint = colors.textPrimary,
                        )
                    }
                },
            )

            // ===== 角色列表 LazyColumn =====
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ===== 角色列表标题 =====
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "我的伙伴",
                            color = colors.textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(
                            modifier = Modifier.clickable(role = Role.Button) { onViewAllPersonas() },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "查看全部",
                                color = colors.primary,
                                fontSize = 14.sp,
                            )
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                itemsIndexed(
                    items = personas,
                    key = { _, it -> it.id },
                ) { _, persona ->
                    // 稳定化 onClick lambda，以 persona.id 为 key 避免不必要的重建
                    val onClick = remember(persona.id) {
                        { onPersonaClick(persona.id) }
                    }
                    PersonaCardItem(
                        persona = persona,
                        onClick = onClick,
                    )
                }

                // 空状态提示
                if (personas.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 64.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "空状态提示",
                                    tint = colors.textMuted,
                                    modifier = Modifier.size(48.dp),
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "还没有伙伴，点击右下角添加",
                                    color = colors.textSecondary,
                                    fontSize = 14.sp,
                                )
                            }
                        }
                    }
                }

                // 底部留白，避免被 FAB 遮挡
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }

        // ===== 第 3 层：可收缩侧边栏（功能入口） =====
        // 稳定化 onFeatureClick lambda，避免每次重组都创建新实例
        val currentOnMomentsClick by rememberUpdatedState(onMomentsClick)
        val currentOnDiaryClick by rememberUpdatedState(onDiaryClick)
        val currentOnAlbumClick by rememberUpdatedState(onAlbumClick)
        val currentOnAchievementClick by rememberUpdatedState(onAchievementClick)
        val currentOnNavigate by rememberUpdatedState(onNavigate)
        val currentOnVirtualWorldClick by rememberUpdatedState(onVirtualWorldClick)
        val currentOnCheckInClick by rememberUpdatedState(onCheckInClick)
        val currentOnGroupChatClick by rememberUpdatedState(onGroupChatClick)

        val stableOnFeatureClick = remember(personas) {
            { entry: FeatureEntry ->
                sidebarExpanded = false
                when (entry.route) {
                    "moments" -> currentOnMomentsClick()
                    "diary" -> {
                        when {
                            personas.isEmpty() -> currentOnDiaryClick("")
                            personas.size == 1 -> currentOnDiaryClick(personas.first().id)
                            else -> showPersonaPicker = true
                        }
                    }
                    "album" -> currentOnAlbumClick()
                    "achievement" -> currentOnAchievementClick()
                    "character_card" -> currentOnNavigate("character_card")
                    "virtual_world" -> currentOnVirtualWorldClick()
                    "check_in" -> currentOnCheckInClick()
                    "group_chat_list" -> currentOnGroupChatClick()
                    else -> currentOnNavigate(entry.route)
                }
            }
        }

        FeatureSidebar(
            expanded = sidebarExpanded,
            onDismiss = { sidebarExpanded = false },
            onFeatureClick = stableOnFeatureClick,
        )

        // ===== FAB: 创建新角色 =====
        FloatingActionButton(
            onClick = onAddPersona,
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "创建新角色",
            )
        }

        // ===== 多角色选择对话框（日记入口） =====
        if (showPersonaPicker) {
            AlertDialog(
                onDismissRequest = { showPersonaPicker = false },
                title = { Text("选择角色写日记") },
                text = {
                    Column {
                        personas.forEach { persona ->
                            Text(
                                text = persona.name,
                                color = colors.textPrimary,
                                fontSize = 15.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onDiaryClick(persona.id)
                                        showPersonaPicker = false
                                    }
                                    .padding(vertical = 12.dp),
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPersonaPicker = false }) { Text("取消") }
                },
            )
        }
    }
}

/**
 * 可收缩的功能入口侧边栏
 *
 * 从左侧滑出的浮动面板，包含所有功能入口（纵向列表）。
 * 展开时覆盖在主内容之上，带半透明遮罩；点击遮罩或功能项后自动收起。
 *
 * @param expanded 是否展开
 * @param onDismiss 关闭回调（点击遮罩时触发）
 * @param onFeatureClick 点击功能项回调
 */
@Composable
private fun FeatureSidebar(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onFeatureClick: (FeatureEntry) -> Unit,
) {
    val colors = StradustTheme.colors

    // 整体容器：仅展开时存在
    AnimatedVisibility(
        visible = expanded,
        enter = fadeIn(animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(200)),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 半透明遮罩：点击关闭侧边栏
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.scrim)
                    .clickable { onDismiss() },
            )

            // 侧边栏面板：从左侧滑入（独立 AnimatedVisibility 控制滑动动画）
            AnimatedVisibility(
                visible = expanded,
                enter = slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(280),
                ),
                exit = slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(240),
                ),
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(280.dp),
                    color = colors.surface.copy(alpha = 0.97f),
                    tonalElevation = 8.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 16.dp),
                    ) {
                        // 侧边栏标题栏
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "功能入口",
                                color = colors.textPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "关闭",
                                    tint = colors.textSecondary,
                                )
                            }
                        }

                        // 功能入口分组纵向列表
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            featureGroups.forEachIndexed { groupIndex, group ->
                                // 组之间分隔线（非首组）
                                if (groupIndex > 0) {
                                    item(key = "divider_$groupIndex") {
                                        HorizontalDivider(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 6.dp),
                                            color = colors.textMuted.copy(alpha = 0.2f),
                                            thickness = 1.dp,
                                        )
                                    }
                                }

                                // 组标题
                                item(key = "group_title_${group.title}") {
                                    Text(
                                        text = group.title,
                                        color = colors.textMuted,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                                    )
                                }

                                // 组内功能项
                                itemsIndexed(
                                    items = group.features,
                                    key = { _, entry -> entry.route },
                                ) { _, entry ->
                                    SidebarFeatureItem(
                                        entry = entry,
                                        onClick = { onFeatureClick(entry) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 侧边栏中的单个功能项
 *
 * 横向布局：图标 + 标签，点击触发回调
 */
@Composable
private fun SidebarFeatureItem(
    entry: FeatureEntry,
    onClick: () -> Unit,
) {
    val colors = StradustTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = colors.primary.copy(alpha = 0.12f),
            modifier = Modifier.size(36.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = entry.icon,
                    contentDescription = entry.label,
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = entry.label,
            color = colors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * 单个角色卡片项
 *
 * 显示角色头像、名称、描述、最后聊天时间、消息数和好感度等级
 */
@Composable
private fun PersonaCardItem(
    persona: PersonaCard,
    onClick: () -> Unit,
) {
    val colors = StradustTheme.colors

    StradustCard(
        onClick = onClick,
        cornerRadius = 16.dp,
        elevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 头像 (48dp 圆形)
            if (!persona.avatarPath.isNullOrBlank()) {
                AsyncImage(
                    model = persona.avatarPath,
                    contentDescription = "${persona.name}的头像",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                // 默认头像：圆形背景 + 人物图标
                Surface(
                    shape = CircleShape,
                    color = colors.primaryContainer,
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "默认头像",
                            tint = colors.onPrimaryContainer,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 角色信息列
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = persona.name,
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = persona.description.ifBlank { "暂无简介" },
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(6.dp))

                // 底部信息行：最后聊天时间 + 消息数 + 好感度
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = persona.lastChatTime,
                        color = colors.textMuted,
                        fontSize = 11.sp,
                    )

                    // 消息数 Badge（缩写大数值）
                    val msgCountText = if (persona.messageCount >= 10000) {
                        "${persona.messageCount / 1000}k"
                    } else if (persona.messageCount >= 1000) {
                        "${(persona.messageCount / 100).toDouble() / 10}k"
                    } else {
                        "${persona.messageCount}"
                    }
                    Badge(
                        containerColor = colors.primaryContainer.copy(alpha = 0.5f),
                        contentColor = colors.primary,
                    ) {
                        Text(
                            text = "$msgCountText 条",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }

                    // 好感度图标 + 等级
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "好感度",
                        tint = colors.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "${persona.affectionLevel} 级好感",
                        color = colors.textMuted,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}
