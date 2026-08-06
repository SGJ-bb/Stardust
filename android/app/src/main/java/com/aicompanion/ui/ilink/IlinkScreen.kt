package com.aicompanion.ui.ilink

import android.app.ActivityManager
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicompanion.ilink.IlinkAuthManager
import com.aicompanion.ilink.IlinkPollingService
import com.aicompanion.ilink.QrcodeStatus
import com.aicompanion.theme.StradustTheme
import com.aicompanion.ui.components.ButtonVariant
import com.aicompanion.ui.components.StradustButton
import com.aicompanion.ui.components.StradustCard
import com.aicompanion.ui.components.StradustInput
import com.aicompanion.ui.components.StradustTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val DEFAULT_BASE_URL = "https://ilinkai.weixin.qq.com"

/**
 * 检查 IlinkPollingService 是否正在运行。
 *
 * IlinkPollingService 未对外暴露静态 isRunning 字段，
 * 这里通过 ActivityManager.getRunningServices 查询本应用的服务状态。
 * 注意：getRunningServices 自 API 26 起仅返回调用方自身应用的服务，
 * 用于自查仍有效。
 */
private fun isPollingServiceRunning(context: Context): Boolean {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
    @Suppress("DEPRECATION")
    val services = am.getRunningServices(50) ?: return false
    return services.any { it.service.className == IlinkPollingService::class.java.name }
}

/**
 * 微信 iLink 绑定界面
 *
 * 功能：
 * - 绑定状态卡片（已绑定/未绑定）
 * - 已绑定时显示 Bot ID / User ID / Base URL
 * - 监听启停控制（开始/停止监听 + 状态指示器）
 * - 绑定按钮（输入 Bot Token + Bot ID → 保存）
 * - 解绑按钮（清除绑定）
 */
@Composable
fun IlinkScreen(onBackClick: () -> Unit = {}) {
    val context = LocalContext.current
    val manager = remember { IlinkAuthManager(context) }
    var refreshTick by remember { mutableStateOf(0) }
    var tokenInput by remember { mutableStateOf("") }
    var botIdInput by remember { mutableStateOf("") }

    // 监听服务运行状态
    var isListening by remember { mutableStateOf(false) }

    val isBound by produceState(false, refreshTick) {
        value = withContext(Dispatchers.IO) { manager.isBound }
    }
    val botToken by produceState("", refreshTick) {
        value = withContext(Dispatchers.IO) { manager.botToken }
    }
    val ilinkBotId by produceState("", refreshTick) {
        value = withContext(Dispatchers.IO) { manager.ilinkBotId }
    }
    val baseUrl by produceState("", refreshTick) {
        value = withContext(Dispatchers.IO) { manager.baseUrl }
    }
    val ilinkUserId by produceState("", refreshTick) {
        value = withContext(Dispatchers.IO) { manager.ilinkUserId }
    }

    // 定期轮询监听服务状态（服务可能因会话过期等自行停止）
    LaunchedEffect(Unit) {
        while (true) {
            isListening = isPollingServiceRunning(context)
            delay(1500)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(StradustTheme.colors.background)) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { StradustTopBar(title = "微信 iLink 绑定", onBackClick = onBackClick) }
            item { Spacer(Modifier.height(16.dp)) }

            // 绑定状态卡片
            item {
                StradustCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isBound) StradustTheme.colors.tertiary
                                    else StradustTheme.colors.surfaceContainerHigh,
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = if (isBound) Icons.Default.Check else Icons.Default.LinkOff,
                                contentDescription = null,
                                tint = if (isBound) StradustTheme.colors.onTertiary
                                else StradustTheme.colors.textSecondary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isBound) "已绑定" else "未绑定",
                                color = StradustTheme.colors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                            )
                            Text(
                                text = if (isBound) "微信 iLink 通道已连接" else "请输入 Bot 信息完成绑定",
                                color = StradustTheme.colors.textMuted,
                                fontSize = 12.sp,
                            )
                        }
                    }

                    if (isBound) {
                        Spacer(Modifier.height(16.dp))
                        InfoRow(label = "Bot ID", value = ilinkBotId.ifBlank { "—" })
                        Spacer(Modifier.height(8.dp))
                        InfoRow(label = "User ID", value = ilinkUserId.ifBlank { "—" })
                        Spacer(Modifier.height(8.dp))
                        InfoRow(label = "Base URL", value = baseUrl)
                        Spacer(Modifier.height(8.dp))
                        InfoRow(label = "Bot Token", value = maskToken(botToken))
                    }
                }
            }

            // 监听启停控制卡片（仅在已绑定时显示）
            if (isBound) {
                item { Spacer(Modifier.height(16.dp)) }
                item {
                    StradustCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // 监听状态指示圆点
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isListening) StradustTheme.colors.tertiary
                                        else StradustTheme.colors.textMuted,
                                    ),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isListening) "监听中" else "已停止",
                                color = if (isListening) StradustTheme.colors.tertiary
                                else StradustTheme.colors.textMuted,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = if (isListening) "正在接收微信消息" else "点击下方按钮开始监听",
                                color = StradustTheme.colors.textMuted,
                                fontSize = 11.sp,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        if (isListening) {
                            // 监听中：显示停止按钮
                            StradustButton(
                                text = "停止监听",
                                onClick = { IlinkPollingService.stop(context) },
                                variant = ButtonVariant.OUTLINED,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            // 未监听：显示开始按钮
                            StradustButton(
                                text = "开始监听",
                                onClick = { IlinkPollingService.start(context) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }

            // 绑定 / 解绑操作区
            item {
                StradustCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(
                        text = if (isBound) "重新绑定" else "绑定 Bot",
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "输入微信 iLink Bot Token 与 Bot ID 完成绑定",
                        color = StradustTheme.colors.textMuted,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    StradustInput(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        hint = "Bot Token",
                    )
                    Spacer(Modifier.height(8.dp))
                    StradustInput(
                        value = botIdInput,
                        onValueChange = { botIdInput = it },
                        hint = "iLink Bot ID",
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StradustButton(
                            text = "保存绑定",
                            onClick = {
                                if (tokenInput.isNotBlank() && botIdInput.isNotBlank()) {
                                    val confirmed = QrcodeStatus.Confirmed(
                                        botToken = tokenInput.trim(),
                                        ilinkBotId = botIdInput.trim(),
                                        baseUrl = DEFAULT_BASE_URL,
                                        ilinkUserId = "",
                                    )
                                    manager.saveBinding(confirmed)
                                    tokenInput = ""
                                    botIdInput = ""
                                    refreshTick++
                                }
                            },
                            enabled = tokenInput.isNotBlank() && botIdInput.isNotBlank(),
                            modifier = Modifier.weight(1f),
                        )
                        if (isBound) {
                            StradustButton(
                                text = "解绑",
                                onClick = {
                                    manager.clearBinding()
                                    tokenInput = ""
                                    botIdInput = ""
                                    refreshTick++
                                },
                                variant = ButtonVariant.OUTLINED,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                StradustTheme.colors.surfaceContainerLow.copy(alpha = 0.4f),
                RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = StradustTheme.colors.textMuted,
            fontSize = 12.sp,
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

private fun maskToken(token: String): String {
    if (token.length <= 8) return "••••"
    return token.take(4) + "••••" + token.takeLast(4)
}
