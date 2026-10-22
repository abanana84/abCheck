package com.abanana.abattery.data.device

import android.content.Context
import com.abanana.abattery.diagnostics.DiagnosticResults

fun DeviceSnapshot.withObservations(context: Context): DeviceSnapshot {
    val fields = DiagnosticResults.results(context).map { DeviceField(it.key, it.value) }
    return if (fields.isEmpty()) this
    else
        copy(
            pages =
                pages +
                    ("User observations" to
                        listOf(DeviceSection("Manually recorded test results", fields)))
        )
}
