# Local Search Engine Setup

This page is only for installing and setting up the app.

## Prerequisites

- Java 17+
- Maven 3.8+

Verify:

```bash
java -version
mvn -version
```

## Build the app

From project root:

```bash
mvn clean package
```

Output jar:

- `dist/local-search-engine-1.0.0.jar`

## Install global `search` command (Windows)

From project root:

```powershell
powershell -ExecutionPolicy Bypass -File .\install-search-command.ps1
```

This script:

- builds the jar if needed
- creates `%USERPROFILE%\search-bin\search.cmd`
- adds `%USERPROFILE%\search-bin` to your user PATH

Then open a new terminal and run:

```powershell
search
```

## First run

Run:

```bash
java -jar dist/local-search-engine-1.0.0.jar search
```

On first run (without `--root`), the app creates and uses:

- `~/Documents/search bin`

## More docs

- Project overview and architecture: `PROJECT_OVERVIEW.md`
