package com.bastionzero.db

import app.cash.sqldelight.db.SqlDriver

/** Platform factory for the local SQLite driver. Android needs a Context; iOS does not. */
expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

/** Opens the app database; one-stop entry point for shared code. */
fun createDatabase(factory: DatabaseDriverFactory): BastionDatabase =
    BastionDatabase(factory.createDriver())
