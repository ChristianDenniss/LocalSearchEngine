package com.localsearch.model;

import java.io.Serial;
import java.io.Serializable;

public class DocumentRecord
implements Serializable
{
    @Serial
    private static final long serialVersionUID = 1L;

    private final int id;
    private final String path;
    private final String content;
    private final long lastModified;

    public DocumentRecord(int id, String path, String content, long lastModified)
    {
        this.id = id;
        this.path = path;
        this.content = content;
        this.lastModified = lastModified;
    }

    public int getId()
    {
        return id;
    }

    public String getPath()
    {
        return path;
    }

    public String getContent()
    {
        return content;
    }

    public long getLastModified()
    {
        return lastModified;
    }
}
