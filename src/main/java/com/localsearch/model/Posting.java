package com.localsearch.model;

import java.io.Serial;
import java.io.Serializable;

public class Posting
implements Serializable
{
    @Serial
    private static final long serialVersionUID = 1L;

    private final int docId;
    private final int termFrequency;

    public Posting(int docId, int termFrequency)
    {
        this.docId = docId;
        this.termFrequency = termFrequency;
    }

    public int getDocId()
    {
        return docId;
    }

    public int getTermFrequency()
    {
        return termFrequency;
    }
}
