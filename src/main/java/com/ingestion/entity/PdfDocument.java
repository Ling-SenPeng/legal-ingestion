package com.ingestion.entity;

import java.time.Instant;

/**
 * Domain model for a PDF document.
 * Represents a stored PDF file with metadata and processing status.
 */
public class PdfDocument {
    private Long id;
    private String fileName;
    private String filePath;
    private String sha256;
    private Long fileSize;
    
    // Ingestion status (for PDF ingestion/chunking)
    private String status;  // NEW, PROCESSING, DONE, FAILED
    private String errorMsg;
    
    // Payment extraction status (for payment extraction pipeline)
    private String paymentExtractionStatus;  // NEW, RUNNING, SUCCEEDED, FAILED
    private String paymentExtractionErrorMsg;
    
    private Instant createdAt;
    private Instant processedAt;
    private Instant paymentExtractionCompletedAt;

    // Constructors
    public PdfDocument() {}

    public PdfDocument(String fileName, String filePath, String sha256, Long fileSize) {
        this.fileName = fileName;
        this.filePath = filePath;
        this.sha256 = sha256;
        this.fileSize = fileSize;
        this.status = "NEW";
        this.createdAt = Instant.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }

    public String getPaymentExtractionStatus() {
        return paymentExtractionStatus;
    }

    public void setPaymentExtractionStatus(String paymentExtractionStatus) {
        this.paymentExtractionStatus = paymentExtractionStatus;
    }

    public String getPaymentExtractionErrorMsg() {
        return paymentExtractionErrorMsg;
    }

    public void setPaymentExtractionErrorMsg(String paymentExtractionErrorMsg) {
        this.paymentExtractionErrorMsg = paymentExtractionErrorMsg;
    }

    public Instant getPaymentExtractionCompletedAt() {
        return paymentExtractionCompletedAt;
    }

    public void setPaymentExtractionCompletedAt(Instant paymentExtractionCompletedAt) {
        this.paymentExtractionCompletedAt = paymentExtractionCompletedAt;
    }
    public String toString() {
        return "PdfDocument{" +
                "id=" + id +
                ", fileName='" + fileName + '\'' +
                ", fileSize=" + fileSize +
                ", status='" + status + '\'' +
                '}';
    }
}
