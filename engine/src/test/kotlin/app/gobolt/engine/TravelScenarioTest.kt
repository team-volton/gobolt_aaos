package app.gobolt.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TravelScenarioTest {
    private val planner = Planner(Vehicle.ioniq9Demo())

    @Test
    fun `기본 계획의 충전 지점과 잔량이 기획서와 같다`() {
        val plan = planner.plan(Scenarios.travel())
        assertTrue(plan.isFeasible)

        val (first, overnight, second) = plan.stops
        assertEquals(3, plan.stops.size)

        assertEquals("칠곡휴게소", first.name)
        assertDoc(26, first.fromPct)
        assertDoc(80, first.toPct, 0.001)
        assertDoc(19, first.minutes)

        assertDoc(44, plan.socAtEndOfDay(0)!!)

        assertEquals("숙소 완속", overnight.name)
        assertEquals(1, overnight.dayIndex)
        assertDoc(23, overnight.fromPct)
        assertDoc(90, overnight.toPct, 0.001)
        assertDoc(10.5, overnight.minutes / 60, 0.05)

        assertDoc(70, plan.socAtEndOfDay(2)!!)

        assertEquals("칠곡휴게소(서울방향)", second.name)
        assertDoc(33, second.fromPct)
        assertDoc(16, second.minutes)
        assertDoc(26, plan.summary.endSocPct)
    }

    @Test
    fun `기본 계획의 합계가 기획서와 같다`() {
        val summary = planner.plan(Scenarios.travel()).summary
        assertDoc(980, summary.totalKm, 0.001)
        assertDoc(245, summary.totalKwh, 0.001)
        assertEquals(2, summary.fastCharges)
        assertEquals(1, summary.slowCharges)
        assertDoc(35, summary.fastMinutes)
    }

    @Test
    fun `3일차 차박을 켜면 뒤쪽 계획만 바뀐다`() {
        val off = planner.plan(Scenarios.travel())
        val on = planner.plan(Scenarios.travel(carCampThirdNight = true))
        assertTrue(on.isFeasible)

        val morning = on.events.first { it.dayIndex == 3 }.fromPct
        assertDoc(60.5, morning, 0.05)

        val stop = on.stops.last()
        assertEquals("칠곡휴게소(서울방향)", stop.name)
        assertDoc(24, stop.fromPct)
        assertDoc(19, stop.minutes)
        assertDoc(255, on.summary.totalKwh, 0.001)
        assertDoc(38, on.summary.fastMinutes)
        assertEquals(3, on.summary.fastCharges + on.summary.slowCharges)

        assertEquals(off.stops.take(2), on.stops.take(2))
    }

    @Test
    fun `칠곡을 못 쓰면 김천으로 바뀌고 첫날 밤 충전이 추가된다`() {
        val plan = planner.plan(Scenarios.travel(), unavailableChargerIds = setOf(Scenarios.chilgok.id))
        assertTrue(plan.isFeasible)

        val stop = plan.stops.first()
        assertEquals("김천휴게소", stop.name)
        assertDoc(33, stop.fromPct)
        assertDoc(31, stop.minutes)
        assertDoc(36, plan.events.last { it.dayIndex == 0 && it.kind == EventKind.DRIVE }.toPct)

        val firstNight = plan.stops[1]
        assertEquals("숙소 완속", firstNight.name)
        assertEquals(0, firstNight.dayIndex)
    }

    @Test
    fun `대체 후보별 도달 가능 여부를 계산한다`() {
        val drive = Scenarios.travel().days[0].activities[0] as Activity.Drive
        val options = planner.chargerOptions(drive, 80.0, setOf(Scenarios.chilgok.id))
            .associateBy { it.charger.id }

        val kimcheon = options.getValue(Scenarios.kimcheon.id)
        assertTrue(kimcheon.reachable)
        assertDoc(33, kimcheon.arrivalPct)
        assertDoc(36, kimcheon.destinationArrivalPct)

        val chilgok = options.getValue(Scenarios.chilgok.id)
        assertFalse(chilgok.available)
        assertTrue(chilgok.reachable)

        val pyeongsa = options.getValue(Scenarios.pyeongsa.id)
        assertFalse(pyeongsa.reachable)
        assertDoc(13.4, pyeongsa.arrivalPct, 0.05)
    }

    @Test
    fun `5도에서 공인 고속 전비를 쓰면 칠곡 도착이 약 21퍼센트다`() {
        val vehicle = Vehicle.ioniq9Rated()
        assertDoc(3.7, vehicle.kmPerKwh(RoadType.HIGHWAY, 5.0), 0.05)
        val check = checkWhileDriving(vehicle, 80.0, 240.0, RoadType.HIGHWAY, 5.0)
        assertDoc(21, check.arrivalPct)
    }
}
