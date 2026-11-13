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

enum class EfficiencyMode(val label: String) {
    DEMO("시연 4.0"),
    RATED("공인 + 기온"),
}

data class Routine(val weekday: Int, val km: Int, val label: String = "약속")

data class AppSettings(
    val floorPct: Int = 20,
    val targetPct: Int = 80,
    val homeCharge: HomeCharge = HomeCharge.SHARED,
    val commuteKm: Int = 40,
    // 0 = 월요일 / 0 is Monday
    val commuteDays: Set<Int> = setOf(0, 1, 2, 3, 4),
    val routines: List<Routine> = emptyList(),
    val efficiencyMode: EfficiencyMode = EfficiencyMode.DEMO,
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
    val epochDay: Long = 0,
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
            routines = json.optJSONArray("routines")?.let { list ->
                List(list.length()) {
                    val item = list.getJSONObject(it)
                    Routine(item.getInt("weekday"), item.getInt("km"), item.getString("label"))
                }
            } ?: emptyList(),
            efficiencyMode = json.optString("efficiencyMode").let { name ->
                EfficiencyMode.entries.firstOrNull { it.name == name } ?: EfficiencyMode.DEMO
            },
        )
    }

    fun save(settings: AppSettings) {
        val json = JSONObject()
            .put("floorPct", settings.floorPct)
            .put("targetPct", settings.targetPct)
            .put("homeCharge", settings.homeCharge.name)
            .put("commuteKm", settings.commuteKm)
            .put("commuteDays", JSONArray(settings.commuteDays.toList()))
            .put(
                "routines",
                JSONArray(settings.routines.map { JSONObject().put("weekday", it.weekday).put("km", it.km).put("label", it.label) }),
            )
            .put("efficiencyMode", settings.efficiencyMode.name)
        prefs.edit().putString(KEY_SETTINGS, json.toString()).apply()
    }

    fun loadTrip(): Trip {
        val json = prefs.getString(KEY_TRIP, null)?.let(::JSONObject) ?: return Trip()
        return try {
            val km = json.getJSONArray("localKm")
            val nights = json.getJSONArray("nights")
            fun ints(key: String) = json.optJSONArray(key)?.let { list -> List(list.length(), list::getInt) }
            Trip(
                destination = NaviPlace(json.getString("name"), json.getDouble("lat"), json.getDouble("lon")),
                startEpochDay = json.getLong("startEpochDay"),
                departMinutes = json.getInt("departMinutes"),
                localKm = List(km.length(), km::getInt),
                nights = List(nights.length()) { Stay.valueOf(nights.getString(it)) },
                localHours = ints("localHours") ?: List(km.length()) { 0 },
                waitHours = ints("waitHours") ?: List(km.length()) { 0 },
                dayTemps = ints("dayTemps") ?: emptyList(),
                outsideC = json.optInt("outsideC", 5),
                waypoint = if (json.has("wpName")) {
                    NaviPlace(json.getString("wpName"), json.getDouble("wpLat"), json.getDouble("wpLon"))
                } else {
                    null
                },
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
        trip.waypoint?.let { json.put("wpName", it.name).put("wpLat", it.latitude).put("wpLon", it.longitude) }
        json.put("localHours", JSONArray(trip.localHours))
            .put("waitHours", JSONArray(trip.waitHours))
            .put("dayTemps", JSONArray(trip.dayTemps))
            .put("outsideC", trip.outsideC)
        prefs.edit().putString(KEY_TRIP, json.toString()).apply()
    }

    // 날짜별 외근 거리 / Extra field trip distance by date
    fun loadExtraKm(): Map<Long, Int> {
        val json = prefs.getString(KEY_EXTRA, null)?.let(::JSONObject) ?: return emptyMap()
        return json.keys().asSequence().associate { it.toLong() to json.getInt(it) }
    }

    fun saveExtraKm(extra: Map<Long, Int>) {
        val json = JSONObject()
        extra.forEach { (day, km) -> json.put(day.toString(), km) }
        prefs.edit().putString(KEY_EXTRA, json.toString()).apply()
    }

    fun loadFastChargeKw(): Double? = prefs.getFloat(KEY_FAST_KW, 0f).toDouble().takeIf { it > 0 }

    fun saveFastChargeKw(kw: Double) {
        prefs.edit().putFloat(KEY_FAST_KW, kw.toFloat()).apply()
    }

    // 같은 알림을 하루 한 번만 / Shows the same notice once a day
    fun notifiedOn(key: String): Long = prefs.getLong("notified_$key", -1)

    fun markNotified(key: String, epochDay: Long) {
        prefs.edit().putLong("notified_$key", epochDay).apply()
    }

    fun loadEfficiencyFactor(): Double = prefs.getFloat(KEY_EFFICIENCY, 1f).toDouble()

    fun saveEfficiencyFactor(factor: Double) {
        prefs.edit().putFloat(KEY_EFFICIENCY, factor.toFloat()).apply()
    }

    fun loadRecords(): List<DriveRecord> {
        val list = prefs.getString(KEY_RECORDS, null)?.let(::JSONArray) ?: return emptyList()
        return List(list.length()) { index ->
            val json = list.getJSONObject(index)
            DriveRecord(
                name = json.getString("name"),
                km = json.getDouble("km"),
                minutes = json.getDouble("minutes"),
                startSocPct = json.getDouble("startSocPct"),
                endSocPct = json.getDouble("endSocPct"),
                usedKwh = json.getDouble("usedKwh"),
                chargedPct = json.getDouble("chargedPct"),
                plannedKwh = json.getDouble("plannedKwh"),
                plannedEndSocPct = json.getDouble("plannedEndSocPct"),
                epochDay = json.optLong("epochDay"),
            )
        }
    }

    // 최근 기록이 앞 / Newest first
    fun add(record: DriveRecord) {
        val records = (listOf(record) + loadRecords()).take(MAX_RECORDS)
        val list = JSONArray(
            records.map {
                JSONObject()
                    .put("name", it.name)
                    .put("km", it.km)
                    .put("minutes", it.minutes)
                    .put("startSocPct", it.startSocPct)
                    .put("endSocPct", it.endSocPct)
                    .put("usedKwh", it.usedKwh)
                    .put("chargedPct", it.chargedPct)
                    .put("plannedKwh", it.plannedKwh)
                    .put("plannedEndSocPct", it.plannedEndSocPct)
                    .put("epochDay", it.epochDay)
            },
        )
        prefs.edit().putString(KEY_RECORDS, list.toString()).apply()
    }

    private companion object {
        const val KEY_SETTINGS = "settings"
        const val KEY_TRIP = "trip_v2"
        const val KEY_EXTRA = "extra_map"
        const val KEY_FAST_KW = "fast_charge_kw"
        const val KEY_EFFICIENCY = "efficiency_factor"
        const val KEY_RECORDS = "records"
        const val MAX_RECORDS = 20
    }
}
