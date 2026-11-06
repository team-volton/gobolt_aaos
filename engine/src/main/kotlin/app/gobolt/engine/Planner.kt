package app.gobolt.engine

class Planner(
    private val vehicle: Vehicle,
    private val config: PlannerConfig = PlannerConfig(),
) {
    private val routes = RoutePlanner(vehicle, config)

    fun plan(
        schedule: Schedule,
        strategy: Strategy = Strategy.FEWEST_STOPS,
        unavailableChargerIds: Set<String> = emptySet(),
    ): Plan {
        val ledger = Ledger()
        var soc = schedule.startSocPct

        for ((index, day) in schedule.days.withIndex()) {
            val outcome = runDay(index, day, soc, strategy, unavailableChargerIds)
            ledger.absorb(outcome)
            soc = outcome.soc
            outcome.failure?.let { return ledger.toPlan(strategy, schedule.startSocPct, it) }

            val next = schedule.days.getOrNull(index + 1) ?: continue
            soc = chargeOvernight(index, day, next, soc, strategy, unavailableChargerIds, ledger)
        }
        return ledger.toPlan(strategy, schedule.startSocPct, null)
    }

    fun planAll(schedule: Schedule, unavailableChargerIds: Set<String> = emptySet()): List<Plan> =
        Strategy.entries.map { plan(schedule, it, unavailableChargerIds) }

    fun chargerOptions(
        drive: Activity.Drive,
        startSocPct: Double,
        unavailableChargerIds: Set<String> = emptySet(),
    ): List<ChargerOption> = routes.options(drive, startSocPct, unavailableChargerIds)

    // 밤 공조는 포함, 야간 충전은 제외 / Includes overnight HVAC, excludes overnight charging
    private fun runDay(
        dayIndex: Int,
        day: Day,
        startSoc: Double,
        strategy: Strategy,
        unavailable: Set<String>,
    ): DayOutcome {
        val out = DayOutcome(startSoc)
        for (activity in day.activities) {
            when (activity) {
                is Activity.Drive -> {
                    val route = routes.plan(activity, out.soc, strategy, unavailable)
                    if (route == null) {
                        out.failure = routes.diagnose(activity, out.soc, unavailable, dayIndex)
                        return out
                    }
                    out.addRoute(dayIndex, activity, route)
                }

                is Activity.Local -> {
                    val km = activity.km ?: (activity.hours!! * config.localAvgKmh)
                    val kwh = vehicle.drivingKwh(km, activity.road, activity.tempC)
                    out.consume(dayIndex, EventKind.LOCAL, activity.label, km, kwh)
                }

                is Activity.Waiting -> {
                    val kwh = vehicle.hvacKwh(activity.hours, activity.outsideC, activity.cabinC)
                    out.consume(dayIndex, EventKind.HVAC, activity.label, 0.0, kwh, activity.hours * 60)
                }
            }
            if (out.soc < config.floorPct) {
                out.failure = belowFloor(dayIndex, activity.label)
                return out
            }
        }

        val night = day.night
        if (night is Night.CarCamp) {
            val kwh = vehicle.hvacKwh(night.hours, night.outsideC, night.cabinC)
            out.consume(dayIndex, EventKind.HVAC, "차박", 0.0, kwh, night.hours * 60)
            if (out.soc < config.floorPct) out.failure = belowFloor(dayIndex, "차박")
        }
        return out
    }

    private fun belowFloor(dayIndex: Int, label: String) = Infeasibility(
        dayIndex,
        label,
        FailureReason.BELOW_FLOOR,
        listOf(Suggestion.AddSlowCharging, Suggestion.ChangeWaypoint, Suggestion.AdjustSchedule),
    )

    private fun chargeOvernight(
        dayIndex: Int,
        day: Day,
        next: Day,
        startSoc: Double,
        strategy: Strategy,
        unavailable: Set<String>,
        ledger: Ledger,
    ): Double {
        fun needsCharge(soc: Double) =
            runDay(dayIndex + 1, next, soc, strategy, unavailable).failure != null

        var soc = startSoc
        if (!needsCharge(soc)) return soc

        val site = day.night.site
        site.slow?.let { place ->
            if (soc < place.targetPct) soc = ledger.chargeSlow(dayIndex, place, soc)
        }
        if (!needsCharge(soc)) return soc

        site.morningFast?.let { place ->
            if (soc < place.targetPct) soc = ledger.chargeFast(dayIndex, place, soc)
        }
        return soc
    }

    private inner class DayOutcome(var soc: Double) {
        val events = mutableListOf<SocEvent>()
        val stops = mutableListOf<ChargeStop>()
        var failure: Infeasibility? = null

        fun consume(
            dayIndex: Int,
            kind: EventKind,
            label: String,
            km: Double,
            kwh: Double,
            minutes: Double = 0.0,
        ) {
            val after = soc - vehicle.pctOf(kwh)
            events += SocEvent(dayIndex, kind, label, soc, after, km, kwh, minutes)
            soc = after
        }

        fun addRoute(dayIndex: Int, drive: Activity.Drive, route: RouteResult) {
            for (segment in route.segments) {
                val label = if (segment.charge == null) drive.label else "${drive.label}: ${segment.toName}까지"
                events += SocEvent(
                    dayIndex, EventKind.DRIVE, label,
                    segment.fromPct, segment.toPct, segment.km, segment.kwh,
                )
                segment.charge?.let { charge ->
                    events += SocEvent(
                        dayIndex, EventKind.FAST_CHARGE, charge.charger.name,
                        charge.fromPct, charge.toPct, 0.0, charge.kwh, charge.minutes,
                    )
                    stops += ChargeStop(
                        dayIndex, charge.charger.name, charge.charger.id, true,
                        charge.fromPct, charge.toPct, charge.kwh, charge.minutes, charge.powerKw,
                    )
                }
            }
            soc = route.endSoc
        }
    }

    private inner class Ledger {
        val events = mutableListOf<SocEvent>()
        val stops = mutableListOf<ChargeStop>()
        val warnings = mutableListOf<String>()

        fun absorb(outcome: DayOutcome) {
            events += outcome.events
            stops += outcome.stops
        }

        fun chargeSlow(dayIndex: Int, place: ChargingPlace, soc: Double): Double {
            // 손실분은 배터리에 들어가지 않음 / Losses never reach the battery
            val batteryKw = place.powerKw * vehicle.slowChargeEfficiency
            val fullKwh = vehicle.kwhOf(place.targetPct - soc)
            val fullHours = fullKwh / batteryKw
            val hours = place.availableHours?.let { minOf(it, fullHours) } ?: fullHours
            val kwh = hours * batteryKw
            val after = soc + vehicle.pctOf(kwh)
            if (hours < fullHours) {
                warnings += "${place.name}: 충전 가능 시간이 부족해 ${place.targetPct.toInt()}%까지 채우지 못합니다."
            }
            events += SocEvent(dayIndex, EventKind.SLOW_CHARGE, place.name, soc, after, 0.0, kwh, hours * 60)
            stops += ChargeStop(dayIndex, place.name, null, false, soc, after, kwh, hours * 60, batteryKw)
            return after
        }

        fun chargeFast(dayIndex: Int, place: ChargingPlace, soc: Double): Double {
            val power = vehicle.fastPowerKw(place.powerKw)
            val kwh = vehicle.kwhOf(place.targetPct - soc)
            val minutes = kwh / power * 60
            events += SocEvent(dayIndex, EventKind.FAST_CHARGE, place.name, soc, place.targetPct, 0.0, kwh, minutes)
            stops += ChargeStop(dayIndex, place.name, null, true, soc, place.targetPct, kwh, minutes, power)
            return place.targetPct
        }

        fun toPlan(strategy: Strategy, startSoc: Double, failure: Infeasibility?): Plan {
            val used = events.filter { it.kind !in CHARGE_KINDS }
            val summary = PlanSummary(
                totalKm = used.sumOf { it.km },
                totalKwh = used.sumOf { it.kwh },
                fastCharges = stops.count { it.fast },
                slowCharges = stops.count { !it.fast },
                fastMinutes = stops.filter { it.fast }.sumOf { it.minutes },
                slowHours = stops.filter { !it.fast }.sumOf { it.minutes } / 60,
                minSocPct = events.minOfOrNull { minOf(it.fromPct, it.toPct) } ?: startSoc,
                endSocPct = events.lastOrNull()?.toPct ?: startSoc,
            )
            return Plan(strategy, events.toList(), stops.toList(), failure, warnings.toList(), summary)
        }
    }

    private companion object {
        val CHARGE_KINDS = setOf(EventKind.FAST_CHARGE, EventKind.SLOW_CHARGE)
    }
}
