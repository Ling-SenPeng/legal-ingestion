package com.ingestion;

import java.sql.Connection;
import java.util.*;
import java.util.stream.Collectors;

/**
 * HybridSearchCommand implements hybrid search combining vector similarity
 * and keyword (full-text) search for legal RAG applications.
 *
 * Scoring formula: finalScore = alpha * vectorScore + (1 - alpha) * keywordScore
 * Default alpha = 0.7 (vector-weighted)
 *
 * Usage:
 *   new HybridSearchCommand(conn, apiKey, "case 2024-001", 10, 20, 20, 0.7).execute()
 */
public class HybridSearchCommand {

	private final Connection conn;
	private final String apiKey;
	private final String query;
	private final int topK;           // Top-K final results to return
	private final int vectorTopN;     // Number of vector search results to fetch
	private final int keywordTopN;    // Number of keyword search results to fetch
	private final double alpha;       // Weight for vector score (0.0 to 1.0)

	/**
	 * Constructor with explicit parameters.
	 *
	 * @param conn the database connection
	 * @param apiKey the OpenAI API key
	 * @param query the search query string
	 * @param topK the number of final merged results to return
	 * @param vectorTopN the number of vector search results
	 * @param keywordTopN the number of keyword search results
	 * @param alpha the weight for vector score (e.g., 0.7 = 70% vector, 30% keyword)
	 */
	public HybridSearchCommand(Connection conn, String apiKey, String query, int topK, int vectorTopN, int keywordTopN, double alpha) {
		this.conn = conn;
		this.apiKey = apiKey;
		this.query = query;
		this.topK = topK;
		this.vectorTopN = vectorTopN;
		this.keywordTopN = keywordTopN;
		this.alpha = Math.max(0.0, Math.min(1.0, alpha)); // Clamp to [0, 1]
	}

	/**
	 * Execute the hybrid search and return merged results.
	 *
	 * @return a list of HybridSearchHit objects sorted by finalScore (descending)
	 * @throws Exception if a database error occurs
	 */
	public List<HybridSearchHit> execute() throws Exception {
		// Generate embedding for the query
		OpenAIEmbeddingClient embeddingClient = new OpenAIEmbeddingClient(apiKey);
		float[] queryEmbedding = embeddingClient.embed(query);

		if (queryEmbedding == null || queryEmbedding.length == 0) {
			System.err.println("Failed to generate embedding for query");
			return new ArrayList<>();
		}

		// Fetch vector search results
		List<Map<String, Object>> vectorResults = ChunkRepo.searchByVectorDetailed(conn, queryEmbedding, vectorTopN);

		// Fetch keyword search results
		List<Map<String, Object>> keywordResults = ChunkRepo.searchByKeyword(conn, query, keywordTopN);

		// Merge results by chunk ID
		Map<Long, HybridSearchHit> mergedMap = new HashMap<>();

		// Process vector results
		for (Map<String, Object> vectorRow : vectorResults) {
			long chunkId = ((Number) vectorRow.get("chunkId")).longValue();
			long docId = ((Number) vectorRow.get("docId")).longValue();
			String fileName = (String) vectorRow.get("fileName");
			String filePath = (String) vectorRow.get("filePath");
			int pageNo = ((Number) vectorRow.get("pageNo")).intValue();
			String text = (String) vectorRow.get("text");
			double vectorScore = ((Number) vectorRow.get("vectorScore")).doubleValue();

			String preview = truncatePreview(text, 200);

			HybridSearchHit hit = new HybridSearchHit(
				chunkId, fileName, filePath, pageNo, preview, vectorScore, 0.0, vectorScore
			);
			mergedMap.put(chunkId, hit);
		}

		// Process keyword results and merge
		for (Map<String, Object> keywordRow : keywordResults) {
			long chunkId = ((Number) keywordRow.get("chunkId")).longValue();
			double keywordScore = ((Number) keywordRow.get("keywordScore")).doubleValue();

			if (mergedMap.containsKey(chunkId)) {
				// Update existing hit with keyword score
				HybridSearchHit hit = mergedMap.get(chunkId);
				hit.setKeywordScore(keywordScore);
				hit.setFinalScore(calculateFinalScore(hit.getVectorScore(), keywordScore));
			} else {
				// Add new hit from keyword search (only keyword score)
				long docId = ((Number) keywordRow.get("docId")).longValue();
				String fileName = (String) keywordRow.get("fileName");
				String filePath = (String) keywordRow.get("filePath");
				int pageNo = ((Number) keywordRow.get("pageNo")).intValue();
				String text = (String) keywordRow.get("text");

				String preview = truncatePreview(text, 200);

				HybridSearchHit hit = new HybridSearchHit(
					chunkId, fileName, filePath, pageNo, preview, 0.0, keywordScore,
					calculateFinalScore(0.0, keywordScore)
				);
				mergedMap.put(chunkId, hit);
			}
		}

		// Sort by finalScore (descending) and limit to topK
		List<HybridSearchHit> results = mergedMap.values().stream()
			.sorted((a, b) -> Double.compare(b.getFinalScore(), a.getFinalScore()))
			.limit(topK)
			.collect(Collectors.toList());

		return results;
	}

	/**
	 * Calculate the final score using weighted combination.
	 *
	 * @param vectorScore the vector similarity score (0.0 to 1.0)
	 * @param keywordScore the keyword/FTS score (typically 0.0 to 1.0, can be higher)
	 * @return the final score: alpha * vectorScore + (1 - alpha) * keywordScore
	 */
	private double calculateFinalScore(double vectorScore, double keywordScore) {
		// Normalize keyword score to [0, 1] range (max observed: ~0.5 for FTS)
		double normalizedKeywordScore = Math.min(1.0, keywordScore);

		return alpha * vectorScore + (1.0 - alpha) * normalizedKeywordScore;
	}

	/**
	 * Truncate text preview to a maximum length, attempting to cut at word boundaries.
	 *
	 * @param text the original text
	 * @param maxLength the maximum length of the preview
	 * @return the truncated preview with ellipsis if needed
	 */
	private String truncatePreview(String text, int maxLength) {
		if (text == null || text.length() <= maxLength) {
			return text;
		}

		String truncated = text.substring(0, maxLength);

		// Try to cut at the last space
		int lastSpace = truncated.lastIndexOf(' ');
		if (lastSpace > 0) {
			truncated = truncated.substring(0, lastSpace);
		}

		return truncated + "...";
	}
}

