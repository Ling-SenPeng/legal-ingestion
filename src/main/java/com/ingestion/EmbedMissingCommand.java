package com.ingestion;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

/**
 * Command to generate and store embeddings for chunks missing embeddings.
 * Supports batch processing with error resilience.
 */
public class EmbedMissingCommand {

	private final OpenAIEmbeddingClient embeddingClient;
	private final String dbUrl;
	private final String dbUser;
	private final String dbPassword;

	public EmbedMissingCommand(String apiKey, String dbUrl, String dbUser, String dbPassword) throws Exception {
		this.embeddingClient = new OpenAIEmbeddingClient(apiKey);
		this.dbUrl = dbUrl;
		this.dbUser = dbUser;
		this.dbPassword = dbPassword;
		DocumentRepo.ensureDriverLoaded();
	}

	/**
	 * Execute the embed-missing command.
	 *
	 * @param limit maximum number of chunks to process in this batch
	 * @param batchSize batch size for updating embeddings
	 */
	public void execute(int limit, int batchSize) {
		System.out.println("=== Embed Missing Embeddings ===");
		System.out.println("Limit: " + limit + ", Batch Size: " + batchSize);
		System.out.println();

		int succeedCount = 0;
		int failureCount = 0;
		long startTime = System.currentTimeMillis();

		try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
			// Fetch chunks missing embeddings
			System.out.println("Fetching chunks with missing embeddings (limit=" + limit + ")...");
			List<ChunkRow> chunks = ChunkRepo.fetchChunksMissingEmbedding(conn, limit);
			System.out.println("Found " + chunks.size() + " chunk(s) missing embeddings");
			System.out.println();

			if (chunks.isEmpty()) {
				System.out.println("No chunks missing embeddings. Done.");
				return;
			}

			// Process each chunk
			for (int i = 0; i < chunks.size(); i++) {
				ChunkRow chunk = chunks.get(i);
				try {
					// Generate embedding
					float[] embedding = embeddingClient.embed(chunk.text);

					if (embedding != null) {
						// Update in database
						ChunkRepo.updateEmbedding(conn, chunk.id, embedding);
						succeedCount++;

						// Print progress
						if ((i + 1) % Math.max(1, batchSize / 2) == 0 || i == chunks.size() - 1) {
							System.out.println(
								String.format(
									"Progress: %d/%d chunks embedded (success=%d, failed=%d)",
									i + 1, chunks.size(), succeedCount, failureCount
								)
							);
						}
					} else {
						System.out.println("  ⊘ Skipped chunkId=" + chunk.id + " (text too short)");
					}

				} catch (Exception e) {
					failureCount++;
					System.err.println("  ✗ Error embedding chunkId=" + chunk.id + ": " + e.getMessage());
					// Continue with next chunk (resilient to errors)
				}
			}

		} catch (Exception e) {
			System.err.println("Fatal error: " + e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}

		// Summary
		long elapsedMs = System.currentTimeMillis() - startTime;
		System.out.println();
		System.out.println("=== Summary ===");
		System.out.println("Success: " + succeedCount);
		System.out.println("Failed: " + failureCount);
		System.out.println("Total processed: " + (succeedCount + failureCount));
		System.out.println("Elapsed time: " + String.format("%.2f", elapsedMs / 1000.0) + "s");

		if (failureCount > 0) {
			System.out.println("⚠ " + failureCount + " chunk(s) failed. You can retry this command.");
		}
	}
}
