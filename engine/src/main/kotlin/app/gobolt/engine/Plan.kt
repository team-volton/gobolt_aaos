package app.gobolt.engine

enum class Strategy { FEWEST_STOPS, FASTEST, MOST_MARGIN }

enum class EventKind { DRIVE, LOCAL, HVAC, FAST_CHARGE, SLOW_CHARGE }

data class SocEvent(
    val dayIndex: Int,
    val kind: EventKind,
    val label: String,
    val fromPct: Double,
    val toPct: Double,
    val km: Double = 0.0,
    // 소비 · 충전 모두 양수 / Always positive
    val kwh: Double = 0.0,
    val minutes: Double = 0.0,
)

data class ChargeStop(
    val dayIndex: Int,
    val name: String,
    val chargerId: String?,
    val fast: Boolean,
    val fromPct: Double,
    val toPct: Double,
    val kwh: Double,
    val minutes: Double,
    val powerKw: Double,
)

enum class FailureReason {
    BELOW_FLOOR,
    NO_USABLE_CHARGER,
    CANNOT_REACH_FIRST_CHARGER,
    GAP_TOO_LONG,
}

sealed interface Suggestion {
    data class RaiseDepartureSoc(val pct: Double) : Suggestion
    data class RaiseChargeTarget(val pct: Double) : Suggestion
    data object AddSlowCharging : Suggestion
    data object ChangeWaypoint : Suggestion
    data object AdjustSchedule : Suggestion
}

data class Infeasibility(
    val dayIndex: Int,
    val activityLabel: String,
    val reason: FailureReason,
    val suggestions: List<Suggestion>,
)

data class PlanSummary(
    val totalKm: Double,
    val totalKwh: Double,
    val fastCharges: Int,
    val slowCharges: Int,
    val fastMinutes: Double,
    val slowHours: Double,
    val minSocPct: Double,
    val endSocPct: Double,
)

data class Plan(
    val strategy: Strategy,
    val events: List<SocEvent>,
    val stops: List<ChargeStop>,
    // 있으면 events는 실패 지점까지만 담김 / When set, events stop at the failure
    val infeasibility: Infeasibility?,
    val warnings: List<String>,
    val summary: PlanSummary,
) {
    val isFeasible: Boolean get() = infeasibility == null

    fun socAtEndOfDay(dayIndex: Int): Double? =
        events.lastOrNull { it.dayIndex == dayIndex }?.toPct
}

data class ChargerOption(
    val charger: Charger,
    val available: Boolean,
    val reachable: Boolean,
    val arrivalPct: Double,
    val chargeMinutes: Double,
    val reachesDestination: Boolean,
    val destinationArrivalPct: Double,
    // 왕복 / Round trip
    val extraKm: Double,
)
