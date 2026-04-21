package com.localsearch.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenizerTest
{
    @Test
    void tokenizesLowercaseAndRemovesPunctuation()
    {
        Tokenizer tokenizer = new Tokenizer();

        List<String> tokens = tokenizer.tokenize("Hello, WORLD!!  Java-search.");

        assertEquals(List.of("hello", "world", "java", "search"), tokens);
    }
}
