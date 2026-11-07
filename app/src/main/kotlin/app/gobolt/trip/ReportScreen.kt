package app.gobolt.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import app.gobolt.ui.PrimaryButton
import app.gobolt.ui.pct
import app.gobolt.ui.rounded
import app.gobolt.ui.secondaryText

@Composable
fun ReportScreen(viewModel: GoboltViewModel, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val plans by viewModel.plans.collectAsStateWithLifecycle()
    val strategy by viewModel.strategy.collectAsStateWithLifecycle()
    val plan = plans.firstOrNull { it.strategy == strategy } ?: return
    val summary = plan.summary

    Column(verticalArrangement = Arrangement.spacedBy(CardGap), modifier = modifier) {
        Text("운행 리포트", style = MaterialTheme.typography.headlineLarge)
        Text(
            "서울 → 부산 3박 4일",
            style = MaterialTheme.typography.titleLarge,
            color = secondaryText(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(CardGap), modifier = Modifier.fillMaxWidth()) {
            Tile("${summary.totalKm.rounded()}km", "총 주행", Modifier.weight(1f))
            Tile("${summary.totalKwh.rounded()}kWh", "총 전기 사용", Modifier.weight(1f))
            Tile("${plan.stops.size}회", "충전 · 급속 ${summary.fastMinutes.rounded()}분", Modifier.weight(1f))
            Tile(pct(summary.endSocPct), "도착 잔량", Modifier.weight(1f))
        }
        GoCard(Modifier.fillMaxWidth()) {
            Text("계획과 실제의 차이", style = MaterialTheme.typography.headlineMedium)
            CompareRow("", "계획", "실제", header = true)
            CompareRow("전기 사용", "${summary.totalKwh.rounded()}kWh", NO_RECORD)
            CompareRow("도착 잔량", pct(summary.endSocPct), NO_RECORD)
            CompareRow("급속 충전 시간", "${summary.fastMinutes.rounded()}분", NO_RECORD)
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("홈으로", onDone, Modifier.fillMaxWidth())
    }
}

private const val NO_RECORD = "운행 기록 없음"

@Composable
private fun Tile(value: String, label: String, modifier: Modifier = Modifier) {
    GoCard(modifier) {
        Text(value, style = MaterialTheme.typography.headlineLarge, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = secondaryText())
    }
}

@Composable
private fun CompareRow(label: String, planned: String, actual: String, header: Boolean = false) {
    val style = if (header) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleLarge
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, style = style, modifier = Modifier.weight(1f))
        Text(planned, style = style, color = if (header) secondaryText() else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(actual, style = style, color = secondaryText(), modifier = Modifier.weight(1f))
    }
}
