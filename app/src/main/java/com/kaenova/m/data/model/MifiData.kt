package com.kaenova.m.data.model

data class HomepageInfo(
    val network_name: String,
    val mac: String,
    val imei: String,
    val sw_version: String,
    val msisdn: String,
    val lan_ip: String,
    val ssid: String
)

data class StatusInfo(
    val rssi: String,
    val signal_quality: String,
    val sys_mode: String,
    val wifi_clients_num: String,
    val run_seconds: String,
    val battery_percent: String,
    val battery_charging: String,
    val tx_byte_all: String,
    val rx_byte_all: String,
    val tx_speed: String,
    val rx_speed: String
)

data class MifiMetrics(
    val isConnected: Boolean = false,
    val signalStrength: String = "N/A",
    val signalQuality: Int = 0,
    val networkMode: String = "N/A",
    val operator: String = "N/A",
    val connectedDevices: Int = 0,
    val runtime: String = "N/A",
    val batteryPercent: Int = 0,
    val batteryCharging: Boolean = false,
    val sentData: String = "0.0 GB",
    val receivedData: String = "0.0 GB",
    val uploadSpeed: String = "0 KB/s",
    val downloadSpeed: String = "0 KB/s",
    val ssid: String = "N/A",
    val imei: String = "N/A",
    val mac: String = "N/A",
    val phoneNumber: String = "N/A",
    val softwareVersion: String = "N/A",
    val lastUpdateTime: Long = 0L,
    val error: String? = null
)
