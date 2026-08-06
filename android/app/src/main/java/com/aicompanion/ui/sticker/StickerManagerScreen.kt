package com.aicompanion.ui.sticker

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aicompanion.sticker.Sticker
import com.aicompanion.sticker.StickerManager
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
import java.io.File

/**
 * 贴纸管理页
 *
 * 功能：
 * - Tab 切换：内置贴纸 / 用户贴纸
 * - 关键词搜索（按描述 / 情绪 / 标签）
 * - 贴纸网格（3 列），图片 + 描述
 * - 用户贴纸可删除
 * - 添加贴纸（选择图片 + 输入描述 / 情绪 / 标签）
 *
 * 后端 API：[StickerManager]
 */
@Composable
fun StickerManagerScreen(onBackClick: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = StradustTheme.colors

    val manager = remember { StickerManager(context) }

    var selectedTab by remember { mutableStateOf(0) } // 0=内置, 1=用户
    var keyword by remember { mutableStateOf("") }
    var stickers by remember { mutableStateOf<List<Sticker>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // 添加贴纸对话框
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingImageUri by remember { mutableStateOf<Uri?>(null) }

    // 删除确认
    var pendingDelete by remember { mutableStateOf<Sticker?>(null) }

    // 刷新当前列表
    val refresh: () -> Unit = {
        scope.launch {
            loading = true
            try {
                val list = withContext(Dispatchers.IO) {
                    try {
                        manager.loadStickers()
                        if (keyword.isBlank()) {
                            if (selectedTab == 0) manager.getBuiltinStickers()
                            else manager.getUserStickers()
                        } else {
                            manager.searchStickersByKeyword(keyword).filter { sticker ->
                                if (selectedTab == 0) sticker.isBuiltin() else !sticker.isBuiltin()
                            }
                        }
                    } catch (e: Exception) {
                        AppLogger.e("StickerManagerScreen", "refresh failed: ${e.message}")
                        emptyList()
                    }
                }
                stickers = list
            } finally {
                loading = false
            }
        }
    }

    // 首次加载 + tab 变化时刷新（LaunchedEffect(selectedTab) 首次组合时也会触发）
    LaunchedEffect(selectedTab) { refresh() }

    // 状态消息自动消失
    LaunchedEffect(statusMessage) {
        if (statusMessage != null) {
            kotlinx.coroutines.delay(2000)
            statusMessage = null
        }
    }

    // 图片选择器（添加贴纸用）
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            pendingImageUri = uri
            showAddDialog = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            StradustTopBar(title = "贴纸管理", onBackClick = onBackClick)

            // Tab 切换（胶囊式，与项目 VwTabRow 风格一致）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("内置贴纸" to 0, "用户贴纸" to 1).forEach { (label, idx) ->
                    val isActive = selectedTab == idx
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isActive) colors.primary
                                else colors.surfaceContainerLow.copy(alpha = 0.5f),
                                RoundedCornerShape(20.dp),
                            )
                            .clickable { selectedTab = idx }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = label,
                            color = if (isActive) colors.onPrimary else colors.textSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }

            // 搜索框
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StradustInput(
                    value = keyword,
                    onValueChange = { keyword = it },
                    modifier = Modifier.weight(1f),
                    hint = "搜索描述 / 情绪 / 标签…",
                    singleLine = true,
                )
                Spacer(Modifier.width(8.dp))
                StradustButton(
                    text = "搜索",
                    onClick = { refresh() },
                    size = ButtonSize.MEDIUM,
                )
            }

            // 操作行：添加贴纸（仅用户贴纸 tab）+ 数量统计
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "共 ${stickers.size} 张",
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = colors.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    if (selectedTab == 1) {
                        StradustButton(
                            text = "添加贴纸",
                            onClick = { imagePicker.launch("image/*") },
                            variant = ButtonVariant.TONAL,
                            size = ButtonSize.SMALL,
                        )
                    }
                }
            }

            // 贴纸网格
            if (stickers.isEmpty() && !loading) {
                EmptyState(isUserTab = selectedTab == 1, onAdd = { imagePicker.launch("image/*") })
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp,
                        vertical = 8.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(stickers, key = { it.id }, contentType = { it.owner }) { sticker ->
                        StickerGridItem(
                            sticker = sticker,
                            canDelete = !sticker.isBuiltin(),
                            onDelete = { pendingDelete = sticker },
                        )
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

    // 添加贴纸对话框
    if (showAddDialog && pendingImageUri != null) {
        AddStickerDialog(
            imageUri = pendingImageUri!!,
            onDismiss = {
                showAddDialog = false
                pendingImageUri = null
            },
            onConfirm = { description, emotion, tagsText ->
                val uri = pendingImageUri
                showAddDialog = false
                pendingImageUri = null
                if (uri == null) return@AddStickerDialog
                scope.launch {
                    loading = true
                    try {
                        val added = withContext(Dispatchers.IO) {
                            val tempPath = try {
                                copyUriToCacheFile(context, uri)
                            } catch (e: Exception) {
                                AppLogger.e("StickerManagerScreen", "copy image failed: ${e.message}")
                                null
                            } ?: return@withContext false
                            val tags = tagsText.split(",", "，", " ")
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                            try {
                                manager.addSticker(
                                    sourcePath = tempPath,
                                    description = description,
                                    emotion = emotion,
                                    tags = tags,
                                    owner = "user",
                                    embedding = null,
                                )
                                true
                            } catch (e: Exception) {
                                AppLogger.e("StickerManagerScreen", "addSticker failed: ${e.message}")
                                false
                            }
                        }
                        statusMessage = if (added) "贴纸已添加" else "添加失败"
                        if (added) refresh()
                    } finally {
                        loading = false
                    }
                }
            },
        )
    }

    // 删除确认对话框
    pendingDelete?.let { sticker ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除贴纸") },
            text = {
                Text(
                    text = "确定删除这张贴纸吗？此操作不可撤销。",
                    color = StradustTheme.colors.textSecondary,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        loading = true
                        var deleted = false
                        try {
                            withContext(Dispatchers.IO) {
                                try {
                                    manager.deleteSticker(sticker.id)
                                    deleted = true
                                } catch (e: Exception) {
                                    AppLogger.e("StickerManagerScreen", "delete failed: ${e.message}")
                                }
                            }
                            statusMessage = if (deleted) "已删除" else "删除失败"
                            if (deleted) {
                                refresh()
                            }
                        } finally {
                            loading = false
                        }
                    }
                }) {
                    Text("删除", color = StradustTheme.colors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

/** 贴纸网格项 */
@Composable
private fun StickerGridItem(
    sticker: Sticker,
    canDelete: Boolean,
    onDelete: () -> Unit,
) {
    StradustCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = 2.dp,
        cornerRadius = 12.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(StradustTheme.colors.surfaceContainerLow),
            contentAlignment = Alignment.Center,
        ) {
            if (sticker.filePath.isNotBlank()) {
                AsyncImage(
                    model = sticker.filePath,
                    contentDescription = sticker.description,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    Icons.Default.Image,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = StradustTheme.colors.textMuted.copy(alpha = 0.5f),
                )
            }

            // 用户贴纸右上角删除按钮
            if (canDelete) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(26.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(StradustTheme.colors.scrim.copy(alpha = 0.5f)),
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = StradustTheme.colors.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // 描述
        Text(
            text = sticker.description.ifBlank { "未命名" },
            color = StradustTheme.colors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // 情绪 / 标签
        if (sticker.emotion.isNotBlank() || sticker.tags.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = buildString {
                    if (sticker.emotion.isNotBlank()) append(sticker.emotion)
                    if (sticker.tags.isNotEmpty()) {
                        if (isNotEmpty()) append(" · ")
                        append(sticker.tags.joinToString(" "))
                    }
                },
                color = StradustTheme.colors.textMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 添加贴纸对话框：预览图 + 描述 / 情绪 / 标签输入 */
@Composable
private fun AddStickerDialog(
    imageUri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (description: String, emotion: String, tags: String) -> Unit,
) {
    var description by remember { mutableStateOf("") }
    var emotion by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    val colors = StradustTheme.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加贴纸") },
        text = {
            Column(modifier = Modifier.imePadding()) {
                // 图片预览
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceContainerLow),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text("描述", color = colors.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                StradustInput(
                    value = description,
                    onValueChange = { description = it },
                    hint = "给贴纸起个名字…",
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Text("情绪", color = colors.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                StradustInput(
                    value = emotion,
                    onValueChange = { emotion = it },
                    hint = "如：开心 / 害羞 / 生气…",
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Text("标签（逗号分隔）", color = colors.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                StradustInput(
                    value = tags,
                    onValueChange = { tags = it },
                    hint = "如：可爱, 表情包, 日常",
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(description, emotion, tags) },
                enabled = description.isNotBlank(),
            ) { Text("添加", color = colors.primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 空状态 */
@Composable
private fun EmptyState(isUserTab: Boolean, onAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.EmojiEmotions,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = StradustTheme.colors.textMuted,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (isUserTab) "还没有用户贴纸" else "没有找到贴纸",
            color = StradustTheme.colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
        )
        Spacer(Modifier.height(6.dp))
        if (isUserTab) {
            StradustButton(
                text = "添加第一张贴纸",
                onClick = onAdd,
                variant = ButtonVariant.TONAL,
                size = ButtonSize.SMALL,
            )
        } else {
            Text(
                text = "试试换个关键词 🎨",
                color = StradustTheme.colors.textMuted,
                fontSize = 13.sp,
            )
        }
    }
}

/** 判断是否内置贴纸（owner 为 builtin 或 id 以 builtin_ 开头） */
private fun Sticker.isBuiltin(): Boolean = owner == "builtin" || id.startsWith("builtin_")

/** 将内容 Uri 拷贝到缓存临时文件，返回绝对路径 */
private fun copyUriToCacheFile(context: Context, uri: Uri): String {
    val tempFile = File(context.cacheDir, "sticker_pick_${System.currentTimeMillis()}.png")
    context.contentResolver.openInputStream(uri)?.use { input ->
        tempFile.outputStream().use { output -> input.copyTo(output) }
    } ?: throw java.io.IOException("无法读取图片流")
    return tempFile.absolutePath
}
