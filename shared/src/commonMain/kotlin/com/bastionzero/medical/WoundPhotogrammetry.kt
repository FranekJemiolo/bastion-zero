package com.bastionzero.medical

import kotlin.math.round

enum class WoundType(val displayName: String, val infectionRiskFactor: Float) {
    SUPERFICIAL_BURN("1st/2nd Degree Burn", 1.2f),
    FULL_THICKNESS_BURN("3rd Degree Burn", 2.5f),
    LACERATION_PUNCTURE("Deep Laceration / Puncture", 1.8f),
    ABRASION_ROADRASH("Extensive Abrasion", 1.4f),
}

data class FluidResuscitationProtocol(
    val total24HrFluidMl: Int,
    val first8HrRateMlPerHour: Int,
    val next16HrRateMlPerHour: Int,
    val fluidType: String = "Lactated Ringer's / Normal Saline (0.9% NaCl)",
)

data class WoundAssessment(
    val woundType: WoundType,
    val surfaceAreaSquareCm: Float,
    val estimatedTbsaPercent: Float,
    val fluidProtocol: FluidResuscitationProtocol?,
    val infectionRiskLevel: String,
    val fieldActionNotes: String,
)

/**
 * AR Wound Sizing and Burn Photogrammetry Engine.
 * Calculates wound surface area, percentage of Total Body Surface Area (%TBSA),
 * and Parkland fluid resuscitation requirements during wilderness trauma.
 */
class WoundPhotogrammetry {

    /**
     * Compute wound surface area from segmented optical/depth pixels.
     * @param segmentedPixelCount number of identified wound pixels
     * @param mmPerPixel optical scale factor determined from depth/parallax sensor
     * @param patientWeightKg patient body weight for fluid calculation
     * @param woundType classification of injury
     */
    fun assessWound(
        segmentedPixelCount: Int,
        mmPerPixel: Float,
        patientWeightKg: Float = 75.0f,
        woundType: WoundType = WoundType.SUPERFICIAL_BURN,
    ): WoundAssessment {
        val areaSquareMm = segmentedPixelCount * (mmPerPixel * mmPerPixel)
        val areaSquareCm = areaSquareMm / 100.0f

        // Adult total body surface area is approximately 1.7 - 1.9 m^2 (17,000 - 19,000 cm^2).
        // 1% TBSA is roughly equivalent to the patient's open palm (~150 - 180 cm^2).
        val estimatedTbsa = (areaSquareCm / 175.0f).coerceIn(0.1f, 100.0f)

        // Parkland Formula applies primarily to burns >= 10% TBSA:
        // Volume (mL) = 4 mL * weight (kg) * % TBSA
        val fluidProtocol = if (woundType == WoundType.SUPERFICIAL_BURN || woundType == WoundType.FULL_THICKNESS_BURN) {
            val totalMl = (4.0f * patientWeightKg * estimatedTbsa).toInt()
            val first8HrRate = (totalMl / 2.0f / 8.0f).toInt()
            val next16HrRate = (totalMl / 2.0f / 16.0f).toInt()

            FluidResuscitationProtocol(
                total24HrFluidMl = totalMl,
                first8HrRateMlPerHour = first8HrRate,
                next16HrRateMlPerHour = next16HrRate,
            )
        } else {
            null
        }

        val riskLevel = when {
            estimatedTbsa >= 20.0f || woundType == WoundType.FULL_THICKNESS_BURN -> "CRITICAL - SEVERE SYSTEMIC INFECTION & HYPOVOLEMIC SHOCK"
            estimatedTbsa >= 10.0f -> "HIGH - INFECTION / FLUID LOSS THREAT"
            areaSquareCm > 50.0f -> "MODERATE - LOCALIZED INFECTION RISK"
            else -> "LOW - STERILE DRESSING REQUIRED"
        }

        val notes = StringBuilder()
        notes.append("Surface: ${areaSquareCm.formatDecimals(1)} cm² (${estimatedTbsa.formatDecimals(1)}% TBSA). ")
        if (fluidProtocol != null && estimatedTbsa >= 5.0f) {
            notes.append("Fluid required: ${fluidProtocol.total24HrFluidMl} mL over 24h. Run at ${fluidProtocol.first8HrRateMlPerHour} mL/hr for first 8h. ")
        }
        notes.append("Clean with sterile water. Apply non-adherent dressing. Elevate extremity to reduce edema.")

        return WoundAssessment(
            woundType = woundType,
            surfaceAreaSquareCm = areaSquareCm,
            estimatedTbsaPercent = estimatedTbsa,
            fluidProtocol = fluidProtocol,
            infectionRiskLevel = riskLevel,
            fieldActionNotes = notes.toString(),
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
