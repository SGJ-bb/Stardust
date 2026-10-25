package com.aicompanion.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicompanion.agent.AgentBus

/**
 * Agent 活动条：贴在输入框上方的一行状态。
 *
 * 为什么做成"一行 + 可展开"而不是把工具调用塞进聊天流：
 * 聊天流里是 AI 说的话，工具调用是"过程"；混在一起会把对话冲散，
 * 而且历史记录、导出、TTS 都要额外判类型。独立一条更干净。
 *
 * 三种形态：
 * - 空闲：不占位（高度 0）
 * - 执行中：转圈 + 一句话（如「点按屏幕 (540, 1200)」），点开看历史
 * - 待确认：警示色 + 说明 + 「允许 / 不允许」两个按钮（默认不放行，超时也按拒绝）
 */
@Composable
fun AgentActivityBar(modifier: Modifier = Modifier) {
    val current by AgentBus.current.collectAsState()
    val events by AgentBus.events.collectAsState()
    val pending by AgentBus.pending.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    // 显示时机：正在执行 / 等待确认 / 最近一次失败（留个痕迹给用户看）/ 用户手动展开
    val last = events.lastOrNull()
    val idleFail = current == null && pending == null && last != null && last.isBad
    if (current == null && pending == null && !idleFail && !expanded) {
        return
    }
    // 没有执行中时，用最近那条失败事件顶替状态行
    val shown = current ?: if (idleFail) last else null

    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
    ) {
        // 待确认：最高优先级，必须盖住"执行中"
        pending?.let { p ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(scheme.errorContainer, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "需要你确认",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = scheme.onErrorContainer,
                    )
                    Text(
                        p.summary,
                        fontSize = 13.sp,
                        color = scheme.onErrorContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (p.reason.isNotBlank()) {
                        Text(
                            p.reason,
                            fontSize = 11.sp,
                            color = scheme.onErrorContainer.copy(alpha = 0.75f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { AgentBus.answerConfirm(p.id, false) }) {
                    Text("不允许", fontSize = 12.sp, color = scheme.onErrorContainer)
                }
                TextButton(onClick = { AgentBus.answerConfirm(p.id, true) }) {
                    Text("允许", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onErrorContainer)
                }
            }
            Spacer(Modifier.size(2.dp))
        }

        // 执行中 / 最近一条
        shown?.let { ev ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .background(scheme.surfaceVariant.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (ev.isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(11.dp),
                        strokeWidth = 1.5.dp,
                        color = scheme.primary,
                    )
                } else {
                    Text(if (ev.status == AgentBus.Status.OK) "✓" else "!", fontSize = 11.sp,
                        color = if (ev.status == AgentBus.Status.OK) scheme.primary else scheme.error)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    ev.summary,
                    fontSize = 12.sp,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (ev.costMs > 0) {
                    Text("${ev.costMs}ms", fontSize = 11.sp, color = scheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
                Text(if (expanded) "收起" else "详情", fontSize = 11.sp,
                    color = scheme.primary.copy(alpha = 0.9f), modifier = Modifier.padding(start = 8.dp))
            }
        }

        // 展开的历史：倒序，最近在上
        AnimatedVisibility(visible = expanded) {
            val shown = events.takeLast(12).reversed()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .background(scheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                    .padding(vertical = 6.dp),
            ) {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(shown) { e ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                when (e.status) {
                                    AgentBus.Status.OK -> "✓"
                                    AgentBus.Status.FAIL -> "✕"
                                    AgentBus.Status.DENIED -> "⛔"
                                    AgentBus.Status.WAIT_CONFIRM -> "?"
                                    AgentBus.Status.RUNNING -> "…"
                                },
                                fontSize = 11.sp,
                                color = when (e.status) {
                                    AgentBus.Status.OK -> scheme.primary
                                    AgentBus.Status.RUNNING -> scheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    else -> scheme.error
                                },
                                modifier = Modifier.width(14.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    e.summary,
                                    fontSize = 11.sp,
                                    color = scheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (e.detail.isNotBlank() && e.status != AgentBus.Status.RUNNING) {
                                    Text(
                                        e.detail,
                                        fontSize = 11.sp,
                                        color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            if (e.costMs > 0) {
                                Text("${e.costMs}ms", fontSize = 9.sp,
                                    color = scheme.onSurfaceVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { AgentBus.clear(); expanded = false }) {
                        Text("清空记录", fontSize = 11.sp, color = scheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
