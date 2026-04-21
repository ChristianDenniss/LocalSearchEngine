package com.localsearch.index;

import com.localsearch.model.DocumentRecord;
import com.localsearch.model.Posting;
import com.localsearch.util.Tokenizer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class IndexBuilder
{
    private final Tokenizer tokenizer;

    public IndexBuilder(Tokenizer tokenizer)
    {
        this.tokenizer = tokenizer;
    }

    public InvertedIndex build(List<DocumentRecord> documents)
    {
        InvertedIndex index = new InvertedIndex();
        Map<String, Set<Integer>> docsPerTerm = new HashMap<>();

        for (DocumentRecord document : documents)
        {
            index.addDocument(document);
            List<String> tokens = tokenizer.tokenize(document.getContent());
            Map<String, Integer> termFrequency = new HashMap<>();
            for (String token : tokens)
            {
                termFrequency.merge(token, 1, Integer::sum);
            }

            for (Map.Entry<String, Integer> entry : termFrequency.entrySet())
            {
                String term = entry.getKey();
                int tf = entry.getValue();
                index.addPosting(term, new Posting(document.getId(), tf));
                docsPerTerm.computeIfAbsent(term, ignored -> new HashSet<>()).add(document.getId());
            }
        }

        for (Map.Entry<String, Set<Integer>> entry : docsPerTerm.entrySet())
        {
            index.setDocumentFrequency(entry.getKey(), entry.getValue().size());
        }
        index.setTotalDocuments(documents.size());
        return index;
    }
}
