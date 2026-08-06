package com.aicompanion.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

@Immutable
data class StradustThemeData(
    val colors: StradustColors,
    val isDark: Boolean,
    val themeId: ThemeId,
)

val LocalStradustTheme = staticCompositionLocalOf {
    StradustThemeData(
        colors = DarkColorSchemes.Sakura,
        isDark = true,
        themeId = ThemeId.SAKURA,
    )
}

object StradustTheme {
    val colors: StradustColors
        @Composable get() = LocalStradustTheme.current.colors
    val isDark: Boolean
        @Composable get() = LocalStradustTheme.current.isDark
    val themeId: ThemeId
        @Composable get() = LocalStradustTheme.current.themeId
}

@Composable
fun StradustTheme(
    themeId: ThemeId = ThemeId.SAKURA,
    forceDarkMode: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = forceDarkMode ?: systemDark

    val colors = if (isDark) {
        DarkColorSchemes.fromId(themeId)
    } else {
        LightColorSchemes.fromId(themeId)
    }

    val mdColorScheme = if (isDark) {
        androidx.compose.material3.darkColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            primaryContainer = colors.primaryContainer,
            onPrimaryContainer = colors.onPrimaryContainer,
            secondary = colors.secondary,
            onSecondary = colors.onSecondary,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            error = colors.error,
            onError = colors.onError,
        )
    } else {
        androidx.compose.material3.lightColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            primaryContainer = colors.primaryContainer,
            onPrimaryContainer = colors.onPrimaryContainer,
            secondary = colors.secondary,
            onSecondary = colors.onSecondary,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            error = colors.error,
            onError = colors.onError,
        )
    }

    val themeData = StradustThemeData(colors = colors, isDark = isDark, themeId = themeId)

    CompositionLocalProvider(LocalStradustTheme provides themeData) {
        MaterialTheme(
            colorScheme = mdColorScheme,
            typography = StradustTypography,
            content = content,
        )
    }
}

val StradustTypography = androidx.compose.material3.Typography()
