package com.bastionzero.medical

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WoundPhotogrammetryTest {

    @Test
    fun testBurnAssessmentAndParklandFormula() {
        val photogrammetry = WoundPhotogrammetry()

        // 1000 pixels with 2mm/pixel resolution:
        // area = 1000 * (2 * 2) = 4000 mm^2 = 40 cm^2
        val assessment = photogrammetry.assessWound(
            segmentedPixelCount = 5000,
            mmPerPixel = 2.0f,
            patientWeightKg = 70.0f,
            woundType = WoundType.SUPERFICIAL_BURN,
        )

        // 5000 * 4 = 20,000 mm^2 = 200 cm^2
        assertEquals(200.0f, assessment.surfaceAreaSquareCm)
        // 200 / 175 ≈ 1.14% TBSA
        assertTrue(assessment.estimatedTbsaPercent in 1.0f..1.5f)

        assertNotNull(assessment.fluidProtocol)
        // Parkland: 4 * 70kg * 1.14% ≈ 319 mL
        assertTrue(assessment.fluidProtocol!!.total24HrFluidMl in 300..350)
        assertTrue(assessment.fluidProtocol!!.first8HrRateMlPerHour > 0)
    }
}
