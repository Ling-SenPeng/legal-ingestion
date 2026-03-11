package com.injestion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Repository for PDF document metadata persistence.
 * Handles upsert operations with SHA256 deduplication and status tracking.
 * Uses single UPSERT SQL statement for optimal performance.
 */
public class DocumentRepo {

	/**
	 * Ensure the PostgreSQL JDBC driver is loaded.
	 */
	public static void ensureDriverLoaded() {
		try {
			Class.forName("org.postgresql.Driver");
		} catch (ClassNotFoundException e) {
			System.err.println("Warning: PostgreSQL JDBC driver not found. Database operations may fail.");
		}
	}

	/**
	 * Upsert a PDF document record and return its ID.
	 * Uses single INSERT ... ON CONFLICT ... RETURNING id statement.
	 * Sets status to 'PROCESSING' and clears error_msg on upsert.
	 *
	 * @param conn the database connection
	 * @param fileName the file name
	 * @param filePath the full file path
	 * @param sha256 the SHA256 hash of the file content
	 * @param fileSize the file size in bytes
	 * @return the document ID
	 * @throws Exception if a database error occurs
	 */
	public static long upsertAndGetId(Connection conn, String fileName, String filePath, String sha256, long fileSize) throws Exception {
		String upsertSql = 
			"INSERT INTO pdf_documents (file_name, file_path, sha256, file_size, status, error_msg, created_at) " +
			"VALUES (?, ?, ?, ?, 'PROCESSING', NULL, CURRENT_TIMESTAMP) " +
			"ON CONFLICT (sha256) DO UPDATE " +
			"SET file_name = EXCLUDED.file_name, " +
			"    file_path = EXCLUDED.file_path, " +
			"    file_size = EXCLUDED.file_size, " +
			"    status = 'PROCESSING', " +
			"    error_msg = NULL " +
			"RETURNING id";
		
		try (PreparedStatement stmt = conn.prepareStatement(upsertSql)) {
			stmt.setString(1, fileName);
			stmt.setString(2, filePath);
			stmt.setString(3, sha256);
			stmt.setLong(4, fileSize);
			
			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					return rs.getLong("id");
				}
			}
		}
		
		throw new Exception("Failed to upsert document and get ID");
	}

	/**
	 * Mark a document as successfully processed.
	 *
	 * @param conn the database connection
	 * @param docId the document ID
	 * @throws Exception if a database error occurs
	 */
	public static void markDone(Connection conn, long docId) throws Exception {
		String sql = "UPDATE pdf_documents SET status = 'DONE', processed_at = CURRENT_TIMESTAMP WHERE id = ?";
		try (PreparedStatement stmt = conn.prepareStatement(sql)) {
			stmt.setLong(1, docId);
			stmt.executeUpdate();
		}
	}

	/**
	 * Mark a document as failed to process and store the error message.
	 *
	 * @param conn the database connection
	 * @param docId the document ID
	 * @param errorMsg the error message
	 * @throws Exception if a database error occurs
	 */
	public static void markFailed(Connection conn, long docId, String errorMsg) throws Exception {
		String sql = "UPDATE pdf_documents SET status = 'FAILED', error_msg = ?, processed_at = CURRENT_TIMESTAMP WHERE id = ?";
		try (PreparedStatement stmt = conn.prepareStatement(sql)) {
			stmt.setString(1, errorMsg);
			stmt.setLong(2, docId);
			stmt.executeUpdate();
		}
	}

	/**
	 * Get document status by ID.
	 *
	 * @param conn the database connection
	 * @param docId the document ID
	 * @return the status (NEW, PROCESSING, DONE, FAILED, or null if not found)
	 * @throws Exception if a database error occurs
	 */
	public static String getStatus(Connection conn, long docId) throws Exception {
		String sql = "SELECT status FROM pdf_documents WHERE id = ?";
		try (PreparedStatement stmt = conn.prepareStatement(sql)) {
			stmt.setLong(1, docId);
			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					return rs.getString("status");
				}
			}
		}
		return null;
	}
}
