package com.localsearch.cli;

import com.localsearch.crawler.FileCrawler;
import com.localsearch.index.IndexBuilder;
import com.localsearch.index.IncrementalIndexer;
import com.localsearch.index.IndexPersistence;
import com.localsearch.index.InvertedIndex;
import com.localsearch.model.DocumentRecord;
import com.localsearch.model.SearchResult;
import com.localsearch.ranking.Ranker;
import com.localsearch.search.GraphRetrievalConfig;
import com.localsearch.search.SemanticSearchConfig;
import com.localsearch.search.SearchService;
import com.localsearch.semantic.EmbeddingProvider;
import com.localsearch.semantic.HashingEmbeddingProvider;
import com.localsearch.semantic.OllamaEmbeddingProvider;
import com.localsearch.util.SnippetGenerator;
import com.localsearch.util.Tokenizer;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

public class LocalSearchEngineApp
{
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_DARK_GREEN = "\u001B[32m";
    private boolean interactiveTipShown;

    public static void main(String[] args)
    {
        try
        {
            new LocalSearchEngineApp().run(args);
        }
        catch (Exception exception)
        {
            System.err.println("Error: " + exception.getMessage());
        }
    }

    public void run(String[] args) throws IOException, ClassNotFoundException
    {
        printStartupBanner();
        CliParser cliParser = new CliParser();
        CliOptions options = cliParser.parse(args);
        if (options.getLimit() <= 0)
        {
            throw new IllegalArgumentException("--limit must be greater than 0");
        }
        if (options.getSemanticWeight() < 0.0d || options.getSemanticWeight() > 1.0d)
        {
            throw new IllegalArgumentException("--semantic-weight must be between 0 and 1");
        }

        Tokenizer tokenizer = new Tokenizer();
        EmbeddingProvider embeddingProvider = buildEmbeddingProvider(options, tokenizer);
        InvertedIndex index = prepareIndex(options, tokenizer, embeddingProvider, true);
        if (options.isListIndexed())
        {
            printIndexedDocuments(index, options.getRootDirectory());
            return;
        }
        if (options.getQuery() == null || options.getQuery().isBlank())
        {
            runInteractiveMode(index, tokenizer, cliParser, options.getRootDirectory());
            return;
        }

        runSingleSearch(index, tokenizer, options, options.getRootDirectory());
    }

    private void printStartupBanner()
    {
        String bannerIndent = "  ";
        String[] lines = {
                "",
                "_      ___   ____    _    _",
                "| |    / _ \\ / ___|  / \\  | |",
                "| |   | | | | |     / _ \\ | |",
                "| |___| |_| | |___ / ___ \\| |___",
                "|_____|\\___/ \\____/_/   \\_\\_____|",
                "",
                "____  _____    _    ____   ____ _   _",
                "/ ___|| ____|  / \\  |  _ \\ / ___| | | |",
                "\\___ \\|  _|   / _ \\ | |_) | |   | |_| |",
                " ___) | |___ / ___ \\|  _ <| |___|  _  |",
                "|____/|_____/_/   \\_\\_| \\_\\\\____|_| |_|",
                ""
        };

        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        boolean useAnsiColor = !osName.contains("win") || System.console() != null;
        if (useAnsiColor)
        {
            System.out.print(ANSI_DARK_GREEN);
        }
        for (String line : lines)
        {
            System.out.println(line.isBlank() ? line : bannerIndent + line);
        }
        if (useAnsiColor)
        {
            System.out.print(ANSI_RESET);
        }
    }

