# Payment Extraction Feature - Implementation Summary

## Overview

This document describes the payment extraction feature added to the legal document processing pipeline. The feature extracts payment information from bank statement PDFs using LLM-based processing.

## Architecture

### Design Principles

1. **Full-Document Level Processing**: The entire PDF is sent to the LLM for analysis, not chunk-based extraction
2. **Multi-Statement Support**: Single PDFs may contain multiple bank statements (different months, different accounts)
3. **Per-Payment Validation**: Invalid individual payments are skipped without failing entire extraction
4. **Audit Trail**: Full LLM responses stored in JSONB columns for debugging and reconciliation
5. **Document-Level Tracking**: Lightweight extraction run records track LLM attempts per document

## Database Schema

### Tables

#### `payment_records`
Stores extracted payment information:
- **id** (BIGSERIAL): Primary key
- **pdf_document_id** (BIGINT FK): Reference to source PDF (not chunk-based)
- **statement_index** (INTEGER): Which statement in the PDF (1-based)
- **statement_period_start/end** (DATE): Statement period dates
- **payment_date** (DATE): When payment was made
- **category** (VARCHAR): Payment type (mortgage, utility, transfer, etc.) - REQUIRED
- **total_amount** (NUMERIC): Total payment - REQUIRED
- **principal_amount, interest_amount, escrow_amount, tax_amount, insurance_amount** (NUMERIC): Breakdowns
- **currency** (VARCHAR): Currency code (default USD)
- **payer_name, payee_name** (TEXT): Parties involved
- **property_address, property_city, property_state, property_zip** (TEXT): Property location
- **description** (TEXT): Payment description
- **source_page** (INTEGER): Which page of PDF
- **source_snippet** (TEXT): Exact quote from source
- **confidence** (NUMERIC 0.0-1.0): Confidence score
- **raw_llm_json** (JSONB): Full LLM response for this payment
- **created_at** (TIMESTAMP): Auto-set creation time

**Indexes**: 7 specialized indexes for common queries (pdf_document_id, payee_name, category, confidence, statement_index, created_at)

#### `pdf_payment_extraction_runs`
Tracks LLM extraction attempts per document:
- **id** (BIGSERIAL): Primary key
- **pdf_document_id** (BIGINT FK UNIQUE): One run per PDF
- **status** (VARCHAR): PENDING, PROCESSING, COMPLETED, FAILED
- **model_name** (VARCHAR): LLM model used (e.g., "claude-3-5-sonnet")
- **prompt_version** (VARCHAR): Version of extraction prompt
- **statement_count** (INTEGER): Number of statements found
- **payment_count** (INTEGER): Number of payments extracted
- **is_scanned** (BOOLEAN): Scanned/OCR'd PDF flag
- **error_msg** (TEXT): Failure reason if status=FAILED
- **raw_llm_response** (JSONB): Full LLM response for debugging
- **created_at, completed_at** (TIMESTAMP): Timing information

## Java Code Structure

### Domain Models (`com.ingestion.entity`)

1. **PdfDocument**: Metadata for a PDF file
2. **PaymentRecord**: ORM entity mapping payment_records table
3. **PdfPaymentExtractionRun**: ORM entity mapping pdf_payment_extraction_runs table
4. **ExtractedPayment**: DTO for individual payment from LLM JSON
5. **StatementSummary**: DTO for statement-level grouping (contains List<ExtractedPayment>)
6. **DocumentSummary**: DTO for document-level metadata (bank_name, statement_count, is_scanned)
7. **PaymentExtractionResult**: DTO for complete LLM response (document_summary + statements[])

### Repositories (`com.ingestion.repository`)

Raw JDBC repositories (following project pattern):

1. **PdfDocumentRepository**
   - `findById()`, `findBySha256()`, `findByStatus()`
   - `create()`, `update()`
   - `findAll()`

2. **PaymentRecordRepository**
   - `insert()`, `batchInsert()` (returns batch results)
   - `findByPdfDocumentId()`, `findByPdfDocumentIdAndStatementIndex()`
   - `findByCategory()`, `countByPdfDocumentId()`

