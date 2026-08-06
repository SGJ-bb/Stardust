package com.aicompanion.ui.groupchat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicompanion.theme.StradustTheme
import com.aicompanion.ui.components.StradustCard
import com.aicompanion.ui.components.StradustTopBar
import com.aicompanion.ui.components.WallpaperBackground
import com.aicompanion.ui.animations.clickScale

/**
 * 群聊列表信息数据类（用于 Compose 层展示）
 * 从 GroupChat 转换而来，避免直接依赖旧代码中的 Context 相关类
 */
data class GroupChatInfo(
    val id: String,
    val name: String,
    val lastMessage: String,
    val memberCount: Int,
    val timestamp: String,
)

/**
 * 群聊列表页面（Compose 版本）
 *
 * 替代旧的 GroupChatListActivity (AppCompatActivity + RecyclerView)
 * 使用 Jetpack Compose + Material3 实现，遵循项目统一风格
 *
 * @param groups 群聊列表数据（由 NavHost 通过 AppHost 接口获取）
 * @param onBackClick 返回按钮点击回调
 * @param onGroupClick 群聊项点击回调，传入群组 ID
 * @param onCreateGroup 创建新群按钮点击回调，传入群名称（外部负责实际创建与导航）
 * @param onDeleteGroup 删除群聊回调，传入群组 ID
 */
@Composable
fun GroupChatListScreen(
    groups: List<GroupChatInfo> = emptyList(),
    onBackClick: () -> Unit = {},
    onGroupClick: (groupId: String) -> Unit = {},
    onCreateGroup: (name: String) -> Unit = {},
    onDeleteGroup: (groupId: String) -> Unit = {},
    /** 壁纸图片路径（本地文件路径或URI） */
    wallpaperPath: String? = null,
) {
    val colors = StradustTheme.colors
    // 创建群聊对话框显隐 + 输入文本
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var createName by rememberSaveable { mutableStateOf("") }
    // 删除确认对话框
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    var pendingDeleteName by remember { mutableStateOf("") }

    WallpaperBackground(wallpaperPath = wallpaperPath) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部栏：返回按钮 + 标题"群聊"
            StradustTopBar(
                title = "群聊",
                onBackClick = onBackClick,
            )

            // 群聊列表内容区
            if (groups.isEmpty()) {
                // 空状态提示
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "还没有群聊，去创建一个吧",
                        color = colors.textSecondary,
                        fontSize = 16.sp,
                    )
                }
            } else {
                // 群聊列表
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
                ) {
                    items(
                        items = groups,
                        key = { it.id },
                    ) { group ->
                        GroupChatItem(
                            group = group,
                            onClick = { onGroupClick(group.id) },
                            onDelete = {
                                pendingDeleteId = group.id
                                pendingDeleteName = group.name
                            },
                        )
                    }
                }
            }
        }

        // 创建群 FAB 按钮（右下角浮动按钮）
        FloatingActionButton(
            onClick = {
                createName = ""
                showCreateDialog = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
        ) {
            Icon(Icons.Default.Add, contentDescription = "创建群聊")
        }
    }

    // ===== 创建群聊对话框 =====
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("创建群聊") },
            text = {
                OutlinedTextField(
                    value = createName,
                    onValueChange = { createName = it },
                    label = { Text("群聊名称") },
                    placeholder = { Text("例如：好友聚会") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        val name = createName.trim()
                        if (name.isNotEmpty()) {
                            onCreateGroup(name)
                            showCreateDialog = false
                            createName = ""
                        }
                    }),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = createName.trim()
                        if (name.isNotEmpty()) {
                            onCreateGroup(name)
                            showCreateDialog = false
                            createName = ""
                        }
                    },
                    enabled = createName.isNotBlank(),
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("取消") }
            },
        )
    }

    // ===== 删除群聊确认对话框 =====
    pendingDeleteId?.let { deleteId ->
        AlertDialog(
            onDismissRequest = {
                pendingDeleteId = null
                pendingDeleteName = ""
            },
            title = { Text("删除群聊") },
            text = {
                Text("确定要删除「${pendingDeleteName}」吗？所有消息将无法恢复。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteGroup(deleteId)
                        pendingDeleteId = null
                        pendingDeleteName = ""
                    },
                ) { Text("删除", color = colors.error) }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingDeleteId = null
                    pendingDeleteName = ""
                }) { Text("取消") }
            },
        )
    }
}

/**
 * 单个群聊列表项组件
 *
 * 使用 StradustCard 包裹，显示：
 * - 群名称（粗体）
 * - 最后消息预览（灰色次要文本）
 * - 成员数量
 * - 时间戳
 * - 删除按钮
 */
@Composable
private fun GroupChatItem(
    group: GroupChatInfo,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = StradustTheme.colors

    StradustCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickScale(onClick = onClick),
        cornerRadius = 12.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左侧：群名称 + 最后消息
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
            ) {
                // 群名称
                Text(
                    text = group.name,
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                // 最后消息预览
                Text(
                    text = group.lastMessage.ifEmpty { "暂无消息" },
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                // 成员数 + 时间戳
                Text(
                    text = "${group.memberCount}人 · ${group.timestamp}",
                    color = colors.textSecondary.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                )
            }

            // 右侧：删除按钮
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除群聊",
                    tint = colors.error,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
