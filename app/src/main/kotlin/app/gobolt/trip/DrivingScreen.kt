package app.gobolt.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gobolt.GoboltViewModel
import app.gobolt.engine.EventKind
import app.gobolt.engine.Plan
import app.gobolt.ui.CardGap
import app.gobolt.ui.GoCard
import app.gobolt.ui.GoboltTheme
import app.gobolt.ui.Metric
import app.gobolt.ui.SecondaryButton
import app.gobolt.ui.pct
import app.gobolt.ui.rounded
import app.gobolt.ui.secondaryText

private val RemainingKmStyle = TextStyle(fontSize = 200.sp, lineHeight = 208.sp, fontWeight = FontWeight.Bold)

private data class NextLeg(val name: String, val km: Double, val arrivalPct: Double)

private fun nextLeg(plan: Plan): NextLeg? {
    val chargeIndex = plan.events.indexOfFirst { it.kind == EventKind.FAST_CHARGE }
    val drive = if (chargeIndex > 0) {
        plan.events.subList(0, chargeIndex).lastOrNull { it.kind == EventKind.DRIVE }
    } else {
        plan.events.firstOrNull { it.kind == EventKind.DRIVE }
    } ?: return null
    return NextLeg(plan.stops.firstOrNull { it.fast }?.name ?: "목적지", drive.km, drive.toPct)
}

@Composable
fun DrivingScreen(viewModel: GoboltViewModel, onFinish: () -> Unit, modifier: Modifier = Modifier) {
    val plans by viewModel.plans.collectAsStateWithLifecycle()
    val strategy by viewModel.strategy.collectAsStateWithLifecycle()
    val plan = plans.firstOrNull { it.strategy == strategy } ?: return
    val leg = nextLeg(plan) ?: return

    Row(horizontalArrangement = Arrangement.spacedBy(CardGap), modifier = modifier) {
        GoCard(Modifier.weight(0.62f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Bolt, null, Modifier.size(48.dp), tint = GoboltTheme.energy.charge)
                Spacer(Modifier.size(8.dp))
                Text("다음 충전", style = MaterialTheme.typography.headlineMedium, color = secondaryText())
            }
            Text(leg.name, style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(leg.km.rounded(), style = RemainingKmStyle, maxLines = 1)
                Text(
                    "km",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(start = 12.dp, bottom = 32.dp),
                )
            }
            Text("남은 거리", style = MaterialTheme.typography.headlineMedium, color = secondaryText())
            Spacer(Modifier.weight(1f))
            Metric(pct(leg.arrivalPct), "도착 예상 잔량")
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(CardGap),
            modifier = Modifier.weight(0.38f).fillMaxHeight(),
        ) {
            GoCard(Modifier.fillMaxWidth().weight(1f)) {
                Text("충전 계획", style = MaterialTheme.typography.headlineMedium)
                plan.stops.forEach { stop ->
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text(stop.name, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${stop.dayIndex + 1}일차 · ${pct(stop.fromPct)} → ${pct(stop.toPct)}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = secondaryText(),
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, null, Modifier.size(36.dp), tint = secondaryText())
                Spacer(Modifier.size(12.dp))
                Text("주행 중 입력 잠금", style = MaterialTheme.typography.bodyLarge, color = secondaryText())
            }
            SecondaryButton("운행 종료", onFinish, Modifier.fillMaxWidth())
        }
    }
}