    private InvertedIndex prepareIndex(
            CliOptions options,
            Tokenizer tokenizer,
            EmbeddingProvider embeddingProvider,
            boolean logProgress)
            throws IOException, ClassNotFoundException
    {
        ensureRootFolderExists(options.getRootDirectory());

        Path indexFile = options.getIndexFile();
        IndexPersistence persistence = new IndexPersistence();
        FileCrawler fileCrawler = new FileCrawler(indexFile.toAbsolutePath().normalize());

        InvertedIndex index;
        if (options.isReindex() || !persistence.exists(indexFile))
        {
            if (logProgress)
            {
                System.out.println("\nBuilding a new index (full scan): " + options.getRootDirectory());
            }
            List<DocumentRecord> documents = fileCrawler.crawl(options.getRootDirectory());
            index = new IndexBuilder(tokenizer, embeddingProvider).build(documents);
        }
        else
        {
            // Keep index in sync with the filesystem:
            // add new files, remove deleted files, and re-read changed files only.
            if (logProgress)
            {
                System.out.println("\nUpdating saved index (checking for new, removed, or changed files only): "
                        + options.getRootDirectory());
            }
            InvertedIndex existingIndex = persistence.load(indexFile);
            index = new IncrementalIndexer(fileCrawler, tokenizer).reindex(options.getRootDirectory(), existingIndex);
            if (embeddingProvider != null)
            {
                index = new IndexBuilder(tokenizer, embeddingProvider).build(new ArrayList<>(index.getDocumentsById().values()));
            }
        }

        persistence.save(indexFile, index);
        if (logProgress)
        {
            System.out.println("Index ready: " + index.getTotalDocuments() + " document(s). Saved to: " + indexFile);
        }
        if (index.getTotalDocuments() == 0)
        {
            System.out.println("No indexable files found under: " + options.getRootDirectory());
            System.out.println("Full-text extensions: " + FileCrawler.supportedExtensionsSummary());
            System.out.println("(All other regular files are indexed by file name and path only; folders are walked recursively.)");
        }
        return index;
    }

    private void printResults(List<SearchResult> results, List<String> queryTerms, boolean explain, Path rootDirectory)
    {
        if (results.isEmpty())
        {
            System.out.println("No results found.");
            return;
        }

        int count = results.size();
        System.out.println("\n" + (count == 1 ? "1 RESULT FOUND:" : count + " RESULTS FOUND:") + "\n");

        SnippetGenerator snippetGenerator = new SnippetGenerator();
        int rank = 1;
        for (SearchResult result : results)
        {
            System.out.println(rank + ". " + displayPath(result.getDocument().getPath(), rootDirectory));
            System.out.printf("   score: %.4f%n", result.getScore());
            String snippet = snippetGenerator.buildSnippet(result.getDocument().getContent(), queryTerms);
            System.out.println("   snippet: \"" + snippet + "\"");
            if (explain)
            {
                System.out.printf("   explain: tfIdf=%.4f, recencyBoost=%.4f, matchedTerms=%s%n",
                        result.getTermFrequencyScore(),
                        result.getRecencyBoost(),
                        result.getMatchedTerms());
            }
            System.out.println("\n");
            rank++;
        }
    }

    private void printUsage()
    {
        System.out.println("Usage:");
        System.out.println("  search \"query string\" [--limit N] [--explain] [--no-semantic] [--no-graph] [--semantic-weight 0.0-1.0] [--ollama] [--ollama-url URL] [--ollama-model NAME] [--reindex] [--root PATH] [--index PATH]");
        System.out.println("  search  (starts interactive mode)");
        System.out.println("  search list [--root PATH] [--index PATH] [--reindex]  (print all indexed file paths)");
        System.out.println("  default root: ~/Documents/search bin\n");
        System.out.println("Examples:");
        System.out.println("  search \"volleyball stats\"");
        System.out.println("  search \"resume\" --limit 10");
        System.out.println("  search \"john doe\" --explain");
        System.out.println("  search \"project summary\"  (semantic is on by default)");
        System.out.println("  search \"benefits package\" --semantic-weight 0.45");
        System.out.println("  search \"exact token lookup\" --no-semantic");
        System.out.println("  search \"keyword only\" --no-graph");
        System.out.println("  search --reindex --root ./docs \"system design\"");
        System.out.println("  interactive input: volleyball stats --limit 10 --explain");
        System.out.println("  interactive input: list   (or clear, cmds, explain, flags, help)");
    }

    private EmbeddingProvider buildEmbeddingProvider(CliOptions options, Tokenizer tokenizer)
    {
        if (!options.isSemantic())
        {
            return null;
        }
        if (options.isOllama())
        {
            if (!OllamaEmbeddingProvider.isReachable(options.getOllamaUrl()))
            {
                System.out.println("Warning: Ollama not reachable at " + options.getOllamaUrl()
                        + " — falling back to local hashing embedder.");
                return new HashingEmbeddingProvider(tokenizer);
            }
            System.out.println("Using Ollama for semantic embeddings: " + options.getOllamaUrl()
                    + "  model: " + options.getOllamaModel());
            return new OllamaEmbeddingProvider(options.getOllamaUrl(), options.getOllamaModel());
        }
        return new HashingEmbeddingProvider(tokenizer);
    }

