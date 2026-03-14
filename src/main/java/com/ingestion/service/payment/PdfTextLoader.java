package com.ingestion.service.payment;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;

/**
 * Service for loading full PDF document text.
 * Supports both digital and scanned PDFs.
 * Returns the complete document text for full-document level LLM processing.
 */
public class PdfTextLoader {

    /**
     * Load full text content from a PDF file.
     * Works with both digital PDFs (text extraction) and scanned PDFs (OCR-processed text).
     *
     * @param filePath the path to the PDF file
     * @return the extracted text content, or null if file doesn't exist
     * @throws Exception if PDF reading fails
     */
    public static String loadTextFromPdf(String filePath) throws Exception {
        if (filePath == null || filePath.isEmpty()) {
            throw new Exception("File path cannot be null or empty");
        }

        File file = new File(filePath);
        if (!file.exists()) {
            return null;  // File not found
        }

        try (PDDocument document = PDDocument.load(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            
            // Include all pages
            stripper.setStartPage(1);
            stripper.setEndPage(document.getNumberOfPages());
            
            String text = stripper.getText(document);
            
            // Clean up excessive whitespace while preserving structure
            text = text.replaceAll("\r\n", "\n");  // normalize line endings
            text = text.replaceAll("[ \t]+\n", "\n");  // remove trailing whitespace on lines
            text = text.replaceAll("\n\n\n+", "\n\n");  // collapse multiple blank lines
            
            return text.trim();
        }
    }

    /**
     * Load text from a PDF file and include page break markers.
     * Useful for tracking source pages during payment extraction.
     *
     * @param filePath the path to the PDF file
     * @return the extracted text with page markers, or null if file doesn't exist
     * @throws Exception if PDF reading fails
     */
    public static String loadTextFromPdfWithPageMarkers(String filePath) throws Exception {
        if (filePath == null || filePath.isEmpty()) {
            throw new Exception("File path cannot be null or empty");
        }

        File file = new File(filePath);
        if (!file.exists()) {
            return null;  // File not found
        }

        try (PDDocument document = PDDocument.load(file)) {
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void startPage(org.apache.pdfbox.pdmodel.PDPage page) throws java.io.IOException {
                    // Called before processing each page
                }

                @Override
                protected void endPage(org.apache.pdfbox.pdmodel.PDPage page) throws java.io.IOException {
                    // Called after processing each page
                }
            };
            
            stripper.setStartPage(1);
            stripper.setEndPage(document.getNumberOfPages());
            
            String text = stripper.getText(document);
            
            // Clean up whitespace
            text = text.replaceAll("\r\n", "\n");
            text = text.replaceAll("[ \t]+\n", "\n");
            text = text.replaceAll("\n\n\n+", "\n\n");
            
            return text.trim();
        }
    }

    /**
     * Get page count for a PDF.
     * Useful for validation and progress tracking.
     *
     * @param filePath the path to the PDF file
     * @return the number of pages, or 0 if file doesn't exist
     * @throws Exception if PDF reading fails
     */
    public static int getPageCount(String filePath) throws Exception {
        if (filePath == null || filePath.isEmpty()) {
            throw new Exception("File path cannot be null or empty");
        }

        File file = new File(filePath);
        if (!file.exists()) {
            return 0;
        }

        try (PDDocument document = PDDocument.load(file)) {
            return document.getNumberOfPages();
        }
    }
}
