package com.abanana.abattery.diagnostics

import android.content.Context
import android.os.SystemClock
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import org.json.JSONArray
import org.json.JSONObject

enum class BenchmarkWorkload {
    CPU,
    MEMORY,
    STORAGE,
    GPU,
}

data class BenchmarkResult(
    val timestamp: Long,
    val cpu: Double?,
    val memory: Double?,
    val write: Double?,
    val read: Double?,
    val durationMs: Long,
    val gpuFps: Double? = null,
) {
    fun json() =
        JSONObject()
            .put("timestamp", timestamp)
            .put("cpuKHashes", cpu ?: JSONObject.NULL)
            .put("memoryMiBs", memory ?: JSONObject.NULL)
            .put("writeMiBs", write ?: JSONObject.NULL)
            .put("readMiBs", read ?: JSONObject.NULL)
            .put("durationMs", durationMs)
            .put("gpuFps", gpuFps ?: JSONObject.NULL)
}

class BenchmarkEngine(private val context: Context) {
    // Bounded local workloads. Units and shader are independent of another app's benchmark scores.
    suspend fun run(
        selected: Set<BenchmarkWorkload> = BenchmarkWorkload.entries.toSet(),
        progress: (String) -> Unit,
    ): BenchmarkResult {
        require(selected.isNotEmpty()) { "Select at least one test" }
        val started = SystemClock.elapsedRealtime()
        var cpu: Double? = null
        var memory: Double? = null
        var write: Double? = null
        var read: Double? = null
        var gpu: Double? = null
        if (BenchmarkWorkload.CPU in selected) {
            progress("CPU · SHA-256")
            val digest = MessageDigest.getInstance("SHA-256")
            val block = ByteArray(16384) { (it % 251).toByte() }
            var operations = 0L
            val begin = System.nanoTime()
            while (System.nanoTime() - begin < 1_500_000_000) {
                repeat(100) {
                    digest.update(block)
                    digest.digest()
                    operations++
                }
                coroutineContext.ensureActive()
            }
            cpu = operations / ((System.nanoTime() - begin) / 1e9) / 1000
        }
        if (BenchmarkWorkload.MEMORY in selected) {
            progress("Memory · copy bandwidth")
            val source = ByteArray(4 * 1024 * 1024) { it.toByte() }
            val target = ByteArray(source.size)
            var copied = 0L
            val begin = System.nanoTime()
            while (System.nanoTime() - begin < 1_500_000_000) {
                source.copyInto(target)
                copied += source.size
                coroutineContext.ensureActive()
            }
            check(target.contentEquals(source))
            memory = copied / 1048576.0 / ((System.nanoTime() - begin) / 1e9)
        }
        if (BenchmarkWorkload.STORAGE in selected) {
            val size = 16 * 1024 * 1024
            check(context.cacheDir.usableSpace > size * 2) { "Insufficient free storage" }
            val source = ByteArray(4 * 1024 * 1024) { (it % 251).toByte() }
            val target = ByteArray(source.size)
            val file = File.createTempFile("benchmark-", ".tmp", context.cacheDir)
            try {
                progress("Storage · sequential write")
                var begin = System.nanoTime()
                file.outputStream().use { output ->
                    repeat(4) {
                        coroutineContext.ensureActive()
                        output.write(source)
                    }
                    output.fd.sync()
                }
                write = size / 1048576.0 / ((System.nanoTime() - begin) / 1e9)
                progress("Storage · sequential read (OS cache may apply)")
                begin = System.nanoTime()
                var count = 0L
                file.inputStream().use { input ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(target)
                        if (n < 0) break
                        count += n
                    }
                }
                check(count == size.toLong())
                check(target.contentEquals(source))
                read = size / 1048576.0 / ((System.nanoTime() - begin) / 1e9)
            } finally {
                file.delete()
            }
        }
        if (BenchmarkWorkload.GPU in selected) {
            progress("GPU · offscreen 512 × 512 shader")
            gpu =
                try {
                    GpuBenchmark.run()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
        }
        yield()
        return BenchmarkResult(
            System.currentTimeMillis(),
            cpu,
            memory,
            write,
            read,
            SystemClock.elapsedRealtime() - started,
            gpu,
        )
    }

    fun history(): List<BenchmarkResult> =
        runCatching {
                val rows =
                    JSONArray(context.getSharedPreferences("benchmarks", 0).getString("runs", "[]"))
                fun JSONObject.number(key: String): Double? =
                    if (isNull(key)) null else optDouble(key).takeIf { it.isFinite() }
                (0 until rows.length()).map { index ->
                    val o = rows.getJSONObject(index)
                    BenchmarkResult(
                        o.getLong("timestamp"),
                        o.number("cpuKHashes") ?: o.number("cpuMops"),
                        o.number("memoryMiBs"),
                        o.number("writeMiBs"),
                        o.number("readMiBs"),
                        o.getLong("durationMs"),
                        o.number("gpuFps"),
                    )
                }
            }
            .getOrDefault(emptyList())

    fun save(result: BenchmarkResult) {
        val rows = JSONArray()
        (listOf(result) + history()).take(30).forEach { rows.put(it.json()) }
        context
            .getSharedPreferences("benchmarks", 0)
            .edit()
            .putString("runs", rows.toString())
            .apply()
    }
}
