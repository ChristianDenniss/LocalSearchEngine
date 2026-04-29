package com.localsearch.index;

import com.localsearch.model.DocumentRecord;
import com.localsearch.util.Tokenizer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentRelationshipGraphTest
{
    @Test
    void linksDocumentsSharingParentFolder()
    {
        long now = 1L;
        DocumentRecord a = new DocumentRecord(1, "folder/a.txt", "alpha", now);
        DocumentRecord b = new DocumentRecord(2, "folder/b.txt", "beta", now);

        InvertedIndex index = new IndexBuilder(new Tokenizer()).build(List.of(a, b));

        assertTrue(index.getNeighborDocIds(1).contains(2));
        assertTrue(index.getNeighborDocIds(2).contains(1));
    }
}
