package com.bastionzero.optical

import kotlin.math.ln
import kotlin.math.max

/**
 * Water clarity category classified in Nephelometric Turbidity Units (NTU).
 */
enum class WaterClarityCategory {
    CLEAR_POTABLE,            // < 1.0 NTU: Transparent spring/snowmelt
    SLIGHTLY_TURBID,          // 1.0 - 5.0 NTU: Faint haze
    CLOUDY_HEAVY_PARTICULATE, // 5.0 - 50.0 NTU: Visible particulate suspended
    HAZARDOUS_MUD             // > 50.0 NTU: Heavy sediment/mud
}

/**
 * Recommended field purification protocol based on optical turbidity.
 */
enum class PurificationRecommendation {
    UV_OR_HALOGEN_READY,              // UV pen or chlorine/iodine tablets effective directly
    MECHANICAL_FILTRATION_FIRST,      // 0.1 micron microfilter required prior to disinfection
    FLOCCULATION_AND_BOILING,         // Flocculant/settling (alum/ash) -> decant -> rolling boil >3min
    SEDIMENT_PREFILTER_MANDATORY      // Multi-tier gravel/sand/charcoal pre-filter mandatory
}

/**
 * Actionable field water diagnostic report.
 */
data class TurbidityReport(
    val luxTransmitted: Float,
    val luxReference: Float,
    val transmittanceRatio: Float,
    val estimatedNtu: Float,
    val category: WaterClarityCategory,
    val recommendation: PurificationRecommendation,
    val requiredBoilTimeMinutes: Int,
    val isUvSterilizerEffective: Boolean,
    val fieldActionNotes: String
)

/**
 * Optical Water Turbidity & Particulate Analyzer.
 *
 * Repurposes the smartphone screen (calibrated lux emitter) and ambient light sensor
 * or optical camera receptor to estimate water turbidity in Nephelometric Turbidity Units (NTU)
 * by measuring optical transmittance and scattering through a transparent container.
 */
class WaterTurbidityAnalyzer(
    private val calibrationAlpha: Float = 28.5f // Calibrated extinction coefficient for 5cm path length
) {

    /**
     * Evaluates water sample transmittance and returns field purification triage.
     *
     * @param luxTransmitted Attenuated light measured through water sample.
     * @param luxReference Baseline light measured through empty container or air.
     * @param pathLengthCm Container optical width in centimeters.
     */
    fun analyze(
        luxTransmitted: Float,
        luxReference: Float,
        pathLengthCm: Float = 5.0f
    ): TurbidityReport {
        require(luxReference > 0f) { "Reference lux must be greater than zero." }
        val safeTransmitted = max(0.01f, luxTransmitted)
        val rawRatio = (safeTransmitted / luxReference).coerceIn(0.0001f, 1.0f)

        // Path-length normalized optical absorption: NTU ~ alpha * (1 / d) * ln(I_0 / I)
        val normalizedFactor = 5.0f / max(1.0f, pathLengthCm)
        val ntu = (calibrationAlpha * normalizedFactor * ln(1.0f / rawRatio)).coerceAtLeast(0f)

        val category = when {
            ntu < 1.0f -> WaterClarityCategory.CLEAR_POTABLE
            ntu < 5.0f -> WaterClarityCategory.SLIGHTLY_TURBID
            ntu < 50.0f -> WaterClarityCategory.CLOUDY_HEAVY_PARTICULATE
            else -> WaterClarityCategory.HAZARDOUS_MUD
        }

        val recommendation = when (category) {
            WaterClarityCategory.CLEAR_POTABLE -> PurificationRecommendation.UV_OR_HALOGEN_READY
            WaterClarityCategory.SLIGHTLY_TURBID -> PurificationRecommendation.MECHANICAL_FILTRATION_FIRST
            WaterClarityCategory.CLOUDY_HEAVY_PARTICULATE -> PurificationRecommendation.FLOCCULATION_AND_BOILING
            WaterClarityCategory.HAZARDOUS_MUD -> PurificationRecommendation.SEDIMENT_PREFILTER_MANDATORY
        }

        val boilTimeMin = when (category) {
            WaterClarityCategory.CLEAR_POTABLE -> 1
            WaterClarityCategory.SLIGHTLY_TURBID -> 2
            WaterClarityCategory.CLOUDY_HEAVY_PARTICULATE -> 3
            WaterClarityCategory.HAZARDOUS_MUD -> 5
        }

        val uvEffective = category == WaterClarityCategory.CLEAR_POTABLE

        val notes = when (category) {
            WaterClarityCategory.CLEAR_POTABLE ->
                "Sample optical clarity is optimal (<1 NTU). UV sterilizer or single halogen tablet will neutralize pathogens reliably."
            WaterClarityCategory.SLIGHTLY_TURBID ->
                "Particulate haze (1-5 NTU) can shield pathogens from UV rays. Run through 0.1 micron Hollow Fiber filter before disinfection."
            WaterClarityCategory.CLOUDY_HEAVY_PARTICULATE ->
                "High particulate load (>5 NTU) will clog fine microfilters rapidly. Add wood ash or crushed alum, allow 45 minutes for coagulation settling, decant top layer, and boil."
            WaterClarityCategory.HAZARDOUS_MUD ->
                "Heavy mud/turbidity (>50 NTU). Dangerous chemical/microbial risk. Construct improvised survival sand/charcoal filter column before attempting boiling."
        }

        return TurbidityReport(
            luxTransmitted = safeTransmitted,
            luxReference = luxReference,
            transmittanceRatio = rawRatio,
            estimatedNtu = ntu,
            category = category,
            recommendation = recommendation,
            requiredBoilTimeMinutes = boilTimeMin,
            isUvSterilizerEffective = uvEffective,
            fieldActionNotes = notes
        )
    }
}
