package com.ingestion;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

/**
 * Command to search chunks by semantic similarity using vector embeddings.
 */
public class SearchCommand {

	private final OpenAIEmbeddingClient embeddingClient;
	private final String dbUrl;
	private final String dbUser;
	private final String dbPassword;

	public SearchCommand(String apiKey, String dbUrl, String dbUser, String dbPassword) throws Exception {
		this.embeddingClient = new OpenAIEmbeddingClient(apiKey);
		this.dbUrl = dbUrl;
		this.dbUser = dbUser;
		this.dbPassword = dbPassword;
		DocumentRepo.ensureDriverLoaded();
	}

	/**
	 * Execute the search command.
	 *
	 * @param query the search query text
	 * @param topK number of top results to return
	 */
	public void execute(String query, int topK) {
		System.out.println("=== Vector Search ===");
		System.out.println("Query: " + query);
		System.out.println("TopK: " + topK);
		System.out.println();

		try {
			// Generate embedding for query
			System.out.println("Generating embedding for query...");
			float[] queryEmbedding = embeddingClient.embed(query);

			if (queryEmbedding == null) {
				System.err.println("Error: Query text is empty or too short.");
				return;
			}

			System.out.println("Query embedding generated (" + queryEmbedding.length + " dimensions)");
			System.out.println();

			// Search database
			try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
				System.out.println("Searching chunks...");
				List<SearchHit> results = ChunkRepo.searchByVector(conn, queryEmbedding, topK);

				if (results.isEmpty()) {
					System.out.println("No results found.");
					return;
				}

				// Print results
				System.out.println("Found " + results.size() + " result(s):");
				System.out.println();

				for (int i = 0; i < results.size(); i++) {
					SearchHit hit = results.get(i);
					System.out.println(String.format(
						"[%d] Score: %.4f | File: %s | Page: %d",
						i + 1, hit.score, hit.fileName, hit.pageNo
					));
					System.out.println("     Path: " + hit.filePath);
					System.out.println("     Preview: " + hit.getPreview(100));
					System.out.println("     Full text length: " + (hit.text != null ? hit.text.length() : 0) + " chars");
					System.out.println();
				}

				// Print summary
				System.out.println("=== Summary ===");
				System.out.println("Total results: " + results.size());
				if (results.size() > 0) {
					System.out.println("Top score: " + String.format("%.4f", results.get(0).score));
					System.out.println("Bottom score: " + String.format("%.4f", results.get(results.size() - 1).score));
				}

			} catch (Exception e) {
				System.err.println("Database error: " + e.getMessage());
				e.printStackTrace();
				System.exit(1);
			}

		} catch (Exception e) {
			System.err.println("Error generating query embedding: " + e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}
	}
}
