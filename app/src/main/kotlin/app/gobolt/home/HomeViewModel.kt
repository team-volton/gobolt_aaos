package app.gobolt.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.gobolt.demo.DemoTrip
import app.gobolt.engine.Plan
import app.gobolt.engine.Planner
import app.gobolt.engine.Vehicle
import app.gobolt.navi.NaviGateway
import app.gobolt.vehicle.VehicleGateway
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val vehicle = VehicleGateway(application)
    private val navi = NaviGateway(application)
    private val planner = Planner(Vehicle.ioniq9Demo())

    val vehicleState = vehicle.state
    val naviState = navi.state

    val plans: StateFlow<List<Plan>> = vehicle.state
        .map { planner.planAll(DemoTrip.schedule((it.socPct ?: DEFAULT_SOC_PCT).toDouble())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        vehicle.start(viewModelScope)
        navi.start()
    }

    fun requestTrip() = navi.requestTrip(DemoTrip.busan)

    fun sendRoute() = navi.sendRoute(DemoTrip.busan, listOf(DemoTrip.chilgok))

    override fun onCleared() {
        navi.stop()
        vehicle.stop()
    }

    private companion object {
        const val DEFAULT_SOC_PCT = 80
    }
}
