package com.ingestion.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for document-level summary from LLM response.
 * Extracted metadata about the PDF document itself.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DocumentSummary {
    
    @JsonProperty("bank_name")
    private String bankName;
    
    @JsonProperty("statement_count")
    private Integer statementCount;
    
    @JsonProperty("is_scanned_document")
    private Boolean isScannedDocument;

    // Constructors
    public DocumentSummary() {}

    // Getters and Setters
    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public Integer getStatementCount() {
        return statementCount;
    }

    public void setStatementCount(Integer statementCount) {
        this.statementCount = statementCount;
    }

    public Boolean getIsScannedDocument() {
        return isScannedDocument;
    }

    public void setIsScannedDocument(Boolean isScannedDocument) {
        this.isScannedDocument = isScannedDocument;
    }

    @Override
    public String toString() {
        return "DocumentSummary{" +
                "bankName='" + bankName + '\'' +
                ", statementCount=" + statementCount +
                ", isScannedDocument=" + isScannedDocument +
                '}';
    }
}
