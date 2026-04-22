package com.localsearch.search;

import com.localsearch.index.IndexBuilder;
import com.localsearch.index.InvertedIndex;
import com.localsearch.model.DocumentRecord;
import com.localsearch.model.SearchResult;
import com.localsearch.ranking.Ranker;
import com.localsearch.util.Tokenizer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SearchServiceSubstringTest
{
    @Test
    void fallsBackToSubstringWhenTokenIndexMissesContiguousPhrase()
    {
        long now = System.currentTimeMillis();
        DocumentRecord doc = new DocumentRecord(1, "notes.txt", "mygpaishigh and other text", now);
        InvertedIndex index = new IndexBuilder(new Tokenizer()).build(List.of(doc));
        SearchService service = new SearchService(new Tokenizer(), new Ranker());

        List<SearchResult> results = service.search(index, "gpa", 5);

        assertFalse(results.isEmpty());
        assertEquals("notes.txt", results.get(0).getDocument().getPath());
    }

    @Test
    void shortQueriesUseSubstringMode()
    {
        long now = System.currentTimeMillis();
        DocumentRecord a = new DocumentRecord(1, "a.txt", "high scores today", now);
        DocumentRecord b = new DocumentRecord(2, "b.txt", "no match here", now);
        InvertedIndex index = new IndexBuilder(new Tokenizer()).build(List.of(a, b));
        SearchService service = new SearchService(new Tokenizer(), new Ranker());

        List<SearchResult> results = service.search(index, "hi", 5);

        assertFalse(results.isEmpty());
        assertEquals("a.txt", results.get(0).getDocument().getPath());
    }
}
