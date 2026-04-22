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

class SearchServiceTest
{
    @Test
    void usesTfIdfToBoostRareTerms()
    {
        long now = System.currentTimeMillis();
        DocumentRecord docA = new DocumentRecord(1, "a.txt", "java java java common", now);
        DocumentRecord docB = new DocumentRecord(2, "b.txt", "java rare", now - 1000L);
        DocumentRecord docC = new DocumentRecord(3, "c.txt", "common common", now - 2000L);

        InvertedIndex index = new IndexBuilder(new Tokenizer()).build(List.of(docA, docB, docC));
        SearchService service = new SearchService(new Tokenizer(), new Ranker());

        List<SearchResult> results = service.search(index, "rare", 5);

        assertEquals(1, results.size());
        assertEquals("b.txt", results.get(0).getDocument().getPath(), "Rare term should strongly boost docB");
    }

    @Test
    void boostsDocumentWhenQueryMatchesFileName()
    {
        long now = System.currentTimeMillis();
        DocumentRecord byName = new DocumentRecord(1, "resume-john-doe.pdf", "general profile text", now);
        DocumentRecord byBody = new DocumentRecord(2, "notes.txt", "general profile text", now - 1000L);

        InvertedIndex index = new IndexBuilder(new Tokenizer()).build(List.of(byName, byBody));
        SearchService service = new SearchService(new Tokenizer(), new Ranker());

        List<SearchResult> results = service.search(index, "resume", 5);

        assertEquals(1, results.size());
        assertEquals("resume-john-doe.pdf", results.get(0).getDocument().getPath());
    }
}
