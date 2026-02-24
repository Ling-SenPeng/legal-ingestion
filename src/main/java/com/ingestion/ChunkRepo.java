package com.ingestion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
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

	/**
	 * Fetch chunks with missing embeddings.
	 * Queries chunks where embedding IS NULL.
	 *
	 * @param conn the database connection
	 * @param limit the maximum number of chunks to fetch
	 * @return a list of ChunkRow objects
	 * @throws Exception if a database error occurs
	 */
	public static List<ChunkRow> fetchChunksMissingEmbedding(Connection conn, int limit) throws Exception {
		String sql = "SELECT id, text FROM pdf_chunks WHERE embedding IS NULL LIMIT ?";
		List<ChunkRow> chunks = new ArrayList<>();

		try (PreparedStatement stmt = conn.prepareStatement(sql)) {
			stmt.setInt(1, limit);
			try (ResultSet rs = stmt.executeQuery()) {
				while (rs.next()) {
					long id = rs.getLong("id");
					String text = rs.getString("text");
					chunks.add(new ChunkRow(id, text));
				}
			}
		}

		return chunks;
	}

	/**
	 * Update the embedding for a chunk.
	 * Updates pdf_chunks.embedding for the given chunk ID.
	 *
	 * @param conn the database connection
	 * @param chunkId the chunk ID
	 * @param embedding the embedding vector (1536 dimensions)
	 * @throws Exception if a database error occurs
	 */
	public static void updateEmbedding(Connection conn, long chunkId, float[] embedding) throws Exception {
		String sql = "UPDATE pdf_chunks SET embedding = ?::vector WHERE id = ?";

		try (PreparedStatement stmt = conn.prepareStatement(sql)) {
			if (embedding != null && embedding.length > 0) {
				// Convert float[] to PostgreSQL vector format: "[0.1, 0.2, ...]"
				StringBuilder vecStr = new StringBuilder("[");
				for (int i = 0; i < embedding.length; i++) {
					if (i > 0) vecStr.append(",");
					vecStr.append(embedding[i]);
				}
				vecStr.append("]");
				stmt.setString(1, vecStr.toString());
			} else {
				stmt.setNull(1, Types.OTHER);
			}
			stmt.setLong(2, chunkId);
			stmt.executeUpdate();
		}
	}

	/**
	 * Search chunks by vector similarity using pgvector cosine distance.
	 * Joins with pdf_documents to get file metadata and returns TopK results.
	 *
	 * @param conn the database connection
	 * @param queryVec the query embedding vector (1536 dimensions)
	 * @param topK the number of results to return
	 * @return a list of SearchHit objects sorted by similarity score (highest first)
	 * @throws Exception if a database error occurs
	 */
	public static List<SearchHit> searchByVector(Connection conn, float[] queryVec, int topK) throws Exception {
		// Convert query vector to PostgreSQL format
		StringBuilder vecStr = new StringBuilder("[");
		for (int i = 0; i < queryVec.length; i++) {
			if (i > 0) vecStr.append(",");
			vecStr.append(queryVec[i]);
		}
		vecStr.append("]");
		String queryVecStr = vecStr.toString();

		// SQL: cosine distance (1 - cosine_similarity)
		// pgvector <=> operator returns cosine distance, so score = 1 - distance
		String sql =
			"SELECT d.file_name, d.file_path, c.page_no, c.text, " +
			"       (1 - (c.embedding <=> ?::vector)) as score " +
			"FROM pdf_chunks c " +
			"JOIN pdf_documents d ON d.id = c.doc_id " +
			"WHERE c.embedding IS NOT NULL " +
			"ORDER BY c.embedding <=> ?::vector " +
			"LIMIT ?";

		List<SearchHit> hits = new ArrayList<>();

		try (PreparedStatement stmt = conn.prepareStatement(sql)) {
			stmt.setString(1, queryVecStr);
			stmt.setString(2, queryVecStr);
			stmt.setInt(3, topK);

			try (ResultSet rs = stmt.executeQuery()) {
				while (rs.next()) {
					String fileName = rs.getString("file_name");
					String filePath = rs.getString("file_path");
					int pageNo = rs.getInt("page_no");
					String text = rs.getString("text");
					double score = rs.getDouble("score");

					hits.add(new SearchHit(fileName, filePath, pageNo, text, score));
				}
			}
		}

		return hits;
	}
}
