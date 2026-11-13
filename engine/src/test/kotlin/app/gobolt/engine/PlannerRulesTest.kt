package app.gobolt.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlannerRulesTest {
    private val demo = Vehicle.ioniq9Demo()
    private val planner = Planner(demo)

    // 1% = 4km
    private val simple = demo.copy(capacityKwh = 100.0)

    private fun drive(km: Double, vararg chargers: Charger) =
        Activity.Drive("구간", km, chargers = chargers.toList())

    private fun schedule(start: Double, vararg activities: Activity) =
        Schedule(start, listOf(Day("1일", activities.toList())))

    @Test
    fun `도착 잔량이 정확히 하한이면 충전하지 않는다`() {
        val plan = Planner(simple).plan(schedule(80.0, drive(240.0)))
        assertTrue(plan.isFeasible)
        assertEquals(0, plan.stops.size)
        assertEquals(20.0, plan.summary.endSocPct, 1e-9)
    }

    @Test
    fun `하한 아래로 조금이라도 내려가면 충전소가 없을 때 불가능으로 판정한다`() {
        val plan = Planner(simple).plan(schedule(80.0, drive(240.1)))
        val failure = assertNotNull(plan.infeasibility)
        assertEquals(FailureReason.NO_USABLE_CHARGER, failure.reason)
        val raise = failure.suggestions.filterIsInstance<Suggestion.RaiseDepartureSoc>().single()
        assertEquals(80.025, raise.pct, 1e-6)
        assertTrue(Suggestion.AddSlowCharging in failure.suggestions)
    }

    @Test
    fun `첫 충전소에 못 닿으면 출발 잔량 상향을 제안한다`() {
        val far = Charger("far", "먼 충전소", 300.0, 350.0)
        val plan = Planner(simple).plan(schedule(80.0, drive(500.0, far)))
        val failure = assertNotNull(plan.infeasibility)
        assertEquals(FailureReason.CANNOT_REACH_FIRST_CHARGER, failure.reason)
        assertEquals(
            95.0,
            failure.suggestions.filterIsInstance<Suggestion.RaiseDepartureSoc>().single().pct,
            1e-9,
        )
    }

    @Test
    fun `충전소 간격이 80퍼센트로 못 가는 거리면 충전 목표 상향을 제안한다`() {
        val near = Charger("near", "가까운 충전소", 100.0, 350.0)
        val plan = Planner(simple).plan(schedule(80.0, drive(400.0, near)))
        val failure = assertNotNull(plan.infeasibility)
        assertEquals(FailureReason.GAP_TOO_LONG, failure.reason)
        assertEquals(
            95.0,
            failure.suggestions.filterIsInstance<Suggestion.RaiseChargeTarget>().single().pct,
            1e-9,
        )
    }

    @Test
    fun `진행 반대 방향과 커넥터가 다른 충전소는 쓰지 않는다`() {
        val opposite = Charger("opp", "반대 방향", 200.0, 350.0, sameDirection = false)
        val chademo = Charger("cha", "차데모", 200.0, 350.0, connectors = setOf(Connector.DC_CHADEMO))
        val plan = Planner(simple).plan(schedule(80.0, drive(400.0, opposite, chademo)))
        assertEquals(FailureReason.NO_USABLE_CHARGER, plan.infeasibility?.reason)
    }

    @Test
    fun `여러 번 충전이 필요한 긴 구간도 계산한다`() {
        val chargers = arrayOf(
            Charger("a", "A", 200.0, 350.0),
            Charger("b", "B", 420.0, 350.0),
            Charger("c", "C", 640.0, 350.0),
        )
        val plan = Planner(simple).plan(schedule(80.0, drive(800.0, *chargers)))
        assertTrue(plan.isFeasible)
        assertEquals(listOf("a", "b", "c"), plan.stops.map { it.chargerId })
        assertTrue(plan.summary.minSocPct >= 20.0)
    }

    @Test
    fun `경로 이탈 거리는 왕복으로 주행 거리와 에너지에 더한다`() {
        val detour = Charger("d", "이탈 충전소", 200.0, 350.0, detourKm = 10.0)
        val plan = Planner(simple).plan(schedule(80.0, drive(400.0, detour)))
        assertTrue(plan.isFeasible)
        assertEquals(420.0, plan.summary.totalKm, 1e-9)
        assertEquals(105.0, plan.summary.totalKwh, 1e-9)
    }

    @Test
    fun `저출력 충전기는 그 출력으로 시간을 계산한다`() {
        val low = Charger("l", "50kW", 200.0, 50.0)
        val high = Charger("h", "350kW", 200.0, 350.0)
        val vehicle = Planner(simple)
        val lowStop = vehicle.plan(schedule(80.0, drive(400.0, low))).stops.single()
        val highStop = vehicle.plan(schedule(80.0, drive(400.0, high))).stops.single()
        assertEquals(50.0, lowStop.powerKw)
        assertEquals(190.0, highStop.powerKw)
        assertEquals(lowStop.minutes * 50 / 190, highStop.minutes, 1e-9)
    }

    @Test
    fun `계획안 3종은 서로 다른 답을 낸다`() {
        val (fewest, fastest, margin) = planner.planAll(Scenarios.travel())
        assertTrue(fewest.isFeasible && fastest.isFeasible && margin.isFeasible)

        val fewestFirst = fewest.stops.first()
        assertEquals("칠곡휴게소", fewestFirst.name)
        assertEquals(80.0, fewestFirst.toPct, 1e-9)

        val fastestFirst = fastest.stops.first()
        assertEquals("칠곡휴게소", fastestFirst.name)
        assertTrue(fastestFirst.toPct < 80.0)
        assertTrue(fastestFirst.minutes < fewestFirst.minutes)

        val marginFirst = margin.stops.first()
        assertEquals("김천휴게소", marginFirst.name)
        assertTrue(margin.events.first { it.kind == EventKind.DRIVE }.toPct > 30.0)
        assertTrue(
            margin.events.filter { it.dayIndex == 0 }.minOf { it.toPct } >
                fewest.events.filter { it.dayIndex == 0 }.minOf { it.toPct },
        )
    }

    @Test
    fun `완속이 없으면 다음 날 일정 전 급속을 넣는다`() {
        val site = ChargingSite(morningFast = ChargingPlace("아침 급속", 350.0, targetPct = 80.0))
        val schedule = Schedule(
            30.0,
            listOf(
                Day("1일", emptyList(), Night.Lodging(site)),
                Day("2일", listOf(Activity.Local("현지", km = 90.0))),
            ),
        )
        val plan = planner.plan(schedule)
        assertTrue(plan.isFeasible)
        val stop = plan.stops.single()
        assertEquals("아침 급속", stop.name)
        assertTrue(stop.fast)
    }

    @Test
    fun `충전 수단이 전혀 없으면 현지 일정에서 불가능으로 판정한다`() {
        val schedule = Schedule(
            30.0,
            listOf(
                Day("1일", emptyList()),
                Day("2일", listOf(Activity.Local("현지", km = 90.0))),
            ),
        )
        val failure = assertNotNull(planner.plan(schedule).infeasibility)
        assertEquals(1, failure.dayIndex)
        assertEquals(FailureReason.BELOW_FLOOR, failure.reason)
    }

    @Test
    fun `차박으로 하한 아래가 되면 불가능으로 판정한다`() {
        val schedule = Schedule(25.0, listOf(Day("1일", emptyList(), Night.CarCamp())))
        val failure = assertNotNull(planner.plan(schedule).infeasibility)
        assertEquals("차박", failure.activityLabel)
    }

    @Test
    fun `완속 시간이 모자라면 가능한 만큼만 충전하고 경고한다`() {
        val site = ChargingSite(slow = ChargingPlace("숙소", 7.0, targetPct = 80.0, availableHours = 2.0))
        val schedule = Schedule(
            30.0,
            listOf(
                Day("1일", emptyList(), Night.Lodging(site)),
                Day("2일", listOf(Activity.Local("현지", km = 50.0))),
            ),
        )
        val plan = planner.plan(schedule)
        val stop = plan.stops.single()
        assertEquals(120.0, stop.minutes, 1e-9)
        assertEquals(14.0, stop.kwh, 1e-9)
        assertEquals(1, plan.warnings.size)
    }

    @Test
    fun `완속 효율을 넣으면 충전 시간이 늘어난다`() {
        val lossless = planner.plan(Scenarios.week()).stops.single()
        val lossy = Planner(demo.copy(slowChargeEfficiency = 0.9)).plan(Scenarios.week()).stops.single()
        assertEquals(lossless.minutes / 0.9, lossy.minutes, 1e-9)
    }

    @Test
    fun `거리 없이 사용 시간만 넣으면 평균 속도로 추정한다`() {
        val plan = planner.plan(schedule(80.0, Activity.Local("현지", hours = 5.0)))
        assertEquals(90.0, plan.summary.totalKm, 1e-9)
    }

    @Test
    fun `도심 전비는 저온에서 기획서 계수만큼 떨어진다`() {
        val rated = Vehicle.ioniq9Rated()
        assertEquals(4.7 * 0.65, rated.kmPerKwh(RoadType.CITY, -20.0), 1e-9)
        assertEquals(4.7 * 0.73, rated.kmPerKwh(RoadType.CITY, 0.0), 1e-9)
        assertEquals(4.7, rated.kmPerKwh(RoadType.CITY, 30.0), 1e-9)
        assertEquals(3.9 * 0.96, rated.kmPerKwh(RoadType.HIGHWAY, 10.0), 1e-9)
        assertEquals(4.7, rated.kmPerKwh(RoadType.CITY, null), 1e-9)
    }

    @Test
    fun `운행 기록이 전비 보정값을 움직이고 짧은 운행은 무시한다`() {
        val calibrator = Calibrator(alpha = 0.5)
        val updated = calibrator.afterTrip(simple, 44.0, 10.0, RoadType.HIGHWAY, null)
        assertEquals(1.05, updated.calibration.efficiencyFactor, 1e-9)
        assertEquals(4.2, updated.kmPerKwh(RoadType.HIGHWAY, null), 1e-9)

        assertEquals(simple, calibrator.afterTrip(simple, 3.0, 1.0, RoadType.CITY, null))
    }

    @Test
    fun `공조와 충전 기록이 계획 계산에 반영된다`() {
        val calibrator = Calibrator()
        val hvac = calibrator.afterHvacStay(simple, 8.0, 16.0)
        assertEquals(2.0, hvac.calibration.hvacKw)
        assertEquals(16.0, hvac.hvacKwh(8.0, null, null), 1e-9)

        val fast = calibrator.afterFastCharge(simple, 30.0, 50.0)
        assertEquals(100.0, fast.calibration.fastChargeKw)
        assertEquals(100.0, fast.fastPowerKw(350.0))
    }

    @Test
    fun `주행 중 확인은 도달 불가와 계획 대비 차이를 알린다`() {
        val fine = checkWhileDriving(simple, 50.0, 40.0, RoadType.HIGHWAY, null, plannedArrivalPct = 41.0)
        assertTrue(fine.canReach)
        assertFalse(fine.shouldAlert)
        assertEquals(-1.0, fine.deviationPct!!, 1e-9)

        val behind = checkWhileDriving(simple, 50.0, 40.0, RoadType.HIGHWAY, null, plannedArrivalPct = 45.0)
        assertTrue(behind.shouldAlert)

        val short = checkWhileDriving(simple, 25.0, 40.0, RoadType.HIGHWAY, null)
        assertFalse(short.canReach)
        assertTrue(short.shouldAlert)
        assertNull(short.deviationPct)
    }

    @Test
    fun `기온 차이 모델은 기획서 예시값을 지난다`() {
        val model = HvacPowerModel.byTemperatureGap(0.3, 0.063, 1.25)
        assertEquals(1.245, model.kw(5.0, 20.0), 1e-9)
        assertEquals(1.25, model.kw(null, null), 1e-9)
        assertTrue(model.kw(-10.0, 20.0) > model.kw(5.0, 20.0))
    }

    @Test
    fun `잘못된 입력은 거부한다`() {
        assertFailsWith<IllegalArgumentException> { Schedule(120.0, emptyList()) }
        assertFailsWith<IllegalArgumentException> { Activity.Local("현지") }
        assertFailsWith<IllegalArgumentException> { PlannerConfig(floorPct = 80.0, fastTargetPct = 80.0) }
        assertIs<Plan>(planner.plan(Schedule(50.0, emptyList())))
    }
}
