package com.abanana.abattery.presentation.device

import android.content.*
import android.content.pm.PackageManager
import android.os.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.abanana.abattery.diagnostics.PrivilegedReadService
import kotlinx.coroutines.*
import rikka.shizuku.Shizuku

@Composable
fun PrivilegedScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var alive by remember { mutableStateOf(Shizuku.pingBinder()) }
    var granted by remember {
        mutableStateOf(
            alive &&
                runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }
                    .getOrDefault(false)
        )
    }
    var binder by remember { mutableStateOf<IBinder?>(null) }
    var status by remember {
        mutableStateOf(
            "Start Shizuku on this device to read additional system diagnostics. All available commands are read-only."
        )
    }
    var output by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val args = remember {
        Shizuku.UserServiceArgs(ComponentName(context, PrivilegedReadService::class.java))
            .daemon(false)
            .processNameSuffix("diagnostics")
            .debuggable(false)
            .version(1)
    }
    val connection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                binder = service
                status = "Privileged diagnostics connected"
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                binder = null
                status = "Privileged diagnostics disconnected"
            }
        }
    }
    DisposableEffect(Unit) {
        val received =
            Shizuku.OnBinderReceivedListener {
                alive = true
                granted =
                    runCatching {
                            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                        }
                        .getOrDefault(false)
            }
        val died =
            Shizuku.OnBinderDeadListener {
                alive = false
                granted = false
                binder = null
            }
        val result =
            Shizuku.OnRequestPermissionResultListener { _, value ->
                granted = value == PackageManager.PERMISSION_GRANTED
                status =
                    if (granted) "Permission granted. Connect to read diagnostics."
                    else "Shizuku permission denied"
            }
        Shizuku.addBinderReceivedListenerSticky(received)
        Shizuku.addBinderDeadListener(died)
        Shizuku.addRequestPermissionResultListener(result)
        onDispose {
            Shizuku.removeBinderReceivedListener(received)
            Shizuku.removeBinderDeadListener(died)
            Shizuku.removeRequestPermissionResultListener(result)
            runCatching { Shizuku.unbindUserService(args, connection, true) }
        }
    }
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DeviceText(status)
        DeviceText(
            "Shizuku: ${if(alive) "running" else "unavailable"} · Permission: ${if(granted) "granted" else "not granted"}"
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    runCatching {
                            if (!alive) {
                                val intent =
                                    context.packageManager.getLaunchIntentForPackage(
                                        "moe.shizuku.privileged.api"
                                    )
                                if (intent != null) context.startActivity(intent)
                                else
                                    status =
                                        "Install Shizuku from shizuku.rikka.app, start it, then return here."
                            } else if (!granted) Shizuku.requestPermission(42)
                            else Shizuku.bindUserService(args, connection)
                        }
                        .onFailure { status = "Connection failed: ${it.message}" }
                }
            ) {
                DeviceText(
                    if (!alive) "Open Shizuku" else if (!granted) "Grant access" else "Connect"
                )
            }
        }
        if (binder != null) {
            listOf("Thermal sensors", "All processes", "Device logs", "Battery service")
                .chunked(2)
                .forEachIndexed { row, names ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        names.forEachIndexed { col, name ->
                            OutlinedButton(
                                enabled = !busy,
                                onClick = {
                                    busy = true
                                    scope.launch {
                                        output =
                                            withContext(Dispatchers.IO) {
                                                val request = Parcel.obtain()
                                                val response = Parcel.obtain()
                                                try {
                                                    request.writeInterfaceToken(
                                                        PrivilegedReadService.DESCRIPTOR
                                                    )
                                                    check(
                                                        binder!!.transact(
                                                            row * 2 + col + 1,
                                                            request,
                                                            response,
                                                            0,
                                                        )
                                                    )
                                                    response.readException()
                                                    response.readString().orEmpty()
                                                } catch (e: Exception) {
                                                    "Read failed: ${e.message}"
                                                } finally {
                                                    request.recycle()
                                                    response.recycle()
                                                }
                                            }
                                        busy = false
                                    }
                                },
                            ) {
                                DeviceText(name)
                            }
                        }
                    }
                }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        SelectionContainer(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            DeviceText(output.ifBlank { "No privileged diagnostic data has been requested." })
        }
    }
}
