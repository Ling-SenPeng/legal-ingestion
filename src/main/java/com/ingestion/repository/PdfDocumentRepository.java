package com.ingestion.repository;

import com.ingestion.entity.PdfDocument;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository for PdfDocument persistence.
 * Handles CRUD and query operations for PDF documents.
 */
public class PdfDocumentRepository {

    /**
     * Create a new PDF document record and return its ID.
     */
    public static long create(Connection conn, PdfDocument doc) throws Exception {
        String sql = 
            "INSERT INTO pdf_documents (file_name, file_path, sha256, file_size, status, error_msg, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?) " +
            "RETURNING id";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, doc.getFileName());
            stmt.setString(2, doc.getFilePath());
            stmt.setString(3, doc.getSha256());
            stmt.setLong(4, doc.getFileSize());
            stmt.setString(5, doc.getStatus() != null ? doc.getStatus() : "NEW");
            stmt.setString(6, doc.getErrorMsg());
            stmt.setObject(7, doc.getCreatedAt());
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("id");
                }
            }
        }
        throw new Exception("Failed to insert PdfDocument");
    }

    /**
     * Find a PDF document by ID.
     */
    public static PdfDocument findById(Connection conn, Long id) throws Exception {
        String sql = "SELECT * FROM pdf_documents WHERE id = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRowToDocument(rs);
                }
            }
        }
        return null;
    }

    /**
     * Find a PDF document by SHA256 hash.
     */
    public static PdfDocument findBySha256(Connection conn, String sha256) throws Exception {
        String sql = "SELECT * FROM pdf_documents WHERE sha256 = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, sha256);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRowToDocument(rs);
                }
            }
        }
        return null;
    }

    /**
     * Update a PDF document.
     */
    public static void update(Connection conn, PdfDocument doc) throws Exception {
        String sql = 
            "UPDATE pdf_documents SET file_name = ?, file_path = ?, status = ?, error_msg = ?, processed_at = ?, " +
            "payment_extraction_status = ?, payment_extraction_error_msg = ?, payment_extraction_completed_at = ? " +
            "WHERE id = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, doc.getFileName());
            stmt.setString(2, doc.getFilePath());
            stmt.setString(3, doc.getStatus());
            stmt.setString(4, doc.getErrorMsg());
            
            // Convert Instant to java.sql.Timestamp for processed_at
            java.sql.Timestamp processedAtTs = doc.getProcessedAt() != null ? 
                java.sql.Timestamp.from(doc.getProcessedAt()) : null;
            stmt.setObject(5, processedAtTs);
            
            stmt.setString(6, doc.getPaymentExtractionStatus());
            stmt.setString(7, doc.getPaymentExtractionErrorMsg());
            
            // Convert Instant to java.sql.Timestamp for payment_extraction_completed_at
            java.sql.Timestamp paymentCompletedAtTs = doc.getPaymentExtractionCompletedAt() != null ? 
                java.sql.Timestamp.from(doc.getPaymentExtractionCompletedAt()) : null;
            stmt.setObject(8, paymentCompletedAtTs);
            
            stmt.setLong(9, doc.getId());
            stmt.executeUpdate();
        }
    }

    /**
     * Find all PDF documents.
     */
    public static List<PdfDocument> findAll(Connection conn) throws Exception {
        String sql = "SELECT * FROM pdf_documents ORDER BY created_at DESC";
        List<PdfDocument> documents = new ArrayList<>();
        
        try (Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    documents.add(mapRowToDocument(rs));
                }
            }
        }
        return documents;
    }

    /**
     * Find all PDF documents with a specific status.
     */
    public static List<PdfDocument> findByStatus(Connection conn, String status) throws Exception {
        String sql = "SELECT * FROM pdf_documents WHERE status = ? ORDER BY created_at DESC";
        List<PdfDocument> documents = new ArrayList<>();
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    documents.add(mapRowToDocument(rs));
                }
            }
        }
        return documents;
    }

    /**
     * Map a ResultSet row to a PdfDocument entity.
     */
    private static PdfDocument mapRowToDocument(ResultSet rs) throws Exception {
        PdfDocument doc = new PdfDocument();
        doc.setId(rs.getLong("id"));
        doc.setFileName(rs.getString("file_name"));
        doc.setFilePath(rs.getString("file_path"));
        doc.setSha256(rs.getString("sha256"));
        doc.setFileSize(rs.getLong("file_size"));
        doc.setStatus(rs.getString("status"));
        doc.setErrorMsg(rs.getString("error_msg"));
        
        // Payment extraction status fields
        doc.setPaymentExtractionStatus(rs.getString("payment_extraction_status"));
        doc.setPaymentExtractionErrorMsg(rs.getString("payment_extraction_error_msg"));
        
        java.sql.Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            doc.setCreatedAt(createdAt.toInstant());
        }
        
        java.sql.Timestamp processedAt = rs.getTimestamp("processed_at");
        if (processedAt != null) {
            doc.setProcessedAt(processedAt.toInstant());
        }
        
        java.sql.Timestamp paymentExtractionCompletedAt = rs.getTimestamp("payment_extraction_completed_at");
        if (paymentExtractionCompletedAt != null) {
            doc.setPaymentExtractionCompletedAt(paymentExtractionCompletedAt.toInstant());
        }
        
        return doc;
    }
}
