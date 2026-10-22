package com.abanana.abattery.presentation.device

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.abanana.abattery.diagnostics.DiagnosticResults

@Composable
fun DiagnosticResultBar(test: String) {
    val context = LocalContext.current
    var status by remember(test) { mutableStateOf(DiagnosticResults.results(context)[test]) }
    Surface(tonalElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            DeviceText(
                status?.let { "Your observation: $it" }
                    ?: "Record your observation after running the test",
                style = MaterialTheme.typography.labelSmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Pass", "Fail", "Not tested").forEach { value ->
                    FilterChip(
                        status == value,
                        {
                            DiagnosticResults.record(context, test, value)
                            status = value
                        },
                        { DeviceText(value) },
                    )
                }
            }
        }
    }
}
