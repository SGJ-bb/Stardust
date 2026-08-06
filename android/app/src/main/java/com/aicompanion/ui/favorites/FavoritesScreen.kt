package com.aicompanion.ui.favorites

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicompanion.theme.StradustTheme
import com.aicompanion.ui.ChatMessage
import com.aicompanion.ui.FavoriteManager
import com.aicompanion.ui.components.ButtonSize
import com.aicompanion.ui.components.ButtonVariant
import com.aicompanion.ui.components.StradustButton
import com.aicompanion.ui.components.StradustCard
import com.aicompanion.ui.components.StradustTopBar
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 收藏列表页面
 *
 * 展示当前角色被收藏的消息，支持取消收藏和图片缩略图显示。
 *
 * @param personaId 角色 ID
 * @param onBackClick 返回回调
 */
@Composable
fun FavoritesScreen(
    personaId: String = "default",
    onBackClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val favoriteManager = remember(personaId) { FavoriteManager(context, personaId) }
    var refreshTick by remember { mutableStateOf(0) }
    val favorites by produceState(initialValue = emptyList<ChatMessage>(), key1 = refreshTick, key2 = personaId) {
        value = withContext(Dispatchers.IO) { favoriteManager.getAll() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StradustTheme.colors.background),
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { StradustTopBar(title = "收藏列表", onBackClick = onBackClick) }

            item { Spacer(Modifier.height(16.dp)) }

            if (favorites.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 80.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Bookmark,
                                contentDescription = null,
                                tint = StradustTheme.colors.textMuted,
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "还没有收藏的消息",
                                color = StradustTheme.colors.textMuted,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            } else {
                items(favorites, key = { it.id }, contentType = { if (it.isUser) "user" else "ai" }) { message ->
                    FavoriteItem(
                        message = message,
                        onRemove = {
                            favoriteManager.removeFavorite(message.id)
                            refreshTick++
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

/** 单条收藏消息卡片 */
@Composable
private fun FavoriteItem(
    message: ChatMessage,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StradustCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            // 用户 / AI 标识头像
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (message.isUser) StradustTheme.colors.primary
                        else StradustTheme.colors.tertiary
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (message.isUser) Icons.Default.Person else Icons.Default.SmartToy,
                    contentDescription = if (message.isUser) "我" else "AI",
                    tint = StradustTheme.colors.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (message.isUser) "我" else "AI",
                        color = StradustTheme.colors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = message.time,
                        color = StradustTheme.colors.textMuted,
                        fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = message.text,
                    color = StradustTheme.colors.textPrimary,
                    fontSize = 14.sp,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
                // 图片缩略图
                if (message.generatedImagePath != null || message.stickerPath != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        message.generatedImagePath?.let { path ->
                            ImageThumbnail(path = path, size = 72.dp)
                        }
                        message.stickerPath?.let { path ->
                            ImageThumbnail(path = path, size = 72.dp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                StradustButton(
                    text = "取消收藏",
                    onClick = onRemove,
                    variant = ButtonVariant.OUTLINED,
                    size = ButtonSize.SMALL,
                )
            }
        }
    }
}

/** 本地图片缩略图（带降采样防止 OOM） */
@Composable
private fun ImageThumbnail(path: String, size: Dp) {
    val bitmap = remember(path) { loadSampledBitmap(path, maxSize = 256) }
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(StradustTheme.colors.surfaceContainerLow),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "图片",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = "图片",
                color = StradustTheme.colors.textMuted,
                fontSize = 11.sp,
            )
        }
    }
}

/** 从文件路径加载降采样 Bitmap */
private fun loadSampledBitmap(path: String, maxSize: Int): android.graphics.Bitmap? {
    return try {
        val file = File(path)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxSize || bounds.outHeight / sampleSize > maxSize) {
            sampleSize *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        BitmapFactory.decodeFile(path, opts)
    } catch (_: Exception) {
        null
    }
}
