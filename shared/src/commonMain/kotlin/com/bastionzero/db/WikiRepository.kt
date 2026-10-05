package com.bastionzero.db

data class WikiArticleModel(val id: Long, val title: String, val category: String, val body: String)

/** Read/write access to the offline wiki, including FTS search. */
class WikiRepository(private val db: BastionDatabase) {
    private val q get() = db.wikiQueries

    /** Insert or replace an article and keep the FTS index in step, atomically. */
    fun upsert(article: WikiArticleModel) {
        db.transaction {
            q.insertArticle(article.id, article.title, article.category, article.body)
            q.deleteFts(article.id)
            q.insertFts(article.id, article.title, article.body)
        }
    }

    fun get(id: Long): WikiArticleModel? =
        q.articleById(id, ::WikiArticleModel).executeAsOneOrNull()

    fun count(): Long = q.articleCount().executeAsOne()

    fun all(): List<WikiArticleModel> = q.listAll(::WikiArticleModel).executeAsList()

    fun byCategory(category: String): List<WikiArticleModel> =
        q.listByCategory(category, ::WikiArticleModel).executeAsList()

    /**
     * Prefix-matching search over title and body. User input is reduced to
     * alphanumeric tokens so FTS operators ('"', '*', 'OR', ':') cannot
     * raise syntax errors under stress.
     */
    fun search(userQuery: String, limit: Long = 20): List<WikiArticleModel> {
        val match = toFtsQuery(userQuery) ?: return emptyList()
        return q.search(match, limit, ::WikiArticleModel).executeAsList()
    }

    companion object {
        private val STOP_WORDS = setOf("or", "and", "not")

        internal fun toFtsQuery(input: String): String? {
            val tokens = input
                .map { if (it.isLetterOrDigit()) it else ' ' }
                .joinToString("")
                .split(' ')
                .map { it.lowercase() }
                .filter { it.isNotEmpty() && it !in STOP_WORDS }
            if (tokens.isEmpty()) return null
            return tokens.joinToString(" ")
        }
    }
}
