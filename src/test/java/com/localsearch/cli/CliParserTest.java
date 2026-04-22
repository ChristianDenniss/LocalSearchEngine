package com.localsearch.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
