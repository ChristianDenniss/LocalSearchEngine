package com.localsearch.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliParserTest
{
    @Test
    void parsesListCommandWithFlags()
    {
        CliOptions options = new CliParser().parse(new String[] {
                "search",
                "--limit",
                "3",
                "list",
        });

        assertTrue(options.isListIndexed());
        assertEquals(3, options.getLimit());
    }

    @Test
    void parsesSemanticFlags()
    {
        CliOptions options = new CliParser().parse(new String[] {
                "search",
                "benefits",
                "--semantic-weight",
                "0.45",
        });

        assertTrue(options.isSemantic());
        assertEquals(0.45d, options.getSemanticWeight());
    }

    @Test
    void disablesSemanticWhenNoSemanticFlagIsPresent()
    {
        CliOptions options = new CliParser().parse(new String[] {
                "search",
                "benefits",
                "--no-semantic",
        });

        assertFalse(options.isSemantic());
    }
}
