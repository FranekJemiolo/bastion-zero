package com.bastionzero.thermal

import kotlin.math.pow
import kotlin.math.round

enum class SurfaceMaterial(val displayName: String, val emissivity: Float) {
    HUMAN_SKIN("Human Skin", 0.98f),
    WATER_STREAM("Water / Stream", 0.96f),
    TARGET_PATCH_TAPE_SOOT("Target Patch (Electrical Tape / Soot)", 0.95f),
    CONCRETE_BRICK("Concrete / Brick", 0.92f),
    ORGANIC_WOOD_SOIL("Organic Soil / Wood", 0.90f),
    OXIDIZED_METAL("Oxidized Rusted Metal", 0.60f),
    POLISHED_METAL("Polished Shiny Metal", 0.10f),
    MYLAR_SPACE_BLANKET("Mylar Space Blanket", 0.05f),
}

data class ThermalReading(
    val apparentTempCelsius: Float,
    val material: SurfaceMaterial,
    val correctedTempCelsius: Float,
    val isScaldHazard: Boolean,
    val isMylarCamouflaged: Boolean,
    val safetyWarning: String?,
    val recommendedFieldAction: String?,
)

/**
 * Long-Wave Infrared (LWIR) Thermal Imaging & Emissivity Engine.
 * Converts raw microbolometer radiant temperature measurements into true surface
 * temperatures via Stefan-Boltzmann law compensation. Protects against "Thermally
 * Invisible" Mylar space blanket camouflage during SAR and false-cold burn hazards
 * on shiny metal surfaces.
 */
class ThermalImagingEngine(
    private val defaultEmissivity: Float = 0.95f,
) {
    companion object {
        const val KELVIN_OFFSET = 273.15f
        const val LOW_EMISSIVITY_THRESHOLD = 0.20f
        const val SCALD_WARNING_TEMP_CELSIUS = 50.0f // Temps above 50°C cause severe burns
    }

    /**
     * Compute true surface temperature from apparent radiant temperature and surface material.
     * Uses Stefan-Boltzmann relationship: T_true = (T_rad^4 / epsilon)^(1/4)
     */
    fun calculateTrueTemperature(
        apparentTempCelsius: Float,
        material: SurfaceMaterial,
    ): ThermalReading {
        val apparentKelvin = apparentTempCelsius + KELVIN_OFFSET
        val epsilon = material.emissivity.coerceIn(0.01f, 1.0f)

        // T_true_K = (T_rad_K^4 / epsilon)^(1/4)
        // With default sensor assumption epsilon0 = 0.95:
        // Correction factor ratio: (0.95 / epsilon)^(0.25)
        val ratio = (defaultEmissivity / epsilon).toDouble()
        val correctedKelvin = (apparentKelvin * ratio.pow(0.25)).toFloat()
        val correctedCelsius = correctedKelvin - KELVIN_OFFSET

        val isLowEmissivity = material.emissivity <= LOW_EMISSIVITY_THRESHOLD
        val isMylar = material == SurfaceMaterial.MYLAR_SPACE_BLANKET

        val isScaldHazard = isLowEmissivity || correctedCelsius >= SCALD_WARNING_TEMP_CELSIUS

        var warning: String? = null
        var fieldAction: String? = null

        if (isMylar) {
            warning = "SAR ALERT: Mylar thermal camouflage detected (emissivity 0.05). Blanket acts as an infrared mirror reflecting freezing ambient ground, concealing survivor body heat from aerial thermal cameras."
            fieldAction = "Expose a patch of bare skin or fold back a corner of the blanket toward the sky so airborne FLIR/SAR can detect a heat signature."
        } else if (isLowEmissivity) {
            val appStr = apparentTempCelsius.formatDecimals(1)
            val corrStr = correctedCelsius.formatDecimals(1)
            warning = "BURN HAZARD WARNING: Low-emissivity surface (${material.emissivity}). Apparent reading ($appStr°C) is an infrared reflection of the cooler background. Actual surface temperature is approximately $corrStr°C or higher!"
            fieldAction = "Field Protocol: Apply a target patch of electrical tape or campfire carbon soot (ε = 0.95) flush to the metal. Wait 90 seconds for thermal equilibrium, then measure the patch."
        } else if (correctedCelsius >= SCALD_WARNING_TEMP_CELSIUS) {
            warning = "HIGH TEMPERATURE ALERT: Surface temperature exceeds scald threshold (${correctedCelsius.formatDecimals(1)}°C)."
        }

        return ThermalReading(
            apparentTempCelsius = apparentTempCelsius,
            material = material,
            correctedTempCelsius = correctedCelsius,
            isScaldHazard = isScaldHazard,
            isMylarCamouflaged = isMylar,
            safetyWarning = warning,
            recommendedFieldAction = fieldAction,
        )
    }

    private fun Float.formatDecimals(decimals: Int): String {
        val factor = when (decimals) {
            0 -> 1.0
            1 -> 10.0
            2 -> 100.0
            else -> 10.0
        }
        val rounded = round(this * factor) / factor
        if (decimals == 0) return rounded.toLong().toString()
        val str = rounded.toString()
        val parts = str.split('.')
        if (parts.size == 1) return "$str." + "0".repeat(decimals)
        val dec = parts[1].padEnd(decimals, '0').take(decimals)
        return "${parts[0]}.$dec"
    }
}
