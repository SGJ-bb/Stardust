package com.aicompanion.ui.album

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aicompanion.album.AlbumEntry
import com.aicompanion.album.LayoutTemplate
import com.aicompanion.album.MemorialAlbumManager
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
 * 生成相册图片页
 *
 * 功能：
 * - 角色参考图区域：显示当前参考图 / 上传按钮（从相册选择）
 * - 布局模板选择（横向滚动卡片，6 个模板）
 * - Prompt 输入框（预填随机 prompt，可编辑）
 * - 随机 prompt 按钮 + 内置 prompt 预设
 * - 生成按钮（调用 generateImage，显示进度）
 * - 生成结果预览（成功显示图片，失败显示错误）
 *
 * 后端 API：[MemorialAlbumManager]（object 单例）
 */
@Composable
fun AlbumGenScreen(onBackClick: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = StradustTheme.colors

    val templates = remember { MemorialAlbumManager.layoutTemplates }

    // 选中的模板索引（持久化）
    var selectedTemplateIndex by remember {
        mutableStateOf(MemorialAlbumManager.getCurrentTemplateIndex(context))
    }

    // Prompt 文本与标题（getRandomPrompt 返回 Pair<title, prompt>）
    var promptText by remember { mutableStateOf("") }
    var promptTitle by remember { mutableStateOf("") }

    // 角色参考图
    var refImagePath by remember { mutableStateOf("") }
    var refImageVersion by remember { mutableStateOf(0) }

    // 生成状态
    var generating by remember { mutableStateOf(false) }
    var resultEntry by remember { mutableStateOf<AlbumEntry?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // 角色参考图自动生成状态
    var generatingRef by remember { mutableStateOf(false) }
    var generatedRefTempPath by remember { mutableStateOf<String?>(null) }
    var showRefConfirmDialog by remember { mutableStateOf(false) }

    // 初始化：随机 prompt + 读取参考图
    LaunchedEffect(Unit) {
        val random = try { MemorialAlbumManager.getRandomPrompt() } catch (e: Exception) {
            AppLogger.e("AlbumGenScreen", "getRandomPrompt failed: ${e.message}")
            null
        }
        if (random != null) {
            promptTitle = random.first
            promptText = random.second
        }
        refImagePath = try { MemorialAlbumManager.getCharacterRefImagePath(context) } catch (e: Exception) {
            AppLogger.e("AlbumGenScreen", "getCharacterRefImagePath failed: ${e.message}")
            ""
        }
    }

    // 状态消息自动消失
    LaunchedEffect(statusMessage) {
        if (statusMessage != null) {
            kotlinx.coroutines.delay(2500)
            statusMessage = null
        }
    }

    // 图片选择器：上传角色参考图
    val refImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                generating = true
                try {
                    val ok = withContext(Dispatchers.IO) {
                        val tempPath = try {
                            copyUriToCacheFile(context, uri)
                        } catch (e: Exception) {
                            AppLogger.e("AlbumGenScreen", "copy ref image failed: ${e.message}")
                            null
                        } ?: return@withContext false
                        try {
                            // 复用 confirmCharacterRefImage：拷贝到 memorial_album/character_ref.png 并持久化路径
                            MemorialAlbumManager.confirmCharacterRefImage(context, tempPath)
                        } catch (e: Exception) {
                            AppLogger.e("AlbumGenScreen", "confirmCharacterRefImage failed: ${e.message}")
                            false
                        }
                    }
                    if (ok) {
                        refImagePath = MemorialAlbumManager.getCharacterRefImagePath(context)
                        refImageVersion++
                        statusMessage = "角色参考图已更新"
                    } else {
                        statusMessage = "参考图保存失败"
                    }
                } finally {
                    generating = false
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            StradustTopBar(title = "生成相册图片", onBackClick = onBackClick)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // ── 角色参考图区域 ──
                StradustCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "角色参考图",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    val hasRef = remember(refImageVersion) {
                        try { MemorialAlbumManager.hasCharacterRefImage(context) }
                        catch (e: Exception) { AppLogger.e("AlbumGenScreen", "hasCharacterRefImage: ${e.message}"); false }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surfaceContainerLow),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (hasRef && refImagePath.isNotBlank()) {
                                AsyncImage(
                                    model = refImagePath,
                                    contentDescription = "角色参考图",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Icon(
                                    Icons.Default.AccountBox,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = colors.textMuted,
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (hasRef) "已设置参考图" else "未设置参考图",
                                color = colors.textSecondary,
                                fontSize = 13.sp,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "上传角色形象，生成时会更贴合人设",
                                color = colors.textMuted,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                            )
                        }
                        // 上传按钮：从相册选择参考图
                        StradustButton(
                            text = "上传",
                            onClick = { refImagePicker.launch("image/*") },
                            variant = ButtonVariant.TONAL,
                            size = ButtonSize.SMALL,
                        )
                        Spacer(Modifier.width(8.dp))
                        // 自动生成按钮：调用 generateCharacterRefImage（suspend）生成参考图
                        StradustButton(
                            text = if (generatingRef) "生成中…" else "自动生成",
                            onClick = {
                                if (!generatingRef) {
                                    generatingRef = true
                                    scope.launch {
                                        try {
                                            val tempPath = try {
                                                MemorialAlbumManager.generateCharacterRefImage(context)
                                            } catch (e: Exception) {
                                                AppLogger.e("AlbumGenScreen", "generateCharacterRefImage: ${e.message}")
                                                null
                                            }
                                            if (tempPath != null) {
                                                // 生成成功，弹出预览确认对话框
                                                generatedRefTempPath = tempPath
                                                showRefConfirmDialog = true
                                                statusMessage = "参考图已生成，请确认"
                                            } else {
                                                statusMessage = "参考图生成失败"
                                            }
                                        } finally {
                                            generatingRef = false
                                        }
                                    }
                                }
                            },
                            variant = ButtonVariant.OUTLINED,
                            size = ButtonSize.SMALL,
                            enabled = !generatingRef,
                        )
                    }
                }

                // ── 布局模板选择 ──
                Text(
                    text = "布局模板",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    templates.forEachIndexed { index, template ->
                        TemplateCard(
                            template = template,
                            selected = index == selectedTemplateIndex,
                            onClick = {
                                selectedTemplateIndex = index
                                try {
                                    MemorialAlbumManager.saveCurrentTemplateIndex(context, index)
                                } catch (e: Exception) {
                                    AppLogger.e("AlbumGenScreen", "saveTemplateIndex: ${e.message}")
                                }
                            },
                        )
                    }
                }

                // ── Prompt 输入 ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Prompt",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f),
                    )
                    StradustButton(
                        text = "随机",
                        onClick = {
                            val random = try { MemorialAlbumManager.getRandomPrompt() }
                            catch (e: Exception) {
                                AppLogger.e("AlbumGenScreen", "getRandomPrompt: ${e.message}")
                                null
                            }
                            if (random != null) {
                                promptTitle = random.first
                                promptText = random.second
                                statusMessage = "已填入「${random.first}」"
                            }
                        },
                        variant = ButtonVariant.OUTLINED,
                        size = ButtonSize.SMALL,
                    )
                }
                StradustInput(
                    value = promptText,
                    onValueChange = { promptText = it },
                    hint = "输入生成画面的 prompt…",
                    maxLines = 4,
                )

                // ── 内置 prompt 预设 ──
                val builtinPrompts = remember {
                    try { MemorialAlbumManager.getBuiltinPrompts() }
                    catch (e: Exception) {
                        AppLogger.e("AlbumGenScreen", "getBuiltinPrompts: ${e.message}")
                        emptyList()
                    }
                }
                if (builtinPrompts.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        builtinPrompts.forEach { (title, prompt) ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(colors.surfaceContainerHigh, RoundedCornerShape(16.dp))
                                    .clickable {
                                        promptTitle = title
                                        promptText = prompt
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = title,
                                    color = colors.textSecondary,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }

                // ── 生成按钮 ──
                val configured = remember {
                    try { MemorialAlbumManager.isImageModelConfigured(context) }
                    catch (e: Exception) {
                        AppLogger.e("AlbumGenScreen", "isImageModelConfigured: ${e.message}")
                        false
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StradustButton(
                        text = if (generating) "生成中…" else "生成图片",
                        onClick = {
                            if (promptText.isBlank()) {
                                statusMessage = "请输入 prompt"
                            } else if (!configured) {
                                errorMessage = "图片生成模型未配置，请先在设置中配置"
                                statusMessage = "模型未配置"
                            } else {
                                errorMessage = null
                                resultEntry = null
                                generating = true
                                val title = promptTitle.ifBlank { "相册图片" }
                                val aspectRatio = templates.getOrNull(selectedTemplateIndex)?.aspectRatio ?: "1:1"
                                scope.launch {
                                    try {
                                        val entry = withContext(Dispatchers.IO) {
                                            try {
                                                MemorialAlbumManager.generateImage(
                                                    context = context,
                                                    prompt = promptText,
                                                    title = title,
                                                    caption = "",
                                                    aspectRatio = aspectRatio,
                                                )
                                            } catch (e: Exception) {
                                                AppLogger.e("AlbumGenScreen", "generateImage: ${e.message}")
                                                null
                                            }
                                        }
                                        if (entry != null) {
                                            resultEntry = entry
                                            statusMessage = "生成成功"
                                        } else {
                                            errorMessage = "生成失败，请检查模型配置或稍后重试"
                                            statusMessage = "生成失败"
                                        }
                                    } finally {
                                        generating = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        variant = ButtonVariant.FILLED,
                        size = ButtonSize.LARGE,
                        enabled = !generating,
                    )
                    if (generating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = colors.primary,
                        )
                    }
                }

                // ── 生成结果预览 ──
                resultEntry?.let { entry -> ResultPreview(entry = entry) }
                errorMessage?.let { msg -> ErrorPreview(message = msg) }

                Spacer(Modifier.height(24.dp))
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

        // 角色参考图自动生成预览确认对话框
        if (showRefConfirmDialog && generatedRefTempPath != null) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = {
                    showRefConfirmDialog = false
                    generatedRefTempPath = null
                },
                title = { Text("确认角色参考图", fontWeight = FontWeight.SemiBold) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "AI 已生成角色参考图，确认后将保存为当前参考图：",
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                        )
                        Spacer(Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surfaceContainerLow),
                            contentAlignment = Alignment.Center,
                        ) {
                            AsyncImage(
                                model = generatedRefTempPath,
                                contentDescription = "生成的参考图",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        val tempPath = generatedRefTempPath
                        showRefConfirmDialog = false
                        if (tempPath != null) {
                            scope.launch {
                                try {
                                    val ok = withContext(Dispatchers.IO) {
                                        try {
                                            MemorialAlbumManager.confirmCharacterRefImage(context, tempPath)
                                        } catch (e: Exception) {
                                            AppLogger.e("AlbumGenScreen", "confirmCharacterRefImage: ${e.message}")
                                            false
                                        }
                                    }
                                    if (ok) {
                                        refImagePath = MemorialAlbumManager.getCharacterRefImagePath(context)
                                        refImageVersion++
                                        statusMessage = "角色参考图已保存"
                                    } else {
                                        statusMessage = "参考图保存失败"
                                    }
                                } finally {
                                    generatedRefTempPath = null
                                }
                            }
                        }
                    }) {
                        Text("确认保存", color = colors.primary, fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        showRefConfirmDialog = false
                        generatedRefTempPath = null
                    }) {
                        Text("放弃", color = colors.textMuted)
                    }
                },
            )
        }
    }
}

