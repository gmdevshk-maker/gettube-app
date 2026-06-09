package com.app.gettube.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.app.gettube.data.DarkMode

private val LightColors = lightColorScheme(
    primary = Red500,
    onPrimary = Color.White,
    primaryContainer = Red700,
    onPrimaryContainer = Color.White,
    secondary = Red500,
    onSecondary = Color.White,
    background = Color(0xFFF5F5F5),
    onBackground = Color(0xFF1C1B1F),
    surface = Color.White,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFEFEFEF),
    onSurfaceVariant = Color(0xFF666666),
)

private val DarkColors = darkColorScheme(
    primary = Red500,
    onPrimary = Color.White,
    primaryContainer = Red700,
    onPrimaryContainer = Color.White,
    secondary = Red200,
    onSecondary = Color.Black,
    background = Color(0xFF121212),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFAAAAAA),
)

/** [DarkMode] 설정값을 실제 라이트/다크 boolean으로 변환한다. */
@Composable
fun DarkMode.isDark(): Boolean = when (this) {
    DarkMode.SYSTEM -> isSystemInDarkTheme()
    DarkMode.LIGHT -> false
    DarkMode.DARK -> true
}

@Composable
fun GetTubeTheme(
    darkMode: DarkMode = DarkMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkMode.isDark()) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
