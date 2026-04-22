package com.localsearch.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class Tokenizer
{
    private static final Pattern PUNCTUATION = Pattern.compile("[^a-z0-9\\s]");

    public List<String> tokenize(String text)
    {
        if (text == null || text.isBlank())
        {
            return List.of();
        }

        String normalized = PUNCTUATION.matcher(text.toLowerCase(Locale.ROOT)).replaceAll(" ");
        String[] split = normalized.trim().split("\\s+");
        List<String> tokens = new ArrayList<>(split.length);
        for (String token : split)
        {
            if (!token.isBlank())
            {
                tokens.add(token);
            }
        }
        return tokens;
    }

    /**
     * Same normalization as {@link #tokenize(String)} but returns one lowercased phrase
     * (spaces collapsed) for substring / contains search.
     */
    public String normalizeForSubstringSearch(String text)
    {
        if (text == null || text.isBlank())
        {
            return "";
        }
        String normalized = PUNCTUATION.matcher(text.toLowerCase(Locale.ROOT)).replaceAll(" ");
        return normalized.trim().replaceAll("\\s+", " ");
    }

    /**
     * Tokens kept in the inverted index. Drops 1-character tokens so noisy PDF output
     * (e.g. spaced-out letters) does not flood the index.
     */
    public List<String> tokenizeForIndexing(String text)
    {
        return tokenize(text).stream().filter(token -> token.length() >= 2).toList();
    }
}
