package com.localsearch.cli;

import com.localsearch.crawler.FileCrawler;
import com.localsearch.index.IndexBuilder;
import com.localsearch.index.IncrementalIndexer;
import com.localsearch.index.IndexPersistence;
import com.localsearch.index.InvertedIndex;
import com.localsearch.model.DocumentRecord;
import com.localsearch.model.SearchResult;
import com.localsearch.ranking.Ranker;
import com.localsearch.search.SearchService;
import com.localsearch.util.SnippetGenerator;
import com.localsearch.util.Tokenizer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class LocalSearchEngineApp
{
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
        CliOptions options = new CliParser().parse(args);
        if (options.getLimit() <= 0)
        {
            throw new IllegalArgumentException("--limit must be greater than 0");
        }

        Tokenizer tokenizer = new Tokenizer();
        InvertedIndex index = prepareIndex(options, tokenizer);
        if (options.getQuery() == null || options.getQuery().isBlank())
        {
            printUsage();
            return;
        }

        SearchService searchService = new SearchService(tokenizer, new Ranker());
        List<SearchResult> results = searchService.search(index, options.getQuery(), options.getLimit());
        printResults(results, tokenizer.tokenize(options.getQuery()), options.isExplain());
    }

    private InvertedIndex prepareIndex(CliOptions options, Tokenizer tokenizer)
            throws IOException, ClassNotFoundException
    {
        ensureRootFolderExists(options.getRootDirectory());

        Path indexFile = options.getIndexFile();
        IndexPersistence persistence = new IndexPersistence();
        FileCrawler fileCrawler = new FileCrawler();

        InvertedIndex index;
        if (options.isReindex() || !persistence.exists(indexFile))
        {
            System.out.println("Building index from root directory: " + options.getRootDirectory());
            List<DocumentRecord> documents = fileCrawler.crawl(options.getRootDirectory());
            index = new IndexBuilder(tokenizer).build(documents);
        }
        else
        {
            // Keep index in sync with the filesystem on every startup:
            // add new files, remove deleted files, and re-read changed files only.
            System.out.println("Syncing index with current files in: " + options.getRootDirectory());
            InvertedIndex existingIndex = persistence.load(indexFile);
            index = new IncrementalIndexer(fileCrawler, tokenizer).reindex(options.getRootDirectory(), existingIndex);
        }

        persistence.save(indexFile, index);
        System.out.println("Indexed " + index.getTotalDocuments() + " document(s). Saved to: " + indexFile);
        return index;
    }

    private void printResults(List<SearchResult> results, List<String> queryTerms, boolean explain)
    {
        if (results.isEmpty())
        {
            System.out.println("No results found.");
            return;
        }

        SnippetGenerator snippetGenerator = new SnippetGenerator();
        int rank = 1;
        for (SearchResult result : results)
        {
            System.out.println(rank + ". " + result.getDocument().getPath());
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
            System.out.println();
            rank++;
        }
    }

    private void printUsage()
    {
        System.out.println("Usage:");
        System.out.println("  search \"query string\" [--limit N] [--explain] [--reindex] [--root PATH] [--index PATH]");
        System.out.println("  default root: ~/Documents/search bin");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  search \"volleyball stats\"");
        System.out.println("  search \"resume\" --limit 10");
        System.out.println("  search \"john doe\" --explain");
        System.out.println("  search --reindex --root ./docs \"system design\"");
    }

    private void ensureRootFolderExists(Path rootDirectory) throws IOException
    {
        if (!Files.exists(rootDirectory))
        {
            Files.createDirectories(rootDirectory);
            System.out.println("Created default search folder: " + rootDirectory);
        }
    }
}
