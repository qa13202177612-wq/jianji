package com.jianji.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = MintPrimary,
    onPrimary = Color.White,
    primaryContainer = MintContainer,
    onPrimaryContainer = Color(0xFF0A3B2E),
    secondary = Peach,
    onSecondary = Color(0xFF3A2408),
    secondaryContainer = Color(0xFFFFE8D1),
    onSecondaryContainer = Color(0xFF4A2B08),
    tertiary = Sky,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = Color(0xFF17211D),
    surface = LightSurface,
    onSurface = Color(0xFF17211D),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF4B5B54),
    outline = Color(0xFFCFDAD5),
    error = Color(0xFFD9534F),
    onError = Color.White,
    errorContainer = Color(0xFFFFE3E1),
    onErrorContainer = Color(0xFF6B1E1C)
)

private val DarkColors = darkColorScheme(
    primary = MintDark,
    onPrimary = Color(0xFF03372A),
    primaryContainer = Color(0xFF16513F),
    onPrimaryContainer = Color(0xFFC7F3E2),
    secondary = Color(0xFFFFC98A),
    onSecondary = Color(0xFF442A08),
    secondaryContainer = Color(0xFF5C3B12),
    onSecondaryContainer = Color(0xFFFFE8D1),
    tertiary = Color(0xFFA9BAFF),
    onTertiary = Color(0xFF1B2A5B),
    background = DarkBackground,
    onBackground = Color(0xFFE6EFEB),
    surface = DarkSurface,
    onSurface = Color(0xFFE6EFEB),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFB4C4BD),
    outline = Color(0xFF3B4A44),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF4A0E0C),
    errorContainer = Color(0xFF7A2622),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun JianJiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                // 应用内切换主题时同步状态栏/导航栏图标颜色
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = JianJiTypography,
        content = content
    )
}
