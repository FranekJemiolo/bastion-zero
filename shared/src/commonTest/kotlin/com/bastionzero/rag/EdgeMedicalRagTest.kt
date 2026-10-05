package com.bastionzero.rag

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EdgeMedicalRagTest {

    @Test
    fun testArterialBleedPanicQuery() {
        val rag = EdgeMedicalRag()

        val guidance = rag.queryTriage(
            "My friend is bleeding heavily from their thigh, bright red blood is spurting out what do I do"
        )

        assertNotNull(guidance)
        assertEquals("Severe Arterial Bleeding & Tourniquet Application", guidance.matchedTopic)
        assertTrue(guidance.isLifeThreatening)
        assertTrue(guidance.confidence > 0.5f)
        assertTrue(guidance.immediateActionSteps.any { it.contains("tourniquet") })
    }

    @Test
    fun testHypothermiaQuery() {
        val rag = EdgeMedicalRag()

        val guidance = rag.queryTriage(
            "Victim fell into freezing cold river water and is shivering violently and confused"
        )

        assertNotNull(guidance)
        assertEquals("Severe Accidental Hypothermia & Rewarming Protocol", guidance.matchedTopic)
        assertTrue(guidance.isLifeThreatening)
        assertTrue(guidance.immediateActionSteps.any { it.contains("Strip all wet clothing") })
    }
}
