package app.gobolt.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gobolt.GoboltViewModel
import app.gobolt.data.EfficiencyMode
import app.gobolt.data.HomeCharge
import app.gobolt.data.Routine
import app.gobolt.demo.DemoTrip
import app.gobolt.navi.NaviPlace
import app.gobolt.ui.CardGap
import app.gobolt.ui.GoCard
import app.gobolt.ui.IconTap
import app.gobolt.ui.ScreenHeader
import app.gobolt.ui.SecondaryButton
import app.gobolt.ui.SegmentedTabs
import app.gobolt.ui.Spacing
import app.gobolt.ui.secondaryText

@Composable
fun SettingsScreen(viewModel: GoboltViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val navi by viewModel.naviState.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(CardGap), modifier = modifier) {
        ScreenHeader("설정", onBack)
        Row(horizontalArrangement = Arrangement.spacedBy(CardGap), modifier = Modifier.weight(1f)) {
            GoCard(Modifier.weight(0.56f).fillMaxHeight()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    Stepper("안전 하한", "${settings.floorPct}%") { delta ->
                        viewModel.updateSettings {
                            it.copy(floorPct = (it.floorPct + delta * 5).coerceIn(5, it.targetPct - 5))
                        }
                    }
                    Stepper("충전 목표", "${settings.targetPct}%") { delta ->
                        viewModel.updateSettings {
                            it.copy(targetPct = (it.targetPct + delta * 5).coerceIn(it.floorPct + 5, 100))
                        }
                    }
                    Stepper("출퇴근 왕복", "${settings.commuteKm}km") { delta ->
                        viewModel.updateSettings { it.copy(commuteKm = (it.commuteKm + delta * 5).coerceIn(0, 300)) }
                    }
                    Text("출퇴근 요일", style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        DemoTrip.weekdays.forEachIndexed { index, label ->
                            FilterChip(
                                selected = index in settings.commuteDays,
                                onClick = {
                                    viewModel.updateSettings {
                                        val days = if (index in it.commuteDays) it.commuteDays - index else it.commuteDays + index
                                        it.copy(commuteDays = days)
                                    }
                                },
                                label = { Text(label, style = MaterialTheme.typography.titleLarge) },
                            )
                        }
                    }
                    Text("차량 " + viewModel.vehicleSummary(), style = MaterialTheme.typography.bodyLarge, color = secondaryText())
                    Text("반복 일정", style = MaterialTheme.typography.titleLarge)
                    settings.routines.forEachIndexed { index, routine ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${DemoTrip.weekdays[routine.weekday]}요일 ${routine.label} ${routine.km}km",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.weight(1f),
                            )
                            IconTap(Icons.Rounded.Remove, "삭제", {
                                viewModel.updateSettings { it.copy(routines = it.routines.filterIndexed { i, _ -> i != index }) }
                            })
                        }
                    }
                    RoutineAdder(navi.places, viewModel::fieldTripKm) { routine -> viewModel.updateSettings { it.copy(routines = it.routines + routine) } }
                    Text("전비 기준", style = MaterialTheme.typography.titleLarge)
                    SegmentedTabs(
                        options = EfficiencyMode.entries.map { it to it.label },
                        selected = settings.efficiencyMode,
                        onSelect = { value -> viewModel.updateSettings { it.copy(efficiencyMode = value) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("집 · 회사 충전", style = MaterialTheme.typography.titleLarge)
                    SegmentedTabs(
                        options = HomeCharge.entries.map { it to it.label },
                        selected = settings.homeCharge,
                        onSelect = { value -> viewModel.updateSettings { it.copy(homeCharge = value) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            GoCard(Modifier.weight(0.44f).fillMaxHeight()) {
                Text("내비 연동 점검", style = MaterialTheme.typography.headlineMedium)
                Text(
                    if (navi.connected) "내비 연결됨" else "내비 연결 중",
                    style = MaterialTheme.typography.titleLarge,
                )
                SecondaryButton("부산까지 예상값", viewModel::requestTrip, Modifier.fillMaxWidth())
                SecondaryButton("칠곡 경유 경로", viewModel::sendTestRoute, Modifier.fillMaxWidth())
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    navi.log.forEach {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = secondaryText())
                    }
                }
            }
        }
    }
}

private const val MAX_ROUTINE_PLACES = 3

@Composable
private fun RoutineAdder(places: List<NaviPlace>, kmFor: (NaviPlace) -> Int, onAdd: (Routine) -> Unit) {
    var weekday by remember { mutableStateOf(3) }
    var km by remember { mutableStateOf(30) }
    var label by remember { mutableStateOf("약속") }
    // 장소를 고르면 왕복 거리를 추정 / Picking a place estimates the round trip
    places.take(MAX_ROUTINE_PLACES).forEach { place ->
        FilterChip(
            selected = label == place.name,
            onClick = {
                label = place.name
                km = kmFor(place).coerceAtLeast(10)
            },
            label = { Text(place.name, style = MaterialTheme.typography.titleLarge) },
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        DemoTrip.weekdays.forEachIndexed { index, label ->
            FilterChip(
                selected = index == weekday,
                onClick = { weekday = index },
                label = { Text(label, style = MaterialTheme.typography.titleLarge) },
            )
        }
    }
    Stepper("약속 왕복", "${km}km") { km = (km + it * 10).coerceIn(10, 300) }
    SecondaryButton("반복 일정 추가", { onAdd(Routine(weekday, km, label)) }, Modifier.fillMaxWidth())
}

@Composable
private fun Stepper(label: String, value: String, onStep: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        IconTap(Icons.Rounded.Remove, "$label 줄이기", { onStep(-1) })
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(144.dp).padding(horizontal = Spacing.xxs),
        )
        IconTap(Icons.Rounded.Add, "$label 늘리기", { onStep(1) })
    }
}
