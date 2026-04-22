package com.localsearch.index;

import com.localsearch.crawler.CrawledFile;
import com.localsearch.crawler.FileCrawler;
import com.localsearch.model.DocumentRecord;
import com.localsearch.util.Tokenizer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class IncrementalIndexer
{
    private final FileCrawler fileCrawler;
    private final IndexBuilder indexBuilder;

    public IncrementalIndexer(FileCrawler fileCrawler, Tokenizer tokenizer)
    {
        this.fileCrawler = fileCrawler;
        this.indexBuilder = new IndexBuilder(tokenizer);
    }

    public InvertedIndex reindex(Path rootDirectory, InvertedIndex existingIndex) throws IOException
    {
        List<CrawledFile> latestFiles = fileCrawler.crawlMetadata(rootDirectory);
        Map<String, DocumentRecord> existingByPath = buildPathMap(existingIndex);
        List<DocumentRecord> finalDocuments = new ArrayList<>();
        AtomicInteger idGenerator = new AtomicInteger(1);

        for (CrawledFile latest : latestFiles)
        {
            String path = latest.getPath().toString();
            DocumentRecord existing = existingByPath.get(path);
            if (existing != null && existing.getLastModified() == latest.getLastModified())
            {
                finalDocuments.add(new DocumentRecord(
                        idGenerator.getAndIncrement(),
                        existing.getPath(),
                        existing.getContent(),
                        existing.getLastModified()));
                continue;
            }

            String content = readContent(latest.getPath());
            finalDocuments.add(new DocumentRecord(
                    idGenerator.getAndIncrement(),
                    path,
                    content,
                    latest.getLastModified()));
        }

        return indexBuilder.build(finalDocuments);
    }

    private String readContent(Path path)
    {
        return fileCrawler.readContent(path);
    }

    private Map<String, DocumentRecord> buildPathMap(InvertedIndex index)
    {
        Map<String, DocumentRecord> byPath = new HashMap<>();
        for (DocumentRecord document : index.getDocumentsById().values())
        {
            byPath.put(document.getPath(), document);
        }
        return byPath;
    }
}
