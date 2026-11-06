package app.gobolt.engine

internal data class RouteResult(
    val path: List<Charger>,
    val segments: List<Segment>,
    val endSoc: Double,
    val minSoc: Double,
) {
    val fastMinutes: Double get() = segments.sumOf { it.charge?.minutes ?: 0.0 }
    val lowestPowerKw: Double
        get() = segments.mapNotNull { it.charge?.powerKw }.minOrNull() ?: Double.MAX_VALUE
    val detourKm: Double get() = path.sumOf { it.detourKm }
}

internal data class ChargeAt(
    val charger: Charger,
    val fromPct: Double,
    val toPct: Double,
    val kwh: Double,
    val minutes: Double,
    val powerKw: Double,
)

internal data class Segment(
    val toName: String,
    val km: Double,
    val kwh: Double,
    val fromPct: Double,
    val toPct: Double,
    val charge: ChargeAt?,
)

internal class RoutePlanner(
    private val vehicle: Vehicle,
    private val config: PlannerConfig,
) {
    fun usableChargers(drive: Activity.Drive, unavailable: Set<String>): List<Charger> =
        compatibleChargers(drive).filter { it.id !in unavailable }

    fun compatibleChargers(drive: Activity.Drive): List<Charger> =
        drive.chargers
            .filter { it.sameDirection }
            .filter { it.connectors.any(vehicle.connectors::contains) }
            .filter { it.positionKm > 0 && it.positionKm < drive.distanceKm }
            .sortedBy { it.positionKm }

    fun plan(
        drive: Activity.Drive,
        startSoc: Double,
        strategy: Strategy,
        unavailable: Set<String>,
    ): RouteResult? {
        val chargers = usableChargers(drive, unavailable)
        val minStops = minimumStops(drive, startSoc, chargers) ?: return null
        val maxStops = if (strategy == Strategy.FEWEST_STOPS) minStops else minStops + 1

        val results = mutableListOf<RouteResult>()
        fun extend(path: List<Charger>, nextIndex: Int) {
            evaluate(drive, startSoc, path, strategy)?.let(results::add)
            if (path.size == maxStops) return
            for (i in nextIndex until chargers.size) {
                val candidate = chargers[i]
                val origin = path.lastOrNull()
                val soc = if (origin == null) startSoc else config.fastTargetPct
                if (soc - pct(drive, hopKm(origin, candidate)) < config.floorPct) continue
                extend(path + candidate, i + 1)
            }
        }
        extend(emptyList(), 0)

        return results.minWithOrNull(comparatorFor(strategy))
    }

    private fun comparatorFor(strategy: Strategy): Comparator<RouteResult> = when (strategy) {
        Strategy.FEWEST_STOPS -> compareBy<RouteResult> { it.path.size }
            .thenByDescending { it.lowestPowerKw }
            .thenBy { it.detourKm }
            .thenBy { it.fastMinutes }

        Strategy.FASTEST -> compareBy<RouteResult> { it.fastMinutes }
            .thenBy { it.path.size }
            .thenBy { it.detourKm }

        Strategy.MOST_MARGIN -> compareByDescending<RouteResult> { it.minSoc }
            .thenByDescending { it.endSoc }
            .thenBy { it.path.size }
    }

    // 매번 충전 목표까지 채운다고 가정 / Assumes charging to the target at every stop
    private fun minimumStops(
        drive: Activity.Drive,
        startSoc: Double,
        chargers: List<Charger>,
    ): Int? {
        val destinationPct = { origin: Charger?, soc: Double ->
            soc - pct(drive, distanceToEnd(drive, origin))
        }
        if (destinationPct(null, startSoc) >= config.floorPct) return 0

        var frontier = chargers.filter { startSoc - pct(drive, hopKm(null, it)) >= config.floorPct }
        val visited = frontier.toMutableSet()
        var stops = 1
        while (frontier.isNotEmpty()) {
            if (frontier.any { destinationPct(it, config.fastTargetPct) >= config.floorPct }) return stops
            frontier = chargers.filter { next ->
                next !in visited && frontier.any { origin ->
                    next.positionKm > origin.positionKm &&
                        config.fastTargetPct - pct(drive, hopKm(origin, next)) >= config.floorPct
                }
            }
            visited += frontier
            stops++
        }
        return null
    }

    // 하한을 어기거나 충전이 무의미하면 null / null if it breaks the floor or a stop adds nothing
    private fun evaluate(
        drive: Activity.Drive,
        startSoc: Double,
        path: List<Charger>,
        strategy: Strategy,
    ): RouteResult? {
        val segments = mutableListOf<Segment>()
        var soc = startSoc
        var minSoc = startSoc
        var origin: Charger? = null

        for ((index, charger) in path.withIndex()) {
            val hop = hopKm(origin, charger)
            val arrival = soc - pct(drive, hop)
            if (arrival < config.floorPct) return null

            val nextKm = path.getOrNull(index + 1)?.let { hopKm(charger, it) }
                ?: distanceToEnd(drive, charger)
            val target = chargeTarget(drive, nextKm, strategy)
            if (arrival >= target) return null

            val kwh = vehicle.kwhOf(target - arrival)
            val power = vehicle.fastPowerKw(charger.powerKw)
            segments += Segment(
                toName = charger.name,
                km = hop,
                kwh = kwhFor(drive, hop),
                fromPct = soc,
                toPct = arrival,
                charge = ChargeAt(charger, arrival, target, kwh, kwh / power * 60, power),
            )
            minSoc = minOf(minSoc, arrival)
            soc = target
            origin = charger
        }

        val lastKm = distanceToEnd(drive, origin)
        val end = soc - pct(drive, lastKm)
        if (end < config.floorPct) return null
        segments += Segment("도착", lastKm, kwhFor(drive, lastKm), soc, end, null)
        return RouteResult(path, segments, end, minOf(minSoc, end))
    }

    private fun chargeTarget(drive: Activity.Drive, nextKm: Double, strategy: Strategy): Double =
        if (strategy == Strategy.FASTEST) {
            minOf(config.fastTargetPct, config.floorPct + config.arrivalBufferPct + pct(drive, nextKm))
        } else {
            config.fastTargetPct
        }

    fun options(
        drive: Activity.Drive,
        startSoc: Double,
        unavailable: Set<String>,
    ): List<ChargerOption> = compatibleChargers(drive).map { charger ->
        val arrival = startSoc - pct(drive, hopKm(null, charger))
        val after = maxOf(arrival, config.fastTargetPct)
        val kwh = vehicle.kwhOf(after - arrival)
        val destination = after - pct(drive, distanceToEnd(drive, charger))
        ChargerOption(
            charger = charger,
            available = charger.id !in unavailable,
            reachable = arrival >= config.floorPct,
            arrivalPct = arrival,
            chargeMinutes = kwh / vehicle.fastPowerKw(charger.powerKw) * 60,
            reachesDestination = destination >= config.floorPct,
            destinationArrivalPct = destination,
            extraKm = charger.detourKm * 2,
        )
    }

    fun diagnose(
        drive: Activity.Drive,
        startSoc: Double,
        unavailable: Set<String>,
        dayIndex: Int,
    ): Infeasibility {
        val chargers = usableChargers(drive, unavailable)
        val suggestions = mutableListOf<Suggestion>()
        val first = chargers.firstOrNull()

        val reason = when {
            first == null -> {
                raiseDeparture(drive, drive.distanceKm, suggestions)
                FailureReason.NO_USABLE_CHARGER
            }

            startSoc - pct(drive, hopKm(null, first)) < config.floorPct -> {
                raiseDeparture(drive, hopKm(null, first), suggestions)
                FailureReason.CANNOT_REACH_FIRST_CHARGER
            }

            else -> {
                val hops = chargers.zipWithNext { a, b -> hopKm(a, b) } +
                    distanceToEnd(drive, chargers.last())
                val needed = config.floorPct + pct(drive, hops.max())
                if (needed <= 100.0) suggestions += Suggestion.RaiseChargeTarget(needed)
                FailureReason.GAP_TOO_LONG
            }
        }
        suggestions += Suggestion.ChangeWaypoint
        suggestions += Suggestion.AdjustSchedule
        return Infeasibility(dayIndex, drive.label, reason, suggestions)
    }

    private fun raiseDeparture(
        drive: Activity.Drive,
        km: Double,
        suggestions: MutableList<Suggestion>,
    ) {
        val needed = config.floorPct + pct(drive, km)
        if (needed <= 100.0) {
            suggestions += Suggestion.RaiseDepartureSoc(needed)
            suggestions += Suggestion.AddSlowCharging
        }
    }

    private fun hopKm(origin: Charger?, next: Charger): Double =
        next.positionKm - (origin?.positionKm ?: 0.0) + (origin?.detourKm ?: 0.0) + next.detourKm

    private fun distanceToEnd(drive: Activity.Drive, origin: Charger?): Double =
        drive.distanceKm - (origin?.positionKm ?: 0.0) + (origin?.detourKm ?: 0.0)

    private fun kwhFor(drive: Activity.Drive, km: Double): Double =
        vehicle.drivingKwh(km, drive.road, drive.tempC)

    private fun pct(drive: Activity.Drive, km: Double): Double = vehicle.pctOf(kwhFor(drive, km))
}
