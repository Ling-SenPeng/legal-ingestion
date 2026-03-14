package com.ingestion.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Domain model for an extracted payment record.
 * Represents a single payment extracted from a PDF document via LLM processing.
 */
public class PaymentRecord {
    private Long id;
    private Long pdfDocumentId;
    
    // Statement context (for PDFs with multiple statements)
    private Integer statementIndex;
    private LocalDate statementPeriodStart;
    private LocalDate statementPeriodEnd;
    
    // Payment date
    private LocalDate paymentDate;
    
    // Payment category
    private String category;  // Required
    
    // Amount breakdown
    private BigDecimal totalAmount;  // Required
    private BigDecimal principalAmount;
    private BigDecimal interestAmount;
    private BigDecimal escrowAmount;
    private BigDecimal taxAmount;
    private BigDecimal insuranceAmount;
    
    // Currency
    private String currency;
    
    // Party information
    private String payerName;
    private String payeeName;
    
    // Property information
    private String propertyAddress;
    private String propertyCity;
    private String propertyState;
    private String propertyZip;
    
    // Payment description
    private String description;
    
    // Provenance
    private Integer sourcePage;
    private String sourceSnippet;
    private BigDecimal confidence;  // 0.0 to 1.0
    
    // Raw LLM response for auditing
    private JsonNode rawLlmJson;
    
    // Timestamp
    private Instant createdAt;

    // Constructors
    public PaymentRecord() {}

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

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(LocalDate paymentDate) {
        this.paymentDate = paymentDate;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public BigDecimal getPrincipalAmount() {
        return principalAmount;
    }

    public void setPrincipalAmount(BigDecimal principalAmount) {
        this.principalAmount = principalAmount;
    }

    public BigDecimal getInterestAmount() {
        return interestAmount;
    }

    public void setInterestAmount(BigDecimal interestAmount) {
        this.interestAmount = interestAmount;
    }

    public BigDecimal getEscrowAmount() {
        return escrowAmount;
    }

    public void setEscrowAmount(BigDecimal escrowAmount) {
        this.escrowAmount = escrowAmount;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
    }

    public BigDecimal getInsuranceAmount() {
        return insuranceAmount;
    }

    public void setInsuranceAmount(BigDecimal insuranceAmount) {
        this.insuranceAmount = insuranceAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getPayerName() {
        return payerName;
    }

    public void setPayerName(String payerName) {
        this.payerName = payerName;
    }

    public String getPayeeName() {
        return payeeName;
    }

    public void setPayeeName(String payeeName) {
        this.payeeName = payeeName;
    }

    public String getPropertyAddress() {
        return propertyAddress;
    }

    public void setPropertyAddress(String propertyAddress) {
        this.propertyAddress = propertyAddress;
    }

    public String getPropertyCity() {
        return propertyCity;
    }

    public void setPropertyCity(String propertyCity) {
        this.propertyCity = propertyCity;
    }

    public String getPropertyState() {
        return propertyState;
    }

    public void setPropertyState(String propertyState) {
        this.propertyState = propertyState;
    }

    public String getPropertyZip() {
        return propertyZip;
    }

    public void setPropertyZip(String propertyZip) {
        this.propertyZip = propertyZip;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getSourcePage() {
        return sourcePage;
    }

    public void setSourcePage(Integer sourcePage) {
        this.sourcePage = sourcePage;
    }

    public String getSourceSnippet() {
        return sourceSnippet;
    }

    public void setSourceSnippet(String sourceSnippet) {
        this.sourceSnippet = sourceSnippet;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public JsonNode getRawLlmJson() {
        return rawLlmJson;
    }

    public void setRawLlmJson(JsonNode rawLlmJson) {
        this.rawLlmJson = rawLlmJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "PaymentRecord{" +
                "id=" + id +
                ", pdfDocumentId=" + pdfDocumentId +
                ", statementIndex=" + statementIndex +
                ", paymentDate=" + paymentDate +
                ", category='" + category + '\'' +
                ", totalAmount=" + totalAmount +
                ", currency='" + currency + '\'' +
                ", payeeName='" + payeeName + '\'' +
                ", confidence=" + confidence +
                '}';
    }
}
