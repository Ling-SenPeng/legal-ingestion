package com.ingestion.service.payment;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Service for extracting images from PDF pages.
 * Supports rendering PDF pages as images for vision-based LLM processing.
 */
public class PdfImageExtractor {

    private static final int DPI = 150;  // Resolution for PDF rendering
    private static final int MAX_PAGES = 20;  // Limit pages for cost/performance

    /**
     * Extract images from PDF pages and encode as base64.
     * Used for vision-based LLM processing of scanned documents.
     *
     * @param filePath the path to the PDF file
     * @return list of base64-encoded image strings (JPEG format)
     * @throws Exception if PDF reading or image encoding fails
     */
    public static List<String> extractPageImagesAsBase64(String filePath) throws Exception {
        if (filePath == null || filePath.isEmpty()) {
            throw new Exception("File path cannot be null or empty");
        }

        File file = new File(filePath);
        if (!file.exists()) {
            throw new Exception("PDF file not found: " + filePath);
        }

        List<String> base64Images = new ArrayList<>();

        try (PDDocument document = PDDocument.load(file)) {
            PDFRenderer renderer = new PDFRenderer(document);
            
            // Limit to first N pages to control costs
            int pageCount = document.getNumberOfPages();
            int pagesToProcess = Math.min(pageCount, MAX_PAGES);
            
            System.out.println("  [Image] Extracting " + pagesToProcess + " pages from " + pageCount + " total");

            for (int pageIndex = 0; pageIndex < pagesToProcess; pageIndex++) {
                try {
                    // Render page to image
                    BufferedImage image = renderer.renderImageWithDPI(pageIndex, DPI);
                    
                    // Encode to base64 JPEG
                    String base64 = encodeImageToBase64Jpeg(image);
                    base64Images.add(base64);
                    
                    System.out.println("  [Image] Page " + (pageIndex + 1) + " extracted");
                } catch (Exception e) {
                    System.err.println("  [Image] Failed to extract page " + (pageIndex + 1) + ": " + e.getMessage());
                    // Continue with next page instead of failing completely
                }
            }

            if (base64Images.isEmpty()) {
                throw new Exception("Failed to extract any images from PDF");
            }

            return base64Images;

        } catch (Exception e) {
            throw new Exception("Failed to extract images from PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Extract first N pages as base64 images.
     *
     * @param filePath the path to the PDF file
     * @param maxPages maximum number of pages to extract
     * @return list of base64-encoded image strings
     * @throws Exception if PDF reading or image encoding fails
     */
    public static List<String> extractPageImagesAsBase64(String filePath, int maxPages) throws Exception {
        if (filePath == null || filePath.isEmpty()) {
            throw new Exception("File path cannot be null or empty");
        }

        File file = new File(filePath);
        if (!file.exists()) {
            throw new Exception("PDF file not found: " + filePath);
        }

        List<String> base64Images = new ArrayList<>();

        try (PDDocument document = PDDocument.load(file)) {
            PDFRenderer renderer = new PDFRenderer(document);
            
            int pageCount = document.getNumberOfPages();
            int pagesToProcess = Math.min(pageCount, maxPages);
            
            System.out.println("  [Image] Extracting " + pagesToProcess + " pages from " + pageCount + " total");

            for (int pageIndex = 0; pageIndex < pagesToProcess; pageIndex++) {
                try {
                    BufferedImage image = renderer.renderImageWithDPI(pageIndex, DPI);
                    String base64 = encodeImageToBase64Jpeg(image);
                    base64Images.add(base64);
                    
                    System.out.println("  [Image] Page " + (pageIndex + 1) + " extracted");
                } catch (Exception e) {
                    System.err.println("  [Image] Failed to extract page " + (pageIndex + 1) + ": " + e.getMessage());
                }
            }

            if (base64Images.isEmpty()) {
                throw new Exception("Failed to extract any images from PDF");
            }

            return base64Images;

        } catch (Exception e) {
            throw new Exception("Failed to extract images from PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Encode a BufferedImage to base64 JPEG string.
     * JPEG is more compact than PNG for LLM vision APIs.
     *
     * @param image the BufferedImage to encode
     * @return base64-encoded JPEG string
     * @throws Exception if encoding fails
     */
    private static String encodeImageToBase64Jpeg(BufferedImage image) throws Exception {
        if (image == null) {
            throw new Exception("Image is null");
        }

        try {
            // Use JAI or built-in ImageIO
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            if (!javax.imageio.ImageIO.write(image, "jpg", baos)) {
                throw new Exception("Failed to encode image as JPEG");
            }
            
            byte[] imageBytes = baos.toByteArray();
            return Base64.getEncoder().encodeToString(imageBytes);

        } catch (Exception e) {
            throw new Exception("Failed to encode image to base64: " + e.getMessage(), e);
        }
    }

    /**
     * Get page count of a PDF.
     * Useful for determining processing capabilities.
     *
     * @param filePath the path to the PDF file
     * @return the number of pages, or 0 if file doesn't exist
     * @throws Exception if PDF reading fails
     */
    public static int getPageCount(String filePath) throws Exception {
        File file = new File(filePath);
        if (!file.exists()) {
            return 0;
        }

        try (PDDocument document = PDDocument.load(file)) {
            return document.getNumberOfPages();
        }
    }
}
