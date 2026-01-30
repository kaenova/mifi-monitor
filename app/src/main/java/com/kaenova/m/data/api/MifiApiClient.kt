package com.kaenova.m.data.api

import com.google.gson.Gson
import com.kaenova.m.data.model.HomepageInfo
import com.kaenova.m.data.model.MifiMetrics
import com.kaenova.m.data.model.StatusInfo
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class MifiApiClient(
    private val mifiIp: String = "192.168.50.1",
    private val username: String = "admin",
    private val password: String = "admin"
) {

    private val gson = Gson()
    private val okHttpClient = OkHttpClient.Builder()
        .authenticator(DigestAuthenticator(username, password))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun getMifiMetrics(): Result<MifiMetrics> = runCatching {
        try {
            val timestamp = System.currentTimeMillis()

            // Fetch homepage info
            val homepageUrl = "http://$mifiIp/xml_action.cgi?method=get&module=duster&file=json_homepage_info$timestamp"
            val homepageResponse = executeRequest(homepageUrl)
            val homepageInfo = gson.fromJson(homepageResponse, HomepageInfo::class.java)

            // Fetch status info
            val statusUrl = "http://$mifiIp/xml_action.cgi?method=get&module=duster&file=json_status_info$timestamp"
            val statusResponse = executeRequest(statusUrl)
            val statusInfo = gson.fromJson(statusResponse, StatusInfo::class.java)

            // Format and return metrics
            formatMetrics(homepageInfo, statusInfo)
        } catch (e: IOException) {
            throw MifiApiException("Network error: ${e.message}", e)
        } catch (e: Exception) {
            throw MifiApiException("Failed to fetch MiFi metrics: ${e.message}", e)
        }
    }

    private fun executeRequest(url: String): String {
        val request = Request.Builder()
            .url(url)
            .build()

        return okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw MifiApiException("HTTP ${response.code}: ${response.message}")
            }
            response.body?.string() ?: throw MifiApiException("Empty response body")
        }
    }

    private fun formatMetrics(homepage: HomepageInfo, status: StatusInfo): MifiMetrics {
        return try {
            // Parse SSID from hex
            val ssid = try {
                val hexBytes = homepage.ssid
                String(hexStringToByteArray(hexBytes), Charsets.UTF_16BE)
            } catch (e: Exception) {
                homepage.ssid
            }

            // Parse runtime
            val runtimeSeconds = status.run_seconds.toLongOrNull() ?: 0L
            val hours = runtimeSeconds / 3600
            val minutes = (runtimeSeconds % 3600) / 60
            val seconds = runtimeSeconds % 60
            val runtime = "${hours}h ${minutes}m ${seconds}s"

            // Parse data usage
            val sentBytes = (status.tx_byte_all.toLongOrNull() ?: 0L)
            val receivedBytes = status.rx_byte_all.toLongOrNull() ?: 0L
            val sentGb = sentBytes / (1024.0 * 1024.0 * 1024.0)
            val receivedGb = receivedBytes / (1024.0 * 1024.0 * 1024.0)

            // Parse network mode
            val sysMode = status.sys_mode.toIntOrNull() ?: 0
            val networkMode = if (sysMode == 17) "4G" else "Unknown"

            // Parse battery
            val batteryPercent = status.battery_percent.toIntOrNull() ?: 0
            val batteryCharging = status.battery_charging.toIntOrNull() == 1

            // Parse signal
            val signalQuality = status.signal_quality.toIntOrNull() ?: 0

            // Parse connected devices
            val connectedDevices = status.wifi_clients_num.toIntOrNull() ?: 0

            MifiMetrics(
                isConnected = true,
                signalStrength = "${status.rssi} dBm",
                signalQuality = signalQuality,
                networkMode = networkMode,
                operator = homepage.network_name,
                connectedDevices = connectedDevices,
                runtime = runtime,
                batteryPercent = batteryPercent,
                batteryCharging = batteryCharging,
                sentData = String.format("%.3f GB", sentGb),
                receivedData = String.format("%.3f GB", receivedGb),
                uploadSpeed = formatSpeed(status.tx_speed),
                downloadSpeed = formatSpeed(status.rx_speed),
                ssid = ssid,
                imei = homepage.imei,
                mac = homepage.mac,
                phoneNumber = homepage.msisdn,
                softwareVersion = homepage.sw_version,
                lastUpdateTime = System.currentTimeMillis(),
                error = null
            )
        } catch (e: Exception) {
            MifiMetrics(
                isConnected = false,
                error = "Parsing error: ${e.message}",
                lastUpdateTime = System.currentTimeMillis()
            )
        }
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((s[i].toString().toInt(16) shl 4) +
                    s[i + 1].toString().toInt(16)).toByte()
        }
        return data
    }

    private fun formatSpeed(kbPerSecond: String): String {
        val kb = (kbPerSecond.toDoubleOrNull() ?: 0.0) / (1024.0)
        return when {
            kb >= 1_048_576 -> String.format("%.1f GB/s", kb / 1_048_576.0)
            kb >= 1_024 -> String.format("%.1f MB/s", kb / 1_024.0)
            else -> String.format("%.1f KB/s", kb)
        }
    }
}

class MifiApiException(message: String, cause: Throwable? = null) : Exception(message, cause)