    private void ensureRootFolderExists(Path rootDirectory) throws IOException
    {
        if (!Files.exists(rootDirectory))
        {
            Files.createDirectories(rootDirectory);
            System.out.println("Created default search folder: " + rootDirectory);
        }
    }

    private void runInteractiveMode(
            InvertedIndex index,
            Tokenizer tokenizer,
            CliParser cliParser,
            Path defaultSearchRoot)
            throws IOException, ClassNotFoundException
    {
        interactiveTipShown = false;
        System.out.println("Default search folder: " + defaultSearchRoot);
        System.out.println("Type your query without quotes | cmds for list of cmds");
        System.out.println();

        @SuppressWarnings("resource")
        Scanner scanner = new Scanner(System.in);
        List<SearchResult> lastResults = List.of();
        while (true)
        {
            System.out.print("search> ");
            if (!scanner.hasNextLine())
            {
                return;
            }

            String line = scanner.nextLine().trim();
            if (line.isEmpty())
            {
                continue;
            }
            if ("exit".equalsIgnoreCase(line) || "quit".equalsIgnoreCase(line))
            {
                return;
            }
            if ("help".equalsIgnoreCase(line))
            {
                printUsage();
                continue;
            }
            if ("cmds".equalsIgnoreCase(line))
            {
                printInteractiveCommands();
                continue;
            }
            if ("flags".equalsIgnoreCase(line))
            {
                printInteractiveFlags();
                continue;
            }
            if ("explain".equalsIgnoreCase(line))
            {
                printHowSearchWorks();
                continue;
            }
            if ("clear".equalsIgnoreCase(line) || "cls".equalsIgnoreCase(line))
            {
                clearInteractiveConsole();
                continue;
            }
            if (line.toLowerCase(Locale.ROOT).startsWith("open "))
            {
                openResult(line, lastResults);
                continue;
            }
            if (line.toLowerCase(Locale.ROOT).startsWith("location "))
            {
                locationResult(line, lastResults);
                continue;
            }

            String[] parsedArgs = parseInputLine(line);
            CliOptions options = cliParser.parse(parsedArgs);
            if (options.getLimit() <= 0)
            {
                System.out.println("Error: --limit must be greater than 0");
                continue;
            }
            if (options.getSemanticWeight() < 0.0d || options.getSemanticWeight() > 1.0d)
            {
                System.out.println("Error: --semantic-weight must be between 0 and 1");
                continue;
            }

            EmbeddingProvider embeddingProvider = buildEmbeddingProvider(options, tokenizer);
            index = prepareIndex(options, tokenizer, embeddingProvider, false);
            if (options.isListIndexed())
            {
                printIndexedDocuments(index, options.getRootDirectory());
                continue;
            }
            if (options.getQuery() == null || options.getQuery().isBlank())
            {
                System.out.println("Enter a search query, or type cmds / explain / flags / clear / list / help / exit.");
                continue;
            }
            lastResults = runSingleSearch(index, tokenizer, options, options.getRootDirectory());
        }
    }

    private List<SearchResult> runSingleSearch(
            InvertedIndex index,
            Tokenizer tokenizer,
            CliOptions options,
            Path rootDirectory)
    {
        SemanticSearchConfig semanticConfig = options.isSemantic()
                ? new SemanticSearchConfig(true, 1.0d - options.getSemanticWeight(), options.getSemanticWeight(), 0.15d)
                : SemanticSearchConfig.DISABLED;
        GraphRetrievalConfig graphConfig = options.isGraphExpansion()
                ? GraphRetrievalConfig.DEFAULT
                : GraphRetrievalConfig.DISABLED;
        SearchService searchService = new SearchService(tokenizer, new Ranker(), semanticConfig, graphConfig);
        List<SearchResult> results = searchService.search(index, options.getQuery(), options.getLimit());
        printResults(results, tokenizer.tokenize(options.getQuery()), options.isExplain(), rootDirectory);
        maybePrintInteractiveTip(results);
        return results;
    }

