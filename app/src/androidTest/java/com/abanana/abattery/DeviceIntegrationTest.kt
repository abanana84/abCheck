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
        if (text in listOf("Dashboard", "Device", "Tests", "Tools")) {
            val heading = if (text == "Dashboard") "abCheck" else text
            if (
                device.findObjects(By.text(heading)).any {
                    it.visibleBounds.top < (device.displayHeight * .14).toInt()
                }
            )
                return
        }
        if (
            text in com.abanana.abattery.presentation.device.deviceTabs.drop(1) &&
                device.hasObject(By.text("CPU status"))
        )
            click("Device")
        repeat(16) { attempt ->
            device.waitForIdle(1000)
            if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
            val rootDestination = text in listOf("Dashboard", "Device", "Tests", "Tools")
            val matches = device.findObjects(By.text(text))
            for (target in
                if (rootDestination) matches.sortedByDescending { it.visibleBounds.top }
                else matches) {
                var action: UiObject2? = target
                var depth = 0
                while (
                    action != null &&
                        !action.isClickable &&
                        !action.isCheckable &&
                        !(rootDestination && action.isSelected) &&
                        depth++ < 4
                ) action = action.parent
                val bounds = action?.visibleBounds
                val contentBottom =
                    device.displayHeight - (90 * context.resources.displayMetrics.density).toInt()
                if (
                    (action?.isClickable == true ||
                        action?.isCheckable == true ||
                        (rootDestination && action?.isSelected == true)) &&
                        bounds != null &&
                        bounds.height() > 0 &&
                        bounds.top >= 0 &&
                        bounds.bottom <
                            (if (rootDestination) device.displayHeight - 20 else contentBottom)
                ) {
                    action.click()
                    android.os.SystemClock.sleep(250)
                    device.waitForIdle(1000)
                    if (android.os.Build.VERSION.SDK_INT >= 33)
                        instrumentation.uiAutomation.clearCache()
                    val destinations =
                        com.abanana.abattery.presentation.device.testNames +
                            com.abanana.abattery.presentation.device.toolNames +
                            com.abanana.abattery.presentation.device.deviceTabs +
                            listOf(
                                "Device",
                                "Battery details",
                                "Monitors",
                                "Widgets",
                                "Battery monitor",
                                "Benchmarks",
                            )
                    if (text in destinations) {
                        val heading = if (text == "Dashboard") "abCheck" else text
                        val reached =
                            device.findObjects(By.text(heading)).any {
                                it.visibleBounds.top < (device.displayHeight * .14).toInt()
                            }
                        if (!reached) {
                            device
                                .findObjects(By.text(text))
                                .firstOrNull { it.visibleBounds.height() > 0 }
                                ?.click()
                            android.os.SystemClock.sleep(300)
                            if (android.os.Build.VERSION.SDK_INT >= 33)
                                instrumentation.uiAutomation.clearCache()
                        }
                    }
                    return
                }
            }
            device.swipe(
                device.displayWidth / 2,
                (device.displayHeight * (if (attempt < 7) .75 else .35)).toInt(),
                device.displayWidth / 2,
                (device.displayHeight * (if (attempt < 7) .35 else .75)).toInt(),
                25,
            )
            android.os.SystemClock.sleep(350)
        }
        fail("Missing UI action: $text")
    }

    private fun backHome() {
        click("Dashboard")
        assertTrue(device.wait(Until.hasObject(By.pkg(context.packageName)), 3000))
        assertTrue(device.wait(Until.hasObject(By.text("abCheck")), 3000))
    }

    private fun openTest(name: String) {
        click("Tests")
        click(name)
    }

    private fun openTool(name: String) {
        click("Tools")
        click(name)
    }

    private fun capture(name: String) {
        android.os.SystemClock.sleep(350)
        device.waitForIdle(1000)
        val dir = File("/sdcard/Android/media/${context.packageName}/ui-refresh").apply { mkdirs() }
        assertTrue(device.takeScreenshot(File(dir, "$name.png")))
        device.dumpWindowHierarchy(File(dir, "$name.xml"))
    }

    @Test
    fun captureRedesignedScreensAndVerifyRoutes() {
        capture("01-dashboard")
        click("Device")
        capture("02-device")
        click("Hardware")
        capture("03-hardware")
        click("Dashboard")
        click("Device")
        click("Battery")
        capture("04-battery")
        click("Battery details")
        assertTrue(device.wait(Until.hasObject(By.desc("Back")), 3000))
        capture("05-battery-details")
        click("Dashboard")
        click("Tests")
        capture("06-tests")
        click("Tools")
        capture("07-tools")
        click("Dashboard")
        click("Device")
        click("Apps")
        capture("08-apps")
        click("System apps")
        capture("09-system-apps")
        click("Dashboard")
        click("Device")
        click("Sensors")
        capture("10-sensors")
        click("Dashboard")
        device.findObject(By.desc("Change theme")).click()
        android.os.SystemClock.sleep(350)
        capture("11-theme")
        device.findObject(By.desc("Change theme")).click()
        android.os.SystemClock.sleep(350)
        capture("12-theme")
        device.findObject(By.desc("Change theme")).click()
    }

    @Test
    fun captureRemainingCategoriesAndAccessibleLayouts() {
        for (name in listOf("System", "Network", "Camera")) {
            click("Device")
            click(name)
            assertTrue(device.hasObject(By.text(name)))
            capture("detail-${name.lowercase()}")
            click("Dashboard")
        }
        val oldFontScale = device.executeShellCommand("settings get system font_scale").trim()
        try {
            device.executeShellCommand("wm size 720x1280")
            device.executeShellCommand("wm density 320")
            device.executeShellCommand("settings put system font_scale 1.3")
            android.os.SystemClock.sleep(700)
            instrumentation.runOnMainSync {
                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                    androidx.core.os.LocaleListCompat.forLanguageTags("vi")
                )
            }
            assertTrue(device.wait(Until.hasObject(By.text("Tổng quan")), 5000))
            capture("small-vi-large-text")
            device.findObject(By.text("Thiết bị")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Thông tin thiết bị")), 3000))
            capture("small-vi-device")
        } finally {
            device.executeShellCommand("wm size reset")
            device.executeShellCommand("wm density reset")
            device.executeShellCommand(
                if (oldFontScale == "null") "settings delete system font_scale"
                else "settings put system font_scale $oldFontScale"
            )
            instrumentation.runOnMainSync {
                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                    androidx.core.os.LocaleListCompat.forLanguageTags("en")
                )
            }
        }
    }

    @Test
    fun captureReleaseScreenInventory() {
        fun save(name: String) = capture("release-" + name.lowercase().replace(" ", "-"))
        save("dashboard")
        click("Device")
        save("device")
        click("Tests")
        save("tests")
        click("Tools")
        save("tools")
        for (name in com.abanana.abattery.presentation.device.deviceTabs.drop(1)) {
            click("Dashboard")
            click("Device")
            click(name)
            save(name)
        }
        click("Dashboard")
        click("Device")
        click("Battery")
        click("Battery details")
        save("battery details")
        device.pressBack()
        for (name in com.abanana.abattery.presentation.device.testNames) {
            backHome()
            openTest(name)
            save(name)
            device.pressBack()
        }
        for (name in
            com.abanana.abattery.presentation.device.toolNames +
                listOf("Monitors", "Battery monitor", "Widgets", "Benchmarks")) {
            backHome()
            openTool(name)
            save(name)
            device.pressBack()
        }
        backHome()
    }

    @Test
    fun captureStoreScreenshots() {
        context.getSharedPreferences("diagnostic_results", 0).edit().clear().commit()
        val kind = InstrumentationRegistry.getArguments().getString("screenshotKind", "phone")
        capture("store-$kind-dashboard")
        for (name in listOf("Hardware", "System", "Battery", "Network", "Camera", "Sensors")) {
            backHome()
            click("Device")
            click(name)
            capture("store-$kind-${name.lowercase()}")
        }
        for (name in listOf("Device", "Tests", "Tools")) {
            click(name)
            capture("store-$kind-${name.lowercase()}")
        }
        backHome()
        device.findObject(By.desc("More options")).click()
        capture("release-menu")
        click("About")
        capture("release-about")
        device.pressBack()
        device.findObject(By.desc("More options")).click()
        click("Language")
        capture("release-language")
        device.pressBack()
        device.findObject(By.desc("More options")).click()
        click("Settings")
        capture("release-settings")
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
        assertTrue(device.hasObject(By.text("Android access")))
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
    fun primaryNavigationAndDeviceCategoriesRemainReachable() {
        click("Device")
        assertTrue(device.wait(Until.hasObject(By.text("Device information")), 3000))
        click("Hardware")
        assertTrue(device.wait(Until.hasObject(By.text("Processor")), 3000))
        click("System")
        assertTrue(device.wait(Until.hasObject(By.text("Android")), 3000))
        click("Dashboard")
        assertTrue(device.wait(Until.hasObject(By.text("CPU status")), 3000))
        click("Tests")
        assertTrue(device.wait(Until.hasObject(By.text("Hardware checks")), 3000))
        click("Tools")
        assertTrue(device.wait(Until.hasObject(By.text("Root check")), 3000))
        backHome()
    }

    @Test
    fun observationsPersistAndAreExplicitlyManual() {
        openTest("Vibration")
        click("Fail")
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
        for (permission in listOf("ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION")) {
            device.executeShellCommand(
                "pm clear-permission-flags ${context.packageName} android.permission.$permission user-set user-fixed"
            )
        }
        openTool("GPS")
        assertTrue(device.wait(Until.hasObject(By.text("Grant permission")), 3000))
        click("Grant permission")
        val deny = device.wait(Until.findObject(By.text("Don’t allow")), 3000)
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
