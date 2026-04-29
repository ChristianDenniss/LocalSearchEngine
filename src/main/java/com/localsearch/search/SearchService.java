package com.localsearch.search;

import com.localsearch.index.InvertedIndex;
import com.localsearch.model.DocumentRecord;
import com.localsearch.model.Posting;
import com.localsearch.model.SearchResult;
import com.localsearch.ranking.Ranker;
import com.localsearch.semantic.VectorMath;
import com.localsearch.util.Tokenizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SearchService
{
    private static final int SUBSTRING_SCORE_CAP = 80;
    /** Drop hits weaker than this fraction of the top score (after sorting by final score). */
    private static final double MIN_SCORE_RATIO_OF_TOP = 0.34d;
    /** Also require at least this final score so semantic/graph noise does not flood results. */
    private static final double MIN_SCORE_ABSOLUTE = 0.12d;

    private final Tokenizer tokenizer;
    private final Ranker ranker;
    private final SemanticSearchConfig semanticConfig;
    private final GraphRetrievalConfig graphConfig;

    public SearchService(Tokenizer tokenizer, Ranker ranker)
    {
        this(tokenizer, ranker, SemanticSearchConfig.DISABLED, GraphRetrievalConfig.DISABLED);
    }

    public SearchService(Tokenizer tokenizer, Ranker ranker, SemanticSearchConfig semanticConfig)
    {
        this(tokenizer, ranker, semanticConfig, GraphRetrievalConfig.DEFAULT);
    }

    public SearchService(
            Tokenizer tokenizer,
            Ranker ranker,
            SemanticSearchConfig semanticConfig,
            GraphRetrievalConfig graphConfig)
    {
        this.tokenizer = tokenizer;
        this.ranker = ranker;
        this.semanticConfig = semanticConfig;
        this.graphConfig = graphConfig;
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

        Map<Integer, Double> semanticScores = semanticScores(index, query);
        if (lexicalScores.isEmpty() && semanticScores.isEmpty() && normalizedPhrase.length() >= 2)
        {
            return substringSearch(index, normalizedPhrase, terms, limit);
        }
        long now = System.currentTimeMillis();
        List<Integer> candidateDocIds = semanticConfig.isEnabled()
                ? unionCandidates(lexicalScores, semanticScores)
                : new ArrayList<>(lexicalScores.keySet());

        Map<Integer, Double> graphBoostByDocId = graphBoostFromSeeds(index, candidateDocIds, lexicalScores, semanticScores);
        Set<Integer> expandedDocIds = new HashSet<>(candidateDocIds);
        expandedDocIds.addAll(graphBoostByDocId.keySet());

        List<SearchResult> results = new ArrayList<>();
        for (Integer docId : expandedDocIds)
        {
            DocumentRecord document = index.getDocument(docId);
            if (document == null)
            {
                continue;
            }
            double tfIdfScore = lexicalScores.getOrDefault(docId, 0.0d);
            double semanticScore = semanticScores.getOrDefault(docId, 0.0d);
            double hybrid = hybridScore(tfIdfScore, semanticScore);
            double graphBoost = graphBoostByDocId.getOrDefault(docId, 0.0d);
            double retrievalScore = hybrid + graphBoost;
            if (retrievalScore <= 0.0d)
            {
                continue;
            }
            double recencyBoost = ranker.recencyBoost(document, now);
            double totalScore = retrievalScore + recencyBoost;
            results.add(new SearchResult(
                    document,
                    totalScore,
                    tfIdfScore,
                    recencyBoost,
                    termMatchesPerDoc.getOrDefault(docId, Map.of())));
        }

        results.sort(Comparator.comparingDouble(SearchResult::getScore).reversed());
        return applyScoreQualityGate(results, limit);
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
        return applyScoreQualityGate(results, limit);
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

    private Map<Integer, Double> semanticScores(InvertedIndex index, String query)
    {
        if (!semanticConfig.isEnabled() || index.getSemanticVectorsByDocId().isEmpty())
        {
            return Map.of();
        }

        float[] queryVector = index.getSemanticVectorsByDocId().values().stream().findFirst().map(v -> new float[v.length]).orElse(null);
        if (queryVector == null)
        {
            return Map.of();
        }

        // Reuse tokenizer normalization to keep query handling aligned with lexical indexing.
        for (String token : tokenizer.tokenizeForIndexing(query))
        {
            int hash = token.hashCode();
            int slot = Math.floorMod(hash, queryVector.length);
            float direction = ((hash >>> 1) & 1) == 0 ? 1.0f : -1.0f;
            queryVector[slot] += direction;
        }
        VectorMath.normalizeInPlace(queryVector);
        if (VectorMath.cosineSimilarity(queryVector, queryVector) == 0.0d)
        {
            return Map.of();
        }

        Map<Integer, Double> scores = new HashMap<>();
        for (Map.Entry<Integer, float[]> entry : index.getSemanticVectorsByDocId().entrySet())
        {
            double cosine = VectorMath.cosineSimilarity(queryVector, entry.getValue());
            if (cosine >= semanticConfig.getSemanticThreshold())
            {
                scores.put(entry.getKey(), cosine);
            }
        }
        return scores;
    }

    private List<Integer> unionCandidates(Map<Integer, Double> lexicalScores, Map<Integer, Double> semanticScores)
    {
        Map<Integer, Boolean> ids = new HashMap<>();
        for (Integer docId : lexicalScores.keySet())
        {
            ids.put(docId, Boolean.TRUE);
        }
        for (Integer docId : semanticScores.keySet())
        {
            ids.put(docId, Boolean.TRUE);
        }
        return new ArrayList<>(ids.keySet());
    }

    private double hybridScore(double lexicalScore, double semanticScore)
    {
        if (!semanticConfig.isEnabled())
        {
            return lexicalScore;
        }
        double lexicalNormalized = lexicalScore > 0.0d ? Math.log1p(lexicalScore) : 0.0d;
        return (semanticConfig.getLexicalWeight() * lexicalNormalized)
                + (semanticConfig.getSemanticWeight() * semanticScore);
    }

    private Map<Integer, Double> graphBoostFromSeeds(
            InvertedIndex index,
            List<Integer> candidateDocIds,
            Map<Integer, Double> lexicalScores,
            Map<Integer, Double> semanticScores)
    {
        if (!graphConfig.isEnabled() || index.getNeighborDocIdsByDocId().isEmpty())
        {
            return Map.of();
        }

        Map<Integer, Double> hybridByDocId = new HashMap<>();
        for (Integer docId : candidateDocIds)
        {
            double hybrid = hybridScore(
                    lexicalScores.getOrDefault(docId, 0.0d),
                    semanticScores.getOrDefault(docId, 0.0d));
            if (hybrid > 0.0d)
            {
                hybridByDocId.put(docId, hybrid);
            }
        }
        if (hybridByDocId.isEmpty())
        {
            return Map.of();
        }

        int maxSeeds = Math.max(1, graphConfig.getMaxSeeds());
        List<Integer> seeds = hybridByDocId.entrySet().stream()
                .sorted(Comparator.comparingDouble(Map.Entry<Integer, Double>::getValue).reversed())
                .limit(maxSeeds)
                .map(Map.Entry::getKey)
                .toList();

        double factor = graphConfig.getNeighborBoostFactor();
        if (factor <= 0.0d)
        {
            return Map.of();
        }

        Map<Integer, Double> graphBoost = new HashMap<>();
        for (Integer seedDocId : seeds)
        {
            double seedHybrid = hybridByDocId.get(seedDocId);
            double contribution = seedHybrid * factor;
            for (Integer neighborId : index.getNeighborDocIds(seedDocId))
            {
                graphBoost.merge(neighborId, contribution, Math::max);
            }
        }
        return graphBoost;
    }

    /**
     * Keeps only results whose final score is close enough to the best hit, so weak semantic/graph
     * tails and unrelated files do not clutter the list.
     */
    private static List<SearchResult> applyScoreQualityGate(List<SearchResult> sortedDescending, int limit)
    {
        if (sortedDescending.isEmpty())
        {
            return sortedDescending;
        }
        double top = sortedDescending.get(0).getScore();
        if (top <= 0.0d)
        {
            return sortedDescending.stream().limit(limit).toList();
        }
        double floor = Math.max(top * MIN_SCORE_RATIO_OF_TOP, MIN_SCORE_ABSOLUTE);
        List<SearchResult> kept = new ArrayList<>();
        for (SearchResult result : sortedDescending)
        {
            if (result.getScore() < floor)
            {
                break;
            }
            kept.add(result);
            if (kept.size() >= limit)
            {
                break;
            }
        }
        return List.copyOf(kept);
    }
}
