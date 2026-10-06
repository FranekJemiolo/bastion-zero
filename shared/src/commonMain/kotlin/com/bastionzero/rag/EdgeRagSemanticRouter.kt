package com.bastionzero.rag

enum class TriageUrgency {
    P1_IMMEDIATE_LIFE_THREAT, // Arterial bleed, airway obstruction, tension pneumo
    P2_DELAYED,               // Major fracture, deep laceration, 2nd-degree burn
    P3_MINIMAL,               // Sprains, minor cuts, mild abrasions
    EXPECTANT,                // Non-survivable trauma
}

data class PanickedTriageAction(
    val priorityOrder: Int,
    val title: String,
    val instruction: String,
    val cautionWarning: String?,
)

data class SemanticTriageResponse(
    val detectedTraumaCategories: List<String>,
    val urgencyLevel: TriageUrgency,
    val triageHeadline: String,
    val immediateActions: List<PanickedTriageAction>,
    val emergencySurvivalPacketCode: String,
)

/**
 * Panicked Conversational Triage Router.
 * Parses unstructured natural-language distress descriptions during panic
 * and produces deterministic, prioritized Tactical Combat Casualty Care (TCCC) procedures.
 */
class EdgeRagSemanticRouter {

    fun routePanickedQuery(input: String): SemanticTriageResponse {
        val lower = input.lowercase()
        val detectedCategories = mutableListOf<String>()

        val hasArterialBleed = lower.contains("spurting") || lower.contains("bright red") ||
            (lower.contains("bleed") && (lower.contains("heavy") || lower.contains("gushing") || lower.contains("fast")))
        val hasTourniquetNeed = hasArterialBleed || lower.contains("amputation") || lower.contains("severed")
        val hasOpenFracture = (lower.contains("bone") && (lower.contains("sticking") || lower.contains("open") || lower.contains("visible"))) ||
            lower.contains("compound fracture")
        val hasTensionPneumo = lower.contains("chest") && (lower.contains("sucking") || lower.contains("hole") || lower.contains("gasping"))
        val hasSevereHypothermia = lower.contains("freezing") || lower.contains("hypothermia") ||
            (lower.contains("shivering") && (lower.contains("slurring") || lower.contains("confused") || lower.contains("stopped")))
        val hasSevereBurn = lower.contains("burn") || lower.contains("scalding") || lower.contains("fire")

        if (hasArterialBleed) detectedCategories.add("MASSIVE_ARTERIAL_HEMORRHAGE")
        if (hasOpenFracture) detectedCategories.add("COMPOUND_OPEN_FRACTURE")
        if (hasTensionPneumo) detectedCategories.add("TENSION_PNEUMOTHORAX")
        if (hasSevereHypothermia) detectedCategories.add("SEVERE_SYSTEMIC_HYPOTHERMIA")
        if (hasSevereBurn) detectedCategories.add("THERMAL_BURN_TRAUMA")

        val actions = mutableListOf<PanickedTriageAction>()
        var urgency = TriageUrgency.P2_DELAYED
        var headline = "TRAUMA ASSESSMENT COMPLETED"
        var packetCode = "TRIAGE_TRAUMA_P2"

        if (hasArterialBleed || hasTourniquetNeed) {
            urgency = TriageUrgency.P1_IMMEDIATE_LIFE_THREAT
            headline = "CRITICAL: MASSIVE ARTERIAL HEMORRHAGE DETECTED"
            packetCode = "SOS_MED_ARTERIAL_BLEED"

            actions.add(
                PanickedTriageAction(
                    priorityOrder = 1,
                    title = "APPLY TOURNIQUET IMMEDIATELY",
                    instruction = "Place commercial tourniquet (or belt/cord) 2-3 inches above the wound. Twist windlass until bright red bleeding completely stops. Lock in bracket.",
                    cautionWarning = "DO NOT place over a joint. Write exact application time on patient's forehead (e.g. T-14:35)."
                )
            )
            actions.add(
                PanickedTriageAction(
                    priorityOrder = 2,
                    title = "PACK WOUND WITH PRESSURE DRESSING",
                    instruction = "Pack remaining cavity tightly with sterile gauze or clean cloth. Hold direct bilateral thumb pressure for minimum 3 continuous minutes.",
                    cautionWarning = "Never remove gauze if blood soaks through; add more layers on top."
                )
            )
        }

        if (hasTensionPneumo) {
            urgency = TriageUrgency.P1_IMMEDIATE_LIFE_THREAT
            headline = "CRITICAL: SUCKING CHEST WOUND"
            packetCode = "SOS_MED_PNEUMOTHORAX"

            actions.add(
                PanickedTriageAction(
                    priorityOrder = 1,
                    title = "VENTED CHEST SEAL APPLICATION",
                    instruction = "Cover the penetrating chest hole with a vented chest seal (or plastic wrapper taped on 3 sides to form a 1-way flutter valve).",
                    cautionWarning = "Monitor for tension buildup; burp the seal if breathing worsens."
                )
            )
        }

        if (hasOpenFracture) {
            actions.add(
                PanickedTriageAction(
                    priorityOrder = actions.size + 1,
                    title = "SPLINT IN POSITION FOUND",
                    instruction = "Immobilize the joint above and below the fracture using rigid sticks or SAM splint. Cover exposed bone with moist sterile dressing.",
                    cautionWarning = "DO NOT attempt to push the bone back inside the skin."
                )
            )
        }

        if (hasSevereHypothermia) {
            actions.add(
                PanickedTriageAction(
                    priorityOrder = actions.size + 1,
                    title = "ACTIVE REWARMING & BURRITO WRAP",
                    instruction = "Strip wet clothing immediately. Wrap patient in vapor barrier + Mylar space blanket + sleeping bag. Place warm bottles in armpits and groin.",
                    cautionWarning = "Handle gently: rough movement can trigger fatal ventricular fibrillation in cold myocardium."
                )
            )
        }

        if (hasSevereBurn) {
            actions.add(
                PanickedTriageAction(
                    priorityOrder = actions.size + 1,
                    title = "COOL AND DRY STERILE DRESSING",
                    instruction = "Cool burn with clean ambient water for 10 minutes. Cover with loose dry sterile dressing. Initiate Parkland fluid replacement.",
                    cautionWarning = "DO NOT apply ice or greasy ointments. Prevent patient hypothermia."
                )
            )
        }

        if (actions.isEmpty()) {
            urgency = TriageUrgency.P3_MINIMAL
            headline = "MINOR INJURY PROTOCOL"
            packetCode = "TRIAGE_ROUTINE_P3"
            actions.add(
                PanickedTriageAction(
                    priorityOrder = 1,
                    title = "CLEAN AND IRRIGATE",
                    instruction = "Irrigate wound thoroughly with clean potable water. Apply sterile adhesive dressing. Monitor for swelling or systemic infection.",
                    cautionWarning = null
                )
            )
        }

        return SemanticTriageResponse(
            detectedTraumaCategories = detectedCategories,
            urgencyLevel = urgency,
            triageHeadline = headline,
            immediateActions = actions,
            emergencySurvivalPacketCode = packetCode
        )
    }
}
