package com.ingestion.repository;

import com.ingestion.entity.PdfPaymentExtractionRun;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository for PdfPaymentExtractionRun persistence.
 * Handles CRUD and query operations for payment extraction run records.
 */
public class PdfPaymentExtractionRunRepository {

    /**
     * Create a new extraction run record and return its ID.
     */
    public static long create(Connection conn, PdfPaymentExtractionRun run) throws Exception {
        String sql = 
            "INSERT INTO pdf_payment_extraction_runs " +
            "(pdf_document_id, status, model_name, prompt_version, statement_count, payment_count, " +
            "is_scanned, error_msg, raw_llm_response, created_at, completed_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?) " +
            "RETURNING id";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, run.getPdfDocumentId());
            stmt.setString(2, run.getStatus() != null ? run.getStatus() : "PENDING");
            stmt.setString(3, run.getModelName());
            stmt.setString(4, run.getPromptVersion());
            stmt.setObject(5, run.getStatementCount());
            stmt.setObject(6, run.getPaymentCount());
            stmt.setObject(7, run.getIsScanned());
            stmt.setString(8, run.getErrorMsg());
            
            String jsonString = run.getRawLlmResponse() != null ? run.getRawLlmResponse().toString() : null;
            stmt.setString(9, jsonString);
            
            // Convert Instant to java.sql.Timestamp for PostgreSQL
            java.sql.Timestamp createdAtTs = run.getCreatedAt() != null ? 
                java.sql.Timestamp.from(run.getCreatedAt()) : 
                java.sql.Timestamp.from(Instant.now());
            stmt.setTimestamp(10, createdAtTs);
            
            java.sql.Timestamp completedAtTs = run.getCompletedAt() != null ? 
                java.sql.Timestamp.from(run.getCompletedAt()) : null;
            stmt.setTimestamp(11, completedAtTs);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("id");
                }
            }
        }
        throw new Exception("Failed to insert PdfPaymentExtractionRun");
    }

    /**
     * Find an extraction run by ID.
     */
    public static PdfPaymentExtractionRun findById(Connection conn, Long id) throws Exception {
        String sql = "SELECT * FROM pdf_payment_extraction_runs WHERE id = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRowToExtractionRun(rs);
                }
            }
        }
        return null;
    }

    /**
     * Find extraction run by PDF document ID.
     */
    public static PdfPaymentExtractionRun findByPdfDocumentId(Connection conn, Long pdfDocumentId) throws Exception {
        String sql = "SELECT * FROM pdf_payment_extraction_runs WHERE pdf_document_id = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, pdfDocumentId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRowToExtractionRun(rs);
                }
            }
        }
        return null;
    }

    /**
     * Find all extraction runs with a specific status.
     */
    public static List<PdfPaymentExtractionRun> findByStatus(Connection conn, String status) throws Exception {
        String sql = "SELECT * FROM pdf_payment_extraction_runs WHERE status = ? ORDER BY created_at DESC";
        List<PdfPaymentExtractionRun> runs = new ArrayList<>();
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    runs.add(mapRowToExtractionRun(rs));
                }
            }
        }
        return runs;
    }

    /**
     * Update an extraction run.
     */
    public static void update(Connection conn, PdfPaymentExtractionRun run) throws Exception {
        String sql = 
            "UPDATE pdf_payment_extraction_runs " +
            "SET status = ?, model_name = ?, prompt_version = ?, statement_count = ?, " +
            "payment_count = ?, is_scanned = ?, error_msg = ?, raw_llm_response = ?::jsonb, completed_at = ? " +
            "WHERE id = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, run.getStatus());
            stmt.setString(2, run.getModelName());
            stmt.setString(3, run.getPromptVersion());
            stmt.setObject(4, run.getStatementCount());
            stmt.setObject(5, run.getPaymentCount());
            stmt.setObject(6, run.getIsScanned());
            stmt.setString(7, run.getErrorMsg());
            
            String jsonString = run.getRawLlmResponse() != null ? run.getRawLlmResponse().toString() : null;
            stmt.setString(8, jsonString);
            
            // Convert Instant to java.sql.Timestamp for PostgreSQL
            java.sql.Timestamp completedAtTs = run.getCompletedAt() != null ? 
                java.sql.Timestamp.from(run.getCompletedAt()) : null;
            stmt.setTimestamp(9, completedAtTs);
            stmt.setLong(10, run.getId());
            
            stmt.executeUpdate();
        }
    }

    /**
     * Map a ResultSet row to a PdfPaymentExtractionRun entity.
     */
    private static PdfPaymentExtractionRun mapRowToExtractionRun(ResultSet rs) throws Exception {
        PdfPaymentExtractionRun run = new PdfPaymentExtractionRun();
        run.setId(rs.getLong("id"));
        run.setPdfDocumentId(rs.getLong("pdf_document_id"));
        run.setStatus(rs.getString("status"));
        run.setModelName(rs.getString("model_name"));
        run.setPromptVersion(rs.getString("prompt_version"));
        
        Object stmtCountObj = rs.getObject("statement_count");
        if (stmtCountObj != null) {
            run.setStatementCount((Integer) stmtCountObj);
        }
        
        Object paymentCountObj = rs.getObject("payment_count");
        if (paymentCountObj != null) {
            run.setPaymentCount((Integer) paymentCountObj);
        }
        
        Object isScannedObj = rs.getObject("is_scanned");
        if (isScannedObj != null) {
            run.setIsScanned((Boolean) isScannedObj);
        }
        
        run.setErrorMsg(rs.getString("error_msg"));
        
        // Raw JSON (handled as string from JSONB column)
        String rawJsonStr = rs.getString("raw_llm_response");
        if (rawJsonStr != null) {
            // Would need ObjectMapper to deserialize here if needed
            // For now, storing as null or string
        }
        
        java.sql.Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            run.setCreatedAt(createdAt.toInstant());
        }
        
        java.sql.Timestamp completedAt = rs.getTimestamp("completed_at");
        if (completedAt != null) {
            run.setCompletedAt(completedAt.toInstant());
        }
        
        return run;
    }
}
