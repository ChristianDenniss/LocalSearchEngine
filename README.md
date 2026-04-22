# Local Search Engine

A plain Java CLI search engine that indexes UTF-8 text files plus common **PDF** and **Office Open XML** documents (`.docx`, `.xlsx`, `.pptx`, etc.), using [Apache PDFBox](https://pdfbox.apache.org/) and [Apache POI](https://poi.apache.org/) for extraction. Search itself stays in plain Java (no Lucene). The release JAR is **shaded** so dependencies are bundled for `java -jar`.

## What it does

- Crawls a root folder recursively (nested folders included)
- Builds an in-memory inverted index
- Persists index data to disk
- Supports keyword search with ranking
- Applies recency boosting
- Generates snippets with matched term highlighting
- Syncs index incrementally on startup (add/remove/update awareness)
- In interactive mode, re-syncs from disk before every search and `list`, so new files are indexed without restarting
- `list` command prints all indexed file paths

## Default behavior

- On first run, the app creates:
  - `~/Documents/search bin`
- That folder is used as the default search root.
- Index file is stored at:
  - `~/Documents/search bin/index.dat`

You can override both with CLI flags.

## Project structure

- `src/main/java/com/localsearch/cli` - CLI parsing and app entrypoint
- `src/main/java/com/localsearch/crawler` - file crawling + ignore rules
- `src/main/java/com/localsearch/index` - index model/build/persistence/incremental sync
- `src/main/java/com/localsearch/search` - query execution and scoring
- `src/main/java/com/localsearch/ranking` - recency boost logic
- `src/main/java/com/localsearch/model` - core domain models
- `src/main/java/com/localsearch/util` - tokenizer and snippet generation
- `src/test/java/com/localsearch` - unit tests

## Ranking model

Document score combines:

- TF-IDF lexical score
- Recency boost

Final score:

- `score = tfIdf + recencyBoost`

## Ignore rules

Crawler ignores:

- `.git`
- `node_modules`
- hidden directories (folder names starting with `.`)

## Build and test

- Build:
  - `mvn clean package`
- Run tests:
  - `mvn test`

## More details

See `USAGE.md` for full command examples and CLI options.
