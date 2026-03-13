package com.ingestion;

import java.nio.file.Path;

/**
 * OcrProvider is the abstraction for different OCR backends.
 * Implementations can use tesseract, PaddleOCR, or other OCR engines.
 *
 * Contract:
 * - Extract text and structured blocks from a PDF at a given page index
 * - Return highly structured output (layout-aware bounding boxes)
 * - Handle errors gracefully
 * - Support concurrent usage (thread-safe)
 */
public interface OcrProvider {

	/**
	 * Extract OCR result for a single PDF page.
	 *
	 * @param pdfPath absolute path to the PDF file
	 * @param pageIndex 0-based page index
	 * @return OcrPage with text, lines, and bounding boxes
	 * @throws Exception if OCR processing fails
	 */
	OcrPage extractPage(Path pdfPath, int pageIndex) throws Exception;

	/**
	 * Get the name of this OCR provider (for logging/debugging).
	 *
	 * @return provider name (e.g., "tesseract", "paddle", "google-vision")
	 */
	String getProviderName();

	/**
	 * Gracefully shutdown the provider (close thread pools, cleanup resources).
	 */
	void shutdown();

	/**
	 * Check if this provider is available (e.g., tesseract binary is installed).
	 *
	 * @return true if provider is available, false otherwise
	 */
	boolean isAvailable();
}
