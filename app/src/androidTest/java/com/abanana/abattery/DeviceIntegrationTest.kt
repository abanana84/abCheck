package com.abanana.abattery

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import com.abanana.abattery.data.device.*
import com.abanana.abattery.diagnostics.*
import com.abanana.abattery.presentation.main.MainActivity
import java.io.File
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    @get:Rule
    val failureCapture =
        object : org.junit.rules.TestWatcher() {
            override fun failed(e: Throwable?, description: org.junit.runner.Description) {
                val dir =
                    File("/sdcard/Android/media/${context.packageName}/additional_test_output")
                        .apply { mkdirs() }
                device.takeScreenshot(File(dir, "${description.methodName}.png"))
                runCatching {
                    device.dumpWindowHierarchy(File(dir, "${description.methodName}.xml"))
                }
            }
        }

    @Before
    fun launch() {
        instrumentation.runOnMainSync {
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                androidx.core.os.LocaleListCompat.forLanguageTags("en")
            )
        }
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        assertTrue(device.wait(Until.hasObject(By.text("CPU status")), 10000))
    }

    private fun click(text: String) {
        repeat(8) {
            device.waitForIdle(1000)
            if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
            val target = device.findObject(By.text(text))
            if (
                target != null &&
                    target.visibleBounds.top > 350 &&
                    target.visibleBounds.bottom <
                        device.displayHeight -
                            (if (text in listOf("Pass", "Fail", "Not tested")) 80 else 150)
            ) {
                target.click()
                device.waitForIdle(1000)
                android.os.SystemClock.sleep(200)
                if (android.os.Build.VERSION.SDK_INT >= 33)
                    instrumentation.uiAutomation.clearCache()
                if (
                    (text in com.abanana.abattery.presentation.device.testNames &&
                        device.hasObject(By.text("Open test"))) ||
                        (text in com.abanana.abattery.presentation.device.toolNames &&
                            device.hasObject(By.text("Open tool")))
                ) {
                    device.findObject(By.text(text))?.click()
                    device.waitForIdle(1000)
                }
                if (android.os.Build.VERSION.SDK_INT >= 33)
                    instrumentation.uiAutomation.clearCache()
                return
            }
            device.swipe(600, 2300, 600, 900, 25)
            android.os.SystemClock.sleep(350)
        }
        fail("Missing UI action: $text")
    }

    private fun backHome() {
        repeat(4) {
            if (device.hasObject(By.text("CPU status"))) return
            device.pressBack()
            device.waitForIdle(1000)
            if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
        }
        assertTrue(device.wait(Until.hasObject(By.text("CPU status")), 3000))
    }

    private fun openTest(name: String) {
        click("Tests")
        click(name)
    }

    private fun openTool(name: String) {
        click("Tools")
        click(name)
    }

    @Test
    fun cpuFrequencyMatchesKernelDriver() {
        val cores = DeviceRepository(context).cores()
        assertTrue(cores.isNotEmpty())
        cores.forEach { core ->
            val path = "/sys/devices/system/cpu/cpu${core.id}/cpufreq"
            val driver = runCatching { File("$path/scaling_driver").readText().trim() }.getOrNull()
            if (driver == "dummy") {
                assertEquals("Virtual CPU", core.status)
                assertNull(core.currentKhz)
                assertTrue(device.wait(Until.hasObject(By.text("Virtual CPU")), 5000))
            } else {
                core.currentKhz?.let { assertTrue(it > 0) }
            }
        }
    }

    @Test
    fun dashboardAndHardwareShowRealValues() {
        click("Hardware")
        assertTrue(device.wait(Until.hasObject(By.text("Processor")), 3000))
        assertTrue(device.hasObject(By.text("Hardware")))
    }

    @Test
    fun batteryExistingScreenStillWorks() {
        click("Battery")
        assertFalse(device.hasObject(By.text("CPU status")))
        assertTrue(device.hasObject(By.pkg(context.packageName)))
        backHome()
    }

    @Test
    fun systemSearchWorks() {
        click("System")
        device.findObject(By.desc("Search information")).click()
        val input = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 3000)!!
        input.text = "Security patch"
        device.waitForIdle(1000)
        if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
        assertTrue(device.wait(Until.hasObject(By.text("Security patch")), 2000))
        assertTrue(device.wait(Until.gone(By.text("Bootloader")), 3000))
    }

    @Test
    fun displayAndBrightnessExitCleanly() {
        openTest("Display test")
        assertTrue(device.hasObject(By.textContains("Tap to cycle colors")))
        device.click(600, 1500)
        backHome()
        openTest("Brightness")
        assertTrue(device.hasObject(By.textContains("Tap to cycle colors")))
        backHome()
    }

    @Test
    fun multitouchTracksAndClearsPointers() {
        openTest("Multitouch")
        assertTrue(device.hasObject(By.textContains("Touch points: 0")))
        device.click(400, 1200)
        assertTrue(device.hasObject(By.textContains("Touch points: 0")))
        backHome()
    }

    @Test
    fun buttonsCaptureKeyEvents() {
        openTest("Buttons")
        device.pressKeyCode(android.view.KeyEvent.KEYCODE_VOLUME_DOWN)
        assertTrue(device.wait(Until.hasObject(By.textContains("KEYCODE_VOLUME_DOWN")), 2000))
        backHome()
    }

    @Test
    fun cameraPermissionAndPreviewDoNotCrash() {
        device.executeShellCommand("pm grant ${context.packageName} android.permission.CAMERA")
        openTest("Camera test")
        assertTrue(device.wait(Until.hasObject(By.textContains("Live preview")), 5000))
        device.pressHome()
        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        assertTrue(device.wait(Until.hasObject(By.textContains("Live preview")), 5000))
        backHome()
    }

    @Test
    fun microphoneRecordsStopsAndPlays() {
        device.executeShellCommand(
            "pm grant ${context.packageName} android.permission.RECORD_AUDIO"
        )
        openTest("Microphone")
        click("Start recording")
        assertTrue(device.wait(Until.hasObject(By.text("Stop recording")), 3000))
        click("Stop recording")
        click("Play recording")
        assertFalse(device.hasObject(By.textContains("failed")))
        backHome()
        assertTrue(context.cacheDir.listFiles().orEmpty().none { it.name.startsWith("mic-test") })
    }

    @Test
    fun sensorReadingsRender() {
        openTest("Accelerometer")
        assertTrue(device.wait(Until.hasObject(By.text("Live sensor readings")), 3000))
        assertTrue(device.hasObject(By.textContains("android.sensor.accelerometer")))
        assertTrue(device.wait(Until.hasObject(By.textContains("X:")), 3000))
        backHome()
    }

    @Test
    fun restrictedToolsShowHonestScope() {
        openTool("Task manager")
        click("Android access")
        assertTrue(
            device.hasObject(By.textContains("System-wide management requires privileged access"))
        )
        backHome()
        openTool("Shizuku diagnostics")
        assertTrue(device.hasObject(By.textContains("Shizuku:")))
        backHome()
    }

    @Test
    fun openglContextReadsRenderer() {
        openTool("OpenGL info")
        assertTrue(device.wait(Until.hasObject(By.text("Renderer")), 5000))
        backHome()
    }

    @Test
    fun widgetPinScreenOpens() {
        click("Widgets")
        assertTrue(device.wait(Until.hasObject(By.text("Add dashboard widget")), 2000))
        backHome()
    }

    @Test
    fun repositorySurvivesRestrictedFields() {
        val data = DeviceRepository(context).snapshot()
        assertTrue(data.ramTotal > 0)
        assertTrue(data.storageTotal > 0)
        assertEquals(setOf("Hardware", "System", "Network", "Camera", "Sensors"), data.pages.keys)
        assertTrue(DeviceRepository(context).apps().any { it.packageName == context.packageName })
        assertTrue(data.pages["Hardware"].orEmpty().isNotEmpty())
    }

    @Test
    fun reportFormatsAreValid() {
        val data = DeviceRepository(context).snapshot()
        for (format in listOf("Text", "JSON", "HTML", "PDF", "PNG")) {
            val file = File(context.cacheDir, "qa-report-$format")
            try {
                ReportExporter.write(context, Uri.fromFile(file), data, format)
                assertTrue(file.length() > 100)
                if (format == "JSON")
                    assertEquals("abCheck", JSONObject(file.readText()).getString("generator"))
                if (format == "PDF")
                    assertEquals(
                        "%PDF",
                        file.inputStream().use {
                            val b = ByteArray(4)
                            it.read(b)
                            String(b)
                        },
                    )
                if (format == "PNG") {
                    val bitmap = BitmapFactory.decodeFile(file.path)
                    assertEquals(1080, bitmap.width)
                    bitmap.recycle()
                }
                if (format == "HTML") assertTrue(file.readText().contains("<meta charset="))
            } finally {
                file.delete()
            }
        }
    }

    @Test
    fun benchmarkProducesMeasuredResultsAndCleansFiles() = runBlocking {
        val engine = BenchmarkEngine(context)
        val result = withContext(Dispatchers.Default) { engine.run {} }
        assertTrue(result.cpu!! > 0)
        assertTrue(result.memory!! > 0)
        assertTrue(result.read!! > 0)
        assertTrue(result.write!! > 0)
        assertNotNull(result.gpuFps)
        assertTrue(result.gpuFps!! > 0)
        engine.save(result)
        assertEquals(result.timestamp, engine.history().first().timestamp)
        assertTrue(context.cacheDir.listFiles().orEmpty().none { it.name.startsWith("benchmark-") })
    }

    @Test
    fun cancelledBenchmarkDoesNotPublishHistory() = runBlocking {
        val engine = BenchmarkEngine(context)
        val before = engine.history().size
        val job = launch(Dispatchers.Default) { engine.run {} }
        delay(100)
        job.cancelAndJoin()
        assertEquals(before, engine.history().size)
        assertTrue(context.cacheDir.listFiles().orEmpty().none { it.name.startsWith("benchmark-") })
    }

    @Test
    fun batteryRecordingStartsStopsAndKeepsLocalHistory() {
        click("Battery monitor")
        val before = BatteryHistory.load(context).size
        click("Start recording")
        val end = android.os.SystemClock.elapsedRealtime() + 5000
        while (
            BatteryHistory.load(context).size <= before &&
                android.os.SystemClock.elapsedRealtime() < end
        ) android.os.SystemClock.sleep(100)
        click("Stop")
        assertTrue(BatteryHistory.load(context).size > before)
        assertTrue(BatteryHistory.load(context).size <= 1440)
        assertTrue(
            device
                .executeShellCommand("dumpsys activity services ${context.packageName}")
                .contains("MonitorService")
                .not()
        )
        backHome()
    }

    @Test
    fun overlayStartsCanRestartAndStops() {
        device.executeShellCommand("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
        click("Monitors")
        click("Start overlay")
        assertTrue(device.wait(Until.hasObject(By.descContains("Floating abCheck monitor")), 5000))
        click("Start overlay")
        assertTrue(device.hasObject(By.descContains("Floating abCheck monitor")))
        click("Stop")
        assertTrue(device.wait(Until.gone(By.descContains("Floating abCheck monitor")), 3000))
        backHome()
    }

    @Test
    fun tabsSupportHorizontalSwipe() {
        device.swipe(1000, 1400, 200, 1400, 30)
        assertTrue(device.wait(Until.hasObject(By.text("Processor")), 3000))
        backHome()
    }

    @Test
    fun observationsPersistAndAreExplicitlyManual() {
        openTest("Vibration")
        click("Pass")
        assertEquals("Pass", DiagnosticResults.results(context)["Vibration"])
        val report = DeviceRepository(context).snapshot().withObservations(context)
        assertEquals(
            "Pass",
            report.pages["User observations"]!!
                .first()
                .fields
                .first { it.label == "Vibration" }
                .value,
        )
        backHome()
    }

    @Test
    fun customBenchmarkDoesNotInventUnselectedMeasurements() = runBlocking {
        val result =
            withContext(Dispatchers.Default) {
                BenchmarkEngine(context).run(setOf(BenchmarkWorkload.STORAGE)) {}
            }
        assertNull(result.cpu)
        assertNull(result.memory)
        assertNull(result.gpuFps)
        assertTrue(result.read!! > 0)
        assertTrue(result.write!! > 0)
    }

    @Test
    fun permissionsGateCanBeDeclinedWithoutLosingOtherTools() {
        openTool("GPS")
        assertTrue(device.wait(Until.hasObject(By.text("Grant permission")), 3000))
        click("Grant permission")
        val deny =
            device.wait(
                Until.findObject(
                    By.res("com.android.permissioncontroller:id/permission_deny_button")
                ),
                3000,
            )
        assertNotNull(deny)
        deny!!.click()
        assertTrue(device.wait(Until.hasObject(By.text("Open app settings")), 3000))
        backHome()
        openTest("Multitouch")
        assertTrue(device.hasObject(By.textContains("Touch points:")))
        backHome()
    }

    @Test
    fun exportedPdfRendersOnAndroid() {
        val file = File(context.cacheDir, "qa-render.pdf")
        try {
            ReportExporter.write(
                context,
                Uri.fromFile(file),
                DeviceRepository(context).snapshot(),
                "PDF",
            )
            android.graphics.pdf
                .PdfRenderer(
                    android.os.ParcelFileDescriptor.open(
                        file,
                        android.os.ParcelFileDescriptor.MODE_READ_ONLY,
                    )
                )
                .use { renderer ->
                    assertTrue(renderer.pageCount > 0)
                    renderer.openPage(0).use { page ->
                        val bitmap =
                            android.graphics.Bitmap.createBitmap(
                                page.width,
                                page.height,
                                android.graphics.Bitmap.Config.ARGB_8888,
                            )
                        try {
                            page.render(
                                bitmap,
                                null,
                                null,
                                android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                            )
                            val pixels = IntArray(bitmap.width * bitmap.height)
                            bitmap.getPixels(
                                pixels,
                                0,
                                bitmap.width,
                                0,
                                0,
                                bitmap.width,
                                bitmap.height,
                            )
                            assertTrue(
                                pixels.any {
                                    android.graphics.Color.alpha(it) > 0 &&
                                        android.graphics.Color.red(it) < 128
                                }
                            )
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
        } finally {
            file.delete()
        }
    }
}
