package com.ingestion;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * OcrProviderFactory creates and selects the appropriate OCR provider.
 *
 * Selection logic (in order of preference if available):
 * 1. If provider name is specified via config/env, use that
 * 2. If "paddle" is specified and available, use PaddleOCR
 * 3. Otherwise, fall back to Tesseract (if available)
 * 4. If no provider is available, return null with error logging
 *
 * Usage example:
 *   OcrProvider provider = OcrProviderFactory.selectProvider("paddle");
 *   if (provider != null) {
 *       OcrPage page = provider.extractPage(pdfPath, 0);
 *   }
 */
public class OcrProviderFactory {

	/**
	 * Select an OCR provider based on name preference.
	 *
	 * Supported names:
	 * - "paddle" or "paddleocr": Use PaddleOCR if available
	 * - "tesseract": Use Tesseract (default)
	 * - null or "auto": Auto-detect best available
	 *
	 * @param providerName the preferred provider name (or null for auto-detect)
	 * @return OcrProvider instance, or null if no provider is available
	 */
	public static OcrProvider selectProvider(String providerName) {
		return selectProvider(providerName, Paths.get("."));
	}

	/**
	 * Select an OCR provider with explicit project root (for testing).
	 *
	 * @param providerName the preferred provider name (or null for auto-detect)
	 * @param projectRoot the project root directory
	 * @return OcrProvider instance, or null if no provider is available
	 */
	public static OcrProvider selectProvider(String providerName, Path projectRoot) {
		if (providerName == null) {
			providerName = "auto";
		}

		providerName = providerName.toLowerCase().trim();

		// Case 1: Explicitly requested "paddle" or "paddleocr"
		if (providerName.equals("paddle") || providerName.equals("paddleocr")) {
			PaddleOcrService paddle = new PaddleOcrService(1, projectRoot);
			if (paddle.isAvailable()) {
				System.out.println("[OCR] Selected: PaddleOCR");
				return paddle;
			} else {
				System.err.println("[OCR] PaddleOCR requested but not available. Trying fallback...");
				// Fall through to try tesseract
			}
		}

		// Case 2: Explicitly requested "tesseract"
		if (providerName.equals("tesseract")) {
			TesseractOcrService tesseract = new TesseractOcrService(2);
			if (tesseract.isAvailable()) {
				System.out.println("[OCR] Selected: Tesseract");
				return tesseract;
			} else {
				System.err.println("[OCR] Tesseract requested but not available.");
				return null;
			}
		}

		// Case 3: Auto-detect (try best available in order)
		if (providerName.equals("auto")) {
			// Try PaddleOCR first (better layout awareness)
			PaddleOcrService paddle = new PaddleOcrService(1, projectRoot);
			if (paddle.isAvailable()) {
				System.out.println("[OCR] Auto-detected: PaddleOCR");
				return paddle;
			}

			// Fall back to Tesseract
			TesseractOcrService tesseract = new TesseractOcrService(2);
			if (tesseract.isAvailable()) {
				System.out.println("[OCR] Auto-detected: Tesseract");
				return tesseract;
			}

			System.err.println("[OCR] No OCR provider available. Install paddleocr or tesseract.");
			return null;
		}

		// Unknown provider name
		System.err.println("[OCR] Unknown OCR provider: " + providerName);
		return null;
	}

	/**
	 * Get a provider with fallback support.
	 *
	 * If primary provider fails, automatically falls back to alternative.
	 *
	 * @param primaryName the primary provider name (or null for auto)
	 * @param fallbackName the fallback provider name if primary unavailable
	 * @return OcrProvider instance, or null if both unavailable
	 */
	public static OcrProvider selectProviderWithFallback(String primaryName, String fallbackName) {
		return selectProviderWithFallback(primaryName, fallbackName, Paths.get("."));
	}

	/**
	 * Get a provider with fallback support (with explicit project root).
	 *
	 * @param primaryName the primary provider name
	 * @param fallbackName the fallback provider name
	 * @param projectRoot the project root directory
	 * @return OcrProvider instance, or null if both unavailable
	 */
	public static OcrProvider selectProviderWithFallback(String primaryName, String fallbackName, Path projectRoot) {
		OcrProvider primary = selectProvider(primaryName, projectRoot);
		if (primary != null) {
			return primary;
		}

		if (fallbackName != null) {
			System.out.println("[OCR] Primary provider unavailable, trying fallback: " + fallbackName);
			return selectProvider(fallbackName, projectRoot);
		}

		return null;
	}
}
