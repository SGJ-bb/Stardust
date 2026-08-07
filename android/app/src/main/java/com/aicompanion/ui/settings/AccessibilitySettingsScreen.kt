/** 无障碍设置页: 服务状态/AI操作权限/功能开关/操作参数/App分类自定义 */
package com.aicompanion.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aicompanion.screen.AppCategoryClassifier
import com.aicompanion.screen.ScreenRecognitionService
import com.aicompanion.settings.SettingsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessibilitySettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsManager(context) }
    val scrollState = rememberScrollState()

    // 本地状态：与 SettingsManager 双向绑定
    var operationMode by remember { mutableStateOf(settings.aiOperationMode) }
    var cooldownMs by remember { mutableStateOf(settings.aiOperationCooldownMs.toFloat()) }
    var maxRounds by remember { mutableStateOf(settings.aiOperationMaxRounds.toFloat()) }
    var abnormalDetection by remember { mutableStateOf(settings.abnormalDetectionEnabled) }
    var longPress by remember { mutableStateOf(settings.longPressEnabled) }
    // 服务状态：进入页面时读取一次（跳转系统设置返回后需用户重进页面刷新）
    var serviceEnabled by remember { mutableStateOf(ScreenRecognitionService.getInstance() != null) }

    // App 分类自定义列表（进入页面时读取一次，增删后本地刷新）
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
                            // 跳转系统无障碍设置
                            val intent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            try {
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                // 部分设备无系统无障碍设置入口时忽略
                            }
                        }) { Text("去开启") }
                    }
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
                        "推荐使用「需确认后执行」模式，更安全",
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
                    SwitchRow("异常检测（支付/验证码/登录页暂停）", abnormalDetection) {
                        abnormalDetection = it
                        settings.abnormalDetectionEnabled = it
                    }
                    SwitchRow("长按手势", longPress) {
                        longPress = it
                        settings.longPressEnabled = it
                    }
                    // WebView 增强已通过无障碍配置开启，此处仅展示状态
                    Text(
                        "WebView 增强：已通过无障碍服务配置开启",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                    Text("最大操作轮次: ${maxRounds.toInt()}")
                    Slider(
                        value = maxRounds,
                        onValueChange = { maxRounds = it },
                        onValueChangeFinished = { settings.aiOperationMaxRounds = maxRounds.toInt() },
                        valueRange = 1f..50f
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
