package app.gobolt.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gobolt.engine.Plan
import app.gobolt.engine.Strategy
import kotlin.math.roundToInt

@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val vehicle by viewModel.vehicleState.collectAsStateWithLifecycle()
    val navi by viewModel.naviState.collectAsStateWithLifecycle()
    val plans by viewModel.plans.collectAsStateWithLifecycle()

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .safeDrawingPadding()
                .padding(24.dp),
        ) {
            Panel("현재 차량", Modifier.weight(0.8f)) {
                Text(
                    vehicle.socPct?.let { "$it%" } ?: "--",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text("배터리 잔량", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("누적 주행 " + (vehicle.odometerKm?.let { "${it.roundToInt()}km" } ?: "--"))
                vehicle.error?.let {
                    Text("잔량 읽기 실패: $it", color = MaterialTheme.colorScheme.error)
                }
            }
            Panel("서울 → 부산 3박 4일", Modifier.weight(1.2f)) {
                plans.forEach { PlanCard(it) }
            }
            Panel("내비 연동 점검", Modifier.weight(1f)) {
                Text(if (navi.connected) "내비 연결됨" else "내비 연결 중")
                Button(onClick = viewModel::requestTrip, modifier = Modifier.fillMaxWidth()) {
                    Text("부산까지 예상값 받기")
                }
                OutlinedButton(onClick = viewModel::sendRoute, modifier = Modifier.fillMaxWidth()) {
                    Text("칠곡 경유 경로 보내기")
                }
                navi.log.forEach {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Panel(title: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier.fillMaxHeight()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun PlanCard(plan: Plan) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(strategyName(plan.strategy), fontWeight = FontWeight.Bold)
        if (plan.isFeasible) {
            val summary = plan.summary
            Text(
                "${summary.totalKwh.roundToInt()}kWh · 급속 ${summary.fastCharges}회 " +
                    "${summary.fastMinutes.roundToInt()}분 · 완속 ${summary.slowCharges}회",
            )
            Text(
                plan.stops.joinToString(" → ") { it.name },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("도착 ${summary.endSocPct.roundToInt()}% · 최저 ${summary.minSocPct.roundToInt()}%")
        } else {
            Text("현재 조건으로는 어려운 일정", color = MaterialTheme.colorScheme.error)
        }
    }
}

private fun strategyName(strategy: Strategy) = when (strategy) {
    Strategy.FEWEST_STOPS -> "충전 횟수 최소"
    Strategy.FASTEST -> "시간 최소"
    Strategy.MOST_MARGIN -> "잔량 여유 최대"
}
