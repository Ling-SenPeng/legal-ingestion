package com.ingestion.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for an extracted payment from LLM response.
 * Represents a single payment in the JSON response before mapping to PaymentRecord.
 */
public class ExtractedPayment {
    
    @JsonProperty("payment_date")
    private LocalDate paymentDate;
    
    @JsonProperty("category")
    private String category;
    
    @JsonProperty("total_amount")
    private BigDecimal totalAmount;
    
    @JsonProperty("principal_amount")
    private BigDecimal principalAmount;
    
    @JsonProperty("interest_amount")
    private BigDecimal interestAmount;
    
    @JsonProperty("escrow_amount")
    private BigDecimal escrowAmount;
    
    @JsonProperty("tax_amount")
    private BigDecimal taxAmount;
    
    @JsonProperty("insurance_amount")
    private BigDecimal insuranceAmount;
    
    @JsonProperty("payer_name")
    private String payerName;
    
    @JsonProperty("payee_name")
    private String payeeName;
    
    @JsonProperty("property_address")
    private String propertyAddress;
    
    @JsonProperty("property_city")
    private String propertyCity;
    
    @JsonProperty("property_state")
    private String propertyState;
    
    @JsonProperty("property_zip")
    private String propertyZip;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("source_page")
    private Integer sourcePage;
    
    @JsonProperty("source_snippet")
    private String sourceSnippet;
    
    @JsonProperty("confidence")
    private BigDecimal confidence;
    
    @JsonProperty("loan_number")
    private String loanNumber;

    // Constructors
    public ExtractedPayment() {}

    // Getters and Setters
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

    public String getLoanNumber() {
        return loanNumber;
    }

    public void setLoanNumber(String loanNumber) {
        this.loanNumber = loanNumber;
    }

    @Override
    public String toString() {
        return "ExtractedPayment{" +
                "paymentDate=" + paymentDate +
                ", category='" + category + '\'' +
                ", totalAmount=" + totalAmount +
                ", payeeName='" + payeeName + '\'' +
                ", confidence=" + confidence +
                '}';
    }
}
