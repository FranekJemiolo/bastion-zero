"""Build-time ETL: Markdown manuals -> offline wiki SQLite database.

Stdlib only. Output schema MUST match shared/src/commonMain/sqldelight/.../Wiki.sq
(WikiArticle table + WikiFts FTS4 index keyed by docid = article id).

Input layout: <source_dir>/<category>/<slug>.md ; the first '# ' heading is the title
(falls back to the file name).

    python wiki_ingestor.py ./manuals ./out/survival_data.db
"""
from __future__ import annotations

import argparse
import re
import sqlite3
import sys
from pathlib import Path

SCHEMA = """
CREATE TABLE IF NOT EXISTS WikiArticle (
    id       INTEGER NOT NULL PRIMARY KEY,
    title    TEXT NOT NULL,
    category TEXT NOT NULL,
    body     TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS WikiFts (
    id       INTEGER NOT NULL PRIMARY KEY,
    title    TEXT NOT NULL,
    body     TEXT NOT NULL
);
"""

_HEADING = re.compile(r"^#\s+(.+?)\s*$", re.MULTILINE)


def parse_markdown(path: Path, root: Path) -> tuple[str, str, str]:
    text = path.read_text(encoding="utf-8")
    m = _HEADING.search(text)
    title = m.group(1) if m else path.stem.replace("-", " ").replace("_", " ").title()
    rel = path.relative_to(root)
    category = rel.parts[0] if len(rel.parts) > 1 else "general"
    body = re.sub(r"\n{3,}", "\n\n", text).strip()
    return title, category, body


def build(source_dir: Path, out_db: Path) -> int:
    files = sorted(source_dir.rglob("*.md"))
    out_db.parent.mkdir(parents=True, exist_ok=True)
    if out_db.exists():
        out_db.unlink()
    con = sqlite3.connect(out_db)
    try:
        con.executescript(SCHEMA)
        for i, f in enumerate(files, start=1):  # stable ids: sorted path order
            title, category, body = parse_markdown(f, source_dir)
            con.execute(
                "INSERT INTO WikiArticle(id, title, category, body) VALUES (?,?,?,?)",
                (i, title, category, body),
            )
            con.execute(
                "INSERT INTO WikiFts(id, title, body) VALUES (?,?,?)", (i, title, body)
            )
        con.commit()
        con.execute("VACUUM")
    finally:
        con.close()
    return len(files)


def main(argv: list[str]) -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("source_dir", type=Path)
    ap.add_argument("out_db", type=Path)
    args = ap.parse_args(argv)
    if not args.source_dir.is_dir():
        ap.error(f"not a directory: {args.source_dir}")
    n = build(args.source_dir, args.out_db)
    print(f"Ingested {n} article(s) -> {args.out_db}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
