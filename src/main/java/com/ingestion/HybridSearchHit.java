package com.ingestion;

/**
 * Represents a hybrid search result combining vector and keyword search scores.
 * Used for displaying merged search results with legal citations.
 */
public class HybridSearchHit {

	private long chunkId;
	private String fileName;
	private String filePath;
	private int pageNo;
	private String textPreview;
	private double vectorScore;
	private double keywordScore;
	private double finalScore;

	public HybridSearchHit(long chunkId, String fileName, String filePath, int pageNo, String textPreview,
			double vectorScore, double keywordScore, double finalScore) {
		this.chunkId = chunkId;
		this.fileName = fileName;
		this.filePath = filePath;
		this.pageNo = pageNo;
		this.textPreview = textPreview;
		this.vectorScore = vectorScore;
		this.keywordScore = keywordScore;
		this.finalScore = finalScore;
	}

	// Getters
	public long getChunkId() {
		return chunkId;
	}

	public String getFileName() {
		return fileName;
	}

	public String getFilePath() {
		return filePath;
	}

	public int getPageNo() {
		return pageNo;
	}

	public String getTextPreview() {
		return textPreview;
	}

	public double getVectorScore() {
		return vectorScore;
	}

	public double getKeywordScore() {
		return keywordScore;
	}

	public double getFinalScore() {
		return finalScore;
	}

	// Setters
	public void setKeywordScore(double keywordScore) {
		this.keywordScore = keywordScore;
	}

	public void setFinalScore(double finalScore) {
		this.finalScore = finalScore;
	}

	@Override
	public String toString() {
		return String.format(
			"Score:%.3f [V:%.3f K:%.3f] %s (Page %d) | %s",
			finalScore, vectorScore, keywordScore, fileName, pageNo,
			textPreview.length() > 80 ? textPreview.substring(0, 80) + "..." : textPreview
		);
	}
}
