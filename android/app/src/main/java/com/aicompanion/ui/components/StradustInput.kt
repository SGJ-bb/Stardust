package com.aicompanion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.aicompanion.theme.StradustTheme

/**
 * 星尘输入框组件
 *
 * 特性：
 * - 全圆角（24dp）
 * - 未聚焦：surfaceContainerHigh 底色，无边框
 * - 聚焦：primary 色 1.5dp 边框 + glow 15% 透明度背景
 * - 文字色 textPrimary，hint 色 textMuted
 * - 右侧清除按钮（text 非空且聚焦时显示）
 * - 支持单行/多行
 */
@Composable
fun StradustInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "",
    maxLines: Int = 1,
    singleLine: Boolean = false,
    imeAction: ImeAction = ImeAction.Send,
    maxLength: Int? = null,
    onSend: (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val colors = StradustTheme.colors
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }

    val effectiveSingleLine = singleLine || maxLines == 1
    val shape = RoundedCornerShape(24.dp)

    BasicTextField(
        value = value,
        onValueChange = {
            val newValue = if (maxLength != null && it.length > maxLength) it.take(maxLength) else it
            onValueChange(newValue)
        },
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused },
        textStyle = LocalTextStyle.current.copy(color = colors.textPrimary),
        singleLine = effectiveSingleLine,
        maxLines = if (effectiveSingleLine) 1 else maxLines,
        cursorBrush = SolidColor(colors.primary),
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onSend = {
                if (value.isNotBlank()) onSend?.invoke()
                focusManager.clearFocus()
            },
            onDone = { focusManager.clearFocus() },
        ),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .background(
                        color = if (isFocused) colors.glow.copy(alpha = 0.15f) else colors.surfaceContainerHigh,
                        shape = shape,
                    )
                    .then(
                        if (isFocused) {
                            Modifier.border(
                                width = 1.5.dp,
                                color = colors.primary,
                                shape = shape,
                            )
                        } else Modifier
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(text = hint, color = colors.textMuted)
                    }
                    innerTextField()
                }

                // 清除按钮：text 非空且聚焦时显示
                if (isFocused && value.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button) { onValueChange("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "清除",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                if (trailingIcon != null) trailingIcon()
            }
        },
    )
}
