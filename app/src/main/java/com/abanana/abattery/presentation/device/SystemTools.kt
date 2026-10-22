package com.abanana.abattery.presentation.device

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.abanana.abattery.data.device.*
import java.io.File
import kotlinx.coroutines.*

@Composable
fun RootScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember {
        mutableStateOf(
            "Not tested. The presence of root-related files alone does not prove that this app has root access."
        )
    }
    var running by remember { mutableStateOf(false) }
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeading("Root access check")
        DeviceText(result)
        DeviceText(
            "Testing may show a request in your device's root manager. The command only reads the process identity."
        )
        Button(
            enabled = !running,
            onClick = {
                running = true
                scope.launch {
                    result =
                        withContext(Dispatchers.IO) {
                            runCatching {
                                    val process =
                                        ProcessBuilder("su", "-c", "id")
                                            .redirectErrorStream(true)
                                            .start()
                                    try {
                                        if (
                                            !process.waitFor(
                                                10,
                                                java.util.concurrent.TimeUnit.SECONDS,
                                            )
                                        )
                                            "Root manager did not respond within 10 seconds"
                                        else {
                                            val output =
                                                process.inputStream
                                                    .bufferedReader()
                                                    .readText()
                                                    .take(1000)
                                            if (
                                                process.exitValue() == 0 && output.contains("uid=0")
                                            )
                                                "Root access granted\n$output"
                                            else "Root access not granted\n$output"
                                        }
                                    } finally {
                                        process.destroy()
                                    }
                                }
                                .getOrElse { "Root access unavailable: ${it.javaClass.simpleName}" }
                        }
                    running = false
                }
            },
        ) {
            DeviceText("Test root access")
        }
        DeviceText("Build tags: ${Build.TAGS}")
    }
}

@Composable
fun CpuTimesScreen(gpu: Boolean = false) {
    var sections by remember { mutableStateOf<List<DeviceSection>>(emptyList()) }
    LaunchedEffect(gpu) {
        sections =
            withContext(Dispatchers.IO) {
                val dirs =
                    if (gpu) listOf(File("/sys/class/kgsl/kgsl-3d0"), File("/sys/class/devfreq"))
                    else
                        File("/sys/devices/system/cpu").listFiles().orEmpty().filter {
                            it.name.matches(Regex("cpu\\d+"))
                        }
                dirs
                    .map { dir ->
                        val candidates =
                            if (gpu)
                                listOf(
                                    File(dir, "gpu_busy_percentage"),
                                    File(dir, "gpubusy"),
                                    File(dir, "devfreq/trans_stat"),
                                )
                            else listOf(File(dir, "cpufreq/stats/time_in_state"))
                        val fields =
                            candidates.mapNotNull { f ->
                                runCatching { DeviceField(f.name, f.readText().trim().take(12000)) }
                                    .getOrNull()
                            }
                        DeviceSection(
                            dir.name,
                            fields.ifEmpty {
                                listOf(
                                    DeviceField(
                                        "Access",
                                        "Frequency residency data is not exposed to this app. A supported kernel and privileged access may be required.",
                                    )
                                )
                            },
                        )
                    }
                    .ifEmpty {
                        listOf(
                            DeviceSection(
                                "Residency data",
                                listOf(DeviceField("Status", UNAVAILABLE)),
                            )
                        )
                    }
            }
    }
    SectionList(sections)
}

@Composable
fun StorageHealthScreen() {
    var sections by remember { mutableStateOf<List<DeviceSection>>(emptyList()) }
    LaunchedEffect(Unit) {
        sections =
            withContext(Dispatchers.IO) {
                val candidates =
                    listOf(
                        "/sys/class/mmc_host/mmc0/mmc0:0001/life_time",
                        "/sys/class/mmc_host/mmc0/mmc0:0001/pre_eol_info",
                        "/sys/class/scsi_host/host0/health_descriptor/life_time_estimation_a",
                        "/sys/class/scsi_host/host0/health_descriptor/life_time_estimation_b",
                        "/sys/class/scsi_host/host0/health_descriptor/pre_eol_info",
                    )
                listOf(
                    DeviceSection(
                        "Storage health · raw manufacturer values",
                        candidates
                            .mapNotNull { path ->
                                runCatching {
                                        DeviceField(
                                            path.substringAfterLast('/'),
                                            File(path).readText().trim(),
                                        )
                                    }
                                    .getOrNull()
                            }
                            .ifEmpty {
                                listOf(
                                    DeviceField(
                                        "Status",
                                        "Wear counters are not readable on this device. Free space is not a measurement of storage health.",
                                    )
                                )
                            },
                    )
                )
            }
    }
    SectionList(sections)
}

@Composable
fun IntegrityScreen() {
    var sections by remember { mutableStateOf<List<DeviceSection>>(emptyList()) }
    LaunchedEffect(Unit) {
        sections =
            withContext(Dispatchers.IO) {
                fun property(key: String): String =
                    runCatching {
                            val p = ProcessBuilder("getprop", key).start()
                            try {
                                p.inputStream.bufferedReader().readText().trim().ifBlank {
                                    UNAVAILABLE
                                }
                            } finally {
                                p.destroy()
                            }
                        }
                        .getOrDefault(UNAVAILABLE)
                listOf(
                    DeviceSection(
                        "Boot and identity signals",
                        listOf(
                            DeviceField(
                                "Verified boot state",
                                property("ro.boot.verifiedbootstate"),
                            ),
                            DeviceField("Flash locked", property("ro.boot.flash.locked")),
                            DeviceField("Device state", property("ro.boot.vbmeta.device_state")),
                            DeviceField("Build fingerprint", Build.FINGERPRINT),
                            DeviceField("Build tags", Build.TAGS),
                        ),
                    ),
                    DeviceSection(
                        "Play Integrity",
                        listOf(
                            DeviceField(
                                "Availability",
                                "Google's signed verdict requires abCheck's own Play Console / Cloud project and server-side verification. No Google verdict has been requested or inferred.",
                            )
                        ),
                    ),
                    DeviceSection(
                        "Hardware verification",
                        listOf(
                            DeviceField(
                                "Availability",
                                "Cross-device identity verification requires a trustworthy comparison database. Local build properties alone do not verify a device's authenticity.",
                            )
                        ),
                    ),
                )
            }
    }
    SectionList(sections)
}
