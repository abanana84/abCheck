package com.abanana.abattery.presentation.device

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.*
import android.location.*
import android.net.ConnectivityManager
import android.net.wifi.*
import android.os.*
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.abanana.abattery.data.device.*
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

@SuppressLint("MissingPermission")
@Composable
fun WifiScanScreen() {
    val context = LocalContext.current
    val wifi = remember { context.applicationContext.getSystemService(WifiManager::class.java) }
    var results by remember { mutableStateOf<List<android.net.wifi.ScanResult>>(emptyList()) }
    var status by remember {
        mutableStateOf("Enable Wi-Fi and Location, then scan. Android throttles repeated scans.")
    }
    DisposableEffect(Unit) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    results =
                        runCatching { wifi.scanResults.sortedByDescending { it.level } }
                            .getOrDefault(emptyList())
                    status =
                        if (
                            intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) ==
                                true
                        )
                            "Scan complete"
                        else "Cached results · scan may be throttled"
                }
            }
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { DeviceText(status) }
        item {
            Row {
                Button(
                    onClick = {
                        status =
                            if (wifi.startScan()) "Scanning…"
                            else "Scan rejected by Android. Check Wi-Fi, Location or retry later."
                        results = wifi.scanResults.sortedByDescending { it.level }
                    }
                ) {
                    DeviceText("Scan Wi-Fi")
                }
                TextButton(
                    onClick = { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
                ) {
                    DeviceText("Settings")
                }
            }
        }
        items(results, key = { it.BSSID }) { result ->
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    SectionHeading(result.SSID.ifBlank { "Hidden network" })
                    DeviceText(
                        "${result.BSSID}\n${result.level} dBm · ${result.frequency} MHz\n${result.capabilities}"
                    )
                }
            }
        }
        if (results.isEmpty())
            item {
                DeviceText(
                    "No scan results. Permission, system Location and Wi-Fi must be enabled."
                )
            }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun BluetoothScreen() {
    val context = LocalContext.current
    val adapter = remember { context.getSystemService(BluetoothManager::class.java)?.adapter }
    var devices by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var scanning by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("BLE scan · paired devices are also listed") }
    val callback = remember {
        object : ScanCallback() {
            override fun onScanResult(type: Int, result: android.bluetooth.le.ScanResult) {
                val d = result.device
                devices =
                    devices +
                        (d.address to
                            "${d.name ?: result.scanRecord?.deviceName ?: "Unnamed"} · ${result.rssi} dBm")
            }

            override fun onScanFailed(code: Int) {
                status = "BLE scan failed: $code"
                scanning = false
            }
        }
    }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer =
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                    runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) }
                    scanning = false
                }
            }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) }
        }
    }
    LaunchedEffect(scanning) {
        if (scanning) {
            delay(12000)
            runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) }
            scanning = false
            status = "Scan complete"
        }
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { DeviceText(if (adapter == null) "Bluetooth is not supported" else status) }
        item {
            Row {
                Button(
                    enabled = adapter != null && !scanning,
                    onClick = {
                        runCatching {
                                if (adapter?.isEnabled != true) {
                                    status = "Turn Bluetooth on in system settings"
                                } else {
                                    devices =
                                        adapter.bondedDevices.associate {
                                            it.address to "${it.name ?: "Unnamed"} · paired"
                                        }
                                    adapter.bluetoothLeScanner?.startScan(callback)
                                    scanning = true
                                    status = "Scanning BLE · 12 seconds"
                                }
                            }
                            .onFailure { status = "Bluetooth unavailable: ${it.message}" }
                    },
                ) {
                    DeviceText(if (scanning) "Scanning…" else "Scan Bluetooth")
                }
                TextButton(
                    onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                ) {
                    DeviceText("Settings")
                }
            }
        }
        items(devices.entries.toList(), key = { it.key }) { (address, value) ->
            ActionTile(value, address) {}
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun GpsScreen() {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(LocationManager::class.java) }
    var location by remember { mutableStateOf<Location?>(null) }
    var satellites by remember { mutableStateOf<List<DeviceField>>(emptyList()) }
    var status by remember {
        mutableStateOf("Waiting for GPS fix. For real results, test outdoors on a physical device.")
    }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val listener =
            object : LocationListener {
                override fun onLocationChanged(value: Location) {
                    location = value
                    status = "Location updated"
                }

                override fun onProviderEnabled(provider: String) {
                    status = "GPS enabled"
                }

                override fun onProviderDisabled(provider: String) {
                    status = "GPS disabled"
                }
            }
        val callback =
            object : GnssStatus.Callback() {
                override fun onSatelliteStatusChanged(s: GnssStatus) {
                    satellites =
                        (0 until s.satelliteCount).map {
                            DeviceField(
                                "Satellite ${s.getSvid(it)} · type ${s.getConstellationType(it)}",
                                "${s.getCn0DbHz(it)} dB-Hz · elevation ${s.getElevationDegrees(it)}° · used ${s.usedInFix(it)}",
                            )
                        }
                }
            }
        fun start() {
            runCatching {
                    manager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        1000,
                        0f,
                        listener,
                        Looper.getMainLooper(),
                    )
                    manager.registerGnssStatusCallback(callback, Handler(Looper.getMainLooper()))
                }
                .onFailure { status = "GPS unavailable: ${it.message}" }
        }
        val observer =
            androidx.lifecycle.LifecycleEventObserver { _, e ->
                if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) start()
                else if (e == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                    manager.removeUpdates(listener)
                    manager.unregisterGnssStatusCallback(callback)
                }
            }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) start()
        onDispose {
            lifecycle.removeObserver(observer)
            manager.removeUpdates(listener)
            manager.unregisterGnssStatusCallback(callback)
        }
    }
    val sections =
        listOf(
            DeviceSection(
                "GPS",
                listOf(
                    DeviceField("Status", status),
                    DeviceField("Latitude", location?.latitude?.toString() ?: UNAVAILABLE),
                    DeviceField("Longitude", location?.longitude?.toString() ?: UNAVAILABLE),
                    DeviceField("Accuracy", location?.let { "${it.accuracy} m" } ?: UNAVAILABLE),
                    DeviceField(
                        "Altitude",
                        location?.takeIf { it.hasAltitude() }?.let { "${it.altitude} m" }
                            ?: UNAVAILABLE,
                    ),
                    DeviceField(
                        "Speed",
                        location?.takeIf { it.hasSpeed() }?.let { "${it.speed} m/s" } ?: UNAVAILABLE,
                    ),
                ),
            ),
            DeviceSection("Satellites · ${satellites.size}", satellites),
        )
    Column {
        TextButton(
            onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
        ) {
            DeviceText("Location settings")
        }
        Box(Modifier.weight(1f)) { SectionList(sections) }
    }
}

