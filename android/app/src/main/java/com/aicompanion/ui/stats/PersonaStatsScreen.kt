package com.aicompanion.ui.stats

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicompanion.affection.AffectionManager
import com.aicompanion.stats.PersonaStatsManager
import com.aicompanion.theme.StradustTheme
import com.aicompanion.ui.components.StradustCard
import com.aicompanion.ui.components.StradustTopBar

/**
 * 角色统计页面
 *
 * 展示当前角色的聊天统计：概览、情绪分布、活跃时段、趋势。
 *
 * @param personaId 角色 ID
 * @param onBackClick 返回回调
 */
@Composable
fun PersonaStatsScreen(
    personaId: String = "default",
    onBackClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val stats = remember(personaId) { PersonaStatsManager(context, personaId) }
    // AffectionManager：用于读取每日好感度统计（getDailyStats）与当前好感度
    val affectionManager = remember(personaId) { AffectionManager(context, personaId) }
    val dailyStats = remember { affectionManager.getDailyStats() }
    val affectionLevel = remember { affectionManager.affectionLevel }
    val affectionDaysSinceFirst = remember { affectionManager.getDaysSinceFirstUse() }
    val affectionTitle = remember { affectionManager.getAffectionTitle() }

    val totalMessages = stats.totalMessages
    val totalChatDays = stats.totalChatDays
    val currentStreak = stats.currentStreak
    val longestStreak = stats.longestStreak
    val avgPerDay = stats.getAvgMessagesPerDay()
    val topEmotionPair = stats.getTopEmotion()
    val emotionPercentages = stats.getEmotionPercentages()
    val hourDistribution = stats.getHourDistribution()
    val peakHour = stats.getPeakChatHour()
    val moodTrend = stats.getMoodTrend()
    val daysSinceFirst = stats.getDaysSinceFirstChat()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StradustTheme.colors.background),
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { StradustTopBar(title = "角色统计", onBackClick = onBackClick) }

            item { Spacer(Modifier.height(16.dp)) }

            // 概览卡片
            item {
                StradustCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(
                        text = "概览",
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    val tiles = listOf(
                        "总消息数" to "$totalMessages",
                        "聊天天数" to "$totalChatDays",
                        "当前连击" to "$currentStreak 天",
                        "最长连击" to "$longestStreak 天",
                        "日均消息" to "%.1f".format(avgPerDay),
                        "用户/AI" to "${stats.userMessages} / ${stats.aiMessages}",
                    )
                    tiles.chunked(2).forEach { rowItems ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            rowItems.forEach { (label, value) ->
                                StatTile(label = label, value = value, modifier = Modifier.weight(1f))
                            }
                            if (rowItems.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }

            // ===== 每日好感度统计卡片 =====
            // 调用 affectionManager.getDailyStats() 显示今日消息数与好感度变化
            // 注：后端目前仅记录当日统计，7 天历史需后端扩展，此处展示当日数据 + 当前好感度
            item {
                StradustCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = null,
                            tint = StradustTheme.colors.error,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "每日好感度统计",
                            color = StradustTheme.colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            modifier = Modifier.weight(1f),
                        )
                        // 当前好感度等级标签
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(StradustTheme.colors.error.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                text = affectionTitle,
                                color = StradustTheme.colors.error,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    // 当前好感度进度条
                    Text(
                        text = "当前好感度：$affectionLevel / 100",
                        color = StradustTheme.colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(StradustTheme.colors.surfaceContainerLow),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((affectionLevel.coerceIn(0, 100) / 100f))
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(StradustTheme.colors.error),
                        )
                    }
                    Spacer(Modifier.height(12.dp))

                    // 今日统计磁贴：今日消息数 / 今日好感度变化 / 累计互动天数
                    val affectionChangeText = if (dailyStats.affectionChange >= 0) {
                        "+${dailyStats.affectionChange}"
                    } else {
                        "${dailyStats.affectionChange}"
                    }
                    val tiles = listOf(
                        "今日消息" to "${dailyStats.messagesToday}",
                        "今日好感变化" to affectionChangeText,
                        "累计互动天数" to "$affectionDaysSinceFirst",
                    )
                    tiles.chunked(3).forEach { rowItems ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            rowItems.forEach { (label, value) ->
                                StatTile(label = label, value = value, modifier = Modifier.weight(1f))
                            }
                            if (rowItems.size < 3) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "提示：最近 7 天的好感度变化趋势需后端记录每日快照后展示，当前显示今日数据。",
                        color = StradustTheme.colors.textMuted,
                        fontSize = 11.sp,
                    )
                }
            }

            item { Spacer(Modifier.height(12.dp)) }

            // 情绪分布卡片
            item {
                StradustCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(
                        text = "情绪分布",
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    val topEmotion = topEmotionPair?.first ?: "暂无数据"
                    Text(
                        text = "主导情绪：$topEmotion",
                        color = StradustTheme.colors.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (emotionPercentages.isEmpty()) {
                        Text(
                            text = "还没有情绪记录",
                            color = StradustTheme.colors.textMuted,
                            fontSize = 13.sp,
                        )
                    } else {
                        emotionPercentages.entries
                            .sortedByDescending { it.value }
                            .forEach { (emotion, percent) ->
                                EmotionRow(emotion = emotion, percent = percent)
                                Spacer(Modifier.height(6.dp))
                            }
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }

            // 活跃时段卡片
            item {
                StradustCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(
                        text = "活跃时段",
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    val peakStr = if (peakHour in 0..23) {
                        "${"%02d".format(peakHour)}:00 - ${"%02d".format((peakHour + 1) % 24)}:00"
                    } else {
                        "暂无数据"
                    }
                    Text(
                        text = "峰值时段：$peakStr",
                        color = StradustTheme.colors.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(12.dp))
                    HourBarChart(hourDistribution = hourDistribution)
                }
            }

            item { Spacer(Modifier.height(12.dp)) }

            // 趋势卡片
            item {
                StradustCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(
                        text = "趋势",
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    val trendStr = if (moodTrend.isEmpty()) {
                        "暂无数据"
                    } else {
                        moodTrend.takeLast(10).joinToString(" → ")
                    }
                    Text(
                        text = "近期心情：$trendStr",
                        color = StradustTheme.colors.textSecondary,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "日均消息：%.1f".format(avgPerDay),
                        color = StradustTheme.colors.textSecondary,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "首次聊天距今：$daysSinceFirst 天",
                        color = StradustTheme.colors.textMuted,
                        fontSize = 12.sp,
                    )
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

/** 单个统计磁贴 */
@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(StradustTheme.colors.surfaceContainerLow)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                color = StradustTheme.colors.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                color = StradustTheme.colors.textMuted,
                fontSize = 11.sp,
            )
        }
    }
}

/** 情绪百分比行（标签 + 进度条 + 百分比） */
@Composable
private fun EmotionRow(emotion: String, percent: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = emotion,
            color = StradustTheme.colors.textPrimary,
            fontSize = 13.sp,
            modifier = Modifier.width(72.dp),
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(StradustTheme.colors.surfaceContainerLow),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((percent / 100f).coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(StradustTheme.colors.primary),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "%.1f%%".format(percent),
            color = StradustTheme.colors.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.width(48.dp),
        )
    }
}

/** 24 小时分布柱状图（用 Box 高度模拟） */
@Composable
private fun HourBarChart(hourDistribution: Map<Int, Int>) {
    val maxValue = hourDistribution.values.maxOrNull() ?: 0
    val safeMax = if (maxValue == 0) 1 else maxValue
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (hour in 0..23) {
            val count = hourDistribution[hour] ?: 0
            val heightFraction = if (count == 0) 0f else count.toFloat() / safeMax
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height((80f * heightFraction).coerceAtLeast(1f).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (count > 0) StradustTheme.colors.primary
                        else StradustTheme.colors.surfaceContainerLow
                    ),
            )
        }
    }
    Spacer(Modifier.height(4.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        listOf("0", "6", "12", "18", "23").forEach { label ->
            Text(
                text = label,
                color = StradustTheme.colors.textMuted,
                fontSize = 10.sp,
            )
        }
    }
}
