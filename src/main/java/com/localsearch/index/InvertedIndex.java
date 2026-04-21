package com.localsearch.index;

import com.localsearch.model.DocumentRecord;
import com.localsearch.model.Posting;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InvertedIndex
implements Serializable
{
    @Serial
    private static final long serialVersionUID = 1L;

    private final Map<String, List<Posting>> postingsByTerm = new HashMap<>();
    private final Map<String, Integer> documentFrequencyByTerm = new HashMap<>();
    private final Map<Integer, DocumentRecord> documentsById = new HashMap<>();
    private int totalDocuments;

    public void addPosting(String term, Posting posting)
    {
        postingsByTerm.computeIfAbsent(term, ignored -> new ArrayList<>()).add(posting);
    }

    public List<Posting> getPostings(String term)
    {
        return postingsByTerm.getOrDefault(term, Collections.emptyList());
    }

    public Map<String, Integer> getDocumentFrequencyByTerm()
    {
        return Collections.unmodifiableMap(documentFrequencyByTerm);
    }

    public void setDocumentFrequency(String term, int df)
    {
        documentFrequencyByTerm.put(term, df);
    }

    public void addDocument(DocumentRecord documentRecord)
    {
        documentsById.put(documentRecord.getId(), documentRecord);
    }

    public DocumentRecord getDocument(int docId)
    {
        return documentsById.get(docId);
    }

    public Map<Integer, DocumentRecord> getDocumentsById()
    {
        return Collections.unmodifiableMap(documentsById);
    }

    public int getTotalDocuments()
    {
        return totalDocuments;
    }

    public void setTotalDocuments(int totalDocuments)
    {
        this.totalDocuments = totalDocuments;
    }
}
