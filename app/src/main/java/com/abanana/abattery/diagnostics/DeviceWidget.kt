package com.abanana.abattery.diagnostics

import android.app.ActivityManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.widget.RemoteViews
import com.abanana.abattery.R
import com.abanana.abattery.data.device.formatBytes

open class DeviceWidget : AppWidgetProvider() {
    protected open val kind = "Dashboard"

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val memory =
            ActivityManager.MemoryInfo().also {
                context.getSystemService(ActivityManager::class.java).getMemoryInfo(it)
            }
        val battery =
            context.registerReceiver(
                null,
                android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            )
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)?.takeIf { it >= 0 }
        val temperature =
            battery
                ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                ?.takeUnless { it == Int.MIN_VALUE }
                ?.div(10f)
        val disk = StatFs(Environment.getDataDirectory().path)
        val values =
            when (kind) {
                "Battery" ->
                    "Battery ${level?.let { "$it%" } ?: "N/A"}\n${temperature?.let { "$it °C" } ?: "N/A"}\nTap to refresh"
                "Storage" ->
                    "RAM ${formatBytes(memory.totalMem-memory.availMem)} / ${formatBytes(memory.totalMem)}\nStorage ${formatBytes(disk.totalBytes-disk.availableBytes)} / ${formatBytes(disk.totalBytes)}\nTap to refresh"
                else ->
                    "${android.os.Build.MODEL}\nBattery ${level?.let { "$it%" } ?: "N/A"} · ${temperature?.let { "$it °C" } ?: "N/A"}\nRAM ${formatBytes(memory.totalMem-memory.availMem)} / ${formatBytes(memory.totalMem)}\nTap to refresh"
            }
        ids.forEach { id ->
            val refresh =
                PendingIntent.getBroadcast(
                    context,
                    id,
                    Intent(context, javaClass)
                        .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(id)),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            val views =
                RemoteViews(context.packageName, R.layout.device_widget).apply {
                    setTextViewText(R.id.widget_title, "abCheck · $kind")
                    setTextViewText(R.id.widget_values, values)
                    setOnClickPendingIntent(
                        R.id.widget_title,
                        PendingIntent.getActivity(
                            context,
                            id,
                            Intent(
                                context,
                                com.abanana.abattery.presentation.main.MainActivity::class.java,
                            ),
                            PendingIntent.FLAG_IMMUTABLE,
                        ),
                    )
                    setOnClickPendingIntent(R.id.widget_values, refresh)
                }
            manager.updateAppWidget(id, views)
        }
    }
}

class BatteryWidget : DeviceWidget() {
    override val kind = "Battery"
}

class StorageWidget : DeviceWidget() {
    override val kind = "Storage"
}
