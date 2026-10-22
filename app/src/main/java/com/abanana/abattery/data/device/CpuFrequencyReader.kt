package com.abanana.abattery.data.device

internal class CpuFrequencyReader(private val read: (String) -> String?) {
    fun core(id: Int): CoreInfo {
        val cpu = "/sys/devices/system/cpu/cpu$id"
        val path = "$cpu/cpufreq"
        fun frequency(vararg names: String): Long? =
            names.firstNotNullOfOrNull { name ->
                read("$path/$name")?.trim()?.toLongOrNull()?.takeIf { it > 0 }
            }

        // The emulator's dummy driver exposes 1/2 as synthetic states, not hardware clocks.
        if (read("$path/scaling_driver")?.trim() == "dummy") {
            return CoreInfo(id, null, null, null, "Virtual CPU")
        }
        val offline = read("$cpu/online")?.trim() == "0"
        val current = if (offline) null else frequency("scaling_cur_freq", "cpuinfo_cur_freq")
        return CoreInfo(
            id,
            current,
            frequency("cpuinfo_min_freq", "scaling_min_freq"),
            frequency("cpuinfo_max_freq", "scaling_max_freq"),
            if (offline) "Offline" else if (current == null) "Frequency unavailable" else null,
        )
    }
}
