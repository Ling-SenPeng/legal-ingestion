package com.ingestion;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.List;
import com.ingestion.PDFReader.PageText;
import org.postgresql.util.PGobject;

/**
 * Repository for PDF chunks (pages) persistence.
 * Handles insertion and upsert of chunk records with page-level granularity and metadata.
 */
public class ChunkRepo {

	private final String jdbcUrl;
	private final String dbUser;
	private final String dbPassword;

	public ChunkRepo(String jdbcUrl, String dbUser, String dbPassword) {
		this.jdbcUrl = jdbcUrl;
		this.dbUser = dbUser;
		this.dbPassword = dbPassword;
		ensureDriverLoaded();
	}

	/**
	 * Ensure the PostgreSQL JDBC driver is loaded.
	 */
	private void ensureDriverLoaded() {
		try {
			Class.forName("org.postgresql.Driver");
		} catch (ClassNotFoundException e) {
			System.err.println("Warning: PostgreSQL JDBC driver not found. Database operations may fail.");
		}
	}

	/**
	 * Insert page chunks for a document.
	 * Uses upsert (INSERT ... ON CONFLICT ... DO UPDATE) to handle duplicates.
	 * If a chunk already exists for (doc_id, page_no, chunk_index), updates the text and metadata.
	 *
	 * @param docId the document ID
	 * @param pages the list of PageText objects (one per page)
	 * @return the number of chunks inserted/updated
	 * @throws Exception if a database error occurs
	 */
	public int insertPageChunks(long docId, List<PageText> pages) throws Exception {
		int chunkCount = 0;

		try (Connection conn = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword)) {
			// Upsert SQL: insert or update on conflict
			String upsertSql = "INSERT INTO pdf_chunks (doc_id, page_no, chunk_index, text, meta, created_at) " +
				"VALUES (?, ?, ?, ?, ?::jsonb, CURRENT_TIMESTAMP) " +
				"ON CONFLICT (doc_id, page_no, chunk_index) DO UPDATE " +
				"SET text = EXCLUDED.text, meta = EXCLUDED.meta";

			try (PreparedStatement stmt = conn.prepareStatement(upsertSql)) {
				for (PageText page : pages) {
					// Build metadata JSON
					String metaJson = buildMetaJson(page.text.length());
					
					stmt.setLong(1, docId);
					stmt.setInt(2, page.pageNo);
					stmt.setInt(3, 0);  // chunk_index = 0 for MVP
					stmt.setString(4, page.text);
					stmt.setString(5, metaJson);

					stmt.addBatch();
					chunkCount++;
				}

				// Execute batch
				int[] results = stmt.executeBatch();
				System.out.println("  Inserted/updated " + results.length + " chunks");
			}
		}

		return chunkCount;
	}

	/**
	 * Build JSON metadata for a chunk.
	 * Includes: extractor (pdfbox), char_count
	 *
	 * @param charCount the character count of the chunk text
	 * @return JSON string for the metadata
	 */
	private String buildMetaJson(int charCount) {
		return String.format("{\"extractor\": \"pdfbox\", \"char_count\": %d}", charCount);
	}
}
