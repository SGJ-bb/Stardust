package com.aicompanion.ui.achievement

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aicompanion.theme.StradustTheme
import com.aicompanion.ui.animations.pressedScale
import com.aicompanion.ui.components.StradustCard
import com.aicompanion.ui.components.StradustTopBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 日期格式化器（主线程使用，避免在循环/remember 中重复创建 SimpleDateFormat） */
private val ACHIEVEMENT_DATE_FMT = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())

/** 成就图标列表 - 按类别分组（避免在函数内重复创建） */
private val CATEGORY_ALL_ICONS = listOf(Icons.Default.AutoAwesome, Icons.Default.Star, Icons.Default.Star)
private val CATEGORY_SOCIAL_ICONS = listOf(Icons.Default.Star, Icons.Default.People, Icons.Default.People)
private val CATEGORY_LEARN_ICONS = listOf(Icons.Default.School, Icons.Default.School, Icons.Default.Favorite)
private val CATEGORY_HEALTH_ICONS = listOf(Icons.Default.Favorite, Icons.Default.Favorite, Icons.Default.AutoAwesome)
private val CATEGORY_SPECIAL_ICONS = listOf(Icons.Default.AutoAwesome, Icons.Default.Star, Icons.Default.Star)

enum class AchievementCategory(val label: String) {
    ALL("全部"),
    SOCIAL("社交"),
    LEARN("学习"),
    HEALTH("健康"),
    SPECIAL("特殊"),
}

/**
 * 成就数据
 * @param iconColorIndex 主题色索引，通过 [themeColorByIndex] 映射到实际主题色
 *                      （0=primary, 1=tertiary, 2=secondary, 3=error，循环使用）
 */
data class Achievement(
    val id: Int,
    val name: String,
    val description: String,
    val icon: ImageVector,
    val iconColorIndex: Int,
    val category: AchievementCategory,
    val isUnlocked: Boolean,
    val progress: Float,
    val progressText: String,
    val unlockedAt: String? = null,
)

/** 将索引映射到主题色（循环），避免硬编码 Color(0xFF...) */
@Composable
private fun themeColorByIndex(index: Int): Color {
    val themeColors = StradustTheme.colors
    val colors = remember(themeColors) {
        listOf(
            themeColors.primary,
            themeColors.tertiary,
            themeColors.secondary,
            themeColors.error,
        )
    }
    return colors[index % colors.size]
}

/**
 * 将模型层 Achievement (com.aicompanion.models.Achievement) 映射为 UI 层 Achievement
 *
 * 字段映射规则：
 * - id: String → Int (hashCode)
 * - title → name
 * - description → description
 * - icon(emoji) + category → ImageVector + iconColorIndex（按类别分配图标和颜色）
 * - category(String) → AchievementCategory（chat/social→SOCIAL, diary/memory/learn→LEARN, checkin/health→HEALTH, 其余→SPECIAL）
 * - unlocked → isUnlocked
 * - progress / unlockCondition → progress(Float) + progressText
 * - unlockedAt(Long) → unlockedAt(String, 格式化日期)
 */
