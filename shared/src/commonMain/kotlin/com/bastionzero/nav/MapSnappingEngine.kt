package com.bastionzero.nav

import kotlin.math.cos

data class SnappingResult(
    val isSnapped: Boolean,
    val snappedCoordinate: GeoPoint2D,
    val originalCoordinate: GeoPoint2D,
    val distanceMeters: Double,
    val activeTrailName: String?,
)

/**
 * Pedestrian Dead Reckoning Map Snapping Engine.
 * Snaps accumulated IMU displacement vectors onto known offline topological trails
 * to bound integration errors and eliminate long-term dead reckoning drift.
 */
class MapSnappingEngine(
    private val snappingThresholdMeters: Double = 25.0,
) {

    fun snapToNearestTrail(
        rawCoord: GeoPoint2D,
        trails: List<VectorTrail>,
    ): SnappingResult {
        if (trails.isEmpty()) {
            return SnappingResult(
                isSnapped = false,
                snappedCoordinate = rawCoord,
                originalCoordinate = rawCoord,
                distanceMeters = 0.0,
                activeTrailName = null
            )
        }

        var minDistance = Double.MAX_VALUE
        var bestSnappedPoint = rawCoord
        var bestTrailName: String? = null

        for (trail in trails) {
            val pts = trail.points
            if (pts.size < 2) continue

            for (i in 0 until pts.size - 1) {
                val a = pts[i]
                val b = pts[i + 1]

                val proj = projectPointOnSegment(rawCoord, a, b)
                val dist = OfflineVectorMapEngine.haversineMeters(
                    rawCoord.latitude, rawCoord.longitude,
                    proj.latitude, proj.longitude
                )

                if (dist < minDistance) {
                    minDistance = dist
                    bestSnappedPoint = proj
                    bestTrailName = trail.name
                }
            }
        }

        val isSnapped = minDistance <= snappingThresholdMeters
        return SnappingResult(
            isSnapped = isSnapped,
            snappedCoordinate = if (isSnapped) bestSnappedPoint else rawCoord,
            originalCoordinate = rawCoord,
            distanceMeters = minDistance,
            activeTrailName = if (isSnapped) bestTrailName else null
        )
    }

    private fun projectPointOnSegment(
        p: GeoPoint2D,
        a: GeoPoint2D,
        b: GeoPoint2D,
    ): GeoPoint2D {
        // Convert to local Cartesian meters relative to A
        val toRad = kotlin.math.PI / 180.0
        val metersPerLat = 111320.0
        val metersPerLon = 111320.0 * cos(a.latitude * toRad)

        val bx = (b.longitude - a.longitude) * metersPerLon
        val by = (b.latitude - a.latitude) * metersPerLat

        val px = (p.longitude - a.longitude) * metersPerLon
        val py = (p.latitude - a.latitude) * metersPerLat

        val ab2 = bx * bx + by * by
        if (ab2 < 1e-6) return a

        // Orthogonal projection parameter t = (P . AB) / |AB|^2
        val t = ((px * bx + py * by) / ab2).coerceIn(0.0, 1.0)

        val projX = t * bx
        val projY = t * by

        val projLon = a.longitude + (projX / metersPerLon)
        val projLat = a.latitude + (projY / metersPerLat)

        return GeoPoint2D(projLat, projLon, a.altitudeMeters + (t * (b.altitudeMeters - a.altitudeMeters)).toFloat())
    }
}
