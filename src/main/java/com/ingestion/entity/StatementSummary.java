package com.ingestion.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO for statement-level summary from LLM response.
 * Represents one statement within a PDF (PDFs may contain multiple statements).
 */
public class StatementSummary {
    
    @JsonProperty("statement_index")
    private Integer statementIndex;
    
    @JsonProperty("statement_period_start")
    private LocalDate statementPeriodStart;
    
    @JsonProperty("statement_period_end")
    private LocalDate statementPeriodEnd;
    
    @JsonProperty("payments")
    private List<ExtractedPayment> payments;

    // Constructors
    public StatementSummary() {}

    public StatementSummary(Integer statementIndex) {
        this.statementIndex = statementIndex;
    }

    // Getters and Setters
    public Integer getStatementIndex() {
        return statementIndex;
    }

    public void setStatementIndex(Integer statementIndex) {
        this.statementIndex = statementIndex;
    }

    public LocalDate getStatementPeriodStart() {
        return statementPeriodStart;
    }

    public void setStatementPeriodStart(LocalDate statementPeriodStart) {
        this.statementPeriodStart = statementPeriodStart;
    }

    public LocalDate getStatementPeriodEnd() {
        return statementPeriodEnd;
    }

    public void setStatementPeriodEnd(LocalDate statementPeriodEnd) {
        this.statementPeriodEnd = statementPeriodEnd;
    }

    public List<ExtractedPayment> getPayments() {
        return payments;
    }

    public void setPayments(List<ExtractedPayment> payments) {
        this.payments = payments;
    }

    @Override
    public String toString() {
        return "StatementSummary{" +
                "statementIndex=" + statementIndex +
                ", paymentCount=" + (payments != null ? payments.size() : 0) +
                ", periodStart=" + statementPeriodStart +
                ", periodEnd=" + statementPeriodEnd +
                '}';
    }
}
