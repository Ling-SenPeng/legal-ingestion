package com.ingestion.service.payment;

/**
 * Service for building LLM prompts for payment extraction from PDFs.
 * Constructs strict JSON-only prompts with schema, examples, and instructions.
 * Designed for full-document level analysis, not chunk-based.
 */
public class PdfPaymentPromptBuilder {

    private static final String PROMPT_VERSION = "1.0";

    /**
     * Build a complete LLM prompt for payment extraction.
     * Returns a prompt that requests strict JSON-only output with expected schema.
     * Optimized for OpenAI API with clear instructions to avoid markdown formatting.
     *
     * @param documentText the full text content of the PDF
     * @return the complete prompt to send to the LLM
     */
    public static String buildExtractionPrompt(String documentText) {
        if (documentText == null || documentText.isEmpty()) {
            throw new IllegalArgumentException("Document text cannot be null or empty");
        }

        String prompt = """
You are a financial document analysis expert specializing in bank statements and payment extraction.

Your task is to extract payment information from the provided bank statement document(s).

CRITICAL OUTPUT FORMAT REQUIREMENTS (for OpenAI):
1. Return ONLY a single JSON object. Nothing else.
2. NO markdown code blocks (no ```json, no ``` anywhere)
3. NO additional text before or after the JSON
4. NO explanation or commentary
5. If you cannot parse valid JSON, return an empty statements array
6. The JSON must be valid and parseable
7. Do NOT include BOM or special characters at the start

IMPORTANT CONSTRAINTS:
1. Return ONLY valid JSON. No other text.
2. If the document contains multiple statements (e.g., statements for different months or accounts), 
   return all statements in the "statements" array, each with a unique statement_index (1, 2, 3, etc.).
3. Do NOT make up data. Only extract what is explicitly present in the document.
4. For amounts, use numeric values (e.g., 1500.00, not "1500 dollars").
5. For dates, use YYYY-MM-DD format.
6. All numeric amounts should have exactly 2 decimal places.
7. Each payment must have a category (required) and total_amount (required). Other fields may be null.
8. Confidence should be a decimal between 0.0 and 1.0, indicating your confidence in the extraction (0.95 = very confident, 0.60 = somewhat confident).
9. If the document appears to be scanned/OCR'd (degraded text, manual annotations), set is_scanned_document to true.
10. Include source_page if you can determine which page the payment appears on.
11. Extract loan_number if visible on the statement.

RESPONSE SCHEMA (JSON):
{
  "document_summary": {
    "bank_name": "string or null",
    "statement_count": integer (number of distinct statements found),
    "is_scanned_document": boolean (true if scanned/OCR'd, false if digital)
  },
  "statements": [
    {
      "statement_index": integer (1, 2, 3, ... for multiple statements),
      "statement_period_start": "YYYY-MM-DD",
      "statement_period_end": "YYYY-MM-DD",
      "payments": [
        {
          "payment_date": "YYYY-MM-DD",
          "category": "string (required - e.g., mortgage, utility, transfer, check deposit, etc.)",
          "total_amount": number (required - the total payment amount, must be >= 0),
          "principal_amount": number or null,
          "interest_amount": number or null,
          "escrow_amount": number or null,
          "tax_amount": number or null,
          "insurance_amount": number or null,
          "payer_name": "string or null",
          "payee_name": "string or null",
          "loan_number": "string or null",
          "property_address": "string or null",
          "property_city": "string or null",
          "property_state": "string or null",
          "property_zip": "string or null",
          "description": "string or null (e.g., 'Monthly mortgage payment', 'Utility bill payment')",
          "source_page": integer or null (which page this appeared on)",
          "source_snippet": "string or null (a short quote from the source)",
          "confidence": number (0.0 to 1.0)
        }
      ]
    }
  ]
}

EXAMPLE RESPONSE for a simple single-statement PDF:
{
  "document_summary": {
    "bank_name": "First National Bank",
    "statement_count": 1,
    "is_scanned_document": false
  },
  "statements": [
    {
      "statement_index": 1,
      "statement_period_start": "2024-01-01",
      "statement_period_end": "2024-01-31",
      "payments": [
        {
          "payment_date": "2024-01-15",
          "category": "mortgage",
          "total_amount": 2500.00,
          "principal_amount": 1500.00,
          "interest_amount": 800.00,
          "escrow_amount": 200.00,
          "payer_name": "John Doe",
          "payee_name": "Mortgage Servicer Inc",
          "loan_number": "123456789",
          "property_address": "123 Main Street",
          "property_city": "Springfield",
          "property_state": "IL",
          "property_zip": "62701",
          "description": "Monthly mortgage payment",
          "source_page": 1,
          "source_snippet": "01/15/2024 - Monthly Mortgage Payment - $2500.00",
          "confidence": 0.98
        }
      ]
    }
  ]
}

MULTIPLE STATEMENTS EXAMPLE (for a statement with 2 months):
{
  "document_summary": {
    "bank_name": "Chase Bank",
    "statement_count": 2,
    "is_scanned_document": false
  },
  "statements": [
    {
      "statement_index": 1,
      "statement_period_start": "2024-01-01",
      "statement_period_end": "2024-01-31",
      "payments": [ ... ]
    },
    {
      "statement_index": 2,
      "statement_period_start": "2024-02-01",
      "statement_period_end": "2024-02-29",
      "payments": [ ... ]
    }
  ]
}

NOW EXTRACT ALL PAYMENTS FROM THIS DOCUMENT.
Return ONLY the JSON response, no explanations:

===== BEGIN DOCUMENT =====
""" + documentText + """
===== END DOCUMENT =====

Return valid JSON only:""";

        return prompt;
    }

    /**
     * Build a prompt for validating and cleaning previously extracted JSON.
     * Useful for fixing minor formatting issues without re-processing the entire document.
     *
     * @param documentText the full text content of the PDF
     * @param previousJson the previously extracted JSON that needs fixing
     * @return a prompt for re-processing/validating
     */
    public static String buildValidationPrompt(String documentText, String previousJson) {
        String prompt = """
You are a financial document analysis expert. Review the following previously extracted payment data.

Your task: Review the extracted data against the source document and report any issues.
If the extracted JSON is invalid or has missing required fields, provide a corrected version.

Return valid JSON only, matching the expected schema.

Original extraction:
""" + previousJson + """

Source document:
===== BEGIN DOCUMENT =====
""" + documentText + """
===== END DOCUMENT =====

Return corrected JSON or valid JSON if original is acceptable:""";

        return prompt;
    }

    /**
     * Get the current prompt version.
     * Useful for tracking which prompt template was used for extraction.
     */
    public static String getPromptVersion() {
        return PROMPT_VERSION;
    }
}
