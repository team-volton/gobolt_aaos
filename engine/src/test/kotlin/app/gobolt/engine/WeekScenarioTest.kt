package app.gobolt.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeekScenarioTest {
    private val planner = Planner(Vehicle.ioniq9Demo())

    @Test
    fun `기본 계획은 목요일 밤 완속 충전 한 번이다`() {
        val plan = planner.plan(Scenarios.week())
        assertTrue(plan.isFeasible)

        assertDoc(61, plan.socAtEndOfDay(0)!!)
        assertDoc(52, plan.socAtEndOfDay(1)!!)
        assertDoc(43, plan.socAtEndOfDay(2)!!)

        val stop = plan.stops.single()
        assertEquals(3, stop.dayIndex)
        assertDoc(27, stop.fromPct)
        assertDoc(8.4, stop.minutes / 60, 0.05)

        assertDoc(71, plan.socAtEndOfDay(4)!!)
        assertDoc(230, plan.summary.totalKm, 0.001)
        assertDoc(58, plan.summary.totalKwh)
    }

    @Test
    fun `수요일 외근을 넣으면 충전이 수요일 밤으로 당겨진다`() {
        val plan = planner.plan(Scenarios.week(fieldTripOnWednesday = true))
        assertTrue(plan.isFeasible)

        val stop = plan.stops.single()
        assertEquals(2, stop.dayIndex)
        // 기획서의 23%는 반올림 값끼리 뺀 결과 / The documented 23% subtracts rounded values
        assertDoc(22.4, stop.fromPct, 0.05)
        assertDoc(9.1, stop.minutes / 60, 0.05)

        assertDoc(55, plan.socAtEndOfDay(4)!!)
        assertDoc(320, plan.summary.totalKm, 0.001)
        assertDoc(80, plan.summary.totalKwh, 0.001)
    }

    @Test
    fun `차 안 대기 1시간만큼 공조 에너지를 더한다`() {
        val schedule = Schedule(
            70.0,
            listOf(Day("월", listOf(Activity.Waiting("외근지 대기", hours = 1.0)))),
        )
        val summary = planner.plan(schedule).summary
        assertEquals(1.25, summary.totalKwh, 1e-9)
        assertEquals(70 - 1.25 / 110.3 * 100, summary.endSocPct, 1e-9)
    }
}
