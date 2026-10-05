# data_pipeline

Build-time ETL tools. Nothing here ships in the app; outputs are bundled as assets.

| Script | Purpose | Status |
| --- | --- | --- |
| `wiki_ingestor.py` | Markdown manuals -> FTS4 SQLite wiki DB (schema mirrors `Wiki.sq`) | Phase 1 |
| `osm_compressor.py` | OSM extract -> offline `.mbtiles` | Planned |

```bash
cd data_pipeline
python wiki_ingestor.py ./manuals ./out/survival_data.db
python -m unittest
```
