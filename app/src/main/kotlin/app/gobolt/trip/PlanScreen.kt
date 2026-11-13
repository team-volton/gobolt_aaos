package app.gobolt.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gobolt.GoboltViewModel
import app.gobolt.data.Stay
import app.gobolt.data.summary
import app.gobolt.engine.ChargeStop
import app.gobolt.engine.ChargerOption
import app.gobolt.engine.Plan
import app.gobolt.engine.Strategy
import app.gobolt.engine.Suggestion
import app.gobolt.ui.CardGap
import app.gobolt.ui.GoCard
import app.gobolt.ui.GoboltTheme
import app.gobolt.ui.IconTap
import app.gobolt.ui.Metric
import app.gobolt.ui.PrimaryButton
import app.gobolt.ui.ScreenHeader
import app.gobolt.ui.SecondaryButton
import app.gobolt.ui.pct
import app.gobolt.ui.rounded
import app.gobolt.ui.secondaryText

fun strategyName(strategy: Strategy) = when (strategy) {
    Strategy.FEWEST_STOPS -> "충전 횟수 최소"
    Strategy.FASTEST -> "소요 시간 최소"
    Strategy.MOST_MARGIN -> "잔량 여유 최대"
}

@Composable
fun PlanScreen(
    viewModel: GoboltViewModel,
    onBack: () -> Unit,
    onStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val plans by viewModel.plans.collectAsStateWithLifecycle()
    val baseline by viewModel.lodgingPlans.collectAsStateWithLifecycle()
    val strategy by viewModel.strategy.collectAsStateWithLifecycle()
    val trip by viewModel.trip.collectAsStateWithLifecycle()
    val lastNight = trip.nights.lastIndex
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val plan = plans.firstOrNull { it.strategy == strategy } ?: return
    val before = baseline.firstOrNull { it.strategy == strategy }
    var openStop by remember { mutableStateOf<ChargeStop?>(null) }
    LaunchedEffect(Unit) { viewModel.refreshNaviChargers() }

    val options: @Composable (Modifier) -> Unit = { panel ->
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = panel) {
            ScreenHeader(
                "${trip.destination.name} 여행",
                onBack,
                trip.summary() + " · 충전소 " + viewModel.chargerSource,
            )
            plans.forEach { candidate ->
                StrategyCard(candidate, candidate.strategy == strategy) {
                    viewModel.selectStrategy(candidate.strategy)
                }
            }
            CarCampCard(
                night = lastNight + 1,
                on = trip.nights[lastNight] == Stay.CAR_CAMP,
                onChange = { viewModel.setStay(lastNight, if (it) Stay.CAR_CAMP else Stay.LODGING) },
                before = before,
                after = plan,
            )
            Spacer(Modifier.weight(1f))
            PrimaryButton(
                "이 계획으로 시작",
                onClick = {
                    viewModel.startGuidance(plan)
                    onStarted()
                },
                enabled = plan.isFeasible,
                icon = Icons.Rounded.Navigation,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    val overview: @Composable (Modifier) -> Unit = { panel ->
        Box(panel) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
                GoCard(Modifier.fillMaxWidth().weight(1f)) {
                    Box(Modifier.fillMaxSize()) {
                        val (line, stops) = viewModel.sketch(plan)
                        RouteSketch(line, stops, Modifier.fillMaxSize())
                        Column {
                            Text(
                                if (plan.isFeasible) pct(plan.summary.endSocPct) else "--",
                                style = MaterialTheme.typography.displayMedium,
                            )
                            Text("도착 예상 잔량", style = MaterialTheme.typography.bodyLarge, color = secondaryText())
                        }
                        Text(
                            "개략도",
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryText(),
                            modifier = Modifier.align(Alignment.TopEnd),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    plan.stops.take(MAX_STOP_CARDS).forEach { stop ->
                        StopCard(stop, Modifier.weight(1f)) { openStop = stop }
                    }
                }
                GoCard(Modifier.fillMaxWidth().weight(1f)) {
                    SocChart(plan, settings.floorPct.toDouble(), Modifier.fillMaxSize())
                }
            }
            openStop?.let { stop ->
                StopSheet(
                    stop = stop,
                    alternatives = viewModel.alternatives(plan, stop),
                    onExclude = {
                        stop.chargerId?.let(viewModel::toggleCharger)
                        openStop = null
                    },
                    onClose = { openStop = null },
                )
            }
        }
    }

    BoxWithConstraints(modifier) {
        if (maxWidth >= SPLIT_MIN_WIDTH) {
            Row(horizontalArrangement = Arrangement.spacedBy(CardGap), modifier = Modifier.fillMaxSize()) {
                options(Modifier.weight(0.35f).fillMaxHeight())
                overview(Modifier.weight(0.65f).fillMaxHeight())
            }
        } else {
            // 좁은 창은 위아래로 쌓음 / Narrow windows stack vertically
            Column(
                verticalArrangement = Arrangement.spacedBy(CardGap),
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            ) {
                options(Modifier.fillMaxWidth().height(STACKED_OPTIONS_HEIGHT))
                overview(Modifier.fillMaxWidth().height(STACKED_OVERVIEW_HEIGHT))
            }
        }
    }
}

private const val MAX_STOP_CARDS = 4
private val SPLIT_MIN_WIDTH = 1200.dp
private val STACKED_OPTIONS_HEIGHT = 1000.dp
private val STACKED_OVERVIEW_HEIGHT = 1100.dp

@Composable
private fun StrategyCard(plan: Plan, selected: Boolean, onClick: () -> Unit) {
    GoCard(Modifier.fillMaxWidth(), selected = selected, onClick = onClick) {
        Text(strategyName(plan.strategy), style = MaterialTheme.typography.titleLarge)
        if (plan.isFeasible) {
            val summary = plan.summary
            Text(
                "충전 ${plan.stops.size}회 · 급속 ${summary.fastMinutes.rounded()}분 · 도착 ${pct(summary.endSocPct)}",
                style = MaterialTheme.typography.bodyMedium,
                color = secondaryText(),
            )
        } else {
            val raise = plan.infeasibility?.suggestions
                ?.filterIsInstance<Suggestion.RaiseDepartureSoc>()?.firstOrNull()
            Text(
                raise?.let { "출발 전 ${pct(it.pct)}까지 충전하면 가능해요" } ?: "현재 조건으로는 어려운 일정",
                style = MaterialTheme.typography.bodyMedium,
                color = GoboltTheme.energy.warning,
            )
        }
    }
}

@Composable
private fun CarCampCard(night: Int, on: Boolean, onChange: (Boolean) -> Unit, before: Plan?, after: Plan) {
    GoCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 72.dp)) {
            Text("${night}일차 밤 차박", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Switch(
                checked = on,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(
                    uncheckedThumbColor = secondaryText(),
                    uncheckedTrackColor = GoboltTheme.energy.track,
                    uncheckedBorderColor = secondaryText(),
                ),
                modifier = Modifier.scale(1.4f).padding(end = 8.dp),
            )
        }
        if (on && before != null) {
            Text(
                "전기 ${before.summary.totalKwh.rounded()} → ${after.summary.totalKwh.rounded()}kWh · " +
                    "급속 ${before.summary.fastMinutes.rounded()} → ${after.summary.fastMinutes.rounded()}분",
                style = MaterialTheme.typography.bodyMedium,
                color = GoboltTheme.energy.stay,
            )
        }
    }
}

