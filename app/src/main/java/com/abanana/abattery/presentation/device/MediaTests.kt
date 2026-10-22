package com.abanana.abattery.presentation.device

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.MediaRecorder
import android.os.*
import android.view.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import kotlinx.coroutines.delay

@Composable
fun CameraTest() {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(CameraManager::class.java) }
    val ids = remember { runCatching { manager.cameraIdList.toList() }.getOrDefault(emptyList()) }
    var selected by remember { mutableStateOf(ids.firstOrNull()) }
    var error by remember { mutableStateOf<String?>(null) }
    var device by remember { mutableStateOf<CameraDevice?>(null) }
    var session by remember { mutableStateOf<CameraCaptureSession?>(null) }
    var previewSurface by remember { mutableStateOf<Surface?>(null) }
    var previewTexture by remember { mutableStateOf<SurfaceTexture?>(null) }
    val previewSize =
        remember(selected) {
            runCatching {
                    selected?.let {
                        manager
                            .getCameraCharacteristics(it)
                            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                            ?.getOutputSizes(SurfaceTexture::class.java)
                            ?.filter { size -> size.width <= 1920 && size.height <= 1080 }
                            ?.maxByOrNull { size -> size.width * size.height }
                    }
                }
                .getOrNull() ?: android.util.Size(640, 480)
        }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    var resumed by remember {
        mutableStateOf(lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycle) {
        val observer =
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) resumed = true
                else if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) resumed = false
            }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(selected, previewSurface, resumed, previewSize) {
        var disposed = false
        val surface = previewSurface
        if (
            selected != null &&
                surface != null &&
                resumed &&
                context.checkSelfPermission(Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        ) {
            runCatching {
                    previewTexture?.setDefaultBufferSize(previewSize.width, previewSize.height)
                    manager.openCamera(
                        selected!!,
                        object : CameraDevice.StateCallback() {
                            override fun onOpened(camera: CameraDevice) {
                                if (disposed) {
                                    camera.close()
                                    return
                                }
                                device = camera
                                camera.createCaptureSession(
                                    listOf(surface),
                                    object : CameraCaptureSession.StateCallback() {
                                        override fun onConfigured(s: CameraCaptureSession) {
                                            if (disposed) {
                                                s.close()
                                                return
                                            }
                                            session = s
                                            runCatching {
                                                    s.setRepeatingRequest(
                                                        camera
                                                            .createCaptureRequest(
                                                                CameraDevice.TEMPLATE_PREVIEW
                                                            )
                                                            .apply {
                                                                addTarget(surface)
                                                                set(
                                                                    CaptureRequest.CONTROL_MODE,
                                                                    CaptureRequest.CONTROL_MODE_AUTO,
                                                                )
                                                            }
                                                            .build(),
                                                        null,
                                                        Handler(Looper.getMainLooper()),
                                                    )
                                                }
                                                .onFailure { error = it.message }
                                        }

                                        override fun onConfigureFailed(s: CameraCaptureSession) {
                                            error = "Camera preview configuration failed"
                                        }
                                    },
                                    Handler(Looper.getMainLooper()),
                                )
                            }

                            override fun onDisconnected(camera: CameraDevice) {
                                camera.close()
                                error = "Camera disconnected"
                            }

                            override fun onError(camera: CameraDevice, code: Int) {
                                camera.close()
                                error = "Camera error $code"
                            }
                        },
                        Handler(Looper.getMainLooper()),
                    )
                }
                .onFailure { error = it.message }
        }
        onDispose {
            disposed = true
            session?.close()
            device?.close()
            session = null
            device = null
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ids.forEach { id ->
                FilterChip(
                    selected == id,
                    {
                        error = null
                        selected = id
                    },
                    { DeviceText("Camera $id") },
                )
            }
        }
        DeviceText(error ?: "Live preview · camera images are not saved", Modifier.padding(12.dp))
        AndroidView(
            factory = {
                TextureView(it).apply {
                    surfaceTextureListener =
                        object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(
                                texture: SurfaceTexture,
                                width: Int,
                                height: Int,
                            ) {
                                previewTexture = texture
                                texture.setDefaultBufferSize(previewSize.width, previewSize.height)
                                previewSurface = Surface(texture)
                            }

                            override fun onSurfaceTextureSizeChanged(
                                texture: SurfaceTexture,
                                width: Int,
                                height: Int,
                            ) {}

                            override fun onSurfaceTextureDestroyed(
                                texture: SurfaceTexture
                            ): Boolean {
                                previewSurface?.release()
                                previewSurface = null
                                previewTexture = null
                                return true
                            }

                            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
                        }
                }
            },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
}

@Composable
fun MicrophoneTest() {
    val context = LocalContext.current
    var recording by remember { mutableStateOf(false) }
    var amplitude by remember { mutableIntStateOf(0) }
    var message by remember {
        mutableStateOf(
            "Ready. Recording stays in a temporary local file and is deleted when you leave this test."
        )
    }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var temp by remember { mutableStateOf<File?>(null) }
    var player by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    fun stop() {
        if (recording) runCatching { recorder?.stop() }
        recorder?.release()
        recorder = null
        recording = false
    }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer =
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                    stop()
                    player?.release()
                    player = null
                }
            }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            stop()
            player?.release()
            temp?.delete()
        }
    }
    LaunchedEffect(recording) {
        while (recording) {
            amplitude = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
            delay(100)
        }
    }
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeading("Microphone loopback test")
        DeviceText(message)
        DeviceText("Amplitude: $amplitude", style = MaterialTheme.typography.headlineMedium)
        LinearProgressIndicator(
            progress = { amplitude / 32767f },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                if (recording) {
                    stop()
                    message = "Stopped. Play back to check microphone quality."
                } else
                    runCatching {
                            player?.release()
                            player = null
                            temp?.delete()
                            val file = File.createTempFile("mic-test", ".m4a", context.cacheDir)
                            temp = file
                            recorder =
                                (if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context)
                                    else MediaRecorder())
                                    .apply {
                                        setAudioSource(MediaRecorder.AudioSource.MIC)
                                        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                        setOutputFile(file.absolutePath)
                                        setMaxDuration(30000)
                                        setOnInfoListener { _, what, _ ->
                                            if (
                                                what ==
                                                    MediaRecorder
                                                        .MEDIA_RECORDER_INFO_MAX_DURATION_REACHED
                                            ) {
                                                stop()
                                                message = "30-second recording complete"
                                            }
                                        }
                                        prepare()
                                        start()
                                    }
                            recording = true
                            message = "Recording · maximum 30 seconds"
                        }
                        .onFailure {
                            stop()
                            temp?.delete()
                            message = "Recording failed: ${it.message}"
                        }
            }
        ) {
            DeviceText(if (recording) "Stop recording" else "Start recording")
        }
        if (!recording && temp?.exists() == true)
            Button(
                onClick = {
                    runCatching {
                            player?.release()
                            player =
                                android.media.MediaPlayer().apply {
                                    setDataSource(temp!!.path)
                                    prepare()
                                    start()
                                }
                        }
                        .onFailure { message = "Playback failed: ${it.message}" }
                }
            ) {
                DeviceText("Play recording")
            }
    }
}

