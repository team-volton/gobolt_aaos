package app.gobolt

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.gobolt.data.AppSettings
import app.gobolt.data.AppStore
import app.gobolt.data.ORIGIN_NAME
import app.gobolt.data.DriveRecord
import app.gobolt.data.EfficiencyMode
import app.gobolt.demo.DemoTrip
import app.gobolt.data.Stay
import app.gobolt.data.Trip
import app.gobolt.data.TripLegs
import app.gobolt.data.TripSchedule
import app.gobolt.engine.Activity
import app.gobolt.engine.Calibration
import app.gobolt.engine.Calibrator
import app.gobolt.engine.ChargeStop
import app.gobolt.engine.ChargerOption
import app.gobolt.engine.DriveCheck
import app.gobolt.engine.EfficiencyModel
import app.gobolt.engine.EventKind
import app.gobolt.engine.Plan
import app.gobolt.engine.Planner
import app.gobolt.engine.PlannerConfig
import app.gobolt.engine.RoadType
import app.gobolt.engine.Strategy
import app.gobolt.engine.Vehicle
import app.gobolt.engine.checkWhileDriving
import app.gobolt.navi.NaviGateway
import app.gobolt.engine.Charger
import app.gobolt.navi.LiveLeg
import app.gobolt.navi.NaviPlace
import app.gobolt.engine.HvacPowerModel
import app.gobolt.vehicle.UxRestrictionGateway
import app.gobolt.vehicle.VehicleGateway
import java.time.LocalDate
import kotlin.math.roundToInt
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

data class FieldTripPreview(val beforePct: Double, val afterPct: Double, val canGo: Boolean)

class GoboltViewModel(application: Application) : AndroidViewModel(application) {
    private val store = AppStore(application)
    private val car = VehicleGateway(application)
    private val navi = NaviGateway(application)
    private val ux = UxRestrictionGateway(application)
    private val calibrator = Calibrator()

    val vehicleState = car.state
    val naviState = navi.state

