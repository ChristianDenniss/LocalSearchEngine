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
import java.util.Locale;
import java.util.Map;

public class SearchService
{
    private static final int SUBSTRING_SCORE_CAP = 80;

    private final Tokenizer tokenizer;
    private final Ranker ranker;

    public SearchService(Tokenizer tokenizer, Ranker ranker)
    {
        this.tokenizer = tokenizer;
        this.ranker = ranker;
    }

    public List<SearchResult> search(InvertedIndex index, String query, int limit)
    {
        String normalizedPhrase = tokenizer.normalizeForSubstringSearch(query);
        List<String> terms = tokenizer.tokenize(query);

        if (shouldUseSubstringOnly(normalizedPhrase, terms))
        {
            return substringSearch(index, normalizedPhrase, terms, limit);
        }

        if (terms.isEmpty())
        {
            if (!normalizedPhrase.isBlank())
            {
                return substringSearch(index, normalizedPhrase, List.of(), limit);
            }
            return List.of();
        }

        Map<Integer, Double> lexicalScores = new HashMap<>();
        Map<Integer, Map<String, Integer>> termMatchesPerDoc = new HashMap<>();
        int totalDocs = Math.max(index.getTotalDocuments(), 1);

        for (String term : terms)
        {
            if (term.length() < 2)
            {
                continue;
            }
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

        if (lexicalScores.isEmpty() && normalizedPhrase.length() >= 2)
        {
            return substringSearch(index, normalizedPhrase, terms, limit);
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

    private boolean shouldUseSubstringOnly(String normalizedPhrase, List<String> terms)
    {
        if (!normalizedPhrase.isBlank() && normalizedPhrase.length() <= 2)
        {
            return true;
        }
        return terms.stream().anyMatch(term -> term.length() == 1);
    }

    private List<SearchResult> substringSearch(
            InvertedIndex index,
            String normalizedPhrase,
            List<String> terms,
            int limit)
    {
        long now = System.currentTimeMillis();
        List<SearchResult> results = new ArrayList<>();

        for (DocumentRecord document : index.getDocumentsById().values())
        {
            String body = substringSearchBody(document);
            if (body.isBlank())
            {
                continue;
            }
            String lower = body.toLowerCase(Locale.ROOT);

            SubstringHit hit = scoreSubstringHit(lower, normalizedPhrase, terms);
            if (hit.score() <= 0.0d)
            {
                continue;
            }

            double recencyBoost = ranker.recencyBoost(document, now);
            double totalScore = hit.score() + recencyBoost;
            results.add(new SearchResult(document, totalScore, hit.score(), recencyBoost, hit.matchedTerms()));
        }

        results.sort(Comparator.comparingDouble(SearchResult::getScore).reversed());
        return results.stream().limit(limit).toList();
    }

    /**
     * Substring fallback searches body text and always includes the path so file-name-only
     * documents still match short queries that bypass the inverted index.
     */
    private static String substringSearchBody(DocumentRecord document)
    {
        String content = document.getContent();
        String path = document.getPath();
        if (content != null && !content.isBlank())
        {
            return content + "\n" + path;
        }
        return path;
    }

    private SubstringHit scoreSubstringHit(String bodyLower, String normalizedPhrase, List<String> terms)
    {
        Map<String, Integer> matched = new HashMap<>();

        if (!normalizedPhrase.isBlank())
        {
            int phraseCount = countNonOverlapping(bodyLower, normalizedPhrase);
            if (phraseCount > 0)
            {
                matched.put(normalizedPhrase, phraseCount);
                double score = Math.min(SUBSTRING_SCORE_CAP, Math.log(1 + phraseCount) * 8.0d);
                return new SubstringHit(score, matched);
            }
        }

        if (terms.isEmpty())
        {
            return new SubstringHit(0.0d, Map.of());
        }

        double sum = 0.0d;
        for (String term : terms)
        {
            if (term.isBlank())
            {
                continue;
            }
            String needle = term.toLowerCase(Locale.ROOT);
            int count = countNonOverlapping(bodyLower, needle);
            if (count == 0)
            {
                return new SubstringHit(0.0d, Map.of());
            }
            int capped = Math.min(count, 40);
            matched.put(term, count);
            sum += Math.log(1 + capped) * 3.5d;
        }
        if (matched.isEmpty())
        {
            return new SubstringHit(0.0d, Map.of());
        }
        return new SubstringHit(Math.min(SUBSTRING_SCORE_CAP, sum), matched);
    }

    private static int countNonOverlapping(String haystack, String needle)
    {
        if (needle.isEmpty())
        {
            return 0;
        }
        int occurrences = 0;
        int index = 0;
        while (index <= haystack.length() - needle.length())
        {
            int found = haystack.indexOf(needle, index);
            if (found < 0)
            {
                break;
            }
            occurrences++;
            index = found + needle.length();
        }
        return occurrences;
    }

    private record SubstringHit(double score, Map<String, Integer> matchedTerms)
    {
    }
}
