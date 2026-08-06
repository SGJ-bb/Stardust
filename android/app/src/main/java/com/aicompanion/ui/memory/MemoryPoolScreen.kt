package com.aicompanion.ui.memory

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import com.aicompanion.memory.GlobalMemoryPool
import com.aicompanion.memory.MemoryEntry
import com.aicompanion.memory.MemoryPool
import com.aicompanion.memory.SessionInfo
import com.aicompanion.memory.SessionManager
import com.aicompanion.theme.StradustTheme
import com.aicompanion.ui.components.ButtonVariant
import com.aicompanion.ui.components.StradustButton
import com.aicompanion.ui.components.StradustCard
import com.aicompanion.ui.components.StradustTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PERSONA_ID = "default"

private enum class MemoryTab(val label: String) {
    SCENE("场景记忆池"),
    GLOBAL("全局记忆池"),
    SESSION("会话管理"),
}

/**
 * 记忆管理界面合并状态：将多个同步 IO 聚合为单个 produceState 异步加载，
 * 避免组合期同步 SP/磁盘读取阻塞主线程。
 */
private data class MemoryPoolUiState(
    val sceneEntries: List<MemoryEntry> = emptyList(),
    val sceneCharCount: Int = 0,
    val sceneNeedsConsolidate: Boolean = false,
    val sceneStats: String = "",
    val globalEntries: List<MemoryEntry> = emptyList(),
    val globalSize: Int = 0,
    val globalStats: String = "",
    val currentSessionId: String = "",
    val currentTurnCount: Int = 0,
    val allSessions: List<SessionInfo> = emptyList(),
)

/**
 * 记忆管理界面
 *
 * 功能：
 * - Tab 切换：场景记忆池 / 全局记忆池 / 会话管理
 * - 场景记忆池：统计栏（总条数/字符数/是否需要压缩）+ 记忆列表
 * - 全局记忆池：统计栏 + 记忆列表
 * - 会话管理：当前会话ID/轮次 + 历史会话列表
 * - 每条记忆可删除
 */
