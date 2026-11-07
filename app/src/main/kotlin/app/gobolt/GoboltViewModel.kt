package app.gobolt

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.gobolt.demo.DemoTrip
import app.gobolt.demo.Stay
import app.gobolt.demo.TripInput
import app.gobolt.engine.Activity
import app.gobolt.engine.ChargeStop
import app.gobolt.engine.ChargerOption
import app.gobolt.engine.EventKind
import app.gobolt.engine.Plan
import app.gobolt.engine.Planner
import app.gobolt.engine.RoadType
import app.gobolt.engine.Strategy
import app.gobolt.engine.Vehicle
import app.gobolt.navi.NaviGateway
import app.gobolt.vehicle.VehicleGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class GoboltViewModel(application: Application) : AndroidViewModel(application) {
    private val car = VehicleGateway(application)
    private val navi = NaviGateway(application)
    private val model = Vehicle.ioniq9Demo()
    private val planner = Planner(model)

    val vehicleState = car.state
    val naviState = navi.state

    private val startSoc: Flow<Double> = car.state
        .map { (it.socPct ?: DEFAULT_SOC_PCT).toDouble() }
        .distinctUntilChanged()

    private val _tripInput = MutableStateFlow(TripInput())
    val tripInput: StateFlow<TripInput> = _tripInput.asStateFlow()

    private val _strategy = MutableStateFlow(Strategy.FEWEST_STOPS)
    val strategy: StateFlow<Strategy> = _strategy.asStateFlow()

    val plans: StateFlow<List<Plan>> = combine(startSoc, _tripInput) { soc, input ->
        planner.planAll(DemoTrip.schedule(soc, input))
    }.stateInUi(emptyList())

    // 차박 전후 비교 기준 / Baseline for the before and after comparison
    val lodgingPlans: StateFlow<List<Plan>> = combine(startSoc, _tripInput) { soc, input ->
        val lodging = input.copy(nights = input.nights.map { Stay.LODGING })
        planner.planAll(DemoTrip.schedule(soc, lodging))
    }.stateInUi(emptyList())

    val weekPlan: StateFlow<Plan?> = startSoc
        .map<Double, Plan?> { planner.plan(DemoTrip.week(it)) }
        .stateInUi(null)

    init {
        car.start(viewModelScope)
        navi.start()
    }

    fun rangeKm(socPct: Int): Double =
        model.kwhOf(socPct.toDouble()) * model.kmPerKwh(RoadType.MIXED, null)

    fun selectStrategy(strategy: Strategy) {
        _strategy.value = strategy
    }

    fun setLocalKm(index: Int, km: Int) = _tripInput.update { input ->
        input.copy(localKm = input.localKm.mapIndexed { i, old -> if (i == index) km.coerceIn(0, 300) else old })
    }

    fun setStay(night: Int, stay: Stay) = _tripInput.update { input ->
        input.copy(nights = input.nights.mapIndexed { i, old -> if (i == night) stay else old })
    }

    fun alternatives(plan: Plan, stop: ChargeStop): List<ChargerOption> {
        if (stop.chargerId == null) return emptyList()
        val start = plan.events.firstOrNull { it.dayIndex == stop.dayIndex && it.kind == EventKind.DRIVE }
            ?: return emptyList()
        val schedule = DemoTrip.schedule(start.fromPct, _tripInput.value)
        val drive = schedule.days[stop.dayIndex].activities.filterIsInstance<Activity.Drive>().firstOrNull()
            ?: return emptyList()
        return planner.chargerOptions(drive, start.fromPct).filter { it.charger.id != stop.chargerId }
    }

    fun startGuidance(plan: Plan) {
        val waypoints = plan.stops
            .filter { it.dayIndex == 0 && it.fast }
            .mapNotNull { DemoTrip.placeOf(it.chargerId) }
        navi.sendRoute(DemoTrip.busan, waypoints)
    }

    fun requestTrip() = navi.requestTrip(DemoTrip.busan)

    fun sendTestRoute() = navi.sendRoute(DemoTrip.busan, listOf(DemoTrip.chilgok))

    override fun onCleared() {
        navi.stop()
        car.stop()
    }

    private fun <T> Flow<T>.stateInUi(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    private companion object {
        const val DEFAULT_SOC_PCT = 80
    }
}
