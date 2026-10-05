package com.bastionzero.thermal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ThermalImagingEngineTest {

    @Test
    fun testHumanSkinTemperatureMeasurement() {
        val engine = ThermalImagingEngine()

        // Human skin has high emissivity (0.98), close to default 0.95
        val reading = engine.calculateTrueTemperature(
            apparentTempCelsius = 36.5f,
            material = SurfaceMaterial.HUMAN_SKIN,
        )

        // Correction for skin (0.98 vs 0.95): true surface temp is ~34.1°C for 36.5°C apparent
        assertTrue(reading.correctedTempCelsius in 33.0f..37.0f)
        assertEquals(false, reading.isScaldHazard)
        assertEquals(false, reading.isMylarCamouflaged)
    }

    @Test
    fun testPolishedMetalTriggersScaldBurnWarning() {
        val engine = ThermalImagingEngine()

        // Polished shiny metal (emissivity 0.10) reads cool 22°C (reflecting room)
        val reading = engine.calculateTrueTemperature(
            apparentTempCelsius = 22.0f,
            material = SurfaceMaterial.POLISHED_METAL,
        )

        assertTrue(reading.isScaldHazard)
        assertNotNull(reading.safetyWarning)
        assertTrue(reading.safetyWarning!!.contains("BURN HAZARD WARNING"))
        assertNotNull(reading.recommendedFieldAction)
        assertTrue(reading.recommendedFieldAction!!.contains("electrical tape or campfire carbon soot"))
    }

    @Test
    fun testMylarSpaceBlanketTriggersSarCamouflageAlert() {
        val engine = ThermalImagingEngine()

        // Mylar has extremely low emissivity (0.05)
        val reading = engine.calculateTrueTemperature(
            apparentTempCelsius = 5.0f,
            material = SurfaceMaterial.MYLAR_SPACE_BLANKET,
        )

        assertTrue(reading.isMylarCamouflaged)
        assertNotNull(reading.safetyWarning)
        assertTrue(reading.safetyWarning!!.contains("Mylar thermal camouflage detected"))
        assertNotNull(reading.recommendedFieldAction)
        assertTrue(reading.recommendedFieldAction!!.contains("Expose a patch of bare skin"))
    }
}
