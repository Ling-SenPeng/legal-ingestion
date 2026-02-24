package com.ingestion;

/**
 * Data class representing a search hit from vector similarity search.
 */
public class SearchHit {
	public final String fileName;
	public final String filePath;
	public final int pageNo;
	public final String text;
	public final double score;

	public SearchHit(String fileName, String filePath, int pageNo, String text, double score) {
		this.fileName = fileName;
		this.filePath = filePath;
		this.pageNo = pageNo;
		this.text = text;
		this.score = score;
	}

	public String getPreview(int maxLen) {
		if (text == null) return "";
		return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
	}

	@Override
	public String toString() {
		return "SearchHit{" +
			"score=" + String.format("%.4f", score) +
			", file='" + fileName + '\'' +
			", page=" + pageNo +
			", pathLength=" + (filePath != null ? filePath.length() : 0) +
			", textPreview='" + getPreview(50) + '\'' +
			'}';
	}
}
