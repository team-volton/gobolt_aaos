package app.gobolt.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Work
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gobolt.GoboltViewModel
import app.gobolt.data.Trip
import app.gobolt.data.summary
import app.gobolt.engine.EventKind
import app.gobolt.engine.Plan
import app.gobolt.ui.CardGap
import app.gobolt.ui.GoCard
import app.gobolt.ui.GoboltTheme
import app.gobolt.ui.IconTap
import app.gobolt.ui.Metric
import app.gobolt.ui.PrimaryButton
import app.gobolt.ui.SecondaryButton
import app.gobolt.ui.SegmentedTabs
import app.gobolt.ui.SocBar
import app.gobolt.ui.pct
import app.gobolt.ui.rounded
import app.gobolt.ui.secondaryText

enum class DriveMode { Daily, Travel }

@Composable
fun HomeScreen(
    viewModel: GoboltViewModel,
    mode: DriveMode,
    onModeChange: (DriveMode) -> Unit,
    onNewTrip: () -> Unit,
    onOpenTrip: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vehicle by viewModel.vehicleState.collectAsStateWithLifecycle()
    val week by viewModel.weekPlan.collectAsStateWithLifecycle()
    val plans by viewModel.plans.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val trip by viewModel.trip.collectAsStateWithLifecycle()
    val floor = settings.floorPct.toDouble()
    var fieldTrip by remember { mutableStateOf(false) }

    if (fieldTrip) {
        FieldTripDialog(
            preview = viewModel::previewFieldTrip,
            onApply = {
                viewModel.applyFieldTrip(it)
                fieldTrip = false
            },
            onDismiss = { fieldTrip = false },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(CardGap), modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SearchBar(onNewTrip, Modifier.weight(1f))
            Spacer(Modifier.size(12.dp))
            IconTap(Icons.Rounded.Settings, "설정", onSettings)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CardGap), modifier = Modifier.weight(1f)) {
            Column(
                verticalArrangement = Arrangement.spacedBy(CardGap),
                modifier = Modifier.weight(0.36f).fillMaxHeight(),
            ) {
                GoCard(Modifier.fillMaxWidth().weight(1f)) {
                    Text("현재 잔량", style = MaterialTheme.typography.titleLarge, color = secondaryText())
                    Text(
                        vehicle.socPct?.let { "$it%" } ?: "--",
                        style = MaterialTheme.typography.displayLarge,
                    )
                    SocBar((vehicle.socPct ?: 0).toDouble(), floor)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        vehicle.socPct?.let { "주행 가능 약 ${viewModel.rangeKm(it).rounded()}km" } ?: "잔량 확인 중",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    vehicle.error?.let {
                        Text(
                            "잔량을 읽지 못했어요",
                            style = MaterialTheme.typography.bodyLarge,
                            color = GoboltTheme.energy.warning,
                        )
                    }
                }
                GoCard(Modifier.fillMaxWidth()) {
                    Text("즐겨찾기", style = MaterialTheme.typography.titleLarge, color = secondaryText())
                    Favorite(Icons.Rounded.Home, "집", onNewTrip)
                    Favorite(Icons.Rounded.Work, "회사", onNewTrip)
                    Favorite(Icons.Rounded.Star, "자주 가는 곳", onNewTrip)
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(CardGap),
                modifier = Modifier.weight(0.64f).fillMaxHeight(),
            ) {
                SegmentedTabs(
                    options = listOf(DriveMode.Daily to "일상", DriveMode.Travel to "여행"),
                    selected = mode,
                    onSelect = onModeChange,
                    modifier = Modifier.fillMaxWidth(),
                )
                when (mode) {
                    DriveMode.Daily -> DailyContent(week, viewModel.weekLabels, floor, { fieldTrip = true }, Modifier.weight(1f))
                    DriveMode.Travel -> TravelContent(trip, plans, onOpenTrip, onNewTrip, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SearchBar(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(96.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 32.dp),
    ) {
        Icon(Icons.Rounded.Search, null, Modifier.size(44.dp), tint = secondaryText())
        Spacer(Modifier.size(16.dp))
        Text("어디까지 고볼트?", style = MaterialTheme.typography.headlineMedium, color = secondaryText())
    }
}

@Composable
private fun Favorite(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
    ) {
        Icon(icon, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.titleLarge)
    }
}

private data class WeekDay(val label: String, val km: Double, val homeSoc: Double, val charges: Boolean)

private fun weekDays(plan: Plan, labels: List<String>): List<WeekDay> {
    var soc = plan.events.firstOrNull()?.fromPct ?: 0.0
    return labels.mapIndexed { index, label ->
        val events = plan.events.filter { it.dayIndex == index }
        val use = events.filter { it.kind != EventKind.SLOW_CHARGE && it.kind != EventKind.FAST_CHARGE }
        val home = use.lastOrNull()?.toPct ?: soc
        soc = events.lastOrNull()?.toPct ?: soc
        WeekDay(label, use.sumOf { it.km }, home, plan.stops.any { it.dayIndex == index })
    }
}

@Composable
private fun DailyContent(
    plan: Plan?,
    labels: List<String>,
    floor: Double,
    onFieldTrip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (plan == null) return
    val days = weekDays(plan, labels)
    val today = days.first()
    val nextCharge = days.firstOrNull { it.charges }

    Column(verticalArrangement = Arrangement.spacedBy(CardGap), modifier = modifier) {
        GoCard(Modifier.fillMaxWidth().weight(1f)) {
            Text("오늘 (${today.label})", style = MaterialTheme.typography.titleLarge, color = secondaryText())
            Text(pct(today.homeSoc), style = MaterialTheme.typography.displayMedium)
            Text("귀가 예상 잔량 · 오늘 ${today.km.rounded()}km", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            ChargeBanner(nextCharge)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            days.forEachIndexed { index, day ->
                DayCell(day, today = index == 0, floor = floor, modifier = Modifier.weight(1f))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            Metric("${plan.summary.totalKm.rounded()}km", "이번 주 총 주행")
            Metric("${plan.summary.totalKwh.rounded()}kWh", "이번 주 총 전기 사용")
            Spacer(Modifier.weight(1f))
            SecondaryButton("외근 추가", onFieldTrip, icon = Icons.Rounded.Add)
        }
    }
}

@Composable
private fun ChargeBanner(day: WeekDay?) {
    val text = if (day != null) "${day.label}요일 밤 완속 충전" else "이번 주는 충전하지 않아도 돼요"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(GoboltTheme.energy.track)
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        Icon(Icons.Rounded.Bolt, null, Modifier.size(40.dp), tint = GoboltTheme.energy.charge)
        Spacer(Modifier.size(12.dp))
        Text(text, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun DayCell(day: WeekDay, today: Boolean, floor: Double, modifier: Modifier = Modifier) {
    GoCard(modifier = modifier, selected = today) {
        Text(day.label, style = MaterialTheme.typography.titleLarge)
        Text("${day.km.rounded()}km", style = MaterialTheme.typography.bodyMedium, color = secondaryText())
        Text(pct(day.homeSoc), style = MaterialTheme.typography.titleLarge, maxLines = 1)
        SocBar(day.homeSoc, floor)
        Box(Modifier.height(40.dp)) {
            if (day.charges) {
                Icon(Icons.Rounded.Bolt, "충전 필요", Modifier.size(40.dp), tint = GoboltTheme.energy.charge)
            }
        }
    }
}

@Composable
private fun TravelContent(
    trip: Trip,
    plans: List<Plan>,
    onOpenTrip: () -> Unit,
    onNewTrip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val plan = plans.firstOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(CardGap), modifier = modifier) {
        GoCard(Modifier.fillMaxWidth(), onClick = onOpenTrip) {
            Text("내 여행", style = MaterialTheme.typography.titleLarge, color = secondaryText())
            Text("${trip.destination.name} 여행", style = MaterialTheme.typography.headlineLarge)
            Text(trip.summary(), style = MaterialTheme.typography.titleLarge)
            if (plan != null && plan.isFeasible) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                    Metric("${plan.summary.totalKm.rounded()}km", "총 주행")
                    Metric("${plan.summary.totalKwh.rounded()}kWh", "총 전기 사용")
                    Metric("${plan.stops.size}회", "충전")
                    Metric(pct(plan.summary.endSocPct), "복귀 잔량")
                }
            }
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("새 여행", onNewTrip, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add)
    }
}
