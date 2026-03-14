package com.ingestion.service.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ingestion.entity.DocumentSummary;
import com.ingestion.entity.ExtractedPayment;
import com.ingestion.entity.PaymentExtractionResult;
import com.ingestion.entity.StatementSummary;
import com.ingestion.service.llm.LlmClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for payment extraction from PDF documents.
 * Coordinates LLM calls, JSON parsing, and validation.
 * Handles per-payment error handling (skips invalid payments without failing entire extraction).
 */
public class PdfPaymentExtractionService {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public PdfPaymentExtractionService(LlmClient llmClient) {
        this.llmClient = llmClient;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Extract payments from full document text.
     * 1. Build LLM prompt
     * 2. Call LLM with full document
     * 3. Parse JSON response
     * 4. Validate and filter payments
     * 5. Return PaymentExtractionResult
     *
     * @param documentText the full PDF text content
     * @param modelName the LLM model to use (e.g., "claude-3-5-sonnet")
     * @return PaymentExtractionResult with statements and payments
     * @throws Exception if LLM call or JSON parsing fails
     */
    public PaymentExtractionResult extractPayments(String documentText, String modelName) throws Exception {
        if (documentText == null || documentText.isEmpty()) {
            throw new IllegalArgumentException("Document text cannot be empty");
        }

        // 1. Build prompt
        String prompt = PdfPaymentPromptBuilder.buildExtractionPrompt(documentText);

        // 2. Call LLM
        String llmResponse = llmClient.complete(prompt, modelName);

        // 3. Parse JSON
        PaymentExtractionResult result = parseJsonResponse(llmResponse);

        // 4. Validate and filter payments
        validateAndCleanResult(result);

        return result;
    }

    /**
     * Extract payments using default model.
     */
    public PaymentExtractionResult extractPayments(String documentText) throws Exception {
        return extractPayments(documentText, null);  // Let LLM client use default
    }

    /**
     * Parse the LLM response JSON into PaymentExtractionResult.
     *
     * @param jsonResponse the JSON string from the LLM
     * @return parsed PaymentExtractionResult
     * @throws Exception if JSON is invalid
     */
    private PaymentExtractionResult parseJsonResponse(String jsonResponse) throws Exception {
        if (jsonResponse == null || jsonResponse.isEmpty()) {
            throw new Exception("Empty response from LLM");
        }

        try {
            // Try to extract JSON if LLM included extra text
            String cleanedJson = extractJsonBlock(jsonResponse);
            PaymentExtractionResult result = objectMapper.readValue(cleanedJson, PaymentExtractionResult.class);
            
            if (result == null) {
                throw new Exception("Parsed result is null");
            }
            
            return result;
        } catch (Exception e) {
            throw new Exception("Failed to parse LLM response JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Extract JSON block from response text.
     * LLM sometimes includes extra text before/after JSON, so we extract just the JSON.
     * Handles both:
     * 1. Markdown code blocks: ```json ... ```
     * 2. Raw JSON: { ... }
     */
    private String extractJsonBlock(String text) {
        if (text == null) {
            return null;
        }

        // First, try to extract from markdown code blocks
        int mdStart = text.indexOf("```");
        int mdEnd = text.lastIndexOf("```");
        
        if (mdStart >= 0 && mdEnd > mdStart + 3) {
            // Found markdown blocks, extract content between them
            String between = text.substring(mdStart + 3, mdEnd).trim();
            
            // Remove "json" or "JSON" after the opening ```
            if (between.startsWith("json")) {
                between = between.substring(4).trim();
            } else if (between.startsWith("JSON")) {
                between = between.substring(4).trim();
            }
            
            text = between;
        }

        // Now extract JSON block: find first { and last }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        
        if (start < 0 || end < 0 || start >= end) {
            return text;  // Assume entire text is JSON
        }
        
        return text.substring(start, end + 1);
    }

    /**
     * Validate and clean the extraction result in-place.
     * Removes invalid payments per statement.
     * Marks the result as "partially successful" if some payments are invalid.
     */
    private void validateAndCleanResult(PaymentExtractionResult result) {
        if (result == null) {
            return;
        }

        // Validate document summary
        if (result.getDocumentSummary() != null) {
            DocumentSummary summary = result.getDocumentSummary();
            if (summary.getStatementCount() == null) {
                summary.setStatementCount(0);
            }
        }

        // Validate statements and their payments
        if (result.getStatements() != null) {
            for (StatementSummary statement : result.getStatements()) {
                if (statement.getPayments() != null) {
                    // Filter out invalid payments
                    List<ExtractedPayment> validPayments = statement.getPayments().stream()
                        .filter(this::isValidPayment)
                        .collect(Collectors.toList());
                    
                    statement.setPayments(validPayments);
                }
            }
        }
    }

    /**
     * Validate a single extracted payment.
     * Returns true if payment meets minimum requirements, false otherwise.
     */
    private boolean isValidPayment(ExtractedPayment payment) {
        if (payment == null) {
            return false;
        }

        // Required fields
        if (payment.getCategory() == null || payment.getCategory().trim().isEmpty()) {
            return false;  // Category is required
        }

        if (payment.getTotalAmount() == null) {
            return false;  // Total amount is required
        }

        // Amount must be >= 0
        if (payment.getTotalAmount().compareTo(BigDecimal.ZERO) < 0) {
            return false;
        }

        // Confidence should be between 0 and 1 if provided
        if (payment.getConfidence() != null) {
            BigDecimal conf = payment.getConfidence();
            if (conf.compareTo(BigDecimal.ZERO) < 0 || conf.compareTo(BigDecimal.ONE) > 0) {
                // Clamp to valid range
                if (conf.compareTo(BigDecimal.ZERO) < 0) {
                    payment.setConfidence(BigDecimal.ZERO);
                } else if (conf.compareTo(BigDecimal.ONE) > 0) {
                    payment.setConfidence(BigDecimal.ONE);
                }
            }
        }

        return true;
    }

    /**
     * Get validation error message for a payment.
     * Used for debugging/logging why a payment was rejected.
     */
    public static String getValidationErrorMessage(ExtractedPayment payment) {
        if (payment == null) {
            return "Payment is null";
        }

        if (payment.getCategory() == null || payment.getCategory().trim().isEmpty()) {
            return "Category is required but missing";
        }

        if (payment.getTotalAmount() == null) {
            return "Total amount is required but missing";
        }

        if (payment.getTotalAmount().compareTo(BigDecimal.ZERO) < 0) {
            return "Total amount must be >= 0, got: " + payment.getTotalAmount();
        }

        return "Unknown validation error";
    }
}
