package app.gobolt.engine

import kotlin.test.assertEquals

object Scenarios {
    val kimcheon = Charger("kimcheon-s", "김천휴게소", 207.0, 100.0)
    val chilgok = Charger("chilgok-s", "칠곡휴게소", 240.0, 350.0)
    val pyeongsa = Charger("pyeongsa-s", "평사휴게소", 294.0, 100.0)
    val chilgokNorth = Charger("chilgok-n", "칠곡휴게소(서울방향)", 160.0, 350.0)

    private val lodging = ChargingSite(slow = ChargingPlace("숙소 완속", 7.0, targetPct = 90.0))
    private val home = ChargingSite(slow = ChargingPlace("공용 완속", 7.0, targetPct = 80.0))

    private val toBusan = Activity.Drive(
        "서울 → 부산", 400.0, chargers = listOf(kimcheon, chilgok, pyeongsa),
    )
    private val toSeoul = Activity.Drive("부산 → 서울", 400.0, chargers = listOf(chilgokNorth))
    private val localUse = Activity.Local("현지 이동", km = 90.0)

    fun travel(carCampThirdNight: Boolean = false) = Schedule(
        startSocPct = 80.0,
        days = listOf(
            Day("11/1", listOf(toBusan), Night.Lodging(lodging)),
            Day("11/2", listOf(localUse), Night.Lodging(lodging)),
            Day(
                "11/3",
                listOf(localUse),
                if (carCampThirdNight) Night.CarCamp() else Night.Lodging(lodging),
            ),
            Day("11/4", listOf(toSeoul)),
        ),
    )

    private val commute = Activity.Local("출퇴근", km = 40.0)

    fun week(fieldTripOnWednesday: Boolean = false): Schedule {
        val wednesday = if (fieldTripOnWednesday) {
            listOf(commute, Activity.Local("인천 외근", km = 90.0))
        } else {
            listOf(commute)
        }
        val nights = Night.Lodging(home)
        return Schedule(
            startSocPct = 70.0,
            days = listOf(
                Day("월", listOf(commute), nights),
                Day("화", listOf(commute), nights),
                Day("수", wednesday, nights),
                Day("목", listOf(commute, Activity.Local("저녁 약속", km = 30.0)), nights),
                Day("금", listOf(commute), nights),
                Day("토", night = nights),
                Day("일", night = nights),
            ),
        )
    }
}

// 기획서 수치는 반올림 값 / Documented figures are rounded
fun assertDoc(documented: Number, actual: Double, tolerance: Double = 0.5, what: String = "") {
    assertEquals(documented.toDouble(), actual, tolerance, what)
}
