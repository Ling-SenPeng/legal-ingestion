package com.ingestion.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.ingestion.entity.PaymentRecord;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository for PaymentRecord persistence.
 * Handles CRUD and query operations for extracted payment records.
 */
public class PaymentRecordRepository {

    /**
     * Insert a single payment record and return its ID.
     */
    public static long insert(Connection conn, PaymentRecord record) throws Exception {
        String sql = 
            "INSERT INTO payment_records " +
            "(pdf_document_id, statement_index, statement_period_start, statement_period_end, " +
            "payment_date, category, total_amount, principal_amount, interest_amount, " +
            "escrow_amount, tax_amount, insurance_amount, currency, payer_name, payee_name, " +
            "loan_number, property_address, property_city, property_state, property_zip, description, " +
            "source_page, source_snippet, confidence, raw_llm_json, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?) " +
            "RETURNING id";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, record.getPdfDocumentId());
            stmt.setObject(2, record.getStatementIndex());
            stmt.setObject(3, record.getStatementPeriodStart());
            stmt.setObject(4, record.getStatementPeriodEnd());
            stmt.setObject(5, record.getPaymentDate());
            stmt.setString(6, record.getCategory());
            stmt.setBigDecimal(7, record.getTotalAmount());
            stmt.setBigDecimal(8, record.getPrincipalAmount());
            stmt.setBigDecimal(9, record.getInterestAmount());
            stmt.setBigDecimal(10, record.getEscrowAmount());
            stmt.setBigDecimal(11, record.getTaxAmount());
            stmt.setBigDecimal(12, record.getInsuranceAmount());
            stmt.setString(13, record.getCurrency() != null ? record.getCurrency() : "USD");
            stmt.setString(14, record.getPayerName());
            stmt.setString(15, record.getPayeeName());
            stmt.setString(16, record.getLoanNumber());
            stmt.setString(17, record.getPropertyAddress());
            stmt.setString(18, record.getPropertyCity());
            stmt.setString(19, record.getPropertyState());
            stmt.setString(20, record.getPropertyZip());
            stmt.setString(21, record.getDescription());
            stmt.setObject(22, record.getSourcePage());
            stmt.setString(23, record.getSourceSnippet());
            stmt.setBigDecimal(24, record.getConfidence());
            
            String jsonString = record.getRawLlmJson() != null ? record.getRawLlmJson().toString() : null;
            stmt.setString(25, jsonString);
            
