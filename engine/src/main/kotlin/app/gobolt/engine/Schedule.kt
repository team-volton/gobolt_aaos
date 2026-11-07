package app.gobolt.engine

data class Charger(
    val id: String,
    val name: String,
    // 구간 출발점 기준 / From the start of the drive
    val positionKm: Double,
    val powerKw: Double,
    val connectors: Set<Connector> = setOf(Connector.DC_COMBO),
    // 편도 / One way
    val detourKm: Double = 0.0,
    val sameDirection: Boolean = true,
)

data class ChargingPlace(
    val name: String,
    val powerKw: Double,
    val targetPct: Double,
    // null이면 제한 없음 / null means no limit
    val availableHours: Double? = null,
)

data class ChargingSite(
    val slow: ChargingPlace? = null,
    val morningFast: ChargingPlace? = null,
) {
    val canCharge: Boolean get() = slow != null || morningFast != null
}

sealed interface Activity {
    val label: String

    data class Drive(
        override val label: String,
        val distanceKm: Double,
        val road: RoadType = RoadType.HIGHWAY,
        val tempC: Double? = null,
        val chargers: List<Charger> = emptyList(),
    ) : Activity

    data class Local(
        override val label: String,
        val km: Double? = null,
        val hours: Double? = null,
        val road: RoadType = RoadType.CITY,
        val tempC: Double? = null,
    ) : Activity {
        init {
            require(km != null || hours != null) { "거리 또는 사용 시간 중 하나는 있어야 합니다." }
        }
    }

    data class Waiting(
        override val label: String,
        val hours: Double,
        val outsideC: Double? = null,
        val cabinC: Double? = null,
    ) : Activity
}

sealed interface Night {
    val site: ChargingSite

    data class Lodging(override val site: ChargingSite = ChargingSite()) : Night

    data class CarCamp(
        val hours: Double = 8.0,
        val outsideC: Double? = null,
        val cabinC: Double? = null,
        override val site: ChargingSite = ChargingSite(),
    ) : Night
}

data class Day(
    val label: String,
    val activities: List<Activity> = emptyList(),
    val night: Night = Night.Lodging(),
)

data class Schedule(
    val startSocPct: Double,
    val days: List<Day>,
) {
    init {
        require(startSocPct in 0.0..100.0) { "출발 잔량은 0~100%여야 합니다." }
    }
}

data class PlannerConfig(
    val floorPct: Double = 20.0,
    val fastTargetPct: Double = 80.0,
    val arrivalBufferPct: Double = 5.0,
    val localAvgKmh: Double = 18.0,
) {
    init {
        require(floorPct in 0.0..100.0 && fastTargetPct in 0.0..100.0) { "잔량 기준은 0~100%여야 합니다." }
        require(floorPct < fastTargetPct) { "안전 하한은 충전 목표보다 낮아야 합니다." }
        require(arrivalBufferPct >= 0) { "도착 여유는 음수일 수 없습니다." }
        require(localAvgKmh > 0) { "평균 속도는 0보다 커야 합니다." }
    }
}
