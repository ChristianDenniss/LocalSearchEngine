package com.localsearch.search;

import com.localsearch.index.InvertedIndex;
import com.localsearch.model.DocumentRecord;
import com.localsearch.model.Posting;
import com.localsearch.model.SearchResult;
import com.localsearch.ranking.Ranker;
import com.localsearch.util.Tokenizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SearchService
{
    private final Tokenizer tokenizer;
    private final Ranker ranker;

    public SearchService(Tokenizer tokenizer, Ranker ranker)
    {
        this.tokenizer = tokenizer;
        this.ranker = ranker;
    }

    public List<SearchResult> search(InvertedIndex index, String query, int limit)
    {
        List<String> terms = tokenizer.tokenize(query);
        if (terms.isEmpty())
        {
            return List.of();
        }

        Map<Integer, Double> lexicalScores = new HashMap<>();
        Map<Integer, Map<String, Integer>> termMatchesPerDoc = new HashMap<>();
        int totalDocs = Math.max(index.getTotalDocuments(), 1);

        for (String term : terms)
        {
            int df = index.getDocumentFrequencyByTerm().getOrDefault(term, 0);
            double idf = Math.log((double) (totalDocs + 1) / (df + 1)) + 1.0d;
            List<Posting> postings = index.getPostings(term);
            for (Posting posting : postings)
            {
                double tfIdf = posting.getTermFrequency() * idf;
                lexicalScores.merge(posting.getDocId(), tfIdf, Double::sum);
                termMatchesPerDoc
                        .computeIfAbsent(posting.getDocId(), ignored -> new HashMap<>())
                        .put(term, posting.getTermFrequency());
            }
        }

        long now = System.currentTimeMillis();
        List<SearchResult> results = new ArrayList<>();
        for (Map.Entry<Integer, Double> entry : lexicalScores.entrySet())
        {
            int docId = entry.getKey();
            DocumentRecord document = index.getDocument(docId);
            if (document == null)
            {
                continue;
            }
            double tfIdfScore = entry.getValue();
            double recencyBoost = ranker.recencyBoost(document, now);
            double totalScore = tfIdfScore + recencyBoost;
            results.add(new SearchResult(document, totalScore, tfIdfScore, recencyBoost, termMatchesPerDoc.get(docId)));
        }

        results.sort(Comparator.comparingDouble(SearchResult::getScore).reversed());
        return results.stream().limit(limit).toList();
    }
}
