package app.gobolt.engine

// 전비 API가 없는 환경이 있어 잔량과 거리로만 계산
// Uses SOC and distance only, since some environments lack efficiency APIs
class Calibrator(
    private val alpha: Double = 0.3,
    private val minTripKm: Double = 5.0,
) {
    init {
        require(alpha in 0.0..1.0) { "반영 비율은 0~1이어야 합니다." }
    }

    fun afterTrip(
        vehicle: Vehicle,
        distanceKm: Double,
        socUsedPct: Double,
        road: RoadType,
        tempC: Double?,
    ): Vehicle {
        if (distanceKm < minTripKm || socUsedPct <= 0) return vehicle
        val observed = distanceKm / vehicle.kwhOf(socUsedPct)
        val factor = observed / vehicle.efficiency.kmPerKwh(road, tempC)
        val current = vehicle.calibration.efficiencyFactor
        return vehicle.withCalibration { it.copy(efficiencyFactor = blend(current, factor)) }
    }

    fun afterHvacStay(vehicle: Vehicle, hours: Double, socUsedPct: Double): Vehicle {
        if (hours <= 0 || socUsedPct <= 0) return vehicle
        val observed = vehicle.kwhOf(socUsedPct) / hours
        val current = vehicle.calibration.hvacKw
        return vehicle.withCalibration { it.copy(hvacKw = current?.let { old -> blend(old, observed) } ?: observed) }
    }

    fun afterFastCharge(vehicle: Vehicle, minutes: Double, socAddedPct: Double): Vehicle {
        if (minutes <= 0 || socAddedPct <= 0) return vehicle
        val observed = vehicle.kwhOf(socAddedPct) / (minutes / 60)
        val current = vehicle.calibration.fastChargeKw
        return vehicle.withCalibration { it.copy(fastChargeKw = current?.let { old -> blend(old, observed) } ?: observed) }
    }

    private fun blend(old: Double, new: Double) = old * (1 - alpha) + new * alpha

    private inline fun Vehicle.withCalibration(change: (Calibration) -> Calibration) =
        copy(calibration = change(calibration))
}

data class DriveCheck(
    val canReach: Boolean,
    val arrivalPct: Double,
    // 음수면 계획보다 적게 남음 / Negative means below plan
    val deviationPct: Double?,
    val shouldAlert: Boolean,
)

fun checkWhileDriving(
    vehicle: Vehicle,
    currentSocPct: Double,
    remainingKm: Double,
    road: RoadType,
    tempC: Double?,
    plannedArrivalPct: Double? = null,
    floorPct: Double = 20.0,
    alertThresholdPct: Double = 5.0,
): DriveCheck {
    val arrival = currentSocPct - vehicle.pctOf(vehicle.drivingKwh(remainingKm, road, tempC))
    val deviation = plannedArrivalPct?.let { arrival - it }
    val canReach = arrival >= floorPct
    return DriveCheck(
        canReach = canReach,
        arrivalPct = arrival,
        deviationPct = deviation,
        shouldAlert = !canReach || (deviation != null && deviation <= -alertThresholdPct),
    )
}
