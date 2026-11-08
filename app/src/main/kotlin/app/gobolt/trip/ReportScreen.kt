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
    val saved by viewModel.record.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(CardGap), modifier = modifier) {
        Text("운행 리포트", style = MaterialTheme.typography.headlineLarge)
        val record = saved
        if (record == null) {
            GoCard(Modifier.fillMaxWidth()) {
                Text("아직 저장된 운행 기록이 없어요", style = MaterialTheme.typography.headlineMedium)
            }
        } else {
            Text("${record.name}까지", style = MaterialTheme.typography.titleLarge, color = secondaryText())
            Row(horizontalArrangement = Arrangement.spacedBy(CardGap), modifier = Modifier.fillMaxWidth()) {
                Tile("${record.km.rounded()}km", "총 주행", Modifier.weight(1f))
                Tile("${record.usedKwh.rounded()}kWh", "총 전기 사용", Modifier.weight(1f))
                Tile("${record.minutes.rounded()}분", "운행 시간", Modifier.weight(1f))
                Tile(pct(record.endSocPct), "도착 잔량", Modifier.weight(1f))
            }
            GoCard(Modifier.fillMaxWidth()) {
                Text("계획과 실제의 차이", style = MaterialTheme.typography.headlineMedium)
                CompareRow("", "계획", "실제", header = true)
                CompareRow("전기 사용", "${record.plannedKwh.rounded()}kWh", "${record.usedKwh.rounded()}kWh")
                CompareRow("도착 잔량", pct(record.plannedEndSocPct), pct(record.endSocPct))
            }
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("홈으로", onDone, Modifier.fillMaxWidth())
    }
}

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
    val color = if (header) secondaryText() else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, style = style, modifier = Modifier.weight(1f))
        Text(planned, style = style, color = color, modifier = Modifier.weight(1f))
        Text(actual, style = style, color = color, modifier = Modifier.weight(1f))
    }
}
