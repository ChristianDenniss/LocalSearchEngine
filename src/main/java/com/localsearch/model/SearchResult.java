package com.localsearch.model;

import java.util.Collections;
import java.util.Map;

public class SearchResult
{
    private final DocumentRecord document;
    private final double score;
    private final double termFrequencyScore;
    private final double recencyBoost;
    private final Map<String, Integer> matchedTerms;

    public SearchResult(
            DocumentRecord document,
            double score,
            double termFrequencyScore,
            double recencyBoost,
            Map<String, Integer> matchedTerms)
    {
        this.document = document;
        this.score = score;
        this.termFrequencyScore = termFrequencyScore;
        this.recencyBoost = recencyBoost;
        this.matchedTerms = Map.copyOf(matchedTerms);
    }

    public DocumentRecord getDocument()
    {
        return document;
    }

    public double getScore()
    {
        return score;
    }

    public double getTermFrequencyScore()
    {
        return termFrequencyScore;
    }

    public double getRecencyBoost()
    {
        return recencyBoost;
    }

    public Map<String, Integer> getMatchedTerms()
    {
        return Collections.unmodifiableMap(matchedTerms);
    }
}