3. **PdfPaymentExtractionRunRepository**
   - `create()`, `findById()`, `findByPdfDocumentId()`, `findByStatus()`
   - `update()`

### LLM Client (`com.ingestion.service.llm`)

1. **LlmClient** (interface)
   - `complete(String prompt)` - Send prompt and get response
   - `complete(String prompt, String model)` - Same with model selection

2. **AnthropicLlmClient** (implementation)
   - Uses Anthropic Claude API
   - Default model: claude-3-5-sonnet-20241022
   - Handles authentication, request/response formatting
   - Max tokens: 4096

### Business Services (`com.ingestion.service.payment`)

1. **PdfTextLoader**
   - `loadTextFromPdf(filePath)` - Extract text from PDF (supports both digital and scanned)
   - `loadTextFromPdfWithPageMarkers()` - Same with page break markers
   - `getPageCount()` - Get number of pages

2. **PdfPaymentPromptBuilder**
   - `buildExtractionPrompt(documentText)` - Creates strict JSON-only extraction prompt
   - `buildValidationPrompt(documentText, json)` - Creates validation/cleaning prompt
   - `getPromptVersion()` - Returns current prompt version

3. **PdfPaymentExtractionService**
   - `extractPayments(documentText, modelName)` - Main extraction logic
   - `parseJsonResponse()` - Parse LLM JSON into PaymentExtractionResult
   - Validates each payment (skips invalid ones)
   - Flails validation: category (required), total_amount (required), amounts >= 0, confidence in [0, 1]

4. **PaymentExtractionPipeline** (Main Orchestrator)
   - `processPdfDocument(pdfDocumentId)` - Full pipeline
   
   Pipeline steps:
   1. Load PDF document metadata
   2. Create extraction run record (PENDING)
   3. Mark run as PROCESSING
   4. Load full document text from PDF
   5. Build LLM prompt
   6. Call LLM API
   7. Parse JSON response
   8. Validate and filter payments
   9. Flatten statements to PaymentRecord entities
   10. Batch insert into database
   11. Mark extraction run as COMPLETED with final counts
   
   On failure:
   - Mark run as FAILED with error message
   - Return result with success=false

## LLM JSON Format

### Request
- Full document text sent in single prompt
- Strict JSON-only response requested
- Examples and schema provided in prompt

### Response
```json
{
  "document_summary": {
    "bank_name": "string or null",
    "statement_count": integer,
    "is_scanned_document": boolean
  },
  "statements": [
    {
      "statement_index": integer (1, 2, 3...),
      "statement_period_start": "YYYY-MM-DD",
      "statement_period_end": "YYYY-MM-DD",
      "payments": [
        {
          "payment_date": "YYYY-MM-DD",
          "category": "string (required)",
          "total_amount": number (required),
          "principal_amount": number or null,
          "interest_amount": number or null,
          "escrow_amount": number or null,
          "tax_amount": number or null,
          "insurance_amount": number or null,
          "payer_name": "string or null",
          "payee_name": "string or null",
          "property_address": "string or null",
          "property_city": "string or null",
          "property_state": "string or null",
          "property_zip": "string or null",
          "description": "string or null",
          "source_page": integer or null,
          "source_snippet": "string or null",
          "confidence": number (0.0 to 1.0)
        }
      ]
    }
  ]
}
```

### Multiple Statements Example
PDFs may contain multiple statements (e.g., 2 months or 2 accounts):
```json
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
      "payments": [...]
    },
    {
      "statement_index": 2,
      "statement_period_start": "2024-02-01",
      "statement_period_end": "2024-02-29",
      "payments": [...]
    }
  ]
}
```

## Validation Rules

### Per-Payment Validation
- **category** (required): Must not be null or empty
- **total_amount** (required): Must not be null
- **Amounts**: All amounts must be >= 0
- **Confidence**: If provided, must be between 0.0 and 1.0 (clamped to valid range)
- Other fields: Optional, may be null

