# Usage Guide

## Prerequisites

- Java 17+
- Maven 3.8+

## Build

From project root:

```bash
mvn clean package
```

Output jar:

- `dist/local-search-engine-1.0.0.jar`

## Install global `search` command (Windows)

From project root, run:

```powershell
powershell -ExecutionPolicy Bypass -File .\install-search-command.ps1
```

This script:

- builds the jar if needed
- creates `%USERPROFILE%\search-bin\search.cmd`
- adds `%USERPROFILE%\search-bin` to your **user** PATH

After running it, open a **new** terminal and use:

```powershell
search
```

Then at the `search>` prompt, type:

```text
volleyball stats
resume --limit 10 --explain
list
```

While interactive mode is open, **each** `list` or search line triggers a disk sync (incremental when an index already exists), so new or changed files under the search folder are picked up before that command runs. You do not need to restart the app.

If you still see no results: confirm files are under the **Default search folder** line printed at startup (often `Documents\search bin`), and that file names end with a **supported extension** (see below). Binary formats like `.pdf` or `.docx` are not indexed.

## List indexed files

Print every file path currently in the index (after the usual startup sync):

```bash
java -jar dist/local-search-engine-1.0.0.jar search list
```

Or from interactive mode at the `search>` prompt:

```text
list
```

Optional flags (same as search), for example:

```bash
java -jar dist/local-search-engine-1.0.0.jar search list --root "C:\path\to\docs" --index "C:\path\to\index.dat"
```

**Note:** A line that is exactly `list` is the list command, not a search for the word “list”. To search for that word alone, use interactive mode and a phrase that is not only `list`, or pass extra context.

## First run

Run:

```bash
java -jar dist/local-search-engine-1.0.0.jar search
```

On first run (without `--root`), the app creates:

- `~/Documents/search bin`

and indexes files there.

## Common commands

Basic search (interactive prompt):

```bash
java -jar dist/local-search-engine-1.0.0.jar search
# then type: volleyball stats
```

Limit results:

```bash
java -jar dist/local-search-engine-1.0.0.jar search
# then type: resume --limit 10
```

Explain scoring:

```bash
java -jar dist/local-search-engine-1.0.0.jar search
# then type: john doe --explain
```

Use custom root:

```bash
java -jar dist/local-search-engine-1.0.0.jar search "system design" --root "C:\docs"
```

Use custom index file:

```bash
java -jar dist/local-search-engine-1.0.0.jar search "api notes" --index "C:\tmp\index.dat"
```

Force full rebuild:

```bash
java -jar dist/local-search-engine-1.0.0.jar search "query" --reindex
```

## Incremental sync behavior

When index exists and `--reindex` is not passed, startup sync:

- adds newly discovered files
- removes deleted files
- reindexes changed files only
- keeps unchanged files as-is

## Supported file types

Subfolders are scanned recursively. Files are indexed when their names end with a supported suffix:

- **PDF** (including “Print to PDF” / Chrome saves): `.pdf` — text is extracted with [Apache PDFBox](https://pdfbox.apache.org/). *Scanned image-only PDFs with no text layer produce little or no searchable text until you OCR them elsewhere.*
- **Microsoft Office (Open XML):** `.docx`, `.xlsx`, `.xlsm`, `.pptx`, and legacy **`.xls`** spreadsheets — parsed with [Apache POI](https://poi.apache.org/).
- **Plain text and code:** `.txt`, `.md`, `.markdown`, `.rst`, `.adoc`, `.java`, `.py`, `.js`, `.ts`, `.json`, `.xml`, `.html`, `.sql`, … (UTF-8; invalid UTF-8 text files may be skipped)

The full suffix list is in `FileCrawler` (`SUPPORTED_EXTENSIONS` / `supportedExtensionsSummary()`). **Not supported** here: `.doc` (pre-2007 Word binary), `.ppt` (old PowerPoint), `.odt`, images, etc.

The packaged `dist/local-search-engine-1.0.0.jar` is a **shaded** (“fat”) JAR that includes PDFBox and POI so `java -jar …` works without a separate `lib` folder.

## Output format

Each result includes:

- file path
- score
- snippet
- optional explain details (`--explain`)

## Notes

- Query text supports multiple words.
- **Normal search** tokenizes the query (lowercase, punctuation removed, split on whitespace) and uses the inverted index on **tokens of length 2+** (single-letter PDF garbage is not indexed).
- **Short queries** (1–2 characters after cleanup, or any single-letter token) use **substring matching** on full document text instead of the token index, so `hi` or `gpa` can still match inside words or messy PDF extraction.
- If the token index finds nothing but your query is 2+ characters (e.g. `gpa` with no `gpa` token), a **substring fallback** runs automatically.
- If no matches are found, output is `No results found.`
