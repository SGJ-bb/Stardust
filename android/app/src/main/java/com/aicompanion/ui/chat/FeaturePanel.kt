package com.aicompanion.ui.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicompanion.theme.StradustTheme

data class FeatureItem(val label: String, val index: Int, val icon: ImageVector)

/**
 * 功能面板 - ModalBottomSheet
 * 17个功能项，LazyVerticalGrid 4列布局，自适应高度
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeaturePanel(show: Boolean, onDismiss: () -> Unit, onFeatureClick: (Int) -> Unit) {
    if (!show) return

    val colors = StradustTheme.colors

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceContainerLow,
        contentColor = colors.textPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Text("功能面板", color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))

            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.5f), thickness = 1.dp, modifier = Modifier.padding(bottom = 16.dp))

            val features = remember {
                listOf(
                    FeatureItem("每日签到", 0, Icons.Default.Bookmark),
                    FeatureItem("成就殿堂", 1, Icons.Default.EmojiEvents),
                    FeatureItem("心情日记", 2, Icons.Default.EditNote),
                    FeatureItem("AI写日记", 9, Icons.Default.AutoAwesome),
                    FeatureItem("专注计时", 3, Icons.Default.Timer),
                    FeatureItem("切换皮套", 4, Icons.Default.Face),
                    FeatureItem("换壁纸", 5, Icons.Default.Image),
                    FeatureItem("运行日志", 6, Icons.Default.Description),
                    FeatureItem("操作教程", 7, Icons.Default.School),
                    FeatureItem("手机自动化", 8, Icons.Default.PhoneAndroid),
                    FeatureItem("记忆池", 10, Icons.Default.Memory),
                    FeatureItem("新会话", 11, Icons.Default.Refresh),
                    FeatureItem("表情包", 12, Icons.Default.EmojiEmotions),
                    FeatureItem("聊天记录", 14, Icons.Default.History),
                    FeatureItem("纪念相册", 15, Icons.Default.PhotoLibrary),
                    FeatureItem("清空记录", 99, Icons.Default.DeleteForever),
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(features, key = { it.index }) { feature ->
                    FeatureGridItem(feature = feature, onClick = { onFeatureClick(feature.index) })
                }
            }

            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

/** 功能面板网格单项 */
@Composable
private fun FeatureGridItem(feature: FeatureItem, onClick: () -> Unit) {
    val colors = StradustTheme.colors
    var isClicked by remember { mutableStateOf(false) }

    val clickScale by animateFloatAsState(
        targetValue = if (isClicked) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "feature_item_scale",
    )

    LaunchedEffect(isClicked) {
        if (isClicked) {
            kotlinx.coroutines.delay(100)
            isClicked = false
        }
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                onClick = { isClicked = true; onClick() },
            )
            .graphicsLayer { scaleX = clickScale; scaleY = clickScale }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(feature.icon, feature.label, tint = colors.tertiary, modifier = Modifier.size(36.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(feature.label, color = colors.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