    private val _settings = MutableStateFlow(store.loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val efficiencyFactor = MutableStateFlow(store.loadEfficiencyFactor())

    private val _records = MutableStateFlow(store.loadRecords())
    val records: StateFlow<List<DriveRecord>> = _records.asStateFlow()

    private val _trip = MutableStateFlow(store.loadTrip())
    val trip: StateFlow<Trip> = _trip.asStateFlow()

    private val _strategy = MutableStateFlow(Strategy.FEWEST_STOPS)
    val strategy: StateFlow<Strategy> = _strategy.asStateFlow()

    private val _excludedChargers = MutableStateFlow(emptySet<String>())
    val excludedChargers: StateFlow<Set<String>> = _excludedChargers.asStateFlow()

    private val today: LocalDate = LocalDate.now()
    val todayIndex: Int = today.dayOfWeek.value - 1
    val weekLabels: List<String> = DemoTrip.weekdaysFrom(todayIndex)

    private val startSoc: Flow<Double> = car.state
        .map { (it.socPct ?: DEFAULT_SOC_PCT).toDouble() }
        .distinctUntilChanged()

    private val carSpec = car.state.map { it.capacityKwh to it.efficiencyKmPerKwh }.distinctUntilChanged()

    private val model: Flow<Vehicle> = combine(efficiencyFactor, carSpec, _settings) { factor, _, _ -> vehicleWith(factor) }

    // 기어 또는 차량의 화면 제한 중 하나라도 걸리면 잠금 / Locks when either the gear or the car restricts the screen
    val inputLocked: StateFlow<Boolean> = combine(car.state, ux.restricted) { state, restricted ->
        state.driving || restricted
    }.stateInUi(false)

    // 변경 전 상태 / The state before the latest edits
    private data class Snapshot(val trip: Trip, val settings: AppSettings)

    private val _undo = MutableStateFlow<Snapshot?>(null)
    val hasChanges: StateFlow<Boolean> = _undo.map { it != null }.stateInUi(false)

    private val planner: Flow<Planner> = combine(model, _settings) { vehicle, settings ->
        Planner(vehicle, settings.toConfig())
    }

    val plans: StateFlow<List<Plan>> = combine(planner, startSoc, _trip, _excludedChargers, navi.state) { planner, soc, trip, excluded, _ ->
        planner.planAll(scheduleFor(soc, trip), excluded)
    }.stateInUi(emptyList())

    // 차박 전후 비교 기준 / Baseline for the before and after comparison
    val lodgingPlans: StateFlow<List<Plan>> = combine(planner, startSoc, _trip, _excludedChargers, navi.state) { planner, soc, trip, excluded, _ ->
        val lodging = trip.copy(nights = trip.nights.map { Stay.LODGING })
        planner.planAll(scheduleFor(soc, lodging), excluded)
    }.stateInUi(emptyList())

    private val extraTodayKm = MutableStateFlow(store.loadExtraKm(today.toEpochDay()))

    val weekPlan: StateFlow<Plan?> = combine(planner, startSoc, _settings, extraTodayKm) { planner, soc, settings, extra ->
        planner.plan(DemoTrip.week(soc, todayIndex, settings, extra))
    }.stateInUi(null)

    // 공용 충전기가 차 있을 때의 다음 충전일 / The next charging night if the shared charger is taken
    val sharedFallback: StateFlow<String?> = combine(planner, startSoc, _settings, extraTodayKm, weekPlan) { planner, soc, settings, extra, week ->
        val night = week?.stops?.firstOrNull { !it.fast }?.dayIndex ?: return@combine null
        if (settings.homeCharge != app.gobolt.data.HomeCharge.SHARED) return@combine null
        val fallback = planner.plan(DemoTrip.week(soc, todayIndex, settings, extra, blockedNight = night))
        if (!fallback.isFeasible) return@combine "못 쓰면 급속 충전이 필요해요"
        val next = fallback.stops.firstOrNull { !it.fast && it.dayIndex != night } ?: return@combine null
        "못 쓰면 ${weekLabels[next.dayIndex]}요일 밤"
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
        ux.start()
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

    fun previewFieldTrip(km: Int): FieldTripPreview? {
        val soc = car.state.value.socPct?.toDouble() ?: return null
        val settings = _settings.value
        val planner = Planner(vehicleWith(efficiencyFactor.value), settings.toConfig())
        val before = planner.plan(DemoTrip.week(soc, todayIndex, settings, extraTodayKm.value))
        val after = planner.plan(DemoTrip.week(soc, todayIndex, settings, extraTodayKm.value + km))
        fun home(plan: Plan) = plan.events
            .lastOrNull { it.dayIndex == 0 && it.kind !in CHARGE_KINDS }?.toPct ?: soc
        return FieldTripPreview(home(before), home(after), after.isFeasible && home(after) >= settings.floorPct)
    }

    fun fieldTripKm(place: NaviPlace): Int {
        val here = navi.state.value.currentLocation ?: return 0
        return (TripSchedule.estimateKm(here, place) * 2).roundToInt()
    }

    fun applyFieldTrip(km: Int) {
        extraTodayKm.update { it + km }
        store.saveExtraKm(today.toEpochDay(), extraTodayKm.value)
    }

    // 다음 급속 충전소를 빼고 내비 경로를 다시 보냄 / Drops the next fast charger and resends the route
    fun replaceNextCharger(): String? {
        val current = selectedPlan.value ?: return null
        val id = current.stops.firstOrNull { it.dayIndex == 0 && it.fast }?.chargerId ?: return null
        val excluded = _excludedChargers.value + id
        val soc = session?.startSoc ?: (car.state.value.socPct ?: DEFAULT_SOC_PCT).toDouble()
        val next = Planner(vehicleWith(efficiencyFactor.value), _settings.value.toConfig())
            .plan(scheduleFor(soc, _trip.value), current.strategy, excluded)
        if (!next.isFeasible) return null
        _excludedChargers.value = excluded
        val stops = next.stops.filter { it.dayIndex == 0 && it.fast }
        navi.sendRoute(legDestination(_trip.value), stops.mapNotNull { placeOf(it.chargerId) })
        return stops.firstOrNull()?.name
    }

    val presetPlaces = listOf(DemoTrip.busan)

    private fun naviChargers(): List<Charger> = navi.state.value.tripChargers.map { found ->
        val known = DemoTrip.outboundChargers.firstOrNull { it.name == found.name }
        Charger(
            id = NAVI_PREFIX + found.name,
            name = found.name,
            positionKm = found.positionKm,
            // 출력을 모르면 보수적으로 가정 / Assumes a conservative power when unknown
            powerKw = navi.state.value.chargerKw[found.name]?.toDouble() ?: known?.powerKw ?: UNKNOWN_CHARGER_KW,
        )
    }

    // 첫 구간만 내비로 계산 가능, 복귀는 추정 / Only the first leg can come from the navi; the return is estimated
    fun legs(trip: Trip = _trip.value): TripLegs {
        val state = navi.state.value
        val measuredKm = state.tripKm?.takeIf { state.tripFor == trip.destination.name }
        // 목적지에 도착한 뒤에는 복귀 구간을 내비로 잴 수 있음 / Once at the destination, the navi can measure the way back
        val backKm = state.tripKm?.takeIf { state.tripFor == ORIGIN_NAME }
        val origin = originOf(trip)
        val outKm = measuredKm ?: TripSchedule.estimateKm(origin, trip.destination)
        val fromNavi = if (measuredKm != null || backKm != null) naviChargers() else emptyList()
        val demoRoute = trip.destination.name == DemoTrip.busan.name
        val out = (if (measuredKm != null) fromNavi else emptyList()).ifEmpty {
            if (demoRoute) DemoTrip.outboundChargers.filter { it.positionKm < outKm } else emptyList()
        }
        val back = when {
            backKm != null -> fromNavi
            demoRoute && fromNavi.isEmpty() -> DemoTrip.returnChargers
            else -> fromNavi
                .map { it.copy(id = it.id + BACK_SUFFIX, name = it.name + " (복귀 추정)", positionKm = outKm - it.positionKm) }
                .sortedBy { it.positionKm }
        }
        return TripLegs(outKm, backKm ?: outKm, out, back, measuredKm != null, backKm != null)
    }

    private fun originOf(trip: Trip): NaviPlace =
        trip.origin ?: navi.state.value.currentLocation ?: DemoTrip.seoul

    // 오늘이 여행 중 며칠째인지 / Which day of the trip today is
    fun dayOffset(trip: Trip = _trip.value): Int =
        (today.toEpochDay() - trip.startEpochDay).coerceIn(0, trip.nights.size.toLong()).toInt()

    private fun onReturnLeg(trip: Trip): Boolean {
        if (dayOffset(trip) == trip.nights.size) return true
        val here = navi.state.value.currentLocation ?: return false
        return TripSchedule.estimateKm(here, trip.destination) < NEAR_DESTINATION_KM
    }

    val chargerSource: String
        get() {
            val state = navi.state.value
            return when {
                state.tripFor in setOf(_trip.value.destination.name, ORIGIN_NAME) && state.tripChargers.isNotEmpty() -> "내비 추천"
                _trip.value.destination.name == DemoTrip.busan.name -> "기본 목록"
                else -> "정보 없음"
            }
        }

    private fun scheduleFor(soc: Double, trip: Trip) =
        TripSchedule.build(trip, soc, legs(trip), _settings.value, dayOffset(trip))

    private fun placeOf(chargerId: String?): NaviPlace? {
        val id = chargerId?.removeSuffix(BACK_SUFFIX)
        return DemoTrip.placeOf(id)
            ?: navi.state.value.tripChargers.firstOrNull { NAVI_PREFIX + it.name == id }?.place
    }

    fun sketch(plan: Plan): Pair<List<NaviPlace>, List<NaviPlace>> {
        val stops = plan.stops.filter { it.dayIndex == 0 && it.fast }.mapNotNull { placeOf(it.chargerId) }.distinct()
        val trip = _trip.value
        if (trip.destination.name == DemoTrip.busan.name && chargerSource == "기본 목록") return DemoTrip.routeLine to stops
        val origin = navi.state.value.currentLocation ?: DemoTrip.seoul
        return (listOf(origin) + stops + trip.destination) to stops
    }

    // 출발 직전에 내비 값으로 다시 계산 / Recalculates from navi values right before each leg
    fun refreshNaviChargers() {
        val trip = _trip.value
        if (onReturnLeg(trip)) {
            val origin = originOf(trip)
            navi.requestTrip(NaviPlace(ORIGIN_NAME, origin.latitude, origin.longitude))
        } else {
            navi.requestTrip(trip.destination)
        }
    }

    private fun legDestination(trip: Trip): NaviPlace =
        if (onReturnLeg(trip)) originOf(trip).let { NaviPlace(ORIGIN_NAME, it.latitude, it.longitude) } else trip.destination

    fun setDestination(place: NaviPlace) {
        updateTrip { it.copy(destination = place, origin = navi.state.value.currentLocation ?: it.origin) }
        _excludedChargers.value = emptySet()
        navi.requestTrip(place)
    }

    fun shiftStartDate(days: Int) = updateTrip { trip ->
        trip.copy(startEpochDay = maxOf(today.toEpochDay(), trip.startEpochDay + days))
    }

    fun shiftDepartTime(minutes: Int) = updateTrip { trip ->
        trip.copy(departMinutes = (trip.departMinutes + minutes).mod(MINUTES_PER_DAY))
    }

    fun setNightCount(count: Int) = updateTrip { it.withNightCount(count) }

    fun shiftOutsideTemp(delta: Int) = updateTrip { it.copy(outsideC = (it.outsideC + delta).coerceIn(-20, 40)) }

    fun vehicleSummary(): String {
        val state = car.state.value
        val vehicle = vehicleWith(efficiencyFactor.value)
        val fromCar = state.efficiencyKmPerKwh?.toDouble()?.let(::validEfficiency) != null
        return "용량 ${vehicle.capacityKwh.rounded1()}kWh · 전비 ${vehicle.kmPerKwh(RoadType.MIXED, null).rounded1()}km/kWh " +
            if (fromCar) "(차량값)" else "(기본값)"
    }

    private fun Double.rounded1() = "%.1f".format(this)

    fun selectStrategy(strategy: Strategy) {
        _strategy.value = strategy
    }

    fun updateSettings(change: (AppSettings) -> AppSettings) {
        remember()
        _settings.update(change)
        store.save(_settings.value)
    }

    fun setLocalKm(index: Int, km: Int) = updateTrip { input ->
        input.copy(localKm = input.localKm.mapIndexed { i, old -> if (i == index) km.coerceIn(0, 300) else old })
    }

    fun setStay(night: Int, stay: Stay) = updateTrip { input ->
        input.copy(nights = input.nights.mapIndexed { i, old -> if (i == night) stay else old })
    }

    private fun remember() {
        if (_undo.value == null) _undo.value = Snapshot(_trip.value, _settings.value)
    }

    fun undoChanges() {
        val before = _undo.value ?: return
        _trip.value = before.trip
        _settings.value = before.settings
        store.save(before.trip)
        store.save(before.settings)
        _undo.value = null
    }

    fun keepChanges() {
        _undo.value = null
    }

    // 변경 전 일정으로 같은 전략의 계획 / The same strategy planned on the pre-edit state
    fun planBeforeChanges(strategy: Strategy): Plan? {
        val before = _undo.value ?: return null
        val soc = (car.state.value.socPct ?: DEFAULT_SOC_PCT).toDouble()
        val planner = Planner(vehicleWith(efficiencyFactor.value, before.settings), before.settings.toConfig())
        val schedule = TripSchedule.build(before.trip, soc, legs(before.trip), before.settings, dayOffset(before.trip))
        return planner.plan(schedule, strategy, _excludedChargers.value)
    }

    private fun updateTrip(change: (Trip) -> Trip) {
        remember()
        _trip.update(change)
        store.save(_trip.value)
    }

    fun toggleCharger(id: String) = _excludedChargers.update { if (id in it) it - id else it + id }

    fun alternatives(plan: Plan, stop: ChargeStop): List<ChargerOption> {
        if (stop.chargerId == null) return emptyList()
        val start = plan.events.firstOrNull { it.dayIndex == stop.dayIndex && it.kind == EventKind.DRIVE }
            ?: return emptyList()
        val schedule = scheduleFor(start.fromPct, _trip.value)
        val drive = schedule.days[stop.dayIndex].activities.filterIsInstance<Activity.Drive>().firstOrNull()
            ?: return emptyList()
        return Planner(vehicleWith(efficiencyFactor.value), _settings.value.toConfig())
            .chargerOptions(drive, start.fromPct)
            .filter { it.charger.id != stop.chargerId }
    }

    fun startGuidance(plan: Plan) {
        val waypoints = plan.stops
            .filter { it.dayIndex == 0 && it.fast }
            .mapNotNull { placeOf(it.chargerId) }
        val firstDay = plan.events.filter { it.dayIndex == 0 && it.kind == EventKind.DRIVE }
        val state = car.state.value
        val soc = (state.socPct ?: DEFAULT_SOC_PCT).toDouble()
        session = DriveSession(
            name = legDestination(_trip.value).name,
            startSoc = soc,
            startOdometerKm = state.odometerKm,
            startedAt = System.currentTimeMillis(),
            plannedKwh = firstDay.sumOf { it.kwh },
            plannedEndSoc = firstDay.lastOrNull()?.toPct ?: soc,
            lastSoc = soc,
        )
        navi.sendRoute(legDestination(_trip.value), waypoints)
    }

    // 차량 용량 기준 % 환산 / Converts to percent of the car's reported capacity
    fun arrivalPct(leg: LiveLeg): Double {
        return leg.arrivalWh / 10.0 / vehicleWith(efficiencyFactor.value).capacityKwh
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
        if (arrivedKm == null) navi.cancelRoute()
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
            epochDay = today.toEpochDay(),
        )
        store.add(record)
        _records.value = store.loadRecords()

        val calibrated = calibrator.afterTrip(vehicle, km, usedPct, RoadType.HIGHWAY, null)
        efficiencyFactor.value = calibrated.calibration.efficiencyFactor
        store.saveEfficiencyFactor(calibrated.calibration.efficiencyFactor)
    }

    fun requestTrip() = navi.requestTrip(_trip.value.destination)

    fun sendTestRoute() = navi.sendRoute(DemoTrip.busan, listOf(DemoTrip.chilgok))

    override fun onCleared() {
        navi.stop()
        car.stop()
        ux.stop()
    }

    // 차량이 준 용량과 전비를 쓰고, 없으면 기본값 / Uses the car's capacity and efficiency, else defaults
    private fun vehicleWith(factor: Double, settings: AppSettings = _settings.value): Vehicle {
        val state = car.state.value
        val base = when (settings.efficiencyMode) {
            EfficiencyMode.DEMO -> Vehicle.ioniq9Demo()
            EfficiencyMode.RATED -> Vehicle.ioniq9Rated().copy(hvac = TEMPERATURE_HVAC)
        }
        // SDK 값은 환경에 따라 Wh로 올 수 있음 / The SDK value may arrive in watt-hours
        val capacity = state.capacityKwh?.toDouble()
            ?.let { if (it > WH_THRESHOLD) it / 1000 else it }
            ?.takeIf { it in MIN_CAPACITY_KWH..MAX_CAPACITY_KWH }
        val efficiency = state.efficiencyKmPerKwh?.toDouble()?.let(::validEfficiency)
        return base.copy(
            capacityKwh = capacity ?: base.capacityKwh,
            efficiency = efficiency?.let { EfficiencyModel.flat(it) } ?: base.efficiency,
            calibration = Calibration(efficiencyFactor = factor),
        )
    }

    private fun validEfficiency(value: Double) = value.takeIf { it in MIN_KM_PER_KWH..MAX_KM_PER_KWH }

    private fun AppSettings.toConfig() =
        PlannerConfig(floorPct = floorPct.toDouble(), fastTargetPct = targetPct.toDouble())

    private fun <T> Flow<T>.stateInUi(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    private companion object {
        const val DEFAULT_SOC_PCT = 80
        const val WH_THRESHOLD = 1000.0
        const val NAVI_PREFIX = "navi-"
        const val UNKNOWN_CHARGER_KW = 100.0
        const val BACK_SUFFIX = "-back"
        const val NEAR_DESTINATION_KM = 30.0

        // 기획서 예시(외부 5°C, 실내 20°C에서 1.25kW)를 지나도록 맞춘 가정
        // Assumption tuned to pass the plan's example (1.25kW at 5°C outside, 20°C cabin)
        val TEMPERATURE_HVAC = HvacPowerModel.byTemperatureGap(baseKw = 0.3, kwPerDegree = 0.063, referenceKw = 1.25)
        const val MINUTES_PER_DAY = 24 * 60
        const val MIN_CAPACITY_KWH = 10.0
        const val MAX_CAPACITY_KWH = 200.0
        const val MIN_KM_PER_KWH = 1.0
        const val MAX_KM_PER_KWH = 15.0
        val CHARGE_KINDS = setOf(EventKind.FAST_CHARGE, EventKind.SLOW_CHARGE)
    }
}
