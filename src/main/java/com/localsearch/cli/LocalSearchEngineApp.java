package com.localsearch.cli;

import com.localsearch.crawler.FileCrawler;
import com.localsearch.index.IndexBuilder;
import com.localsearch.index.IncrementalIndexer;
import com.localsearch.index.IndexPersistence;
import com.localsearch.index.InvertedIndex;
import com.localsearch.model.DocumentRecord;
import com.localsearch.model.SearchResult;
import com.localsearch.ranking.Ranker;
import com.localsearch.search.SemanticSearchConfig;
import com.localsearch.search.SearchService;
import com.localsearch.semantic.EmbeddingProvider;
import com.localsearch.semantic.HashingEmbeddingProvider;
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
        EmbeddingProvider embeddingProvider = options.isSemantic() ? new HashingEmbeddingProvider(tokenizer) : null;
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
        FileCrawler fileCrawler = new FileCrawler();

        InvertedIndex index;
        if (options.isReindex() || !persistence.exists(indexFile))
        {
            if (logProgress)
            {
                System.out.println("Building index from root directory: " + options.getRootDirectory());
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
                System.out.println("Syncing index with current files in: " + options.getRootDirectory());
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
            System.out.println("Indexed " + index.getTotalDocuments() + " document(s). Saved to: " + indexFile);
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
        System.out.println("  search \"query string\" [--limit N] [--explain] [--no-semantic] [--semantic-weight 0.0-1.0] [--reindex] [--root PATH] [--index PATH]");
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
        System.out.println("  search --reindex --root ./docs \"system design\"");
        System.out.println("  interactive input: volleyball stats --limit 10 --explain");
        System.out.println("  interactive input: list");
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
        System.out.println("Interactive mode started.");
        System.out.println("Default search folder: " + defaultSearchRoot);
        System.out.println("Type your query without quotes. Example: volleyball stats --limit 10 --explain");
        printInteractiveCommands();

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

            EmbeddingProvider embeddingProvider = options.isSemantic() ? new HashingEmbeddingProvider(tokenizer) : null;
            index = prepareIndex(options, tokenizer, embeddingProvider, false);
            if (options.isListIndexed())
            {
                printIndexedDocuments(index, options.getRootDirectory());
                continue;
            }
            if (options.getQuery() == null || options.getQuery().isBlank())
            {
                System.out.println("Enter a search query, or type cmds / list / help / exit.");
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
        SearchService searchService = new SearchService(tokenizer, new Ranker());
        if (options.isSemantic())
        {
            double semanticWeight = options.getSemanticWeight();
            double lexicalWeight = 1.0d - semanticWeight;
            searchService = new SearchService(
                    tokenizer,
                    new Ranker(),
                    new SemanticSearchConfig(true, lexicalWeight, semanticWeight, 0.15d));
        }
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
        System.out.println("Commands: cmds, help, list, open <n>, location <n>, exit, quit");
        System.out.println("Flags per search: --no-semantic, --semantic-weight <0.0-1.0>, --limit <n>, --explain");
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
