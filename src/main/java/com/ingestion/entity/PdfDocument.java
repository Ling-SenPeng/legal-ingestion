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
    private String status;  // NEW, PROCESSING, COMPLETED, FAILED, etc.
    private String errorMsg;
    private Instant createdAt;
    private Instant processedAt;

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

    @Override
    public String toString() {
        return "PdfDocument{" +
                "id=" + id +
                ", fileName='" + fileName + '\'' +
                ", fileSize=" + fileSize +
                ", status='" + status + '\'' +
                '}';
    }
}
