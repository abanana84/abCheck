package com.abanana.abattery.diagnostics

import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.TrafficStats
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.TextView
import com.abanana.abattery.data.device.*
import com.abanana.abattery.presentation.main.MainActivity
import java.io.File
import java.util.concurrent.Executors

val monitorMetrics =
    listOf("CPU frequency", "RAM", "Battery", "Temperature", "Network", "GPU frequency")

class MonitorService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var panel: TextView? = null
    private var lastSample = 0L
    private var previousRx = -1L
    private var previousTx = -1L
    private var previousTime = 0L
    private var destroyed = false
    private var taskRunning = false
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var windows: WindowManager
    private val refresh =
        object : Runnable {
            override fun run() {
                if (taskRunning || destroyed) return
                taskRunning = true
                worker.execute {
                    val text =
                        runCatching {
                                val info =
                                    ActivityManager.MemoryInfo().also {
                                        getSystemService(ActivityManager::class.java)
                                            .getMemoryInfo(it)
                                    }
                                val battery =
                                    registerReceiver(
                                        null,
                                        IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                                    )
                                val level =
                                    battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)?.takeIf {
                                        it >= 0
                                    }
                                val temperature =
                                    battery
                                        ?.getIntExtra(
                                            BatteryManager.EXTRA_TEMPERATURE,
                                            Int.MIN_VALUE,
                                        )
                                        ?.takeUnless { it == Int.MIN_VALUE }
                                        ?.div(10f)
                                val cpu =
                                    runCatching {
                                            File(
                                                    "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq"
                                                )
                                                .readText()
                                                .trim()
                                                .toLong()
                                                .takeIf { it >= 1000 }
                                                ?.div(1000)
                                        }
                                        .getOrNull()
                                val gpu =
                                    runCatching {
                                            File("/sys/class/kgsl/kgsl-3d0/gpuclk")
                                                .readText()
                                                .trim()
                                                .toLong()
                                                .takeIf { it > 0 }
                                                ?.div(1000000)
                                        }
                                        .getOrNull()
                                val now = SystemClock.elapsedRealtime()
                                val rx = TrafficStats.getTotalRxBytes()
                                val tx = TrafficStats.getTotalTxBytes()
                                val rxRate = counterRate(previousRx, rx, now - previousTime)
                                val txRate = counterRate(previousTx, tx, now - previousTime)
                                previousRx = rx
                                previousTx = tx
                                previousTime = now
                                val selected =
                                    getSharedPreferences("monitor", 0)
                                        .getStringSet(
                                            "metrics",
                                            setOf("CPU frequency", "RAM", "Battery"),
                                        )!!
                                val readings =
                                    linkedMapOf(
                                        "CPU frequency" to "CPU ${cpu?.let { "$it MHz" } ?: "N/A"}",
                                        "RAM" to "RAM ${formatBytes(info.totalMem-info.availMem)}",
                                        "Battery" to "Battery ${level?.let { "$it%" } ?: "N/A"}",
                                        "Temperature" to
                                            "Temp ${temperature?.let { "$it °C" } ?: "N/A"}",
                                        "Network" to
                                            "↓ ${rxRate?.let { formatBytes(it) } ?: "—"}/s  ↑ ${txRate?.let { formatBytes(it) } ?: "—"}/s",
                                        "GPU frequency" to "GPU ${gpu?.let { "$it MHz" } ?: "N/A"}",
                                    )
                                if (now - lastSample > 60000 || lastSample == 0L) {
                                    lastSample = now
                                    BatteryHistory.append(applicationContext)
                                }
                                "abCheck     ×\n" +
                                    readings.filterKeys { it in selected }.values.joinToString("\n")
                            }
                            .getOrElse { "abCheck\nData temporarily unavailable" }
                    handler.post {
                        taskRunning = false
                        if (!destroyed) {
                            panel?.text = text
                            handler.postDelayed(this, 2000)
                        }
                    }
                }
            }
        }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }
        val batteryOnly = intent?.action == "BATTERY"
        if (!batteryOnly && !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(
            NotificationChannel(
                "monitor",
                "Diagnostic monitoring",
                NotificationManager.IMPORTANCE_LOW,
            )
        )
        val stop =
            PendingIntent.getService(
                this,
                2,
                Intent(this, MonitorService::class.java).setAction("STOP"),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val open =
            PendingIntent.getActivity(
                this,
                3,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            Notification.Builder(this, "monitor")
                .setContentTitle(
                    if (batteryOnly) "abCheck battery recording" else "abCheck monitor running"
                )
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentText("Tap to open · Stop to end monitoring")
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(Notification.Action.Builder(null, "Stop", stop).build())
                .build()
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(
                12,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        else startForeground(12, notification)
        handler.removeCallbacks(refresh)
        if (batteryOnly) {
            panel?.let { runCatching { windows.removeView(it) } }
            panel = null
        } else if (panel == null) {
            windows = getSystemService(WindowManager::class.java)
            val params =
                WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT,
                    )
                    .apply {
                        gravity = Gravity.TOP or Gravity.START
                        x = 20
                        y = 160
                    }
            val padding = (12 * resources.displayMetrics.density).toInt()
            panel =
                TextView(this).apply {
                    setTextColor(Color.WHITE)
                    setBackgroundColor(0xE6203026.toInt())
                    textSize = 13f
                    setPadding(padding, padding, padding, padding)
                    contentDescription = "Floating abCheck monitor. Drag to move. Tap to close."
                    var startX = 0f
                    var startY = 0f
                    var windowX = 0
                    var windowY = 0
                    setOnClickListener { stopSelf() }
                    setOnTouchListener { v, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                startX = event.rawX
                                startY = event.rawY
                                windowX = params.x
                                windowY = params.y
                                true
                            }
                            MotionEvent.ACTION_MOVE -> {
                                params.x = windowX + (event.rawX - startX).toInt()
                                params.y = windowY + (event.rawY - startY).toInt()
                                windows.updateViewLayout(v, params)
                                true
                            }
                            MotionEvent.ACTION_UP -> {
                                if (
                                    kotlin.math.abs(event.rawX - startX) < 8 &&
                                        kotlin.math.abs(event.rawY - startY) < 8
                                )
                                    v.performClick()
                                true
                            }
                            else -> false
                        }
                    }
                }
            runCatching { windows.addView(panel, params) }.onFailure { stopSelf() }
        }
        handler.post(refresh)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        destroyed = true
        handler.removeCallbacksAndMessages(null)
        worker.shutdown()
        panel?.let { runCatching { windows.removeView(it) } }
        panel = null
        super.onDestroy()
    }
}
