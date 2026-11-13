package app.gobolt.navi

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.RoutePointPoiType
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.BookmarkInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.DrivingState
import ai.pleos.playground.navi.data.RecentDestinationInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteWithWaypointsRequest
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.TripRequest
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class NaviPlace(val name: String, val latitude: Double, val longitude: Double)

data class LiveLeg(
    val name: String,
    val remainKm: Double,
    val remainMinutes: Double,
    // 내비가 주는 값은 Wh / The navi reports watt-hours
    val arrivalWh: Int,
    val isCharger: Boolean,
)

data class NaviState(
    val connected: Boolean = false,
    val routeActive: Boolean = false,
    val destination: LiveLeg? = null,
    val nextStop: LiveLeg? = null,
    val arrivedKm: Double? = null,
    val places: List<NaviPlace> = emptyList(),
    val log: List<String> = emptyList(),
)

class NaviGateway(context: Context) {
    private val navi = NaviHelper(context.applicationContext)

    private val _state = MutableStateFlow(NaviState())
    val state: StateFlow<NaviState> = _state.asStateFlow()

    private val listener = object : NaviHelperEventListener {
        override fun onTripInfo(tripInfo: TripInfo) = log(describe(tripInfo))

        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            _state.update { it.copy(routeActive = true, arrivedKm = null) }
            log("경로 시작: ${routeStartInfo.distance / 1000}km")
            // 내비가 경유지를 더 넣었는지 확인 / Check whether the navi added waypoints
            navi.getWaypointInfo()
        }

        override fun onDrivingInfo(drivingInfo: DrivingInfo) {
            // 경로 계산 중에는 0이 올 수 있음 / Zeros can arrive while the route is still being calculated
            val destination = drivingInfo.destination.toLeg().takeIf { it.remainKm > 0 } ?: return
            val next = drivingInfo.waypoints.firstOrNull()?.toLeg()?.takeIf { it.remainKm > 0 } ?: destination
            _state.update { it.copy(routeActive = true, destination = destination, nextStop = next) }
        }

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            _state.update { it.copy(routeActive = false, arrivedKm = destinationArrivedInfo.distance / 1000.0) }
            log("도착: ${destinationArrivedInfo.name}")
        }

        override fun onRouteEnded() = _state.update { it.copy(routeActive = false) }

        override fun onRouteCancelled() = _state.update { it.copy(routeActive = false) }

        override fun onWayPointInfo(waypointInfoList: List<WaypointInfo>) {
            log("경유지 ${waypointInfoList.size}개: " + waypointInfoList.joinToString { it.name })
        }

        override fun onBookmarkInfo(bookmarkInfoList: List<BookmarkInfo>) =
            addPlaces(bookmarkInfoList.map { NaviPlace(it.name, it.latitude, it.longitude) })

        override fun onRecentDestinationInfo(recentDestinationInfoList: List<RecentDestinationInfo>) =
            addPlaces(recentDestinationInfoList.map { NaviPlace(it.name, it.latitude, it.longitude) })

        override fun onError(errorCode: NaviErrorCode) = log("오류: $errorCode")
    }

    fun start() {
        navi.initialize(
            onConnected = {
                navi.addListener(listener)
                _state.update { it.copy(connected = true) }
                log("내비 연결됨")
                navi.getBookmarkInfo()
                navi.getRecentDestinationInfo()
            },
            onFailed = { log("내비 연결 실패: ${it.message}") },
        )
    }

    fun stop() {
        navi.removeListener(listener)
        navi.release()
    }

    fun cancelRoute() = guarded {
        navi.cancelRoute()
        _state.update { it.copy(routeActive = false, destination = null, nextStop = null) }
    }

    fun requestTrip(destination: NaviPlace) = guarded {
        log("요청: ${destination.name}까지 예상값")
        navi.requestTrip(
            TripRequest(destination.latitude, destination.longitude, "", destination.name, null),
        )
    }

    fun sendRoute(destination: NaviPlace, waypoints: List<NaviPlace>) = guarded {
        // SDK의 경유지 번호는 FIRST, SECOND뿐 / The SDK only indexes FIRST and SECOND
        require(waypoints.size <= WAYPOINT_SLOTS.size) { "경유지는 ${WAYPOINT_SLOTS.size}개까지입니다." }
        log("요청: ${destination.name} 경로, 경유지 " + waypoints.joinToString { it.name })
        _state.update { it.copy(destination = null, nextStop = null) }
        navi.requestRouteWithWaypoints(
            RouteWithWaypointsRequest(
                destination = RouteInfo(
                    latitude = destination.latitude,
                    longitude = destination.longitude,
                    poiName = destination.name,
                    poiId = "",
                    address = "",
                    poiSubId = "",
                ),
                waypoints = waypoints.zip(WAYPOINT_SLOTS) { place, slot ->
                    RequestWaypointInfo(
                        latitude = place.latitude,
                        longitude = place.longitude,
                        poiName = place.name,
                        waypointIndex = slot,
                        poiId = "",
                        address = "",
                        poiSubId = "",
                    )
                },
            ),
        )
    }

    private fun DrivingState.toLeg() = LiveLeg(
        name = name,
        remainKm = distance / 1000.0,
        remainMinutes = duration / 60.0,
        arrivalWh = remainDestinationChargingValue,
        isCharger = isChargingStation,
    )

    private fun addPlaces(found: List<NaviPlace>) {
        _state.update { state -> state.copy(places = (state.places + found).distinctBy { it.name }) }
    }

    private fun guarded(call: () -> Unit) {
        try {
            call()
        } catch (e: Exception) {
            log("호출 실패: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun describe(trip: TripInfo): String {
        val chargers = trip.summary.routePoints
            .filter { it.poiType == RoutePointPoiType.EV_CHARGING }
            .joinToString { "${it.poiName}(${it.settingType})" }
            .ifEmpty { "없음" }
        val kwh = "%.1f".format(trip.energyConsumption / 1000)
        return "예상: ${trip.distance / 1000}km, ${trip.eta / 60}분, " +
            "도착 ${trip.estimatedSocAtDest}%, ${kwh}kWh, 충전소 $chargers"
    }

    private fun log(line: String) {
        _state.update { it.copy(log = (listOf(line) + it.log).take(MAX_LOG_LINES)) }
    }

    private companion object {
        val WAYPOINT_SLOTS = listOf(WaypointIndex.FIRST, WaypointIndex.SECOND)
        const val MAX_LOG_LINES = 30
    }
}
