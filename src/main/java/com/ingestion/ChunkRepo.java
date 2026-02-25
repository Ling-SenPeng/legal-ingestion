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

	// Configuration constants for sliding window chunking
	private static final int DEFAULT_WINDOW_SIZE = 2;
	private static final int DEFAULT_STRIDE = 1;
	private static final int MIN_PARAGRAPH_CHARS = 50;

	/**
	 * Insert page chunks for a document using sliding window paragraph chunking (Legal RAG).
	 * Default: windowSize=2, stride=1 (produces overlapping context windows).
	 *
	 * @param conn the database connection
	 * @param docId the document ID
	 * @param pages the list of PageText objects (one per page)
	 * @return the total number of chunks inserted/updated
	 * @throws Exception if a database error occurs
	 */
	public static int insertPageChunks(Connection conn, long docId, List<PageText> pages) throws Exception {
		return insertPageChunks(conn, docId, pages, DEFAULT_WINDOW_SIZE, DEFAULT_STRIDE);
	}

	/**
	 * Insert page chunks for a document using sliding window paragraph chunking (Legal RAG).
	 * Splits each page into paragraphs, generates window chunks for enhanced semantic context.
	 * Uses upsert (INSERT ... ON CONFLICT ... DO UPDATE) to handle duplicates.
	 *
	 * Processing:
	 * 1. Split page text by blank lines: text.split("\\n\\s*\\n")
	 * 2. Filter: trim, keep only paragraphs >= 50 characters
	 * 3. Generate sliding windows:
	 *    - for i = 0 to paragraphs.size()-1 step stride
	 *    - take paragraphs[i .. i+windowSize-1]
	 *    - if fewer than windowSize remain: include as-is if enough paragraphs
	 * 4. Combine window paragraphs with "\\n\\n" separator
	 * 5. Write to DB with metadata: window_size, stride, paragraph_count
	 *
	 * Example (windowSize=2, stride=1):
	 * - Para[0,1]: "Para0 text\\n\\nPara1 text" → chunk_index=0
	 * - Para[1,2]: "Para1 text\\n\\nPara2 text" → chunk_index=1
	 * - Para[2,3]: "Para2 text\\n\\nPara3 text" → chunk_index=2
	 *
	 * @param conn the database connection
	 * @param docId the document ID
	 * @param pages the list of PageText objects (one per page)
	 * @param windowSize number of paragraphs per window (default: 2)
	 * @param stride number of paragraphs to slide per step (default: 1)
	 * @return the total number of chunks inserted/updated
	 * @throws Exception if a database error occurs
	 */
	public static int insertPageChunks(Connection conn, long docId, List<PageText> pages, int windowSize, int stride) throws Exception {
		if (pages == null || pages.isEmpty()) {
			return 0;
		}

		// Upsert SQL: insert or update on conflict
		String upsertSql = 
			"INSERT INTO pdf_chunks (doc_id, page_no, chunk_index, text, meta, created_at) " +
			"VALUES (?, ?, ?, ?, ?::jsonb, CURRENT_TIMESTAMP) " +
			"ON CONFLICT (doc_id, page_no, chunk_index) DO UPDATE " +
			"SET text = EXCLUDED.text, meta = EXCLUDED.meta, created_at = CURRENT_TIMESTAMP";

		int totalChunksInserted = 0;

		try (PreparedStatement stmt = conn.prepareStatement(upsertSql)) {
			for (PageText page : pages) {
				// Step 1: Split page text by blank lines (paragraph separator)
				// Pattern: \n\s*\n matches newline, optional whitespace, newline
				String[] rawParagraphs = page.text.split("\\n\\s*\\n");

				// Step 2: Filter paragraphs - keep only those >= minParaChars after trim
				List<String> validParagraphs = new ArrayList<>();
				for (String p : rawParagraphs) {
					String trimmed = p.trim();
					if (trimmed.length() >= MIN_PARAGRAPH_CHARS) {
						validParagraphs.add(trimmed);
					}
				}

				// Step 3: Generate sliding window chunks
				int chunkIndex = 0;
				for (int i = 0; i < validParagraphs.size(); i += stride) {
					// Determine window end: take min(i+windowSize, paragraphs.size())
					int windowEnd = Math.min(i + windowSize, validParagraphs.size());
					int actualWindowSize = windowEnd - i;

					// Skip if window has fewer than 1 paragraph (shouldn't happen with stride >= 1)
					if (actualWindowSize < 1) {
						break;
					}

					// Build window chunk text by joining paragraphs with "\\n\\n"
					StringBuilder windowText = new StringBuilder();
					for (int j = i; j < windowEnd; j++) {
						if (j > i) {
							windowText.append("\n\n");  // Paragraph separator
						}
						windowText.append(validParagraphs.get(j));
					}

					String chunkText = windowText.toString();

					// Build metadata with window information
					String metaJson = buildMetaJson(chunkText.length(), "paragraph_window", windowSize, stride, actualWindowSize);

					// Insert window chunk
					stmt.setLong(1, docId);
					stmt.setInt(2, page.pageNo);
					stmt.setInt(3, chunkIndex);
					stmt.setString(4, chunkText);
					stmt.setString(5, metaJson);

					stmt.addBatch();
					totalChunksInserted++;
					chunkIndex++;
				}
			}

			// Execute batch
			if (totalChunksInserted > 0) {
				int[] results = stmt.executeBatch();
				return results.length;
			}

			return 0;
		}
	}

	/**
	 * Build JSON metadata for a window-based chunk.
	 * Includes: extractor (pdfbox), char_count, chunk_type, window_size, stride, paragraph_count
	 *
	 * @param charCount the character count of the chunk text
	 * @param chunkType the type of chunk (e.g., "paragraph_window")
	 * @param windowSize the number of paragraphs per window
	 * @param stride the stride size for sliding window
	 * @param paragraphCount the actual number of paragraphs in this window
	 * @return JSON string for the metadata
	 */
	private static String buildMetaJson(int charCount, String chunkType, int windowSize, int stride, int paragraphCount) {
		return String.format(
			"{\"extractor\": \"pdfbox\", \"char_count\": %d, \"chunk_type\": \"%s\", \"window_size\": %d, \"stride\": %d, \"paragraph_count\": %d}",
			charCount, chunkType, windowSize, stride, paragraphCount
		);
	}

	/**
	 * Build JSON metadata for a chunk.
	 * Includes: extractor (pdfbox), char_count, chunk_type
	 *
	 * @param charCount the character count of the chunk text
	 * @param chunkType the type of chunk (e.g., "paragraph_window", "page")
	 * @return JSON string for the metadata
	 */
	private static String buildMetaJson(int charCount, String chunkType) {
		return String.format(
			"{\"extractor\": \"pdfbox\", \"char_count\": %d, \"chunk_type\": \"%s\"}",
			charCount, chunkType
		);
	}

	/**
	 * Build JSON metadata for a chunk (legacy, defaults to "page" type).
	 * Includes: extractor (pdfbox), char_count
	 *
	 * @param charCount the character count of the chunk text
	 * @return JSON string for the metadata
	 */
	private static String buildMetaJson(int charCount) {
		return buildMetaJson(charCount, "page");
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

	/**
	 * Search chunks by vector similarity with detailed result information.
	 * Used by hybrid search to combine vector and keyword results.
	 * Returns detailed info including chunk_id and document metadata.
	 *
	 * @param conn the database connection
	 * @param queryVec the query embedding vector (1536 dimensions)
	 * @param limit the maximum number of results to return
	 * @return a list of maps containing: chunkId, docId, fileName, filePath, pageNo, text, score (vector score)
	 * @throws Exception if a database error occurs
	 */
	public static List<java.util.Map<String, Object>> searchByVectorDetailed(Connection conn, float[] queryVec, int limit) throws Exception {
		// Convert query vector to PostgreSQL format
		StringBuilder vecStr = new StringBuilder("[");
		for (int i = 0; i < queryVec.length; i++) {
			if (i > 0) vecStr.append(",");
			vecStr.append(queryVec[i]);
		}
		vecStr.append("]");
		String queryVecStr = vecStr.toString();

		// SQL: cosine distance (1 - cosine_similarity)
		String sql =
			"SELECT c.id as chunk_id, c.doc_id, d.file_name, d.file_path, c.page_no, c.text, " +
			"       (1 - (c.embedding <=> ?::vector)) as vector_score " +
			"FROM pdf_chunks c " +
			"JOIN pdf_documents d ON d.id = c.doc_id " +
			"WHERE c.embedding IS NOT NULL " +
			"ORDER BY c.embedding <=> ?::vector " +
			"LIMIT ?";

		List<java.util.Map<String, Object>> results = new ArrayList<>();

		try (PreparedStatement stmt = conn.prepareStatement(sql)) {
			stmt.setString(1, queryVecStr);
			stmt.setString(2, queryVecStr);
			stmt.setInt(3, limit);

			try (ResultSet rs = stmt.executeQuery()) {
				while (rs.next()) {
					java.util.Map<String, Object> row = new java.util.HashMap<>();
					row.put("chunkId", rs.getLong("chunk_id"));
					row.put("docId", rs.getLong("doc_id"));
					row.put("fileName", rs.getString("file_name"));
					row.put("filePath", rs.getString("file_path"));
					row.put("pageNo", rs.getInt("page_no"));
					row.put("text", rs.getString("text"));
					row.put("vectorScore", rs.getDouble("vector_score"));
					results.add(row);
				}
			}
		}

		return results;
	}

	/**
	 * Search chunks by keyword using full-text search (tsvector + plainto_tsquery).
	 * Used by hybrid search to combine vector and keyword results.
	 *
	 * @param conn the database connection
	 * @param query the search query (keywords, names, case numbers, etc.)
	 * @param limit the maximum number of results to return
	 * @return a list of maps containing: chunkId, docId, fileName, filePath, pageNo, text, score (keyword score)
	 * @throws Exception if a database error occurs
	 */
	public static List<java.util.Map<String, Object>> searchByKeyword(Connection conn, String query, int limit) throws Exception {
		// SQL: full-text search using tsvector and ts_rank for scoring
		String sql =
			"SELECT c.id as chunk_id, c.doc_id, d.file_name, d.file_path, c.page_no, c.text, " +
			"       ts_rank(c.ts, plainto_tsquery('english', ?)) as keyword_score " +
			"FROM pdf_chunks c " +
			"JOIN pdf_documents d ON d.id = c.doc_id " +
			"WHERE c.ts @@ plainto_tsquery('english', ?) " +
			"ORDER BY keyword_score DESC " +
			"LIMIT ?";

		List<java.util.Map<String, Object>> results = new ArrayList<>();

		try (PreparedStatement stmt = conn.prepareStatement(sql)) {
			stmt.setString(1, query);
			stmt.setString(2, query);
			stmt.setInt(3, limit);

			try (ResultSet rs = stmt.executeQuery()) {
				while (rs.next()) {
					java.util.Map<String, Object> row = new java.util.HashMap<>();
					row.put("chunkId", rs.getLong("chunk_id"));
					row.put("docId", rs.getLong("doc_id"));
					row.put("fileName", rs.getString("file_name"));
					row.put("filePath", rs.getString("file_path"));
					row.put("pageNo", rs.getInt("page_no"));
					row.put("text", rs.getString("text"));
					row.put("keywordScore", rs.getDouble("keyword_score"));
					results.add(row);
				}
			}
		}

		return results;
	}
}
