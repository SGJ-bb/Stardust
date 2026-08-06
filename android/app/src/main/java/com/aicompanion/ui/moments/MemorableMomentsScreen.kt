package com.aicompanion.ui.moments

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicompanion.memory.MemorableMomentsManager
import com.aicompanion.memory.ScoredMemory
import com.aicompanion.theme.StradustTheme
import com.aicompanion.ui.components.StradustCard
import com.aicompanion.ui.components.StradustTopBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 分数筛选档位 */
private enum class ScoreFilter(val label: String, val minScore: Int) {
    ALL("全部", 0),
    ABOVE_EIGHT("8分以上", 8),
    ABOVE_NINE("9分以上", 9),
}

/**
 * 难忘时刻页面
 *
 * 展示按分数降序排列的难忘时刻列表，支持按分数筛选和删除。
 *
 * @param onBackClick 返回回调
 */
@Composable
fun MemorableMomentsScreen(
    onBackClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val personaId = remember {
        val pm = com.aicompanion.persona.PersonaManager(context)
        pm.load()
        pm.getActivePersona()?.id ?: "default"
    }
    val manager = remember(personaId) { MemorableMomentsManager(context, personaId) }
    var refreshTick by remember { mutableStateOf(0) }
    var filter by remember { mutableStateOf(ScoreFilter.ALL) }

    val allMoments = remember(refreshTick) { manager.getAll() }
    val moments = remember(allMoments, filter) {
        if (filter == ScoreFilter.ALL) allMoments
        else allMoments.filter { it.score >= filter.minScore }
    }
    val totalCount = allMoments.size
    val avgScore = remember(allMoments) {
        if (allMoments.isEmpty()) 0f else allMoments.map { it.score }.sum().toFloat() / allMoments.size
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StradustTheme.colors.background),
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { StradustTopBar(title = "难忘时刻", onBackClick = onBackClick) }

            item { Spacer(Modifier.height(16.dp)) }

            // 统计栏
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatPill(label = "总数", value = "$totalCount")
                    StatPill(label = "平均分", value = "%.1f".format(avgScore))
                }
            }

            item { Spacer(Modifier.height(12.dp)) }

            // 分数筛选
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ScoreFilter.entries.forEach { f ->
                        FilterChip(
                            selected = filter == f,
                            onClick = { filter = f },
                            label = { Text(f.label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = StradustTheme.colors.primary,
                                selectedLabelColor = StradustTheme.colors.onPrimary,
                            ),
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }

            if (moments.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 80.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = StradustTheme.colors.textMuted,
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "还没有难忘时刻",
                                color = StradustTheme.colors.textMuted,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            } else {
                items(moments, key = { it.id }) { moment ->
                    MomentCard(
                        moment = moment,
                        onDelete = {
                            manager.deleteMoment(moment.id)
                            refreshTick++
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

/** 统计胶囊 */
@Composable
private fun StatPill(label: String, value: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(StradustTheme.colors.surfaceContainerLow)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$label：",
                color = StradustTheme.colors.textMuted,
                fontSize = 12.sp,
            )
            Text(
                text = value,
                color = StradustTheme.colors.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** 单条难忘时刻卡片 */
@Composable
private fun MomentCard(
    moment: ScoredMemory,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StradustCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                // 顶栏：分类 + 时间
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(StradustTheme.colors.tertiary.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = moment.category,
                            color = StradustTheme.colors.tertiary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Text(
                        text = formatTime(moment.timestamp),
                        color = StradustTheme.colors.textMuted,
                        fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = moment.content,
                    color = StradustTheme.colors.textPrimary,
                    fontSize = 14.sp,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                ScoreStars(score = moment.score)
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = StradustTheme.colors.textMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** 分数星级显示（每星 = 2 分，半星用透明度模拟） */
@Composable
private fun ScoreStars(score: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val clamped = score.coerceIn(0, 10)
        val fullStars = clamped / 2
        val hasHalf = clamped % 2 == 1
        for (i in 0 until 5) {
            val tint = when {
                i < fullStars -> StradustTheme.colors.tertiary
                i == fullStars && hasHalf -> StradustTheme.colors.tertiary.copy(alpha = 0.5f)
                else -> StradustTheme.colors.surfaceContainerHigh
            }
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp),
            )
            if (i < 4) Spacer(Modifier.width(2.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$score",
            color = StradustTheme.colors.tertiary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 时间戳格式化 */
private fun formatTime(timestamp: Long): String {
    return try {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    } catch (_: Exception) {
        ""
    }
}
