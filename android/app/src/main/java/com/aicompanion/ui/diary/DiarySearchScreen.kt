package com.aicompanion.ui.diary

import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicompanion.diary.DiaryManager
import com.aicompanion.theme.StradustTheme
import com.aicompanion.ui.components.ButtonSize
import com.aicompanion.ui.components.ButtonVariant
import com.aicompanion.ui.components.StradustButton
import com.aicompanion.ui.components.StradustCard
import com.aicompanion.ui.components.StradustInput
import com.aicompanion.ui.components.StradustTopBar
import com.aicompanion.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 日记搜索页
 *
 * 功能：
 * - 关键词搜索 / RAG 语义搜索
 * - 心情筛选（开心 / 难过 / 平静 / 生气 / 全部）
 * - 搜索结果列表（日期 / 标题 / 内容摘要 / 心情 emoji）
 * - 导出 Markdown / JSON 到剪贴板
 * - 从剪贴板读取 JSON 导入
 *
 * 后端 API：[DiaryManager]
 */
@Composable
fun DiarySearchScreen(onBackClick: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = StradustTheme.colors

    // DiaryManager 构造参数 personaId 有默认值，这里仅传 context
    val diaryManager = remember { DiaryManager(context) }

    var query by remember { mutableStateOf("") }
    var selectedMood by remember { mutableStateOf(MoodFilter.ALL) }
    var useRag by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<com.aicompanion.diary.DiaryEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // 执行搜索（统一在 IO 线程执行，RAG 为 suspend）
    val doSearch: () -> Unit = {
        scope.launch {
            loading = true
            try {
                val list = withContext(Dispatchers.IO) {
                    runCatching {
                        when {
                            // 心情筛选优先：按心情过滤，再叠加关键词客户端二次过滤
                            selectedMood != MoodFilter.ALL -> {
                                val byMood = diaryManager.getDiariesByMood(selectedMood.moodKey)
                                if (query.isNotBlank()) {
                                    byMood.filter {
                                        it.title.contains(query, true) ||
                                            it.content.contains(query, true)
                                    }
                                } else byMood
                            }
                            query.isNotBlank() && useRag -> diaryManager.searchDiariesRag(query)
                            query.isNotBlank() -> diaryManager.searchDiaries(query)
                            else -> diaryManager.getAllDiaries()
                        }
                    }.getOrElse { e ->
                        AppLogger.e("DiarySearchScreen", "search failed: ${e.message}")
                        emptyList()
                    }
                }
                results = list
                statusMessage = "找到 ${list.size} 条日记"
            } finally {
                loading = false
            }
        }
    }

    // 首次进入加载全部
    LaunchedEffect(Unit) { doSearch() }

    // 状态消息自动消失
    LaunchedEffect(statusMessage) {
        if (statusMessage != null) {
            kotlinx.coroutines.delay(2000)
            statusMessage = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            StradustTopBar(title = "日记搜索", onBackClick = onBackClick)

            // 搜索框 + 搜索按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StradustInput(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    hint = "搜索日记标题或内容…",
                    singleLine = true,
                )
                Spacer(Modifier.width(8.dp))
                StradustButton(
                    text = "搜索",
                    onClick = { doSearch() },
                    size = ButtonSize.MEDIUM,
                )
            }

            // 心情筛选 + RAG 开关
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MoodFilter.entries.forEach { mood ->
                    FilterChip(
                        selected = selectedMood == mood,
                        onClick = {
                            selectedMood = mood
                            doSearch()
                        },
                        label = { Text(mood.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.primary,
                            selectedLabelColor = colors.onPrimary,
                        ),
                    )
                }
                Spacer(Modifier.weight(1f))
                FilterChip(
                    selected = useRag,
                    onClick = { useRag = !useRag },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("RAG")
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.tertiary,
                        selectedLabelColor = colors.onTertiary,
                    ),
                )
            }

            // 导出 / 导入操作栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StradustButton(
                    text = "导出 MD",
                    onClick = {
                        val content = try {
                            diaryManager.exportToMarkdown(results)
                        } catch (e: Exception) {
                            AppLogger.e("DiarySearchScreen", "export MD failed: ${e.message}")
                            statusMessage = "导出失败：${e.message}"
                            null
                        }
                        if (content != null) {
                            exportToClipboard(context, "diary_markdown", content)
                            statusMessage = "已导出 Markdown 到剪贴板（${results.size} 条）"
                        }
                    },
                    variant = ButtonVariant.OUTLINED,
                    size = ButtonSize.SMALL,
                    enabled = results.isNotEmpty(),
                )
                StradustButton(
                    text = "导出 JSON",
                    onClick = {
                        val json = try {
                            diaryManager.exportToJson(results)
                        } catch (e: Exception) {
                            AppLogger.e("DiarySearchScreen", "export JSON failed: ${e.message}")
                            statusMessage = "导出失败：${e.message}"
                            null
                        }
                        if (json != null) {
                            exportToClipboard(context, "diary_json", json)
                            statusMessage = "已导出 JSON 到剪贴板（${results.size} 条）"
                        }
                    },
                    variant = ButtonVariant.OUTLINED,
                    size = ButtonSize.SMALL,
                    enabled = results.isNotEmpty(),
                )
                StradustButton(
                    text = "从剪贴板导入",
                    onClick = {
                        val clipText = readFromClipboard(context)
                        if (clipText.isNullOrBlank()) {
                            statusMessage = "剪贴板为空"
                        } else {
                            scope.launch {
                                loading = true
                                try {
                                    val result = withContext(Dispatchers.IO) {
                                        try {
                                            diaryManager.importFromJson(clipText)
                                        } catch (e: Exception) {
                                            AppLogger.e("DiarySearchScreen", "import failed: ${e.message}")
                                            statusMessage = "导入失败：${e.message}"
                                            null
                                        }
                                    }
                                    if (result != null) {
                                        statusMessage = "导入 ${result.imported} 条，跳过 ${result.skipped} 条" +
                                            if (result.errors.isNotEmpty()) "，错误 ${result.errors.size} 条" else ""
                                        doSearch()
                                    }
                                } finally {
                                    loading = false
                                }
                            }
                        }
                    },
                    variant = ButtonVariant.TONAL,
                    size = ButtonSize.SMALL,
                )
            }

            // 结果统计
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "共 ${results.size} 条结果",
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                )
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = colors.primary,
                    )
                }
            }

            // 结果列表
            if (results.isEmpty() && !loading) {
                EmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp,
                        vertical = 8.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(results, key = { it.date }) { entry ->
                        DiarySearchResultCard(entry = entry)
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }

        // 底部状态提示
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
}

