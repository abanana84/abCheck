package com.abanana.abattery.presentation.device

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.abanana.abattery.diagnostics.DeviceWidget

@Composable
fun WidgetScreen() {
    val context = LocalContext.current
    val manager = AppWidgetManager.getInstance(context)
    var kind by remember { mutableStateOf("Dashboard") }
    val provider =
        when (kind) {
            "Battery" -> com.abanana.abattery.diagnostics.BatteryWidget::class.java
            "Storage" -> com.abanana.abattery.diagnostics.StorageWidget::class.java
            else -> DeviceWidget::class.java
        }
    var message by remember {
        mutableStateOf(
            "Add a resizable device dashboard to your home screen. Data updates every 30 minutes or when you tap Refresh."
        )
    }
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeading("Home screen widget")
        DeviceText(message)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Dashboard", "Battery", "Storage").forEach { name ->
                FilterChip(kind == name, { kind = name }, { DeviceText(name) })
            }
        }
        Button(
            onClick = {
                if (manager.isRequestPinAppWidgetSupported)
                    manager.requestPinAppWidget(ComponentName(context, provider), null, null)
                else
                    message =
                        "Your launcher does not support pinning. Long-press the home screen, choose Widgets, then abCheck."
            }
        ) {
            DeviceText("Add dashboard widget")
        }
        OutlinedButton(
            onClick = {
                val ids = manager.getAppWidgetIds(ComponentName(context, provider))
                context.sendBroadcast(
                    Intent(context, provider)
                        .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                )
                message = "Refreshed ${ids.size} widgets"
            }
        ) {
            DeviceText("Refresh widgets")
        }
    }
}
