package com.abanana.abattery.presentation.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abanana.abattery.data.device.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@HiltViewModel
class DeviceViewModel @Inject constructor(val repository: DeviceRepository) : ViewModel() {
    private val _snapshot = MutableStateFlow(DeviceSnapshot())
    val snapshot = _snapshot.asStateFlow()
    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps = _apps.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job =
            viewModelScope.launch(Dispatchers.IO) {
                refreshApps()
                var index = 0
                while (isActive) {
                    runCatching {
                            repository.snapshot(_snapshot.value, refreshDetails = index++ % 5 == 0)
                        }
                        .onSuccess {
                            _snapshot.value = it
                            _error.value = null
                        }
                        .onFailure {
                            _error.value =
                                "Could not refresh device data: ${it.javaClass.simpleName}"
                        }
                    delay(2000)
                }
            }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun refreshApps() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.apps() }
                .onSuccess { _apps.value = it }
                .onFailure { _error.value = "App list unavailable" }
        }
    }
}
