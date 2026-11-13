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
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

private fun plannedLeg(plan: Plan): NextLeg? {
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
    val selected by viewModel.selectedPlan.collectAsStateWithLifecycle()
    val navi by viewModel.naviState.collectAsStateWithLifecycle()
    val vehicle by viewModel.vehicleState.collectAsStateWithLifecycle()
    var replaced by remember { mutableStateOf<String?>(null) }
    val plan = selected ?: return
    val planned = plannedLeg(plan) ?: return
    val live = navi.nextStop
    val leg = NextLeg(
        name = live?.name ?: planned.name,
        km = live?.remainKm ?: planned.km,
        arrivalPct = live?.let(viewModel::arrivalPct) ?: planned.arrivalPct,
    )
    val check = live?.let { viewModel.driveCheck(it, planned.arrivalPct) }

    Row(horizontalArrangement = Arrangement.spacedBy(CardGap), modifier = modifier) {
        GoCard(Modifier.weight(0.62f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Bolt, null, Modifier.size(48.dp), tint = GoboltTheme.energy.charge)
                Spacer(Modifier.size(8.dp))
                Text(
                    if (live == null) "다음 충전 · 계획값" else "다음 지점",
                    style = MaterialTheme.typography.headlineMedium,
                    color = secondaryText(),
                )
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
            Row(horizontalArrangement = Arrangement.spacedBy(64.dp)) {
                Metric(pct(leg.arrivalPct), "도착 예상 잔량")
                live?.let { Metric("${it.remainMinutes.rounded()}분", "남은 시간") }
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(CardGap),
            modifier = Modifier.weight(0.38f).fillMaxHeight(),
        ) {
            if (check?.shouldAlert == true) {
                GoCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Warning, null, Modifier.size(40.dp), tint = GoboltTheme.energy.warning)
                        Spacer(Modifier.size(12.dp))
                        Text(
                            if (check.canReach) "계획보다 잔량이 적어요" else "다음 지점까지 빠듯해요",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
            }
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
            if (vehicle.driving) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Lock, null, Modifier.size(36.dp), tint = secondaryText())
                    Spacer(Modifier.size(12.dp))
                    Text("주행 중 입력 잠금", style = MaterialTheme.typography.bodyLarge, color = secondaryText())
                }
            }
            replaced?.let {
                Text("$it(으)로 경유지를 바꿨어요", style = MaterialTheme.typography.titleLarge, color = GoboltTheme.energy.charge)
            }
            if (!vehicle.driving && plan.stops.any { it.dayIndex == 0 && it.fast }) {
                SecondaryButton(
                    "다음 충전소를 못 써요",
                    { replaced = viewModel.replaceNextCharger() ?: "다른 충전소가 없어 그대로 유지" },
                    Modifier.fillMaxWidth(),
                )
            }
            SecondaryButton("운행 종료", onFinish, Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun DriveLock(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(CardGap), modifier = modifier) {
        GoCard(Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Lock, null, Modifier.size(56.dp), tint = secondaryText())
            Text("주행 중에는 입력할 수 없어요", style = MaterialTheme.typography.headlineLarge)
            Text("정차한 뒤 다시 열어 주세요", style = MaterialTheme.typography.titleLarge, color = secondaryText())
        }
        SecondaryButton("돌아가기", onBack, Modifier.fillMaxWidth())
    }
}
