package com.abanana.abattery.data.device

import java.util.Locale

const val UNAVAILABLE = "Unavailable"

data class DeviceField(val label: String, val value: String)

data class DeviceSection(val title: String, val fields: List<DeviceField>)

data class DeviceSnapshot(
    val pages: Map<String, List<DeviceSection>> = emptyMap(),
    val cores: List<CoreInfo> = emptyList(),
    val ramUsed: Long = 0,
    val ramTotal: Long = 0,
    val storageUsed: Long = 0,
    val storageTotal: Long = 0,
    val network: String = UNAVAILABLE,
    val received: Long = 0,
    val sent: Long = 0,
    val sampledAt: Long = 0,
)

data class CoreInfo(
    val id: Int,
    val currentKhz: Long?,
    val minKhz: Long?,
    val maxKhz: Long?,
    val status: String? = null,
)

fun formatCpuFrequency(khz: Long?): String =
    when {
        khz == null -> UNAVAILABLE
        khz < 1000 -> "$khz kHz"
        else -> "${khz / 1000} MHz"
    }

data class InstalledApp(
    val label: String,
    val packageName: String,
    val version: String,
    val system: Boolean,
    val targetSdk: Int,
    val minSdk: Int,
    val permissions: List<Pair<String, Boolean>>,
    val installed: Long,
    val updated: Long,
    val sourcePath: String,
    val enabled: Boolean,
)

fun formatBytes(bytes: Long): String {
    if (bytes < 0) return UNAVAILABLE
    val units = listOf("B", "KiB", "MiB", "GiB", "TiB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return String.format(Locale.US, "%.1f %s", value, units[index])
}

fun usedFraction(used: Long, total: Long): Float =
    if (total <= 0) 0f else (used.toDouble() / total).coerceIn(0.0, 1.0).toFloat()

fun counterRate(previous: Long, current: Long, elapsedMs: Long): Long? =
    if (previous < 0 || current < previous || elapsedMs <= 0) null
    else ((current - previous).toDouble() * 1000 / elapsedMs).toLong()