### Failure Handling
- Invalid individual payments are **skipped** without failing entire extraction
- Extraction run marked as COMPLETED even if some payments are invalid
- Full results returned to caller

## Key Constraints

✅ **DO**
- Extract full document at once (not chunk-based)
- Support multiple statements per PDF
- Store full LLM response for auditing
- Skip invalid payments without failing
- Track extraction runs per PDF

❌ **DO NOT**
- Reference pdf_chunk_id anywhere
- Depend on chunk-level processing
- Use UUID for primary keys (use BIGSERIAL)
- Redesign pdf_documents table
- Implement deduplication at this stage

## Usage Example

```java
// Setup
Connection dbConn = getConnectionFromPool();
LlmClient llmClient = new AnthropicLlmClient(apiKey);
PdfPaymentExtractionService extractionService = 
    new PdfPaymentExtractionService(llmClient);

PaymentExtractionPipeline pipeline = 
    new PaymentExtractionPipeline(dbConn, extractionService);

// Process a PDF
Long pdfDocumentId = 123L;
try {
    PaymentExtractionPipeline.PaymentExtractionPipelineResult result = 
        pipeline.processPdfDocument(pdfDocumentId);
    
    System.out.println("Payments extracted: " + result.getPaymentCount());
    System.out.println("Inserted to DB: " + result.getInsertedCount());
} catch (Exception e) {
    System.err.println("Extraction failed: " + e.getMessage());
}
```

## Future Enhancements

1. **Prompt Tuning**: Fine-tune LLM prompt for specific bank formats
2. **Scanned PDF Handling**: Integrate OCR for better scanned PDF support
3. **Deduplication**: Add logic to detect duplicate payments across multiple PDFs
4. **Reconciliation**: Compare extracted payments against bank transaction data
5. **Retry Logic**: Implement exponential backoff for failed LLM calls
6. **Pipeline Caching**: Cache PDF text and LLM responses for quick re-runs
7. **Batch Processing**: Queue multiple PDFs for async processing
8. **Model Switching**: Allow per-PDF or per-category model selection
9. **Confidence Threshold**: Filter/flag low-confidence extractions
10. **Normalized Tables**: Create reference tables for categories, banks, properties

## Files Created

Domain Models:
- `src/main/java/com/ingestion/entity/PdfDocument.java`
- `src/main/java/com/ingestion/entity/PaymentRecord.java`
- `src/main/java/com/ingestion/entity/PdfPaymentExtractionRun.java`
- `src/main/java/com/ingestion/entity/ExtractedPayment.java`
- `src/main/java/com/ingestion/entity/StatementSummary.java`
- `src/main/java/com/ingestion/entity/DocumentSummary.java`
- `src/main/java/com/ingestion/entity/PaymentExtractionResult.java`

Repositories:
- `src/main/java/com/ingestion/repository/PdfDocumentRepository.java`
- `src/main/java/com/ingestion/repository/PaymentRecordRepository.java`
- `src/main/java/com/ingestion/repository/PdfPaymentExtractionRunRepository.java`

LLM Integration:
- `src/main/java/com/ingestion/service/llm/LlmClient.java`
- `src/main/java/com/ingestion/service/llm/AnthropicLlmClient.java`

Services:
- `src/main/java/com/ingestion/service/payment/PdfTextLoader.java`
- `src/main/java/com/ingestion/service/payment/PdfPaymentPromptBuilder.java`
- `src/main/java/com/ingestion/service/payment/PdfPaymentExtractionService.java`
- `src/main/java/com/ingestion/service/payment/PaymentExtractionPipeline.java`

Database Schema:
- `ddl_payment_extraction.sql` (already provided in workspace)

## Notes

- All code follows raw JDBC pattern consistent with project style
- BigDecimal used for monetary amounts
- LocalDate used for dates
- Instant used for timestamps
- ObjectMapper used for JSON serialization/deserialization
- Production-ready error handling and logging capability
- No external frameworks beyond Jackson (JSON) and PDFBox (PDF reading)
