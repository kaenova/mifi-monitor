package com.kaenova.m.data.repository

import com.kaenova.m.data.model.MifiMetrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MifiRepository {
    private val _metrics = MutableStateFlow(MifiMetrics())
    val metrics: StateFlow<MifiMetrics> = _metrics.asStateFlow()

    fun updateMetrics(newMetrics: MifiMetrics) {
        _metrics.value = newMetrics
    }
}
