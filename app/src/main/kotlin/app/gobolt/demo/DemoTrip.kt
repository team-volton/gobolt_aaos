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

object DemoTrip {
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

    val outboundChargers = listOf(
        Charger("kimcheon-s", "김천휴게소", 207.0, 100.0),
        Charger("chilgok-s", "칠곡휴게소", 240.0, 350.0),
        Charger("pyeongsa-s", "평사휴게소", 294.0, 100.0),
    )

    // 경부선 서울 → 부산 기준 / Gyeongbu line, Seoul to Busan
    val returnChargers = listOf(Charger("chilgok-n", "칠곡휴게소(서울방향)", 160.0, 350.0))

    val weekdays = listOf("월", "화", "수", "목", "금", "토", "일")

    fun weekdaysFrom(todayIndex: Int): List<String> = List(7) { weekdays[(todayIndex + it) % 7] }

    // 오늘부터 7일 / Seven days starting today
    // blockedNight 밤은 충전기를 못 쓴다고 가정 / Assumes the charger is taken on blockedNight
    fun week(
        startSocPct: Double,
        todayIndex: Int,
        settings: AppSettings,
        extraTodayKm: Int = 0,
        blockedNight: Int? = null,
    ): Schedule {
        val commute = Activity.Local("출퇴근", km = settings.commuteKm.toDouble())
        val site = when (settings.homeCharge) {
            HomeCharge.NONE -> ChargingSite()
            else -> ChargingSite(slow = ChargingPlace(settings.homeCharge.label, 7.0, targetPct = settings.targetPct.toDouble()))
        }
        return Schedule(
            startSocPct = startSocPct,
            days = List(7) { offset ->
                val weekday = (todayIndex + offset) % 7
                val usual = if (weekday in settings.commuteDays) listOf(commute) else emptyList()
                val extra = if (offset == 0 && extraTodayKm > 0) listOf(Activity.Local("외근", km = extraTodayKm.toDouble())) else emptyList()
                val routines = settings.routines.filter { it.weekday == weekday }.map { Activity.Local(it.label, km = it.km.toDouble()) }
                Day(weekdays[weekday], usual + routines + extra, Night.Lodging(if (offset == blockedNight) ChargingSite() else site))
            },
        )
    }
}