    private String[] parseInputLine(String line)
    {
        String[] split = line.split("\\s+");
        String[] args = new String[split.length + 1];
        args[0] = "search";
        System.arraycopy(split, 0, args, 1, split.length);
        return Arrays.stream(args).filter(token -> !token.isBlank()).toArray(String[]::new);
    }

    private void printIndexedDocuments(InvertedIndex index, Path rootDirectory)
    {
        List<DocumentRecord> documents = new ArrayList<>(index.getDocumentsById().values());
        documents.sort(Comparator.comparing(DocumentRecord::getPath, String.CASE_INSENSITIVE_ORDER));
        System.out.println("Indexed documents (" + documents.size() + "):");
        for (DocumentRecord document : documents)
        {
            System.out.println(displayPath(document.getPath(), rootDirectory));
        }
    }

    private void openResult(String input, List<SearchResult> lastResults)
    {
        Integer index = parseResultNumber(input, "open");
        if (index == null)
        {
            return;
        }
        Path path = resolveResultPath(index, lastResults);
        if (path == null)
        {
            return;
        }
        if (!Desktop.isDesktopSupported())
        {
            System.out.println("Open is not supported on this system.");
            return;
        }
        try
        {
            Desktop.getDesktop().open(path.toFile());
            System.out.println("Opened: " + path);
        }
        catch (IOException exception)
        {
            System.out.println("Failed to open file: " + exception.getMessage());
        }
    }

    private void locationResult(String input, List<SearchResult> lastResults)
    {
        Integer index = parseResultNumber(input, "location");
        if (index == null)
        {
            return;
        }
        Path path = resolveResultPath(index, lastResults);
        if (path == null)
        {
            return;
        }
        try
        {
            if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"))
            {
                new ProcessBuilder("explorer.exe", "/select,", path.toString()).start();
            }
            else if (Desktop.isDesktopSupported())
            {
                Desktop.getDesktop().open(path.getParent().toFile());
            }
            else
            {
                System.out.println("Location is not supported on this system.");
                return;
            }
            System.out.println("Opened location for: " + path);
        }
        catch (IOException exception)
        {
            System.out.println("Failed to open location: " + exception.getMessage());
        }
    }

    private Integer parseResultNumber(String input, String command)
    {
        String value = input.substring(command.length()).trim();
        if (value.isEmpty())
        {
            System.out.println("Usage: " + command + " <result_number>");
            return null;
        }
        try
        {
            return Integer.parseInt(value);
        }
        catch (NumberFormatException exception)
        {
            System.out.println("Result number must be an integer.");
            return null;
        }
    }

    private Path resolveResultPath(int resultNumber, List<SearchResult> lastResults)
    {
        if (lastResults == null || lastResults.isEmpty())
        {
            System.out.println("No search results available yet. Run a search first.");
            return null;
        }
        if (resultNumber < 1 || resultNumber > lastResults.size())
        {
            System.out.println("Result number out of range. Valid range: 1-" + lastResults.size());
            return null;
        }
        String pathString = lastResults.get(resultNumber - 1).getDocument().getPath();
        Path path = Path.of(pathString);
        if (!Files.exists(path))
        {
            System.out.println("File no longer exists: " + path);
            return null;
        }
        return path;
    }

    private void printInteractiveCommands()
    {
        System.out.println("Commands: clear, cmds, explain, flags, help, list, open <n>, location <n>, exit, quit");
    }

