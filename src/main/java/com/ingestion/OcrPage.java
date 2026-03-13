package com.ingestion;

import java.util.ArrayList;
import java.util.List;

/**
 * OcrPage represents OCR output for a single page.
 * Contains normalized text, structured line-level blocks, and metadata.
 */
public class OcrPage {

	private int pageNumber;
	private String text;  // Full normalized page text
	private List<OcrLine> lines;  // Line-level blocks with bboxes
	private String providerName;  // Which OCR engine produced this

	public OcrPage(int pageNumber, String text, String providerName) {
		this.pageNumber = pageNumber;
		this.text = text != null ? text : "";
		this.lines = new ArrayList<>();
		this.providerName = providerName;
	}

	// Getters
	public int getPageNumber() {
		return pageNumber;
	}

	public String getText() {
		return text;
	}

	public List<OcrLine> getLines() {
		return lines;
	}

	public String getProviderName() {
		return providerName;
	}

	// Setters
	public void setText(String text) {
		this.text = text != null ? text : "";
	}

	public void addLine(OcrLine line) {
		this.lines.add(line);
	}

	public void addAllLines(List<OcrLine> allLines) {
		this.lines.addAll(allLines);
	}

	@Override
	public String toString() {
		return "OcrPage{" +
			"pageNumber=" + pageNumber +
			", text='" + (text.length() > 50 ? text.substring(0, 50) + "..." : text) + '\'' +
			", lines=" + lines.size() +
			", provider='" + providerName + '\'' +
			'}';
	}
}
