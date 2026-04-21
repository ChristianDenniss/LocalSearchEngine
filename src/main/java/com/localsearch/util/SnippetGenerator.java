package com.localsearch.util;

import java.util.List;
import java.util.Locale;

public class SnippetGenerator
{
    private static final int WINDOW = 120;

    public String buildSnippet(String content, List<String> queryTerms)
    {
        if (content == null || content.isBlank())
        {
            return "";
        }
        if (queryTerms.isEmpty())
        {
            return shorten(content, 0);
        }

        String lower = content.toLowerCase(Locale.ROOT);
        int bestIndex = -1;
        for (String term : queryTerms)
        {
            int index = lower.indexOf(term.toLowerCase(Locale.ROOT));
            if (index >= 0 && (bestIndex == -1 || index < bestIndex))
            {
                bestIndex = index;
            }
        }
        if (bestIndex < 0)
        {
            bestIndex = 0;
        }

        String snippet = shorten(content, bestIndex);
        return highlight(snippet, queryTerms);
    }

    private String shorten(String content, int focusIndex)
    {
        int start = Math.max(0, focusIndex - (WINDOW / 2));
        int end = Math.min(content.length(), start + WINDOW);
        String segment = content.substring(start, end).replaceAll("\\s+", " ").trim();

        String prefix = start > 0 ? "..." : "";
        String suffix = end < content.length() ? "..." : "";
        return prefix + segment + suffix;
    }

    private String highlight(String snippet, List<String> queryTerms)
    {
        String result = snippet;
        for (String term : queryTerms)
        {
            if (term.isBlank())
            {
                continue;
            }
            result = result.replaceAll("(?i)\\b" + java.util.regex.Pattern.quote(term) + "\\b", "[" + term + "]");
        }
        return result;
    }
}
