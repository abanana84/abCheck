package com.abanana.abattery.presentation.battery

import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abanana.abattery.R
import com.abanana.abattery.domain.model.BatteryHealthState
import com.abanana.abattery.domain.model.BatteryInfo
import com.abanana.abattery.domain.model.PowerPlugType
import com.abanana.abattery.presentation.components.ACheckProgressBar
import com.abanana.abattery.presentation.components.ACheckTopBar
import com.abanana.abattery.presentation.components.InfoRow
import com.abanana.abattery.presentation.components.SectionCard
import com.abanana.abattery.presentation.components.SectionLabel
import com.abanana.abattery.presentation.locale.labelRes
import com.abanana.abattery.ui.theme.AppTheme
import com.abanana.abattery.ui.theme.InterFont
import com.abanana.abattery.ui.theme.Manrope
import com.abanana.abattery.ui.theme.SpaceGrotesk
import com.abanana.abattery.ui.theme.ThemeMode
import com.abanana.abattery.util.formatBatteryCycleCount

@Composable
fun BatteryScreen(
    viewModel: BatteryViewModel = hiltViewModel(),
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    showTopBar: Boolean = true,
    onThemeModeChange: (ThemeMode) -> Unit = {},
) {
    val info by viewModel.batteryInfo.collectAsStateWithLifecycle()
    val currentHistory by viewModel.currentHistoryMa.collectAsStateWithLifecycle()
    val na = stringResource(R.string.value_na)
    val c = AppTheme.colors

    Column(modifier = Modifier.fillMaxSize().background(c.background)) {
        if (showTopBar) ACheckTopBar(
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal),
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(4.dp))

            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        SectionLabel(stringResource(R.string.section_status))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = info?.let { stringResource(it.chargeStatus.labelRes()) }
                                ?: stringResource(R.string.em_dash),
                            fontFamily = Manrope,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 28.sp,
                            color = c.onSurface,
                        )
                        Text(
                            text = info?.let { plugLine(it.plugTypes) } ?: "",
                            fontFamily = SpaceGrotesk,
                            fontSize = 12.sp,
                            color = c.primaryGreen,
                        )
                    }
                    BatteryCircularGauge(
                        percent = info?.percent ?: 0,
                        isCharging = info?.isCharging ?: false,
                        modifier = Modifier.size(120.dp),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SectionCard(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    SectionLabel(stringResource(R.string.section_temperature))
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "%.1f".format(info?.temperatureCelsius ?: 0f),
                            fontFamily = Manrope,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = c.onSurface,
                        )
                        Text(
                            stringResource(R.string.unit_celsius),
                            fontFamily = SpaceGrotesk,
                            fontSize = 14.sp,
                            color = c.outline,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    ACheckProgressBar(
                        progress = ((info?.temperatureCelsius ?: 0f) / 60f).coerceIn(0f, 1f),
                        color = c.secondary,
                    )
                }
                SectionCard(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    SectionLabel(stringResource(R.string.section_voltage))
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "%.3f".format(info?.voltageV ?: 0f),
                            fontFamily = Manrope,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = c.onSurface,
                        )
                        Text(
                            stringResource(R.string.unit_volt),
                            fontFamily = SpaceGrotesk,
                            fontSize = 14.sp,
                            color = c.outline,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SectionCard(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    SectionLabel(stringResource(R.string.section_power))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = formatPowerW(info?.chargePowerW, na),
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = c.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.power_formula_hint),
                        fontFamily = SpaceGrotesk,
                        fontSize = 10.sp,
                        color = c.outline,
                    )
                }
                SectionCard(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    SectionLabel(stringResource(R.string.label_cycle_count))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = formatBatteryCycleCount(
                            info?.cycleCount,
                            info?.cycleCountEstimated == true,
                            na,
                            stringResource(R.string.cycle_estimated),
                        ),
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = c.onSurface,
                    )
                }
            }

            SectionCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.current_flow_title),
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = c.onSurface,
                    )
                    Text(
                        text = formatCurrentMa(info?.currentMicroA, na),
                        fontFamily = Manrope,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        color = c.onSurface,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.current_flow_subtitle),
                    fontFamily = SpaceGrotesk,
                    fontSize = 11.sp,
                    color = c.outline,
                )
                Spacer(Modifier.height(12.dp))
                BatteryCurrentFlowChart(
                    samples = currentHistory,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionCard {
                Text(
                    stringResource(R.string.section_chemistry_health),
                    fontFamily = Manrope,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = c.onSurface,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = c.outlineVariant.copy(alpha = 0.15f),
                )
                InfoRow(
                    stringResource(R.string.label_technology),
                    formatTechnology(info?.technology, na),
                )
                InfoRow(
                    label = stringResource(R.string.label_health),
                    value = info?.let { stringResource(it.healthState.labelRes()) } ?: na,
                    valueColor = c.primaryGreen,
                )
            }

            SectionCard {
                SectionLabel(stringResource(R.string.section_charge_capability))
                Spacer(Modifier.height(8.dp))
                InfoRow(
                    stringResource(R.string.label_max_charging_current),
                    formatMaxUa(info?.maxChargingCurrentUa, na),
                )
                val curUa = info?.maxChargingCurrentUa
                if (curUa != null && curUa > 0) {
                    Spacer(Modifier.height(6.dp))
                    ACheckProgressBar(
                        progress = (curUa / 3_000_000f).coerceIn(0f, 1f),
                        color = c.primaryGreen,
                    )
                }
                Spacer(Modifier.height(10.dp))
                InfoRow(
                    stringResource(R.string.label_max_charging_voltage),
                    formatMaxUv(info?.maxChargingVoltageUv, na),
                )
                val maxUv = info?.maxChargingVoltageUv
                if (maxUv != null && maxUv > 0) {
                    Spacer(Modifier.height(6.dp))
                    ACheckProgressBar(
                        progress = (maxUv / 12_000_000f).coerceIn(0f, 1f),
                        color = c.secondary,
                    )
                }
            }

            SectionCard {
                SectionLabel(stringResource(R.string.section_capacity_cycles))
                Spacer(Modifier.height(8.dp))
                InfoRow(
                    stringResource(R.string.label_design_capacity),
                    formatMah(info?.designCapacityMah, na),
                )
                InfoRow(
                    stringResource(R.string.label_full_capacity),
                    formatMah(info?.fullChargeCapacityMah, na),
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = c.surfaceLow,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = c.primaryGreen,
                        modifier = Modifier
                            .size(20.dp)
                            .padding(top = 2.dp),
                    )
                    Text(
                        text = batteryHealthMessage(info),
                        fontFamily = InterFont,
                        fontSize = 13.sp,
                        color = c.onSurfaceVar,
                        lineHeight = 18.sp,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun plugLine(types: List<PowerPlugType>): String {
    if (types.isEmpty()) return stringResource(R.string.power_on_battery)
    val out = StringBuilder()
    for ((index, type) in types.withIndex()) {
        if (index > 0) out.append(" + ")
        out.append(stringResource(type.labelRes()))
    }
    return out.toString()
}

private fun formatTechnology(raw: String?, na: String): String {
    if (raw.isNullOrBlank() || raw.equals("Unknown", true)) return na
    return raw.trim()
}

private fun formatCurrentMa(microA: Int?, na: String): String {
    if (microA == null) return na
    val ma = microA / 1000f
    return "%+.0f mA".format(ma)
}

private fun formatPowerW(w: Float?, na: String): String {
    if (w == null || w <= 0f) return na
    return "%.2f W".format(w)
}

private fun formatMah(mah: Int?, na: String): String =
    if (mah != null && mah > 0) "$mah mAh" else na

private fun formatMaxUa(ua: Int?, na: String): String =
    when {
        ua == null || ua <= 0 -> na
        else -> "%d mA".format(ua / 1000)
    }

private fun formatMaxUv(uv: Int?, na: String): String =
    when {
        uv == null || uv <= 0 -> na
        else -> "%.2f V".format(uv / 1_000_000.0)
    }

@Composable
private fun batteryHealthMessage(info: BatteryInfo?): String {
    if (info == null) return stringResource(R.string.msg_reading_battery)
    return when (info.healthState) {
        BatteryHealthState.EXCELLENT,
        BatteryHealthState.GOOD,
        -> stringResource(R.string.msg_health_good)
        BatteryHealthState.FAIR -> stringResource(R.string.msg_health_fair)
        BatteryHealthState.POOR -> stringResource(R.string.msg_health_poor)
        else -> stringResource(
            R.string.msg_health_status,
            stringResource(info.chargeStatus.labelRes()),
            stringResource(info.healthState.labelRes()),
        )
    }
}

@Composable
fun BatteryCurrentFlowChart(samples: List<Float>, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val line = c.primaryGreen
    val gridColor = c.outlineVariant.copy(alpha = 0.22f)
    val chartBg = c.surfaceHighest.copy(alpha = 0.35f)
    Box(
        modifier
            .fillMaxWidth()
            .height(168.dp)
            .background(chartBg, RoundedCornerShape(12.dp)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val pad = 12.dp.toPx()
            val w = (size.width - pad * 2).coerceAtLeast(1f)
            val h = (size.height - pad * 2).coerceAtLeast(1f)
            val gridLines = 5
            for (i in 0..gridLines) {
                val gy = pad + i * h / gridLines
                drawLine(
                    color = gridColor,
                    start = Offset(pad, gy),
                    end = Offset(size.width - pad, gy),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            if (samples.size < 2) return@Canvas
            val minV = samples.minOrNull() ?: 0f
            val maxV = samples.maxOrNull() ?: 1f
            val spanRaw = (maxV - minV).coerceAtLeast(1f)
            val inset = spanRaw * 0.08f
            val minAdj = minV - inset
            val maxAdj = maxV + inset
            val span = (maxAdj - minAdj).coerceAtLeast(1f)
            val path = Path()
            samples.forEachIndexed { i, v ->
                val t = i / (samples.size - 1).coerceAtLeast(1).toFloat()
                val x = pad + t * w
                val y = pad + h - ((v - minAdj) / span) * h
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path,
                color = line,
                style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        if (samples.size < 2) {
            Text(
                text = "…",
                modifier = Modifier.align(Alignment.Center),
                fontFamily = SpaceGrotesk,
                fontSize = 22.sp,
                color = c.outline.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
fun BatteryCircularGauge(percent: Int, isCharging: Boolean, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val animPct by animateFloatAsState(
        targetValue = percent.toFloat(),
        animationSpec = tween(800, easing = EaseInOutCubic),
        label = "battery_gauge",
    )
    val chargeLabel = stringResource(R.string.gauge_charging)
    val onBatteryLabel = stringResource(R.string.gauge_on_battery)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 10.dp.toPx()
            val sweepAngle = animPct / 100f * 360f
            drawArc(
                color = c.surfaceHighest,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = c.primaryGreen,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$percent",
                fontFamily = Manrope,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 36.sp,
                color = c.onSurface,
            )
            Text(
                "%",
                fontFamily = Manrope,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = c.onSurface.copy(alpha = 0.5f),
            )
            Spacer(Modifier.height(2.dp))
            Surface(
                shape = RoundedCornerShape(50.dp),
                color = c.primaryGreen.copy(alpha = 0.1f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = if (isCharging) Icons.Filled.Bolt else Icons.Filled.BatteryFull,
                        contentDescription = null,
                        tint = c.primaryGreen,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        if (isCharging) chargeLabel else onBatteryLabel,
                        fontFamily = SpaceGrotesk,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = c.primaryGreen,
                    )
                }
            }
        }
    }
}
