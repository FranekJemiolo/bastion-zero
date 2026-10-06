package com.bastionzero.optical

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ZeroLightSpatialMapperTest {

    private val mapper = ZeroLightSpatialMapper()

    @Test
    fun testEmptyPointCloudYieldsClearTopology() {
        val topo = mapper.mapTopology(emptyList())
        assertEquals(0, topo.totalPointsProcessed)
        assertTrue(topo.obstacles.isEmpty())
        assertFalse(topo.corridor.hasDropoffPitfall)
    }

    @Test
    fun testObstacleDetectionAndCorridorVeering() {
        // Floor points at Z ~ 0.0
        val points = mutableListOf<SpatialPoint3D>()
        for (i in -10..10) {
            for (j in 0..10) {
                points.add(SpatialPoint3D(i * 0.2f, j * 0.5f, 0.0f))
            }
        }

        // Add obstacle directly in forward path (X in [-0.2, 0.2], Y in [1.5, 2.0], Z in [0.5, 1.2])
        for (z in 5..12) {
            points.add(SpatialPoint3D(-0.1f, 1.6f, z * 0.1f))
            points.add(SpatialPoint3D(0.1f, 1.8f, z * 0.1f))
            points.add(SpatialPoint3D(0.0f, 1.7f, z * 0.1f))
        }

        val topo = mapper.mapTopology(points)

        assertTrue(topo.obstacles.isNotEmpty(), "Should detect obstacle in forward path")
        assertTrue(topo.corridor.forwardClearanceMeters < 2.5f, "Forward clearance should be constricted")
        assertFalse(topo.corridor.hasDropoffPitfall)
        assertTrue(topo.wireframeLines.isNotEmpty(), "Wireframes should be generated")
    }

    @Test
    fun testDropoffHazardDetection() {
        val points = mutableListOf<SpatialPoint3D>()
        // Floor points
        points.add(SpatialPoint3D(0f, 0f, 0f))
        points.add(SpatialPoint3D(0f, 0.2f, 0f))
        points.add(SpatialPoint3D(0.2f, 0.2f, 0f))

        // Forward pitfall points dropping down to -1.2m
        points.add(SpatialPoint3D(0f, 1.5f, -1.2f))
        points.add(SpatialPoint3D(0.2f, 1.8f, -1.5f))

        val topo = mapper.mapTopology(points)
        assertTrue(topo.corridor.hasDropoffPitfall, "Dropoff pitfall must be flagged")
    }
}