/** 布局模板卡片 */
@Composable
private fun TemplateCard(
    template: LayoutTemplate,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = StradustTheme.colors
    Column(
        modifier = Modifier
            .width(96.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) colors.primaryContainer.copy(alpha = 0.5f)
                else colors.surfaceContainerLow,
                RoundedCornerShape(12.dp),
            )
            .then(
                if (selected) Modifier.border(1.5.dp, colors.primary, RoundedCornerShape(12.dp))
                else Modifier,
            )
            .clickable { onClick() }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = template.icon,
            fontSize = 28.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = template.name,
            color = colors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = template.aspectRatio,
            color = colors.textMuted,
            fontSize = 10.sp,
        )
    }
}

/** 生成成功结果预览 */
@Composable
private fun ResultPreview(entry: AlbumEntry) {
    val colors = StradustTheme.colors
    StradustCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = colors.tertiary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "生成结果",
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = entry.createdAt,
                color = colors.textMuted,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.height(10.dp))
        val ratio = MemorialAlbumManager.aspectRatioToHeightMultiplier(entry.aspectRatio.ifBlank { "1:1" })
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ratio.coerceIn(0.4f, 2f))
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceContainerLow),
            contentAlignment = Alignment.Center,
        ) {
            if (entry.imagePath.isNotBlank()) {
                AsyncImage(
                    model = entry.imagePath,
                    contentDescription = entry.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Icon(
                    Icons.Default.Image,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = colors.textMuted.copy(alpha = 0.5f),
                )
            }
        }
        if (entry.title.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = entry.title,
                color = colors.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** 生成失败错误预览 */
@Composable
private fun ErrorPreview(message: String) {
    val colors = StradustTheme.colors
    StradustCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.BrokenImage,
                contentDescription = null,
                tint = colors.error,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = message,
                color = colors.error,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** 将内容 Uri 拷贝到缓存临时文件，返回绝对路径 */
private fun copyUriToCacheFile(context: Context, uri: Uri): String {
    val tempFile = File(context.cacheDir, "album_pick_${System.currentTimeMillis()}.png")
    context.contentResolver.openInputStream(uri)?.use { input ->
        tempFile.outputStream().use { output -> input.copyTo(output) }
    } ?: throw java.io.IOException("无法读取图片流")
    return tempFile.absolutePath
}
