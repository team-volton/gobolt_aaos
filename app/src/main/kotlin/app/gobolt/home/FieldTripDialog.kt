package app.gobolt.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.gobolt.FieldTripPreview
import app.gobolt.navi.NaviPlace
import app.gobolt.ui.GoboltTheme
import app.gobolt.ui.Metric
import app.gobolt.ui.PrimaryButton
import app.gobolt.ui.SecondaryButton
import app.gobolt.ui.Spacing
import app.gobolt.ui.pct
import app.gobolt.ui.secondaryText

private val DISTANCES = listOf(30, 60, 90, 120)
private const val MAX_PLACES = 3
private const val DAYS_AHEAD = 7

@Composable
fun FieldTripDialog(
    preview: (Int, Int) -> FieldTripPreview?,
    places: List<NaviPlace>,
    kmFor: (NaviPlace) -> Int,
    dayLabels: List<String>,
    onApply: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var km by remember { mutableStateOf<Int?>(null) }
    var day by remember { mutableStateOf(0) }
    val result = km?.let { preview(it, day) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(min = 760.dp),
        confirmButton = {},
        title = { Text("외근 추가", style = MaterialTheme.typography.headlineLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg), modifier = Modifier.fillMaxWidth()) {
                if (result == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        dayLabels.take(DAYS_AHEAD).forEachIndexed { index, label ->
                            FilterChip(
                                selected = index == day,
                                onClick = { day = index },
                                label = { Text(if (index == 0) "오늘" else label, style = MaterialTheme.typography.titleLarge) },
                            )
                        }
                    }
                    if (places.isNotEmpty()) {
                        Text("장소를 고르거나", style = MaterialTheme.typography.titleLarge, color = secondaryText())
                        places.take(MAX_PLACES).forEach { place ->
                            SecondaryButton(
                                "${place.name} · 왕복 약 ${kmFor(place)}km",
                                { km = kmFor(place) },
                                Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    Text("왕복 거리를 골라 주세요", style = MaterialTheme.typography.titleLarge, color = secondaryText())
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        DISTANCES.forEach { value ->
                            SecondaryButton("${value}km", { km = value }, Modifier.weight(1f))
                        }
                    }
                    SecondaryButton("닫기", onDismiss, Modifier.fillMaxWidth())
                } else {
                    Text(
                        (if (result.canGo) "다녀와도 돼요" else "충전 없이는 어려워요") + " · 귀가 ${pct(result.afterPct)}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (result.canGo) MaterialTheme.colorScheme.onSurface else GoboltTheme.energy.warning,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxxl)) {
                        Metric(pct(result.beforePct), "외근 전 귀가 잔량")
                        Metric(pct(result.afterPct), "외근 후 귀가 잔량")
                    }
                    PrimaryButton("충전 일정 반영", { onApply(km!!, day) }, Modifier.fillMaxWidth())
                    SecondaryButton("취소", onDismiss, Modifier.fillMaxWidth())
                }
            }
        },
    )
}
