package com.localsearch.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SnippetGenerator
{
    private static final int WINDOW = 160;

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
        List<String> sorted = new ArrayList<>(queryTerms.stream().filter(t -> t != null && !t.isBlank()).toList());
        sorted.sort(Comparator.comparingInt(String::length).reversed());

        int bestIndex = -1;
        for (String term : sorted)
        {
            int index = lower.indexOf(term.toLowerCase(Locale.ROOT));
            if (index >= 0)
            {
                bestIndex = index;
                break;
            }
        }
        if (bestIndex < 0)
        {
            bestIndex = 0;
        }

        String snippet = shorten(content, bestIndex);
        return highlight(snippet, sorted);
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

    private String highlight(String snippet, List<String> queryTermsSortedLongestFirst)
    {
        String result = snippet;
        for (String term : queryTermsSortedLongestFirst)
        {
            if (term.isBlank())
            {
                continue;
            }
            if (term.length() >= 3)
            {
                result = result.replaceAll(
                        "(?i)\\b" + Pattern.quote(term) + "\\b",
                        "[" + Matcher.quoteReplacement(term) + "]");
            }
            else
            {
                result = highlightShortTerm(result, term);
            }
        }
        return result;
    }

    private String highlightShortTerm(String snippet, String term)
    {
        Pattern pattern = Pattern.compile("(?i)(?<![a-z0-9])" + Pattern.quote(term) + "(?![a-z0-9])");
        Matcher matcher = pattern.matcher(snippet);
        StringBuilder builder = new StringBuilder();
        int appended = 0;
        int maxHighlights = 12;
        int end = 0;
        while (matcher.find() && appended < maxHighlights)
        {
            builder.append(snippet, end, matcher.start());
            builder.append("[").append(snippet, matcher.start(), matcher.end()).append("]");
            end = matcher.end();
            appended++;
        }
        builder.append(snippet.substring(end));
        return builder.toString();
    }
}
