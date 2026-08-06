package com.aicompanion.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aicompanion.R
import com.aicompanion.milestone.Milestone
import com.aicompanion.milestone.MilestoneManager
import com.aicompanion.memory.MemorableMomentsManager
import com.aicompanion.memory.ScoredMemory
import com.aicompanion.models.Achievement
import com.aicompanion.theme.StradustTheme
import com.aicompanion.ui.components.StradustCard
import com.aicompanion.ui.components.StradustButton
import com.aicompanion.ui.components.StradustTopBar
import com.aicompanion.ui.components.WallpaperBackground
import com.aicompanion.ui.home.PersonaCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ProfileMenuItem(
    val icon: ImageVector,
    val name: String,
    val onClick: () -> Unit,
)

/** 个人中心页面所需的真实用户数据 */
data class ProfileData(
    val userName: String = "星辰旅人",
    val userId: String = "STR_20240601",
    val signature: String = "与星尘同行，每一天都是冒险",
    val avatarPath: String = "",
    val userAvatarPath: String = "",
    val affectionLevel: Int = 1,
    val affectionExp: Int = 0,
    val affectionMaxExp: Int = 1000,
    val chatDays: Int = 0,
    val diaryCount: Int = 0,
    val checkInDays: Int = 0,
    val onlineHours: Int = 0,
    val daysTogether: Int = 0,
    // 社交账号 / 反馈渠道（迁移自旧版 SettingsActivity footer）
    val bilibiliUid: String = "1523985433",
    val douyinId: String = "31991565756",
)

