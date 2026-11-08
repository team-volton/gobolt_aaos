package app.gobolt.data

import android.content.Context
import app.gobolt.demo.Stay
import app.gobolt.demo.TripInput
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

    fun loadTrip(): TripInput {
        val json = prefs.getString(KEY_TRIP, null)?.let(::JSONObject) ?: return TripInput()
        val km = json.getJSONArray("localKm")
        val nights = json.getJSONArray("nights")
        return TripInput(
            localKm = List(km.length(), km::getInt),
            nights = List(nights.length()) { Stay.valueOf(nights.getString(it)) },
        )
    }

    fun save(trip: TripInput) {
        val json = JSONObject()
            .put("localKm", JSONArray(trip.localKm))
            .put("nights", JSONArray(trip.nights.map { it.name }))
        prefs.edit().putString(KEY_TRIP, json.toString()).apply()
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
        const val KEY_TRIP = "trip"
        const val KEY_EFFICIENCY = "efficiency_factor"
        const val KEY_RECORD = "last_record"
    }
}
