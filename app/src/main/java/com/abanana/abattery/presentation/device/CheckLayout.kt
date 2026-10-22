package com.abanana.abattery.presentation.device

import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.abanana.abattery.data.device.*
import com.abanana.abattery.presentation.locale.labelRes
import com.abanana.abattery.ui.theme.ReadingStyle

// ---------------------------------------------------------------------------------------------
// Shared building blocks: one card style, one grouped-list style, one gauge style.
// ---------------------------------------------------------------------------------------------

internal val CardShape = RoundedCornerShape(20.dp)
internal val ScreenPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp)

@Composable
internal fun CheckCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surface,
    border: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val stroke =
        if (border) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
    if (onClick != null)
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = CardShape,
            color = color,
            border = stroke,
        ) {
            Column(content = content)
        }
    else
        Surface(modifier = modifier, shape = CardShape, color = color, border = stroke) {
            Column(content = content)
        }
}

/** Small uppercase-free section label that sits above a card or group. */
@Composable
internal fun SectionLabel(title: String, modifier: Modifier = Modifier) {
    DeviceText(
        title,
        modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
internal fun IconBadge(
    icon: ImageVector,
    size: Dp = 40.dp,
    tint: Color = MaterialTheme.colorScheme.primary,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
) {
    Box(
        Modifier.size(size).background(container, RoundedCornerShape(size * 0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(size * 0.55f), tint = tint)
    }
}

internal data class GroupEntry(
    val title: String,
    val subtitle: String = "",
    val icon: ImageVector? = null,
    val badge: String? = null,
    val badgeColor: Color? = null,
    val selected: Boolean? = null,
    val onClick: () -> Unit,
)

/** Settings-style grouped list: one card, rows separated by inset dividers. */
@Composable
internal fun ListGroup(title: String?, entries: List<GroupEntry>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (title != null) SectionLabel(title)
        CheckCard(Modifier.fillMaxWidth()) {
            entries.forEachIndexed { index, entry ->
                if (index > 0)
                    HorizontalDivider(
                        Modifier.padding(start = 68.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                GroupRow(entry)
            }
        }
    }
}

@Composable
private fun GroupRow(entry: GroupEntry) {
    Surface(onClick = entry.onClick, color = Color.Transparent) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconBadge(entry.icon ?: checkIcon(entry.title), size = 40.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                DeviceText(entry.title, style = MaterialTheme.typography.titleSmall)
                if (entry.subtitle.isNotBlank() && entry.subtitle !in listOf("Open test", "Open tool"))
                    DeviceText(
                        entry.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }
            if (entry.badge != null) StatusPill(entry.badge, entry.badgeColor)
            when (entry.selected) {
                null ->
                    Icon(
                        Icons.Default.ChevronRight,
                        null,
                        Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                else -> RadioButton(selected = entry.selected, onClick = null)
            }
        }
    }
}

@Composable
internal fun StatusPill(text: String, color: Color? = null) {
    val tint = color ?: MaterialTheme.colorScheme.primary
    Surface(shape = CircleShape, color = tint.copy(alpha = .14f)) {
        DeviceText(
            text,
            Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            maxLines = 1,
        )
    }
}

/** Circular gauge; a null fraction draws only the track so missing data is never invented. */
@Composable
internal fun RingGauge(
    fraction: Float?,
    size: Dp,
    stroke: Dp,
    color: Color = MaterialTheme.colorScheme.primary,
    content: @Composable BoxScope.() -> Unit,
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val width = stroke.toPx()
            val inset = width / 2
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - width, this.size.height - width)
            drawArc(track, 135f, 270f, false, Offset(inset, inset), arcSize, style = Stroke(width, cap = StrokeCap.Round))
            if (fraction != null)
                drawArc(
                    color,
                    135f,
                    270f * fraction.coerceIn(0f, 1f),
                    false,
                    Offset(inset, inset),
                    arcSize,
                    style = Stroke(width, cap = StrokeCap.Round),
                )
        }
        content()
    }
}

@Composable
internal fun levelColor(fraction: Float?, invert: Boolean = false): Color {
    if (fraction == null) return MaterialTheme.colorScheme.outline
    val load = if (invert) 1 - fraction else fraction
    return when {
        load >= .9f -> MaterialTheme.colorScheme.error
        load >= .75f -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }
}

internal fun formatUptime(): String {
    val minutes = SystemClock.elapsedRealtime() / 60000
    val days = minutes / 1440
    val hours = (minutes % 1440) / 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes % 60}m"
        else -> "${minutes}m"
    }
}

