package com.abanana.abattery.presentation.battery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abanana.abattery.data.battery.BatteryDataSource
import com.abanana.abattery.domain.model.BatteryInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@HiltViewModel
class BatteryViewModel @Inject constructor(
    private val batteryDataSource: BatteryDataSource,
) : ViewModel() {

    private val _batteryInfo = MutableStateFlow<BatteryInfo?>(null)
    val batteryInfo = _batteryInfo.asStateFlow()

    private val _currentHistoryMa = MutableStateFlow<List<Float>>(emptyList())
    val currentHistoryMa = _currentHistoryMa.asStateFlow()

    private val historyBuffer = ArrayDeque<Float>(120)

    private var samplingJob: Job? = null

    fun startSampling() {
        if (samplingJob?.isActive == true) return
        samplingJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val info = batteryDataSource.getBatteryInfo()
                    _batteryInfo.value = info
                    val ma = info.currentMicroA?.let { it / 1000f } ?: 0f
                    historyBuffer.addLast(ma)
                    while (historyBuffer.size > 120) {
                        historyBuffer.removeFirst()
                    }
                    _currentHistoryMa.value = historyBuffer.toList()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
                delay(500L)
            }
        }
    }

    fun stopSampling() {
        samplingJob?.cancel()
        samplingJob = null
    }
}
