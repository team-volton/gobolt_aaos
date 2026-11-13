package app.gobolt.trip

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import app.gobolt.engine.EventKind
import app.gobolt.engine.Plan
import app.gobolt.navi.NaviPlace
import app.gobolt.ui.GoboltTheme
import app.gobolt.ui.secondaryText
import kotlin.math.roundToInt

// 거리가 없는 이벤트의 그래프 폭(km 환산) / Chart width, in km, for events without distance
private const val FAST_CHARGE_WIDTH = 30.0
private const val OTHER_EVENT_WIDTH = 50.0

// 같은 좌표일 때 0으로 나누지 않게 / Avoids dividing by zero for identical points
private const val MIN_SPAN = 0.01

@Composable
fun SocChart(plan: Plan, floorPct: Double, dayOffset: Int, modifier: Modifier = Modifier) {
    val energy = GoboltTheme.energy
    val muted = secondaryText()
    val grid = energy.track
    val measurer = rememberTextMeasurer()
    val label = MaterialTheme.typography.labelMedium.copy(color = muted)
    val warning = MaterialTheme.typography.labelMedium.copy(color = energy.warning)
    val value = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurface)

    Canvas(modifier) {
        val left = 80f
        val top = 44f
        val bottom = size.height - 8f
        val right = size.width - 8f

        fun yOf(pct: Double) = bottom - (bottom - top) * (pct.toFloat() / 100f)

        fun widthOf(km: Double, kind: EventKind) = when {
            km > 0 -> km
            kind == EventKind.FAST_CHARGE -> FAST_CHARGE_WIDTH
            else -> OTHER_EVENT_WIDTH
        }

        val total = plan.events.sumOf { widthOf(it.km, it.kind) }.coerceAtLeast(1.0)
        val scale = (right - left) / total.toFloat()

        listOf(0.0, 50.0, 100.0).forEach { tick ->
            drawLine(grid, Offset(left, yOf(tick)), Offset(right, yOf(tick)), strokeWidth = 2f)
            drawLabel(measurer, "${tick.toInt()}", Offset(0f, yOf(tick) - 16f), label)
        }

        var x = left
        var day = -1
        plan.events.forEach { event ->
            if (event.dayIndex != day) {
                day = event.dayIndex
                drawLine(grid, Offset(x, 0f), Offset(x, bottom), strokeWidth = 2f)
                drawLabel(measurer, "${day + 1 + dayOffset}일차", Offset(x + 8f, 0f), label)
            }
            val next = x + widthOf(event.km, event.kind).toFloat() * scale
            val color = when (event.kind) {
                EventKind.DRIVE, EventKind.LOCAL -> energy.drive
                EventKind.HVAC -> energy.stay
                EventKind.FAST_CHARGE, EventKind.SLOW_CHARGE -> energy.charge
            }
            drawLine(
                color,
                Offset(x, yOf(event.fromPct)),
                Offset(next, yOf(event.toPct)),
                strokeWidth = 8f,
                cap = StrokeCap.Round,
            )
            if (event.kind == EventKind.FAST_CHARGE) {
                drawLabel(
                    measurer,
                    "${event.fromPct.roundToInt()}%",
                    Offset(x + 40f, yOf(event.fromPct) - 40f),
                    value,
                )
            }
            x = next
        }

        drawLine(
            energy.warning,
            Offset(left, yOf(floorPct)),
            Offset(right, yOf(floorPct)),
            strokeWidth = 4f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 8f)),
        )
        val floorText = measurer.measure("▲ 하한 ${floorPct.roundToInt()}%", warning)
        drawText(
            floorText,
            topLeft = Offset(right - floorText.size.width, yOf(floorPct) + 4f),
        )
    }
}

@Composable
fun RouteSketch(line: List<NaviPlace>, stopPlaces: List<NaviPlace>, modifier: Modifier = Modifier) {
    val energy = GoboltTheme.energy
    val routeColor = energy.drive
    val measurer = rememberTextMeasurer()
    val placeStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurface)
    val endColor = MaterialTheme.colorScheme.onSurface

    Canvas(modifier) {
        val minLat = line.minOf { it.latitude }
        val maxLat = maxOf(line.maxOf { it.latitude }, minLat + MIN_SPAN)
        val minLon = line.minOf { it.longitude }
        val maxLon = maxOf(line.maxOf { it.longitude }, minLon + MIN_SPAN)
        // 왼쪽 위는 잔량 표시 자리 / The top left is reserved for the arrival figure
        val startX = size.width * 0.28f
        val endX = size.width * 0.92f
        val startY = size.height * 0.14f
        val endY = size.height * 0.86f

        fun project(latitude: Double, longitude: Double) = Offset(
            startX + (endX - startX) * ((longitude - minLon) / (maxLon - minLon)).toFloat(),
            startY + (endY - startY) * ((maxLat - latitude) / (maxLat - minLat)).toFloat(),
        )

        val path = Path()
        line.forEachIndexed { index, place ->
            val point = project(place.latitude, place.longitude)
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        drawPath(path, routeColor, style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round))

        listOf(line.first(), line.last()).forEach { place ->
            val point = project(place.latitude, place.longitude)
            drawCircle(endColor, 12f, point)
            drawLabel(measurer, place.name, point + Offset(24f, -16f), placeStyle)
        }
        stopPlaces.forEachIndexed { index, place ->
            val point = project(place.latitude, place.longitude)
            drawCircle(energy.charge, 20f, point)
            drawCircle(Color.White, 8f, point)
            val below = index % 2 == 0
            drawLabel(
                measurer,
                place.name.removeSuffix("휴게소"),
                point + Offset(-20f, if (below) 24f else -64f),
                placeStyle,
            )
        }
    }
}

private fun DrawScope.drawLabel(measurer: TextMeasurer, text: String, at: Offset, style: TextStyle) {
    drawText(measurer.measure(text, style), topLeft = at)
}
