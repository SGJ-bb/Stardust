package com.aicompanion.ui.virtualworld

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicompanion.theme.StradustTheme
import com.aicompanion.ui.components.StradustButton
import com.aicompanion.ui.components.StradustTopBar
import com.aicompanion.virtualworld.VirtualWorldManager

/**
 * 虚拟世界参考图上传页面
 *
 * 功能：
 * - 已上传图片网格（2 列），每项显示图片 + 删除按钮
 * - 上传按钮（从相册选择图片）
 * - 空状态提示
 *
 * 后端：VirtualWorldManager.config.uploadedImages / saveUploadedImage(Uri) / removeUploadedImage(path)
 *
 * @param onBackClick 返回回调
 */
@Composable
fun VwImageUploadScreen(
    onBackClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val colors = StradustTheme.colors

    val vwm = remember { VirtualWorldManager(context) }
    var refreshTick by remember { mutableIntStateOf(0) }
    val images = remember(refreshTick) { vwm.config.uploadedImages }

    // 相册选择 Launcher（ActivityResultContracts.PickVisualMedia）
    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            // 实际 API：saveUploadedImage(sourceUri: Uri): String?
            vwm.saveUploadedImage(uri)
            refreshTick++
        }
    }

    fun openPicker() {
        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        StradustTopBar(title = "世界参考图", onBackClick = onBackClick)
        HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.3f), thickness = 0.5.dp)

        if (images.isEmpty()) {
            // ===== 空状态提示 =====
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 32.dp),
                ) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        tint = colors.textDisabled,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "暂无参考图",
                        color = colors.textSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "上传场景参考图，AI 生成剧情时会参考这些图片",
                        color = colors.textMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    StradustButton(
                        text = "从相册选择",
                        onClick = { openPicker() },
                    )
                }
            }
        } else {
            // ===== 已上传图片网格（2 列） =====
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items = images, key = { it: String -> it }) { path ->
                    UploadedImageItem(
                        path = path,
                        onDelete = {
                            vwm.removeUploadedImage(path)
                            refreshTick++
                        },
                    )
                }
                // 上传按钮项
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surfaceContainerLow.copy(alpha = 0.4f))
                            .clickable { openPicker() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.AddPhotoAlternate,
                                contentDescription = "添加图片",
                                tint = colors.primary,
                                modifier = Modifier.size(28.dp),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "添加图片",
                                color = colors.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 单张已上传图片项（图片 + 删除按钮）
 */
@Composable
private fun UploadedImageItem(
    path: String,
    onDelete: () -> Unit,
) {
    val colors = StradustTheme.colors
    val bitmap = remember(path) {
        try {
            BitmapFactory.decodeFile(path)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceContainerLow),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "参考图",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Image,
                    contentDescription = null,
                    tint = colors.textDisabled,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        // 删除按钮（右上角）
        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.scrim.copy(alpha = 0.6f)),
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "删除",
                tint = colors.onPrimary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
