package com.vescdash.data

import kotlinx.serialization.Serializable
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString().substring(0, 8)

/**
 * Physical vehicle + the "base" limits from the VESC Tool motor config.
 * Drive modes scale down from these base limits.
 */
@Serializable
data class VehicleSettings(
    val configured: Boolean = false,
    val motorPoles: Int = 30,
    val motorKv: Double = 30.0,
    val gearRatio: Double = 1.0,
    val wheelDiameterMm: Double = 254.0,
    val cellsSeries: Int = 13,
    val imperial: Boolean = false,
    val motorCurrentMax: Double = 60.0,
    val batteryCurrentMax: Double = 30.0,
    val batteryRegenMax: Double = 10.0,
    val maxErpm: Double = 60000.0,
    val maxDuty: Double = 0.95,
    val dualController: Boolean = false,
    val canSlaveId: Int = 1,
    val pollHz: Int = 10,
    val keepScreenOn: Boolean = true,
    val applyModeOnConnect: Boolean = true,
    val demoMode: Boolean = false,
    val appearance: AppAppearance = AppAppearance.CLASSIC,
    val rideStyle: RideStyle = RideStyle.CLASSIC,
    val rideTheme: RideTheme = RideTheme.DARK,
    /** Speed streaks, an edge glow and a screen push on the ride view when accelerating hard. */
    val launchAnimation: Boolean = false,
    /** Compensate voltage sag so battery % doesn't jump around under load. */
    val batteryStabilize: Boolean = true,
    /** Pack capacity in Ah; 0 = unknown. When set, battery % uses charge counting. */
    val batteryCapacityAh: Double = 0.0,
    /** Ride-view heat icons appear at these temperatures (°C). */
    val heatWarnControllerC: Double = 60.0,
    val heatWarnMotorC: Double = 60.0,
) {
    val controllers: Int get() = if (dualController) 2 else 1
    val nominalVoltage: Double get() = cellsSeries * 3.7

    /** Samples kept for graph widgets: 30 s at the poll rate. */
    val historySamples: Int get() = 30 * pollHz.coerceIn(1, 30)
}

/** How the whole app looks. Stark Varg is a landscape-only, unofficial lookalike of the Stark Varg phone app. */
@Serializable
enum class AppAppearance(val label: String) {
    CLASSIC("Classic"),
    STARK("Stark Varg"),
}

@Serializable
enum class RideStyle(val label: String) {
    CLASSIC("Classic gauge"),
    MINIMAL("Minimal"),
    TILES("Tiles"),
    CUSTOM("Custom"),
}

@Serializable
enum class RideTheme(val label: String) {
    DARK("Dark"),
    LIGHT("Light"),
    SYSTEM("Auto"),
}

@Serializable
data class DriveMode(
    val id: String = newId(),
    val name: String,
    val color: Long,
    /** Scales motor current (torque) and battery current. 10–100. */
    val powerPct: Int = 70,
    /** Scales braking current and battery regen current. 10–100. */
    val regenPct: Int = 50,
    /** Null = no limit beyond the base max ERPM. Always stored in km/h. */
    val topSpeedKmh: Double? = null,
    /** Null = no power cap. */
    val powerCapKw: Double? = null,
)

@Serializable
enum class WidgetType(val label: String) {
    NUMBER("Number"),
    GAUGE("Gauge"),
    BAR("Bar"),
    GRAPH("Graph"),
}

@Serializable
enum class WidgetSize { HALF, FULL }

@Serializable
data class DashWidget(
    val id: String = newId(),
    val type: WidgetType,
    val metric: Metric,
    val size: WidgetSize = WidgetSize.HALF,
    val min: Double? = null,
    val max: Double? = null,
    val warn: Double? = null,
    val danger: Double? = null,
    val label: String? = null,
)

@Serializable
data class Dashboard(
    val id: String = newId(),
    val name: String,
    val widgets: List<DashWidget>,
)

/** A rectangle in fractions of the ride screen (0..1, top-left origin) so a layout scales to any phone. */
@Serializable
data class RideRect(val x: Float, val y: Float, val w: Float, val h: Float) {
    val right: Float get() = x + w
    val bottom: Float get() = y + h
}

/** One widget placed on the Custom ride screen. The widget's id is the item id; its [DashWidget.size] is ignored. */
@Serializable
data class RideItem(val widget: DashWidget, val rect: RideRect)

