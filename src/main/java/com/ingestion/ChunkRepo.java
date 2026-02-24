package com.ingestion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import com.ingestion.PDFReader.PageText;

/**
 * Repository for PDF chunks (pages) persistence.
 * Handles insertion and upsert of chunk records with page-level granularity and metadata.
 * Uses batch INSERT ... ON CONFLICT ... DO UPDATE for optimal performance.
 */
public class ChunkRepo {

	/**
	 * Insert page chunks for a document using batch upsert.
	 * Uses upsert (INSERT ... ON CONFLICT ... DO UPDATE) to handle duplicates.
	 * If a chunk already exists for (doc_id, page_no, chunk_index), updates the text and metadata.
	 *
	 * @param conn the database connection
	 * @param docId the document ID
	 * @param pages the list of PageText objects (one per page)
	 * @return the number of chunks inserted/updated
	 * @throws Exception if a database error occurs
	 */
	public static int insertPageChunks(Connection conn, long docId, List<PageText> pages) throws Exception {
		if (pages == null || pages.isEmpty()) {
			return 0;
		}

		// Upsert SQL: insert or update on conflict
		String upsertSql = 
			"INSERT INTO pdf_chunks (doc_id, page_no, chunk_index, text, meta, created_at) " +
			"VALUES (?, ?, ?, ?, ?::jsonb, CURRENT_TIMESTAMP) " +
			"ON CONFLICT (doc_id, page_no, chunk_index) DO UPDATE " +
			"SET text = EXCLUDED.text, meta = EXCLUDED.meta, created_at = CURRENT_TIMESTAMP";

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
			}

			// Execute batch
			int[] results = stmt.executeBatch();
			return results.length;
		}
	}

	/**
	 * Build JSON metadata for a chunk.
	 * Includes: extractor (pdfbox), char_count
	 *
	 * @param charCount the character count of the chunk text
	 * @return JSON string for the metadata
	 */
	private static String buildMetaJson(int charCount) {
		return String.format("{\"extractor\": \"pdfbox\", \"char_count\": %d}", charCount);
	}
}
