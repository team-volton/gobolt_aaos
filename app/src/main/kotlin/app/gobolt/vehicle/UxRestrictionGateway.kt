package app.gobolt.vehicle

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// 차량이 알려주는 주행 중 화면 제한 / Driving restrictions reported by the car
class UxRestrictionGateway(context: Context) {
    private val appContext = context.applicationContext
    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null

    private val _restricted = MutableStateFlow(false)
    val restricted: StateFlow<Boolean> = _restricted.asStateFlow()

    fun start() {
        // 연결 대기 없는 생성은 API 30부터 / Non-blocking creation needs API 30
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            car = Car.createCar(appContext, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connected, ready ->
                if (!ready) return@createCar
                val ux = connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
                    ?: return@createCar
                manager = ux
                _restricted.value = ux.currentCarUxRestrictions?.isRequiresDistractionOptimization == true
                ux.registerListener { restrictions ->
                    _restricted.value = restrictions.isRequiresDistractionOptimization
                }
            }
        } catch (e: RuntimeException) {
            // 차량 서비스가 없는 환경은 기어 기준으로만 판단 / Falls back to the gear when the car service is absent
            _restricted.value = false
        }
    }

    fun stop() {
        manager?.unregisterListener()
        car?.disconnect()
        car = null
    }
}
