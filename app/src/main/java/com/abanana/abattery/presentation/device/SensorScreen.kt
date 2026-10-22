package com.abanana.abattery.presentation.device

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Locale

@Composable
fun SensorScreen(sensorType: Int? = null) {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(SensorManager::class.java) }
    val sensors = remember(sensorType) { manager.getSensorList(sensorType ?: Sensor.TYPE_ALL) }
    var selected by remember {
        mutableStateOf<Sensor?>(if (sensorType == null) null else sensors.firstOrNull())
    }
    var values by remember { mutableStateOf(floatArrayOf()) }
    var accuracy by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(selected, lifecycle) {
        values = floatArrayOf()
        val listener =
            object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    values = event.values.copyOf()
                    accuracy = event.accuracy
                }

                override fun onAccuracyChanged(sensor: Sensor?, value: Int) {
                    accuracy = value
                }
            }
        fun register() {
            selected?.let { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) register()
            else if (event == Lifecycle.Event.ON_PAUSE) manager.unregisterListener(listener)
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) register()
        onDispose {
            manager.unregisterListener(listener)
            lifecycle.removeObserver(observer)
        }
    }
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (sensorType != null) item { SectionHeading("Live sensor readings") }
        if (sensors.isEmpty()) item { DeviceText("This sensor is not reported by your device.") }
        items(sensors) { s ->
            val expanded = selected == s
            Surface(
                onClick = { selected = if (expanded && sensorType == null) null else s },
                shape = RoundedCornerShape(16.dp),
                border =
                    androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                    ),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            DeviceText(
                                sensorLabel(s),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.height(6.dp))
                            DeviceText(s.name, fontSize = 14.sp)
                        }
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            "Sensor details",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (expanded) {
                        Spacer(Modifier.height(18.dp))
                        DeviceText(
                            if (values.isEmpty()) "Waiting for sensor events…"
                            else
                                values
                                    .mapIndexed { i, v ->
                                        "${listOf("X", "Y", "Z").getOrElse(i) { "Value $i" }}: ${String.format(Locale.US,"%.4f",v)}"
                                    }
                                    .joinToString("\n"),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(12.dp))
                        DeviceText(
                            "Accuracy $accuracy · ${s.vendor}\n${s.stringType}\nRange ${s.maximumRange} · Resolution ${s.resolution}\nPower ${s.power} mA · Min delay ${s.minDelay} µs",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun sensorLabel(sensor: Sensor): String =
    when (sensor.type) {
        Sensor.TYPE_ACCELEROMETER -> "Accelerometer"
        Sensor.TYPE_GYROSCOPE -> "Gyroscope"
        Sensor.TYPE_MAGNETIC_FIELD -> "Magnetic field"
        Sensor.TYPE_LIGHT -> "Light sensor"
        Sensor.TYPE_PROXIMITY -> "Proximity"
        Sensor.TYPE_AMBIENT_TEMPERATURE -> "Temperature"
        Sensor.TYPE_PRESSURE -> "Pressure"
        Sensor.TYPE_GRAVITY -> "Gravity"
        Sensor.TYPE_LINEAR_ACCELERATION -> "Linear acceleration"
        Sensor.TYPE_ROTATION_VECTOR -> "Rotation vector"
        Sensor.TYPE_RELATIVE_HUMIDITY -> "Humidity"
        else -> sensor.name
    }
