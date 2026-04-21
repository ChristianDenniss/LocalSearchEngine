package com.localsearch.crawler;

import java.nio.file.Path;

public class CrawledFile
{
    private final Path path;
    private final long lastModified;
    private final String content;

    public CrawledFile(Path path, long lastModified, String content)
    {
        this.path = path;
        this.lastModified = lastModified;
        this.content = content;
    }

    public Path getPath()
    {
        return path;
    }

    public long getLastModified()
    {
        return lastModified;
    }

    public String getContent()
    {
        return content;
    }
}
