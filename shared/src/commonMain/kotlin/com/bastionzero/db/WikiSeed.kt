package com.bastionzero.db

/** Tiny bundled starter content so the wiki is usable before the ETL asset is loaded. */
object WikiSeed {
    private val sample = listOf(
        WikiArticleModel(
            1, "Water purification", "water",
            "Boil water at a rolling boil for 1 minute (3 minutes above 2,000 m / 6,500 ft). " +
                "Let it cool covered. Filter cloudy water through cloth first. Sample entry; " +
                "full manuals arrive via data_pipeline.",
        ),
        WikiArticleModel(
            2, "Severe bleeding", "trauma",
            "Apply firm direct pressure with cloth and do not remove soaked layers; add more on top. " +
                "If bleeding on a limb is life-threatening and pressure fails, apply a tourniquet " +
                "2-3 inches above the wound, tighten until bleeding stops, note the time. " +
                "Sample entry, not a substitute for training.",
        ),
        WikiArticleModel(
            3, "Hypothermia", "exposure",
            "Get out of wind and wet, remove wet clothing, insulate from the ground, " +
                "warm the trunk first with dry layers and warm sweet drinks if conscious. " +
                "Sample entry.",
        ),
    )

    fun ensure(repo: WikiRepository) {
        if (repo.count() == 0L) sample.forEach(repo::upsert)
    }
}
