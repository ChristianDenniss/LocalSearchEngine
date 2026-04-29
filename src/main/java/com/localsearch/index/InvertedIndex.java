package com.localsearch.index;

import com.localsearch.model.DocumentRecord;
import com.localsearch.model.Posting;

import java.io.IOException;
import java.io.ObjectInputStream;
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
    private final Map<Integer, float[]> semanticVectorsByDocId = new HashMap<>();
    private Map<Integer, List<Integer>> neighborDocIdsByDocId = new HashMap<>();
    private int totalDocuments;

    @Serial
    private void readObject(ObjectInputStream inputStream) throws IOException, ClassNotFoundException
    {
        inputStream.defaultReadObject();
        if (neighborDocIdsByDocId == null)
        {
            neighborDocIdsByDocId = new HashMap<>();
        }
    }

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

    public void setSemanticVector(int docId, float[] vector)
    {
        if (vector == null)
        {
            semanticVectorsByDocId.remove(docId);
            return;
        }
        semanticVectorsByDocId.put(docId, vector);
    }

    public float[] getSemanticVector(int docId)
    {
        return semanticVectorsByDocId.get(docId);
    }

    public Map<Integer, float[]> getSemanticVectorsByDocId()
    {
        return Collections.unmodifiableMap(semanticVectorsByDocId);
    }

    public void setNeighborDocIds(int docId, List<Integer> neighborDocIds)
    {
        if (neighborDocIds == null || neighborDocIds.isEmpty())
        {
            neighborDocIdsByDocId.remove(docId);
            return;
        }
        neighborDocIdsByDocId.put(docId, List.copyOf(neighborDocIds));
    }

    public List<Integer> getNeighborDocIds(int docId)
    {
        return neighborDocIdsByDocId.getOrDefault(docId, List.of());
    }

    public Map<Integer, List<Integer>> getNeighborDocIdsByDocId()
    {
        return Collections.unmodifiableMap(neighborDocIdsByDocId);
    }
}