    private void printHowSearchWorks()
    {
        double graphFactor = GraphRetrievalConfig.DEFAULT.getNeighborBoostFactor();
        int graphSeeds = GraphRetrievalConfig.DEFAULT.getMaxSeeds();
        System.out.println();
        System.out.println("How search works (lexical + semantic + graph)");
        System.out.println();
        System.out.println("LEXICAL (always on unless the query uses the very-short substring path)");
        System.out.println("  Uses an inverted index: your query is split into terms, each term points at");
        System.out.println("  documents where it appears. Scoring is TF-IDF style (rarer terms count more).");
        System.out.println("  File names and paths are indexed too, so a query like \"resume\" can match");
        System.out.println("  resume.pdf even if the body is empty. This is the usual \"keyword\" match.");
        System.out.println();
        System.out.println("SEMANTIC (on by default; turn off for one query with --no-semantic)");
        System.out.println("  Each document gets a fixed-size vector built from its path + text (local");
        System.out.println("  hashing embedder, not a big neural model). The query gets a vector the same way.");
        System.out.println("  Cosine similarity measures how \"close\" the query is to each document.");
        System.out.println("  Only documents above a modest similarity cutoff are kept as semantic hits.");
        System.out.println("  That score is blended with lexical: default mix is 70% lexical / 30% semantic");
        System.out.println("  (change with --semantic-weight 0.0-1.0). Pure vectors when lexical is weak");
        System.out.println("  can still surface related wording.");
        System.out.println();
        System.out.println("GRAPH (on by default; turn off with --no-graph)");
        System.out.println("  At index time, files in the same folder are linked as neighbors.");
        System.out.println("  After lexical+semantic scores are computed, the strongest hits (up to "
                + graphSeeds + ") act as \"seeds.\" Each seed boosts its folder neighbors by up to");
        System.out.println("  (seed score x " + graphFactor + "). That can surface a sibling file that");
        System.out.println("  does not literally contain your query but belongs with a strong hit.");
        System.out.println();
        System.out.println("RECENCY");
        System.out.println("  Slightly prefers newer files on top of the retrieval score above.");
        System.out.println();
        System.out.println("FINAL LIST");
        System.out.println("  Results are sorted by total score, then very weak hits compared to the");
        System.out.println("  best match are dropped so unrelated files do not fill the list.");
        System.out.println();
        System.out.println("PER-RESULT NUMBERS (different from this command)");
        System.out.println("  Add --explain to a query line to print tf-idf, recency, and matched terms");
        System.out.println("  for each hit (e.g. my query --explain).");
        System.out.println();
    }

    /** Same idea as the shell `clear` / Windows `cls`: wipe the terminal scrollback for this session. */
    private static void clearInteractiveConsole()
    {
        try
        {
            if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"))
            {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            }
            else
            {
                System.out.print("\033[3J\033[2J\033[H");
                System.out.flush();
            }
        }
        catch (Exception ignored)
        {
            for (int i = 0; i < 60; i++)
            {
                System.out.println();
            }
        }
    }

    private void printInteractiveFlags()
    {
        System.out.println("Flags (append to any query line, e.g. my topic --limit 10 --explain):");
        System.out.println("  --limit <n>           Max number of results (default: 5)");
        System.out.println("  --explain             Show score breakdown (tf-idf, recency, matched terms)");
        System.out.println("  --reindex             Rebuild index from scratch for this run");
        System.out.println("  --root <path>         Folder to index and search (default: ~/Documents/search bin)");
        System.out.println("  --index <path>        Index file path (default: <root>/index.dat)");
        System.out.println("  --no-semantic         Lexical + recency only for this query (semantic is on by default)");
        System.out.println("  --no-graph            Turn off folder-neighbor graph boost for this query");
        System.out.println("  --semantic-weight <x>  How much to weight vectors vs keywords, 0.0–1.0 (default: 0.3)");
        System.out.println("  --ollama              Use a local Ollama server for neural embeddings instead of the hashing embedder");
        System.out.println("  --ollama-url <url>    Ollama base URL (default: http://localhost:11434)");
        System.out.println("  --ollama-model <name> Ollama embedding model (default: nomic-embed-text)");
    }

    private void maybePrintInteractiveTip(List<SearchResult> results)
    {
        if (!interactiveTipShown && results != null && !results.isEmpty())
        {
            System.out.println("Tip: use `open <number>` or `location <number>`.\n");
            interactiveTipShown = true;
        }
    }

    private String displayPath(String absolutePath, Path rootDirectory)
    {
        try
        {
            Path absolute = Path.of(absolutePath).normalize().toAbsolutePath();
            Path root = rootDirectory.normalize().toAbsolutePath();
            if (absolute.startsWith(root))
            {
                return root.relativize(absolute).toString();
            }
            return absolutePath;
        }
        catch (Exception ignored)
        {
            return absolutePath;
        }
    }
}
