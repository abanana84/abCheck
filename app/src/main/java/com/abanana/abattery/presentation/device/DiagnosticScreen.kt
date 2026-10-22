package com.abanana.abattery.presentation.device

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.abanana.abattery.data.device.*
import kotlinx.coroutines.*

val testNames =
    listOf(
        "Display test",
        "Multitouch",
        "Brightness",
        "Buttons",
        "Vibration",
        "Speakers",
        "Earpiece",
        "Microphone",
        "Flashlight",
        "Camera test",
        "Accelerometer",
        "Gyroscope",
        "Light sensor",
        "Proximity",
        "Magnetic field",
        "Headset",
        "Charging",
        "Biometric scanner",
    )
val toolNames =
    listOf(
        "Shizuku diagnostics",
        "Root check",
        "Boot integrity",
        "CPU times",
        "GPU times",
        "Storage health",
        "CPU analysis",
        "System graphs",
        "Temperatures",
        "Wi-Fi scan",
        "Bluetooth scan",
        "GPS",
        "USB",
        "Network usage",
        "Screen time",
        "Permissions explorer",
        "Task manager",
        "Logcat",
        "OpenGL info",
        "Network reachability",
        "Network mapper",
        "Device report",
    )

@Composable
fun TestsScreen(navigate: (String) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        items(testNames, key = { it }) { name -> ActionTile(name, "") { navigate(name) } }
    }
}

@Composable
fun ToolsScreen(navigate: (String) -> Unit) {
    val groups =
        listOf(
            "System" to
                listOf(
                    "Root check",
                    "Permissions explorer",
                    "Boot integrity",
                    "Screen time",
                    "Task manager",
                    "System graphs",
                    "Logcat",
                    "Shizuku diagnostics",
                ),
            "Hardware" to
                listOf(
                    "CPU analysis",
                    "CPU times",
                    "GPU times",
                    "Temperatures",
                    "Storage health",
                    "OpenGL info",
                    "USB",
                ),
            "Network" to
                listOf(
                    "Bluetooth scan",
                    "Wi-Fi scan",
                    "Network mapper",
                    "Network usage",
                    "Network reachability",
                    "GPS",
                ),
            "Reports" to listOf("Device report"),
        )
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        groups.forEach { (title, names) ->
            item {
                DeviceText(
                    title,
                    Modifier.padding(start = 16.dp, top = 20.dp, bottom = 10.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            items(names, key = { it }) { name -> ActionTile(name, "") { navigate(name) } }
        }
    }
}

@Composable
fun PermissionGate(permissions: List<String>, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by
        remember(permissions) {
            mutableStateOf(
                permissions.all {
                    context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
                }
            )
        }
    var denied by remember { mutableStateOf(false) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            result ->
            granted =
                permissions.all {
                    result[it] == true ||
                        context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
                }
            denied = !granted
        }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, permissions) {
        val observer =
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME)
                    granted =
                        permissions.all {
                            context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
                        }
            }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    if (granted) content()
    else
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            DeviceText("This tool needs permission", style = MaterialTheme.typography.titleLarge)
            DeviceText(permissions.joinToString("\n") { it.substringAfterLast('.') })
            Button(onClick = { launcher.launch(permissions.toTypedArray()) }) {
                DeviceText("Grant permission")
            }
            if (denied) {
                DeviceText("Permission denied. You can enable it in Android app settings.")
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}"),
                            )
                        )
                    }
                ) {
                    DeviceText("Open app settings")
                }
            }
        }
}

