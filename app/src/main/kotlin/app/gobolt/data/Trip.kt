package app.gobolt.data

import app.gobolt.demo.DemoTrip
import app.gobolt.engine.Activity
import app.gobolt.engine.Charger
import app.gobolt.engine.ChargingPlace
import app.gobolt.engine.ChargingSite
import app.gobolt.engine.Day
import app.gobolt.engine.Night
import app.gobolt.engine.Schedule
import app.gobolt.navi.NaviPlace
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class Stay { LODGING, CAR_CAMP }

// 1일차 출발, 중간 날은 현지 이동, 마지막 날 복귀
// Day one drives out, middle days are local, the last day drives back
data class Trip(
    val destination: NaviPlace = DemoTrip.busan,
    val startEpochDay: Long = LocalDate.now().plusDays(1).toEpochDay(),
    val departMinutes: Int = 8 * 60 + 40,
    // 중간 날마다 하나씩 / One per middle day
    val localKm: List<Int> = listOf(90, 90),
    // 0이면 거리로 계산 / Zero means use the distance
    val localHours: List<Int> = listOf(0, 0),
    val waitHours: List<Int> = listOf(0, 0),
    val nights: List<Stay> = List(3) { Stay.LODGING },
    // 목적지를 고를 때의 위치 / Where the car was when the destination was chosen
    val origin: NaviPlace? = null,
    // 1일차에 들르는 곳 / A stop on the way out
    val waypoint: NaviPlace? = null,
    val outsideC: Int = 5,
    // 날짜별 기온, 비어 있으면 outsideC / Per-day temperatures, else outsideC
    val dayTemps: List<Int> = emptyList(),
    val cabinC: Int = 20,
) {
    val startDate: LocalDate get() = LocalDate.ofEpochDay(startEpochDay)

    fun dateOf(day: Int): LocalDate = startDate.plusDays(day.toLong())

    fun tempOf(day: Int): Int = dayTemps.getOrElse(day) { outsideC }

    fun withNightCount(count: Int): Trip {
        val n = count.coerceIn(MIN_NIGHTS, MAX_NIGHTS)
        return copy(
            nights = List(n) { nights.getOrElse(it) { Stay.LODGING } },
            localKm = List(n - 1) { localKm.getOrElse(it) { DEFAULT_LOCAL_KM } },
            localHours = List(n - 1) { localHours.getOrElse(it) { 0 } },
            waitHours = List(n - 1) { waitHours.getOrElse(it) { 0 } },
            dayTemps = if (dayTemps.isEmpty()) emptyList() else List(n + 1) { dayTemps.getOrElse(it) { outsideC } },
        )
    }

    fun withTemp(day: Int, value: Int): Trip {
        val temps = List(nights.size + 1) { tempOf(it) }.toMutableList()
        temps[day] = value.coerceIn(MIN_TEMP, MAX_TEMP)
        return copy(dayTemps = temps)
    }

    companion object {
        const val MIN_NIGHTS = 1
        const val MAX_NIGHTS = 6
        const val DEFAULT_LOCAL_KM = 50
        const val MIN_TEMP = -20
        const val MAX_TEMP = 40
    }
}

fun LocalDate.label(): String =
    "${monthValue}/${dayOfMonth} (${dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)})"

fun Trip.summary(): String {
    val hour = departMinutes / 60
    val minute = departMinutes % 60
    return "${startDate.label()} %02d:%02d 출발 · ${nights.size}박 ${nights.size + 1}일".format(hour, minute)
}

data class TripLegs(
    val outKm: Double,
    val backKm: Double,
    val outChargers: List<Charger>,
    val backChargers: List<Charger>,
    // 내비가 계산한 거리인지 / Whether the navi calculated the distance
    val measured: Boolean,
    val backMeasured: Boolean = false,
)

object TripSchedule {
    // 직선거리를 도로 거리로 바꾸는 보정 / Converts straight-line distance to road distance
    private const val ROAD_FACTOR = 1.3
    private const val EARTH_KM = 6371.0

    fun estimateKm(from: NaviPlace, to: NaviPlace): Double {
        fun rad(deg: Double) = Math.toRadians(deg)
        val dLat = rad(to.latitude - from.latitude)
        val dLon = rad(to.longitude - from.longitude)
        val a = sin(dLat / 2).pow(2) + cos(rad(from.latitude)) * cos(rad(to.latitude)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_KM * asin(sqrt(a)) * ROAD_FACTOR
    }

    // fromDay 이전 날은 이미 지난 일정 / Days before fromDay have already passed
    fun build(trip: Trip, startSocPct: Double, legs: TripLegs, settings: AppSettings, fromDay: Int = 0): Schedule {
        val slow = ChargingSite(slow = ChargingPlace("숙소 완속", 7.0, targetPct = 90.0))
        val lastDay = trip.nights.size
        val days = (fromDay.coerceIn(0, lastDay)..lastDay).map { day ->
            val temp = trip.tempOf(day).toDouble()
            val activities = when (day) {
                0 -> listOf(Activity.Drive("${trip.destination.name}까지", legs.outKm, tempC = temp, chargers = legs.outChargers))
                lastDay -> listOf(Activity.Drive("집으로", legs.backKm, tempC = temp, chargers = legs.backChargers))
                else -> middleDay(trip, day - 1, temp)
            }
            val night = when {
                day == lastDay -> Night.Lodging(homeSite(settings))
                trip.nights[day] == Stay.CAR_CAMP -> Night.CarCamp(outsideC = temp, cabinC = trip.cabinC.toDouble())
                else -> Night.Lodging(slow)
            }
            Day(trip.dateOf(day).label(), activities, night)
        }
        return Schedule(startSocPct = startSocPct, days = days)
    }

    private fun middleDay(trip: Trip, index: Int, temp: Double): List<Activity> {
        val hours = trip.localHours.getOrElse(index) { 0 }
        val local = if (hours > 0) {
            Activity.Local("현지 이동", hours = hours.toDouble(), tempC = temp)
        } else {
            Activity.Local("현지 이동", km = trip.localKm[index].toDouble(), tempC = temp)
        }
        val wait = trip.waitHours.getOrElse(index) { 0 }
        return if (wait > 0) {
            listOf(local, Activity.Waiting("정차 대기", wait.toDouble(), outsideC = temp, cabinC = trip.cabinC.toDouble()))
        } else {
            listOf(local)
        }
    }

    private fun homeSite(settings: AppSettings) = when (settings.homeCharge) {
        HomeCharge.NONE -> ChargingSite()
        else -> ChargingSite(slow = ChargingPlace(settings.homeCharge.label, 7.0, targetPct = settings.targetPct.toDouble()))
    }
}
