package com.aicompanion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.aicompanion.theme.StradustTheme

/**
 * 通用壁纸背景组件
 * @param wallpaperPath 壁纸图片路径（本地文件路径或URI）
 * @param content 前景内容（在 BoxScope 中，支持 align 等修饰符）
 */
@Composable
fun WallpaperBackground(
    wallpaperPath: String?,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = StradustTheme.colors
    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        // 壁纸层（如果有）— 使用 Crop 确保覆盖整个屏幕
        if (!wallpaperPath.isNullOrBlank()) {
            AsyncImage(
                model = wallpaperPath,
                contentDescription = "壁纸",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            // 半透明遮罩层（与聊天页面一致，确保文字可读）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.background.copy(alpha = 0.6f)),
            )
        }
        // 前景内容层（支持 BoxScope）
        content()
    }
}