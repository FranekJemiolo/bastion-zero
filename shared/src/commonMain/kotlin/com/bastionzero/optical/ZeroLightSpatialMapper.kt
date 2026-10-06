package com.bastionzero.optical

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 3D point in metric coordinate space (X: right, Y: forward, Z: up).
 */
data class SpatialPoint3D(
    val x: Float,
    val y: Float,
    val z: Float
)

/**
 * Detected physical obstacle bounding box in metric space.
 */
data class SpatialObstacle(
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float,
    val minZ: Float,
    val maxZ: Float,
    val distanceMeters: Float,
    val isHeadClearanceHazard: Boolean
) {
    val widthMeters: Float get() = maxX - minX
    val depthMeters: Float get() = maxY - minY
    val heightMeters: Float get() = maxZ - minZ
    val centerBearingDeg: Float get() = (atan2(minX + maxX, minY + maxY) * (180f / kotlin.math.PI.toFloat())).coerceIn(-180f, 180f)
}

/**
 * Walkable corridor and clearance vectors.
 */
data class WalkableCorridor(
    val forwardClearanceMeters: Float,
    val leftClearanceMeters: Float,
    val rightClearanceMeters: Float,
    val safeBearingDegrees: Float,
    val hasDropoffPitfall: Boolean
)

/**
 * 2.5D wireframe line segment for OLED tactical vector rendering.
 */
data class WireframeSegment(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val isHazard: Boolean
)

/**
 * High-contrast spatial topology map for zero-light tactical display.
 */
data class SpatialTopology(
    val floorPlaneZ: Float,
    val obstacles: List<SpatialObstacle>,
    val corridor: WalkableCorridor,
    val wireframeLines: List<WireframeSegment>,
    val totalPointsProcessed: Int
)

/**
 * Zero-Light Spatial Wireframe Navigation Engine.
 *
 * Converts infrared Time-of-Flight / LiDAR depth point clouds into high-contrast
 * obstacle bounding boxes, drop-off pitfall alarms, and safe walkable corridors
 * without emitting any visible light.
 */
