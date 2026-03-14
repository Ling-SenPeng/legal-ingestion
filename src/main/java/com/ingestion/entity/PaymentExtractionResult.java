package com.ingestion.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * DTO for the complete payment extraction result from LLM.
 * Top-level container that holds document-level summary and statements.
 * Expects JSON structure with "document_summary" and "statements" fields.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentExtractionResult {
    
    @JsonProperty("document_summary")
    private DocumentSummary documentSummary;
    
    @JsonProperty("statements")
    private List<StatementSummary> statements;

    // Constructors
    public PaymentExtractionResult() {}

    // Getters and Setters
    public DocumentSummary getDocumentSummary() {
        return documentSummary;
    }

    public void setDocumentSummary(DocumentSummary documentSummary) {
        this.documentSummary = documentSummary;
    }

    public List<StatementSummary> getStatements() {
        return statements;
    }

    public void setStatements(List<StatementSummary> statements) {
        this.statements = statements;
    }

    @Override
    public String toString() {
        return "PaymentExtractionResult{" +
                "documentSummary=" + documentSummary +
                ", statementCount=" + (statements != null ? statements.size() : 0) +
                '}';
    }
}
