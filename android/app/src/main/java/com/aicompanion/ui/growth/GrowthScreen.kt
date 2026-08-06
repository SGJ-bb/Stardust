package com.aicompanion.ui.growth

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import com.aicompanion.gamify.GrowthManager
import com.aicompanion.models.GrowthNode
import com.aicompanion.theme.StradustTheme
import com.aicompanion.ui.components.StradustCard
import com.aicompanion.ui.components.StradustTopBar

/**
 * 成长之路页面
 *
 * 顶部当前阶段大卡片（阶段名/描述/进度条） + 成长树（6 阶段垂直列表）
 * 已解锁高亮，未解锁灰色锁图标
 *
 * 后端 GrowthManager 的方法均需 affectionLevel 与 daysSinceFirstUse 参数
 */
@Composable
fun GrowthScreen(
    affectionLevel: Int = 0,
    daysSinceFirstUse: Int = 0,
    onBackClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val manager = remember { GrowthManager(context) }

    val currentStage = remember(affectionLevel, daysSinceFirstUse) {
        manager.getCurrentStage(affectionLevel, daysSinceFirstUse)
    }
    val (current, next) = remember(affectionLevel, daysSinceFirstUse) {
        manager.getStageProgress(affectionLevel, daysSinceFirstUse)
    }
    val growthTree = remember(affectionLevel, daysSinceFirstUse) {
        manager.buildGrowthTree(affectionLevel, daysSinceFirstUse)
    }

    // 当前进度：当前阶段 → 下一阶段的好感度区间
    val progress = remember(affectionLevel, current, next) {
        if (current.id == next.id) 1f
        else {
            val range = next.requiredAffection - current.requiredAffection
            if (range <= 0) 1f
            else ((affectionLevel - current.requiredAffection).toFloat() / range).coerceIn(0f, 1f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StradustTheme.colors.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            StradustTopBar(title = "成长之路", onBackClick = onBackClick)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 当前阶段大卡片
                item {
                    Spacer(Modifier.height(8.dp))
                    CurrentStageCard(
                        stage = currentStage,
                        progress = progress,
                        affectionLevel = affectionLevel,
                        nextStageName = if (current.id != next.id) next.name else null,
                        nextRequiredAffection = if (current.id != next.id) next.requiredAffection else null,
                    )
                }

                // 成长树标题
                item {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "成长树",
                            color = StradustTheme.colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "(${growthTree.size} 阶段)",
                            color = StradustTheme.colors.textMuted,
                            fontSize = 12.sp,
                        )
                    }
                }

                // 成长树节点
                itemsIndexed(growthTree, key = { _, node -> node.id }) { index, node ->
                    GrowthTreeNodeRow(
                        node = node,
                        isCurrent = node.id == currentStage.id,
                        isLast = index == growthTree.lastIndex,
                    )
                }

                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}

/** 当前阶段大卡片：阶段名 + 描述 + 进度条 */
@Composable
private fun CurrentStageCard(
    stage: GrowthManager.GrowthStage,
    progress: Float,
    affectionLevel: Int,
    nextStageName: String?,
    nextRequiredAffection: Int?,
) {
    StradustCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 大图标
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(
                        StradustTheme.colors.primary.copy(alpha = 0.15f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stage.icon,
                    fontSize = 32.sp,
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "当前阶段",
                    color = StradustTheme.colors.textMuted,
                    fontSize = 11.sp,
                )
                Text(
                    text = stage.name,
                    color = StradustTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stage.description,
                    color = StradustTheme.colors.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 2,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // 进度条
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = StradustTheme.colors.primary,
            trackColor = StradustTheme.colors.surfaceContainerHigh,
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "好感度 $affectionLevel",
                color = StradustTheme.colors.textSecondary,
                fontSize = 12.sp,
            )
            if (nextStageName != null && nextRequiredAffection != null) {
                Text(
                    text = "距 $nextStageName 还需 ${nextRequiredAffection - affectionLevel}",
                    color = StradustTheme.colors.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            } else {
                Text(
                    text = "已达到最高阶段 ✨",
                    color = StradustTheme.colors.tertiary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/** 成长树单个节点（垂直列表项，含连接线） */
@Composable
private fun GrowthTreeNodeRow(
    node: GrowthNode,
    isCurrent: Boolean,
    isLast: Boolean,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        // 左侧：图标 + 连接线
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isCurrent -> StradustTheme.colors.primary
                            node.unlocked -> StradustTheme.colors.primary.copy(alpha = 0.15f)
                            else -> StradustTheme.colors.surfaceContainerHigh
                        },
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (node.unlocked) {
                    Text(
                        text = node.icon,
                        fontSize = 22.sp,
                    )
                } else {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "未解锁",
                        modifier = Modifier.size(20.dp),
                        tint = StradustTheme.colors.textMuted,
                    )
                }
            }
            // 连接线
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(36.dp)
                        .background(
                            if (node.unlocked) StradustTheme.colors.primary.copy(alpha = 0.3f)
                            else StradustTheme.colors.surfaceContainerHigh,
                        ),
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // 右侧：内容
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = node.name,
                    color = if (node.unlocked) StradustTheme.colors.textPrimary
                    else StradustTheme.colors.textMuted,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
                if (isCurrent) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(StradustTheme.colors.primary, RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "当前",
                            color = StradustTheme.colors.onPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                if (node.unlocked && !isCurrent) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "已解锁",
                        modifier = Modifier.size(14.dp),
                        tint = StradustTheme.colors.tertiary,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = node.description,
                color = if (node.unlocked) StradustTheme.colors.textSecondary
                else StradustTheme.colors.textMuted,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "所需好感度：${node.requiredAffection}",
                color = StradustTheme.colors.textMuted,
                fontSize = 11.sp,
            )
            if (!isLast) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(
                    color = StradustTheme.colors.outlineVariant.copy(alpha = 0.3f),
                )
            }
        }
    }
}
