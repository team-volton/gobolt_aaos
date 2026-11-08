package app.gobolt.demo

import app.gobolt.data.AppSettings
import app.gobolt.data.HomeCharge
import app.gobolt.engine.Activity
import app.gobolt.engine.Charger
import app.gobolt.engine.ChargingPlace
import app.gobolt.engine.ChargingSite
import app.gobolt.engine.Day
import app.gobolt.engine.Night
import app.gobolt.engine.Schedule
import app.gobolt.navi.NaviPlace

enum class Stay { LODGING, CAR_CAMP }

data class TripInput(
    val localKm: List<Int> = listOf(90, 90),
    val nights: List<Stay> = listOf(Stay.LODGING, Stay.LODGING, Stay.LODGING),
) {
    val totalKm: Int get() = DemoTrip.LEG_KM * 2 + localKm.sum()
}

object DemoTrip {
    const val LEG_KM = 400

    // 내비 연동과 개략도 전용 근사 좌표 / Approximate coordinates for navi checks and the sketch only
    val seoul = NaviPlace("서울", 37.5550, 126.9707)
    val busan = NaviPlace("부산역", 35.1152, 129.0415)
    val chilgok = NaviPlace("칠곡휴게소", 36.0100, 128.4000)
    private val kimcheon = NaviPlace("김천휴게소", 36.1300, 128.1200)
    private val pyeongsa = NaviPlace("평사휴게소", 35.9000, 128.8500)

    val routeLine = listOf(seoul, kimcheon, chilgok, pyeongsa, busan)

    private val chargerPlaces = mapOf(
        "kimcheon-s" to kimcheon,
        "chilgok-s" to chilgok,
        "pyeongsa-s" to pyeongsa,
        "chilgok-n" to chilgok,
    )

    fun placeOf(chargerId: String?): NaviPlace? = chargerPlaces[chargerId]

    fun schedule(startSocPct: Double, input: TripInput = TripInput()): Schedule {
        val slow = ChargingSite(slow = ChargingPlace("숙소 완속", 7.0, targetPct = 90.0))
        fun night(index: Int) = when (input.nights[index]) {
            Stay.LODGING -> Night.Lodging(slow)
            Stay.CAR_CAMP -> Night.CarCamp()
        }
        return Schedule(
            startSocPct = startSocPct,
            days = listOf(
                Day(
                    "1일차",
                    listOf(
                        Activity.Drive(
                            "서울 → 부산",
                            LEG_KM.toDouble(),
                            chargers = listOf(
                                Charger("kimcheon-s", "김천휴게소", 207.0, 100.0),
                                Charger("chilgok-s", "칠곡휴게소", 240.0, 350.0),
                                Charger("pyeongsa-s", "평사휴게소", 294.0, 100.0),
                            ),
                        ),
                    ),
                    night(0),
                ),
                Day("2일차", listOf(Activity.Local("현지 이동", km = input.localKm[0].toDouble())), night(1)),
                Day("3일차", listOf(Activity.Local("현지 이동", km = input.localKm[1].toDouble())), night(2)),
                Day(
                    "4일차",
                    listOf(
                        Activity.Drive(
                            "부산 → 서울",
                            LEG_KM.toDouble(),
                            chargers = listOf(Charger("chilgok-n", "칠곡휴게소(서울방향)", 160.0, 350.0)),
                        ),
                    ),
                ),
            ),
        )
    }

    val weekdays = listOf("월", "화", "수", "목", "금", "토", "일")

    fun weekdaysFrom(todayIndex: Int): List<String> = List(7) { weekdays[(todayIndex + it) % 7] }

    // 오늘부터 7일 / Seven days starting today
    fun week(startSocPct: Double, todayIndex: Int, settings: AppSettings): Schedule {
        val commute = Activity.Local("출퇴근", km = settings.commuteKm.toDouble())
        val site = when (settings.homeCharge) {
            HomeCharge.NONE -> ChargingSite()
            else -> ChargingSite(slow = ChargingPlace(settings.homeCharge.label, 7.0, targetPct = settings.targetPct.toDouble()))
        }
        return Schedule(
            startSocPct = startSocPct,
            days = List(7) { offset ->
                val weekday = (todayIndex + offset) % 7
                Day(weekdays[weekday], if (weekday in settings.commuteDays) listOf(commute) else emptyList(), Night.Lodging(site))
            },
        )
    }
}
