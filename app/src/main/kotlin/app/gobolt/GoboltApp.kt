package app.gobolt

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gobolt.home.DriveMode
import app.gobolt.home.HomeScreen
import app.gobolt.settings.SettingsScreen
import app.gobolt.trip.DriveLock
import app.gobolt.trip.DrivingScreen
import app.gobolt.trip.PlanScreen
import app.gobolt.trip.ReportScreen
import app.gobolt.trip.TripInputScreen
import app.gobolt.ui.ScreenPadding

private enum class Screen { Home, TripInput, Plan, Driving, Report, Settings }

// 주행 중 입력을 막는 화면 / Screens that take input and lock while driving
private val INPUT_SCREENS = setOf(Screen.TripInput, Screen.Settings)

@Composable
fun GoboltApp(viewModel: GoboltViewModel) {
    val stack = remember { mutableStateListOf(Screen.Home) }
    var mode by remember { mutableStateOf(DriveMode.Daily) }

    fun open(screen: Screen) {
        stack.add(screen)
    }

    fun back() {
        stack.removeAt(stack.lastIndex)
    }

    fun goHome() {
        stack.clear()
        stack.add(Screen.Home)
    }

    BackHandler(enabled = stack.size > 1) { back() }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        val content = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(ScreenPadding)
        val vehicle by viewModel.vehicleState.collectAsStateWithLifecycle()
        val locked = vehicle.driving && stack.last() in INPUT_SCREENS
        if (locked) {
            DriveLock(onBack = ::back, modifier = content)
        } else when (stack.last()) {
            Screen.Home -> HomeScreen(
                viewModel = viewModel,
                mode = mode,
                onModeChange = { mode = it },
                onNewTrip = { open(Screen.TripInput) },
                onOpenTrip = { open(Screen.Plan) },
                onSettings = { open(Screen.Settings) },
                modifier = content,
            )
            Screen.TripInput -> TripInputScreen(
                viewModel = viewModel,
                onBack = ::back,
                onMakePlan = { open(Screen.Plan) },
                modifier = content,
            )
            Screen.Plan -> PlanScreen(
                viewModel = viewModel,
                onBack = ::back,
                onStarted = { open(Screen.Driving) },
                modifier = content,
            )
            Screen.Driving -> DrivingScreen(
                viewModel = viewModel,
                onFinish = {
                    viewModel.finishDrive()
                    back()
                    open(Screen.Report)
                },
                modifier = content,
            )
            Screen.Report -> ReportScreen(viewModel = viewModel, onDone = ::goHome, modifier = content)
            Screen.Settings -> SettingsScreen(viewModel = viewModel, onBack = ::back, modifier = content)
        }
    }
}
