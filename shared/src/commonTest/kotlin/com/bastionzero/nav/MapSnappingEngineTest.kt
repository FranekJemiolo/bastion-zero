package com.bastionzero.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MapSnappingEngineTest {

    @Test
    fun testVectorMapEngineQueriesViewport() {
        val mapEngine = OfflineVectorMapEngine()
        val viewport = mapEngine.queryViewport(
            minLat = 49.20,
            maxLat = 49.25,
            minLon = 22.50,
            maxLon = 22.60
        )

        assertTrue(viewport.contours.isNotEmpty(), "Should retrieve mountain contours")
        assertTrue(viewport.trails.isNotEmpty(), "Should retrieve ridge trails")
        assertTrue(viewport.waterways.isNotEmpty(), "Should retrieve streams")
        assertTrue(viewport.shelters.isNotEmpty(), "Should retrieve alpine shelters")

        val nearestWater = mapEngine.getNearestWaterSource(49.217, 22.535)
        assertNotNull(nearestWater)
        assertEquals("Sector Alpha Mountain Hut", nearestWater.name)
    }

    @Test
    fun testMapSnappingToTrailWithinThreshold() {
        val snappingEngine = MapSnappingEngine(snappingThresholdMeters = 30.0)

        val trail = VectorTrail(
            trailId = "trail_test",
            name = "Test Ridge Trail",
            isPrimaryRidge = true,
            points = listOf(
                GeoPoint2D(49.210, 22.515),
                GeoPoint2D(49.220, 22.515)
            )
        )

        // Raw IMU coordinate slightly drifted east by 10 meters (longitude 22.51513)
        val driftedCoord = GeoPoint2D(49.215, 22.51513)

        val result = snappingEngine.snapToNearestTrail(driftedCoord, listOf(trail))

        assertTrue(result.isSnapped, "Points within 30m should snap to trail")
        assertEquals("Test Ridge Trail", result.activeTrailName)
        assertTrue(result.distanceMeters < 20.0)
        // Snapped point should lie exactly on longitude 22.515
        assertTrue(kotlin.math.abs(result.snappedCoordinate.longitude - 22.515) < 1e-4)
    }

    @Test
    fun testMapSnappingRejectsFarOutlier() {
        val snappingEngine = MapSnappingEngine(snappingThresholdMeters = 20.0)

        val trail = VectorTrail(
            trailId = "trail_test",
            name = "Test Ridge Trail",
            isPrimaryRidge = true,
            points = listOf(
                GeoPoint2D(49.210, 22.515),
                GeoPoint2D(49.220, 22.515)
            )
        )

        // Raw point 500 meters away
        val farCoord = GeoPoint2D(49.215, 22.525)

        val result = snappingEngine.snapToNearestTrail(farCoord, listOf(trail))
        assertFalse(result.isSnapped, "Points far from trail should not snap")
        assertEquals(farCoord, result.snappedCoordinate)
    }
}
