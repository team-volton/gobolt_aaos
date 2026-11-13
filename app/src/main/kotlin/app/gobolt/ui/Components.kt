package app.gobolt.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.math.roundToInt

val ScreenPadding = Spacing.xl
val CardGap = Spacing.lg

val CardShape = RoundedCornerShape(Radius.lg)
val ControlShape = RoundedCornerShape(Radius.md)
val InnerShape = RoundedCornerShape(Radius.sm)

fun pct(value: Double) = "${value.roundToInt()}%"

fun Double.rounded() = roundToInt().toString()

@Composable
fun GoCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val border = if (selected) BorderStroke(Sizing.selectedBorder, MaterialTheme.colorScheme.primary) else null
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = CardShape,
        border = border,
        modifier = modifier,
    ) {
        val tap = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = tap.padding(Spacing.xl),
            content = content,
        )
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ControlShape,
        colors = ButtonDefaults.buttonColors(),
        modifier = modifier.heightIn(min = Sizing.button),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(Sizing.iconMd))
            Box(Modifier.width(Spacing.sm))
        }
        Text(text, style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        shape = ControlShape,
        border = BorderStroke(Sizing.border, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        modifier = modifier.heightIn(min = Sizing.touch),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(Sizing.iconSm))
            Box(Modifier.width(Spacing.sm))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun IconTap(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(Sizing.touch)
            .clip(ControlShape)
            .clickable(onClick = onClick),
    ) {
        Icon(icon, description, Modifier.size(Sizing.iconLg), tint = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, subtitle: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconTap(Icons.AutoMirrored.Rounded.ArrowBack, "뒤로", onBack)
        Column(Modifier.padding(start = Spacing.xs)) {
            Text(title, style = MaterialTheme.typography.headlineLarge, maxLines = 1)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = secondaryText())
            }
        }
    }
}

@Composable
fun <T> SegmentedTabs(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(ControlShape)
            .background(GoboltTheme.energy.track)
            .padding(Spacing.xxs),
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(Sizing.tab)
                    .clip(InnerShape)
                    .background(if (active) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(value) },
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) MaterialTheme.colorScheme.onSurface else secondaryText(),
                )
            }
        }
    }
}

@Composable
fun Metric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineMedium, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = secondaryText())
    }
}

@Composable
fun SocBar(socPct: Double, floorPct: Double, modifier: Modifier = Modifier) {
    val energy = GoboltTheme.energy
    val fill = if (socPct < floorPct) energy.warning else energy.charge
    Box(
        modifier
            .fillMaxWidth()
            .height(Sizing.bar)
            .clip(InnerShape)
            .background(energy.track),
    ) {
        Box(
            Modifier
                .fillMaxWidth((socPct / 100).toFloat().coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(fill),
        )
    }
}

@Composable
fun secondaryText() = MaterialTheme.colorScheme.onSurfaceVariant
