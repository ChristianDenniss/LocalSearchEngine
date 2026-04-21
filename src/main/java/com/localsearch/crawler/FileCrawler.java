package com.localsearch.crawler;

import com.localsearch.model.DocumentRecord;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class FileCrawler
{
    private static final List<String> SUPPORTED_EXTENSIONS = List.of(".txt", ".md");
    private static final List<String> IGNORED_DIRECTORIES = List.of(".git", "node_modules");

    public List<DocumentRecord> crawl(Path rootDirectory) throws IOException
    {
        AtomicInteger idGenerator = new AtomicInteger(1);
        List<DocumentRecord> documents = new ArrayList<>();
        for (CrawledFile file : crawlWithContent(rootDirectory))
        {
            documents.add(new DocumentRecord(
                    idGenerator.getAndIncrement(),
                    file.getPath().toString(),
                    file.getContent(),
                    file.getLastModified()));
        }
        return documents;
    }

    public List<CrawledFile> crawlWithContent(Path rootDirectory) throws IOException
    {
        List<CrawledFile> files = new ArrayList<>();
        for (Path path : crawlPaths(rootDirectory))
        {
            String content = readContent(path);
            if (content != null)
            {
                files.add(new CrawledFile(
                        path,
                        Files.getLastModifiedTime(path).toMillis(),
                        content));
            }
        }
        return files;
    }

    public List<CrawledFile> crawlMetadata(Path rootDirectory) throws IOException
    {
        List<CrawledFile> files = new ArrayList<>();
        for (Path path : crawlPaths(rootDirectory))
        {
            try
            {
                files.add(new CrawledFile(path, Files.getLastModifiedTime(path).toMillis(), null));
            }
            catch (IOException ignored)
            {
                // Skip unreadable files and keep indexing the rest.
            }
        }
        return files;
    }

    public String readContent(Path path)
    {
        try
        {
            return Files.readString(path, StandardCharsets.UTF_8);
        }
        catch (IOException ignored)
        {
            return null;
        }
    }

    private List<Path> crawlPaths(Path rootDirectory) throws IOException
    {
        List<Path> paths = new ArrayList<>();
        Files.walkFileTree(rootDirectory, new SimpleFileVisitor<>()
        {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
            {
                String directoryName = dir.getFileName() == null ? "" : dir.getFileName().toString();
                if (isIgnoredDirectory(directoryName) || isHiddenDirectory(directoryName))
                {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
            {
                if (isTextFile(file))
                {
                    paths.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return paths;
    }

    private boolean isTextFile(Path path)
    {
        String fileName = path.getFileName().toString().toLowerCase();
        return SUPPORTED_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }

    private boolean isIgnoredDirectory(String directoryName)
    {
        return IGNORED_DIRECTORIES.stream().anyMatch(ignored -> ignored.equalsIgnoreCase(directoryName));
    }

    private boolean isHiddenDirectory(String directoryName)
    {
        return directoryName.startsWith(".");
    }
}
