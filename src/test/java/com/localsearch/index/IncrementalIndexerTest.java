package com.localsearch.index;

import com.localsearch.crawler.FileCrawler;
import com.localsearch.model.DocumentRecord;
import com.localsearch.util.Tokenizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncrementalIndexerTest
{
    @TempDir
    Path tempDir;

    @Test
    void keepsUnchangedAndUpdatesChangedAndRemovesDeleted() throws IOException
    {
        Path unchanged = tempDir.resolve("unchanged.txt");
        Path changed = tempDir.resolve("changed.txt");
        Path deleted = tempDir.resolve("deleted.txt");
        Files.writeString(unchanged, "same");
        Files.writeString(changed, "old");
        Files.writeString(deleted, "remove me");

        Tokenizer tokenizer = new Tokenizer();
        FileCrawler crawler = new FileCrawler();
        InvertedIndex initial = new IndexBuilder(tokenizer).build(crawler.crawl(tempDir));

        Files.writeString(changed, "new content");
        Files.delete(deleted);
        Path added = tempDir.resolve("added.md");
        Files.writeString(added, "added doc");

        InvertedIndex reindexed = new IncrementalIndexer(crawler, tokenizer).reindex(tempDir, initial);
        List<String> paths = reindexed.getDocumentsById()
                .values()
                .stream()
                .map(DocumentRecord::getPath)
                .toList();

        assertEquals(3, reindexed.getTotalDocuments());
        assertTrue(paths.contains(unchanged.toString()));
        assertTrue(paths.contains(changed.toString()));
        assertTrue(paths.contains(added.toString()));
    }
}
