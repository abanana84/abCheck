package com.abanana.abattery.presentation.device

import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.abanana.abattery.data.device.*
import com.abanana.abattery.presentation.locale.labelRes

@Composable
internal fun ReferenceDashboard(
    snapshot: DeviceSnapshot,
    apps: List<InstalledApp>,
    percent: Int?,
    temperature: Float?,
    navigate: (String) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val metrics = LocalContext.current.resources.displayMetrics
    val wifiFields =
        snapshot.pages["Network"]
            .orEmpty()
            .firstOrNull { it.title == "Wi-Fi" }
            ?.fields
            .orEmpty()
            .associate { it.label to it.value }
    val display =
        LocalContext.current.getSystemService(android.view.WindowManager::class.java).defaultDisplay
    LazyColumn(
        contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Surface(
                onClick = { navigate("CPU analysis") },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Box(
                    Modifier.fillMaxWidth()
                        .height(maxOf(120, 32 + ((snapshot.cores.size + 1) / 2) * 36).dp)
                ) {
                    CpuFrequencyArea(snapshot)
                    DeviceText(
                        "CPU status",
                        Modifier.padding(12.dp),
                        color = accent,
                        fontSize = 12.sp,
                    )
                    Column(
                        Modifier.fillMaxSize().padding(top = 32.dp, start = 88.dp, end = 26.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        snapshot.cores.chunked(2).forEach { cores ->
                            Row(
                                Modifier.fillMaxWidth().weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                cores.forEach { core ->
                                    Column(Modifier.weight(1f)) {
                                        DeviceText("Core ${core.id}", fontSize = 10.sp)
                                        DeviceText(
                                            core.status ?: formatCpuFrequency(core.currentKhz),
                                            fontSize = if (core.status == null) 22.sp else 12.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Column(
                Modifier.padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DashboardCell(
                        "Battery",
                        Modifier.weight(1f),
                        RoundedCornerShape(topStart = 18.dp),
                        { navigate("Battery") },
                    ) {
                        Icon(Icons.Default.BatteryFull, null, Modifier.size(40.dp), tint = accent)
                        Column {
                            DeviceText(
                                "${percent?.let { "$it%" } ?: "N/A"}  ${temperature?.let { "%.0f°C".format(it) } ?: ""}",
                                color = accent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            DeviceText(
                                snapshot.pages["Battery"]
                                    ?.flatMap { it.fields }
                                    ?.firstOrNull { it.label == "Charge status" }
                                    ?.value
                                    ?.replace('_', ' ')
                                    ?.lowercase()
                                    ?.replaceFirstChar { it.uppercase() } ?: "Unavailable",
                                fontSize = 12.sp,
                            )
                        }
                    }
                    DashboardCell(
                        "Network",
                        Modifier.weight(1f),
                        RoundedCornerShape(topEnd = 18.dp),
                        { navigate("Network") },
                    ) {
                        Icon(
                            Icons.Default.SignalCellularAlt,
                            null,
                            Modifier.size(40.dp),
                            tint = accent,
                        )
                        Column {
                            DeviceText(
                                snapshot.network,
                                color = accent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            DeviceText(
                                wifiFields["Link speed"]
                                    ?: "${formatBytes(snapshot.received)} received",
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            DeviceText(
                                wifiFields["Signal"] ?: "${formatBytes(snapshot.sent)} sent",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DashboardCell(
                        "Apps",
                        Modifier.weight(1f),
                        RoundedCornerShape(2.dp),
                        { navigate("Apps") },
                        79,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            DeviceText(
                                apps.size.toString(),
                                color = accent,
                                fontSize = 27.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            DeviceText("Visible", color = accent, fontSize = 9.sp)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            DeviceText(
                                "${apps.count { !it.system }} User",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            DeviceText(
                                "${apps.count { it.system }} System",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    DashboardCell(
                        "Display",
                        Modifier.weight(1f),
                        RoundedCornerShape(2.dp),
                        { navigate("Hardware") },
                        79,
                    ) {
                        Icon(Icons.Default.Smartphone, null, Modifier.size(32.dp), tint = accent)
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            DeviceText(
                                Build.HARDWARE,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            DeviceText(
                                "${metrics.heightPixels} × ${metrics.widthPixels}  ${display.refreshRate.toInt()}Hz",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DashboardUsage(
                        "RAM",
                        snapshot.ramUsed,
                        snapshot.ramTotal,
                        Modifier.weight(1f),
                        RoundedCornerShape(bottomStart = 18.dp),
                    ) {
                        navigate("Monitors")
                    }
                    DashboardUsage(
                        "Storage",
                        snapshot.storageUsed,
                        snapshot.storageTotal,
                        Modifier.weight(1f),
                        RoundedCornerShape(bottomEnd = 18.dp),
                    ) {
                        navigate("Hardware")
                    }
                }
            }
        }
        item {
            Column(
                Modifier.padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf(
                        listOf("Tests", "Tools", "Widgets"),
                        listOf("Monitors", "Benchmarks", "Battery monitor"),
                    )
                    .forEachIndexed { r, names ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            names.forEachIndexed { c, name ->
                                Surface(
                                    onClick = { navigate(name) },
                                    modifier = Modifier.weight(1f).height(62.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    shape =
                                        RoundedCornerShape(
                                            topStart = if (r == 0 && c == 0) 18.dp else 2.dp,
                                            topEnd = if (r == 0 && c == 2) 18.dp else 2.dp,
                                            bottomStart = if (r == 1 && c == 0) 18.dp else 2.dp,
                                            bottomEnd = if (r == 1 && c == 2) 18.dp else 2.dp,
                                        ),
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        Icon(
                                            referenceIcon(name),
                                            null,
                                            Modifier.size(24.dp),
                                            tint = accent,
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        DeviceText(name, fontSize = 12.sp, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
            }
        }
        item {
            Surface(
                onClick = { navigate("System") },
                modifier = Modifier.padding(horizontal = 8.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(
                        Icons.Default.PhoneAndroid,
                        null,
                        Modifier.padding(top = 8.dp).size(60.dp),
                        tint = accent,
                    )
                    Column(Modifier.weight(1f)) {
                        DeviceText(
                            "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL.replace('_', ' ')}",
                            color = accent,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(24.dp))
                        DeviceText(
                            "Android ${Build.VERSION.RELEASE}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        DeviceText(
                            "Uptime: ${SystemClock.elapsedRealtime() / 60000}m",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        DeviceText(
                            "API ${Build.VERSION.SDK_INT} · ${Build.SUPPORTED_ABIS.firstOrNull().orEmpty()}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CpuFrequencyArea(snapshot: DeviceSnapshot) {
    val accent = MaterialTheme.colorScheme.primary
    val history = remember { mutableStateListOf<Float?>() }
    LaunchedEffect(snapshot.sampledAt) {
        val frequencies =
            snapshot.cores.mapNotNull { core ->
                core.currentKhz?.let { current ->
                    core.maxKhz?.takeIf { it > 0 }?.let { current.toFloat() / it }
                }
            }
        history.add(frequencies.takeIf { it.isNotEmpty() }?.average()?.toFloat()?.coerceIn(0f, 1f))
        if (history.size > 60) history.removeAt(0)
    }
    Canvas(Modifier.fillMaxSize()) {
        // Missing kernel frequency data leaves the plot empty instead of inventing readings.
        val samples = history.toList()
        if (samples.size < 2) return@Canvas
        val path = Path()
        var drawing = false
        samples.forEachIndexed { i, value ->
            val x = size.width * i / 59f
            if (value == null) {
                drawing = false
            } else {
                val y = size.height * (1 - value * 0.85f)
                if (!drawing) {
                    path.moveTo(x, size.height)
                    path.lineTo(x, y)
                    drawing = true
                } else path.lineTo(x, y)
                if (i == samples.lastIndex || samples.getOrNull(i + 1) == null) {
                    path.lineTo(x, size.height)
                    path.close()
                }
            }
        }
        drawPath(
            path,
            Brush.verticalGradient(listOf(accent.copy(alpha = .5f), accent.copy(alpha = .2f))),
        )
    }
}

@Composable
private fun DashboardCell(
    title: String,
    modifier: Modifier,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
    height: Int = 91,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height((height * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 7.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DeviceText(title, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                Icon(
                    Icons.Default.MoreVert,
                    null,
                    Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .6f),
                )
            }
            Row(
                Modifier.weight(1f).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun DashboardUsage(
    title: String,
    used: Long,
    total: Long,
    modifier: Modifier,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
) {
    DashboardCell(title, modifier, shape, onClick, 93) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { usedFraction(used, total) },
                modifier = Modifier.size(48.dp),
                strokeWidth = 8.dp,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = .5f),
            )
            DeviceText(
                "${(usedFraction(used, total) * 100).toInt()}%",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            DeviceText(
                "${formatBytes(used)} used",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            DeviceText(
                "${formatBytes(total)} total",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

internal fun referenceIcon(name: String): ImageVector =
    when {
        name == "Root check" -> Icons.Default.Security
        name == "Boot integrity" -> Icons.Default.VerifiedUser
        name == "Permissions explorer" -> Icons.Default.AdminPanelSettings
        name == "Screen time" -> Icons.Default.Timelapse
        name == "Task manager" -> Icons.Default.AccountTree
        name == "System graphs" -> Icons.Default.ShowChart
        name == "Logcat" -> Icons.Default.Terminal
        name == "Shizuku diagnostics" -> Icons.Default.SettingsEthernet
        name == "Temperatures" -> Icons.Default.Thermostat
        name == "Storage health" -> Icons.Default.Storage
        name == "OpenGL info" || name == "GPU times" -> Icons.Default.DeveloperBoard
        name == "USB" -> Icons.Default.Usb
        name == "Device report" -> Icons.Default.Description
        name == "Buttons" -> Icons.Default.Gamepad
        name == "Vibration" -> Icons.Default.Vibration
        name == "Multitouch" -> Icons.Default.TouchApp
        name == "Biometric scanner" -> Icons.Default.Fingerprint
        name == "Tests" -> Icons.Default.Checklist
        name == "Tools" -> Icons.Default.Handyman
        name == "Widgets" -> Icons.Default.Widgets
        name == "Monitors" -> Icons.Default.MonitorHeart
        name == "Benchmarks" -> Icons.Default.BarChart
        name.contains("Battery", true) || name == "Charging" -> Icons.Default.Bolt
        name.contains("CPU", true) || name.contains("Root", true) -> Icons.Default.Memory
        name.contains("Wi-Fi", true) || name.contains("Network", true) -> Icons.Default.Wifi
        name.contains("Bluetooth", true) -> Icons.Default.Bluetooth
        name.contains("GPS", true) -> Icons.Default.LocationOn
        name.contains("Camera", true) -> Icons.Default.CameraAlt
        name.contains("Display", true) || name.contains("light", true) -> Icons.Default.Brightness6
        name.contains("speaker", true) || name.contains("head", true) -> Icons.Default.VolumeUp
        name.contains("microphone", true) -> Icons.Default.Mic
        name.contains("sensor", true) -> Icons.Default.Sensors
        else -> Icons.Default.Tune
    }

@Composable
internal fun ReferenceActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Icon(
                referenceIcon(title),
                null,
                Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f)) {
                DeviceText(title, fontSize = 16.sp)
                if (subtitle.isNotBlank() && subtitle !in listOf("Open test", "Open tool"))
                    DeviceText(
                        subtitle,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }
            Icon(
                Icons.Default.ChevronRight,
                null,
                Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ReferenceSections(sections: List<DeviceSection>, header: @Composable () -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val ordered =
            if (sections.firstOrNull()?.title == "Processor") {
                sections.sortedBy {
                    when (it.title) {
                        "Processor" -> 0
                        "OpenGL ES" -> 1
                        "Graphics" -> 2
                        "Display" -> 3
                        "Memory and storage" -> 4
                        else -> 5
                    }
                }
            } else if (sections.firstOrNull()?.title == "Connection") {
                val connection = sections.first()
                listOf(
                    connection.copy(fields = connection.fields.filter { it.label == "Transport" })
                ) +
                    sections.drop(1).take(1) +
                    connection.copy(
                        title = "IP configuration",
                        fields = connection.fields.filter { it.label != "Transport" },
                    ) +
                    sections.drop(2)
            } else sections
        items(ordered) { section ->
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(13.dp)) {
                    SectionHeading(
                        when (section.title) {
                            "Android" -> "Operating System"
                            "OpenGL ES" ->
                                if (sections.any { it.title == "Processor" }) "GPU"
                                else section.title
                            else -> section.title
                        }
                    )
                    Spacer(Modifier.height(18.dp))
                    if (section.title == "Processor") ProcessorLayout(section)
                    else {
                        if (
                            section.title == "OpenGL ES" && sections.any { it.title == "Processor" }
                        ) {
                            ReferenceHero(
                                Icons.Default.Memory,
                                section.fields
                                    .firstOrNull { it.label == "Renderer" }
                                    ?.value
                                    .orEmpty(),
                            )
                        }
                        if (section.title == "Connection") {
                            ReferenceHero(
                                Icons.Default.SignalCellularAlt,
                                section.fields
                                    .firstOrNull { it.label == "Transport" }
                                    ?.value
                                    .orEmpty(),
                            )
                        }
                        if (section.title == "Device" || section.title == "Android") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Icon(
                                    if (section.title == "Device") Icons.Default.PhoneAndroid
                                    else Icons.Default.Android,
                                    null,
                                    Modifier.size(72.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                DeviceText(
                                    if (section.title == "Device")
                                        "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL.replace('_', ' ')}"
                                    else "Android ${Build.VERSION.RELEASE}",
                                    Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            Spacer(Modifier.height(26.dp))
                        }
                        SelectionContainer {
                            Column {
                                if (
                                    section.title in
                                        listOf("Device", "Display", "Memory and storage")
                                )
                                    FieldGrid(section.fields)
                                else FieldRows(section.fields)
                            }
                        }
                    }
                }
            }
        }
        item { header() }
    }
}

@Composable
private fun ProcessorLayout(section: DeviceSection) {
    val fields = section.fields.associate { it.label to it.value }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        DeviceText("Processor", color = MaterialTheme.colorScheme.onSurfaceVariant)
        DeviceText(fields["SoC model"].orEmpty(), fontWeight = FontWeight.Bold)
    }
    Row(Modifier.padding(vertical = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
                "${fields["Cores"].orEmpty()} cores",
                if (Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()) "64-bit" else "32-bit",
            )
            .forEach { label ->
                Surface(
                    shape = RoundedCornerShape(50),
                    border =
                        androidx.compose.foundation.BorderStroke(
                            0.5.dp,
                            MaterialTheme.colorScheme.primary,
                        ),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    DeviceText(
                        label,
                        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .05f),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DeviceText(
                "CPU Configuration",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
            DeviceText(
                "${fields["Cores"].orEmpty()} cores · ${fields["Architecture"].orEmpty()}",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            DeviceText(
                "${deviceString("SoC manufacturer")}: ${fields["SoC manufacturer"].orEmpty()}",
                fontSize = 13.sp,
            )
        }
    }
    Spacer(Modifier.height(20.dp))
    FieldGrid(section.fields.filter { it.label !in listOf("Cores", "SoC model") })
}

@Composable
private fun FieldGrid(fields: List<DeviceField>) {
    fields.chunked(2).forEach { row ->
        Row(
            Modifier.fillMaxWidth().padding(bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            row.forEach { field ->
                Column(Modifier.weight(1f)) {
                    DeviceText(
                        field.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                    )
                    DeviceText(field.value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
internal fun deviceSearchMatches(field: DeviceField, query: String): Boolean =
    field.label.contains(query, true) ||
        field.value.contains(query, true) ||
        deviceString(field.label).contains(query, true) ||
        deviceString(field.value).contains(query, true)

@Composable
private fun FieldRows(fields: List<DeviceField>) {
    fields.forEach { field ->
        Row(
            Modifier.fillMaxWidth().padding(bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DeviceText(
                field.label,
                Modifier.weight(.38f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
            )
            DeviceText(
                when (field.value) {
                    "true" -> "Yes"
                    "false" -> "No"
                    "GOOD" -> "Good"
                    else -> field.value
                },
                Modifier.weight(.62f),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
internal fun ReferenceBattery(
    info: com.abanana.abattery.domain.model.BatteryInfo?,
    details: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val sections = DeviceSnapshot().withBattery(info).pages["Battery"].orEmpty()
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    SectionHeading("Battery")
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Icon(Icons.Default.BatteryFull, null, Modifier.size(78.dp), tint = accent)
                        Column {
                            DeviceText(
                                info?.let { "${it.percent}%" } ?: "N/A",
                                color = accent,
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            DeviceText(
                                info?.let {
                                    androidx.compose.ui.res.stringResource(
                                        it.chargeStatus.labelRes()
                                    )
                                } ?: "Unavailable",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            DeviceText(
                                info?.technology ?: "Unavailable",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                            )
                        }
                    }
                    FieldRows(
                        sections
                            .flatMap { it.fields }
                            .filter { it.label !in listOf("Level", "Charge status", "Technology") }
                    )
                    FilledTonalButton(onClick = details) { DeviceText("Battery details") }
                }
            }
        }
    }
}

@Composable
internal fun AppListRow(app: InstalledApp, last: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val icon =
        remember(app.packageName) {
            runCatching {
                    context.packageManager
                        .getApplicationIcon(app.packageName)
                        .toBitmap(96, 96)
                        .asImageBitmap()
                }
                .getOrNull()
        }
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape =
            RoundedCornerShape(
                bottomStart = if (last) 18.dp else 0.dp,
                bottomEnd = if (last) 18.dp else 0.dp,
            ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (icon != null) androidx.compose.foundation.Image(icon, null, Modifier.size(42.dp))
            else
                Icon(
                    Icons.Default.Android,
                    null,
                    Modifier.size(42.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                DeviceText(app.label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                DeviceText(
                    app.packageName,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ReferenceHero(icon: ImageVector, title: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Icon(
            icon,
            null,
            Modifier.size(88.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = .75f),
        )
        DeviceText(
            title,
            Modifier.weight(1f),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
    Spacer(Modifier.height(16.dp))
}
