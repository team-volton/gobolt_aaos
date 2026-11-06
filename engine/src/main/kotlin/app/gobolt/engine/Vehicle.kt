package app.gobolt.engine

enum class RoadType { HIGHWAY, CITY, MIXED }

enum class Connector { DC_COMBO, DC_CHADEMO, AC3, AC_SLOW }

fun interface EfficiencyModel {
    fun kmPerKwh(road: RoadType, tempC: Double?): Double

    companion object {
        fun flat(kmPerKwh: Double) = EfficiencyModel { _, _ -> kmPerKwh }
    }
}

class RatedEfficiency(
    private val highway: Double,
    private val city: Double,
    private val mixed: Double,
) : EfficiencyModel {
    override fun kmPerKwh(road: RoadType, tempC: Double?): Double = when (road) {
        RoadType.HIGHWAY -> highway * temperatureFactor(HIGHWAY_FACTORS, tempC)
        RoadType.CITY -> city * temperatureFactor(CITY_FACTORS, tempC)
        RoadType.MIXED -> mixed *
            (temperatureFactor(HIGHWAY_FACTORS, tempC) + temperatureFactor(CITY_FACTORS, tempC)) / 2
    }

    private fun temperatureFactor(table: List<Pair<Double, Double>>, tempC: Double?): Double {
        if (tempC == null) return 1.0
        if (tempC <= table.first().first) return table.first().second
        if (tempC >= table.last().first) return table.last().second
        val upperIndex = table.indexOfFirst { it.first >= tempC }
        val (lowTemp, lowFactor) = table[upperIndex - 1]
        val (highTemp, highFactor) = table[upperIndex]
        return lowFactor + (highFactor - lowFactor) * (tempC - lowTemp) / (highTemp - lowTemp)
    }

    private companion object {
        // 상온 · 저온 인증 주행거리 비율 기반
        // Based on certified room-temperature vs cold range ratios
        val HIGHWAY_FACTORS = listOf(-7.0 to 0.91, 0.0 to 0.93, 10.0 to 0.96, 25.0 to 1.00)
        val CITY_FACTORS = listOf(-7.0 to 0.65, 0.0 to 0.73, 10.0 to 0.84, 25.0 to 1.00)
    }
}

fun interface HvacPowerModel {
    fun kw(outsideC: Double?, cabinC: Double?): Double

    companion object {
        fun fixed(kw: Double) = HvacPowerModel { _, _ -> kw }
    }
}

data class Calibration(
    val efficiencyFactor: Double = 1.0,
    val hvacKw: Double? = null,
    val fastChargeKw: Double? = null,
)

data class Vehicle(
    val capacityKwh: Double,
    val efficiency: EfficiencyModel,
    val hvac: HvacPowerModel,
    // 최대가 아닌 평균 전력 / Average power, not peak
    val maxFastKw: Double,
    val connectors: Set<Connector> = setOf(Connector.DC_COMBO, Connector.AC_SLOW),
    val slowChargeEfficiency: Double = 1.0,
    val calibration: Calibration = Calibration(),
) {
    init {
        require(capacityKwh > 0) { "배터리 용량은 0보다 커야 합니다." }
        require(maxFastKw > 0) { "급속 충전 전력은 0보다 커야 합니다." }
        require(slowChargeEfficiency in 0.1..1.0) { "완속 충전 효율은 0.1~1.0이어야 합니다." }
    }

    fun kmPerKwh(road: RoadType, tempC: Double?): Double =
        efficiency.kmPerKwh(road, tempC) * calibration.efficiencyFactor

    fun drivingKwh(km: Double, road: RoadType, tempC: Double?): Double = km / kmPerKwh(road, tempC)

    fun hvacKwh(hours: Double, outsideC: Double?, cabinC: Double?): Double =
        (calibration.hvacKw ?: hvac.kw(outsideC, cabinC)) * hours

    fun pctOf(kwh: Double): Double = kwh / capacityKwh * 100

    fun kwhOf(pct: Double): Double = pct / 100 * capacityKwh

    fun fastPowerKw(chargerKw: Double): Double =
        minOf(chargerKw, calibration.fastChargeKw ?: maxFastKw)

    companion object {
        fun ioniq9Demo() = Vehicle(
            capacityKwh = 110.3,
            efficiency = EfficiencyModel.flat(4.0),
            hvac = HvacPowerModel.fixed(1.25),
            maxFastKw = 190.0,
        )

        fun ioniq9Rated() = ioniq9Demo().copy(
            efficiency = RatedEfficiency(highway = 3.9, city = 4.7, mixed = 4.3),
        )
    }
}