            stmt.setObject(26, record.getCreatedAt() != null ? record.getCreatedAt() : Instant.now());
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("id");
                }
            }
        }
        throw new Exception("Failed to insert PaymentRecord");
    }

    /**
     * Batch insert multiple payment records.
     */
    public static int[] batchInsert(Connection conn, List<PaymentRecord> records) throws Exception {
        String sql = 
            "INSERT INTO payment_records " +
            "(pdf_document_id, statement_index, statement_period_start, statement_period_end, " +
            "payment_date, category, total_amount, principal_amount, interest_amount, " +
            "escrow_amount, tax_amount, insurance_amount, currency, payer_name, payee_name, " +
            "loan_number, property_address, property_city, property_state, property_zip, description, " +
            "source_page, source_snippet, confidence, raw_llm_json, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (PaymentRecord record : records) {
                stmt.setLong(1, record.getPdfDocumentId());
                stmt.setObject(2, record.getStatementIndex());
                stmt.setObject(3, record.getStatementPeriodStart());
                stmt.setObject(4, record.getStatementPeriodEnd());
                stmt.setObject(5, record.getPaymentDate());
                stmt.setString(6, record.getCategory());
                stmt.setBigDecimal(7, record.getTotalAmount());
                stmt.setBigDecimal(8, record.getPrincipalAmount());
                stmt.setBigDecimal(9, record.getInterestAmount());
                stmt.setBigDecimal(10, record.getEscrowAmount());
                stmt.setBigDecimal(11, record.getTaxAmount());
                stmt.setBigDecimal(12, record.getInsuranceAmount());
                stmt.setString(13, record.getCurrency() != null ? record.getCurrency() : "USD");
                stmt.setString(14, record.getPayerName());
                stmt.setString(15, record.getPayeeName());
                stmt.setString(16, record.getLoanNumber());
                stmt.setString(17, record.getPropertyAddress());
                stmt.setString(18, record.getPropertyCity());
                stmt.setString(19, record.getPropertyState());
                stmt.setString(20, record.getPropertyZip());
                stmt.setString(21, record.getDescription());
                stmt.setObject(22, record.getSourcePage());
                stmt.setString(23, record.getSourceSnippet());
                stmt.setBigDecimal(24, record.getConfidence());
                
                String jsonString = record.getRawLlmJson() != null ? record.getRawLlmJson().toString() : null;
                stmt.setString(25, jsonString);
                
                // Convert Instant to java.sql.Timestamp for PostgreSQL
                java.sql.Timestamp createdAtTs = record.getCreatedAt() != null ? 
                    java.sql.Timestamp.from(record.getCreatedAt()) : 
                    java.sql.Timestamp.from(Instant.now());
                stmt.setTimestamp(26, createdAtTs);
                
                stmt.addBatch();
            }
            return stmt.executeBatch();
        }
    }

    /**
     * Find all payments for a PDF document.
     */
    public static List<PaymentRecord> findByPdfDocumentId(Connection conn, Long pdfDocumentId) throws Exception {
        String sql = "SELECT * FROM payment_records WHERE pdf_document_id = ? ORDER BY id ASC";
        List<PaymentRecord> records = new ArrayList<>();
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, pdfDocumentId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    records.add(mapRowToPaymentRecord(rs));
                }
            }
        }
        return records;
    }

    /**
     * Find payments for a specific statement in a PDF.
     */
    public static List<PaymentRecord> findByPdfDocumentIdAndStatementIndex(
            Connection conn, Long pdfDocumentId, Integer statementIndex) throws Exception {
        String sql = 
            "SELECT * FROM payment_records " +
            "WHERE pdf_document_id = ? AND statement_index = ? " +
            "ORDER BY id ASC";
        List<PaymentRecord> records = new ArrayList<>();
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, pdfDocumentId);
            stmt.setInt(2, statementIndex);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    records.add(mapRowToPaymentRecord(rs));
                }
            }
        }
        return records;
    }

    /**
     * Find payments by category.
     */
    public static List<PaymentRecord> findByCategory(Connection conn, String category) throws Exception {
        String sql = "SELECT * FROM payment_records WHERE category = ? ORDER BY payment_date DESC";
        List<PaymentRecord> records = new ArrayList<>();
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, category);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    records.add(mapRowToPaymentRecord(rs));
                }
            }
        }
        return records;
    }

    /**
     * Count payments for a PDF document.
     */
    public static long countByPdfDocumentId(Connection conn, Long pdfDocumentId) throws Exception {
        String sql = "SELECT COUNT(*) as count FROM payment_records WHERE pdf_document_id = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, pdfDocumentId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("count");
                }
            }
        }
        return 0L;
    }

    /**
     * Delete all payment records for a PDF document.
     * Used for re-processing PDFs with new extraction results.
     */
    public static int deleteByPdfDocumentId(Connection conn, Long pdfDocumentId) throws Exception {
        String sql = "DELETE FROM payment_records WHERE pdf_document_id = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, pdfDocumentId);
            return stmt.executeUpdate();
        }
    }

    /**
     * Map a ResultSet row to a PaymentRecord entity.
     */
    private static PaymentRecord mapRowToPaymentRecord(ResultSet rs) throws Exception {
        PaymentRecord record = new PaymentRecord();
        record.setId(rs.getLong("id"));
        record.setPdfDocumentId(rs.getLong("pdf_document_id"));
        
        // Statement context
        Object stmtIndexObj = rs.getObject("statement_index");
        if (stmtIndexObj != null) {
            record.setStatementIndex((Integer) stmtIndexObj);
        }
        
        Object stmtPeriodStartObj = rs.getObject("statement_period_start");
        if (stmtPeriodStartObj != null) {
            record.setStatementPeriodStart((LocalDate) stmtPeriodStartObj);
        }
        
        Object stmtPeriodEndObj = rs.getObject("statement_period_end");
        if (stmtPeriodEndObj != null) {
            record.setStatementPeriodEnd((LocalDate) stmtPeriodEndObj);
        }
        
        // Payment date
        Object paymentDateObj = rs.getObject("payment_date");
        if (paymentDateObj != null) {
            record.setPaymentDate((LocalDate) paymentDateObj);
        }
        
        // Category and amounts
        record.setCategory(rs.getString("category"));
        record.setTotalAmount(rs.getBigDecimal("total_amount"));
        record.setPrincipalAmount(rs.getBigDecimal("principal_amount"));
        record.setInterestAmount(rs.getBigDecimal("interest_amount"));
        record.setEscrowAmount(rs.getBigDecimal("escrow_amount"));
        record.setTaxAmount(rs.getBigDecimal("tax_amount"));
        record.setInsuranceAmount(rs.getBigDecimal("insurance_amount"));
        
        // Currency and parties
        record.setCurrency(rs.getString("currency"));
        record.setPayerName(rs.getString("payer_name"));
        record.setPayeeName(rs.getString("payee_name"));
        record.setLoanNumber(rs.getString("loan_number"));
        
        // Property info
        record.setPropertyAddress(rs.getString("property_address"));
        record.setPropertyCity(rs.getString("property_city"));
        record.setPropertyState(rs.getString("property_state"));
        record.setPropertyZip(rs.getString("property_zip"));
        
        // Description and provenance
        record.setDescription(rs.getString("description"));
        
        Object sourcePageObj = rs.getObject("source_page");
        if (sourcePageObj != null) {
            record.setSourcePage((Integer) sourcePageObj);
        }
        
        record.setSourceSnippet(rs.getString("source_snippet"));
        record.setConfidence(rs.getBigDecimal("confidence"));
        
        // Raw JSON (handled as string from JSONB column)
        String rawJsonStr = rs.getString("raw_llm_json");
        if (rawJsonStr != null) {
            // Would need ObjectMapper to deserialize here if needed
            // For now, storing as null or string
        }
        
        // Timestamp
        java.sql.Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            record.setCreatedAt(createdAt.toInstant());
        }
        
        return record;
    }
}
