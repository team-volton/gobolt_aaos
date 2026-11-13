package app.gobolt.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gobolt.GoboltViewModel
import app.gobolt.data.DriveRecord
import app.gobolt.data.label
import app.gobolt.ui.CardGap
import app.gobolt.ui.GoCard
import app.gobolt.ui.PrimaryButton
import app.gobolt.ui.Spacing
import app.gobolt.ui.pct
import app.gobolt.ui.rounded
import app.gobolt.ui.secondaryText
import java.time.LocalDate

@Composable
fun ReportScreen(viewModel: GoboltViewModel, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val records by viewModel.records.collectAsStateWithLifecycle()
    val latest = records.firstOrNull()

    Column(verticalArrangement = Arrangement.spacedBy(CardGap), modifier = modifier) {
        Text("운행 리포트", style = MaterialTheme.typography.headlineLarge)
        Column(
            verticalArrangement = Arrangement.spacedBy(CardGap),
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        ) {
            if (latest == null) {
                GoCard(Modifier.fillMaxWidth()) {
                    Text("아직 저장된 운행 기록이 없어요", style = MaterialTheme.typography.headlineMedium)
                }
            } else {
                Text("${latest.name}까지", style = MaterialTheme.typography.titleLarge, color = secondaryText())
                Row(horizontalArrangement = Arrangement.spacedBy(CardGap), modifier = Modifier.fillMaxWidth()) {
                    Tile("${latest.km.rounded()}km", "총 주행", Modifier.weight(1f))
                    Tile("${latest.usedKwh.rounded()}kWh", "총 전기 사용", Modifier.weight(1f))
                    Tile("${latest.minutes.rounded()}분", "운행 시간", Modifier.weight(1f))
                    Tile(pct(latest.endSocPct), "도착 잔량", Modifier.weight(1f))
                }
                GoCard(Modifier.fillMaxWidth()) {
                    Text("계획과 실제의 차이", style = MaterialTheme.typography.headlineMedium)
                    CompareRow("", "계획", "실제", header = true)
                    CompareRow("전기 사용", "${latest.plannedKwh.rounded()}kWh", "${latest.usedKwh.rounded()}kWh")
                    CompareRow("도착 잔량", pct(latest.plannedEndSocPct), pct(latest.endSocPct))
                }
                viewModel.tripTotals()?.let { (count, km, kwh) ->
                    GoCard(Modifier.fillMaxWidth()) {
                        Text("이번 여행 합계", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "운행 ${count}회 · ${km.rounded()}km · ${kwh.rounded()}kWh",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
                if (records.size > 1) {
                    GoCard(Modifier.fillMaxWidth()) {
                        Text("지난 운행", style = MaterialTheme.typography.headlineMedium)
                        records.drop(1).forEach { HistoryRow(it) }
                    }
                }
            }
        }
        PrimaryButton("홈으로", onDone, Modifier.fillMaxWidth())
    }
}

@Composable
private fun HistoryRow(record: DriveRecord) {
    val date = if (record.epochDay > 0) LocalDate.ofEpochDay(record.epochDay).label() else ""
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
        Text("$date ${record.name}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Text(
            "${record.km.rounded()}km · ${record.usedKwh.rounded()}kWh · 도착 ${pct(record.endSocPct)}",
            style = MaterialTheme.typography.bodyLarge,
            color = secondaryText(),
        )
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
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
        Text(label, style = style, modifier = Modifier.weight(1f))
        Text(planned, style = style, color = color, modifier = Modifier.weight(1f))
        Text(actual, style = style, color = color, modifier = Modifier.weight(1f))
    }
}
