package com.abanana.abattery.presentation.device

import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.abanana.abattery.data.device.DeviceRepository
import com.abanana.abattery.diagnostics.*
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.*

@Composable
fun BenchmarkScreen() {
    val context = LocalContext.current
    val engine = remember { BenchmarkEngine(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("Ready to run") }
    var history by remember { mutableStateOf(engine.history()) }
    var iterations by remember { mutableIntStateOf(1) }
    var selected by remember { mutableStateOf(BenchmarkWorkload.entries.toSet()) }
    var cooldown by remember { mutableIntStateOf(5) }
    val power = context.getSystemService(PowerManager::class.java)
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer =
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) job?.cancel()
            }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            job?.cancel()
        }
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionHeading("Local performance benchmark") }
        item {
            DeviceText(
                "SHA-256 throughput, memory copy sequential storage I/O and a 512 × 512 offscreen OpenGL shader. Results use physical units and are only comparable with the same abCheck workload. OS caching affects disk reads. No online rankings are fabricated."
            )
        }
        item {
            DeviceText(
                "Power saving: ${power.isPowerSaveMode}\nThermal state: ${if (Build.VERSION.SDK_INT >= 29) DeviceRepository.thermalStatus(power.currentThermalStatus) else "Unavailable"}"
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 3, 5).forEach { count ->
                    FilterChip(
                        iterations == count,
                        { if (!running) iterations = count },
                        { DeviceText(if (count == 1) "1 run" else "$count stress runs") },
                        enabled = !running,
                    )
                }
            }
        }
        item { SectionHeading("Benchmark Labs") }
        item {
            Column {
                BenchmarkWorkload.entries.forEach { workload ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(
                            workload in selected,
                            { enabled ->
                                if (!running)
                                    selected =
                                        if (enabled) selected + workload else selected - workload
                            },
                            enabled = !running,
                        )
                        DeviceText(workload.name)
                    }
                }
                DeviceText("Cooldown: $cooldown seconds")
                Slider(
                    cooldown.toFloat(),
                    { cooldown = it.toInt() },
                    valueRange = 0f..30f,
                    steps = 29,
                    enabled = !running,
                )
            }
        }
        item {
            Button(
                onClick = {
                    running = true
                    job =
                        scope.launch {
                            try {
                                repeat(iterations) { index ->
                                    val result =
                                        withContext(Dispatchers.Default) {
                                            engine.run(selected) { stage ->
                                                scope.launch {
                                                    progress = "Run ${index+1}/$iterations · $stage"
                                                }
                                            }
                                        }
                                    engine.save(result)
                                    history = engine.history()
                                    if (index < iterations - 1) {
                                        progress = "Cooling down · $cooldown seconds"
                                        delay(cooldown * 1000L)
                                    }
                                }
                                progress = "Complete"
                            } catch (e: CancellationException) {
                                progress = "Cancelled"
                                throw e
                            } catch (e: Exception) {
                                progress = "Benchmark failed: ${e.message}"
                            } finally {
                                running = false
                            }
                        }
                },
                enabled = !running && selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                DeviceText("Run benchmark")
            }
        }
        item {
            DeviceText(progress)
            if (running) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                TextButton(onClick = { job?.cancel() }) { DeviceText("Cancel") }
            }
        }
        item { SectionHeading("Recent runs · ${history.size}") }
        items(history) { result ->
            Card {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DeviceText(Date(result.timestamp).toString())
                    fun measured(value: Double?, unit: String) =
                        value?.let { String.format(Locale.US, "%.2f %s", it, unit) }
                            ?: "Not run / unavailable"
                    DeviceText(
                        "CPU SHA-256: ${measured(result.cpu, "k hashes/s")}\nMemory: ${measured(result.memory, "MiB/s")}\nWrite: ${measured(result.write, "MiB/s")}\nRead (cached): ${measured(result.read, "MiB/s")}\nGPU offscreen: ${measured(result.gpuFps, "frames/s")}\nDuration: ${result.durationMs/1000.0} s"
                    )
                }
            }
        }
        if (history.isEmpty()) item { DeviceText("Finished runs will appear here.") }
    }
}
