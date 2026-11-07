package app.gobolt.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gobolt.GoboltViewModel
import app.gobolt.ui.CardGap
import app.gobolt.ui.GoCard
import app.gobolt.ui.ScreenHeader
import app.gobolt.ui.SecondaryButton
import app.gobolt.ui.secondaryText
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(viewModel: GoboltViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vehicle by viewModel.vehicleState.collectAsStateWithLifecycle()
    val navi by viewModel.naviState.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(CardGap), modifier = modifier) {
        ScreenHeader("설정", onBack)
        Row(horizontalArrangement = Arrangement.spacedBy(CardGap), modifier = Modifier.weight(1f)) {
            GoCard(Modifier.weight(0.4f).fillMaxHeight()) {
                Text("계획 기준", style = MaterialTheme.typography.headlineMedium)
                Line("안전 하한", "20%")
                Line("충전 목표", "80%")
                Line("집 충전", "공용 완속")
                Text("차량 정보", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 24.dp))
                Line("잔량", vehicle.socPct?.let { "$it%" } ?: "--")
                Line("누적 주행", vehicle.odometerKm?.let { "${it.roundToInt()}km" } ?: "--")
            }
            GoCard(Modifier.weight(0.6f).fillMaxHeight()) {
                Text("내비 연동 점검", style = MaterialTheme.typography.headlineMedium)
                Text(
                    if (navi.connected) "내비 연결됨" else "내비 연결 중",
                    style = MaterialTheme.typography.titleLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SecondaryButton("부산까지 예상값", viewModel::requestTrip, Modifier.weight(1f))
                    SecondaryButton("칠곡 경유 경로", viewModel::sendTestRoute, Modifier.weight(1f))
                }
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    navi.log.forEach {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = secondaryText())
                    }
                }
            }
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleLarge, color = secondaryText())
    }
}
