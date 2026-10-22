package com.abanana.abattery.data.device

import org.junit.Assert.*
import org.junit.Test

class CpuFrequencyReaderTest {
    private fun core(files: Map<String, String>): CoreInfo =
        CpuFrequencyReader { files[it.removePrefix("/sys/devices/system/cpu/cpu0/")] }.core(0)

    @Test
    fun `dummy driver states are never presented as measured clocks`() {
        val result =
            core(mapOf("cpufreq/scaling_driver" to "dummy", "cpufreq/scaling_cur_freq" to "2"))
        assertEquals("Virtual CPU", result.status)
        assertNull(result.currentKhz)
        assertNull(result.maxKhz)
    }

    @Test
    fun `inaccessible primary nodes fall back to alternate kernel nodes`() {
        val result =
            core(
                mapOf(
                    "cpufreq/cpuinfo_cur_freq" to "1800000",
                    "cpufreq/scaling_min_freq" to "300000",
                    "cpufreq/scaling_max_freq" to "2400000",
                )
            )
        assertEquals(1800000L, result.currentKhz)
        assertEquals(300000L, result.minKhz)
        assertEquals(2400000L, result.maxKhz)
        assertNull(result.status)
    }

    @Test
    fun `offline cores do not expose stale current samples`() {
        val result = core(mapOf("online" to "0", "cpufreq/scaling_cur_freq" to "1800000"))
        assertEquals("Offline", result.status)
        assertNull(result.currentKhz)
    }

    @Test
    fun `invalid and restricted samples stay unavailable`() {
        assertEquals("Frequency unavailable", core(emptyMap()).status)
        val result =
            core(mapOf("cpufreq/scaling_cur_freq" to "0", "cpufreq/cpuinfo_cur_freq" to "-1"))
        assertNull(result.currentKhz)
        assertNull(core(mapOf("cpufreq/scaling_cur_freq" to "bad")).currentKhz)
    }

    @Test
    fun `kernel kilohertz units are preserved even below one megahertz`() {
        assertEquals(500L, core(mapOf("cpufreq/scaling_cur_freq" to "500")).currentKhz)
        assertEquals("500 kHz", formatCpuFrequency(500))
        assertEquals("1800 MHz", formatCpuFrequency(1800000))
    }
}
