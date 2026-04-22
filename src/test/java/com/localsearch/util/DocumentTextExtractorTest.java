package com.localsearch.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentTextExtractorTest
{
    @TempDir
    Path tempDir;

    @Test
    void readsDocxBody() throws Exception
    {
        Path docx = tempDir.resolve("sample.docx");
        try (XWPFDocument document = new XWPFDocument())
        {
            document.createParagraph().createRun().setText("hello docx world");
            try (OutputStream out = Files.newOutputStream(docx))
            {
                document.write(out);
            }
        }

        String text = DocumentTextExtractor.readPlainText(docx);

        assertNotNull(text);
        assertTrue(text.toLowerCase().contains("hello"));
        assertTrue(text.toLowerCase().contains("docx"));
    }

    @Test
    void readsPdfText() throws Exception
    {
        Path pdf = tempDir.resolve("sample.pdf");
        try (PDDocument document = new PDDocument())
        {
            PDPage page = new PDPage();
            document.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream stream = new PDPageContentStream(document, page))
            {
                stream.beginText();
                stream.setFont(font, 12);
                stream.newLineAtOffset(50, 700);
                stream.showText("hello pdf world");
                stream.endText();
            }
            document.save(pdf.toFile());
        }

        String text = DocumentTextExtractor.readPlainText(pdf);

        assertNotNull(text);
        assertTrue(text.toLowerCase().contains("hello"));
        assertTrue(text.toLowerCase().contains("pdf"));
    }
}