private fun mapToUiAchievement(
    model: com.aicompanion.models.Achievement,
    index: Int,
): Achievement {
    val category = when (model.category) {
        "chat", "feedback" -> AchievementCategory.SOCIAL
        "diary", "memory", "affection" -> AchievementCategory.LEARN
        "checkin", "pomodoro" -> AchievementCategory.HEALTH
        else -> AchievementCategory.SPECIAL
    }

    // 按类别分配图标：每个类别内循环使用代表性图标
    val (icon, colorIndex) = when (category) {
        AchievementCategory.ALL -> {
            // ALL 是筛选视图，单个成就不会是 ALL，兜底到 SPECIAL
            val idx = index % CATEGORY_ALL_ICONS.size
            Pair(CATEGORY_ALL_ICONS[idx], idx % 2)
        }
        AchievementCategory.SOCIAL -> {
            val idx = index % CATEGORY_SOCIAL_ICONS.size
            Pair(CATEGORY_SOCIAL_ICONS[idx], idx)
        }
        AchievementCategory.LEARN -> {
            val idx = index % CATEGORY_LEARN_ICONS.size
            Pair(CATEGORY_LEARN_ICONS[idx], idx + 2)
        }
        AchievementCategory.HEALTH -> {
            val idx = index % CATEGORY_HEALTH_ICONS.size
            Pair(CATEGORY_HEALTH_ICONS[idx], idx % 2 + 3)
        }
        AchievementCategory.SPECIAL -> {
            val idx = index % CATEGORY_SPECIAL_ICONS.size
            Pair(CATEGORY_SPECIAL_ICONS[idx], idx % 2)
        }
    }

    val progressFloat = if (model.unlockCondition > 0) {
        (model.progress.toFloat() / model.unlockCondition.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val progressText = "${model.progress}/${model.unlockCondition}"

    val unlockedAtStr = if (model.unlocked && model.unlockedAt > 0) {
        try {
            ACHIEVEMENT_DATE_FMT.format(Date(model.unlockedAt))
        } catch (_: Exception) {
            null
        }
    } else null

    return Achievement(
        id = model.id.hashCode(),
        name = model.title,
        description = model.description,
        icon = icon,
        iconColorIndex = colorIndex,
        category = category,
        isUnlocked = model.unlocked,
        progress = progressFloat,
        progressText = progressText,
        unlockedAt = unlockedAtStr,
    )
}

@Composable
fun AchievementScreen(
    /** 外部传入的真实成就数据（来自 AchievementManager），为空时使用默认演示数据保持向后兼容 */
    achievementModels: List<com.aicompanion.models.Achievement>? = null,
    onBackClick: (() -> Unit)? = null,
) {
    var selectedCategory by rememberSaveable { mutableStateOf(AchievementCategory.ALL) }
    var detailAchievement by remember { mutableStateOf<Achievement?>(null) }

    val achievements = remember(achievementModels) {
        if (!achievementModels.isNullOrEmpty()) {
            // 使用真实数据：模型层 → UI 层映射
            achievementModels.mapIndexed { index, model -> mapToUiAchievement(model, index) }
        } else {
            // 向后兼容：使用默认演示数据
            listOf(
                Achievement(id = 1, name = "初次相遇", description = "与 AI 进行第一次对话",
                    icon = Icons.Default.Star, iconColorIndex = 0, category = AchievementCategory.SOCIAL,
                    isUnlocked = true, progress = 1f, progressText = "1/1", unlockedAt = "2024.06.10"),
                Achievement(id = 2, name = "话痨达人", description = "累计聊天 100 条消息",
                    icon = Icons.Default.People, iconColorIndex = 1, category = AchievementCategory.SOCIAL,
                    isUnlocked = true, progress = 1f, progressText = "100/100", unlockedAt = "2024.06.11"),
                Achievement(id = 3, name = "社交蝴蝶", description = "连续 7 天与 AI 互动",
                    icon = Icons.Default.People, iconColorIndex = 1, category = AchievementCategory.SOCIAL,
                    isUnlocked = true, progress = 1f, progressText = "7/7", unlockedAt = "2024.06.12"),
                Achievement(id = 4, name = "日记新手", description = "撰写第一篇日记",
                    icon = Icons.Default.School, iconColorIndex = 2, category = AchievementCategory.LEARN,
                    isUnlocked = true, progress = 1f, progressText = "1/1", unlockedAt = "2024.06.13"),
                Achievement(id = 5, name = "笔耕不辍", description = "累计撰写 30 篇日记",
                    icon = Icons.Default.School, iconColorIndex = 0, category = AchievementCategory.LEARN,
                    isUnlocked = false, progress = 0.37f, progressText = "11/30"),
                Achievement(id = 6, name = "早起鸟儿", description = "在早上 6-8 点间签到 7 次",
                    icon = Icons.Default.Favorite, iconColorIndex = 3, category = AchievementCategory.HEALTH,
                    isUnlocked = false, progress = 0.45f, progressText = "3/7"),
                Achievement(id = 7, name = "夜猫子", description = "在晚上 10 点后仍在线 10 次",
                    icon = Icons.Default.Favorite, iconColorIndex = 1, category = AchievementCategory.HEALTH,
                    isUnlocked = false, progress = 0.53f, progressText = "5.3/10"),
                Achievement(id = 8, name = "全勤王", description = "一个月内每天签到",
                    icon = Icons.Default.AutoAwesome, iconColorIndex = 2, category = AchievementCategory.HEALTH,
                    isUnlocked = false, progress = 0.61f, progressText = "18.3/30"),
                Achievement(id = 9, name = "收藏家", description = "解锁所有主题",
                    icon = Icons.Default.AutoAwesome, iconColorIndex = 3, category = AchievementCategory.SPECIAL,
                    isUnlocked = false, progress = 0.69f, progressText = "8.3/12"),
                Achievement(id = 10, name = "忠实伙伴", description = "使用 App 满 100 天",
                    icon = Icons.Default.Star, iconColorIndex = 0, category = AchievementCategory.SPECIAL,
                    isUnlocked = false, progress = 0.77f, progressText = "77/100"),
            )
        }
    }

    val filteredAchievements = remember(selectedCategory) {
        if (selectedCategory == AchievementCategory.ALL) achievements
        else achievements.filter { it.category == selectedCategory }
    }

    val unlockedCount = achievements.count { it.isUnlocked }
    val totalCount = achievements.size

    Box(
        modifier = Modifier.fillMaxSize().background(StradustTheme.colors.background),
    ) {
        Column {
            StradustTopBar(title = "成就", onBackClick = onBackClick)

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            ) {
                // 成就统计卡片：左大数字 + 右圆形进度(64dp)
                item {
                    Spacer(Modifier.height(12.dp))
                    StradustCard {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 左侧："成就进度" + 大数字
                            Column {
                                Text(text = "成就进度", color = StradustTheme.colors.textSecondary, fontSize = 13.sp)
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(text = "$unlockedCount", color = StradustTheme.colors.primary,
                                        fontWeight = FontWeight.Bold, fontSize = 22.sp)
                                    Text(text = " / $totalCount", color = StradustTheme.colors.textMuted, fontSize = 14.sp)
                                }
                            }
                            // 右侧：圆形进度显示 (64dp)
                            CircularProgressDisplay(
                                progress = unlockedCount.toFloat() / totalCount.coerceAtLeast(1),
                                modifier = Modifier.size(64.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // 分类筛选 Tab — FilterChip 横滚
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AchievementCategory.entries.forEach { cat ->
                            val isSelected = cat == selectedCategory
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedCategory = cat },
                                label = { Text(text = cat.label, fontSize = 13.sp) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = StradustTheme.colors.primary.copy(alpha = 0.15f),
                                    selectedLabelColor = StradustTheme.colors.primary,
                                    selectedLeadingIconColor = StradustTheme.colors.primary,
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // 成就列表 — items with 动效
                itemsIndexed(items = filteredAchievements, key = { _, it -> it.id }) { index, achievement ->
                    AchievementItemCard(achievement = achievement, onClick = { detailAchievement = achievement })
                    Spacer(Modifier.height(8.dp))
                }

                // 分类筛选后空状态
                if (filteredAchievements.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = StradustTheme.colors.textMuted,
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "该分类暂无成就",
                                color = StradustTheme.colors.textMuted,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }

                // 底部留白
                item { Spacer(Modifier.height(32.dp)) }
            }
        }

        // 详情弹窗
        if (detailAchievement != null) {
            AchievementDetailDialog(achievement = detailAchievement!!, onDismiss = { detailAchievement = null })
        }
    }
}

/**
 * 圆形进度显示组件：百分比文本 + 细条进度
 */
@Composable
private fun CircularProgressDisplay(progress: Float, modifier: Modifier = Modifier) {
    val percentage = (progress * 100).toInt()

    Box(
        modifier = modifier.size(64.dp).background(StradustTheme.colors.surfaceContainerHigh, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "$percentage%", color = StradustTheme.colors.primary,
                fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = StradustTheme.colors.primary,
                trackColor = StradustTheme.colors.surfaceContainerHighest,
            )
        }
    }
}

/** 成就列表项卡片 */
@Composable
private fun AchievementItemCard(achievement: Achievement, onClick: () -> Unit) {
    val iconColor = themeColorByIndex(achievement.iconColorIndex)

    StradustCard(
        modifier = Modifier.pressedScale(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 图标区域：44dp 圆形
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(
                    if (achievement.isUnlocked) iconColor else StradustTheme.colors.surfaceContainerHigh,
                    CircleShape,
                ),
                contentAlignment = Alignment.Center,
            ) {
                if (achievement.isUnlocked) {
                    Icon(imageVector = achievement.icon, contentDescription = null,
                        modifier = Modifier.size(24.dp), tint = StradustTheme.colors.onPrimary)
                } else {
                    Icon(Icons.Default.Lock, contentDescription = "未解锁",
                        modifier = Modifier.size(20.dp), tint = StradustTheme.colors.textDisabled)
                }
            }

            Spacer(Modifier.width(12.dp))

            // 内容区域：name(15sp SemiBold) + desc(12sp muted) + 进度条/解锁时间
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = achievement.name,
                    color = if (achievement.isUnlocked) StradustTheme.colors.textPrimary else StradustTheme.colors.textSecondary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
                Text(
                    text = achievement.description,
                    color = StradustTheme.colors.textMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))

                if (!achievement.isUnlocked) {
                    // 未解锁：LinearProgressIndicator(6dp高, rounded 3dp)
                    LinearProgressIndicator(
                        progress = { achievement.progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = StradustTheme.colors.primary,
                        trackColor = StradustTheme.colors.surfaceContainerHigh,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = achievement.progressText,
                        color = StradustTheme.colors.textMuted,
                        fontSize = 11.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    // 已解锁：Star icon(14dp tertiary) + 解锁时间
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null,
                            modifier = Modifier.size(14.dp), tint = StradustTheme.colors.tertiary)
                        Spacer(Modifier.width(4.dp))
                        Text(text = "解锁于 ${achievement.unlockedAt ?: ""}",
                            color = StradustTheme.colors.textMuted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

/** 成就详情弹窗 — 全宽 Card 圆角 24dp */
@Composable
private fun AchievementDetailDialog(achievement: Achievement, onDismiss: () -> Unit) {
    val iconColor = themeColorByIndex(achievement.iconColorIndex)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        StradustCard(
            modifier = Modifier.padding(32.dp).fillMaxWidth(),
            cornerRadius = 24.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                // 72dp 圆形大图
                Box(
                    modifier = Modifier.size(72.dp).clip(CircleShape).background(
                        if (achievement.isUnlocked) iconColor else StradustTheme.colors.surfaceContainerHigh,
                        CircleShape,
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (achievement.isUnlocked) {
                        Icon(imageVector = achievement.icon, contentDescription = null,
                        modifier = Modifier.size(36.dp), tint = StradustTheme.colors.onPrimary)
                    } else {
                        Icon(Icons.Default.Lock, contentDescription = null,
                            modifier = Modifier.size(28.dp), tint = StradustTheme.colors.textDisabled)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 名称(20sp Bold) + category tag(12sp primary)
                Text(text = achievement.name, color = StradustTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(text = achievement.category.label, color = StradustTheme.colors.primary,
                    fontSize = 12.sp, fontWeight = FontWeight.Medium)

                Spacer(Modifier.height(12.dp))

                // 描述(14sp center)
                Text(text = achievement.description, color = StradustTheme.colors.textSecondary,
                    fontSize = 14.sp, textAlign = TextAlign.Center)

                Spacer(Modifier.height(16.dp))

                HorizontalDivider(color = StradustTheme.colors.outlineVariant)

                Spacer(Modifier.height(12.dp))

                if (!achievement.isUnlocked) {
                    // 进度信息
                    Text(text = "完成进度", color = StradustTheme.colors.textMuted, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { achievement.progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = StradustTheme.colors.primary, trackColor = StradustTheme.colors.surfaceContainerHigh)
                    Spacer(Modifier.height(4.dp))
                    Text(text = "${(achievement.progress * 100).toInt()}%  ${achievement.progressText}",
                        color = StradustTheme.colors.textSecondary, fontSize = 13.sp)
                } else {
                    // 已解锁标识
                    Icon(Icons.Default.Star, contentDescription = null,
                        tint = StradustTheme.colors.tertiary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(text = "已解锁", color = StradustTheme.colors.tertiary,
                        fontWeight = FontWeight.Medium, fontSize = 15.sp)
                }

                Spacer(Modifier.height(20.dp))

                // 关闭按钮
                TextButton(onClick = onDismiss) {
                    Text(text = "关闭", color = StradustTheme.colors.primary)
                }
            }
        }
    }
}