@Composable
fun MemoryPoolScreen(onBackClick: () -> Unit = {}) {
    val context = LocalContext.current
    val scenePool = remember { MemoryPool(context, PERSONA_ID) }
    val globalPool = remember { GlobalMemoryPool(context, PERSONA_ID) }
    val sessionManager = remember { SessionManager(context) }
    var refreshTick by remember { mutableStateOf(0) }
    var selectedTab by remember { mutableStateOf(MemoryTab.SCENE) }

    // 预计算各 Tab 数据（异步加载，避免组合期同步 IO）
    val uiState by produceState(MemoryPoolUiState(), refreshTick) {
        value = withContext(Dispatchers.IO) {
            MemoryPoolUiState(
                sceneEntries = scenePool.getAll(),
                sceneCharCount = scenePool.getPoolCharCount(),
                sceneNeedsConsolidate = scenePool.needsConsolidate(),
                sceneStats = scenePool.getStats(),
                globalEntries = globalPool.getAll(),
                globalSize = globalPool.size,
                globalStats = globalPool.getStats(),
                currentSessionId = sessionManager.currentSessionId,
                currentTurnCount = sessionManager.currentTurnCount,
                allSessions = sessionManager.getAllSessions(),
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(StradustTheme.colors.background)) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { StradustTopBar(title = "记忆管理", onBackClick = onBackClick) }
            item { Spacer(Modifier.height(12.dp)) }

            // Tab 切换栏
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MemoryTab.entries.forEach { tab ->
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
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }

            when (selectedTab) {
                MemoryTab.SCENE -> sceneContent(
                    entries = uiState.sceneEntries,
                    charCount = uiState.sceneCharCount,
                    needsConsolidate = uiState.sceneNeedsConsolidate,
                    stats = uiState.sceneStats,
                    onClear = { scenePool.clear(); refreshTick++ },
                    onDelete = { id -> scenePool.delete(id); refreshTick++ },
                )
                MemoryTab.GLOBAL -> globalContent(
                    entries = uiState.globalEntries,
                    size = uiState.globalSize,
                    stats = uiState.globalStats,
                    onClear = { globalPool.clear(); refreshTick++ },
                    onDelete = { id -> globalPool.delete(id); refreshTick++ },
                )
                MemoryTab.SESSION -> sessionContent(
                    currentId = uiState.currentSessionId,
                    currentTurns = uiState.currentTurnCount,
                    sessions = uiState.allSessions,
                    onClear = { sessionManager.clear(); refreshTick++ },
                )
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

// ===== 场景记忆池 =====

private fun LazyListScope.sceneContent(
    entries: List<MemoryEntry>,
    charCount: Int,
    needsConsolidate: Boolean,
    stats: String,
    onClear: () -> Unit,
    onDelete: (String) -> Unit,
) {
    item {
        StradustCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "统计",
                    color = StradustTheme.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f),
                )
                if (needsConsolidate) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                StradustTheme.colors.error.copy(alpha = 0.15f),
                                RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "需要压缩",
                            color = StradustTheme.colors.error,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            StatRow(label = "总条数", value = "${entries.size}")
            Spacer(Modifier.height(6.dp))
            StatRow(label = "字符数", value = "$charCount")
            Spacer(Modifier.height(6.dp))
            StatRow(label = "状态", value = stats)
            Spacer(Modifier.height(12.dp))
            StradustButton(
                text = "清空记忆池",
                onClick = onClear,
                variant = ButtonVariant.OUTLINED,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    item { Spacer(Modifier.height(16.dp)) }

    item {
        StradustCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                text = "记忆列表",
                color = StradustTheme.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(8.dp))
            if (entries.isEmpty()) {
                Text(
                    text = "暂无场景记忆",
                    color = StradustTheme.colors.textMuted,
                    fontSize = 13.sp,
                )
            }
        }
    }

    items(entries.size, key = { entries[it].id }) { index ->
        val entry = entries[index]
        MemoryEntryItem(
            entry = entry,
            onDelete = { onDelete(entry.id) },
        )
        Spacer(Modifier.height(8.dp))
    }
}

// ===== 全局记忆池 =====

private fun LazyListScope.globalContent(
    entries: List<MemoryEntry>,
    size: Int,
    stats: String,
    onClear: () -> Unit,
    onDelete: (String) -> Unit,
) {
    item {
        StradustCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                text = "统计",
                color = StradustTheme.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(8.dp))
            StatRow(label = "总条数", value = "$size")
            Spacer(Modifier.height(6.dp))
            StatRow(label = "状态", value = stats)
            Spacer(Modifier.height(12.dp))
            StradustButton(
                text = "清空全局记忆",
                onClick = onClear,
                variant = ButtonVariant.OUTLINED,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    item { Spacer(Modifier.height(16.dp)) }

    item {
        StradustCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                text = "跨场景共享记忆",
                color = StradustTheme.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(8.dp))
            if (entries.isEmpty()) {
                Text(
                    text = "暂无全局记忆",
                    color = StradustTheme.colors.textMuted,
                    fontSize = 13.sp,
                )
            }
        }
    }

    items(entries.size, key = { entries[it].id }) { index ->
        val entry = entries[index]
        MemoryEntryItem(
            entry = entry,
            onDelete = { onDelete(entry.id) },
        )
        Spacer(Modifier.height(8.dp))
    }
}

// ===== 会话管理 =====

private fun LazyListScope.sessionContent(
    currentId: String,
    currentTurns: Int,
    sessions: List<SessionInfo>,
    onClear: () -> Unit,
) {
    item {
        StradustCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                text = "当前会话",
                color = StradustTheme.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(8.dp))
            StatRow(label = "会话 ID", value = currentId)
            Spacer(Modifier.height(6.dp))
            StatRow(label = "当前轮次", value = "$currentTurns")
            Spacer(Modifier.height(12.dp))
            StradustButton(
                text = "清空全部会话",
                onClick = onClear,
                variant = ButtonVariant.OUTLINED,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    item { Spacer(Modifier.height(16.dp)) }

    item {
        StradustCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "历史会话",
                    color = StradustTheme.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "共 ${sessions.size} 个",
                    color = StradustTheme.colors.textMuted,
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
            if (sessions.isEmpty()) {
                Text(
                    text = "暂无历史会话",
                    color = StradustTheme.colors.textMuted,
                    fontSize = 13.sp,
                )
            }
        }
    }

    items(sessions.size, key = { sessions[it].id }) { index ->
        val session = sessions[index]
        SessionItem(session = session, isCurrent = session.id == currentId)
        Spacer(Modifier.height(8.dp))
    }
}

// ===== 通用组件 =====

@Composable
private fun MemoryEntryItem(entry: MemoryEntry, onDelete: () -> Unit) {
    StradustCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.content,
                    color = StradustTheme.colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row {
                    CategoryTag(text = entry.category)
                    Spacer(Modifier.width(8.dp))
                    if (entry.scene.isNotBlank()) {
                        Text(
                            text = "场景：${entry.scene}",
                            color = StradustTheme.colors.textMuted,
                            fontSize = 11.sp,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row {
                    Text(
                        text = "轮次：${entry.sourceTurn}",
                        color = StradustTheme.colors.textMuted,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = formatMemoryTime(entry.timestamp),
                        color = StradustTheme.colors.textMuted,
                        fontSize = 11.sp,
                    )
                }
                if (entry.place.isNotBlank() || entry.people.isNotBlank() || entry.event.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    val detail = buildString {
                        if (entry.place.isNotBlank()) append("地点:${entry.place} ")
                        if (entry.people.isNotBlank()) append("人物:${entry.people} ")
                        if (entry.event.isNotBlank()) append("事件:${entry.event}")
                    }.trim()
                    Text(
                        text = detail,
                        color = StradustTheme.colors.textMuted,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
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
    }
}

@Composable
private fun SessionItem(session: SessionInfo, isCurrent: Boolean) {
    StradustCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "会话 ${session.id}",
                        color = StradustTheme.colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isCurrent) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    StradustTheme.colors.tertiary.copy(alpha = 0.15f),
                                    RoundedCornerShape(6.dp),
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "当前",
                                color = StradustTheme.colors.tertiary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row {
                    Text(
                        text = "轮次：${session.turnCount}",
                        color = StradustTheme.colors.textMuted,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = formatMemoryTime(session.startTime),
                        color = StradustTheme.colors.textMuted,
                        fontSize = 11.sp,
                    )
                }
                if (session.inheritedFrom != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "继承自：${session.inheritedFrom}",
                        color = StradustTheme.colors.textMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = StradustTheme.colors.textMuted,
            fontSize = 13.sp,
            modifier = Modifier.width(72.dp),
        )
        Text(
            text = value,
            color = StradustTheme.colors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun CategoryTag(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                StradustTheme.colors.primary.copy(alpha = 0.12f),
                RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            color = StradustTheme.colors.primary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun formatMemoryTime(timestamp: Long): String {
    if (timestamp <= 0L) return "—"
    return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA)
        .format(java.util.Date(timestamp))
}
