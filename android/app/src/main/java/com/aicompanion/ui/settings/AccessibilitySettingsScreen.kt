/** 无障碍设置页: 服务状态/AI操作权限/功能开关/操作参数/命令执行/技能库/App分类自定义 */
package com.aicompanion.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.aicompanion.file.AgentWorkspace
import com.aicompanion.screen.AppCategoryClassifier
import com.aicompanion.screen.ScreenRecognitionService
import com.aicompanion.settings.SettingsManager
import com.aicompanion.shell.ShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessibilitySettingsScreen(
    onBack: () -> Unit,
    onOpenSkills: () -> Unit = {},
    onOpenGuide: () -> Unit = {},
) {
    val context = LocalContext.current
    val settings = remember { SettingsManager(context) }
    val scrollState = rememberScrollState()

    // 本地状态：与 SettingsManager 双向绑定
    var operationMode by remember { mutableStateOf(settings.aiOperationMode) }
    var cooldownMs by remember { mutableStateOf(settings.aiOperationCooldownMs.toFloat()) }
    var maxRounds by remember { mutableStateOf(settings.aiOperationMaxRounds.toFloat()) }
    var toolRounds by remember { mutableStateOf(settings.aiToolLoopMaxRounds.toFloat()) }
    var treeDepth by remember { mutableStateOf(settings.uiTreeMaxDepth.toFloat()) }
    var treeElements by remember { mutableStateOf(settings.uiTreeMaxElements.toFloat()) }
    var reactiveStrategy by remember { mutableStateOf(settings.phoneOpStrategy != "single_shot") }
    var screenFormat by remember { mutableStateOf(settings.phoneOpScreenFormat) }
    var abnormalDetection by remember { mutableStateOf(settings.abnormalDetectionEnabled) }
    var longPress by remember { mutableStateOf(settings.longPressEnabled) }
    var skillEnabled by remember { mutableStateOf(settings.skillEnabled) }
    var fileOpsEnabled by remember { mutableStateOf(settings.fileOpsEnabled) }
    var shellEnabled by remember { mutableStateOf(settings.shellEnabled) }
    var shellAllowDangerous by remember { mutableStateOf(settings.shellAllowDangerous) }
    var shellCaps by remember { mutableStateOf("检测中…") }
    var capsReloadKey by remember { mutableStateOf(0) }
    // Shizuku 授权状态（免 root 的 ADB 级通道）
    var shizukuGranted by remember { mutableStateOf(com.aicompanion.shell.ShizukuBridge.hasPermission()) }
    var shizukuStatus by remember { mutableStateOf(com.aicompanion.shell.ShizukuBridge.statusText()) }
    // 自建 ADB 通道
    val scope = rememberCoroutineScope()
    var adbState by remember { mutableStateOf(com.aicompanion.adb.AdbSelf.state) }
    var adbPort by remember { mutableStateOf("5555") }
    var adbHost by remember { mutableStateOf("127.0.0.1") }
    var adbScanMsg by remember { mutableStateOf("") }
    var adbBusy by remember { mutableStateOf(false) }
    var pairCode by remember { mutableStateOf("") }
    var pairMsg by remember { mutableStateOf("") }
    var pairBusy by remember { mutableStateOf(false) }
    var pairOk by remember { mutableStateOf(false) }
    // 服务状态：进入页面时读取一次，并在 onResume 时刷新
    var serviceEnabled by remember { mutableStateOf(ScreenRecognitionService.getInstance() != null) }
    // 公共存储权限状态：用户从系统设置页返回后要在 onResume 里重新读
    var publicAccess by remember { mutableStateOf(AgentWorkspace.hasPublicStorageAccess(context)) }
    // 系统动作（拨号 / 通讯录）相关的运行时权限状态
    var contactsGranted by remember {
        mutableStateOf(hasRuntimePermission(context, android.Manifest.permission.READ_CONTACTS))
    }
    var callGranted by remember {
        mutableStateOf(hasRuntimePermission(context, android.Manifest.permission.CALL_PHONE))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        contactsGranted = result[android.Manifest.permission.READ_CONTACTS]
            ?: hasRuntimePermission(context, android.Manifest.permission.READ_CONTACTS)
        callGranted = result[android.Manifest.permission.CALL_PHONE]
            ?: hasRuntimePermission(context, android.Manifest.permission.CALL_PHONE)
    }

    // 检测命令执行通道（会起进程，必须放后台）
    LaunchedEffect(capsReloadKey) {
        shellCaps = "检测中…"
        shellCaps = withContext(Dispatchers.IO) { ShellExecutor.capabilityReport() }
        shizukuGranted = com.aicompanion.shell.ShizukuBridge.hasPermission()
        shizukuStatus = com.aicompanion.shell.ShizukuBridge.statusText()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                serviceEnabled = ScreenRecognitionService.getInstance() != null
                // 用户可能刚从「所有文件访问权限」页面返回
                publicAccess = AgentWorkspace.hasPublicStorageAccess(context)
                // 也可能刚在系统设置里改过应用权限
                contactsGranted = hasRuntimePermission(context, android.Manifest.permission.READ_CONTACTS)
                callGranted = hasRuntimePermission(context, android.Manifest.permission.CALL_PHONE)
                // 服务可能刚被用户打开，重新套用采集参数
                if (ScreenRecognitionService.getInstance() != null) {
                    ScreenRecognitionService.applyConfig(settings)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // App 分类自定义列表
    var categoryOverrides by remember { mutableStateOf(AppCategoryClassifier.getAllUserOverrides()) }
    var newPkgInput by remember { mutableStateOf("") }
    var newCategoryInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("无障碍设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // ===== 互动教程入口 =====
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("第一次用？看互动教程", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "每个功能三步动画演示：语音唤醒怎么叫、无障碍怎么开、ADB 通道怎么连。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = onOpenGuide) { Text("打开功能引导") }
                }
            }

            // ===== 服务状态卡片 =====
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("服务状态", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    if (serviceEnabled) {
                        Text("✓ 无障碍服务已开启", color = MaterialTheme.colorScheme.primary)
                    } else {
                        Text("✗ 无障碍服务未开启", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            try {
                                context.startActivity(intent)
                            } catch (_: Exception) {
                            }
                        }) { Text("去开启") }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "提示：服务开启后请关闭再重新开启一次，让新增的「上报资源ID / 包含非重要视图 / 读取全部窗口」三项采集能力生效。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ===== AI 操作权限 =====
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("AI 操作权限", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    RadioButtonRow(
                        text = "全程允许（AI 自动执行所有命令）",
                        selected = operationMode == "auto",
                        onSelect = {
                            operationMode = "auto"
                            settings.aiOperationMode = "auto"
                        }
                    )
                    RadioButtonRow(
                        text = "需确认后执行（AI 提议，用户同意才操作）",
                        selected = operationMode == "confirm",
                        onSelect = {
                            operationMode = "confirm"
                            settings.aiOperationMode = "confirm"
                        }
                    )
                    Text(
                        "默认「全程允许」，AI 无需逐条确认即可自动操作，最适合测试自动化流程；" +
                            "如担心误操作可切回需确认模式。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ===== 功能开关 =====
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("功能开关", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    SwitchRow("异常检测（支付/验证码/登录页暂停，默认关闭以方便测试）", abnormalDetection) {
                        abnormalDetection = it
                        settings.abnormalDetectionEnabled = it
                    }
                    SwitchRow("长按手势", longPress) {
                        longPress = it
                        settings.longPressEnabled = it
                    }
                    SwitchRow("技能库（AI 读写跨 App 自动化技能）", skillEnabled) {
                        skillEnabled = it
                        settings.skillEnabled = it
                    }
                    Text(
                        "WebView 增强：已通过无障碍服务配置开启",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ===== 技能库入口 =====
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("技能库", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "把「怎么操作某个 App」沉淀成技能，AI 下次执行同类任务时会先读技能再动手，成功率高很多。支持手写、AI 生成、导入导出。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onOpenSkills) { Text("打开技能库") }
                }
            }

            // ===== 操作参数 =====
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("操作参数", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))

                    Text("动作冷却时间: ${cooldownMs.toInt()} ms")
                    Slider(
                        value = cooldownMs,
                        onValueChange = { cooldownMs = it },
                        onValueChangeFinished = { settings.aiOperationCooldownMs = cooldownMs.toInt() },
                        valueRange = 0f..2000f
                    )

                    Text("手机操作最大轮次（每轮=一次观察+一到几个动作）: ${maxRounds.toInt()}")
                    Slider(
                        value = maxRounds,
                        onValueChange = { maxRounds = it },
                        onValueChangeFinished = { settings.aiOperationMaxRounds = maxRounds.toInt() },
                        valueRange = 1f..50f
                    )

                    Text("AI 工具调用最大轮次（一轮 = AI 决策一次 + 执行一批工具）: ${toolRounds.toInt()}")
                    Slider(
                        value = toolRounds,
                        onValueChange = { toolRounds = it },
                        onValueChangeFinished = { settings.aiToolLoopMaxRounds = toolRounds.toInt() },
                        valueRange = 1f..SettingsManager.MAX_TOOL_LOOP_ROUNDS.toFloat()
                    )
                    Text(
                        "默认 ${SettingsManager.DEFAULT_TOOL_LOOP_MAX_ROUNDS} 轮（早期版本被写死成 3，复杂任务会被硬切断）。" +
                            "调大后 AI 能连着调更多次工具，代价是耗时与 token 消耗增加；调小可强制它早点给结论。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(10.dp))
                    SwitchRow("反应式闭环（每步重新读屏后再决策）", reactiveStrategy) {
                        reactiveStrategy = it
                        settings.phoneOpStrategy = if (it) "reactive" else "single_shot"
                    }
                    Text(
                        "关闭后退回旧行为：一次规划整段动作后盲执行。除非排障，建议保持开启。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(10.dp))
                    Text("AI 读取屏幕的格式", style = MaterialTheme.typography.titleSmall)
                    RadioButtonRow(
                        text = "结构化 UI 树（推荐，让 AI 真正看到界面层级与稳定 id）",
                        selected = screenFormat != "text",
                        onSelect = { screenFormat = "tree"; settings.phoneOpScreenFormat = "tree" }
                    )
                    RadioButtonRow(
                        text = "压扁文本（旧版降级，看不到层级/坐标）",
                        selected = screenFormat == "text",
                        onSelect = { screenFormat = "text"; settings.phoneOpScreenFormat = "text" }
                    )
                    Text(
                        "UI 树模式下，AI 能看懂每个界面的父子结构、按钮/输入框类型和元素坐标，" +
                            "用稳定 id 精确定位，操作可靠性和成功率更高。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ===== UI 树采集参数 =====
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("UI 树采集", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "看不到某些板块/找不到可点击元素时，优先调大这两个值。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))

                    Text("最大深度: ${treeDepth.toInt()} 层（旧版写死 3 层）")
                    Slider(
                        value = treeDepth,
                        onValueChange = { treeDepth = it },
                        onValueChangeFinished = {
                            settings.uiTreeMaxDepth = treeDepth.toInt()
                            ScreenRecognitionService.applyConfig(settings)
                        },
                        valueRange = 3f..20f
                    )

                    Text("最多登记可点击元素: ${treeElements.toInt()} 个（旧版写死 50 个）")
                    Slider(
                        value = treeElements,
                        onValueChange = { treeElements = it },
                        onValueChangeFinished = {
                            settings.uiTreeMaxElements = treeElements.toInt()
                            ScreenRecognitionService.applyConfig(settings)
                        },
                        valueRange = 30f..400f
                    )
                }
            }

            // ===== 文件读写 =====
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("文件读写", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "允许 AI 像 agent 一样列目录、读、写、追加、移动、复制、搜索文件。不经过 shell，零权限即可用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))

                    SwitchRow("允许 AI 读写文件", fileOpsEnabled) {
                        fileOpsEnabled = it
                        settings.fileOpsEnabled = it
                    }
                    Spacer(Modifier.height(8.dp))
                    // 公共存储授权：Android 11+ 需要 MANAGE_EXTERNAL_STORAGE 才能读写 /sdcard
                    val canPublic = publicAccess
                    Text(
                        "公共存储权限：" + if (canPublic) "已授权" else "未授权",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (canPublic) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!canPublic) {
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(onClick = { openAllFilesAccessSettings(context) }) {
                            Text("去授权（所有文件访问权限）")
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "不授权也能用：AI 只能读写下面两个零权限目录。想让它读写手机里的公共文档再开。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "可访问范围（越界路径会被拒绝）：\n" +
                            "· workspace/ —— 应用私有工作区，零权限，始终可用\n" +
                            "· external/ —— App 专属外部目录，插电脑可在 Android/data 下看到\n" +
                            "· public/ —— /sdcard/StardustAgent，需要上面的权限",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ===== 系统动作（拨号 / 通讯录）=====
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("系统动作", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "打电话、设闹钟、打开应用和系统设置这类事，AI 走的是系统官方接口，" +
                            "比模拟点击可靠得多，也不会点错。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "通讯录：${if (contactsGranted) "已授权（可按姓名拨号）" else "未授权（只能按号码拨号）"}\n" +
                            "通话：${if (callGranted) "已授权（可直接拨出）" else "未授权（填好号码由你按拨号键）"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "不授权也能用：AI 会把号码填进拨号盘，最后那一下拨号键由你按。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!contactsGranted || !callGranted) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = {
                            permissionLauncher.launch(
                                arrayOf(
                                    android.Manifest.permission.READ_CONTACTS,
                                    android.Manifest.permission.CALL_PHONE
                                )
                            )
                        }) {
                            Text("去授权（通讯录 / 通话）")
                        }
                    }
                }
            }

            // ===== 命令执行 =====
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("命令执行（高级）", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "允许 AI 在手机上执行 shell 命令，可查设备信息、拉起页面、在 ADB 通道下模拟触摸/截图等。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))

                    SwitchRow("允许执行 shell 命令", shellEnabled) {
                        shellEnabled = it
                        settings.shellEnabled = it
                    }
                    SwitchRow("允许高危命令（删除/卸载/改系统设置）", shellAllowDangerous) {
                        shellAllowDangerous = it
                        settings.shellAllowDangerous = it
                    }
                    if (shellAllowDangerous) {
                        Text(
                            "⚠ 已允许高危命令：AI 可能误删数据。请只在你完全信任当前任务时开启。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("可用通道：", style = MaterialTheme.typography.titleSmall)
                    Text(shellCaps, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "说明：免 root 只能以本应用身份执行，权限有限；装上 Shizuku 并授权后自动获得 ADB 级能力（input/settings/screencap 等）；设备已 root 则直通 root。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { capsReloadKey++ }) { Text("重新检测") }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Shizuku（免 root 的 ADB 级通道）",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "状态：" + shizukuStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (shizukuGranted) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(8.dp))
                    Row {
                        OutlinedButton(onClick = {
                            com.aicompanion.shell.ShizukuBridge.requestPermission { granted ->
                                shizukuGranted = granted
                                shizukuStatus = com.aicompanion.shell.ShizukuBridge.statusText()
                                if (granted) capsReloadKey++
                            }
                        }) { Text(if (shizukuGranted) "已授权" else "授权 Shizuku") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://shizuku.rikka.app/")
                                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }) { Text("安装 / 了解") }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "一次性操作：装 Shizuku → 用「无线调试」启动它的服务 → 回来点授权。" +
                            "之后 input / settings / screencap / pm 这些 ADB 命令就能直接跑，不需要 root。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    Text("自建 ADB 通道（不依赖 Shizuku）", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "应用自己当 adb 客户端连本机 adbd，身份同样是 shell(2000)：" +
                            "settings / pm / input / screencap 都能直接跑，不需要装任何第三方 App。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "状态：" + when {
                            adbState.connected -> "已连接 · " + adbState.uid
                            adbState.error.isNotBlank() -> "未连接 · " + adbState.error
                            else -> "未连接"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (adbState.connected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = adbPort,
                        onValueChange = { adbPort = it.filter { c -> c.isDigit() }.take(5) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("adbd 端口（默认 5555）") },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row {
                        OutlinedButton(onClick = {
                            adbScanMsg = "扫描中…"
                            com.aicompanion.adb.AdbDiscovery.discoverOwn(
                                context, com.aicompanion.adb.AdbDiscovery.SERVICE_CONNECT, 5000
                            ) { own, others ->
                                adbScanMsg = when {
                                    own.isNotEmpty() -> {
                                        // 无线调试端口只认 127.0.0.1，避免连到别人的设备
                                        adbHost = "127.0.0.1"
                                        adbPort = own.first().port.toString()
                                        "本机无线调试端口 ${own.first().port}（注意：该端口是 TLS + 配对，本客户端暂不支持）"
                                    }
                                    others.isNotEmpty() ->
                                        "只扫到局域网里其他设备（${others.joinToString { it.toString() }}），已忽略"
                                    else -> "没扫到：请先在开发者选项里打开「无线调试」"
                                }
                            }
                        }) { Text("扫描本机端口") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(
                                        android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
                                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }) { Text("打开无线调试") }
                    }
                    if (adbScanMsg.isNotBlank()) {
                        Text(
                            adbScanMsg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(10.dp))
                    Text("配对码配对（Android 11+，全程不用电脑）", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "开发者选项 → 无线调试 → 使用配对码配对设备。屏幕上会出现 6 位数字，" +
                            "填进来点「开始配对」即可；端口会自动识别，识别不到时按「配对码:端口」写。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = pairCode,
                        onValueChange = { s ->
                            pairCode = s.filter { c -> c.isDigit() || c == ':' }.take(12)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("6 位配对码（可写成 配对码:端口）") },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Button(
                            enabled = !pairBusy && pairCode.isNotBlank(),
                            onClick = {
                                val spec = pairCode.trim()
                                val code = spec.substringBefore(':')
                                val explicitPort = spec.substringAfter(':', "")
                                    .toIntOrNull() ?: 0
                                pairBusy = true
                                pairOk = false
                                pairMsg = "配对中…（保持配对窗口不要关）"
                                scope.launch {
                                    val r = withContext(Dispatchers.IO) {
                                        com.aicompanion.adb.AdbPairing.pair(
                                            context, "127.0.0.1", explicitPort, code
                                        )
                                    }
                                    pairBusy = false
                                    pairOk = r.ok
                                    pairMsg = if (r.ok) {
                                        adbPort = "5555"
                                        "配对成功：系统已永久信任本应用，重启后不需要再配。"
                                    } else {
                                        "配对失败：${r.message}"
                                    }
                                }
                            }
                        ) { Text(if (pairBusy) "配对中…" else "开始配对") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(enabled = !pairBusy, onClick = {
                            pairCode = ""
                            pairMsg = ""
                            pairOk = false
                        }) { Text("清空") }
                    }
                    if (pairMsg.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            pairMsg,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (pairOk) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "原理：TLS 1.3 建通道 → 导出密钥 → SPAKE2 用配对码换出共享密钥 → " +
                            "AES-128-GCM 交换双方公钥。成功后本应用公钥会被写进系统的信任列表，" +
                            "和「电脑已授权」是同一份名单。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row {
                        OutlinedButton(
                            enabled = !adbBusy,
                            onClick = {
                                adbBusy = true
                                scope.launch {
                                    adbState = withContext(Dispatchers.IO) {
                                        com.aicompanion.adb.AdbSelf.connect(
                                            context, adbHost, adbPort.toIntOrNull() ?: 5555
                                        )
                                    }
                                    adbBusy = false
                                }
                            }
                        ) { Text(if (adbBusy) "连接中…" else "连接并测试") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            enabled = !adbBusy,
                            onClick = {
                                adbBusy = true
                                adbScanMsg = "自动连接中…"
                                scope.launch {
                                    // 零操作连接：端口自己找（缓存→邻近→全量扫），TLS 自动升级
                                    val st = withContext(Dispatchers.IO) {
                                        com.aicompanion.adb.AdbSelf.autoConnect(context) { msg ->
                                            adbScanMsg = msg
                                        }
                                    }
                                    adbState = st
                                    adbPort = if (st.connected) adbPort else adbPort
                                    adbBusy = false
                                }
                            }
                        ) { Text(if (adbBusy) "连接中…" else "一键连接") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = {
                            com.aicompanion.adb.AdbSelf.disconnect()
                            adbState = com.aicompanion.adb.AdbSelf.state
                        }) { Text("断开") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = {
                            val line = com.aicompanion.adb.AdbSelf.keyLine(context)
                            runCatching {
                                val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                    as android.content.ClipboardManager
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("adb pubkey", line))
                                com.aicompanion.adb.AdbSelf.exportPubkey(context)
                            }
                        }) { Text("复制公钥") }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "两条路：①「配对码配对」+「一键连接」（Android 11+，不用电脑，一次永久；" +
                            "连上后身份就是 shell(2000)，settings/screencap/input 全部可用）；" +
                            "② 更老的系统/受限机型 → 连一次电脑跑 tools/adb_elevate.bat，它会顺手 adb tcpip 5555。" +
                            "「连接并测试」走的是明文 adb 通道（默认 5555），无线调试那个端口是 TLS 包裹的，" +
                            "两者不能混用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ===== App 分类自定义 =====
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("App 分类自定义", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "为指定 App 自定义类别（如 social/video/shopping），覆盖内置识别",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))

                    if (categoryOverrides.isEmpty()) {
                        Text(
                            "暂无自定义分类",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        categoryOverrides.forEach { (pkg, category) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(pkg, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "类别: $category",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = {
                                    AppCategoryClassifier.removeUserOverride(pkg)
                                    categoryOverrides = AppCategoryClassifier.getAllUserOverrides()
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "删除")
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPkgInput,
                        onValueChange = { newPkgInput = it },
                        label = { Text("包名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = newCategoryInput,
                        onValueChange = { newCategoryInput = it },
                        label = { Text("类别（如 social/video）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val pkg = newPkgInput.trim()
                            val cat = newCategoryInput.trim()
                            if (pkg.isNotBlank() && cat.isNotBlank()) {
                                AppCategoryClassifier.setUserOverride(pkg, cat)
                                categoryOverrides = AppCategoryClassifier.getAllUserOverrides()
                                newPkgInput = ""
                                newCategoryInput = ""
                            }
                        },
                        enabled = newPkgInput.isNotBlank() && newCategoryInput.isNotBlank()
                    ) { Text("添加") }
                }
            }
        }
    }
}

@Composable
private fun RadioButtonRow(text: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * 跳到系统的「所有文件访问权限」页面。
 * Android 11+ 用 MANAGE_EXTERNAL_STORAGE 的专属页面；老版本没有这个页面，
 * 退回应用详情页并给出提示（老版本靠 READ/WRITE_EXTERNAL_STORAGE 运行时权限即可）。
 */
/** 检查某个运行时权限是否已授予 */
private fun hasRuntimePermission(context: android.content.Context, permission: String): Boolean =
    androidx.core.content.ContextCompat.checkSelfPermission(context, permission) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED

private fun openAllFilesAccessSettings(context: android.content.Context) {
    try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                android.net.Uri.parse("package:${context.packageName}")
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:${context.packageName}")
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    } catch (e: Exception) {
        // 部分 ROM 没有这个页面，退回应用详情设置
        try {
            context.startActivity(
                android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:${context.packageName}")
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            android.widget.Toast.makeText(
                context,
                "无法打开权限页面，请手动到「设置 → 应用 → 特殊应用权限 → 所有文件访问权限」开启",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
}
