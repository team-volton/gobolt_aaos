package app.gobolt.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gobolt.GoboltViewModel
import app.gobolt.data.Stay
import app.gobolt.data.label
import app.gobolt.ui.CardGap
import app.gobolt.ui.GoCard
import app.gobolt.ui.GoboltTheme
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
    val trip by viewModel.trip.collectAsStateWithLifecycle()
    val navi by viewModel.naviState.collectAsStateWithLifecycle()
    val legs = viewModel.legs(trip)
    val totalKm = legs.outKm + legs.backKm + trip.localKm.sum()
    val places = (navi.places + viewModel.presetPlaces).filter { it.name.isNotBlank() }.distinctBy { it.name }
    LaunchedEffect(Unit) { viewModel.refreshNaviChargers() }

    Column(verticalArrangement = Arrangement.spacedBy(CardGap), modifier = modifier) {
        ScreenHeader(
            "${trip.destination.name} 여행",
            onBack,
            "총 예상 ${totalKm.rounded()}km · 편도 ${legs.outKm.rounded()}km " +
                if (legs.measured) "(내비 계산)" else "(직선거리 추정)",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(CardGap), modifier = Modifier.weight(1f)) {
            GoCard(Modifier.weight(0.34f).fillMaxHeight()) {
                Text("목적지", style = MaterialTheme.typography.titleLarge, color = secondaryText())
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    places.forEach { place ->
                        val selected = place.name == trip.destination.name
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 88.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (selected) GoboltTheme.energy.track else Color.Transparent)
                                .clickable { viewModel.setDestination(place) }
                                .padding(horizontal = 16.dp),
                        ) {
                            Icon(Icons.Rounded.Place, null, Modifier.size(36.dp), tint = secondaryText())
                            Spacer(Modifier.size(16.dp))
                            Text(place.name, style = MaterialTheme.typography.titleLarge, maxLines = 2)
                        }
                    }
                    if (navi.places.isEmpty()) {
                        Text(
                            "내비 즐겨찾기와 최근 목적지가 여기에 함께 표시돼요",
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryText(),
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(CardGap),
                modifier = Modifier
                    .weight(0.66f)
                    .fillMaxHeight(),
            ) {
                GoCard(Modifier.fillMaxWidth()) {
                    Stepper("출발일", trip.startDate.label()) { viewModel.shiftStartDate(it) }
                    Stepper("출발 시각", "%02d:%02d".format(trip.departMinutes / 60, trip.departMinutes % 60)) {
                        viewModel.shiftDepartTime(it * 30)
                    }
                    Stepper("숙박", "${trip.nights.size}박") { viewModel.setNightCount(trip.nights.size + it) }
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(CardGap),
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    trip.nights.forEachIndexed { night, stay ->
                        GoCard(Modifier.fillMaxWidth()) {
                            Text(
                                "${night + 1}일차 · ${trip.dateOf(night).label()}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = secondaryText(),
                            )
                            if (night == 0) {
                                Text("${trip.destination.name}까지 이동", style = MaterialTheme.typography.headlineMedium)
                            } else {
                                Stepper("현지 이동", "${trip.localKm[night - 1]}km") { delta ->
                                    viewModel.setLocalKm(night - 1, trip.localKm[night - 1] + delta * 10)
                                }
                            }
                            SegmentedTabs(
                                options = listOf(Stay.LODGING to "숙소", Stay.CAR_CAMP to "차박"),
                                selected = stay,
                                onSelect = { viewModel.setStay(night, it) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    GoCard(Modifier.fillMaxWidth()) {
                        Text(
                            "${trip.nights.size + 1}일차 · ${trip.dateOf(trip.nights.size).label()}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = secondaryText(),
                        )
                        Text("집으로 복귀", style = MaterialTheme.typography.headlineMedium)
                    }
                }
                PrimaryButton("계획 만들기", onMakePlan, Modifier.fillMaxWidth())
            }
        }
    }
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
            modifier = Modifier.width(220.dp),
        )
        IconTap(Icons.Rounded.Add, "$label 늘리기", { onStep(1) })
    }
}