@Composable
fun DiagnosticScreen(
    name: String,
    repository: DeviceRepository,
    apps: List<InstalledApp>,
    snapshot: DeviceSnapshot,
) {
    val sensorTypes =
        mapOf(
            "Accelerometer" to Sensor.TYPE_ACCELEROMETER,
            "Gyroscope" to Sensor.TYPE_GYROSCOPE,
            "Light sensor" to Sensor.TYPE_LIGHT,
            "Proximity" to Sensor.TYPE_PROXIMITY,
            "Magnetic field" to Sensor.TYPE_MAGNETIC_FIELD,
        )
    when {
        name in sensorTypes -> SensorScreen(sensorTypes[name])
        name == "Display test" || name == "Brightness" -> DisplayTest(name == "Brightness")
        name == "Multitouch" -> MultitouchTest()
        name == "Speakers" || name == "Earpiece" -> AudioOutputTest(name == "Earpiece")
        name == "Flashlight" ->
            PermissionGate(listOf(Manifest.permission.CAMERA)) { HardwareTest(name) }
        name == "Camera test" -> PermissionGate(listOf(Manifest.permission.CAMERA)) { CameraTest() }
        name == "Microphone" ->
            PermissionGate(listOf(Manifest.permission.RECORD_AUDIO)) { MicrophoneTest() }
        name == "Wi-Fi scan" ->
            PermissionGate(listOf(Manifest.permission.ACCESS_FINE_LOCATION)) { WifiScanScreen() }
        name == "Bluetooth scan" ->
            PermissionGate(
                if (Build.VERSION.SDK_INT >= 31)
                    listOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT,
                    )
                else listOf(Manifest.permission.ACCESS_FINE_LOCATION)
            ) {
                BluetoothScreen()
            }
        name == "GPS" ->
            PermissionGate(listOf(Manifest.permission.ACCESS_FINE_LOCATION)) { GpsScreen() }
        name == "Network reachability" || name == "Network mapper" ->
            NetworkTool(name == "Network mapper")
        name == "OpenGL info" -> GraphicsScreen()
        name == "System graphs" || name == "Network usage" -> MonitorScreen(snapshot, null)
        name == "CPU analysis" ->
            SectionList(
                snapshot.pages["Hardware"].orEmpty().filter {
                    it.title.contains("CPU") || it.title == "Processor"
                }
            )
        name == "Temperatures" -> TemperatureScreen()
        name == "Shizuku diagnostics" -> PrivilegedScreen()
        name == "Root check" -> RootScreen()
        name == "Boot integrity" -> IntegrityScreen()
        name == "CPU times" -> CpuTimesScreen()
        name == "GPU times" -> CpuTimesScreen(gpu = true)
        name == "Storage health" -> StorageHealthScreen()
        name == "USB" -> {
            var sections by remember { mutableStateOf(repository.usb()) }
            Column {
                TextButton(onClick = { sections = repository.usb() }) {
                    DeviceText("Refresh connected USB devices")
                }
                Box(Modifier.weight(1f)) { SectionList(sections) }
            }
        }
        name == "Screen time" -> UsageScreen(repository)
        name == "Permissions explorer" -> PermissionsScreen(apps)
        name == "Task manager" -> ProcessScreen()
        name == "Logcat" -> LogcatScreen()
        else -> HardwareTest(name)
    }
}

@Composable
private fun DisplayTest(brightness: Boolean) {
    val activity = LocalContext.current as Activity
    var index by remember { mutableIntStateOf(0) }
    var level by remember { mutableFloatStateOf(0.5f) }
    val colors = listOf(Color.White, Color.Red, Color.Green, Color.Blue, Color.Black, Color.Gray)
    DisposableEffect(Unit) {
        val old = activity.window.attributes.screenBrightness
        onDispose {
            activity.window.attributes = activity.window.attributes.apply { screenBrightness = old }
        }
    }
    LaunchedEffect(level, brightness) {
        if (brightness)
            activity.window.attributes =
                activity.window.attributes.apply { screenBrightness = level }
    }
    Column(
        Modifier.fillMaxSize()
            .background(colors[index])
            .pointerInput(Unit) { detectTapGestures { index = (index + 1) % colors.size } }
            .padding(24.dp)
    ) {
        Surface(color = Color.Black.copy(alpha = .7f), shape = MaterialTheme.shapes.medium) {
            DeviceText(
                "Tap to cycle colors. Inspect pixels, uniformity and burn-in. Use Back to exit.",
                Modifier.padding(12.dp),
                color = Color.White,
            )
        }
        if (brightness) Slider(level, { level = it }, valueRange = 0.01f..1f)
    }
}

