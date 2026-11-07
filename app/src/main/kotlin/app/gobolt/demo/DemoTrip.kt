package app.gobolt.demo

import app.gobolt.engine.Activity
import app.gobolt.engine.Charger
import app.gobolt.engine.ChargingPlace
import app.gobolt.engine.ChargingSite
import app.gobolt.engine.Day
import app.gobolt.engine.Night
import app.gobolt.engine.Schedule
import app.gobolt.navi.NaviPlace

object DemoTrip {
    // 내비 연동 확인용 근사 좌표 / Approximate coordinates for navi checks only
    val busan = NaviPlace("부산역", 35.1152, 129.0415)
    val chilgok = NaviPlace("칠곡휴게소", 36.0100, 128.4000)

    fun schedule(startSocPct: Double): Schedule {
        val lodging = ChargingSite(slow = ChargingPlace("숙소 완속", 7.0, targetPct = 90.0))
        val local = Activity.Local("현지 이동", km = 90.0)
        return Schedule(
            startSocPct = startSocPct,
            days = listOf(
                Day(
                    "1일차",
                    listOf(
                        Activity.Drive(
                            "서울 → 부산",
                            400.0,
                            chargers = listOf(
                                Charger("kimcheon-s", "김천휴게소", 207.0, 100.0),
                                Charger("chilgok-s", "칠곡휴게소", 240.0, 350.0),
                                Charger("pyeongsa-s", "평사휴게소", 294.0, 100.0),
                            ),
                        ),
                    ),
                    Night.Lodging(lodging),
                ),
                Day("2일차", listOf(local), Night.Lodging(lodging)),
                Day("3일차", listOf(local), Night.Lodging(lodging)),
                Day(
                    "4일차",
                    listOf(
                        Activity.Drive(
                            "부산 → 서울",
                            400.0,
                            chargers = listOf(Charger("chilgok-n", "칠곡휴게소(서울방향)", 160.0, 350.0)),
                        ),
                    ),
                ),
            ),
        )
    }
}
