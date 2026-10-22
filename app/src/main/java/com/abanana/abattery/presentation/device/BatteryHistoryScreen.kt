package com.abanana.abattery.presentation.device

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.repeatOnLifecycle
import com.abanana.abattery.diagnostics.*
import java.util.Date
import kotlinx.coroutines.*

@Composable
fun BatteryHistoryScreen() {
    val context = LocalContext.current
    var samples by remember { mutableStateOf<List<BatterySample>>(emptyList()) }
    var message by remember {
        mutableStateOf(
            "Record one sample per minute while the user-started monitoring service runs. Up to 1,440 samples stay on this device."
        )
    }
    val activeLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(activeLifecycle) {
        activeLifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (isActive) {
                samples = withContext(Dispatchers.IO) { BatteryHistory.load(context) }
                delay(2000)
            }
        }
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionHeading("Battery history") }
        item { DeviceText(message) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        runCatching {
                                context.startForegroundService(
                                    Intent(context, MonitorService::class.java).setAction("BATTERY")
                                )
                                message =
                                    "Recording requested. Use Stop to end background recording."
                            }
                            .onFailure { message = "Could not start: ${it.message}" }
                    }
                ) {
                    DeviceText("Start recording")
                }
                OutlinedButton(
                    onClick = {
                        context.stopService(Intent(context, MonitorService::class.java))
                        message = "Recording stopped"
                    }
                ) {
                    DeviceText("Stop")
                }
            }
        }
        item { GraphCard("Charge level · %", samples.map { it.level.toFloat() }) }
        item { GraphCard("Temperature · °C", samples.map { it.temperature }) }
        item {
            GraphCard(
                "Current · mA (magnitude)",
                samples.map {
                    it.currentUa?.let { v -> kotlin.math.abs(v.toFloat()) / 1000 } ?: Float.NaN
                },
            )
        }
        item {
            TextButton(
                onClick = {
                    val text =
                        "time,level_percent,temperature_c,current_ua,plugged\n" +
                            samples.joinToString("\n") {
                                "${it.time},${it.level},${it.temperature},${it.currentUa ?: ""},${it.plugged}"
                            }
                    context
                        .getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText("Battery history CSV", text))
                    message = "Copied ${samples.size} samples as CSV"
                }
            ) {
                DeviceText("Copy history CSV")
            }
        }
        items(samples.takeLast(30).reversed()) { s ->
            DeviceText(
                "${Date(s.time)}\n${s.level}% · ${s.temperature} °C · ${s.currentUa?.let { "${it/1000f} mA" } ?: "N/A"} · ${if(s.plugged) "Plugged" else "Unplugged"}"
            )
        }
    }
}
