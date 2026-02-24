package com.ingestion;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;

/**
 * Repository for PDF document metadata persistence.
 * Handles upsert operations with SHA256 deduplication and status tracking.
 */
public class DocumentRepo {

	private final String jdbcUrl;
	private final String dbUser;
	private final String dbPassword;

	public DocumentRepo(String jdbcUrl, String dbUser, String dbPassword) {
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
	 * Upsert a PDF document record and return its ID.
	 * If the document already exists (by sha256), update its metadata and status to PROCESSING.
	 * Otherwise, insert a new record with status='NEW'.
	 *
	 * @param fileName the file name
	 * @param filePath the full file path
	 * @param sha256 the SHA256 hash of the file content
	 * @param fileSize the file size in bytes
	 * @return the document ID
	 * @throws Exception if a database error occurs
	 */
	public long upsertAndGetId(String fileName, String filePath, String sha256, long fileSize) throws Exception {
		try (Connection conn = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword)) {
			// Check if document exists by sha256
			String checkSql = "SELECT id, status FROM pdf_documents WHERE sha256 = ?";
			try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
				checkStmt.setString(1, sha256);
				try (ResultSet rs = checkStmt.executeQuery()) {
					if (rs.next()) {
						long docId = rs.getLong("id");
						String status = rs.getString("status");
						
						// If not already processing, update status to PROCESSING
						if (!"PROCESSING".equals(status) && !"DONE".equals(status)) {
							String updateStatusSql = "UPDATE pdf_documents SET status = 'PROCESSING' WHERE id = ?";
							try (PreparedStatement updateStmt = conn.prepareStatement(updateStatusSql)) {
								updateStmt.setLong(1, docId);
								updateStmt.executeUpdate();
							}
						}
						
						return docId;
					}
				}
			}

			// Document does not exist, insert new record with status='NEW'
			String insertSql = "INSERT INTO pdf_documents (file_name, file_path, sha256, file_size, status, created_at) " +
				"VALUES (?, ?, ?, ?, 'NEW', CURRENT_TIMESTAMP)";
			try (PreparedStatement insertStmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
				insertStmt.setString(1, fileName);
				insertStmt.setString(2, filePath);
				insertStmt.setString(3, sha256);
				insertStmt.setLong(4, fileSize);
				insertStmt.executeUpdate();

				try (ResultSet generatedKeys = insertStmt.getGeneratedKeys()) {
					if (generatedKeys.next()) {
						return generatedKeys.getLong(1);
					}
				}
			}

			throw new Exception("Failed to insert document and get ID");
		}
	}

	/**
	 * Mark a document as successfully processed.
	 *
	 * @param docId the document ID
	 * @throws Exception if a database error occurs
	 */
	public void markDone(long docId) throws Exception {
		try (Connection conn = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword)) {
			String sql = "UPDATE pdf_documents SET status = 'DONE', processed_at = CURRENT_TIMESTAMP WHERE id = ?";
			try (PreparedStatement stmt = conn.prepareStatement(sql)) {
				stmt.setLong(1, docId);
				stmt.executeUpdate();
			}
		}
	}

	/**
	 * Mark a document as failed to process and store the error message.
	 *
	 * @param docId the document ID
	 * @param errorMsg the error message
	 * @throws Exception if a database error occurs
	 */
	public void markFailed(long docId, String errorMsg) throws Exception {
		try (Connection conn = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword)) {
			String sql = "UPDATE pdf_documents SET status = 'FAILED', error_msg = ?, processed_at = CURRENT_TIMESTAMP WHERE id = ?";
			try (PreparedStatement stmt = conn.prepareStatement(sql)) {
				stmt.setString(1, errorMsg);
				stmt.setLong(2, docId);
				stmt.executeUpdate();
			}
		}
	}

	/**
	 * Get document status by ID.
	 *
	 * @param docId the document ID
	 * @return the status (NEW, PROCESSING, DONE, FAILED, or null if not found)
	 * @throws Exception if a database error occurs
	 */
	public String getStatus(long docId) throws Exception {
		try (Connection conn = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword)) {
			String sql = "SELECT status FROM pdf_documents WHERE id = ?";
			try (PreparedStatement stmt = conn.prepareStatement(sql)) {
				stmt.setLong(1, docId);
				try (ResultSet rs = stmt.executeQuery()) {
					if (rs.next()) {
						return rs.getString("status");
					}
				}
			}
		}
		return null;
	}
}
