package com.aicompanion.ui.album

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.aicompanion.theme.StradustTheme
import com.aicompanion.ui.animations.pressedScale
import com.aicompanion.ui.components.StradustCard
import com.aicompanion.ui.components.StradustTopBar
import com.aicompanion.ui.components.WallpaperBackground

/** 相册照片数据模型（含真实图片路径） */
data class PhotoItem(
    val id: Int,
    val date: String,
    val description: String,
    /** 预览色索引（映射到主题色） */
    val previewColorIndex: Int = 0,
    /** 真实图片文件路径（空字符串表示无真实图片） */
    val imagePath: String = "",
)

/** 从 MemorialAlbumManager 转换而来的相册数据 */
data class AlbumPhotoData(
    val id: Int,
    val date: String,
    val description: String,
    val imagePath: String,
)

/** 演示数据 */
private fun samplePhotos(): List<PhotoItem> = listOf(
    PhotoItem(1, "2024.06.20", "和 AI 的第一次合影", 0),
    PhotoItem(2, "2024.06.18", "花园里的下午茶", 1),
    PhotoItem(3, "2024.06.15", "星空下的对话", 2),
    PhotoItem(4, "2024.06.10", "生日快乐 🎂", 3),
    PhotoItem(5, "2024.06.05", "雨天读书时光", 0),
    PhotoItem(6, "2024.05.28", "一起看日落", 1),
    PhotoItem(7, "2024.05.20", "520 特别纪念", 2),
    PhotoItem(8, "2024.05.15", "新皮肤解锁", 3),
    PhotoItem(9, "2024.05.10", "第一次视频通话", 0),
)

/** 根据索引返回主题色（用于照片预览底色） */
@Composable
private fun themeColorByIndex(index: Int): Color {
    val colors = listOf(
        StradustTheme.colors.primary,
        StradustTheme.colors.tertiary,
        StradustTheme.colors.secondary,
        StradustTheme.colors.error,
    )
    return colors[index % colors.size].copy(alpha = 0.3f)
}

@Composable
fun AlbumScreen(
    /** 从 MemorialAlbumManager 读取的真实照片数据列表（默认空则使用演示数据） */
    albumPhotos: List<AlbumPhotoData> = emptyList(),
    /** 顶部返回按钮回调（null 则不显示返回按钮） */
    onBackClick: (() -> Unit)? = null,
    /** 点击"生成图片"FAB 的回调（跳转到图片生成页） */
    onGenImageClick: () -> Unit = {},
    /** 壁纸图片路径（本地文件路径或URI） */
    wallpaperPath: String? = null,
) {
    // 使用真实数据，无数据时显示空状态（不再回退到演示数据，避免空状态不可达）
    val photos = remember(albumPhotos) {
        albumPhotos.mapIndexed { index, data ->
            PhotoItem(
                id = data.id,
                date = data.date,
                description = data.description,
                previewColorIndex = index % 4,
                imagePath = data.imagePath,
            )
        }
    }
    var previewPhoto by remember { mutableStateOf<PhotoItem?>(null) }

    WallpaperBackground(wallpaperPath = wallpaperPath) {
        Column {
            StradustTopBar(title = "纪念相册", onBackClick = onBackClick)

            // 统计信息栏：左 "共 X 张照片"(secondary) + 右 "Y 个回忆"(muted)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "共 ${photos.size} 张照片",
                    color = StradustTheme.colors.textSecondary,
                    fontSize = 13.sp,
                )
                Text(
                    text = "${photos.size} 个回忆",
                    color = StradustTheme.colors.textMuted,
                    fontSize = 13.sp,
                )
            }

            if (photos.isEmpty()) {
                // 空状态
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = StradustTheme.colors.textMuted,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "还没有难忘时刻哦",
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "和 AI 的美好瞬间会在这里展示 📸",
                        color = StradustTheme.colors.textMuted,
                        fontSize = 14.sp,
                    )
                }
            } else {
                // 照片网格：LazyVerticalGrid(3列), aspectRatio(1f), 圆角 12dp, shadow 2dp
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp,
                        vertical = 8.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(photos, key = { _, it -> it.id }) { index, photo ->
                        PhotoGridItem(
                            photo = photo,
                            onClick = { previewPhoto = photo },
                        )
                    }
                }
            }
        }

        // 大图预览弹窗 — 全屏 scrim 遮罩
        if (previewPhoto != null) {
            PhotoPreviewDialog(
                photo = previewPhoto!!,
                onDismiss = { previewPhoto = null },
            )
        }

        // 生成图片 FAB
        FloatingActionButton(
            onClick = onGenImageClick,
            containerColor = StradustTheme.colors.primary,
            contentColor = StradustTheme.colors.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = "生成图片")
        }
    }
}

/** 照片网格项：StradustCard 包裹（自带阴影/圆角/暗色边框），点击 scale(0.95) 反馈 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoGridItem(photo: PhotoItem, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val previewColor = themeColorByIndex(photo.previewColorIndex)

    StradustCard(
        modifier = Modifier
            .scale(if (isPressed) 0.95f else 1f)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            ),
        elevation = 2.dp,
        cornerRadius = 12.dp,
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .background(previewColor),
            contentAlignment = Alignment.Center,
        ) {
            if (photo.imagePath.isNotBlank()) {
                AsyncImage(
                    model = photo.imagePath,
                    contentDescription = photo.description,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            } else {
                Icon(
                    Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = StradustTheme.colors.textMuted.copy(alpha = 0.5f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        // date 标签(11sp muted)
        Text(
            text = photo.date,
            color = StradustTheme.colors.textMuted,
            fontSize = 11.sp,
        )
    }
}

/** 全屏预览 Dialog：scrim 遮罩 + 圆角16dp大图 + 关闭按钮 */
@Composable
private fun PhotoPreviewDialog(photo: PhotoItem, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(StradustTheme.colors.scrim),
            contentAlignment = Alignment.Center,
        ) {
            // 右上角关闭按钮：圆形白*10%底 + 按压缩放反馈
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .pressedScale()
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f), CircleShape),
            ) {
                // 例外：白图标在黑底上
                Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.White)
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 32.dp),
            ) {
                // 大图：圆角 16dp，优先显示真实图片
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            themeColorByIndex(photo.previewColorIndex),
                            RoundedCornerShape(16.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (photo.imagePath.isNotBlank()) {
                        AsyncImage(
                            model = photo.imagePath,
                            contentDescription = photo.description,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        )
                    } else {
                        // 例外：白图标在彩色底上
                        Icon(
                            Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Color.White.copy(alpha = 0.3f),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // date(16sp white SemiBold)
                Text(
                    text = photo.date,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
                Spacer(Modifier.height(4.dp))
                // description(14sp white*70%)
                Text(
                    text = photo.description,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                )
            }
        }
    }
}
