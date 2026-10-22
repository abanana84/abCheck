package com.abanana.abattery.diagnostics

import android.os.Binder
import android.os.Parcel
import java.util.concurrent.TimeUnit

/**
 * Fixed read-only diagnostics exposed only through Shizuku's permission-controlled user service.
 */
class PrivilegedReadService : Binder() {
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == 16777115) {
            kotlin.system.exitProcess(0)
        }
        if (code !in 1..4) return super.onTransact(code, data, reply, flags)
        data.enforceInterface(DESCRIPTOR)
        val command =
            when (code) {
                1 -> listOf("/system/bin/dumpsys", "thermalservice")
                2 -> listOf("/system/bin/ps", "-A", "-o", "PID,UID,NAME")
                3 -> listOf("/system/bin/logcat", "-d", "-t", "150")
                else -> listOf("/system/bin/dumpsys", "battery")
            }
        val result =
            runCatching {
                    val process = ProcessBuilder(command).redirectErrorStream(true).start()
                    val output = StringBuilder()
                    val reader = Thread {
                        process.inputStream.bufferedReader().useLines { lines ->
                            lines.forEach { if (output.length < 60000) output.appendLine(it) }
                        }
                    }
                    reader.start()
                    try {
                        if (!process.waitFor(8, TimeUnit.SECONDS)) {
                            process.destroy()
                            reader.join(1000)
                            "Timed out"
                        } else {
                            reader.join(1000)
                            output.toString()
                        }
                    } finally {
                        process.destroy()
                    }
                }
                .getOrElse { "Unavailable: ${it.message}" }
        reply?.writeNoException()
        reply?.writeString(result)
        return true
    }

    companion object {
        const val DESCRIPTOR = "com.abanana.abattery.PrivilegedRead"
    }
}
