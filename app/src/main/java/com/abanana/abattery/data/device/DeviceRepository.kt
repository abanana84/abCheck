package com.abanana.abattery.data.device

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics as CC
import android.hardware.camera2.CameraManager
import android.hardware.usb.UsbManager
import android.media.AudioManager
import android.media.MediaCodecList
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.NetworkInterface
import java.util.Collections
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private val pm
        get() = context.packageManager

    private fun field(name: String, value: Any?) =
        DeviceField(name, value?.toString()?.takeIf { it.isNotBlank() } ?: UNAVAILABLE)

    private fun section(name: String, vararg values: Pair<String, Any?>) =
        DeviceSection(name, values.map { field(it.first, it.second) })

    private fun read(path: String): String? =
        runCatching { File(path).readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }

    private fun yes(value: Boolean) = if (value) "Yes" else "No"

    private fun mhz(value: Long?) = formatCpuFrequency(value)

    fun cores(): List<CoreInfo> =
        CpuFrequencyReader(::read).let { reader ->
            (0 until Runtime.getRuntime().availableProcessors()).map(reader::core)
        }

    fun snapshot(previous: DeviceSnapshot? = null, refreshDetails: Boolean = true): DeviceSnapshot {
        val memory =
            ActivityManager.MemoryInfo().also {
                context.getSystemService(ActivityManager::class.java).getMemoryInfo(it)
            }
        val disk = StatFs(Environment.getDataDirectory().path)
        val coreList = cores()
        return DeviceSnapshot(
            pages =
                if (refreshDetails || previous == null)
                    mapOf(
                        "Hardware" to safely("Hardware") { hardware(coreList) },
                        "System" to safely("System") { system() },
                        "Network" to safely("Network") { network() },
                        "Camera" to safely("Camera") { cameras() },
                        "Sensors" to safely("Sensors") { sensors() },
                    )
                else previous.pages,
            cores = coreList,
            ramUsed = memory.totalMem - memory.availMem,
            ramTotal = memory.totalMem,
            storageUsed = disk.totalBytes - disk.availableBytes,
            storageTotal = disk.totalBytes,
            network = connectionType(),
            received = TrafficStats.getTotalRxBytes(),
            sent = TrafficStats.getTotalTxBytes(),
            sampledAt = SystemClock.elapsedRealtime(),
        )
    }

    private fun safely(name: String, read: () -> List<DeviceSection>): List<DeviceSection> =
        runCatching(read).getOrElse {
            listOf(section(name, "Status" to "Unavailable: ${it.javaClass.simpleName}"))
        }

    private fun hardware(cores: List<CoreInfo>): List<DeviceSection> {
        val metrics = context.resources.displayMetrics
        val display = context.getSystemService(WindowManager::class.java).defaultDisplay
        val mode = display.mode
        val cpu =
            read("/proc/cpuinfo")
                ?.lineSequence()
                ?.filter { it.contains(":") }
                ?.map { it.substringBefore(':').trim() to it.substringAfter(':').trim() }
                ?.distinct()
                ?.take(20)
                ?.toList()
                .orEmpty()
        val codec = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        val audio = context.getSystemService(AudioManager::class.java)
        val disk = StatFs(Environment.getDataDirectory().path)
        return listOf(
            section(
                "Processor",
                "SoC manufacturer" to
                    if (Build.VERSION.SDK_INT >= 31) Build.SOC_MANUFACTURER else null,
                "SoC model" to if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else null,
                "Hardware" to Build.HARDWARE,
                "Board" to Build.BOARD,
                "Cores" to cores.size,
                "Architecture" to Build.SUPPORTED_ABIS.joinToString(),
                "64-bit ABIs" to Build.SUPPORTED_64_BIT_ABIS.joinToString(),
            ),
            DeviceSection(
                "CPU frequencies",
                cores.map {
                    field(
                        "Core ${it.id}",
                        it.status ?: "${mhz(it.currentKhz)} · ${mhz(it.minKhz)} – ${mhz(it.maxKhz)}",
                    )
                },
            ),
            DeviceSection(
                "CPU information",
                cpu.map { field(it.first, it.second) }
                    .ifEmpty { listOf(field("Kernel CPU information", "Restricted by Android")) },
            ),
            com.abanana.abattery.diagnostics.GraphicsInfo.sections.first(),
            section(
                "Graphics",
                "OpenGL ES" to
                    context
                        .getSystemService(ActivityManager::class.java)
                        .deviceConfigurationInfo
                        .glEsVersion,
                "Vulkan" to
                    pm.systemAvailableFeatures
                        .filter { it.name?.contains("vulkan") == true }
                        .joinToString { "${it.name?.substringAfterLast('.')} ${it.version}" },
            ),
            section(
                "Display",
                "Resolution" to "${mode.physicalWidth} × ${mode.physicalHeight}",
                "Refresh rate" to "${mode.refreshRate} Hz",
                "Density" to "${metrics.densityDpi} dpi",
                "Logical density" to metrics.density,
                "X / Y density" to "${metrics.xdpi} / ${metrics.ydpi}",
                "HDR types" to display.hdrCapabilities.supportedHdrTypes.joinToString(),
                "Wide color gamut" to yes(display.isWideColorGamut),
                "Supported modes" to
                    display.supportedModes.joinToString("\n") {
                        "${it.physicalWidth} × ${it.physicalHeight} @ ${it.refreshRate} Hz"
                    },
            ),
            section(
                "Memory and storage",
                "Java heap limit" to formatBytes(Runtime.getRuntime().maxMemory()),
                "Internal storage" to formatBytes(disk.totalBytes),
                "Available storage" to formatBytes(disk.availableBytes),
                "Page size" to android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE),
                "External volumes" to
                    context.getExternalFilesDirs(null).filterNotNull().joinToString("\n") {
                        it.path
                    },
            ),
            DeviceSection(
                "Kernel memory",
                read("/proc/meminfo")
                    ?.lineSequence()
                    ?.take(24)
                    ?.map { field(it.substringBefore(':'), it.substringAfter(':').trim()) }
                    ?.toList()
                    .orEmpty(),
            ),
            DeviceSection(
                "Audio devices",
                audio
                    .getDevices(AudioManager.GET_DEVICES_INPUTS or AudioManager.GET_DEVICES_OUTPUTS)
                    .map {
                        field(
                            it.productName.toString(),
                            "Type ${it.type} · ${it.sampleRates.joinToString()} Hz · channels ${it.channelCounts.joinToString()}",
                        )
                    },
            ),
            DeviceSection(
                "Media codecs",
                codec.map {
                    field(
                        it.name,
                        "${if (it.isEncoder) "Encoder" else "Decoder"} · ${it.supportedTypes.joinToString()}",
                    )
                },
            ),
            DeviceSection(
                "Hardware features",
                pm.systemAvailableFeatures
                    .filter { it.name != null }
                    .sortedBy { it.name }
                    .map { field(it.name.removePrefix("android.hardware."), it.version) },
            ),
        )
    }

    private fun system(): List<DeviceSection> {
        val power = context.getSystemService(PowerManager::class.java)
        return listOf(
            section(
                "Device",
                "Manufacturer" to Build.MANUFACTURER,
                "Brand" to Build.BRAND,
                "Model" to Build.MODEL,
                "Product" to Build.PRODUCT,
                "Device" to Build.DEVICE,
                "Board" to Build.BOARD,
                "Bootloader" to Build.BOOTLOADER,
            ),
            section(
                "Android",
                "Version" to Build.VERSION.RELEASE,
                "API level" to Build.VERSION.SDK_INT,
                "Security patch" to Build.VERSION.SECURITY_PATCH,
                "Build" to Build.DISPLAY,
                "Build ID" to Build.ID,
                "Fingerprint" to Build.FINGERPRINT,
                "Build type" to Build.TYPE,
                "Tags" to Build.TAGS,
                "Kernel" to System.getProperty("os.version"),
                "Language" to Locale.getDefault().toLanguageTag(),
                "Time zone" to java.util.TimeZone.getDefault().id,
            ),
            section(
                "Runtime",
                "Uptime" to duration(SystemClock.elapsedRealtime()),
                "Awake time" to duration(SystemClock.uptimeMillis()),
                "Deep sleep since boot" to
                    duration(SystemClock.elapsedRealtime() - SystemClock.uptimeMillis()),
                "Power saving" to yes(power.isPowerSaveMode),
                "Interactive" to yes(power.isInteractive),
                "Thermal status" to
                    if (Build.VERSION.SDK_INT >= 29) thermalStatus(power.currentThermalStatus)
                    else UNAVAILABLE,
                "ADB enabled" to
                    yes(
                        Settings.Global.getInt(
                            context.contentResolver,
                            Settings.Global.ADB_ENABLED,
                            0,
                        ) == 1
                    ),
                "Developer options" to
                    yes(
                        Settings.Global.getInt(
                            context.contentResolver,
                            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                            0,
                        ) == 1
                    ),
            ),
            section(
                "Root and privileged access",
                "Root indicators" to
                    if (
                        listOf("/system/bin/su", "/system/xbin/su", "/sbin/su").any {
                            File(it).exists()
                        }
                    )
                        "su binary detected (access not tested)"
                    else "No accessible su binary; inconclusive",
                "Shizuku installed" to
                    yes(
                        runCatching { pm.getPackageInfo("moe.shizuku.privileged.api", 0) }.isSuccess
                    ),
                "System-wide logs / process data" to
                    "Requires Android DUMP / READ_LOGS privileges or a compatible privileged bridge",
            ),
        )
    }

    fun connectionType(): String {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val cap = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "Disconnected"
        return when {
            cap.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            cap.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Other"
        }
    }

    @Suppress("DEPRECATION")
    private fun network(): List<DeviceSection> = buildList {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val link = cm.getLinkProperties(cm.activeNetwork)
        val cap = cm.getNetworkCapabilities(cm.activeNetwork)
        add(
            section(
                "Connection",
                "Transport" to connectionType(),
                "Internet validated" to
                    yes(cap?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true),
                "Metered" to yes(cm.isActiveNetworkMetered),
                "Downstream estimate" to cap?.linkDownstreamBandwidthKbps?.let { "$it Kbit/s" },
                "Upstream estimate" to cap?.linkUpstreamBandwidthKbps?.let { "$it Kbit/s" },
                "Interface" to link?.interfaceName,
                "Addresses" to link?.linkAddresses?.joinToString("\n"),
                "DNS" to link?.dnsServers?.joinToString { it.hostAddress.orEmpty() },
                "Routes" to link?.routes?.joinToString("\n"),
            )
        )
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        val info = runCatching { wifi.connectionInfo }.getOrNull()
        add(
            section(
                "Wi-Fi",
                "Enabled" to yes(wifi.isWifiEnabled),
                "SSID" to info?.ssid?.takeUnless { it == "<unknown ssid>" }?.trim('"'),
                "BSSID" to info?.bssid?.takeUnless { it == "02:00:00:00:00:00" },
                "Signal" to info?.rssi?.takeIf { it > -127 }?.let { "$it dBm" },
                "Link speed" to info?.linkSpeed?.takeIf { it > 0 }?.let { "$it Mbit/s" },
                "Frequency" to info?.frequency?.takeIf { it > 0 }?.let { "$it MHz" },
                "Names and scans" to
                    "Android may require precise location permission and Location enabled",
            )
        )
        add(
            section(
                "Traffic since boot",
                "Received" to formatBytes(TrafficStats.getTotalRxBytes()),
                "Sent" to formatBytes(TrafficStats.getTotalTxBytes()),
                "Mobile received" to formatBytes(TrafficStats.getMobileRxBytes()),
                "Mobile sent" to formatBytes(TrafficStats.getMobileTxBytes()),
                "This app received" to
                    formatBytes(TrafficStats.getUidRxBytes(android.os.Process.myUid())),
                "This app sent" to
                    formatBytes(TrafficStats.getUidTxBytes(android.os.Process.myUid())),
            )
        )
        runCatching { Collections.list(NetworkInterface.getNetworkInterfaces()) }
            .getOrDefault(emptyList())
            .forEach { net ->
                add(
                    section(
                        net.name,
                        "Up" to yes(net.isUp),
                        "Loopback" to yes(net.isLoopback),
                        "MTU" to net.mtu,
                        "Addresses" to
                            Collections.list(net.inetAddresses).joinToString("\n") {
                                it.hostAddress.orEmpty()
                            },
                    )
                )
            }
        val tm = context.getSystemService(android.telephony.TelephonyManager::class.java)
        fun mobile(read: () -> Any?): Any? = runCatching(read).getOrNull()
        add(
            section(
                "Mobile network",
                "Operator" to mobile { tm?.networkOperatorName },
                "Country" to mobile { tm?.networkCountryIso },
                "SIM operator" to mobile { tm?.simOperatorName },
                "SIM state" to mobile { tm?.simState },
                "Phone type" to mobile { tm?.phoneType },
                "Roaming" to mobile { tm?.isNetworkRoaming?.let(::yes) },
            )
        )
    }

    private fun cameras(): List<DeviceSection> {
        val manager = context.getSystemService(CameraManager::class.java)
        return runCatching {
                manager.cameraIdList.map { id ->
                    val c = manager.getCameraCharacteristics(id)
                    val sizes =
                        c.get(CC.SCALER_STREAM_CONFIGURATION_MAP)
                            ?.getOutputSizes(android.graphics.ImageFormat.JPEG)
                            .orEmpty()
                    section(
                        "Camera $id · ${when(c.get(CC.LENS_FACING)) { CC.LENS_FACING_BACK -> "Rear"
 CC.LENS_FACING_FRONT -> "Front"
 else -> "External" }}",
                        "Hardware level" to
                            when (c.get(CC.INFO_SUPPORTED_HARDWARE_LEVEL)) {
                                0 -> "Limited"
                                1 -> "Full"
                                2 -> "Legacy"
                                3 -> "Level 3"
                                else -> "External"
                            },
                        "Sensor size" to c.get(CC.SENSOR_INFO_PHYSICAL_SIZE),
                        "Pixel array" to c.get(CC.SENSOR_INFO_PIXEL_ARRAY_SIZE),
                        "JPEG resolutions" to sizes.joinToString("\n"),
                        "Focal lengths" to
                            c.get(CC.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.joinToString(),
                        "Apertures" to c.get(CC.LENS_INFO_AVAILABLE_APERTURES)?.joinToString(),
                        "ISO range" to c.get(CC.SENSOR_INFO_SENSITIVITY_RANGE),
                        "Exposure range (ns)" to c.get(CC.SENSOR_INFO_EXPOSURE_TIME_RANGE),
                        "Flash" to c.get(CC.FLASH_INFO_AVAILABLE),
                        "Autofocus modes" to c.get(CC.CONTROL_AF_AVAILABLE_MODES)?.joinToString(),
                        "Capabilities" to c.get(CC.REQUEST_AVAILABLE_CAPABILITIES)?.joinToString(),
                    )
                }
            }
            .getOrElse {
                listOf(
                    section(
                        "Cameras",
                        "Status" to "Camera information unavailable: ${it.javaClass.simpleName}",
                    )
                )
            }
    }

    private fun sensors(): List<DeviceSection> =
        context.getSystemService(SensorManager::class.java).getSensorList(Sensor.TYPE_ALL).map { s
            ->
            section(
                s.name,
                "Vendor" to s.vendor,
                "Type" to s.stringType,
                "Version" to s.version,
                "Range" to s.maximumRange,
                "Resolution" to s.resolution,
                "Power" to "${s.power} mA",
                "Minimum delay" to "${s.minDelay} µs",
                "Wake-up" to yes(s.isWakeUpSensor),
                "Reporting mode" to s.reportingMode,
            )
        }

    fun apps(): List<InstalledApp> =
        pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            .map { p ->
                val a = p.applicationInfo
                InstalledApp(
                    a?.loadLabel(pm)?.toString() ?: p.packageName,
                    p.packageName,
                    p.versionName.orEmpty(),
                    a != null && a.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                    a?.targetSdkVersion ?: 0,
                    a?.minSdkVersion ?: 0,
                    p.requestedPermissions.orEmpty().mapIndexed { i, name ->
                        name to
                            ((p.requestedPermissionsFlags?.getOrNull(i) ?: 0) and
                                android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED != 0)
                    },
                    p.firstInstallTime,
                    p.lastUpdateTime,
                    a?.sourceDir.orEmpty(),
                    a?.enabled ?: false,
                )
            }
            .sortedBy { it.label.lowercase(Locale.ROOT) }

    fun usb(): List<DeviceSection> =
        context
            .getSystemService(UsbManager::class.java)
            .deviceList
            .values
            .map { d ->
                section(
                    d.deviceName,
                    "Manufacturer" to d.manufacturerName,
                    "Product" to d.productName,
                    "Vendor ID" to d.vendorId,
                    "Product ID" to d.productId,
                    "Class" to d.deviceClass,
                    "Interfaces" to d.interfaceCount,
                )
            }
            .ifEmpty { listOf(section("USB", "Status" to "No USB devices connected")) }

    fun usage(): List<DeviceSection> {
        val stats =
            context
                .getSystemService(UsageStatsManager::class.java)
                .queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    System.currentTimeMillis() - 86400000,
                    System.currentTimeMillis(),
                )
        return listOf(
            DeviceSection(
                "Screen time · last 24 hours",
                stats
                    .filter { it.totalTimeInForeground > 0 }
                    .sortedByDescending { it.totalTimeInForeground }
                    .map { field(it.packageName, duration(it.totalTimeInForeground)) }
                    .ifEmpty {
                        listOf(
                            field(
                                "Access",
                                "Enable Usage access in Android settings to read screen time",
                            )
                        )
                    },
            )
        )
    }

    companion object {
        fun duration(ms: Long): String {
            val s = ms / 1000
            return "${s / 3600}h ${s % 3600 / 60}m ${s % 60}s"
        }

        fun thermalStatus(value: Int) =
            listOf("Normal", "Light", "Moderate", "Severe", "Critical", "Emergency", "Shutdown")
                .getOrElse(value) { UNAVAILABLE }
    }
}