@Composable
private fun StopCard(stop: ChargeStop, modifier: Modifier = Modifier, onClick: () -> Unit) {
    GoCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Bolt, null, Modifier.size(32.dp), tint = GoboltTheme.energy.charge)
            Text(
                "${stop.dayIndex + 1}일차",
                style = MaterialTheme.typography.bodyMedium,
                color = secondaryText(),
            )
        }
        Text(stop.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, minLines = 2)
        Text(
            "${pct(stop.fromPct)} → ${pct(stop.toPct)}",
            style = MaterialTheme.typography.bodyMedium,
            color = secondaryText(),
            maxLines = 1,
        )
    }
}

@Composable
private fun StopSheet(
    stop: ChargeStop,
    alternatives: List<ChargerOption>,
    onExclude: () -> Unit,
    onClose: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(28.dp))
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(32.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stop.name, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                    IconTap(Icons.Rounded.Close, "닫기", onClose)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                    Metric("${pct(stop.fromPct)} → ${pct(stop.toPct)}", "충전 잔량")
                    Metric(chargeTime(stop.minutes), "걸리는 시간")
                    Metric("${stop.powerKw.rounded()}kW", if (stop.fast) "평균 급속" else "완속")
                }
                if (alternatives.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("못 쓸 때 대체 충전소", style = MaterialTheme.typography.titleLarge, color = secondaryText())
                    alternatives.forEach { AlternativeRow(it) }
                    SecondaryButton("이 충전소 빼고 다시 계획", onExclude, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun AlternativeRow(option: ChargerOption) {
    val usable = option.available && option.reachable && option.reachesDestination
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 72.dp)) {
        Text(option.charger.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Text(
            if (usable) {
                "도착 ${pct(option.arrivalPct)} · 충전 ${option.chargeMinutes.rounded()}분 · 추가 ${option.extraKm.rounded()}km"
            } else {
                "도달 어려움"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = if (usable) MaterialTheme.colorScheme.onSurface else GoboltTheme.energy.warning,
        )
    }
}

private fun chargeTime(minutes: Double): String =
    if (minutes < 120) "약 ${minutes.rounded()}분" else "약 ${"%.1f".format(minutes / 60)}시간"