@Composable
fun MultitouchTest() {
    var pointers by remember { mutableStateOf<Map<Long, Offset>>(emptyMap()) }
    Column(Modifier.fillMaxSize()) {
        DeviceText(
            "Touch points: ${pointers.size} · use multiple fingers",
            Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleLarge,
        )
        Canvas(
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            pointers =
                                event.changes
                                    .filter { it.pressed }
                                    .associate { it.id.value to it.position }
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
        ) {
            pointers.forEach { (id, point) ->
                drawCircle(Color.hsl((id * 67 % 360).toFloat(), .7f, .5f), 60f, point)
                drawCircle(Color.White, 8f, point)
            }
        }
    }
}

object KeyDiagnostics {
    val lastKey = kotlinx.coroutines.flow.MutableStateFlow("Waiting for a volume key")
}

@Composable
fun ButtonTest() {
    val key by KeyDiagnostics.lastKey.collectAsState()
    DeviceText(key, style = MaterialTheme.typography.headlineMedium)
}

@Composable
fun ChargingTest() {
    val context = LocalContext.current
    var state by remember { mutableStateOf("Waiting for battery status") }
    DisposableEffect(Unit) {
        fun update(intent: Intent?) {
            if (intent != null) {
                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                state =
                    "Plugged: ${when(plugged) { 0 -> "No"
 1 -> "AC"
 2 -> "USB"
 4 -> "Wireless"
 else -> plugged.toString() }}\nStatus: ${when(status) { 2 -> "Charging"
 3 -> "Discharging"
 4 -> "Not charging"
 5 -> "Full"
 else -> "Unknown" }}\nLevel: ${intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)}%"
            }
        }
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    update(intent)
                }
            }
        update(context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
        onDispose { context.unregisterReceiver(receiver) }
    }
    DeviceText(state, style = MaterialTheme.typography.headlineSmall)
}
