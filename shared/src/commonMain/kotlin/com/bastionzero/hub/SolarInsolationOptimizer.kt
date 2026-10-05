package com.bastionzero.hub

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin

data class SolarCoordinates(
    val elevationDegrees: Float,
    val azimuthDegrees: Float,
    val isDaylight: Boolean,
)

data class PowerTriageRouting(
    val incomingSolarWatts: Float,
    val primaryTargetDevice: String,
    val primaryPercent: Int,
    val primaryAllocatedWatts: Float,
    val secondaryTargetDevice: String,
    val secondaryPercent: Int,
    val secondaryAllocatedWatts: Float,
)

/**
 * AR Solar Insolation Optimizer & Triage Power Distributor.
 * Calculates astronomical solar trajectory and optimal tilt orientation for
 * portable foldable solar panels, and orchestrates OTG reverse power distribution.
 */
class SolarInsolationOptimizer {

    /**
     * Approximate solar position (elevation and azimuth) using astronomical formulas.
     * @param latDegrees latitude in degrees
     * @param dayOfYear day of the year (1 - 365)
     * @param hourOfDay solar hour (0.0 - 24.0)
     */
    fun calculateSolarPosition(
        latDegrees: Float,
        dayOfYear: Int,
        hourOfDay: Float,
    ): SolarCoordinates {
        val latRad = latDegrees * (PI / 180.0)

        // Solar declination delta = 23.45 * sin(2*pi * (284 + n) / 365)
        val declinationDeg = 23.45 * sin(2.0 * PI * (284 + dayOfYear) / 365.0)
        val declinationRad = declinationDeg * (PI / 180.0)

        // Hour angle omega = 15° * (hour - 12)
        val hourAngleDeg = 15.0 * (hourOfDay - 12.0)
        val hourAngleRad = hourAngleDeg * (PI / 180.0)

        // Solar elevation alpha: sin(alpha) = sin(lat)*sin(decl) + cos(lat)*cos(decl)*cos(omega)
        val sinElevation = sin(latRad) * sin(declinationRad) + cos(latRad) * cos(declinationRad) * cos(hourAngleRad)
        val elevationRad = asin(sinElevation.coerceIn(-1.0, 1.0))
        val elevationDeg = (elevationRad * (180.0 / PI)).toFloat()

        // Approximate solar azimuth (0° North, 90° East, 180° South, 270° West)
        val azimuthDeg = if (latDegrees >= 0) {
            // Northern Hemisphere: sun transits South (180°)
            (180.0f + hourAngleDeg.toFloat()).coerceIn(0.0f, 360.0f)
        } else {
            // Southern Hemisphere: sun transits North (0°)
            ((hourAngleDeg.toFloat() + 360f) % 360f)
        }

        return SolarCoordinates(
            elevationDegrees = elevationDeg,
            azimuthDegrees = azimuthDeg,
            isDaylight = elevationDeg > 0.0f,
        )
    }

    /**
     * Calculate optimal panel tilt angle relative to ground.
     * In winter: latitude + 15°; in summer: latitude - 15°; in spring/fall: latitude.
     */
    fun calculateOptimalTiltDegrees(latDegrees: Float, dayOfYear: Int): Float {
        val isWinter = dayOfYear < 80 || dayOfYear > 355
        val isSummer = dayOfYear in 172..265

        val baseTilt = kotlin.math.abs(latDegrees)
        return when {
            isWinter -> (baseTilt + 15f).coerceIn(10f, 85f)
            isSummer -> (baseTilt - 15f).coerceIn(0f, 75f)
            else -> baseTilt.coerceIn(10f, 75f)
        }
    }

    /**
     * Triage distribution of incoming solar power between phone and critical external radio/gear.
     */
    fun triagePower(
        incomingSolarWatts: Float,
        primaryTarget: String = "Two-Way Medical Radio",
        primaryPercent: Int = 80,
        secondaryTarget: String = "Bastion Zero Internal Battery",
    ): PowerTriageRouting {
        val p1 = primaryPercent.coerceIn(0, 100)
        val p2 = 100 - p1

        val w1 = incomingSolarWatts * (p1 / 100.0f)
        val w2 = incomingSolarWatts * (p2 / 100.0f)

        return PowerTriageRouting(
            incomingSolarWatts = incomingSolarWatts,
            primaryTargetDevice = primaryTarget,
            primaryPercent = p1,
            primaryAllocatedWatts = w1,
            secondaryTargetDevice = secondaryTarget,
            secondaryPercent = p2,
            secondaryAllocatedWatts = w2,
        )
    }
}
