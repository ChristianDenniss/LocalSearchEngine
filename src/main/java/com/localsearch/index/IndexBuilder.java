package com.localsearch.index;

import com.localsearch.model.DocumentRecord;
import com.localsearch.model.Posting;
import com.localsearch.util.Tokenizer;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class IndexBuilder
{
    private static final int FILENAME_TOKEN_BOOST = 3;

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
            List<String> tokens = tokenizer.tokenizeForIndexing(document.getContent());
            Map<String, Integer> termFrequency = new HashMap<>();
            for (String token : tokens)
            {
                termFrequency.merge(token, 1, Integer::sum);
            }
            // File-name hits are usually strong intent signals (resume.pdf, tax_return.pdf, etc.).
            for (String token : tokenizeFileName(document.getPath()))
            {
                termFrequency.merge(token, FILENAME_TOKEN_BOOST, Integer::sum);
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

    private List<String> tokenizeFileName(String pathString)
    {
        try
        {
            Path path = Path.of(pathString);
            Path fileName = path.getFileName();
            if (fileName == null)
            {
                return List.of();
            }
            return tokenizer.tokenizeForIndexing(fileName.toString());
        }
        catch (Exception ignored)
        {
            return List.of();
        }
    }
}
