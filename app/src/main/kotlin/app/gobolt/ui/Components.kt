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
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// 운전 중 조작을 고려한 최소 터치 높이 / Minimum touch height for in-car use
val TouchTarget = 88.dp
val ScreenPadding = 28.dp
val CardGap = 20.dp

private val CardShape = RoundedCornerShape(28.dp)
private val ControlShape = RoundedCornerShape(22.dp)

fun pct(value: Double) = "${value.roundToInt()}%"

fun Double.rounded() = roundToInt().toString()

@Composable
fun GoCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val border = if (selected) BorderStroke(4.dp, MaterialTheme.colorScheme.primary) else null
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = CardShape,
        border = border,
        modifier = modifier,
    ) {
        val tap = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = tap.padding(28.dp),
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
        modifier = modifier.heightIn(min = 96.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(36.dp))
            Box(Modifier.width(12.dp))
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
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        modifier = modifier.heightIn(min = TouchTarget),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(32.dp))
            Box(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun IconTap(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(TouchTarget)
            .clip(ControlShape)
            .clickable(onClick = onClick),
    ) {
        Icon(icon, description, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, subtitle: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconTap(Icons.AutoMirrored.Rounded.ArrowBack, "뒤로", onBack)
        Column(Modifier.padding(start = 8.dp)) {
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
            .padding(6.dp),
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(76.dp)
                    .clip(RoundedCornerShape(18.dp))
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
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp))
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
