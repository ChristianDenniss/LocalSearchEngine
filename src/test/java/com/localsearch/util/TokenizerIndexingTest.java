package com.localsearch.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TokenizerIndexingTest
{
    @Test
    void tokenizeForIndexingDropsSingleCharacterTokens()
    {
        Tokenizer tokenizer = new Tokenizer();

        List<String> indexed = tokenizer.tokenizeForIndexing("a b cat");

        assertFalse(indexed.contains("a"));
        assertFalse(indexed.contains("b"));
        assertEquals(List.of("cat"), indexed);
    }
}
