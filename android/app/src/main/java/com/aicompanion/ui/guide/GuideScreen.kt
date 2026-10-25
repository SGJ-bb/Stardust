/** 功能引导中心：入口列表 + 分步互动教程（动画 + 直达系统设置）。 */
package com.aicompanion.ui.guide

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicompanion.settings.SettingsManager
import com.aicompanion.ui.components.StradustCard
import com.aicompanion.ui.components.StradustTopBar
import com.aicompanion.theme.StradustTheme

@Composable
fun GuideScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit = {},
) {
    val colors = StradustTheme.colors
    val context = LocalContext.current
    val sm = remember { SettingsManager(context) }
    var doneKeys by remember { mutableStateOf(sm.guideDone()) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var stepIndex by rememberSaveable { mutableStateOf(0) }

    val topic = GuideCatalog.topic(openId)

    Column(Modifier.fillMaxSize().background(colors.background)) {
        StradustTopBar(
            title = topic?.title ?: "功能引导",
            subtitle = topic?.subtitle ?: "每个功能，三步看懂",
            onBackClick = {
                if (topic != null) { openId = null; stepIndex = 0 } else onBack()
            },
        )

        if (topic == null) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(GuideCatalog.topics) { t ->
                    val done = t.steps.indices.count { doneKeys.contains(key(t.id, it)) }
                    StradustCard(
                        modifier = Modifier.fillMaxWidth().clickable { openId = t.id; stepIndex = 0 },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape)
                                    .background(colors.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) { Text(t.emoji, fontSize = 18.sp) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.title, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                                Text(
                                    t.subtitle,
                                    fontSize = 12.sp,
                                    color = colors.textSecondary,
                                )
                            }
                            Text(
                                if (done >= t.steps.size) "已完成" else "$done/${t.steps.size}",
                                fontSize = 12.sp,
                                color = if (done >= t.steps.size) colors.primary else colors.textSecondary,
                            )
                        }
                    }
                }
                item {
                    Text(
                        "这些教程只讲“为什么”和“怎么点”，不涉及账号数据；" +
                            "任何一步卡住，都可以在设置里找到对应开关。",
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        } else {
            val s = topic.steps[stepIndex.coerceIn(0, topic.steps.lastIndex)]
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            ) {
                // 适用范围提示（只有带兼容性说明的章节才显示）
                if (topic.compat.isNotBlank()) {
                    val supported = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.primary.copy(alpha = 0.08f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            if (supported) "✓" else "!",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                if (supported) "你的系统可以使用本功能"
                                else "你的系统可能不支持本功能",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                topic.compat,
                                fontSize = 11.5.sp,
                                color = colors.textSecondary,
                                lineHeight = 17.sp,
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }

                // 进度点
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    topic.steps.indices.forEach { i ->
                        Box(
                            Modifier
                                .height(4.dp)
                                .weight(1f)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (i <= stepIndex) colors.primary
                                    else colors.surfaceContainerHigh
                                )
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))

                // 动画舞台
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surfaceContainerLow),
                    contentAlignment = Alignment.Center,
                ) {
                    GuideAnimation(s.anim, colors.primary, Modifier.padding(16.dp))
                }

                Spacer(Modifier.height(16.dp))
                Text(s.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                Spacer(Modifier.height(8.dp))
                Text(s.body, fontSize = 14.sp, color = colors.textPrimary.copy(alpha = 0.86f), lineHeight = 21.sp)
                if (s.tip.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "提示：${s.tip}",
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        lineHeight = 18.sp,
                    )
                }
                if (s.action != GuideAction.NONE) {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { runAction(context, s.action, onNavigate) },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    ) { Text(s.action.label) }
                }

                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (stepIndex > 0) {
                        OutlinedButton(onClick = { stepIndex-- }, modifier = Modifier.weight(1f)) {
                            Text("上一步")
                        }
                    }
                    Button(
                        onClick = {
                            sm.markGuideDone(key(topic.id, stepIndex))
                            doneKeys = sm.guideDone()
                            if (stepIndex < topic.steps.lastIndex) stepIndex++
                            else { openId = null; stepIndex = 0 }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    ) {
                        Text(if (stepIndex < topic.steps.lastIndex) "懂了，下一步" else "完成")
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "第 ${stepIndex + 1} / ${topic.steps.size} 步",
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun key(topicId: String, step: Int) = "$topicId#$step"

/** 把教程里的按钮变成真的系统页面 */
private fun runAction(context: Context, action: GuideAction, onNavigate: (String) -> Unit) {
    fun open(action: String, data: Uri? = null) {
        runCatching {
            context.startActivity(
                Intent(action).apply {
                    if (data != null) this.data = data
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }
    val pkgUri = Uri.parse("package:${context.packageName}")
    when (action) {
        GuideAction.A11Y_SETTINGS -> open(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        GuideAction.NOTIFICATION_ACCESS -> open(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        GuideAction.USAGE_ACCESS -> open(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        GuideAction.OVERLAY_PERMISSION -> open(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri)
        GuideAction.BATTERY -> open(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        GuideAction.ALL_FILES -> open(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkgUri)
        GuideAction.WAKE_SETTINGS -> onNavigate("settings")
        GuideAction.ADB_PANEL -> onNavigate("accessibility_settings")
        GuideAction.WIRELESS_DEBUG -> {
            // 直接落到「开发者选项」；无线调试是它下面的子项（各 ROM 都走这个入口）
            open(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        }
        GuideAction.NONE -> Unit
    }
}
