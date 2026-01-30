package com.kaenova.m.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaenova.m.data.model.MifiMetrics
import com.kaenova.m.ui.components.BatteryIndicator
import com.kaenova.m.ui.components.MetricCard
import com.kaenova.m.ui.components.MetricRow
import com.kaenova.m.ui.components.SignalStrengthIndicator
import com.kaenova.m.ui.components.StatusBadge
import com.kaenova.m.ui.viewmodel.MifiViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: MifiViewModel) {
    val metrics = viewModel.metrics.collectAsState().value
    val isLoading = viewModel.isLoading.value
    val isServiceRunning = viewModel.isServiceRunning.value

    LaunchedEffect(isServiceRunning) {
        if (!isServiceRunning) {
            viewModel.autoRefresh()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MiFi Monitor") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading && metrics.signalStrength == "N/A") {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Status Section
                    MetricCard(title = "Status") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusBadge(metrics.isConnected)
                            if (metrics.isConnected) {
                                Text(
                                    text = metrics.operator,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontSize = 12.sp
                                )
                            } else if (metrics.error != null) {
                                Text(
                                    text = metrics.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    if (metrics.isConnected) {
                        // Signal Card
                        MetricCard(title = "Signal Strength") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                MetricRow("RSSI", metrics.signalStrength)
                                MetricRow("Quality", "${metrics.signalQuality}/5")
                                SignalStrengthIndicator(metrics.signalQuality)
                                MetricRow("Network Mode", metrics.networkMode)
                            }
                        }

                        // Battery Card
                        MetricCard(title = "Battery") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                BatteryIndicator(
                                    metrics.batteryPercent,
                                    metrics.batteryCharging
                                )
                                MetricRow("Capacity", "${metrics.batteryPercent}%")
                            }
                        }

                        // Speed Card
                        MetricCard(title = "Speed") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                MetricRow("Upload", metrics.uploadSpeed)
                                MetricRow("Download", metrics.downloadSpeed)
                            }
                        }

                        // Data Usage Card
                        MetricCard(title = "Data Usage") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                MetricRow("Sent", metrics.sentData)
                                MetricRow("Received", metrics.receivedData)
                            }
                        }

                        // Device Info Card
                        MetricCard(title = "Connected Devices") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                MetricRow("Devices", "${metrics.connectedDevices}")
                                MetricRow("Runtime", metrics.runtime)
                            }
                        }

                        // Device Details Card
                        MetricCard(title = "Device Details") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                MetricRow("SSID", metrics.ssid)
                                Divider(modifier = Modifier.padding(vertical = 4.dp))
                                MetricRow("IMEI", metrics.imei)
                                MetricRow("MAC", metrics.mac)
                                MetricRow("Phone", metrics.phoneNumber)
                                MetricRow("Version", metrics.softwareVersion)
                            }
                        }
                    }

                    // Control Buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.loadMetrics() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Refresh")
                        }

                        Button(
                            onClick = {
                                if (isServiceRunning) {
                                    viewModel.stopService()
                                } else {
                                    viewModel.startService()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isServiceRunning) "Stop Service" else "Start Service")
                        }
                    }
                }
            }
        }
    }
}
