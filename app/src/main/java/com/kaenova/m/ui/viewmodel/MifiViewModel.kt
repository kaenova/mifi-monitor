package com.kaenova.m.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaenova.m.data.api.MifiApiClient
import com.kaenova.m.data.model.MifiMetrics
import com.kaenova.m.data.repository.MifiRepository
import com.kaenova.m.service.MifiMonitorService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MifiViewModel(application: Application) : AndroidViewModel(application) {

    private val apiClient = MifiApiClient()
    private val context = application.applicationContext

    val metrics: StateFlow<MifiMetrics> = MifiRepository.metrics

    private val _isServiceRunning = mutableStateOf(false)
    val isServiceRunning: State<Boolean> = _isServiceRunning

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val _isManuallyStopped = mutableStateOf(false)
    
    private var autoRefreshJob: Job? = null

    init {
        loadMetrics()
    }

    fun loadMetrics() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = apiClient.getMifiMetrics()
                result.onSuccess { metrics ->
                    MifiRepository.updateMetrics(metrics)
                }.onFailure { error ->
                    val errorMetrics = MifiMetrics(
                        isConnected = false,
                        error = "Failed to load metrics: ${error.message}"
                    )
                    MifiRepository.updateMetrics(errorMetrics)
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun startService() {
        // Cancel autoRefresh when service takes over
        autoRefreshJob?.cancel()
        autoRefreshJob = null
        
        viewModelScope.launch {
            _isServiceRunning.value = true
            _isManuallyStopped.value = false

            val intent = Intent(context, MifiMonitorService::class.java).apply {
                action = MifiMonitorService.ACTION_START
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    fun stopService() {
        viewModelScope.launch {
            _isServiceRunning.value = false
            _isManuallyStopped.value = true

            val intent = Intent(context, MifiMonitorService::class.java).apply {
                action = MifiMonitorService.ACTION_STOP
            }
            context.startService(intent)
        }
    }

    fun autoRefresh() {
        // Cancel any existing autoRefresh job
        autoRefreshJob?.cancel()
        
        autoRefreshJob = viewModelScope.launch {
            while (!_isManuallyStopped.value) {
                delay(1000)
                loadMetrics()
            }
        }
    }
}
