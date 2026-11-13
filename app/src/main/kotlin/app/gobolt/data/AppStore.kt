package app.gobolt.data

import android.content.Context
import app.gobolt.navi.NaviPlace
import org.json.JSONArray
import org.json.JSONObject

enum class HomeCharge(val label: String) {
    PRIVATE("개인 완속"),
    SHARED("공용 완속"),
    NONE("없음"),
}

data class AppSettings(
    val floorPct: Int = 20,
    val targetPct: Int = 80,
    val homeCharge: HomeCharge = HomeCharge.SHARED,
    val commuteKm: Int = 40,
    // 0 = 월요일 / 0 is Monday
    val commuteDays: Set<Int> = setOf(0, 1, 2, 3, 4),
)

data class DriveRecord(
    val name: String,
    val km: Double,
    val minutes: Double,
    val startSocPct: Double,
    val endSocPct: Double,
    val usedKwh: Double,
    val chargedPct: Double,
    val plannedKwh: Double,
    val plannedEndSocPct: Double,
)

const val ORIGIN_NAME = "출발지"

class AppStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("gobolt", Context.MODE_PRIVATE)

    fun loadSettings(): AppSettings {
        val json = prefs.getString(KEY_SETTINGS, null)?.let(::JSONObject) ?: return AppSettings()
        val days = json.getJSONArray("commuteDays")
        return AppSettings(
            floorPct = json.getInt("floorPct"),
            targetPct = json.getInt("targetPct"),
            homeCharge = HomeCharge.valueOf(json.getString("homeCharge")),
            commuteKm = json.getInt("commuteKm"),
            commuteDays = List(days.length(), days::getInt).toSet(),
        )
    }

    fun save(settings: AppSettings) {
        val json = JSONObject()
            .put("floorPct", settings.floorPct)
            .put("targetPct", settings.targetPct)
            .put("homeCharge", settings.homeCharge.name)
            .put("commuteKm", settings.commuteKm)
            .put("commuteDays", JSONArray(settings.commuteDays.toList()))
        prefs.edit().putString(KEY_SETTINGS, json.toString()).apply()
    }

    fun loadTrip(): Trip {
        val json = prefs.getString(KEY_TRIP, null)?.let(::JSONObject) ?: return Trip()
        return try {
            val km = json.getJSONArray("localKm")
            val nights = json.getJSONArray("nights")
            Trip(
                destination = NaviPlace(json.getString("name"), json.getDouble("lat"), json.getDouble("lon")),
                startEpochDay = json.getLong("startEpochDay"),
                departMinutes = json.getInt("departMinutes"),
                localKm = List(km.length(), km::getInt),
                nights = List(nights.length()) { Stay.valueOf(nights.getString(it)) },
                origin = if (json.has("originLat")) {
                    NaviPlace(ORIGIN_NAME, json.getDouble("originLat"), json.getDouble("originLon"))
                } else {
                    null
                },
            )
        } catch (e: org.json.JSONException) {
            Trip()
        }
    }

    fun save(trip: Trip) {
        val json = JSONObject()
            .put("name", trip.destination.name)
            .put("lat", trip.destination.latitude)
            .put("lon", trip.destination.longitude)
            .put("startEpochDay", trip.startEpochDay)
            .put("departMinutes", trip.departMinutes)
            .put("localKm", JSONArray(trip.localKm))
            .put("nights", JSONArray(trip.nights.map { it.name }))
        trip.origin?.let { json.put("originLat", it.latitude).put("originLon", it.longitude) }
        prefs.edit().putString(KEY_TRIP, json.toString()).apply()
    }

    // 오늘 날짜에만 유효 / Valid only for today
    fun loadExtraKm(epochDay: Long): Int =
        if (prefs.getLong(KEY_EXTRA_DAY, -1) == epochDay) prefs.getInt(KEY_EXTRA_KM, 0) else 0

    fun saveExtraKm(epochDay: Long, km: Int) {
        prefs.edit().putLong(KEY_EXTRA_DAY, epochDay).putInt(KEY_EXTRA_KM, km).apply()
    }

    fun loadEfficiencyFactor(): Double = prefs.getFloat(KEY_EFFICIENCY, 1f).toDouble()

    fun saveEfficiencyFactor(factor: Double) {
        prefs.edit().putFloat(KEY_EFFICIENCY, factor.toFloat()).apply()
    }

    fun loadRecord(): DriveRecord? {
        val json = prefs.getString(KEY_RECORD, null)?.let(::JSONObject) ?: return null
        return DriveRecord(
            name = json.getString("name"),
            km = json.getDouble("km"),
            minutes = json.getDouble("minutes"),
            startSocPct = json.getDouble("startSocPct"),
            endSocPct = json.getDouble("endSocPct"),
            usedKwh = json.getDouble("usedKwh"),
            chargedPct = json.getDouble("chargedPct"),
            plannedKwh = json.getDouble("plannedKwh"),
            plannedEndSocPct = json.getDouble("plannedEndSocPct"),
        )
    }

    fun save(record: DriveRecord) {
        val json = JSONObject()
            .put("name", record.name)
            .put("km", record.km)
            .put("minutes", record.minutes)
            .put("startSocPct", record.startSocPct)
            .put("endSocPct", record.endSocPct)
            .put("usedKwh", record.usedKwh)
            .put("chargedPct", record.chargedPct)
            .put("plannedKwh", record.plannedKwh)
            .put("plannedEndSocPct", record.plannedEndSocPct)
        prefs.edit().putString(KEY_RECORD, json.toString()).apply()
    }

    private companion object {
        const val KEY_SETTINGS = "settings"
        const val KEY_TRIP = "trip_v2"
        const val KEY_EXTRA_DAY = "extra_day"
        const val KEY_EXTRA_KM = "extra_km"
        const val KEY_EFFICIENCY = "efficiency_factor"
        const val KEY_RECORD = "last_record"
    }
}
