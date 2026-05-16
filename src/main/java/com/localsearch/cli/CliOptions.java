package com.localsearch.cli;

import java.nio.file.Path;
import java.nio.file.Paths;

public class CliOptions
{
    private static final String DEFAULT_SEARCH_FOLDER_NAME = "search bin";

    private String query;
    private int limit = 5;
    private boolean explain;
    private boolean reindex;
    private boolean listIndexed;
    private boolean semantic = true;
    private double semanticWeight = 0.30d;
    private boolean graphExpansion = true;
    private boolean ollama;
    private String ollamaUrl = "http://localhost:11434";
    private String ollamaModel = "nomic-embed-text";
    private int page = 1;
    private boolean noBanner;
    private Path rootDirectory = defaultRootDirectory();
    private Path indexFile = defaultRootDirectory().resolve("index.dat").toAbsolutePath().normalize();

    public String getQuery()
    {
        return query;
    }

    public void setQuery(String query)
    {
        this.query = query;
    }

    public int getLimit()
    {
        return limit;
    }

    public void setLimit(int limit)
    {
        this.limit = limit;
    }

    public boolean isExplain()
    {
        return explain;
    }

    public void setExplain(boolean explain)
    {
        this.explain = explain;
    }

    public boolean isReindex()
    {
        return reindex;
    }

    public void setReindex(boolean reindex)
    {
        this.reindex = reindex;
    }

    public boolean isListIndexed()
    {
        return listIndexed;
    }

    public void setListIndexed(boolean listIndexed)
    {
        this.listIndexed = listIndexed;
    }

    public Path getRootDirectory()
    {
        return rootDirectory;
    }

    public void setRootDirectory(Path rootDirectory)
    {
        this.rootDirectory = rootDirectory.toAbsolutePath().normalize();
    }

    public Path getIndexFile()
    {
        return indexFile;
    }

    public void setIndexFile(Path indexFile)
    {
        this.indexFile = indexFile.toAbsolutePath().normalize();
    }

    public boolean isSemantic()
    {
        return semantic;
    }

    public void setSemantic(boolean semantic)
    {
        this.semantic = semantic;
    }

    public double getSemanticWeight()
    {
        return semanticWeight;
    }

    public void setSemanticWeight(double semanticWeight)
    {
        this.semanticWeight = semanticWeight;
    }

    public boolean isGraphExpansion()
    {
        return graphExpansion;
    }

    public void setGraphExpansion(boolean graphExpansion)
    {
        this.graphExpansion = graphExpansion;
    }

    public boolean isOllama()
    {
        return ollama;
    }

    public void setOllama(boolean ollama)
    {
        this.ollama = ollama;
    }

    public String getOllamaUrl()
    {
        return ollamaUrl;
    }

    public void setOllamaUrl(String ollamaUrl)
    {
        this.ollamaUrl = ollamaUrl;
    }

    public String getOllamaModel()
    {
        return ollamaModel;
    }

    public void setOllamaModel(String ollamaModel)
    {
        this.ollamaModel = ollamaModel;
    }

    public int getPage()
    {
        return page;
    }

    public void setPage(int page)
    {
        this.page = page;
    }

    public boolean isNoBanner()
    {
        return noBanner;
    }

    public void setNoBanner(boolean noBanner)
    {
        this.noBanner = noBanner;
    }

    private static Path defaultRootDirectory()
    {
        String userHome = System.getProperty("user.home", ".");
        return Paths.get(userHome, "Documents", DEFAULT_SEARCH_FOLDER_NAME).toAbsolutePath().normalize();
    }
}
