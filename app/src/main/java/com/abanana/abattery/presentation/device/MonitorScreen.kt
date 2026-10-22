package com.abanana.abattery.presentation.device

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.repeatOnLifecycle
import com.abanana.abattery.data.device.*
import com.abanana.abattery.diagnostics.MonitorService
import com.abanana.abattery.diagnostics.monitorMetrics
import java.io.File
import kotlinx.coroutines.*

@Composable
fun MonitorScreen(snapshot: DeviceSnapshot, temperature: Float?) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("monitor", 0) }
    var selectedMetrics by remember {
        mutableStateOf(
            preferences.getStringSet("metrics", setOf("CPU frequency", "RAM", "Battery"))!!.toSet()
        )
    }
    var ram by remember { mutableStateOf<List<Float>>(emptyList()) }
    var rx by remember { mutableStateOf<List<Float>>(emptyList()) }
    var tx by remember { mutableStateOf<List<Float>>(emptyList()) }
    var coreHistory by remember { mutableStateOf<List<Float>>(emptyList()) }
    var previous by remember { mutableStateOf<DeviceSnapshot?>(null) }
    LaunchedEffect(snapshot.sampledAt) {
        if (snapshot.sampledAt > 0) {
            ram = (ram + usedFraction(snapshot.ramUsed, snapshot.ramTotal) * 100).takeLast(90)
            coreHistory =
                (coreHistory +
                        (snapshot.cores
                            .mapNotNull { it.currentKhz }
                            .average()
                            .takeIf { it.isFinite() }
                            ?.div(1000)
                            ?.toFloat() ?: Float.NaN))
                    .takeLast(90)
            previous?.let { old ->
                counterRate(old.received, snapshot.received, snapshot.sampledAt - old.sampledAt)
                    ?.let { rx = (rx + it.toFloat()).takeLast(90) }
                counterRate(old.sent, snapshot.sent, snapshot.sampledAt - old.sampledAt)?.let {
                    tx = (tx + it.toFloat()).takeLast(90)
                }
            }
            previous = snapshot
        }
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { SectionHeading("Live system graphs") }
        item {
            DeviceText(
                "Samples every 2 seconds while visible. Frequencies are not CPU utilization. Missing readings are omitted."
            )
        }
        item {
            GraphCard(
                "RAM · ${(usedFraction(snapshot.ramUsed,snapshot.ramTotal)*100).toInt()}%",
                ram,
            )
        }
        item { GraphCard("Mean CPU frequency · MHz", coreHistory) }
        item {
            GraphCard(
                "Receive · ${rx.lastOrNull()?.toLong()?.let { formatBytes(it) } ?: "—"}/s",
                rx,
            )
        }
        item {
            GraphCard("Send · ${tx.lastOrNull()?.toLong()?.let { formatBytes(it) } ?: "—"}/s", tx)
        }
        temperature?.let { item { DeviceText("Battery temperature: $it °C") } }
        item { SectionHeading("Floating monitor") }
        item {
            DeviceText(
                "Show RAM, battery and CPU frequency above other apps. A persistent notification includes a Stop action."
            )
        }
        item {
            Column {
                monitorMetrics.forEach { name ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(
                            name in selectedMetrics,
                            { checked ->
                                selectedMetrics =
                                    if (checked) selectedMetrics + name else selectedMetrics - name
                                preferences.edit().putStringSet("metrics", selectedMetrics).apply()
                            },
                        )
                        DeviceText(name)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (!Settings.canDrawOverlays(context))
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}"),
                                )
                            )
                        else {
                            val intent = Intent(context, MonitorService::class.java)
                            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
                            else context.startService(intent)
                        }
                    }
                ) {
                    DeviceText("Start overlay")
                }
                OutlinedButton(
                    onClick = { context.stopService(Intent(context, MonitorService::class.java)) }
                ) {
                    DeviceText("Stop")
                }
            }
        }
    }
}

@Composable
fun GraphCard(title: String, values: List<Float>) {
    val color = MaterialTheme.colorScheme.primary
    Card {
        Column(Modifier.padding(16.dp)) {
            SectionHeading(title)
            Canvas(Modifier.fillMaxWidth().height(100.dp).padding(top = 12.dp)) {
                val max = values.filter { it.isFinite() }.maxOrNull()?.coerceAtLeast(1f) ?: 1f
                val path = Path()
                var connected = false
                values.forEachIndexed { index, v ->
                    if (v.isFinite()) {
                        val x = index.toFloat() / (values.size - 1).coerceAtLeast(1) * size.width
                        val y = size.height - (v / max).coerceIn(0f, 1f) * size.height
                        if (connected) path.lineTo(x, y) else path.moveTo(x, y)
                        connected = true
                    } else connected = false
                }
                drawLine(
                    color.copy(alpha = .2f),
                    Offset(0f, size.height),
                    Offset(size.width, size.height),
                )
                drawPath(path, color, style = Stroke(3f))
            }
        }
    }
}

@Composable
fun TemperatureScreen() {
    val context = LocalContext.current
    var sections by remember { mutableStateOf<List<DeviceSection>>(emptyList()) }
    val activeLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(activeLifecycle) {
        activeLifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (isActive) {
                sections =
                    withContext(Dispatchers.IO) {
                        val zones =
                            File("/sys/class/thermal")
                                .listFiles()
                                .orEmpty()
                                .filter { it.name.startsWith("thermal_zone") }
                                .mapNotNull { zone ->
                                    runCatching {
                                            val raw = File(zone, "temp").readText().trim().toFloat()
                                            val c =
                                                if (kotlin.math.abs(raw) > 200) raw / 1000 else raw
                                            DeviceField(
                                                File(zone, "type").readText().trim(),
                                                "$c °C",
                                            )
                                        }
                                        .getOrNull()
                                }
                        val battery =
                            context
                                .registerReceiver(
                                    null,
                                    android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                                )
                                ?.getIntExtra(
                                    android.os.BatteryManager.EXTRA_TEMPERATURE,
                                    Int.MIN_VALUE,
                                )
                        val fields =
                            zones +
                                listOfNotNull(
                                    battery
                                        ?.takeUnless { it == Int.MIN_VALUE }
                                        ?.let { DeviceField("Battery", "${it/10f} °C") }
                                )
                        listOf(
                            DeviceSection(
                                "Temperature sensors",
                                fields.ifEmpty {
                                    listOf(
                                        DeviceField("Status", "Thermal nodes restricted by Android")
                                    )
                                },
                            ),
                            DeviceSection(
                                "Android thermal status",
                                listOf(
                                    DeviceField(
                                        "Throttling",
                                        if (Build.VERSION.SDK_INT >= 29)
                                            DeviceRepository.thermalStatus(
                                                context
                                                    .getSystemService(PowerManager::class.java)
                                                    .currentThermalStatus
                                            )
                                        else UNAVAILABLE,
                                    )
                                ),
                            ),
                        )
                    }
                delay(2000)
            }
        }
    }
    SectionList(sections)
}
