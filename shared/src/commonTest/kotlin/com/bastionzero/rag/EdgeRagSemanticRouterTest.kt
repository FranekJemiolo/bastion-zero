package com.bastionzero.rag

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EdgeRagSemanticRouterTest {

    private val router = EdgeRagSemanticRouter()

    @Test
    fun testArterialHemorrhageTriageRouting() {
        val query = "My partner fell from tree, deep cut on thigh, blood is bright red and spurting heavily!"
        val response = router.routePanickedQuery(query)

        assertEquals(TriageUrgency.P1_IMMEDIATE_LIFE_THREAT, response.urgencyLevel)
        assertTrue(response.detectedTraumaCategories.contains("MASSIVE_ARTERIAL_HEMORRHAGE"))
        assertEquals("SOS_MED_ARTERIAL_BLEED", response.emergencySurvivalPacketCode)
        assertTrue(response.immediateActions.isNotEmpty())
        assertEquals("APPLY TOURNIQUET IMMEDIATELY", response.immediateActions[0].title)
    }

    @Test
    fun testCompoundFractureTriageRouting() {
        val query = "Hiker slipped on wet rock, bone sticking out of the leg and severe pain"
        val response = router.routePanickedQuery(query)

        assertTrue(response.detectedTraumaCategories.contains("COMPOUND_OPEN_FRACTURE"))
        assertTrue(response.immediateActions.any { it.title.contains("SPLINT") })
    }

    @Test
    fun testHypothermiaTriageRouting() {
        val query = "Trapped in freezing cold blizzard, survivor stopped shivering and is slurring words confused"
        val response = router.routePanickedQuery(query)

        assertTrue(response.detectedTraumaCategories.contains("SEVERE_SYSTEMIC_HYPOTHERMIA"))
        assertTrue(response.immediateActions.any { it.title.contains("BURRITO WRAP") })
    }
}
