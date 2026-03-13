package com.ingestion;

/**
 * OcrLine represents a single text line detected by OCR.
 * Includes layout-aware bounding box and confidence score.
 *
 * Bounding box format: [x1, y1, x2, y2] normalized to page dimensions.
 */
public class OcrLine {

	private String text;
	private int[] bbox;  // [x1, y1, x2, y2] in pixels
	private double confidence;  // 0.0 to 1.0

	public OcrLine(String text, int[] bbox, double confidence) {
		this.text = text != null ? text : "";
		this.bbox = bbox;
		this.confidence = confidence;
	}

	// Getters
	public String getText() {
		return text;
	}

	public int[] getBbox() {
		return bbox;
	}

	public double getConfidence() {
		return confidence;
	}

	// Setters
	public void setText(String text) {
		this.text = text != null ? text : "";
	}

	public void setBbox(int[] bbox) {
		this.bbox = bbox;
	}

	public void setConfidence(double confidence) {
		this.confidence = confidence;
	}

	@Override
	public String toString() {
		return "OcrLine{" +
			"text='" + (text.length() > 40 ? text.substring(0, 40) + "..." : text) + '\'' +
			", bbox=[" + (bbox != null ? bbox[0] + "," + bbox[1] + "," + bbox[2] + "," + bbox[3] : "N/A") + ']' +
			", confidence=" + String.format("%.2f", confidence) +
			'}';
	}
}