/** 单条搜索结果卡片 */
@Composable
private fun DiarySearchResultCard(entry: com.aicompanion.diary.DiaryEntry) {
    StradustCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.CalendarToday,
                contentDescription = null,
                tint = StradustTheme.colors.primary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = entry.date,
                color = StradustTheme.colors.textSecondary,
                fontSize = 13.sp,
            )
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        StradustTheme.colors.primaryContainer.copy(alpha = 0.4f),
                        RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "${entry.moodEmoji} ${entry.mood}",
                    color = StradustTheme.colors.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "❤ ${entry.affectionLevel}",
                color = StradustTheme.colors.tertiary,
                fontSize = 12.sp,
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = entry.title.ifBlank { "无标题" },
            color = StradustTheme.colors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = entry.content,
            color = StradustTheme.colors.textSecondary,
            fontSize = 13.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 20.sp,
        )
    }
}

/** 空状态 */
@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = StradustTheme.colors.textMuted,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "没有找到匹配的日记",
            color = StradustTheme.colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "试试换个关键词或心情筛选 ✍️",
            color = StradustTheme.colors.textMuted,
            fontSize = 13.sp,
        )
    }
}

/** 心情筛选项 */
private enum class MoodFilter(val label: String, val moodKey: String) {
    ALL("全部", ""),
    HAPPY("开心", "happy"),
    SAD("难过", "sad"),
    CALM("平静", "calm"),
    ANGRY("生气", "angry"),
}

/** 写入系统剪贴板（兼容大文本，使用系统 ClipboardManager） */
private fun exportToClipboard(context: Context, label: String, content: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(android.content.ClipData.newPlainText(label, content))
}

/** 读取系统剪贴板纯文本 */
private fun readFromClipboard(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = clipboard.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}
