package app.gobolt

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.gobolt.data.AppSettings
import app.gobolt.data.AppStore
import app.gobolt.data.DriveRecord
import app.gobolt.demo.DemoTrip
import app.gobolt.demo.Stay
import app.gobolt.demo.TripInput
import app.gobolt.engine.Activity
import app.gobolt.engine.Calibration
import app.gobolt.engine.Calibrator
import app.gobolt.engine.ChargeStop
import app.gobolt.engine.ChargerOption
import app.gobolt.engine.DriveCheck
import app.gobolt.engine.EventKind
import app.gobolt.engine.Plan
import app.gobolt.engine.Planner
import app.gobolt.engine.PlannerConfig
import app.gobolt.engine.RoadType
import app.gobolt.engine.Strategy
import app.gobolt.engine.Vehicle
import app.gobolt.engine.checkWhileDriving
import app.gobolt.navi.NaviGateway
import app.gobolt.navi.LiveLeg
import app.gobolt.vehicle.VehicleGateway
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class GoboltViewModel(application: Application) : AndroidViewModel(application) {
    private val store = AppStore(application)
    private val car = VehicleGateway(application)
    private val navi = NaviGateway(application)
    private val calibrator = Calibrator()

    val vehicleState = car.state
    val naviState = navi.state

    private val _settings = MutableStateFlow(store.loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val efficiencyFactor = MutableStateFlow(store.loadEfficiencyFactor())

    private val _record = MutableStateFlow(store.loadRecord())
    val record: StateFlow<DriveRecord?> = _record.asStateFlow()

    private val _tripInput = MutableStateFlow(store.loadTrip())
    val tripInput: StateFlow<TripInput> = _tripInput.asStateFlow()

    private val _strategy = MutableStateFlow(Strategy.FEWEST_STOPS)
    val strategy: StateFlow<Strategy> = _strategy.asStateFlow()

    private val _excludedChargers = MutableStateFlow(emptySet<String>())
    val excludedChargers: StateFlow<Set<String>> = _excludedChargers.asStateFlow()

    val todayIndex: Int = LocalDate.now().dayOfWeek.value - 1
    val weekLabels: List<String> = DemoTrip.weekdaysFrom(todayIndex)

    private val startSoc: Flow<Double> = car.state
        .map { (it.socPct ?: DEFAULT_SOC_PCT).toDouble() }
        .distinctUntilChanged()

    private val model: Flow<Vehicle> = efficiencyFactor.map(::vehicleWith)

    private val planner: Flow<Planner> = combine(model, _settings) { vehicle, settings ->
        Planner(vehicle, settings.toConfig())
    }

    val plans: StateFlow<List<Plan>> = combine(planner, startSoc, _tripInput, _excludedChargers) { planner, soc, input, excluded ->
        planner.planAll(DemoTrip.schedule(soc, input), excluded)
    }.stateInUi(emptyList())

    // 차박 전후 비교 기준 / Baseline for the before and after comparison
    val lodgingPlans: StateFlow<List<Plan>> = combine(planner, startSoc, _tripInput, _excludedChargers) { planner, soc, input, excluded ->
        val lodging = input.copy(nights = input.nights.map { Stay.LODGING })
        planner.planAll(DemoTrip.schedule(soc, lodging), excluded)
    }.stateInUi(emptyList())

    val weekPlan: StateFlow<Plan?> = combine(planner, startSoc, _settings) { planner, soc, settings ->
        planner.plan(DemoTrip.week(soc, todayIndex, settings))
    }.stateInUi(null)

    val selectedPlan: StateFlow<Plan?> = combine(plans, _strategy) { plans, strategy ->
        plans.firstOrNull { it.strategy == strategy }
    }.stateInUi(null)

    private var session: DriveSession? = null

    private class DriveSession(
        val name: String,
        val startSoc: Double,
        val startOdometerKm: Float?,
        val startedAt: Long,
        val plannedKwh: Double,
        val plannedEndSoc: Double,
        var lastSoc: Double,
        var chargedPct: Double = 0.0,
    )

    init {
        car.start(viewModelScope)
        navi.start()
        car.state.onEach { state ->
            val soc = state.socPct?.toDouble() ?: return@onEach
            session?.let { active ->
                // 잔량이 오른 만큼은 충전분 / Any rise in SOC counts as charging
                if (soc > active.lastSoc) active.chargedPct += soc - active.lastSoc
                active.lastSoc = soc
            }
        }.launchIn(viewModelScope)
        navi.state.map { it.arrivedKm }.distinctUntilChanged().onEach { km ->
            if (km != null) finishDrive(km)
        }.launchIn(viewModelScope)
    }

    fun rangeKm(socPct: Int): Double {
        val vehicle = vehicleWith(efficiencyFactor.value)
        return vehicle.kwhOf(socPct.toDouble()) * vehicle.kmPerKwh(RoadType.MIXED, null)
    }

    fun selectStrategy(strategy: Strategy) {
        _strategy.value = strategy
    }

    fun updateSettings(change: (AppSettings) -> AppSettings) {
        _settings.update(change)
        store.save(_settings.value)
    }

    fun setLocalKm(index: Int, km: Int) = updateTrip { input ->
        input.copy(localKm = input.localKm.mapIndexed { i, old -> if (i == index) km.coerceIn(0, 300) else old })
    }

    fun setStay(night: Int, stay: Stay) = updateTrip { input ->
        input.copy(nights = input.nights.mapIndexed { i, old -> if (i == night) stay else old })
    }

    private fun updateTrip(change: (TripInput) -> TripInput) {
        _tripInput.update(change)
        store.save(_tripInput.value)
    }

    fun toggleCharger(id: String) = _excludedChargers.update { if (id in it) it - id else it + id }

    fun alternatives(plan: Plan, stop: ChargeStop): List<ChargerOption> {
        if (stop.chargerId == null) return emptyList()
        val start = plan.events.firstOrNull { it.dayIndex == stop.dayIndex && it.kind == EventKind.DRIVE }
            ?: return emptyList()
        val schedule = DemoTrip.schedule(start.fromPct, _tripInput.value)
        val drive = schedule.days[stop.dayIndex].activities.filterIsInstance<Activity.Drive>().firstOrNull()
            ?: return emptyList()
        return Planner(vehicleWith(efficiencyFactor.value), _settings.value.toConfig())
            .chargerOptions(drive, start.fromPct)
            .filter { it.charger.id != stop.chargerId }
    }

    fun startGuidance(plan: Plan) {
        val waypoints = plan.stops
            .filter { it.dayIndex == 0 && it.fast }
            .mapNotNull { DemoTrip.placeOf(it.chargerId) }
        val firstDay = plan.events.filter { it.dayIndex == 0 && it.kind == EventKind.DRIVE }
        val state = car.state.value
        val soc = (state.socPct ?: DEFAULT_SOC_PCT).toDouble()
        session = DriveSession(
            name = DemoTrip.busan.name,
            startSoc = soc,
            startOdometerKm = state.odometerKm,
            startedAt = System.currentTimeMillis(),
            plannedKwh = firstDay.sumOf { it.kwh },
            plannedEndSoc = firstDay.lastOrNull()?.toPct ?: soc,
            lastSoc = soc,
        )
        navi.sendRoute(DemoTrip.busan, waypoints)
    }

    // 차량 용량 기준 % 환산 / Converts to percent of the car's reported capacity
    fun arrivalPct(leg: LiveLeg): Double? {
        val capacity = car.state.value.capacityKwh ?: return null
        return (leg.arrivalWh / 10.0 / capacity).takeIf { capacity > 0 }
    }

    fun driveCheck(leg: LiveLeg, plannedArrivalPct: Double?): DriveCheck? {
        val soc = car.state.value.socPct?.toDouble() ?: return null
        return checkWhileDriving(
            vehicle = vehicleWith(efficiencyFactor.value),
            currentSocPct = soc,
            remainingKm = leg.remainKm,
            road = RoadType.HIGHWAY,
            tempC = null,
            plannedArrivalPct = plannedArrivalPct,
            floorPct = _settings.value.floorPct.toDouble(),
        )
    }

    fun finishDrive(arrivedKm: Double? = null) {
        val active = session ?: return
        session = null
        val state = car.state.value
        val endSoc = state.socPct?.toDouble() ?: active.lastSoc
        val odometerKm = active.startOdometerKm?.let { start -> state.odometerKm?.minus(start)?.toDouble() }
        val km = odometerKm?.takeIf { it > 0 } ?: arrivedKm ?: 0.0
        val usedPct = (active.startSoc - endSoc + active.chargedPct).coerceAtLeast(0.0)
        val vehicle = vehicleWith(efficiencyFactor.value)
        val record = DriveRecord(
            name = active.name,
            km = km,
            minutes = (System.currentTimeMillis() - active.startedAt) / 60_000.0,
            startSocPct = active.startSoc,
            endSocPct = endSoc,
            usedKwh = vehicle.kwhOf(usedPct),
            chargedPct = active.chargedPct,
            plannedKwh = active.plannedKwh,
            plannedEndSocPct = active.plannedEndSoc,
        )
        store.save(record)
        _record.value = record

        val calibrated = calibrator.afterTrip(vehicle, km, usedPct, RoadType.HIGHWAY, null)
        efficiencyFactor.value = calibrated.calibration.efficiencyFactor
        store.saveEfficiencyFactor(calibrated.calibration.efficiencyFactor)
    }

    fun requestTrip() = navi.requestTrip(DemoTrip.busan)

    fun sendTestRoute() = navi.sendRoute(DemoTrip.busan, listOf(DemoTrip.chilgok))

    override fun onCleared() {
        navi.stop()
        car.stop()
    }

    private fun vehicleWith(factor: Double) =
        Vehicle.ioniq9Demo().copy(calibration = Calibration(efficiencyFactor = factor))

    private fun AppSettings.toConfig() =
        PlannerConfig(floorPct = floorPct.toDouble(), fastTargetPct = targetPct.toDouble())

    private fun <T> Flow<T>.stateInUi(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    private companion object {
        const val DEFAULT_SOC_PCT = 80
    }
}
