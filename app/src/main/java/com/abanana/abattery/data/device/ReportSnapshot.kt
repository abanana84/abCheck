package com.abanana.abattery.data.device

import com.abanana.abattery.domain.model.BatteryInfo

fun DeviceSnapshot.withBattery(battery: BatteryInfo?): DeviceSnapshot {
    if (battery == null) return this
    fun field(name: String, value: Any?) = DeviceField(name, value?.toString() ?: UNAVAILABLE)
    val fields =
        listOf(
            field("Level", "${battery.percent}%"),
            field("Charge status", battery.chargeStatus),
            field("Temperature", "${battery.temperatureCelsius} °C"),
            field("Voltage", "${battery.voltageV} V"),
            field("Current (µA)", battery.currentMicroA),
            field("Power (W)", battery.chargePowerW),
            field("Design capacity (mAh)", battery.designCapacityMah),
            field("Full capacity (mAh)", battery.fullChargeCapacityMah),
            field("Cycle count", battery.cycleCount),
            field("Cycles estimated", battery.cycleCountEstimated),
            field("Android health state", battery.healthState),
            field("Technology", battery.technology),
        )
    return copy(pages = pages + ("Battery" to listOf(DeviceSection("Battery", fields))))
}