/**
 * The Custom ride screen. [items] draw first to last, so the last is on top. The mode pill and
 * the warning icons are fixed elements: they can be moved and resized but not removed.
 */
@Serializable
data class RideLayout(
    val items: List<RideItem>,
    val modePill: RideRect,
    val warnings: RideRect,
)

object Defaults {
    val modeColors = listOf(
        0xFF30D158, 0xFF29B6F6, 0xFFFF9100, 0xFFFF1744,
        0xFFB388FF, 0xFFFFEA00, 0xFFFF4081, 0xFFE0E0E0,
    )

    val modes = listOf(
        DriveMode("eco", "Eco", 0xFF30D158, powerPct = 40, regenPct = 70, topSpeedKmh = 25.0),
        DriveMode("trail", "Trail", 0xFF29B6F6, powerPct = 65, regenPct = 50),
        DriveMode("sport", "Sport", 0xFFFF9100, powerPct = 85, regenPct = 35),
        DriveMode("max", "Max", 0xFFFF1744, powerPct = 100, regenPct = 25),
    )

    val dashboards = listOf(
        Dashboard(
            "ride", "Ride",
            listOf(
                DashWidget("r-speed", WidgetType.GAUGE, Metric.SPEED, WidgetSize.FULL),
                DashWidget("r-power", WidgetType.NUMBER, Metric.POWER),
                DashWidget("r-batt", WidgetType.NUMBER, Metric.BATTERY, warn = 20.0, danger = 10.0),
                DashWidget("r-tmot", WidgetType.BAR, Metric.TEMP_MOTOR, warn = 80.0, danger = 100.0),
                DashWidget("r-tfet", WidgetType.BAR, Metric.TEMP_FET, warn = 70.0, danger = 85.0),
                DashWidget("r-pgraph", WidgetType.GRAPH, Metric.POWER, WidgetSize.FULL),
                DashWidget("r-trip", WidgetType.NUMBER, Metric.TRIP),
                DashWidget("r-eff", WidgetType.NUMBER, Metric.EFFICIENCY),
            ),
        ),
        Dashboard(
            "tune", "Tuning",
            listOf(
                DashWidget("t-imot", WidgetType.GRAPH, Metric.MOTOR_CURRENT, WidgetSize.FULL),
                DashWidget("t-ibat", WidgetType.GRAPH, Metric.BATTERY_CURRENT, WidgetSize.FULL),
                DashWidget("t-duty", WidgetType.BAR, Metric.DUTY, WidgetSize.FULL, warn = 85.0, danger = 92.0),
                DashWidget("t-volt", WidgetType.NUMBER, Metric.VOLTAGE),
                DashWidget("t-rpm", WidgetType.NUMBER, Metric.MOTOR_RPM),
                DashWidget("t-wh", WidgetType.NUMBER, Metric.WH_USED),
                DashWidget("t-regen", WidgetType.NUMBER, Metric.WH_REGEN),
            ),
        ),
    )

    /** Speed in the middle, battery and trip readouts along the top, power graph along the bottom. */
    val rideLayout = RideLayout(
        items = listOf(
            RideItem(
                DashWidget("c-batt", WidgetType.BAR, Metric.BATTERY, warn = 20.0, danger = 10.0),
                RideRect(0.31f, 0.05f, 0.40f, 0.16f),
            ),
            RideItem(DashWidget("c-speed", WidgetType.NUMBER, Metric.SPEED), RideRect(0.26f, 0.24f, 0.48f, 0.49f)),
            RideItem(DashWidget("c-trip", WidgetType.NUMBER, Metric.TRIP), RideRect(0.78f, 0.05f, 0.19f, 0.19f)),
            RideItem(
                DashWidget("c-tmot", WidgetType.NUMBER, Metric.TEMP_MOTOR, warn = 80.0, danger = 100.0),
                RideRect(0.78f, 0.26f, 0.19f, 0.19f),
            ),
            RideItem(DashWidget("c-power", WidgetType.GRAPH, Metric.POWER), RideRect(0.035f, 0.75f, 0.93f, 0.21f)),
        ),
        modePill = RideRect(0.035f, 0.05f, 0.24f, 0.13f),
        warnings = RideRect(0.035f, 0.24f, 0.19f, 0.45f),
    )
}
