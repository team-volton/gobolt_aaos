package app.gobolt.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gobolt.GoboltViewModel
import app.gobolt.demo.DemoTrip
import app.gobolt.demo.Stay
import app.gobolt.ui.CardGap
import app.gobolt.ui.GoCard
import app.gobolt.ui.IconTap
import app.gobolt.ui.PrimaryButton
import app.gobolt.ui.ScreenHeader
import app.gobolt.ui.SegmentedTabs
import app.gobolt.ui.rounded
import app.gobolt.ui.secondaryText

@Composable
fun TripInputScreen(
    viewModel: GoboltViewModel,
    onBack: () -> Unit,
    onMakePlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val input by viewModel.tripInput.collectAsStateWithLifecycle()
    val plans by viewModel.plans.collectAsStateWithLifecycle()
    val kwh = plans.firstOrNull()?.summary?.totalKwh

    Column(verticalArrangement = Arrangement.spacedBy(CardGap), modifier = modifier) {
        ScreenHeader(
            "서울 → 부산",
            onBack,
            "총 예상 ${input.totalKm}km" + (kwh?.let { " · ${it.rounded()}kWh" } ?: ""),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(CardGap), modifier = Modifier.weight(1f)) {
            Column(
                verticalArrangement = Arrangement.spacedBy(CardGap),
                modifier = Modifier
                    .weight(0.66f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
            ) {
                DayRow("1일차 · 11/1", "서울 → 부산 ${DemoTrip.LEG_KM}km") {
                    StayTabs(input.nights[0]) { viewModel.setStay(0, it) }
                }
                DayRow("2일차 · 11/2", "현지 이동") {
                    KmStepper(input.localKm[0]) { viewModel.setLocalKm(0, it) }
                    StayTabs(input.nights[1]) { viewModel.setStay(1, it) }
                }
                DayRow("3일차 · 11/3", "현지 이동") {
                    KmStepper(input.localKm[1]) { viewModel.setLocalKm(1, it) }
                    StayTabs(input.nights[2]) { viewModel.setStay(2, it) }
                }
                DayRow("4일차 · 11/4", "부산 → 서울 ${DemoTrip.LEG_KM}km") {}
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(CardGap),
                modifier = Modifier.weight(0.34f).fillMaxHeight(),
            ) {
                GoCard(Modifier.fillMaxWidth()) {
                    Text("기본값", style = MaterialTheme.typography.titleLarge, color = secondaryText())
                    Setting("출발 시각", "08:40")
                    Setting("안전 하한", "20%")
                    Setting("충전 목표", "80%")
                }
                Spacer(Modifier.weight(1f))
                PrimaryButton("계획 만들기", onMakePlan, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun DayRow(day: String, title: String, controls: @Composable () -> Unit) {
    GoCard(Modifier.fillMaxWidth()) {
        Text(day, style = MaterialTheme.typography.bodyLarge, color = secondaryText())
        Text(title, style = MaterialTheme.typography.headlineMedium)
        controls()
    }
}

@Composable
private fun StayTabs(selected: Stay, onSelect: (Stay) -> Unit) {
    SegmentedTabs(
        options = listOf(Stay.LODGING to "숙소", Stay.CAR_CAMP to "차박"),
        selected = selected,
        onSelect = onSelect,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun KmStepper(km: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("차량 이동", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        IconTap(Icons.Rounded.Remove, "10km 줄이기", { onChange(km - 10) })
        Text(
            "${km}km",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(160.dp),
        )
        IconTap(Icons.Rounded.Add, "10km 늘리기", { onChange(km + 10) })
    }
}

@Composable
private fun Setting(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.headlineMedium)
    }
}
