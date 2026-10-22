package com.abanana.abattery.data.device

import org.junit.Assert.*
import org.junit.Test

class DeviceModelsTest {
    @Test
    fun `negative traffic sentinel is never shown as a byte count`() {
        assertEquals(UNAVAILABLE, formatBytes(-1))
    }

    @Test
    fun `binary units are labelled accurately`() {
        assertEquals("1.0 KiB", formatBytes(1024))
        assertEquals("1.0 GiB", formatBytes(1024L * 1024 * 1024))
    }

    @Test
    fun `usage handles unknown totals and bounds`() {
        assertEquals(0f, usedFraction(100, 0), 0f)
        assertEquals(1f, usedFraction(200, 100), 0f)
        assertEquals(.5f, usedFraction(50, 100), 0f)
        assertEquals(0f, usedFraction(-50, 100), 0f)
    }

    @Test
    fun `rates use elapsed milliseconds and reject counter resets`() {
        assertEquals(1000L, counterRate(1000, 3000, 2000))
        assertNull(counterRate(3000, 1000, 1000))
        assertNull(counterRate(-1, 1000, 1000))
        assertNull(counterRate(0, 1000, 0))
    }
}
