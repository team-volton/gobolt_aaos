package app.gobolt.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF00C27A),
    onPrimary = Color(0xFF0F1714),
    background = Color(0xFFF4F6F5),
    onBackground = Color(0xFF0F1714),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F1714),
    onSurfaceVariant = Color(0xFF5B6762),
    error = Color(0xFFC2410C),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF3DF5A0),
    onPrimary = Color(0xFF0F1714),
    background = Color(0xFF0B0F0E),
    onBackground = Color(0xFFF2F5F4),
    surface = Color(0xFF151B19),
    onSurface = Color(0xFFF2F5F4),
    onSurfaceVariant = Color(0xFF9AA6A1),
    error = Color(0xFFFF8A63),
)

@Composable
fun GoboltTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
