package com.injestion;

/**
 * OcrDecider provides MVP logic to determine whether a page needs OCR processing.
 *
 * MVP Rule: If extracted text is too short or low quality, OCR the page.
 * This avoids unnecessary OCR calls on already-readable text.
 */
public class OcrDecider {

	/** Minimum character count to consider text as "readable" */
	private static final int MIN_TEXT_LENGTH = 30;

	/** Minimum ratio of alphanumeric characters (optional enhancement) */
	private static final double MIN_ALPHANUMERIC_RATIO = 0.1;

	/**
	 * MVP Decision: Should this page be OCR'd?
	 *
	 * Rule: If trimmed text is shorter than MIN_TEXT_LENGTH, return true (needs OCR).
	 *
	 * @param pageText the text extracted from the page using PDFBox
	 * @return true if the page should be OCR'd, false otherwise
	 */
	public static boolean shouldOcr(String pageText) {
		if (pageText == null) {
			return true;  // No text at all, definitely OCR
		}

		String trimmed = pageText.trim();

		// MVP Rule: too little text
		if (trimmed.length() < MIN_TEXT_LENGTH) {
			return true;
		}

		// Optional enhancement: check alphanumeric ratio
		// Useful for detecting pages with mostly symbols/garbage
		/*
		double alphanumericRatio = countAlphanumeric(trimmed) / (double) trimmed.length();
		if (alphanumericRatio < MIN_ALPHANUMERIC_RATIO) {
			return true;
		}
		*/

		return false;
	}

	/**
	 * Optional: Count alphanumeric characters in text.
	 * This could be used to filter pages with too many symbols/garbage.
	 *
	 * @param text the text to analyze
	 * @return the count of alphanumeric (a-z, A-Z, 0-9) characters
	 */
	private static int countAlphanumeric(String text) {
		int count = 0;
		for (char c : text.toCharArray()) {
			if (Character.isLetterOrDigit(c)) {
				count++;
			}
		}
		return count;
	}
}
