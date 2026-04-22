package com.localsearch.crawler;

import com.localsearch.model.DocumentRecord;
import com.localsearch.util.DocumentTextExtractor;

import java.io.IOException;
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
    /**
     * Extensions whose contents are parsed for full-text indexing (case-insensitive suffix).
     * UTF-8 text; PDF / Office use dedicated parsers. Any other regular file is still listed and
     * indexed by file name (and path) only — contents are not read.
     */
    private static final List<String> SUPPORTED_EXTENSIONS = List.of(
            ".pdf",
            ".docx",
            ".xlsx", ".xlsm", ".xls",
            ".pptx",
            ".txt", ".md", ".markdown",
            ".java", ".kt", ".kts",
            ".py", ".js", ".mjs", ".cjs", ".ts", ".jsx", ".tsx",
            ".c", ".cc", ".cpp", ".h", ".hpp",
            ".cs", ".go", ".rs", ".rb", ".php", ".swift", ".scala",
            ".json", ".xml", ".yaml", ".yml", ".toml",
            ".html", ".htm", ".css", ".scss", ".sass", ".less",
            ".sql", ".log", ".csv", ".tsv",
            ".sh", ".bash", ".zsh", ".ps1", ".bat", ".cmd",
            ".gradle", ".properties", ".ini", ".cfg", ".conf", ".env",
            ".rst", ".adoc");

    private static final List<String> IGNORED_DIRECTORIES = List.of(".git", "node_modules");

    public static String supportedExtensionsSummary()
    {
        return String.join(", ", SUPPORTED_EXTENSIONS);
    }

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
            files.add(new CrawledFile(
                    path,
                    Files.getLastModifiedTime(path).toMillis(),
                    content));
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

    /**
     * Full document text for supported formats; empty string when we only index the file name
     * (unknown extension or failed extraction). Never null.
     */
    public String readContent(Path path)
    {
        if (path == null || path.getFileName() == null)
        {
            return "";
        }
        if (!hasScannableContentExtension(path))
        {
            return "";
        }
        String extracted = DocumentTextExtractor.readPlainText(path);
        return extracted != null ? extracted : "";
    }

    /**
     * True for regular files that appear in the index (any extension under the crawl rules).
     */
    public boolean isIndexedFile(Path path)
    {
        if (path == null || path.getFileName() == null)
        {
            return false;
        }
        try
        {
            return Files.isRegularFile(path);
        }
        catch (SecurityException ignored)
        {
            return false;
        }
    }

    /** True if we attempt to read and parse file contents for full-text indexing. */
    public boolean hasScannableContentExtension(Path path)
    {
        return hasScannableContentExtensionInternal(path);
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
                if (attrs.isRegularFile() && isIndexableFilePath(file))
                {
                    paths.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return paths;
    }

    private boolean isIndexableFilePath(Path path)
    {
        if (path.getFileName() == null)
        {
            return false;
        }
        return !path.getFileName().toString().isEmpty();
    }

    private boolean hasScannableContentExtensionInternal(Path path)
    {
        if (path.getFileName() == null)
        {
            return false;
        }
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
