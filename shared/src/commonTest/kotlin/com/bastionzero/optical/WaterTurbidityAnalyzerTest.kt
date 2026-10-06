package com.bastionzero.optical

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WaterTurbidityAnalyzerTest {

    private val analyzer = WaterTurbidityAnalyzer()

    @Test
    fun testClearWaterEvaluation() {
        // High transmission: 980 lux out of 1000 lux
        val report = analyzer.analyze(luxTransmitted = 980f, luxReference = 1000f, pathLengthCm = 5.0f)

        assertEquals(WaterClarityCategory.CLEAR_POTABLE, report.category)
        assertEquals(PurificationRecommendation.UV_OR_HALOGEN_READY, report.recommendation)
        assertTrue(report.isUvSterilizerEffective)
        assertTrue(report.estimatedNtu < 1.0f)
        assertEquals(1, report.requiredBoilTimeMinutes)
    }

    @Test
    fun testSlightlyTurbidWaterEvaluation() {
        // Moderate transmission: 900 lux out of 1000 lux
        val report = analyzer.analyze(luxTransmitted = 900f, luxReference = 1000f, pathLengthCm = 5.0f)

        assertEquals(WaterClarityCategory.SLIGHTLY_TURBID, report.category)
        assertEquals(PurificationRecommendation.MECHANICAL_FILTRATION_FIRST, report.recommendation)
        assertFalse(report.isUvSterilizerEffective)
        assertTrue(report.estimatedNtu in 1.0f..5.0f)
        assertEquals(2, report.requiredBoilTimeMinutes)
    }

    @Test
    fun testCloudyParticulateWaterEvaluation() {
        // Low transmission: 400 lux out of 1000 lux
        val report = analyzer.analyze(luxTransmitted = 400f, luxReference = 1000f, pathLengthCm = 5.0f)

        assertEquals(WaterClarityCategory.CLOUDY_HEAVY_PARTICULATE, report.category)
        assertEquals(PurificationRecommendation.FLOCCULATION_AND_BOILING, report.recommendation)
        assertFalse(report.isUvSterilizerEffective)
        assertTrue(report.estimatedNtu in 5.0f..50.0f)
        assertEquals(3, report.requiredBoilTimeMinutes)
    }

    @Test
    fun testHazardousMudWaterEvaluation() {
        // Very low transmission: 50 lux out of 1000 lux
        val report = analyzer.analyze(luxTransmitted = 50f, luxReference = 1000f, pathLengthCm = 5.0f)

        assertEquals(WaterClarityCategory.HAZARDOUS_MUD, report.category)
        assertEquals(PurificationRecommendation.SEDIMENT_PREFILTER_MANDATORY, report.recommendation)
        assertFalse(report.isUvSterilizerEffective)
        assertTrue(report.estimatedNtu > 50.0f)
        assertEquals(5, report.requiredBoilTimeMinutes)
    }
}
