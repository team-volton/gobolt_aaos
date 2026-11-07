package app.gobolt.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import android.content.Context
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

data class VehicleState(
    val socPct: Int? = null,
    val odometerKm: Float? = null,
    val error: String? = null,
)

class VehicleGateway(context: Context) {
    private val vehicle = Vehicle(context.applicationContext)
    private val battery = vehicle.getEvBattery()
    private val odometer = vehicle.getOdometer()

    private val _state = MutableStateFlow(VehicleState())
    val state: StateFlow<VehicleState> = _state.asStateFlow()

    private var registered = false

    private val socListener = object : EvBatteryStateOfChargeListener {
        override fun onEvBatteryStateOfChargeUpdated(value: Int?) {
            _state.update { it.copy(socPct = value, error = null) }
        }

        override fun onFailed(e: Exception) {
            _state.update { it.copy(error = e.message ?: e.javaClass.simpleName) }
        }
    }

    fun start(scope: CoroutineScope) {
        vehicle.initialize()
        scope.launch {
            // 서비스 연결이 비동기라 첫 호출은 실패할 수 있음
            // The service binds asynchronously, so early calls can fail
            retry { readSoc() }
                .onSuccess { value ->
                    _state.update { it.copy(socPct = value, error = null) }
                    battery.registerEvBatteryStateOfCharge(socListener)
                    registered = true
                }
                .onFailure { failure ->
                    _state.update { it.copy(error = failure.message ?: failure.javaClass.simpleName) }
                }
            readOdometer().onSuccess { value -> _state.update { it.copy(odometerKm = value) } }
        }
    }

    fun stop() {
        if (registered) battery.unregisterEvBatteryStateOfCharge(socListener)
        registered = false
        vehicle.release()
    }

    private suspend fun readSoc(): Result<Int?> = awaitResult { done, failed ->
        battery.getEvBatteryStateOfCharge(done, failed)
    }

    private suspend fun readOdometer(): Result<Float?> = awaitResult { done, failed ->
        odometer.getCurrentDistanceDriven(done, failed)
    }

    private suspend fun <T> retry(attempts: Int = 10, call: suspend () -> Result<T>): Result<T> {
        var result = call()
        repeat(attempts - 1) {
            if (result.isSuccess) return result
            delay(RETRY_DELAY_MS)
            result = call()
        }
        return result
    }

    private suspend fun <T> awaitResult(
        call: (onComplete: (T) -> Unit, onFailed: (Exception) -> Unit) -> Unit,
    ): Result<T> = suspendCancellableCoroutine { continuation ->
        fun finish(result: Result<T>) {
            if (continuation.isActive) continuation.resume(result)
        }
        try {
            call({ finish(Result.success(it)) }, { finish(Result.failure(it)) })
        } catch (e: Exception) {
            finish(Result.failure(e))
        }
    }

    private companion object {
        const val RETRY_DELAY_MS = 1_000L
    }
}
