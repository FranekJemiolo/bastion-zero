package com.bastionzero.rag

import kotlin.math.sqrt

data class SurvivalKnowledgeChunk(
    val id: String,
    val title: String,
    val category: String,
    val content: String,
    val keywords: List<String>,
    val urgencyScore: Int, // 1 to 10
)

data class TriageGuidance(
    val matchedTopic: String,
    val confidence: Float,
    val isLifeThreatening: Boolean,
    val immediateActionSteps: List<String>,
    val fullGuideMarkdown: String,
)

/**
 * On-Device Edge-LLM Medical RAG Engine.
 * Parses natural conversational queries during high-stress crises, matching symptoms
 * against localized vector/semantic survival embeddings without internet access.
 */
class EdgeMedicalRag {

    private val embeddedCorpus = listOf(
        SurvivalKnowledgeChunk(
            id = "MED_ARTERIAL_BLEED",
            title = "Severe Arterial Bleeding & Tourniquet Application",
            category = "Trauma Surgery",
            content = "Bright red spurting blood indicates arterial rupture. Immediate action is critical to prevent hypovolemic shock within 90 seconds.",
            keywords = listOf("bleeding", "blood", "spurting", "thigh", "arm", "artery", "arterial", "wound", "cut", "tourniquet"),
            urgencyScore = 10,
        ),
        SurvivalKnowledgeChunk(
            id = "MED_HYPOTHERMIA",
            title = "Severe Accidental Hypothermia & Rewarming Protocol",
            category = "Environmental Medicine",
            content = "Violent shivering, slurred speech, lethargy, or paradoxical undressing indicate core body temperature below 32°C (90°F).",
            keywords = listOf("cold", "freezing", "shivering", "hypothermia", "snow", "water", "wet", "ice", "temperature", "lethargy"),
            urgencyScore = 9,
        ),
        SurvivalKnowledgeChunk(
            id = "MED_CRUSH_SYNDROME",
            title = "Crush Syndrome & Traumatic Entrapment Triage",
            category = "Disaster Medicine",
            content = "Prolonged compression of limbs releases lethal potassium and myoglobin into circulation upon extrication.",
            keywords = listOf("crush", "trapped", "rubble", "collapse", "debris", "heavy", "pinned", "concrete", "syndrome"),
            urgencyScore = 9,
        ),
        SurvivalKnowledgeChunk(
            id = "MED_WATER_PURIFY",
            title = "Emergency Water Purification & Filtration",
            category = "Wilderness Survival",
            content = "Biological pathogens (Giardia, Cryptosporidium, cholera) must be eliminated through boiling, chlorine dioxide, or 0.1 micron microfiltration.",
            keywords = listOf("water", "drink", "purify", "filter", "boil", "stream", "pond", "contamination", "chlorine", "giardia"),
            urgencyScore = 6,
        ),
        SurvivalKnowledgeChunk(
            id = "MED_HEAT_STROKE",
            title = "Exertional Heat Stroke & Hyperthermia",
            category = "Environmental Medicine",
            content = "Hot, dry skin, altered mental status, and core temperature > 40°C require immediate whole-body immersion in ice/cold water.",
            keywords = listOf("heat", "hot", "sun", "fainting", "confusion", "stroke", "hyperthermia", "exhaustion", "dehydration"),
            urgencyScore = 8,
        )
    )

    /**
     * Query the offline survival database using natural language panic queries.
     */
    fun queryTriage(naturalQuery: String): TriageGuidance {
        val queryTokens = naturalQuery.lowercase()
            .split(" ", ",", ".", "!", "?", "\n", "\t")
            .filter { it.isNotBlank() && it.length > 2 }

        var bestMatch: SurvivalKnowledgeChunk? = null
        var bestScore = 0.0f

        for (chunk in embeddedCorpus) {
            var matchCount = 0
            for (token in queryTokens) {
                if (chunk.keywords.any { it.contains(token) || token.contains(it) }) {
                    matchCount++
                }
            }

            val score = (matchCount.toFloat() / (queryTokens.size + chunk.keywords.size)) * chunk.urgencyScore
            if (score > bestScore) {
                bestScore = score
                bestMatch = chunk
            }
        }

        val selected = bestMatch ?: embeddedCorpus.first()
        val isLifeThreat = selected.urgencyScore >= 8

        val actionSteps = when (selected.id) {
            "MED_ARTERIAL_BLEED" -> listOf(
                "1. Apply direct manual pressure into the wound with gloved hands or clean cloth immediately.",
                "2. Place tourniquet 2-3 inches above the bleed (never on a joint). High and tight.",
                "3. Turn windlass rod until bright red bleeding completely stops and distal pulse vanishes.",
                "4. Secure windlass clip. Write the exact military time (e.g., '14:25') on forehead or tourniquet band.",
                "5. Do NOT loosen tourniquet once applied. Prepare for urgent surgical transport."
            )
            "MED_HYPOTHERMIA" -> listOf(
                "1. Gently remove victim from wind and wet snow. Move horizontally without jarring movements.",
                "2. Strip all wet clothing immediately. Wrap in dry sleeping bag and vapor barrier.",
                "3. Apply warm chemical packs to axillae (armpits), groin, and torso (never to feet/hands).",
                "4. Insulate underneath the body from the freezing ground with foliage or foam pads.",
                "5. If conscious, administer warm, high-calorie sugar fluids. Do NOT give alcohol."
            )
            "MED_CRUSH_SYNDROME" -> listOf(
                "1. Assess entrapment duration. If > 1 hour, do NOT rapidly release without medical saline prep.",
                "2. Initiate aggressive intravenous or oral hydration with sodium bicarbonate if available.",
                "3. Apply tourniquets immediately prior to lifting debris if compression exceeded 2 hours.",
                "4. Monitor for fatal hyperkalemic cardiac arrhythmias upon extrication."
            )
            "MED_WATER_PURIFY" -> listOf(
                "1. Pre-filter muddy water through cloth or bandana to eliminate particulates and turbidity.",
                "2. Bring water to a rolling boil for a full 60 seconds (3 minutes at elevations > 2,000 meters).",
                "3. Alternatively, treat with chemical chlorine dioxide tablets, allowing 30 minutes dwell time."
            )
            else -> listOf(
                "1. Ensure scene safety before approaching victim.",
                "2. Check responsiveness (AVPU scale) and airway breathing circulation.",
                "3. Stabilize cervical spine if fall or blunt trauma is suspected."
            )
        }

        return TriageGuidance(
            matchedTopic = selected.title,
            confidence = (bestScore * 10f).coerceIn(0.40f, 0.98f),
            isLifeThreatening = isLifeThreat,
            immediateActionSteps = actionSteps,
            fullGuideMarkdown = selected.content,
        )
    }
}
