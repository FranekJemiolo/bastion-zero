"""Sanity test: ingest sample manuals and query via FTS4 (run: python -m unittest)."""
import sqlite3
import tempfile
import unittest
from pathlib import Path

from wiki_ingestor import build


class IngestorTest(unittest.TestCase):
    def test_roundtrip_and_search(self):
        with tempfile.TemporaryDirectory() as d:
            src = Path(d) / "src"
            (src / "water").mkdir(parents=True)
            (src / "water" / "boil.md").write_text("# Water purification\n\nBoil for one minute.\n")
            (src / "trauma").mkdir()
            (src / "trauma" / "tq.md").write_text("Tourniquet\n\nTighten above the wound.\n")
            db = Path(d) / "out" / "w.db"
            self.assertEqual(build(src, db), 2)
            con = sqlite3.connect(db)
            hits = con.execute(
                "SELECT a.title, a.category FROM WikiArticle a "
                "WHERE a.id IN (SELECT rowid FROM WikiFts WHERE WikiFts MATCH 'purif*')"
            ).fetchall()
            self.assertEqual(hits, [("Water purification", "water")])


if __name__ == "__main__":
    unittest.main()
