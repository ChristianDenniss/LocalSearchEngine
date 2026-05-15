package com.localsearch.cli;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppIntegrationTest
{
    private PrintStream originalOut;
    private InputStream originalIn;
    private ByteArrayOutputStream capturedOut;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp()
    {
        originalOut = System.out;
        originalIn = System.in;
        capturedOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedOut));
    }

    @AfterEach
    void tearDown()
    {
        System.setOut(originalOut);
        System.setIn(originalIn);
    }

    @Test
    void singleSearchFindsMatchingDocument() throws Exception
    {
        Files.writeString(tempDir.resolve("java-notes.txt"), "Java is a programming language used for building applications.");
        Files.writeString(tempDir.resolve("python-notes.txt"), "Python is a scripting language popular in data science.");

        run("search", "java", "--root", root(), "--index", index(), "--no-semantic", "--no-graph");

        String output = output();
        assertTrue(output.contains("RESULT"), "Should find at least one result");
        assertTrue(output.contains("java-notes.txt"), "Should find the java document");
    }

    @Test
    void singleSearchReturnsNoResultsWhenNothingMatches() throws Exception
    {
        Files.writeString(tempDir.resolve("cooking-recipes.txt"), "Mix flour and butter to make a roux.");

        run("search", "quantum physics dark matter", "--root", root(), "--index", index(), "--no-semantic", "--no-graph");

        assertTrue(output().contains("No results found"), "Should report no results");
    }

    @Test
    void explainFlagPrintsScoreBreakdown() throws Exception
    {
        Files.writeString(tempDir.resolve("report.txt"), "quarterly financial report summary results");

        run("search", "financial report", "--root", root(), "--index", index(), "--no-semantic", "--explain");

        String output = output();
        assertTrue(output.contains("RESULT"), "Should find a result");
        assertTrue(output.contains("explain:"), "Should include score breakdown");
        assertTrue(output.contains("tfIdf="), "Should show tfIdf component");
    }

    @Test
    void limitFlagCapsResultCount() throws Exception
    {
        Files.writeString(tempDir.resolve("alpha.txt"), "java spring boot framework");
        Files.writeString(tempDir.resolve("beta.txt"), "java maven build tool");
        Files.writeString(tempDir.resolve("gamma.txt"), "java junit testing library");

        run("search", "java", "--root", root(), "--index", index(), "--no-semantic", "--no-graph", "--limit", "2");

        String output = output();
        assertTrue(output.contains("2 RESULTS FOUND:"), "Should cap results at 2");
    }

    @Test
    void listCommandShowsIndexedDocuments() throws Exception
    {
        Files.writeString(tempDir.resolve("alpha.txt"), "content alpha");
        Files.writeString(tempDir.resolve("beta.txt"), "content beta");

        run("search", "list", "--root", root(), "--index", index());

        String output = output();
        assertTrue(output.contains("Indexed documents (2):"), "Should list 2 documents");
        assertTrue(output.contains("alpha.txt"), "Should include alpha.txt");
        assertTrue(output.contains("beta.txt"), "Should include beta.txt");
    }

    @Test
    void interactiveModeExitsOnQuit() throws Exception
    {
        Files.writeString(tempDir.resolve("notes.txt"), "meeting notes for the project");
        System.setIn(new ByteArrayInputStream("quit\n".getBytes()));

        run("--root", root(), "--index", index());

        assertTrue(output().contains("search>"), "Should show the interactive prompt");
    }

    @Test
    void interactiveModeShowsUsageOnHelp() throws Exception
    {
        Files.writeString(tempDir.resolve("sample.txt"), "sample document content");
        System.setIn(new ByteArrayInputStream("help\nquit\n".getBytes()));

        run("--root", root(), "--index", index());

        assertTrue(output().contains("Usage:"), "Should print usage on help command");
    }

    @Test
    void interactiveModeShowsCommandsOnCmds() throws Exception
    {
        Files.writeString(tempDir.resolve("sample.txt"), "sample content");
        System.setIn(new ByteArrayInputStream("cmds\nquit\n".getBytes()));

        run("--root", root(), "--index", index());

        assertTrue(output().contains("Commands:"), "Should list commands on cmds command");
    }

    @Test
    void interactiveModeShowsFlagsOnFlags() throws Exception
    {
        Files.writeString(tempDir.resolve("sample.txt"), "sample content");
        System.setIn(new ByteArrayInputStream("flags\nquit\n".getBytes()));

        run("--root", root(), "--index", index());

        assertTrue(output().contains("--limit"), "Should print flag descriptions");
    }

    @Test
    void interactiveModeSearchesForDocuments() throws Exception
    {
        Files.writeString(tempDir.resolve("machine-learning.txt"), "neural networks and deep learning algorithms");
        Files.writeString(tempDir.resolve("cooking.txt"), "bake at 350 degrees for 30 minutes");

        String query = "neural networks --no-semantic --no-graph --root " + root() + " --index " + index();
        System.setIn(new ByteArrayInputStream((query + "\nquit\n").getBytes()));

        run("--root", root(), "--index", index());

        String output = output();
        assertTrue(output.contains("RESULT"), "Should find result for neural networks query");
        assertTrue(output.contains("machine-learning.txt"), "Should find the machine-learning document");
    }

    @Test
    void interactiveModeIgnoresBlankInput() throws Exception
    {
        Files.writeString(tempDir.resolve("sample.txt"), "content");
        System.setIn(new ByteArrayInputStream("\n\nquit\n".getBytes()));

        run("--root", root(), "--index", index());

        assertFalse(output().contains("Error"), "Blank lines should be ignored without error");
    }

    private void run(String... args) throws Exception
    {
        new LocalSearchEngineApp().run(args);
    }

    private String root()
    {
        return tempDir.toString();
    }

    private String index()
    {
        return tempDir.resolve("index.dat").toString();
    }

    private String output()
    {
        return capturedOut.toString();
    }
}
