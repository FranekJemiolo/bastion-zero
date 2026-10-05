package com.bastionzero.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WikiRepositoryTest {
    private lateinit var repo: WikiRepository

    @BeforeTest
    fun setUp() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        BastionDatabase.Schema.create(driver)
        repo = WikiRepository(BastionDatabase(driver))
        repo.upsert(WikiArticleModel(1, "Water purification", "water", "Boil water for one minute at a rolling boil."))
        repo.upsert(WikiArticleModel(2, "Tourniquet application", "trauma", "Apply two inches above the wound and tighten."))
    }

    @Test
    fun findsByPrefixAcrossTitleAndBody() {
        assertEquals(listOf(1L), repo.search("purif").map { it.id })
        assertEquals(listOf(2L), repo.search("wound").map { it.id })
    }

    @Test
    fun hostileQueryDoesNotThrow() {
        assertTrue(repo.search("\"*) OR :").isEmpty())
        assertTrue(repo.search("   ").isEmpty())
    }

    @Test
    fun upsertReplacesIndexEntry() {
        repo.upsert(WikiArticleModel(1, "Water purification", "water", "Use iodine tablets."))
        assertTrue(repo.search("boil").isEmpty())
        assertEquals(listOf(1L), repo.search("iodine").map { it.id })
        assertEquals(2, repo.count())
    }

    @Test
    fun getAndCategory() {
        assertEquals("trauma", repo.get(2)?.category)
        assertNull(repo.get(99))
        assertEquals(listOf(1L), repo.byCategory("water").map { it.id })
    }
}