class ZeroLightSpatialMapper(
    private val floorPlaneToleranceMeters: Float = 0.15f,
    private val headClearanceHeightMeters: Float = 2.0f,
    private val dropoffThresholdMeters: Float = 0.45f
) {

    /**
     * Ingests a raw 3D point cloud and synthesizes an actionable topological map.
     */
    fun mapTopology(points: List<SpatialPoint3D>): SpatialTopology {
        if (points.isEmpty()) {
            return SpatialTopology(
                floorPlaneZ = 0f,
                obstacles = emptyList(),
                corridor = WalkableCorridor(0f, 0f, 0f, 0f, false),
                wireframeLines = emptyList(),
                totalPointsProcessed = 0
            )
        }

        // 1. Estimate floor plane Z (median of lowest 20% of points)
        val sortedZ = points.map { it.z }.sorted()
        val floorSampleCount = max(1, (sortedZ.size * 0.20f).toInt())
        val floorPlaneZ = sortedZ.take(floorSampleCount).average().toFloat()

        // 2. Identify obstacles and drop-offs
        val obstaclePoints = mutableListOf<SpatialPoint3D>()
        var dropoffDetected = false

        for (pt in points) {
            val heightAboveFloor = pt.z - floorPlaneZ

            // Check forward drop-off pitfall (e.g. collapsed stairs or ravine)
            if (pt.y > 0.5f && heightAboveFloor < -dropoffThresholdMeters) {
                dropoffDetected = true
            }

            // Points between floor clearance and head height are obstacles
            if (heightAboveFloor > floorPlaneToleranceMeters && heightAboveFloor < headClearanceHeightMeters) {
                obstaclePoints.add(pt)
            }
        }

        // 3. Cluster obstacle points into spatial bounding boxes (grid binning)
        val obstacles = clusterObstacles(obstaclePoints, floorPlaneZ)

        // 4. Compute safe navigable corridor
        val corridor = computeCorridor(obstacles, dropoffDetected)

        // 5. Generate wireframe outline segments for OLED vector rendering
        val wireframes = generateWireframes(obstacles, corridor)

        return SpatialTopology(
            floorPlaneZ = floorPlaneZ,
            obstacles = obstacles,
            corridor = corridor,
            wireframeLines = wireframes,
            totalPointsProcessed = points.size
        )
    }

    private fun clusterObstacles(points: List<SpatialPoint3D>, floorZ: Float): List<SpatialObstacle> {
        if (points.isEmpty()) return emptyList()

        // Simple spatial grid binning (0.5m grid resolution)
        val bins = mutableMapOf<Pair<Int, Int>, MutableList<SpatialPoint3D>>()
        val gridSize = 0.6f

        for (pt in points) {
            val gx = (pt.x / gridSize).toInt()
            val gy = (pt.y / gridSize).toInt()
            bins.getOrPut(Pair(gx, gy)) { mutableListOf() }.add(pt)
        }

        return bins.values.mapNotNull { cluster ->
            if (cluster.size < 2) return@mapNotNull null // Reject single-point noise

            var minX = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            var minZ = Float.MAX_VALUE
            var maxZ = -Float.MAX_VALUE

            for (p in cluster) {
                minX = min(minX, p.x)
                maxX = max(maxX, p.x)
                minY = min(minY, p.y)
                maxY = max(maxY, p.y)
                minZ = min(minZ, p.z)
                maxZ = max(maxZ, p.z)
            }

            val centerX = (minX + maxX) / 2f
            val centerY = (minY + maxY) / 2f
            val dist = sqrt(centerX * centerX + centerY * centerY)
            val isHeadHazard = (maxZ - floorZ) >= 1.6f

            SpatialObstacle(
                minX = minX,
                maxX = maxX,
                minY = minY,
                maxY = maxY,
                minZ = minZ,
                maxZ = maxZ,
                distanceMeters = dist,
                isHeadClearanceHazard = isHeadHazard
            )
        }.sortedBy { it.distanceMeters }
    }

    private fun computeCorridor(obstacles: List<SpatialObstacle>, dropoffDetected: Boolean): WalkableCorridor {
        var forwardClearance = 10.0f
        var leftClearance = 3.0f
        var rightClearance = 3.0f

        for (obs in obstacles) {
            // Forward corridor within [-0.4m, 0.4m] width
            if (obs.minX < 0.4f && obs.maxX > -0.4f && obs.minY > 0f) {
                forwardClearance = min(forwardClearance, obs.minY)
            }
            // Left sector (X < 0)
            if (obs.minX < 0f && obs.minY in 0f..3f) {
                leftClearance = min(leftClearance, abs(obs.maxX))
            }
            // Right sector (X > 0)
            if (obs.maxX > 0f && obs.minY in 0f..3f) {
                rightClearance = min(rightClearance, obs.minX)
            }
        }

        // Calculate safe bearing deviation based on clearances
        val safeBearing = when {
            dropoffDetected -> 0f // Halt forward movement
            forwardClearance < 1.0f && leftClearance > rightClearance -> -30f // Veer left
            forwardClearance < 1.0f && rightClearance >= leftClearance -> 30f // Veer right
            else -> 0f // Straight ahead safe
        }

        return WalkableCorridor(
            forwardClearanceMeters = forwardClearance,
            leftClearanceMeters = leftClearance,
            rightClearanceMeters = rightClearance,
            safeBearingDegrees = safeBearing,
            hasDropoffPitfall = dropoffDetected
        )
    }

    private fun generateWireframes(obstacles: List<SpatialObstacle>, corridor: WalkableCorridor): List<WireframeSegment> {
        val segments = mutableListOf<WireframeSegment>()

        // Draw obstacle bounding box rectangles (XY top-down plane)
        for (obs in obstacles) {
            segments.add(WireframeSegment(obs.minX, obs.minY, obs.maxX, obs.minY, isHazard = true))
            segments.add(WireframeSegment(obs.maxX, obs.minY, obs.maxX, obs.maxY, isHazard = true))
            segments.add(WireframeSegment(obs.maxX, obs.maxY, obs.minX, obs.maxY, isHazard = true))
            segments.add(WireframeSegment(obs.minX, obs.maxY, obs.minX, obs.minY, isHazard = true))
        }

        // Draw safe navigation ray
        val navLength = min(corridor.forwardClearanceMeters, 4.0f)
        val rad = corridor.safeBearingDegrees * (kotlin.math.PI.toFloat() / 180f)
        val targetX = navLength * kotlin.math.sin(rad)
        val targetY = navLength * kotlin.math.cos(rad)
        segments.add(WireframeSegment(0f, 0f, targetX, targetY, isHazard = false))

        return segments
    }
}
