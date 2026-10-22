package com.abanana.abattery.diagnostics

import android.graphics.*
import com.abanana.abattery.data.device.DeviceSnapshot
import java.io.OutputStream

object BitmapReport {
    fun write(snapshot: DeviceSnapshot, output: OutputStream) {
        val bitmap = Bitmap.createBitmap(1080, 1200, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.rgb(19, 27, 22))
            val paint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(136, 217, 130)
                    textSize = 48f
                    typeface = Typeface.create("sans-serif", Typeface.BOLD)
                }
            canvas.drawText("abCheck · Device report", 64f, 100f, paint)
            paint.textSize = 28f
            paint.color = Color.WHITE
            paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            val fields =
                snapshot.pages["System"]
                    .orEmpty()
                    .take(2)
                    .flatMap { it.fields }
                    .filter {
                        it.label in
                            listOf(
                                "Manufacturer",
                                "Model",
                                "Version",
                                "API level",
                                "Security patch",
                                "Build",
                            )
                    }
            val lines =
                fields.map { "${it.label}: ${it.value}" } +
                    listOf(
                        "RAM: ${com.abanana.abattery.data.device.formatBytes(snapshot.ramTotal)}",
                        "Storage: ${com.abanana.abattery.data.device.formatBytes(snapshot.storageTotal)}",
                        "Generated: ${java.util.Date()}",
                        "Local device readings · availability varies",
                    )
            var y = 200f
            for (line in lines) {
                var rest = line
                while (rest.isNotEmpty()) {
                    val n = paint.breakText(rest, true, 950f, null).coerceAtLeast(1)
                    canvas.drawText(rest.take(n), 64f, y, paint)
                    rest = rest.drop(n)
                    y += 48f
                }
            }
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        } finally {
            bitmap.recycle()
        }
    }
}
