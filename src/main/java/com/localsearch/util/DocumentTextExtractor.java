package com.localsearch.util;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads plain text from UTF-8 text files and common Office / PDF binary formats.
 */
public final class DocumentTextExtractor
{
    private DocumentTextExtractor()
    {
    }

    public static String readPlainText(Path path)
    {
        if (path == null || path.getFileName() == null)
        {
            return null;
        }
        String name = path.getFileName().toString().toLowerCase();
        try
        {
            if (name.endsWith(".pdf"))
            {
                return normalize(extractPdf(path));
            }
            if (name.endsWith(".docx"))
            {
                return normalize(extractDocx(path));
            }
            if (name.endsWith(".xlsx") || name.endsWith(".xlsm") || name.endsWith(".xls"))
            {
                return normalize(extractSpreadsheet(path));
            }
            if (name.endsWith(".pptx"))
            {
                return normalize(extractPowerpoint(path));
            }
            return normalize(readUtf8(path));
        }
        catch (Exception ignored)
        {
            return null;
        }
    }

    private static String readUtf8(Path path) throws IOException
    {
        try
        {
            return Files.readString(path, StandardCharsets.UTF_8);
        }
        catch (MalformedInputException ignored)
        {
            return null;
        }
    }

    private static String extractPdf(Path path) throws IOException
    {
        try (PDDocument document = Loader.loadPDF(path.toFile()))
        {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    private static String extractDocx(Path path) throws IOException
    {
        StringBuilder builder = new StringBuilder();
        try (XWPFDocument document = new XWPFDocument(Files.newInputStream(path)))
        {
            for (XWPFParagraph paragraph : document.getParagraphs())
            {
                builder.append(paragraph.getText()).append('\n');
            }
            for (XWPFTable table : document.getTables())
            {
                table.getRows().forEach(row ->
                        row.getTableCells().forEach(cell ->
                                builder.append(cell.getText()).append('\t')));
                builder.append('\n');
            }
        }
        return builder.toString();
    }

    private static String extractSpreadsheet(Path path) throws IOException
    {
        DataFormatter formatter = new DataFormatter();
        StringBuilder builder = new StringBuilder();
        try (Workbook workbook = WorkbookFactory.create(path.toFile()))
        {
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++)
            {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                if (sheet == null)
                {
                    continue;
                }
                for (Row row : sheet)
                {
                    if (row == null)
                    {
                        continue;
                    }
                    for (Cell cell : row)
                    {
                        builder.append(formatter.formatCellValue(cell)).append('\t');
                    }
                    builder.append('\n');
                }
            }
        }
        return builder.toString();
    }

    private static String extractPowerpoint(Path path) throws IOException
    {
        StringBuilder builder = new StringBuilder();
        try (XMLSlideShow presentation = new XMLSlideShow(Files.newInputStream(path)))
        {
            for (XSLFSlide slide : presentation.getSlides())
            {
                for (XSLFShape shape : slide.getShapes())
                {
                    if (shape instanceof XSLFTextShape textShape)
                    {
                        String text = textShape.getText();
                        if (text != null && !text.isBlank())
                        {
                            builder.append(text).append('\n');
                        }
                    }
                }
            }
        }
        return builder.toString();
    }

    private static String normalize(String text)
    {
        if (text == null)
        {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
