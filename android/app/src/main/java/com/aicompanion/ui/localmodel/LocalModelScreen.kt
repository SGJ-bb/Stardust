package com.aicompanion.ui.localmodel

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicompanion.localmodel.DeviceProfile
import com.aicompanion.localmodel.LocalModelManager
import com.aicompanion.localmodel.ModelInfo
import com.aicompanion.localmodel.ModelTier
import com.aicompanion.theme.StradustTheme
import com.aicompanion.ui.components.ButtonSize
import com.aicompanion.ui.components.ButtonVariant
import com.aicompanion.ui.components.StradustButton
import com.aicompanion.ui.components.StradustCard
import com.aicompanion.ui.components.StradustTopBar
import kotlinx.coroutines.launch

/**
 * 本地模型管理界面
 *
 * 功能：
 * - 设备性能卡片（设备名/RAM/CPU/GPU）
 * - OCR / 场景分类 / 自动分析 三个开关
 * - 可用模型列表（名称/大小/类型/下载状态）
 * - 下载/删除按钮 + 下载进度显示
 */
@Composable
fun LocalModelScreen(onBackClick: () -> Unit = {}) {
    val context = LocalContext.current
    val manager = remember { LocalModelManager(context) }
    val scope = rememberCoroutineScope()
    var refreshTick by remember { mutableStateOf(0) }

    var ocrEnabled by remember(refreshTick) { mutableStateOf(manager.isOcrEnabled) }
    var sceneEnabled by remember(refreshTick) { mutableStateOf(manager.isSceneEnabled) }
    var autoAnalyze by remember(refreshTick) { mutableStateOf(manager.isAutoAnalyze) }

    val deviceProfile = remember(refreshTick) { runCatching { manager.getDeviceProfile() }.getOrNull() }
    val ocrAvailable = remember(refreshTick) { manager.isOcrModelAvailable() }
    val availableModels = remember(refreshTick) { manager.getAvailableModels() }
    // 已下载模型列表（用于"仅看已下载"筛选）
    val downloadedModels = remember(refreshTick) { runCatching { manager.getDownloadedModels() }.getOrDefault(emptyList()) }
    // 是否仅显示已下载模型
    var showDownloadedOnly by remember { mutableStateOf(false) }
    // 当前已加载的模型 ID（用于显示加载状态）：遍历检查 isModelLoaded
    val loadedModelId = remember(refreshTick, availableModels) {
        availableModels.firstOrNull { runCatching { manager.isModelLoaded(it.id) }.getOrDefault(false) }?.id
    }

    // 下载状态：modelId -> 进度百分比（-1 表示下载中但无百分比）
    val downloadingId = remember { mutableStateOf<String?>(null) }
    val downloadProgress = remember { mutableStateOf(0) }

    // 测试工具相关状态
    var testing by remember { mutableStateOf(false) }
    var testResultText by remember { mutableStateOf<String?>(null) }
    var testStatus by remember { mutableStateOf<String?>(null) }

    // 退出时释放资源
    DisposableEffect(Unit) {
        onDispose {
            runCatching { manager.release() }
        }
    }

    val displayedModels = remember(availableModels, showDownloadedOnly, refreshTick) {
        if (showDownloadedOnly) {
            availableModels.filter { runCatching { manager.isModelAvailable(it) }.getOrDefault(false) }
        } else {
            availableModels
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(StradustTheme.colors.background)) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { StradustTopBar(title = "本地模型管理", onBackClick = onBackClick) }
            item { Spacer(Modifier.height(16.dp)) }

            // 设备性能卡片
            item {
                StradustCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(
                        text = "设备性能",
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    if (deviceProfile != null) {
                        DeviceInfoRow(label = "设备", value = deviceProfile.deviceInfo)
                        Spacer(Modifier.height(8.dp))
                        DeviceInfoRow(label = "总内存", value = "${deviceProfile.totalRamMB} MB")
                        Spacer(Modifier.height(8.dp))
                        DeviceInfoRow(label = "可用内存", value = "${deviceProfile.availableRamMB} MB")
                        Spacer(Modifier.height(8.dp))
                        DeviceInfoRow(label = "CPU 架构", value = deviceProfile.cpuAbi)
                        Spacer(Modifier.height(8.dp))
                        DeviceInfoRow(label = "GPU 加速", value = if (deviceProfile.gpuSupport) "支持" else "不支持")
                        Spacer(Modifier.height(8.dp))
                        DeviceInfoRow(label = "推荐档位", value = deviceProfile.recommendedTier.label)
                        Spacer(Modifier.height(8.dp))
                        DeviceInfoRow(label = "可用存储", value = "${deviceProfile.availableStorageMB} MB")
                    } else {
                        Text(
                            text = "无法获取设备信息",
                            color = StradustTheme.colors.textMuted,
                            fontSize = 13.sp,
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }

            // 功能开关
            item {
                StradustCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(
                        text = "功能开关",
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    SwitchRow(
                        title = "OCR 文字识别",
                        subtitle = if (ocrAvailable) "模型已就绪" else "模型未就绪",
                        checked = ocrEnabled,
                        onCheckedChange = {
                            ocrEnabled = it
                            manager.setOcrEnabled(it)
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    SwitchRow(
                        title = "场景分类",
                        subtitle = "识别当前屏幕所属的应用场景",
                        checked = sceneEnabled,
                        onCheckedChange = {
                            sceneEnabled = it
                            manager.setSceneEnabled(it)
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    SwitchRow(
                        title = "自动分析",
                        subtitle = "自动捕获并分析屏幕内容",
                        checked = autoAnalyze,
                        onCheckedChange = {
                            autoAnalyze = it
                            manager.setAutoAnalyze(it)
                        },
                    )
                }
            }

            item { Spacer(Modifier.height(16.dp)) }

            // ===== 测试工具卡片：屏幕分析 / OCR / 场景分类 / 停止截屏 =====
            item {
                StradustCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(
                        text = "测试工具",
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "需要先在主界面开启屏幕截屏权限",
                        color = StradustTheme.colors.textMuted,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(12.dp))

                    // 分析当前屏幕按钮（调用 analyzeCurrentScreen，suspend）
                    StradustButton(
                        text = if (testing) "分析中…" else "分析当前屏幕",
                        onClick = {
                            if (!testing) {
                                testing = true
                                testResultText = null
                                testStatus = "正在分析屏幕…"
                                scope.launch {
                                    try {
                                        val result = manager.analyzeCurrentScreen()
                                        if (result == null) {
                                            testStatus = "屏幕截屏未启动，无法分析"
                                            testResultText = null
                                        } else {
                                            testStatus = "分析完成"
                                            // 拼接 OCR 文本和场景分类
                                            val sb = StringBuilder()
                                            if (result.ocrText.isNotBlank()) {
                                                sb.appendLine("【OCR 文本】")
                                                sb.appendLine(result.ocrText.take(500))
                                            }
                                            if (result.sceneClassification.isNotEmpty()) {
                                                sb.appendLine("【场景分类】")
                                                result.sceneClassification.forEach { cr ->
                                                    sb.appendLine("- ${cr.label} (${(cr.confidence * 100).toInt()}%)")
                                                }
                                            }
                                            if (result.combinedDescription.isNotBlank()) {
                                                sb.appendLine("【综合描述】")
                                                sb.appendLine(result.combinedDescription)
                                            }
                                            testResultText = if (sb.isEmpty()) "无识别结果" else sb.toString()
                                        }
                                    } catch (e: Exception) {
                                        testStatus = "分析失败: ${e.message}"
                                    } finally {
                                        testing = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        size = ButtonSize.SMALL,
                        enabled = !testing,
                    )
                    Spacer(Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // OCR 测试按钮：捕获屏幕后调用 performOcr
                        StradustButton(
                            text = "OCR 测试",
                            onClick = {
                                if (!testing) {
                                    testing = true
                                    testResultText = null
                                    testStatus = "OCR 识别中…"
                                    scope.launch {
                                        try {
                                            val bmp = manager.getScreenCaptureManager().captureScreen()
                                            if (bmp == null) {
                                                testStatus = "屏幕截屏未启动"
                                            } else {
                                                val ocrText = manager.performOcr(bmp)
                                                testStatus = "OCR 完成"
                                                testResultText = if (ocrText.isBlank()) "未识别到文字"
                                                else "【OCR 文本】\n${ocrText.take(500)}"
                                            }
                                        } catch (e: Exception) {
                                            testStatus = "OCR 失败: ${e.message}"
                                        } finally {
                                            testing = false
                                        }
                                    }
                                }
                            },
                            variant = ButtonVariant.OUTLINED,
                            size = ButtonSize.SMALL,
                            modifier = Modifier.weight(1f),
                            enabled = !testing,
                        )
                        // 场景分类测试按钮：捕获屏幕后调用 classifyScene
                        StradustButton(
                            text = "场景分类",
                            onClick = {
                                if (!testing) {
                                    testing = true
                                    testResultText = null
                                    testStatus = "场景分类中…"
                                    scope.launch {
                                        try {
                                            val bmp = manager.getScreenCaptureManager().captureScreen()
                                            if (bmp == null) {
                                                testStatus = "屏幕截屏未启动"
                                            } else {
                                                val results = manager.classifyScene(bmp)
                                                testStatus = "场景分类完成"
                                                testResultText = if (results.isEmpty()) "未加载场景分类模型或无结果"
                                                else buildString {
                                                    appendLine("【场景分类】")
                                                    results.forEach { cr ->
                                                        appendLine("- ${cr.label} (${(cr.confidence * 100).toInt()}%)")
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {
                                            testStatus = "场景分类失败: ${e.message}"
                                        } finally {
                                            testing = false
                                        }
                                    }
                                }
                            },
                            variant = ButtonVariant.OUTLINED,
                            size = ButtonSize.SMALL,
                            modifier = Modifier.weight(1f),
                            enabled = !testing,
                        )
                    }
                    Spacer(Modifier.height(8.dp))

                    // 停止截屏按钮：调用 ScreenCaptureManager.stopCapture
                    StradustButton(
                        text = "停止截屏",
                        onClick = {
                            try {
                                manager.getScreenCaptureManager().stopCapture()
                                testStatus = "已停止屏幕截屏"
                            } catch (e: Exception) {
                                testStatus = "停止失败: ${e.message}"
                            }
                        },
                        variant = ButtonVariant.OUTLINED,
                        size = ButtonSize.SMALL,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // 测试状态与结果展示
                    testStatus?.let { status ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = status,
                            color = StradustTheme.colors.tertiary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    testResultText?.let { result ->
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(StradustTheme.colors.surfaceContainerLow.copy(alpha = 0.5f))
                                .padding(10.dp),
                        ) {
                            Text(
                                text = result,
                                color = StradustTheme.colors.textSecondary,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }

            // 可用模型列表
            item {
                StradustCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "可用模型",
                            color = StradustTheme.colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "共 ${availableModels.size} 个 · 已下载 ${downloadedModels.size} 个",
                            color = StradustTheme.colors.textMuted,
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (availableModels.isEmpty()) {
                        Text(
                            text = "当前设备无可用模型",
                            color = StradustTheme.colors.textMuted,
                            fontSize = 13.sp,
                        )
                    } else {
                        // 已下载模型筛选切换：调用 getDownloadedModels 后仅显示已下载
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(StradustTheme.colors.surfaceContainerLow.copy(alpha = 0.4f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Switch(
                                checked = showDownloadedOnly,
                                onCheckedChange = { showDownloadedOnly = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = StradustTheme.colors.onPrimary,
                                    checkedTrackColor = StradustTheme.colors.primary,
                                    uncheckedThumbColor = StradustTheme.colors.textSecondary,
                                    uncheckedTrackColor = StradustTheme.colors.surfaceContainerHigh,
                                ),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "仅显示已下载模型",
                                color = StradustTheme.colors.textSecondary,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }

            // 模型条目（使用 items 以便懒加载）：displayedModels 已在 LazyColumn 外计算
            items(displayedModels, key = { it.id }) { model ->
                val isDownloaded = remember(refreshTick, model.id) { manager.isModelAvailable(model) }
                val isDownloading = downloadingId.value == model.id
                val progress = if (isDownloading) downloadProgress.value else 0
                val isLoaded = loadedModelId == model.id

                ModelRow(
                    model = model,
                    isDownloaded = isDownloaded,
                    isDownloading = isDownloading,
                    progress = progress,
                    isLoaded = isLoaded,
                    onDownload = {
                        if (downloadingId.value == null) {
                            scope.launch {
                                downloadingId.value = model.id
                                downloadProgress.value = 0
                                val downloader = manager.getModelDownloader()
                                val success = downloader.downloadModel(model) { p ->
                                    downloadProgress.value = p.percentage
                                }
                                downloadingId.value = null
                                downloadProgress.value = 0
                                if (success) refreshTick++
                            }
                        }
                    },
                    onDelete = {
                        // 删除前若该模型已加载，先卸载
                        if (isLoaded) manager.unloadModel()
                        manager.getModelDownloader().deleteModel(model)
                        refreshTick++
                    },
                    onCancel = {
                        manager.getModelDownloader().cancelDownload(model.id)
                        downloadingId.value = null
                        downloadProgress.value = 0
                    },
                    onLoad = {
                        // 加载 TFLite 模型
                        val ok = manager.loadTFLiteModel(model)
                        refreshTick++
                        testStatus = if (ok) "模型 ${model.name} 加载成功" else "模型 ${model.name} 加载失败"
                    },
                    onUnload = {
                        // 卸载当前模型
                        manager.unloadModel()
                        refreshTick++
                        testStatus = "已卸载模型"
                    },
                )
                Spacer(Modifier.height(8.dp))
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun DeviceInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = StradustTheme.colors.textMuted,
            fontSize = 13.sp,
            modifier = Modifier.width(80.dp),
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
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                StradustTheme.colors.surfaceContainerLow.copy(alpha = 0.4f),
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = StradustTheme.colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                color = StradustTheme.colors.textMuted,
                fontSize = 11.sp,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = StradustTheme.colors.onPrimary,
                checkedTrackColor = StradustTheme.colors.primary,
                uncheckedThumbColor = StradustTheme.colors.textSecondary,
                uncheckedTrackColor = StradustTheme.colors.surfaceContainerHigh,
            ),
        )
    }
}

@Composable
private fun ModelRow(
    model: ModelInfo,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    progress: Int,
    isLoaded: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
) {
    StradustCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = model.name,
                        color = StradustTheme.colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(8.dp))
                    TierBadge(tier = model.tier)
                    // 已加载状态标识（isModelLoaded）
                    if (isLoaded) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(StradustTheme.colors.primary.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "● 已加载",
                                color = StradustTheme.colors.primary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = model.description,
                    color = StradustTheme.colors.textMuted,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row {
                    Text(
                        text = "大小：${formatModelSize(model)}",
                        color = StradustTheme.colors.textMuted,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "最低 RAM：${model.minRamMB} MB",
                        color = StradustTheme.colors.textMuted,
                        fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row {
                    Text(
                        text = if (model.builtIn) "类型：内置" else "类型：可下载",
                        color = StradustTheme.colors.textMuted,
                        fontSize = 11.sp,
                    )
                    if (model.gpuRequired) {
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "需 GPU",
                            color = StradustTheme.colors.tertiary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = if (isDownloaded) "✓ 已就绪" else "未下载",
                        color = if (isDownloaded) StradustTheme.colors.tertiary
                        else StradustTheme.colors.textMuted,
                        fontSize = 11.sp,
                        fontWeight = if (isDownloaded) FontWeight.Medium else FontWeight.Normal,
                    )
                }
            }
        }

        // 下载进度条
        if (isDownloading) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { (progress.coerceIn(0, 100)) / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = StradustTheme.colors.primary,
                trackColor = StradustTheme.colors.surfaceContainerHigh,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "下载中… $progress%",
                color = StradustTheme.colors.textMuted,
                fontSize = 11.sp,
            )
        }

        // 操作按钮：下载/删除 + 加载/卸载
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // 下载管理按钮（仅可下载模型）
            if (!model.builtIn) {
                when {
                    isDownloading -> {
                        StradustButton(
                            text = "取消",
                            onClick = onCancel,
                            variant = ButtonVariant.OUTLINED,
                            size = ButtonSize.SMALL,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    isDownloaded -> {
                        StradustButton(
                            text = "删除",
                            onClick = onDelete,
                            variant = ButtonVariant.OUTLINED,
                            size = ButtonSize.SMALL,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    else -> {
                        StradustButton(
                            text = "下载",
                            onClick = onDownload,
                            size = ButtonSize.SMALL,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            // 加载/卸载按钮（模型已就绪时显示）
            if (isDownloaded || model.builtIn) {
                if (isLoaded) {
                    StradustButton(
                        text = "卸载",
                        onClick = onUnload,
                        variant = ButtonVariant.OUTLINED,
                        size = ButtonSize.SMALL,
                        modifier = if (!model.builtIn) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                    )
                } else {
                    StradustButton(
                        text = "加载",
                        onClick = onLoad,
                        size = ButtonSize.SMALL,
                        modifier = if (!model.builtIn) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun TierBadge(tier: ModelTier) {
    val bgColor = when (tier) {
        ModelTier.LITE -> StradustTheme.colors.tertiary.copy(alpha = 0.15f)
        ModelTier.STANDARD -> StradustTheme.colors.primary.copy(alpha = 0.15f)
        ModelTier.PRO -> StradustTheme.colors.error.copy(alpha = 0.15f)
    }
    val textColor = when (tier) {
        ModelTier.LITE -> StradustTheme.colors.tertiary
        ModelTier.STANDARD -> StradustTheme.colors.primary
        ModelTier.PRO -> StradustTheme.colors.error
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = tier.label,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun formatModelSize(model: ModelInfo): String {
    if (model.builtIn || model.sizeBytes <= 0L) return "内置"
    val mb = model.sizeBytes / (1024.0 * 1024.0)
    return if (mb >= 1024) {
        String.format("%.1f GB", mb / 1024)
    } else {
        String.format("%.0f MB", mb)
    }
}
