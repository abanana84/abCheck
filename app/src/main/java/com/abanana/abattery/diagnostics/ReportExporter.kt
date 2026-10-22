package com.abanana.abattery.diagnostics

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.abanana.abattery.data.device.DeviceSnapshot
import org.json.JSONArray
import org.json.JSONObject

object ReportExporter {
    fun text(snapshot: DeviceSnapshot): String = buildString {
        appendLine("abCheck · Device report")
        appendLine(java.util.Date().toString())
        appendLine("Some fields depend on Android permissions and manufacturer support.")
        snapshot.pages.forEach { (name, sections) ->
            appendLine("\n$name")
            sections.forEach { s ->
                appendLine("\n${s.title}")
                s.fields.forEach { appendLine("${it.label}: ${it.value}") }
            }
        }
    }

    fun json(snapshot: DeviceSnapshot): String =
        JSONObject()
            .apply {
                put("generator", "abCheck")
                put("generatedAt", System.currentTimeMillis())
                snapshot.pages.forEach { (name, sections) ->
                    put(
                        name,
                        JSONArray().apply {
                            sections.forEach { s ->
                                put(
                                    JSONObject()
                                        .put("title", s.title)
                                        .put(
                                            "fields",
                                            JSONArray().apply {
                                                s.fields.forEach {
                                                    put(
                                                        JSONObject()
                                                            .put("label", it.label)
                                                            .put("value", it.value)
                                                    )
                                                }
                                            },
                                        )
                                )
                            }
                        },
                    )
                }
            }
            .toString(2)

    fun write(context: Context, uri: Uri, snapshot: DeviceSnapshot, format: String) {
        context.contentResolver.openOutputStream(uri)?.use { output ->
            when (format) {
                "PNG" -> BitmapReport.write(snapshot, output)
                "PDF" -> {
                    val document = PdfDocument()
                    try {
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f }
                        val lines =
                            text(snapshot).lines().flatMap { line ->
                                if (line.isEmpty()) listOf("")
                                else
                                    buildList {
                                        var rest = line
                                        while (rest.isNotEmpty()) {
                                            val n =
                                                paint
                                                    .breakText(rest, true, 510f, null)
                                                    .coerceAtLeast(1)
                                            add(rest.take(n))
                                            rest = rest.drop(n)
                                        }
                                    }
                            }
                        lines.chunked(49).forEachIndexed { index, pageLines ->
                            val page =
                                document.startPage(
                                    PdfDocument.PageInfo.Builder(595, 842, index + 1).create()
                                )
                            pageLines.forEachIndexed { i, line ->
                                page.canvas.drawText(line, 42f, 45f + i * 15, paint)
                            }
                            document.finishPage(page)
                        }
                        document.writeTo(output)
                    } finally {
                        document.close()
                    }
                }
                "JSON" -> output.write(json(snapshot).toByteArray())
                "HTML" -> {
                    val escaped =
                        text(snapshot)
                            .replace("&", "&amp;")
                            .replace("<", "&lt;")
                            .replace(">", "&gt;")
                    output.write(
                        "<!doctype html><meta charset=\"utf-8\"><title>abCheck report</title><style>body{font:16px monospace;max-width:900px;margin:40px auto;white-space:pre-wrap;overflow-wrap:anywhere}</style><body>$escaped</body>"
                            .toByteArray()
                    )
                }
                else -> output.write(text(snapshot).toByteArray())
            }
        } ?: error("Could not open export destination")
    }
}
