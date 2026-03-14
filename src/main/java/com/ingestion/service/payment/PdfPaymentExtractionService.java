package com.ingestion.service.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ingestion.entity.DocumentSummary;
import com.ingestion.entity.ExtractedPayment;
import com.ingestion.entity.PaymentExtractionResult;
import com.ingestion.entity.StatementSummary;
import com.ingestion.service.llm.LlmClient;
import com.ingestion.service.llm.OpenAiLlmClient;

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
     * Extract payments from PDF images using vision AI.
     * For scanned documents that cannot be text-extracted.
     * Requires OpenAI client with vision support (GPT-4o or similar).
     *
     * @param filePath path to the PDF file
     * @param modelName the vision-capable model to use (e.g., "gpt-4o")
     * @param maxPages maximum number of pages to process
     * @return PaymentExtractionResult with statements and payments
     * @throws Exception if image extraction or LLM call fails
     */
    public PaymentExtractionResult extractPaymentsFromScannedPdf(
            String filePath, 
            String modelName,
            int maxPages) throws Exception {
        
        if (filePath == null || filePath.isEmpty()) {
            throw new IllegalArgumentException("File path cannot be empty");
        }

        // Check if LLM client is OpenAI with vision support
        if (!(llmClient instanceof OpenAiLlmClient)) {
            throw new Exception("Vision-based extraction requires OpenAI client");
        }

        OpenAiLlmClient openAiClient = (OpenAiLlmClient) llmClient;

        // Extract images from PDF
        System.out.println("  [Vision] Extracting images from scanned PDF...");
        List<String> base64Images = PdfImageExtractor.extractPageImagesAsBase64(filePath, maxPages);
        
        if (base64Images.isEmpty()) {
            throw new Exception("Failed to extract images from PDF");
        }

        // Build vision prompt
        String prompt = buildVisionExtractionPrompt(base64Images.size());

        // Call LLM with images
        System.out.println("  [Vision] Calling LLM with " + base64Images.size() + " images...");
        String llmResponse = openAiClient.completeWithImages(prompt, base64Images, modelName);

        // Parse response
        PaymentExtractionResult result = parseJsonResponse(llmResponse);

        // Validate and filter
        validateAndCleanResult(result);

        // Mark as processed via vision
        if (result.getDocumentSummary() != null) {
            result.getDocumentSummary().setIsScannedDocument(true);
        }

        return result;
    }

    /**
     * Extract payments from scanned PDF using default vision model (GPT-4o).
     *
     * @param filePath path to the PDF file
     * @return PaymentExtractionResult
     * @throws Exception if extraction fails
     */
    public PaymentExtractionResult extractPaymentsFromScannedPdf(String filePath) throws Exception {
        return extractPaymentsFromScannedPdf(filePath, "gpt-4o", 20);
    }

    /**
     * Build prompt for vision-based extraction from PDF images.
     *
     * @param pageCount number of pages being analyzed
     * @return the prompt for the LLM
     */
    private String buildVisionExtractionPrompt(int pageCount) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("You are a financial document analysis expert. Analyze the provided PDF page images and extract payment information.\n\n");
        
        prompt.append("CRITICAL OUTPUT FORMAT:\n");
        prompt.append("1. Return ONLY a single JSON object. Nothing else.\n");
        prompt.append("2. NO markdown code blocks (no ```json or ```)\n");
        prompt.append("3. NO additional text before or after the JSON\n");
        prompt.append("4. NO explanation or commentary\n\n");
        
        prompt.append("EXTRACTION RULES:\n");
        prompt.append("1. Extract all payment records from the ").append(pageCount).append(" page(s) provided\n");
        prompt.append("2. For each payment, extract:\n");
        prompt.append("   - payment_date (YYYY-MM-DD format)\n");
        prompt.append("   - category (PRINCIPAL, INTEREST, ESCROW, TAX, INSURANCE, OTHER)\n");
        prompt.append("   - total_amount (numeric, >= 0, with 2 decimals)\n");
        prompt.append("   - Amount breakdown: principal, interest, escrow, tax, insurance (all optional)\n");
        prompt.append("   - payer/payee names\n");
        prompt.append("   - loan_number if visible\n");
        prompt.append("   - property address/city/state/zip if visible\n");
        prompt.append("   - source_page (page number where found)\n");
        prompt.append("3. Group payments by statement period if multiple statements exist\n");
        prompt.append("4. Include confidence scores (0.0-1.0) for each extraction\n");
        prompt.append("5. Mark unclear amounts with lower confidence\n");
        prompt.append("6. Do NOT fabricate data - only extract what is visible\n\n");
        
        prompt.append("RESPONSE SCHEMA:\n");
        prompt.append("{\n");
        prompt.append("  \"document_summary\": {\n");
        prompt.append("    \"bank_name\": \"string or null\",\n");
        prompt.append("    \"statement_count\": integer,\n");
        prompt.append("    \"is_scanned_document\": true\n");
        prompt.append("  },\n");
        prompt.append("  \"statements\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"statement_index\": 1,\n");
        prompt.append("      \"statement_period_start\": \"YYYY-MM-DD\",\n");
        prompt.append("      \"statement_period_end\": \"YYYY-MM-DD\",\n");
        prompt.append("      \"payments\": [\n");
        prompt.append("        {\n");
        prompt.append("          \"payment_date\": \"YYYY-MM-DD\",\n");
        prompt.append("          \"category\": \"string (required)\",\n");
        prompt.append("          \"total_amount\": number (required),\n");
        prompt.append("          \"principal_amount\": number or null,\n");
        prompt.append("          \"interest_amount\": number or null,\n");
        prompt.append("          \"escrow_amount\": number or null,\n");
        prompt.append("          \"tax_amount\": number or null,\n");
        prompt.append("          \"insurance_amount\": number or null,\n");
        prompt.append("          \"payer_name\": \"string or null\",\n");
        prompt.append("          \"payee_name\": \"string or null\",\n");
        prompt.append("          \"loan_number\": \"string or null\",\n");
        prompt.append("          \"property_address\": \"string or null\",\n");
        prompt.append("          \"property_city\": \"string or null\",\n");
        prompt.append("          \"property_state\": \"string or null\",\n");
        prompt.append("          \"property_zip\": \"string or null\",\n");
        prompt.append("          \"description\": \"string or null\",\n");
        prompt.append("          \"source_page\": integer or null,\n");
        prompt.append("          \"source_snippet\": \"string or null\",\n");
        prompt.append("          \"confidence\": number (0.0 to 1.0)\n");
        prompt.append("        }\n");
        prompt.append("      ]\n");
        prompt.append("    }\n");
        prompt.append("  ]\n");
        prompt.append("}\n\n");
        
        prompt.append("If no payments found, return: {\"statements\": [], \"document_summary\": {\"statement_count\": 0, \"is_scanned_document\": true}}\n\n");
        
        prompt.append("Return ONLY the JSON object, nothing else.\n");

        return prompt.toString();
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
