package com.bastionzero.nav

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Astronomical ephemeris coordinates for celestial bodies.
 */
data class CelestialEphemeris(
    val solarAzimuthDeg: Float,     // 0 to 360 degrees true North
    val solarElevationDeg: Float,   // -90 to +90 degrees above horizon
    val isSunAboveHorizon: Boolean,
    val polarisAzimuthDeg: Float,   // Polaris true azimuth (~0.0 to 0.7 deg)
    val polarisElevationDeg: Float  // Polaris elevation (~latitude in Northern hemisphere)
)

/**
 * Celestial navigation fix and magnetic calibration check.
 */
data class CelestialNavigationFix(
    val ephemeris: CelestialEphemeris,
    val calculatedTrueHeadingDeg: Float,
    val magneticHeadingDeg: Float,
    val magneticDeviationDeg: Float,
    val magneticAnomalyDetected: Boolean,
    val guidanceNote: String
)

/**
 * Celestial & Solar Ephemeris Navigation Engine.
 *
 * Provides unjammable astronomical heading references using solar ephemeris
 * and North Star (Polaris) coordinates when GNSS is jammed or spoofed, and
 * magnetometers suffer from local metal/structural interference.
 */
class CelestialCompassEngine {

    private val rad = kotlin.math.PI / 180.0
    private val deg = 180.0 / kotlin.math.PI

    /**
     * Computes solar and Polaris ephemeris for a given UTC epoch timestamp and estimated location.
     *
     * @param epochMillis UTC timestamp in milliseconds.
     * @param latitudeDeg Estimated latitude in degrees (-90 to +90).
     * @param longitudeDeg Estimated longitude in degrees (-180 to +180).
     */
    fun computeEphemeris(
        epochMillis: Long,
        latitudeDeg: Double,
        longitudeDeg: Double
    ): CelestialEphemeris {
        // Julian Day calculation
        val julianDay = (epochMillis / 86400000.0) + 2440587.5
        val daysSinceJ2000 = julianDay - 2451545.0

        // Mean solar anomaly and longitude
        val meanAnomalyRad = ((357.529 + 0.98560028 * daysSinceJ2000) % 360.0) * rad
        val meanLongitudeRad = ((280.459 + 0.98564736 * daysSinceJ2000) % 360.0) * rad

        // Ecliptic longitude
        val eclipticRad = meanLongitudeRad + (1.915 * sin(meanAnomalyRad) + 0.020 * sin(2.0 * meanAnomalyRad)) * rad
        val obliquityRad = 23.439 * rad

        // Solar Declination (delta)
        val sinDeclination = sin(obliquityRad) * sin(eclipticRad)
        val declinationRad = asin(sinDeclination)

        // Equation of Time (EoT in minutes)
        val y = kotlin.math.tan(obliquityRad / 2.0) * kotlin.math.tan(obliquityRad / 2.0)
        val eotMinutes = 4.0 * deg * (
            y * sin(2.0 * meanLongitudeRad) -
            2.0 * 0.0167 * sin(meanAnomalyRad) +
            4.0 * 0.0167 * y * sin(meanAnomalyRad) * cos(2.0 * meanLongitudeRad)
        )

        // Solar Time and Hour Angle (H)
        val utcHours = ((epochMillis / 3600000.0) % 24.0)
        val timeZoneOffsetHours = longitudeDeg / 15.0
        val solarTimeHours = (utcHours + timeZoneOffsetHours + (eotMinutes / 60.0) + 24.0) % 24.0
        val hourAngleRad = ((solarTimeHours - 12.0) * 15.0) * rad

        // Solar Elevation (h)
        val latRad = latitudeDeg * rad
        val sinElevation = sin(latRad) * sin(declinationRad) + cos(latRad) * cos(declinationRad) * cos(hourAngleRad)
        val elevationRad = asin(sinElevation.coerceIn(-1.0, 1.0))
        val elevationDeg = elevationRad * deg

        // Solar Azimuth (Az)
        val cosAzimuth = (sin(declinationRad) - sin(latRad) * sin(elevationRad)) /
            (cos(latRad) * cos(elevationRad)).let { if (it == 0.0) 0.0001 else it }
        var azimuthDeg = acos(cosAzimuth.coerceIn(-1.0, 1.0)) * deg

        if (sin(hourAngleRad) > 0.0) {
            azimuthDeg = 360.0 - azimuthDeg
        }

        // Polaris ephemeris (Northern Hemisphere)
        val isNorthern = latitudeDeg > 0.0
        val polarisElev = if (isNorthern) latitudeDeg.toFloat() else 0f
        val polarisAz = if (isNorthern) 0.5f else 180.0f

        return CelestialEphemeris(
            solarAzimuthDeg = azimuthDeg.toFloat(),
            solarElevationDeg = elevationDeg.toFloat(),
            isSunAboveHorizon = elevationDeg > 0.0,
            polarisAzimuthDeg = polarisAz,
            polarisElevationDeg = polarisElev
        )
    }

    /**
     * Determines true heading using shadow-stick alignment and cross-checks magnetic compass.
     *
     * @param shadowRelativeBearingDeg Angle of shadow cast relative to phone's top axis (0 to 360).
     * @param measuredMagneticHeadingDeg Magnetic compass reading from internal magnetometer.
     */
    fun solveHeadingWithShadow(
        epochMillis: Long,
        latitudeDeg: Double,
        longitudeDeg: Double,
        shadowRelativeBearingDeg: Float,
        measuredMagneticHeadingDeg: Float
    ): CelestialNavigationFix {
        val ephemeris = computeEphemeris(epochMillis, latitudeDeg, longitudeDeg)

        // Shadow points directly away from the sun: Shadow Azimuth = Solar Azimuth + 180°
        // True Heading = Shadow Azimuth - Shadow Relative Bearing
        var trueHeading = (ephemeris.solarAzimuthDeg + 180f - shadowRelativeBearingDeg) % 360f
        if (trueHeading < 0f) trueHeading += 360f

        val deviation = abs(trueHeading - measuredMagneticHeadingDeg).let {
            if (it > 180f) 360f - it else it
        }

        val anomalyDetected = deviation > 15.0f

        val guidance = if (anomalyDetected) {
            "CRITICAL: Magnetic compass deviates by ${deviation.toInt()}° from solar ephemeris. Local magnetic distortion, rebar interference, or electronic spoofing detected."
        } else {
            "Nominal: Celestial fix correlates with magnetic sensor (deviation ${deviation.toInt()}°)."
        }

        return CelestialNavigationFix(
            ephemeris = ephemeris,
            calculatedTrueHeadingDeg = trueHeading,
            magneticHeadingDeg = measuredMagneticHeadingDeg,
            magneticDeviationDeg = deviation,
            magneticAnomalyDetected = anomalyDetected,
            guidanceNote = guidance
        )
    }
}