@Composable
fun NetworkTool(mapper: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf(if (mapper) "" else "example.com") }
    var output by remember { mutableStateOf("Ready") }
    var running by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(Unit) { onDispose { job?.cancel() } }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DeviceText(
            if (mapper)
                "Discover responsive hosts on your current local IPv4 /24 subnet. Devices can block probes, so absence is inconclusive."
            else "DNS lookup and TCP connection timing on port 443. This is not an ICMP ping."
        )
        if (!mapper)
            OutlinedTextField(
                host,
                { host = it },
                Modifier.fillMaxWidth(),
                label = { DeviceText("Hostname or IP") },
                singleLine = true,
            )
        Button(
            enabled = !running,
            onClick = {
                running = true
                output = "Running…"
                job =
                    scope.launch {
                        try {
                            output =
                                withContext(Dispatchers.IO) {
                                    if (!mapper) {
                                        require(host.matches(Regex("[A-Za-z0-9.:-]{1,253}"))) {
                                            "Enter a hostname or IP without a URL scheme"
                                        }
                                        val addresses = InetAddress.getAllByName(host)
                                        addresses.joinToString("\n") { address ->
                                            val start = SystemClock.elapsedRealtime()
                                            runCatching {
                                                    Socket().use {
                                                        it.connect(
                                                            InetSocketAddress(address, 443),
                                                            2500,
                                                        )
                                                    }
                                                    "${address.hostAddress}:443 · ${SystemClock.elapsedRealtime()-start} ms"
                                                }
                                                .getOrElse {
                                                    "${address.hostAddress}: ${it.javaClass.simpleName}"
                                                }
                                        }
                                    } else {
                                        val cm =
                                            context.getSystemService(
                                                ConnectivityManager::class.java
                                            )
                                        val address =
                                            cm.getLinkProperties(cm.activeNetwork)
                                                ?.linkAddresses
                                                ?.firstOrNull {
                                                    it.address is java.net.Inet4Address &&
                                                        it.address.isSiteLocalAddress &&
                                                        it.prefixLength >= 24
                                                }
                                                ?.address
                                                ?.hostAddress
                                                ?: error("No private IPv4 /24 network found")
                                        val prefix = address.substringBeforeLast('.')
                                        val semaphore = Semaphore(16)
                                        coroutineScope {
                                            (1..254)
                                                .map { n ->
                                                    async {
                                                        semaphore.withPermit {
                                                            ensureActive()
                                                            val target = "$prefix.$n"
                                                            if (target == address)
                                                                "$target · this device"
                                                            else {
                                                                val reachable =
                                                                    runCatching {
                                                                            InetAddress.getByName(
                                                                                    target
                                                                                )
                                                                                .isReachable(350)
                                                                        }
                                                                        .getOrDefault(false)
                                                                if (reachable) target else null
                                                            }
                                                        }
                                                    }
                                                }
                                                .awaitAll()
                                                .filterNotNull()
                                                .joinToString("\n")
                                                .ifBlank {
                                                    "No responsive hosts. Firewalls can hide devices."
                                                }
                                        }
                                    }
                                }
                        } catch (e: CancellationException) {
                            output = "Cancelled"
                            throw e
                        } catch (e: Exception) {
                            output = "Failed: ${e.message}"
                        } finally {
                            running = false
                        }
                    }
            },
        ) {
            DeviceText(if (mapper) "Scan local network" else "Check connectivity")
        }
        if (running) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            TextButton(onClick = { job?.cancel() }) { DeviceText("Cancel") }
        }
        androidx.compose.foundation.text.selection.SelectionContainer {
            LazyColumn { item { DeviceText(output) } }
        }
    }
}
