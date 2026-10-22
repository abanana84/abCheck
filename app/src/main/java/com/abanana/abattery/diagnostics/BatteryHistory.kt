package com.abanana.abattery.diagnostics

import android.content.Context
import android.os.BatteryManager
import org.json.JSONArray
import org.json.JSONObject

data class BatterySample(
    val time: Long,
    val level: Int,
    val temperature: Float,
    val currentUa: Int?,
    val plugged: Boolean,
)

object BatteryHistory {
    fun load(context: Context): List<BatterySample> =
        runCatching {
                val rows =
                    JSONArray(
                        context
                            .getSharedPreferences("battery_history", 0)
                            .getString("samples", "[]")
                    )
                (0 until rows.length()).map { i ->
                    val o = rows.getJSONObject(i)
                    BatterySample(
                        o.getLong("time"),
                        o.getInt("level"),
                        o.getDouble("temperature").toFloat(),
                        if (o.isNull("current")) null else o.getInt("current"),
                        o.getBoolean("plugged"),
                    )
                }
            }
            .getOrDefault(emptyList())

    fun append(context: Context) {
        val intent =
            context.registerReceiver(
                null,
                android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED),
            ) ?: return
        val manager = context.getSystemService(BatteryManager::class.java)
        val current =
            manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW).takeUnless {
                it == Int.MIN_VALUE
            }
        val sample =
            BatterySample(
                System.currentTimeMillis(),
                intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
                intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f,
                current,
                intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0,
            )
        val rows = JSONArray()
        (load(context).takeLast(1439) + sample).forEach {
            rows.put(
                JSONObject()
                    .put("time", it.time)
                    .put("level", it.level)
                    .put("temperature", it.temperature)
                    .put("current", it.currentUa ?: JSONObject.NULL)
                    .put("plugged", it.plugged)
            )
        }
        context
            .getSharedPreferences("battery_history", 0)
            .edit()
            .putString("samples", rows.toString())
            .apply()
    }
}
