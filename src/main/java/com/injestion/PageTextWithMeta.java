package com.injestion;

import java.util.HashMap;
import java.util.Map;

/**
 * PageTextWithMeta extends the simple PageText with metadata about the text origin and processing.
 *
 * Metadata includes:
 * - source: "pdfbox" or "ocr" (indicates where text came from)
 * - ocr_engine: "tesseract" (if source is "ocr")
 * - ocr_lang: language used for OCR
 * - dpi: DPI used for rendering (for OCR)
 * - Other extraction details as needed
 */
public class PageTextWithMeta {

	public final int pageNo;       // 1-based page number for legal citation
	public final String text;      // The extracted/OCR'd text
	public final Map<String, Object> meta;  // Metadata about text origin

	/**
	 * Constructor with full metadata.
	 *
	 * @param pageNo 1-based page number
	 * @param text the extracted text
	 * @param meta metadata map
	 */
	public PageTextWithMeta(int pageNo, String text, Map<String, Object> meta) {
		this.pageNo = pageNo;
		this.text = text != null ? text : "";
		this.meta = meta != null ? meta : new HashMap<>();
	}

	/**
	 * Constructor with simple source-based metadata.
	 *
	 * @param pageNo 1-based page number
	 * @param text the extracted text
	 * @param source "pdfbox" or "ocr"
	 */
	public PageTextWithMeta(int pageNo, String text, String source) {
		this(pageNo, text, Map.of("source", source));
	}

	/**
	 * Create with PDFBox source (default).
	 */
	public static PageTextWithMeta fromPdfBox(int pageNo, String text) {
		return new PageTextWithMeta(pageNo, text, Map.of("source", "pdfbox"));
	}

	/**
	 * Create with OCR source.
	 *
	 * @param pageNo 1-based page number
	 * @param text the OCR'd text
	 * @param engine the OCR engine name (e.g., "tesseract")
	 * @param lang the language used
	 * @param dpi the DPI setting
	 */
	public static PageTextWithMeta fromOcr(int pageNo, String text, String engine, String lang, int dpi) {
		Map<String, Object> meta = new HashMap<>();
		meta.put("source", "ocr");
		meta.put("ocr_engine", engine);
		meta.put("ocr_lang", lang);
		meta.put("dpi", dpi);
		return new PageTextWithMeta(pageNo, text, meta);
	}

	/**
	 * Convert this to the legacy PDFReader.PageText format.
	 * Useful for backward compatibility.
	 */
	public PDFReader.PageText toPageText() {
		return new PDFReader.PageText(pageNo, text);
	}

	@Override
	public String toString() {
		return "PageTextWithMeta{" +
			"pageNo=" + pageNo +
			", textLength=" + text.length() +
			", source=" + meta.getOrDefault("source", "unknown") +
			'}';
	}

	/**
	 * Get metadata as JSON string (simplified for logging).
	 */
	public String metaToString() {
		return meta.toString();
	}
}
