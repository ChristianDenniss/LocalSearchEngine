# Local Search Engine

A plain Java CLI search engine that indexes UTF-8 text files plus common **PDF** and **Office Open XML** documents (`.docx`, `.xlsx`, `.pptx`, etc.), using [Apache PDFBox](https://pdfbox.apache.org/) and [Apache POI](https://poi.apache.org/) for extraction. Search itself stays in plain Java (no Lucene). The release JAR is **shaded** so dependencies are bundled for `java -jar`.

## What it does

- Crawls a root folder recursively (nested folders included)
- Builds an in-memory inverted index
- Persists index data to disk
- Supports keyword search with ranking
- Semantic retrieval is on by default (disable per-search with `--no-semantic`)
- Offline **relationship graph** (same parent folder) is built at index time; at query time neighbors of strong hits get a small score boost (disable with `--no-graph`)
- Applies recency boosting
- Generates snippets with matched term highlighting
- On each run it **updates** the saved index incrementally (not a full rebuild unless you use `--reindex` or the index file is missing): it checks the folder for new, removed, or changed files only
- The **index file itself** (e.g. `index.dat`) is never crawled as a document, even when it lives under the search folder
- Search results drop **very weak** hits compared to the best score so unrelated files do not fill the list
- In interactive mode, it does that same quick update before every search and `list`, so new files show up without restarting
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

With semantic mode (default):

- `score = (lexicalWeight * log(1 + tfIdf) + semanticWeight * cosineSimilarity) + recencyBoost`

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

## Console and PDFs

Some PDFs trigger Apache PDFBox warnings about embedded font names (for example “No PostScript name data…”). Those messages are technical noise and rarely affect extracted text, so the app **filters that specific line** from console output.

## More details

See `README.md` for installation and setup.