@Composable
private fun HardwareTest(name: String) {
    val context = LocalContext.current
    var message by remember { mutableStateOf("Ready") }
    val manager = remember { context.getSystemService(CameraManager::class.java) }
    var torch by remember { mutableStateOf(false) }
    val flashId = remember {
        runCatching {
                manager.cameraIdList.firstOrNull {
                    manager
                        .getCameraCharacteristics(it)
                        .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) ==
                        true
                }
            }
            .getOrNull()
    }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    var authentication by remember { mutableStateOf<CancellationSignal?>(null) }
    DisposableEffect(lifecycle) {
        fun stop() {
            authentication?.cancel()
            if (torch) {
                runCatching { flashId?.let { manager.setTorchMode(it, false) } }
                torch = false
            }
        }
        val observer =
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) stop()
            }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            stop()
        }
    }
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeading(name)
        DeviceText(
            when (name) {
                "Buttons" ->
                    "Press volume up or down. The key event will appear below. Back exits the test."
                "Charging" ->
                    "Connect and disconnect your charger. The battery tab shows live charge status, current and voltage."
                "Headset" ->
                    "Connect a wired or Bluetooth headset, then refresh the detected audio routes."
                "Flashlight" ->
                    "Toggle the camera flash. Some devices need camera permission or do not have a flash."
                "Biometric scanner" ->
                    "Authenticate using enrolled device biometrics. No biometric data is read or stored by this app."
                else -> "Run a hardware check on this device."
            }
        )
        if (name == "Buttons") ButtonTest()
        else if (name == "Charging") ChargingTest()
        else
            Button(
                onClick = {
                    message =
                        runCatching {
                                when (name) {
                                    "Vibration" -> {
                                        val v = context.getSystemService(Vibrator::class.java)
                                        if (v.hasVibrator()) {
                                            v.vibrate(
                                                VibrationEffect.createOneShot(
                                                    500,
                                                    VibrationEffect.DEFAULT_AMPLITUDE,
                                                )
                                            )
                                            "Vibration requested. Confirm by touch."
                                        } else "No vibrator reported"
                                    }
                                    "Flashlight" -> {
                                        if (flashId == null) "No flash reported"
                                        else {
                                            manager.setTorchMode(flashId, !torch)
                                            torch = !torch
                                            "Flash ${if (torch) "on" else "off"}"
                                        }
                                    }
                                    "Headset" ->
                                        context
                                            .getSystemService(AudioManager::class.java)
                                            .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                                            .joinToString("\n") {
                                                "${it.productName} · type ${it.type}"
                                            }
                                    "Biometric scanner" -> {
                                        if (Build.VERSION.SDK_INT >= 28) {
                                            val prompt =
                                                android.hardware.biometrics.BiometricPrompt.Builder(
                                                        context
                                                    )
                                                    .setTitle("abCheck biometric test")
                                                    .setDescription(
                                                        "Verify the device biometric scanner"
                                                    )
                                                    .setNegativeButton(
                                                        "Cancel",
                                                        context.mainExecutor,
                                                    ) { _, _ ->
                                                    }
                                                    .build()
                                            authentication?.cancel()
                                            val signal = CancellationSignal()
                                            authentication = signal
                                            prompt.authenticate(
                                                signal,
                                                context.mainExecutor,
                                                object :
                                                    android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                                                    override fun onAuthenticationSucceeded(
                                                        result:
                                                            android.hardware.biometrics.BiometricPrompt.AuthenticationResult?
                                                    ) {
                                                        message = "Authentication succeeded"
                                                    }

                                                    override fun onAuthenticationError(
                                                        code: Int,
                                                        text: CharSequence?,
                                                    ) {
                                                        message = text.toString()
                                                    }

                                                    override fun onAuthenticationFailed() {
                                                        message = "Not recognized. Try again."
                                                    }
                                                },
                                            )
                                            "Waiting for authentication"
                                        } else "Biometric prompt requires Android 9 or newer"
                                    }
                                    else -> "Unsupported test"
                                }
                            }
                            .getOrElse { "Cannot run: ${it.message}" }
                }
            ) {
                DeviceText(if (name == "Flashlight") "Toggle flash" else "Run test")
            }
        DeviceText(message)
    }
}

@Composable
private fun UsageScreen(repository: DeviceRepository) {
    val context = LocalContext.current
    var sections by remember { mutableStateOf<List<DeviceSection>>(emptyList()) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(refresh) { sections = withContext(Dispatchers.IO) { repository.usage() } }
    Column {
        Row {
            TextButton(
                onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            ) {
                DeviceText("Enable usage access")
            }
            TextButton(onClick = { refresh++ }) { DeviceText("Refresh") }
        }
        Box(Modifier.weight(1f)) { SectionList(sections) }
    }
}

@Composable
private fun PermissionsScreen(apps: List<InstalledApp>) {
    val sections =
        remember(apps) {
            apps
                .flatMap { app -> app.permissions.map { it.first to (app.label to it.second) } }
                .groupBy({ it.first }, { it.second })
                .toSortedMap()
                .map { (name, users) ->
                    DeviceSection(
                        name,
                        users.map {
                            DeviceField(it.first, if (it.second) "Granted" else "Not granted")
                        },
                    )
                }
        }
    SectionList(sections)
}

@Composable
private fun ProcessScreen() {
    val context = LocalContext.current
    val am = context.getSystemService(ActivityManager::class.java)
    val sections = remember {
        listOf(
            DeviceSection(
                "Visible processes",
                am.runningAppProcesses.orEmpty().map {
                    DeviceField(it.processName, "PID ${it.pid} · importance ${it.importance}")
                },
            ),
            DeviceSection(
                "Android access",
                listOf(
                    DeviceField(
                        "Scope",
                        "Android restricts visibility to this app and related processes. System-wide management requires privileged access.",
                    )
                ),
            ),
        )
    }
    SectionList(sections)
}

@Composable
private fun LogcatScreen() {
    var logs by remember { mutableStateOf("Reading app logs…") }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(refresh) {
        logs =
            withContext(Dispatchers.IO) {
                runCatching {
                        val p =
                            ProcessBuilder(
                                    "logcat",
                                    "-d",
                                    "-t",
                                    "200",
                                    "--pid=${android.os.Process.myPid()}",
                                )
                                .redirectErrorStream(true)
                                .start()
                        try {
                            p.inputStream.bufferedReader().readText().takeLast(40000)
                        } finally {
                            p.destroy()
                        }
                    }
                    .getOrElse { "Logs unavailable: ${it.message}" }
            }
    }
    Column {
        DeviceText(
            "App logs only. Device-wide logs require READ_LOGS privileges.",
            Modifier.padding(16.dp),
        )
        TextButton(onClick = { refresh++ }) { DeviceText("Refresh") }
        androidx.compose.foundation.text.selection.SelectionContainer {
            LazyColumn(contentPadding = PaddingValues(16.dp)) {
                item {
                    DeviceText(
                        logs,
                        fontSize =
                            androidx.compose.ui.unit.TextUnit(
                                11f,
                                androidx.compose.ui.unit.TextUnitType.Sp,
                            ),
                    )
                }
            }
        }
    }
}
