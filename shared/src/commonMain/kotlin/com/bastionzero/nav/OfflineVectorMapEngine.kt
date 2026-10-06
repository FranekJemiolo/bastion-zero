package com.bastionzero.nav

import kotlin.math.cos

data class GeoPoint2D(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Float = 0f,
)

data class VectorContourLine(
    val elevationMeters: Int,
    val isIndexContour: Boolean, // e.g. every 50m or 100m is thicker index line
    val points: List<GeoPoint2D>,
)

data class VectorTrail(
    val trailId: String,
    val name: String,
    val isPrimaryRidge: Boolean,
    val points: List<GeoPoint2D>,
)

data class VectorWaterway(
    val name: String,
    val isPotableSource: Boolean,
    val points: List<GeoPoint2D>,
)

data class VectorShelter(
    val shelterId: String,
    val name: String,
    val coordinate: GeoPoint2D,
    val hasWater: Boolean,
)

data class TacticalTerrainView(
    val contours: List<VectorContourLine>,
    val trails: List<VectorTrail>,
    val waterways: List<VectorWaterway>,
    val shelters: List<VectorShelter>,
)

/**
 * Offline Vector Terrain & Topography Engine.
 * Manages zero-cloud offline vector layers (topographic contours, ridge trails, water sources).
 */
class OfflineVectorMapEngine {

    private val contours = ArrayList<VectorContourLine>()
    private val trails = ArrayList<VectorTrail>()
    private val waterways = ArrayList<VectorWaterway>()
    private val shelters = ArrayList<VectorShelter>()

    init {
        seedSampleTerrain()
    }

    fun addContour(contour: VectorContourLine) {
        contours.add(contour)
    }

    fun addTrail(trail: VectorTrail) {
        trails.add(trail)
    }

    fun addWaterway(waterway: VectorWaterway) {
        waterways.add(waterway)
    }

    fun addShelter(shelter: VectorShelter) {
        shelters.add(shelter)
    }

    fun queryViewport(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
    ): TacticalTerrainView {
        val filteredContours = contours.filter { c ->
            c.points.any { it.latitude in minLat..maxLat && it.longitude in minLon..maxLon }
        }
        val filteredTrails = trails.filter { t ->
            t.points.any { it.latitude in minLat..maxLat && it.longitude in minLon..maxLon }
        }
        val filteredWaterways = waterways.filter { w ->
            w.points.any { it.latitude in minLat..maxLat && it.longitude in minLon..maxLon }
        }
        val filteredShelters = shelters.filter { s ->
            s.coordinate.latitude in minLat..maxLat && s.coordinate.longitude in minLon..maxLon
        }

        return TacticalTerrainView(
            contours = filteredContours,
            trails = filteredTrails,
            waterways = filteredWaterways,
            shelters = filteredShelters
        )
    }

    fun getNearestWaterSource(currentLat: Double, currentLon: Double): VectorShelter? {
        if (shelters.isEmpty()) return null
        return shelters.filter { it.hasWater }.minByOrNull {
            haversineMeters(currentLat, currentLon, it.coordinate.latitude, it.coordinate.longitude)
        }
    }

    private fun seedSampleTerrain() {
        // Sample Bieszczady Mountain Ridge (49.20° N to 49.26° N, 22.50° E to 22.58° E)
        contours.add(
            VectorContourLine(
                elevationMeters = 1000,
                isIndexContour = true,
                points = listOf(
                    GeoPoint2D(49.210, 22.510),
                    GeoPoint2D(49.215, 22.530),
                    GeoPoint2D(49.220, 22.550),
                    GeoPoint2D(49.225, 22.570)
                )
            )
        )
        contours.add(
            VectorContourLine(
                elevationMeters = 1050,
                isIndexContour = false,
                points = listOf(
                    GeoPoint2D(49.212, 22.512),
                    GeoPoint2D(49.217, 22.532),
                    GeoPoint2D(49.222, 22.552)
                )
            )
        )

        // Main Ridge Trail (Polonina Carynska corridor)
        trails.add(
            VectorTrail(
                trailId = "trail_ridge_alpha",
                name = "Main Ridge Crest Trail",
                isPrimaryRidge = true,
                points = listOf(
                    GeoPoint2D(49.210, 22.515),
                    GeoPoint2D(49.218, 22.535),
                    GeoPoint2D(49.224, 22.555),
                    GeoPoint2D(49.230, 22.575)
                )
            )
        )

        // Mountain Stream (Potable water source)
        waterways.add(
            VectorWaterway(
                name = "Wolosaty Stream Upper Fork",
                isPotableSource = true,
                points = listOf(
                    GeoPoint2D(49.208, 22.510),
                    GeoPoint2D(49.212, 22.525),
                    GeoPoint2D(49.216, 22.540)
                )
            )
        )

        // Emergency Alpine Shelter
        shelters.add(
            VectorShelter(
                shelterId = "shelter_post_1",
                name = "Sector Alpha Mountain Hut",
                coordinate = GeoPoint2D(49.218, 22.536, 1055f),
                hasWater = true
            )
        )
    }

    companion object {
        fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val toRad = kotlin.math.PI / 180.0
            val dLat = (lat2 - lat1) * toRad
            val dLon = (lon2 - lon1) * toRad
            val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                cos(lat1 * toRad) * cos(lat2 * toRad) *
                kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
            val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
            return 6371000.0 * c
        }
    }
}
