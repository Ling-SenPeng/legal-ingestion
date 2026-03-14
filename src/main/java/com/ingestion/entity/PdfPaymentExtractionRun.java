package com.ingestion.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/**
 * Domain model for a PDF payment extraction run.
 * Tracks LLM extraction attempt for a single PDF document.
 * Useful for retry logic, audit trail, and debugging.
 */
public class PdfPaymentExtractionRun {
    private Long id;
    private Long pdfDocumentId;
    
    // Extraction status
    private String status;  // PENDING, PROCESSING, COMPLETED, FAILED
    
    // Model information
    private String modelName;  // e.g., "claude-3-5-sonnet"
    private String promptVersion;
    
    // Result counts
    private Integer statementCount;  // number of statements found
    private Integer paymentCount;    // number of payments extracted
    
    // Input characteristics
    private Integer inputTextLength;  // length of document text sent to LLM
    private Boolean isScanned;  // indicates if PDF was scanned/OCR'd
    
    // Error tracking
    private String errorMsg;
    
    // Full LLM response for debugging and auditing
    private JsonNode rawLlmResponse;
    
    // Timestamps
    private Instant createdAt;
    private Instant completedAt;

    // Constructors
    public PdfPaymentExtractionRun() {}

    public PdfPaymentExtractionRun(Long pdfDocumentId) {
        this.pdfDocumentId = pdfDocumentId;
        this.status = "PENDING";
        this.createdAt = Instant.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPdfDocumentId() {
        return pdfDocumentId;
    }

    public void setPdfDocumentId(Long pdfDocumentId) {
        this.pdfDocumentId = pdfDocumentId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public Integer getStatementCount() {
        return statementCount;
    }

    public void setStatementCount(Integer statementCount) {
        this.statementCount = statementCount;
    }

    public Integer getPaymentCount() {
        return paymentCount;
    }

    public void setPaymentCount(Integer paymentCount) {
        this.paymentCount = paymentCount;
    }

    public Integer getInputTextLength() {
        return inputTextLength;
    }

    public void setInputTextLength(Integer inputTextLength) {
        this.inputTextLength = inputTextLength;
    }

    public Boolean getIsScanned() {
        return isScanned;
    }

    public void setIsScanned(Boolean isScanned) {
        this.isScanned = isScanned;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public JsonNode getRawLlmResponse() {
        return rawLlmResponse;
    }

    public void setRawLlmResponse(JsonNode rawLlmResponse) {
        this.rawLlmResponse = rawLlmResponse;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    @Override
    public String toString() {
        return "PdfPaymentExtractionRun{" +
                "id=" + id +
                ", pdfDocumentId=" + pdfDocumentId +
                ", status='" + status + '\'' +
                ", modelName='" + modelName + '\'' +
                ", statementCount=" + statementCount +
                ", paymentCount=" + paymentCount +
                ", isScanned=" + isScanned +
                '}';
    }
}
