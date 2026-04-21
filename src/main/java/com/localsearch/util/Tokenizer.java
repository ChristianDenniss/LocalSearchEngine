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
}
