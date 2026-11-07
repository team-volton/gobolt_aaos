package app.gobolt.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF0F1714)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00C27A),
    onPrimary = Ink,
    background = Color(0xFFF4F6F5),
    onBackground = Ink,
    surface = Color(0xFFFFFFFF),
    onSurface = Ink,
    onSurfaceVariant = Color(0xFF5B6762),
    outline = Color(0xFFD5DBD8),
    error = Color(0xFFC2410C),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF3DF5A0),
    onPrimary = Ink,
    background = Color(0xFF0B0F0E),
    onBackground = Color(0xFFF2F5F4),
    surface = Color(0xFF151B19),
    onSurface = Color(0xFFF2F5F4),
    onSurfaceVariant = Color(0xFF9AA6A1),
    outline = Color(0xFF2C3532),
    error = Color(0xFFFF8A63),
)

@Immutable
data class EnergyColors(
    val drive: Color,
    val charge: Color,
    val stay: Color,
    val warning: Color,
    val track: Color,
)

private val LightEnergy = EnergyColors(
    drive = Color(0xFF2F6BFF),
    charge = Color(0xFF007A4D),
    stay = Color(0xFF7A5CFF),
    warning = Color(0xFFC2410C),
    track = Color(0xFFE4E9E6),
)

private val DarkEnergy = EnergyColors(
    drive = Color(0xFF7FA6FF),
    charge = Color(0xFF3DF5A0),
    stay = Color(0xFFAE97FF),
    warning = Color(0xFFFF8A63),
    track = Color(0xFF2C3532),
)

private val LocalEnergyColors = staticCompositionLocalOf { LightEnergy }

object GoboltTheme {
    val energy: EnergyColors
        @Composable @ReadOnlyComposable get() = LocalEnergyColors.current
}

// 차량 화면은 160dpi라 1sp가 1px / The car display is 160dpi, so 1sp equals 1px
private val CarTypography = Typography(
    displayLarge = TextStyle(fontSize = 120.sp, lineHeight = 128.sp, fontWeight = FontWeight.Bold),
    displayMedium = TextStyle(fontSize = 88.sp, lineHeight = 96.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 44.sp, lineHeight = 60.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 36.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 32.sp, lineHeight = 48.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 28.sp, lineHeight = 42.sp),
    bodyMedium = TextStyle(fontSize = 24.sp, lineHeight = 36.sp),
    labelLarge = TextStyle(fontSize = 28.sp, lineHeight = 42.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun GoboltTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalEnergyColors provides if (dark) DarkEnergy else LightEnergy) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = CarTypography,
            content = content,
        )
    }
}
