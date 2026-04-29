package com.localsearch.crawler;

import com.localsearch.model.DocumentRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileCrawlerTest
{
    @TempDir
    Path tempDir;

    @Test
    void ignoresHiddenGitAndNodeModulesDirectories() throws IOException
    {
        Path visible = tempDir.resolve("visible.txt");
        Path git = tempDir.resolve(".git").resolve("config.txt");
        Path nodeModule = tempDir.resolve("node_modules").resolve("lib.txt");
        Path hiddenCustom = tempDir.resolve(".cache").resolve("hidden.txt");

        Files.createDirectories(git.getParent());
        Files.createDirectories(nodeModule.getParent());
        Files.createDirectories(hiddenCustom.getParent());

        Files.writeString(visible, "visible");
        Files.writeString(git, "git");
        Files.writeString(nodeModule, "module");
        Files.writeString(hiddenCustom, "hidden");

        FileCrawler crawler = new FileCrawler();
        List<DocumentRecord> documents = crawler.crawl(tempDir);

        assertEquals(1, documents.size());
        assertEquals(visible.toString(), documents.get(0).getPath());
    }

    @Test
    void indexesNestedJavaFiles() throws IOException
    {
        Path nested = tempDir.resolve("project").resolve("src").resolve("Main.java");
        Files.createDirectories(nested.getParent());
        Files.writeString(nested, "class Main { }");

        List<DocumentRecord> documents = new FileCrawler().crawl(tempDir);

        assertEquals(1, documents.size());
        assertEquals(nested.toString(), documents.get(0).getPath());
    }

    @Test
    void indexesUnknownExtensionByFileNameOnly() throws IOException
    {
        Path known = tempDir.resolve("notes.txt");
        Path unknown = tempDir.resolve("model.sketch");
        Files.writeString(known, "hello world");
        Files.writeString(unknown, "binary-not-indexed");

        List<DocumentRecord> documents = new FileCrawler().crawl(tempDir);

        assertEquals(2, documents.size());
        DocumentRecord sketch = documents.stream()
                .filter(d -> d.getPath().equals(unknown.toString()))
                .findFirst()
                .orElseThrow();
        assertTrue(sketch.getContent().isEmpty());
    }

    @Test
    void skipsExplicitExcludedPath() throws IOException
    {
        Path indexFile = tempDir.resolve("index.dat");
        Path doc = tempDir.resolve("readme.txt");
        Files.writeString(indexFile, "fake index bytes");
        Files.writeString(doc, "hello");

        List<DocumentRecord> documents = new FileCrawler(indexFile).crawl(tempDir);

        assertEquals(1, documents.size());
        assertEquals(doc.toString(), documents.get(0).getPath());
        assertFalse(documents.stream().anyMatch(d -> d.getPath().equals(indexFile.toString())));
    }
}