@Composable
fun ProfileScreen(
    profileData: ProfileData = ProfileData(),
    /** AI角色列表（用于横向选择器） */
    personas: List<PersonaCard> = emptyList(),
    /** 当前选中的角色ID */
    selectedPersonaId: String = "",
    /** 切换角色回调 */
    onPersonaSelected: (String) -> Unit = {},
    /** 成就列表 */
    achievements: List<Achievement> = emptyList(),
    /** AI头像路径 */
    aiAvatarPath: String? = null,
    onSettingsClick: () -> Unit = {},
    onMemoryPoolClick: () -> Unit = {},
    onChatHistoryClick: () -> Unit = {},
    onHelpClick: () -> Unit = {},
    onAboutClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    versionName: String = "1.0.0",
    /** 回调：返回上一页 */
    onBackClick: (() -> Unit)? = null,
    /** 回调：更换 AI 头像（启动相册选择器） */
    onChangeAiAvatar: () -> Unit = {},
    /** 回调：更换用户头像（启动相册选择器） */
    onChangeUserAvatar: () -> Unit = {},
    /** 回调：导航到指定路由（昵称管理/角色统计/时光胶囊/里程碑/日历等） */
    onNavigate: (String) -> Unit = {},
    /** 数据刷新触发器（传入 dataVersion，里程碑和铭记时刻等本地数据会随之刷新） */
    refreshTick: Int = 0,
    /** 壁纸图片路径（本地文件路径或URI） */
    wallpaperPath: String? = null,
) {
    val affinityLevel = profileData.affectionLevel
    val affinityExp = profileData.affectionExp
    val affinityMaxExp = profileData.affectionMaxExp
    val affinityProgress = if (affinityMaxExp > 0) affinityExp.toFloat() / affinityMaxExp.toFloat() else 0f

    val primaryColor = StradustTheme.colors.primary
    val tertiaryColor = StradustTheme.colors.tertiary
    val secondaryColor = StradustTheme.colors.secondary
    val errorColor = StradustTheme.colors.error

    // 帮助/关于弹窗内部状态
    var showHelpDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }

    // 从本地加载里程碑与铭记时刻数据（按角色隔离）
    val context = LocalContext.current
    val milestoneManager = remember(selectedPersonaId) { MilestoneManager(context, selectedPersonaId) }
    val momentsManager = remember(selectedPersonaId) { MemorableMomentsManager(context, selectedPersonaId) }
    val milestones = remember(refreshTick) { milestoneManager.loadMilestones().sortedByDescending { it.timestamp } }
    val memorableMoments = remember(refreshTick) { momentsManager.getAll().sortedByDescending { it.score } }

    // 成就数据：只取已解锁的前几个用于展示
    val unlockedAchievements = remember(achievements) {
        achievements.filter { it.unlocked }
    }

    val stats = remember(profileData) {
        listOf(
            StatItem("聊天天数", "${profileData.chatDays}", Icons.Default.ChatBubble, primaryColor),
            StatItem("日记篇数", "${profileData.diaryCount}", Icons.Default.EditNote, tertiaryColor),
            StatItem("签到天数", "${profileData.checkInDays}", Icons.Default.Timer, secondaryColor),
            StatItem("相处天数", "${profileData.daysTogether}", Icons.Default.Favorite, errorColor),
        )
    }

    val menuItems = remember(onMemoryPoolClick, onChatHistoryClick, onHelpClick, onAboutClick, onNavigate) {
        listOf(
            ProfileMenuItem(Icons.Default.Memory, "我的记忆池", onMemoryPoolClick),
            ProfileMenuItem(Icons.Default.History, "聊天记录", onChatHistoryClick),
            ProfileMenuItem(Icons.Default.EditNote, "昵称管理") { onNavigate("nickname") },
            ProfileMenuItem(Icons.Default.HourglassEmpty, "时光胶囊") { onNavigate("time_capsule") },
            ProfileMenuItem(Icons.Default.CalendarToday, "日历") { onNavigate("calendar") },
            ProfileMenuItem(Icons.AutoMirrored.Filled.Help, "帮助与反馈", { showHelpDialog = true }),
            ProfileMenuItem(Icons.Default.Info, "关于我们", { showAboutDialog = true }),
        )
    }

    WallpaperBackground(wallpaperPath = wallpaperPath) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item { StradustTopBar(title = "我的", onSettingsClick = onSettingsClick, onBackClick = onBackClick) }

            // ===== 顶部区域：AI角色头像 + 名称 + 角色选择器 =====
            item {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                        + slideInVertically(
                            initialOffsetY = { -30 },
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        ),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // AI角色大头像（96dp）+ 可点击换头像
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(StradustTheme.colors.themeGradient, CircleShape)
                                .padding(3.dp)
                                .clickable { onChangeAiAvatar() },
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(StradustTheme.colors.surface, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (!aiAvatarPath.isNullOrBlank()) {
                                    AsyncImage(
                                        model = aiAvatarPath,
                                        contentDescription = "AI 头像",
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                        contentScale = ContentScale.Crop,
                                    )
                                } else {
                                    Icon(Icons.Default.Person, "AI头像",
                                        modifier = Modifier.size(48.dp), tint = StradustTheme.colors.textMuted)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        // AI名称
                        val currentPersona = personas.find { it.id == selectedPersonaId }
                        val aiName = currentPersona?.name ?: profileData.userName
                        Text(
                            text = aiName,
                            color = StradustTheme.colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        )
                        Spacer(Modifier.height(12.dp))

                        // 多AI角色横向选择器
                        if (personas.size > 1) {
                            PersonaSelectorRow(
                                personas = personas,
                                selectedPersonaId = selectedPersonaId,
                                onPersonaSelected = onPersonaSelected,
                            )
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }
            }

            // ===== 好感度卡片 =====
            item {
                StradustCard {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Favorite, "好感度",
                            tint = StradustTheme.colors.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(text = "Lv.$affinityLevel", color = StradustTheme.colors.primary,
                            fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(text = "好感度", color = StradustTheme.colors.textSecondary, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Column {
                        LinearProgressIndicator(progress = { affinityProgress },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = StradustTheme.colors.primary.copy(alpha = 0.3f),
                            trackColor = StradustTheme.colors.surfaceContainerHigh)
                        Spacer(Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "$affinityExp / $affinityMaxExp EXP",
                                color = StradustTheme.colors.textMuted, fontSize = 11.sp)
                            Text(text = "下一级需 ${affinityMaxExp - affinityExp} EXP",
                                color = StradustTheme.colors.textMuted, fontSize = 11.sp)
                        }
                    }
                }
            }

            // ===== 数据统计卡片（2x2网格） =====
            item {
                Spacer(Modifier.height(16.dp))
                StradustCard {
                    Text(text = "数据统计", color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    stats.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            rowItems.forEach { stat ->
                                Box(modifier = Modifier.weight(1f)) {
                                    StatCard(stat = stat)
                                }
                            }
                            if (rowItems.size < 2) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                        if (rowItems != stats.chunked(2).last()) {
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }

            // ===== 成就卡片 =====
            item {
                Spacer(Modifier.height(16.dp))
                StradustCard(
                    onClick = { onNavigate("achievement") },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, null, tint = StradustTheme.colors.tertiary,
                                modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(text = "成就", color = StradustTheme.colors.textPrimary,
                                fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "查看全部", color = StradustTheme.colors.textMuted, fontSize = 12.sp)
                            Icon(Icons.Default.ChevronRight, null,
                                modifier = Modifier.size(16.dp), tint = StradustTheme.colors.textDisabled)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (unlockedAchievements.isEmpty()) {
                        EmptyHint(text = "暂无已解锁的成就")
                    } else {
                        unlockedAchievements.take(3).forEachIndexed { index, achievement ->
                            AchievementPreviewRow(achievement = achievement)
                            if (index < unlockedAchievements.take(3).lastIndex) {
                                HorizontalDivider(
                                    color = StradustTheme.colors.outlineVariant.copy(alpha = 0.3f),
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
            }

            // ===== 里程碑卡片 =====
            item {
                Spacer(Modifier.height(16.dp))
                StradustCard(
                    onClick = { onNavigate("milestone") },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Flag, null, tint = StradustTheme.colors.primary,
                                modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(text = "里程碑", color = StradustTheme.colors.textPrimary,
                                fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "查看全部", color = StradustTheme.colors.textMuted, fontSize = 12.sp)
                            Icon(Icons.Default.ChevronRight, null,
                                modifier = Modifier.size(16.dp), tint = StradustTheme.colors.textDisabled)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (milestones.isEmpty()) {
                        EmptyHint(text = "暂无里程碑事件")
                    } else {
                        milestones.take(3).forEachIndexed { index, milestone ->
                            MilestonePreviewRow(milestone = milestone)
                            if (index < milestones.take(3).lastIndex) {
                                HorizontalDivider(
                                    color = StradustTheme.colors.outlineVariant.copy(alpha = 0.3f),
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
            }

            // ===== 铭记时刻卡片 =====
            item {
                Spacer(Modifier.height(16.dp))
                StradustCard(
                    onClick = { onNavigate("memorable_moments") },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, null, tint = StradustTheme.colors.secondary,
                                modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(text = "铭记时刻", color = StradustTheme.colors.textPrimary,
                                fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "查看全部", color = StradustTheme.colors.textMuted, fontSize = 12.sp)
                            Icon(Icons.Default.ChevronRight, null,
                                modifier = Modifier.size(16.dp), tint = StradustTheme.colors.textDisabled)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (memorableMoments.isEmpty()) {
                        EmptyHint(text = "暂无铭记时刻")
                    } else {
                        memorableMoments.take(3).forEachIndexed { index, moment ->
                            MomentPreviewRow(moment = moment)
                            if (index < memorableMoments.take(3).lastIndex) {
                                HorizontalDivider(
                                    color = StradustTheme.colors.outlineVariant.copy(alpha = 0.3f),
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
            }

            // ===== 角色回忆卡片 =====
            item {
                Spacer(Modifier.height(16.dp))
                StradustCard {
                    Text(
                        text = "角色回忆",
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "查看角色的过往经历与成长记录",
                        color = StradustTheme.colors.textMuted,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StradustButton(
                            text = "难忘时刻",
                            onClick = { onNavigate("memorable_moments") },
                            modifier = Modifier.weight(1f),
                        )
                        StradustButton(
                            text = "里程碑",
                            onClick = { onNavigate("milestone") },
                            modifier = Modifier.weight(1f),
                        )
                        StradustButton(
                            text = "成长阶段",
                            onClick = { onNavigate("growth") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // ===== 菜单列表 =====
            item {
                Spacer(Modifier.height(16.dp))
                StradustCard {
                    menuItems.forEachIndexed { index, item ->
                        ProfileMenuRow(item = item)
                        if (index < menuItems.lastIndex) {
                            HorizontalDivider(color = StradustTheme.colors.outlineVariant.copy(alpha = 0.3f),
                                modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            }

            // ===== 退出登录按钮 =====
            item {
                Spacer(Modifier.height(20.dp))
                OutlinedButton(
                    onClick = { showLogoutConfirmDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(40.dp),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.5.dp, errorColor),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = errorColor,
                    ),
                ) {
                    Text(text = "退出登录", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }

        // ===== 帮助与反馈弹窗 =====
        if (showHelpDialog) {
            AlertDialog(
                onDismissRequest = { showHelpDialog = false },
                title = { Text("帮助与反馈", fontWeight = FontWeight.SemiBold) },
                text = {
                    Column {
                        Text("常见问题：", color = StradustTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text("1. AI 不回复？请检查设置中的 API 地址和 API Key 是否配置正确。",
                            color = StradustTheme.colors.textSecondary, fontSize = 13.sp)
                        Text("2. 语音不可用？请确认已授予麦克风权限，并在设置中开启 ASR。",
                            color = StradustTheme.colors.textSecondary, fontSize = 13.sp)
                        Text("3. 头像不显示？请确认已在角色档案中设置头像路径。",
                            color = StradustTheme.colors.textSecondary, fontSize = 13.sp)
                        Text("4. 群聊不回复？请确保群内有成员角色且 API 配置正确。",
                            color = StradustTheme.colors.textSecondary, fontSize = 13.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showHelpDialog = false }) { Text("我知道了") }
                },
            )
        }

        // ===== 关于我们弹窗 =====
        if (showAboutDialog) {
            AlertDialog(
                onDismissRequest = { showAboutDialog = false },
                title = { Text("关于星尘", fontWeight = FontWeight.SemiBold) },
                text = {
                    Column {
                        Text("星尘 Stradust",
                            color = StradustTheme.colors.textPrimary,
                            fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text("版本：v$versionName",
                            color = StradustTheme.colors.textSecondary, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("一款基于 AI 的伴侣应用，陪伴你的每一天。",
                            color = StradustTheme.colors.textSecondary, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("功能特色：",
                            color = StradustTheme.colors.textPrimary,
                            fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("• 多角色 AI 伴侣\n• 群聊互动\n• 虚拟世界\n• 日记与相册\n• 通话与语音",
                            color = StradustTheme.colors.textSecondary, fontSize = 12.sp)

                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(
                            color = StradustTheme.colors.outlineVariant.copy(alpha = 0.3f),
                            thickness = 0.5.dp,
                        )
                        Spacer(Modifier.height(12.dp))

                        Text("反馈与关注",
                            color = StradustTheme.colors.textPrimary,
                            fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("📺 B站 UID：${profileData.bilibiliUid}",
                            color = StradustTheme.colors.textSecondary, fontSize = 12.sp)
                        Text("🎵 抖音 ID：${profileData.douyinId}",
                            color = StradustTheme.colors.textSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("提示：B站/抖音 ID 可长按复制，或在浏览器搜索关注作者",
                            color = StradustTheme.colors.textMuted, fontSize = 11.sp)

                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(
                            color = StradustTheme.colors.outlineVariant.copy(alpha = 0.3f),
                            thickness = 0.5.dp,
                        )
                        Spacer(Modifier.height(12.dp))

                        // 支持作者收款码
                        Text("支持作者",
                            color = StradustTheme.colors.textPrimary,
                            fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally))
                        Spacer(Modifier.height(4.dp))
                        Text("作者穷的只能送外卖了给点米资助一下吧",
                            color = StradustTheme.colors.textSecondary, fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.CenterHorizontally))
                        Spacer(Modifier.height(8.dp))
                        Image(
                            painter = painterResource(id = R.drawable.donate_qr),
                            contentDescription = "支持作者收款码",
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .size(180.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit,
                        )

                        Spacer(Modifier.height(6.dp))
                        Text("© 2024-2025 Stradust Team",
                            color = StradustTheme.colors.textMuted, fontSize = 11.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAboutDialog = false }) { Text("关闭") }
                },
            )
        }

        // ===== 退出登录确认弹窗 =====
        if (showLogoutConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutConfirmDialog = false },
                title = { Text("退出登录", fontWeight = FontWeight.SemiBold) },
                text = { Text("确定要退出登录吗？当前会话将被清除") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showLogoutConfirmDialog = false
                            onLogoutClick()
                        },
                    ) { Text("确定退出", color = errorColor) }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutConfirmDialog = false }) { Text("取消") }
                },
            )
        }
    }
}

// ===== 子组件 =====

/** AI角色横向选择器 */
@Composable
private fun PersonaSelectorRow(
    personas: List<PersonaCard>,
    selectedPersonaId: String,
    onPersonaSelected: (String) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        items(items = personas, key = { it.id }) { persona ->
            val isSelected = persona.id == selectedPersonaId
            val borderColor by animateColorAsState(
                targetValue = if (isSelected) StradustTheme.colors.primary else StradustTheme.colors.surfaceContainerHigh,
                animationSpec = tween(300), label = "personaBorder",
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) StradustTheme.colors.primary else StradustTheme.colors.textMuted,
                animationSpec = tween(300), label = "personaText",
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable(role = Role.Button) { onPersonaSelected(persona.id) },
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .then(
                            if (isSelected) Modifier.background(StradustTheme.colors.themeGradient, CircleShape)
                            else Modifier.background(StradustTheme.colors.surfaceContainerHigh, CircleShape)
                        )
                        .padding(if (isSelected) 3.dp else 0.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(StradustTheme.colors.surface, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!persona.avatarPath.isNullOrBlank()) {
                            AsyncImage(
                                model = persona.avatarPath,
                                contentDescription = persona.name,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Icon(Icons.Default.Person, persona.name,
                                modifier = Modifier.size(24.dp), tint = StradustTheme.colors.textMuted)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = persona.name,
                    color = textColor,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

/** 统计卡片 */
@Composable
private fun StatCard(stat: StatItem) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(stat.color.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Column {
            Icon(stat.icon, null, tint = stat.color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(8.dp))
            Text(text = stat.value, color = StradustTheme.colors.textPrimary,
                fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(text = stat.label, color = StradustTheme.colors.textMuted, fontSize = 11.sp)
        }
    }
}

/** 成就预览行 */
@Composable
private fun AchievementPreviewRow(achievement: Achievement) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Star, null, tint = StradustTheme.colors.tertiary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = achievement.title,
                color = StradustTheme.colors.textPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = achievement.description,
                color = StradustTheme.colors.textMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(14.dp),
            tint = StradustTheme.colors.textDisabled)
    }
}

/** 里程碑预览行 */
@Composable
private fun MilestonePreviewRow(milestone: Milestone) {
    val dateFormatter = remember { SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Flag, null, tint = StradustTheme.colors.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = milestone.title,
                color = StradustTheme.colors.textPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = dateFormatter.format(Date(milestone.timestamp)),
                color = StradustTheme.colors.textMuted,
                fontSize = 11.sp,
            )
        }
        Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(14.dp),
            tint = StradustTheme.colors.textDisabled)
    }
}

/** 铭记时刻预览行 */
@Composable
private fun MomentPreviewRow(moment: ScoredMemory) {
    val dateFormatter = remember { SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.AutoAwesome, null, tint = StradustTheme.colors.secondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = moment.content,
                color = StradustTheme.colors.textPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = dateFormatter.format(Date(moment.timestamp)),
                color = StradustTheme.colors.textMuted,
                fontSize = 11.sp,
            )
        }
        Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(14.dp),
            tint = StradustTheme.colors.textDisabled)
    }
}

/** 空状态提示 */
@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        color = StradustTheme.colors.textMuted,
        fontSize = 13.sp,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
}

/** 菜单行 */
@Composable
private fun ProfileMenuRow(item: ProfileMenuItem) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .height(48.dp)
            .clickable(role = Role.Button, onClick = item.onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(item.icon, null, tint = StradustTheme.colors.textSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text = item.name, color = StradustTheme.colors.textPrimary,
            fontSize = 15.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(18.dp),
            tint = StradustTheme.colors.textDisabled)
    }
}

private data class StatItem(val label: String, val value: String, val icon: ImageVector, val color: Color)
