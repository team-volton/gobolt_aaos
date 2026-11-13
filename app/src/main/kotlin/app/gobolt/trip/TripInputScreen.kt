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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gobolt.GoboltViewModel
import app.gobolt.data.Stay
import app.gobolt.data.Trip
import app.gobolt.data.label
import app.gobolt.navi.NaviPlace
import app.gobolt.ui.CardGap
import app.gobolt.ui.GoCard
import app.gobolt.ui.GoboltTheme
import app.gobolt.ui.IconTap
import app.gobolt.ui.PrimaryButton
import app.gobolt.ui.ScreenHeader
import app.gobolt.ui.SegmentedTabs
import app.gobolt.ui.rounded
import app.gobolt.ui.secondaryText

private enum class PickTarget { DESTINATION, WAYPOINT }

@Composable
fun TripInputScreen(
    viewModel: GoboltViewModel,
    onBack: () -> Unit,
    onMakePlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val trip by viewModel.trip.collectAsStateWithLifecycle()
    val navi by viewModel.naviState.collectAsStateWithLifecycle()
    var target by remember { mutableStateOf(PickTarget.DESTINATION) }
    val legs = viewModel.legs(trip)
    val totalKm = legs.outKm + legs.backKm + trip.localKm.sum()
    val places = (navi.places + viewModel.presetPlaces).filter { it.name.isNotBlank() }.distinctBy { it.name }
    LaunchedEffect(Unit) { viewModel.refreshNaviChargers() }

    Column(verticalArrangement = Arrangement.spacedBy(CardGap), modifier = modifier) {
        ScreenHeader(
            "${trip.destination.name} 여행",
            onBack,
            "총 예상 ${totalKm.rounded()}km · 편도 ${legs.outKm.rounded()}km " +
                if (legs.measured) "(내비 계산)" else "(추정)",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(CardGap), modifier = Modifier.weight(1f)) {
            GoCard(Modifier.weight(0.34f).fillMaxHeight()) {
                SegmentedTabs(
                    options = listOf(PickTarget.DESTINATION to "목적지", PickTarget.WAYPOINT to "경유지"),
                    selected = target,
                    onSelect = { target = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (target == PickTarget.WAYPOINT) {
                        PlaceRow("경유지 없음", trip.waypoint == null) { viewModel.setWaypoint(null) }
                    }
                    places.forEach { place ->
                        val selected = when (target) {
                            PickTarget.DESTINATION -> place.name == trip.destination.name
                            PickTarget.WAYPOINT -> place.name == trip.waypoint?.name
                        }
                        PlaceRow(place.name, selected) {
                            when (target) {
                                PickTarget.DESTINATION -> viewModel.setDestination(place)
                                PickTarget.WAYPOINT -> viewModel.setWaypoint(place)
                            }
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
                modifier = Modifier.weight(0.66f).fillMaxHeight(),
            ) {
                GoCard(Modifier.fillMaxWidth()) {
                    Stepper("출발일", trip.startDate.label()) { viewModel.shiftStartDate(it) }
                    Stepper("출발 시각", "%02d:%02d".format(trip.departMinutes / 60, trip.departMinutes % 60)) {
                        viewModel.shiftDepartTime(it * 30)
                    }
                    Stepper("숙박", "${trip.nights.size}박") { viewModel.setNightCount(trip.nights.size + it) }
                    Stepper("전체 기온", "${trip.outsideC}°C") { viewModel.shiftOutsideTemp(it) }
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(CardGap),
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                ) {
                    (0..trip.nights.size).forEach { day -> DayCard(viewModel, trip, day) }
                }
                PrimaryButton("계획 만들기", onMakePlan, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun DayCard(viewModel: GoboltViewModel, trip: Trip, day: Int) {
    val lastDay = trip.nights.size
    GoCard(Modifier.fillMaxWidth()) {
        Text("${day + 1}일차 · ${trip.dateOf(day).label()}", style = MaterialTheme.typography.bodyLarge, color = secondaryText())
        when (day) {
            0 -> Text(
                trip.waypoint?.let { "${it.name} 거쳐 ${trip.destination.name}까지" } ?: "${trip.destination.name}까지 이동",
                style = MaterialTheme.typography.headlineMedium,
            )
            lastDay -> Text("집으로 복귀", style = MaterialTheme.typography.headlineMedium)
            else -> {
                val index = day - 1
                val byHours = trip.localHours.getOrElse(index) { 0 } > 0
                SegmentedTabs(
                    options = listOf(false to "이동 거리", true to "사용 시간"),
                    selected = byHours,
                    onSelect = { viewModel.setLocalHours(index, if (it) DEFAULT_HOURS else 0) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (byHours) {
                    Stepper("차량 사용", "${trip.localHours[index]}시간") { viewModel.setLocalHours(index, trip.localHours[index] + it) }
                } else {
                    Stepper("현지 이동", "${trip.localKm[index]}km") { viewModel.setLocalKm(index, trip.localKm[index] + it * 10) }
                }
                Stepper("정차 대기", "${trip.waitHours.getOrElse(index) { 0 }}시간") {
                    viewModel.setWaitHours(index, trip.waitHours.getOrElse(index) { 0 } + it)
                }
            }
        }
        Stepper("기온", "${trip.tempOf(day)}°C") { viewModel.setDayTemp(day, trip.tempOf(day) + it) }
        if (day < lastDay) {
            SegmentedTabs(
                options = listOf(Stay.LODGING to "숙소", Stay.CAR_CAMP to "차박"),
                selected = trip.nights[day],
                onSelect = { viewModel.setStay(day, it) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private const val DEFAULT_HOURS = 5

@Composable
private fun PlaceRow(name: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) GoboltTheme.energy.track else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        Icon(Icons.Rounded.Place, null, Modifier.size(36.dp), tint = secondaryText())
        Spacer(Modifier.size(16.dp))
        Text(name, style = MaterialTheme.typography.titleLarge, maxLines = 2)
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