// ---------------------------------------------------------------------------------------------
// Dashboard
// ---------------------------------------------------------------------------------------------

@Composable
internal fun CheckDashboard(
    snapshot: DeviceSnapshot,
    apps: List<InstalledApp>,
    percent: Int?,
    temperature: Float?,
    navigate: (String) -> Unit,
) {
    val display =
        LocalContext.current.getSystemService(android.view.WindowManager::class.java).defaultDisplay
    val metrics = LocalContext.current.resources.displayMetrics
    val wifi =
        snapshot.pages["Network"].orEmpty().flatMap { it.fields }.associate { it.label to it.value }
    LazyColumn(
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { DeviceHero { navigate("System") } }
        item {
            Row(
                Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val ram = usedFraction(snapshot.ramUsed, snapshot.ramTotal)
                val storage = usedFraction(snapshot.storageUsed, snapshot.storageTotal)
                GaugeTile(
                    "Battery",
                    percent?.div(100f),
                    percent?.let { "$it%" } ?: "N/A",
                    temperature?.let { "%.1f °C".format(it) } ?: "Unavailable",
                    Modifier.weight(1f),
                    invert = true,
                ) {
                    navigate("Battery")
                }
                GaugeTile(
                    "RAM",
                    ram,
                    "${(ram * 100).toInt()}%",
                    "${formatBytes(snapshot.ramUsed)} / ${formatBytes(snapshot.ramTotal)}",
                    Modifier.weight(1f),
                ) {
                    navigate("Monitors")
                }
                GaugeTile(
                    "Storage",
                    storage,
                    "${(storage * 100).toInt()}%",
                    "${formatBytes(snapshot.storageUsed)} / ${formatBytes(snapshot.storageTotal)}",
                    Modifier.weight(1f),
                ) {
                    navigate("Hardware")
                }
            }
        }
        item { CpuCard(snapshot) { navigate("CPU analysis") } }
        item {
            ListGroup(
                "Device information",
                listOf(
                    GroupEntry(
                        "Network",
                        "${snapshot.network} / ${wifi["Link speed"] ?: formatBytes(snapshot.received)} / ${wifi["Signal"] ?: formatBytes(snapshot.sent)}",
                    ) {
                        navigate("Network")
                    },
                    GroupEntry(
                        "Apps",
                        "${apps.count { !it.system }} User / ${apps.count { it.system }} System / ${apps.size} Visible",
                        Icons.Default.Apps,
                    ) {
                        navigate("Apps")
                    },
                    GroupEntry(
                        "Display",
                        "${metrics.widthPixels} x ${metrics.heightPixels} / ${display.refreshRate.toInt()} Hz / ${Build.HARDWARE}",
                    ) {
                        navigate("Hardware")
                    },
                    GroupEntry("Device report", "Export or share device information") {
                        navigate("Device report")
                    },
                ),
            )
        }
        item { SectionLabel("Quick access") }
        items(
            listOf("Tests", "Tools", "Monitors", "Battery monitor", "Benchmarks", "Widgets")
                .chunked(3)
        ) { row ->
            Row(
                Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { name ->
                    ShortcutTile(name, Modifier.weight(1f).fillMaxHeight()) { navigate(name) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceHero(onClick: () -> Unit) {
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    CheckCard(
        Modifier.fillMaxWidth(),
        onClick = onClick,
        color = MaterialTheme.colorScheme.primaryContainer,
        border = false,
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DeviceText(
                    "Device & System",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                DeviceText(
                    Build.MODEL.replace('_', ' '),
                    style = MaterialTheme.typography.headlineLarge,
                    color = onContainer,
                )
                DeviceText(
                    Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyMedium,
                    color = onContainer.copy(alpha = .75f),
                )
                FlowRow(
                    Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HeroChip("Android ${Build.VERSION.RELEASE}")
                    HeroChip("API ${Build.VERSION.SDK_INT}")
                    HeroChip("${deviceString("Uptime")} ${formatUptime()}")
                }
            }
        }
    }
}

@Composable
private fun HeroChip(text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = .7f)) {
        DeviceText(
            text,
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun GaugeTile(
    title: String,
    fraction: Float?,
    value: String,
    detail: String,
    modifier: Modifier,
    invert: Boolean = false,
    onClick: () -> Unit,
) {
    CheckCard(modifier.fillMaxHeight(), onClick = onClick) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DeviceText(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            RingGauge(fraction, 68.dp, 6.dp, levelColor(fraction, invert)) {
                DeviceText(
                    value,
                    style = ReadingStyle,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            DeviceText(
                detail,
                style = MaterialTheme.typography.labelSmall.copy(textAlign = TextAlign.Center),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun CpuCard(snapshot: DeviceSnapshot, onClick: () -> Unit) {
    CheckCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconBadge(Icons.Default.Memory, 36.dp)
                Column(Modifier.weight(1f)) {
                    DeviceText("CPU status", style = MaterialTheme.typography.titleMedium)
                    DeviceText(
                        "${deviceString("Cores")}: ${snapshot.cores.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    null,
                    Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                Modifier.fillMaxWidth()
                    .height(48.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f),
                        RoundedCornerShape(10.dp),
                    )
            ) {
                CpuFrequencyArea(snapshot)
                if (snapshot.cores.none { it.currentKhz != null })
                    DeviceText(
                        "Frequency unavailable",
                        Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }
            val columns = if (snapshot.cores.any { it.status != null }) 2 else 4
            snapshot.cores.chunked(columns).forEach { cores ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cores.forEach { core -> CoreCell(core, Modifier.weight(1f)) }
                    repeat(columns - cores.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun CoreCell(core: CoreInfo, modifier: Modifier) {
    val load =
        core.currentKhz?.let { current -> core.maxKhz?.takeIf { it > 0 }?.let { current.toFloat() / it } }
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        DeviceText(
            "Core ${core.id}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DeviceText(
            core.status ?: formatCpuFrequency(core.currentKhz),
            style = ReadingStyle,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val barColor = levelColor(load)
        if (load != null) Box(
            Modifier.fillMaxWidth()
                .height(3.dp)
                .background(MaterialTheme.colorScheme.outlineVariant, CircleShape)
        ) {
            Box(
                Modifier.fillMaxWidth((load ?: 0f).coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(barColor, CircleShape)
            )
        }
    }
}

@Composable
private fun ShortcutTile(name: String, modifier: Modifier, onClick: () -> Unit) {
    CheckCard(modifier, onClick = onClick) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconBadge(checkIcon(name), 40.dp)
            DeviceText(
                name,
                style = MaterialTheme.typography.labelMedium.copy(textAlign = TextAlign.Center),
                maxLines = 2,
            )
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
            Brush.verticalGradient(listOf(accent.copy(alpha = .5f), accent.copy(alpha = .08f))),
        )
    }
}

internal fun checkIcon(name: String): ImageVector =
    when {
        name == "Hardware" || name == "Graphics" -> Icons.Default.DeveloperBoard
        name == "System" || name == "Android" || name == "Operating System" ->
            Icons.Default.Android
        name == "Apps" -> Icons.Default.Apps
        name == "Device" -> Icons.Default.PhoneAndroid
        name == "Processor" || name == "Kernel memory" || name == "CPU information" ->
            Icons.Default.Memory
        name == "Memory and storage" -> Icons.Default.SdStorage
        name == "Media codecs" -> Icons.Default.Movie
        name == "Audio devices" -> Icons.Default.VolumeUp
        name == "Hardware features" -> Icons.Default.Checklist
        name == "Runtime" -> Icons.Default.Code
        name == "Language" -> Icons.Default.Language
        name == "Settings" -> Icons.Default.Settings
        name.endsWith(" theme") -> Icons.Default.Palette
        name == "App permissions" -> Icons.Default.AdminPanelSettings
        name == "Usage access" -> Icons.Default.Timelapse
        name == "Display over other apps" -> Icons.Default.PictureInPicture
        name.startsWith("Export ") -> Icons.Default.FileDownload
        name == "Copy quick specs" -> Icons.Default.ContentCopy
        name == "Network tools" -> Icons.Default.NetworkCheck
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
        name in listOf("Accelerometer", "Gyroscope", "Proximity", "Magnetic field") ||
            name.contains("sensor", true) -> Icons.Default.Sensors
        else -> Icons.Default.Tune
    }

@Composable
internal fun CheckActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    CheckCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconBadge(checkIcon(title), 40.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                DeviceText(title, style = MaterialTheme.typography.titleSmall)
                if (subtitle.isNotBlank() && subtitle !in listOf("Open test", "Open tool"))
                    DeviceText(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }
            Icon(
                Icons.Default.ChevronRight,
                null,
                Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Data sections
// ---------------------------------------------------------------------------------------------

@Composable
internal fun DataSections(sections: List<DeviceSection>, header: @Composable () -> Unit) {
    LazyColumn(
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { header() }
        items(sections) { section -> SectionBlock(section, selectable = true) }
    }
}

@Composable
private fun SectionBlock(section: DeviceSection, selectable: Boolean) {
    CheckCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconBadge(checkIcon(section.title), 32.dp)
            DeviceText(
                section.title,
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            DeviceText(
                "${section.fields.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selectable) SelectionContainer { Column { FieldRows(section.fields) } }
        else FieldRows(section.fields)
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
internal fun deviceSearchMatches(field: DeviceField, query: String): Boolean =
    field.label.contains(query, true) ||
        field.value.contains(query, true) ||
        deviceString(field.label).contains(query, true) ||
        deviceString(field.value).contains(query, true)

/**
 * Short values sit to the right of their label so a section reads like a spec sheet; long
 * identifiers (fingerprints, addresses, codec lists) drop below the label at full width.
 */
@Composable
private fun FieldRows(fields: List<DeviceField>) {
    fields.forEachIndexed { index, field ->
        if (index > 0)
            HorizontalDivider(
                Modifier.padding(horizontal = 14.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .7f),
            )
        val value =
            when (field.value) {
                "true" -> "Yes"
                "false" -> "No"
                "GOOD" -> "Good"
                else -> field.value
            }
        val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        if (value.length <= 26 && '\n' !in value)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DeviceText(
                    field.label,
                    Modifier.weight(.45f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = labelColor,
                )
                DeviceText(
                    value,
                    Modifier.weight(.55f),
                    style = ReadingStyle.copy(textAlign = TextAlign.End),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        else
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                DeviceText(field.label, style = MaterialTheme.typography.bodyMedium, color = labelColor)
                DeviceText(
                    value,
                    style =
                        ReadingStyle.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    fontSize = 13.sp,
                )
            }
    }
}

// ---------------------------------------------------------------------------------------------
// Battery overview
// ---------------------------------------------------------------------------------------------

@Composable
internal fun BatteryOverview(
    info: com.abanana.abattery.domain.model.BatteryInfo?,
    details: () -> Unit,
) {
    val sections = DeviceSnapshot().withBattery(info).pages["Battery"].orEmpty()
    LazyColumn(
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            val fraction = info?.percent?.div(100f)
            CheckCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        RingGauge(fraction, 116.dp, 10.dp, levelColor(fraction, invert = true)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                DeviceText(
                                    info?.let { "${it.percent}%" } ?: "N/A",
                                    style = ReadingStyle,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (info?.isCharging == true)
                                    Icon(
                                        Icons.Default.Bolt,
                                        null,
                                        Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            DeviceText("Battery", style = MaterialTheme.typography.titleLarge)
                            DeviceText(
                                info?.let {
                                    androidx.compose.ui.res.stringResource(it.chargeStatus.labelRes())
                                } ?: "Unavailable",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (info != null)
                                DeviceText(
                                    "%.1f °C · %.2f V".format(info.temperatureCelsius, info.voltageV),
                                    style = ReadingStyle,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                        }
                    }
                    FilledTonalButton(onClick = details, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Insights, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        DeviceText("Battery details")
                    }
                }
            }
        }
        sections.forEach { section -> item { SectionBlock(section, selectable = false) } }
    }
}

// ---------------------------------------------------------------------------------------------
// Apps
// ---------------------------------------------------------------------------------------------

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
                bottomStart = if (last) 20.dp else 0.dp,
                bottomEnd = if (last) 20.dp else 0.dp,
            ),
    ) {
        Column {
            HorizontalDivider(
                Modifier.padding(start = 70.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (icon != null)
                    androidx.compose.foundation.Image(icon, null, Modifier.size(40.dp))
                else IconBadge(Icons.Default.Android, 40.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    DeviceText(
                        app.label,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    DeviceText(
                        app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (app.system) StatusPill("System", MaterialTheme.colorScheme.outline)
            }
        }
    }
}
