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

private val LightColors = lightColorScheme(
    primary = Color(0xFF0E8A5F),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFFF2F4F5),
    onBackground = Color(0xFF14181B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14181B),
    surfaceVariant = Color(0xFFE6E9EB),
    onSurfaceVariant = Color(0xFF5C656C),
    outline = Color(0xFFD2D7DB),
    error = Color(0xFFB4501A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF34B987),
    onPrimary = Color(0xFF07130E),
    background = Color(0xFF0D1012),
    onBackground = Color(0xFFECEFF1),
    surface = Color(0xFF171B1E),
    onSurface = Color(0xFFECEFF1),
    surfaceVariant = Color(0xFF242A2E),
    onSurfaceVariant = Color(0xFF98A1A8),
    outline = Color(0xFF323A3F),
    error = Color(0xFFEE9150),
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
    drive = Color(0xFF2D62CF),
    charge = Color(0xFF0E8A5F),
    stay = Color(0xFF66707E),
    warning = Color(0xFFB4501A),
    track = Color(0xFFE6E9EB),
)

private val DarkEnergy = EnergyColors(
    drive = Color(0xFF6E98EC),
    charge = Color(0xFF34B987),
    stay = Color(0xFF8C96A4),
    warning = Color(0xFFEE9150),
    track = Color(0xFF242A2E),
)

private val LocalEnergyColors = staticCompositionLocalOf { LightEnergy }

object GoboltTheme {
    val energy: EnergyColors
        @Composable @ReadOnlyComposable get() = LocalEnergyColors.current
}

// 차량 화면은 160dpi라 1sp가 1px. 크기와 줄 높이 모두 4의 배수
// The car display is 160dpi (1sp = 1px). Sizes and line heights are multiples of 4
private val CarTypography = Typography(
    displayLarge = TextStyle(fontSize = 200.sp, lineHeight = 208.sp, fontWeight = FontWeight.Bold),
    displayMedium = TextStyle(fontSize = 120.sp, lineHeight = 128.sp, fontWeight = FontWeight.Bold),
    displaySmall = TextStyle(fontSize = 88.sp, lineHeight = 96.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 44.sp, lineHeight = 56.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 36.sp, lineHeight = 48.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 32.sp, lineHeight = 44.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 28.sp, lineHeight = 40.sp),
    bodyMedium = TextStyle(fontSize = 24.sp, lineHeight = 32.sp),
    labelLarge = TextStyle(fontSize = 28.sp, lineHeight = 40.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium),
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
