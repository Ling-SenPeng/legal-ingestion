# OpenAI Payment Extraction Integration Guide

## Overview

This document describes how to use the OpenAI-based payment extraction pipeline for bank statement PDFs. The pipeline is designed for full-document level processing (not chunk-based) and supports PDFs with multiple statements.

## Configuration

### Environment Variables

The system reads configuration from environment variables via the `EnvLoader` class. Variables can be set in:
1. `.env` file (local development)
2. System environment variables (production)

System environment variables take precedence over `.env` file values.

### Required Configuration

**OPENAI_API_KEY** (required)
- Your OpenAI API key
- Get from: https://platform.openai.com/api-keys
- Format: `sk-...`
- Example: `OPENAI_API_KEY=sk-proj-abc123...`

### Optional Configuration

**OPENAI_MODEL** (optional)
- OpenAI model to use for payment extraction
- Default: `gpt-4o-mini` (fast and cost-effective)
- Recommended models:
  - `gpt-4o-mini`: Fast, good for most cases (default)
  - `gpt-4`: More accurate/complex extractions
  - `gpt-4-turbo-preview`: Balance of speed and accuracy

Example:
```
OPENAI_MODEL=gpt-4o-mini
```

## Quick Start

### 1. Setup Environment

Create a `.env` file in the project root:
```bash
cp .env.example .env
# Edit .env and add your OpenAI API key
```

### 2. Basic Usage

```java
import com.ingestion.service.payment.PaymentExtractionPipeline;
import java.sql.Connection;

// Get your database connection
Connection dbConn = getConnectionFromPool(); // or DataSource.getConnection()

try {
    // Create pipeline with default OpenAI configuration
    PaymentExtractionPipeline pipeline = 
        PaymentExtractionPipeline.withDefaultOpenAi(dbConn);
    
    // Process a PDF (pdf_document_id must exist in pdf_documents table)
    Long pdfDocumentId = 123L;
    var result = pipeline.processPdfDocument(pdfDocumentId);
    
    if (result.isSuccess()) {
        System.out.println("Success!");
        System.out.println("Payments extracted: " + result.getPaymentCount());
        System.out.println("Inserted to DB: " + result.getInsertedCount());
    } else {
        System.err.println("Failed: " + result.getErrorMessage());
    }
} catch (Exception e) {
    System.err.println("Pipeline setup failed: " + e.getMessage());
}
```

### 3. Using Non-Default Model

```java
// Use a specific model instead of the configured default
PaymentExtractionPipeline pipeline = 
    PaymentExtractionPipeline.withOpenAi(dbConn, "gpt-4");

// Continue with processPdfDocument() as normal
```

## Architecture

### Classes and Responsibilities

#### OpenAiConfig
Reads and manages OpenAI configuration from environment.

Methods:
- `getApiKey()` - Get OPENAI_API_KEY (throws if not set)
- `getModel()` - Get OPENAI_MODEL or default
- `createLlmClient()` - Create configured OpenAI client

#### OpenAiLlmClient (implements LlmClient)
Direct OpenAI API integration.

Methods:
- `complete(String prompt)` - Send prompt, get response
- `complete(String prompt, String model)` - Send with model override
- `getModelName()` - Get current model name

Constructor options:
```java
// From environment (preferred)
LlmClient client = new OpenAiLlmClient();

// Explicit API key and model
LlmClient client = new OpenAiLlmClient(apiKey, "gpt-4o-mini");
```

#### PaymentExtractionPipeline
Main orchestrator that coordinates the full payment extraction flow.

Static factory methods:
- `withDefaultOpenAi(Connection dbConn)` - Create with default config
- `withOpenAi(Connection dbConn, String model)` - Create with model override

Instance methods:
- `processPdfDocument(Long pdfDocumentId)` - Run full extraction pipeline

Return value: `PaymentExtractionPipelineResult`

#### PdfPaymentExtractionService
Coordinates LLM calls, JSON parsing, and validation.

Methods:
- `extractPayments(String documentText)` - Extract from PDF text
- `extractPayments(String documentText, String modelName)` - With model override

Return value: `PaymentExtractionResult`

#### PdfPaymentPromptBuilder
Builds the extraction prompt for OpenAI.

Methods:
- `buildExtractionPrompt(String documentText)` - Main extraction prompt
- `buildValidationPrompt(String documentText, String previousJson)` - Validation/cleanup prompt

#### PdfTextLoader
Loads text content from PDF files.

Methods:
- `loadTextFromPdf(String filePath)` - Extract text from PDF
- `getPageCount(String filePath)` - Get number of pages

### Pipeline Execution Flow

```
PaymentExtractionPipeline.processPdfDocument(pdfDocumentId)
  ↓
1. Load PdfDocument from database
2. Create PdfPaymentExtractionRun (status=PENDING)
3. Update run to PROCESSING
4. Load full PDF text file
5. Build OpenAI prompt via PdfPaymentPromptBuilder
6. Call OpenAI API via PdfPaymentExtractionService
7. Parse JSON response into PaymentExtractionResult
8. Validate each payment (skip invalid ones)
9. Flatten statements → PaymentRecord entities
10. Batch insert PaymentRecords to database
11. Update run to COMPLETED with counts
    ↓
Return PaymentExtractionPipelineResult {
    success: bool
    paymentCount: int
    insertedCount: int
    extractionRun: PdfPaymentExtractionRun
    errorMessage: string (if failed)
}
```

## JSON Response Format

### Request
- **Input**: Full PDF document text (multiple pages, possibly multiple statements)
- **Model**: OpenAI (gpt-4o-mini, gpt-4, etc.)
- **Temperature**: 0 (deterministic for extraction)
- **Max Tokens**: 4096

### Expected Response

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
      "payments": [
        {
          "payment_date": "2024-01-15",
          "category": "mortgage",
          "total_amount": 2500.00,
          "principal_amount": 1500.00,
          "interest_amount": 800.00,
          "escrow_amount": 200.00,
          "tax_amount": null,
          "insurance_amount": null,
          "payer_name": "John Doe",
          "payee_name": "Mortgage Servicer Inc",
          "property_address": "123 Main Street",
          "property_city": "Springfield",
          "property_state": "IL",
          "property_zip": "62701",
          "description": "Monthly mortgage payment",
          "source_page": 1,
          "source_snippet": "01/15/2024 - Monthly Mortgage Payment - $2,500.00",
          "confidence": 0.98
        }
      ]
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

## Error Handling

### Configuration Errors

**Missing OPENAI_API_KEY**
```
Exception: OPENAI_API_KEY not configured. Set OPENAI_API_KEY in .env file or as environment variable.
```
Solution: Set OPENAI_API_KEY in `.env` or as system environment variable

### OpenAI API Errors

**401 Unauthorized**
- API key is invalid or expired
- Solution: Verify API key at https://platform.openai.com/api-keys

**429 Rate Limited**
- Too many requests to OpenAI
- Solution: Implement exponential backoff retry logic (out of scope for this version)

**500+ Server Errors**
- OpenAI API is experiencing issues
- Solution: Retry after a delay

### JSON Parsing Errors

**Invalid JSON from OpenAI**
- LLM returned malformed JSON
- Solution: Invalid payments are skipped; extraction continues with valid payments
- Fallback: `PaymentExtractionResult` with empty statements array

**Missing Required Fields**
- `category` or `total_amount` missing from payment
- Solution: Payment is skipped; extraction continues

## Validation Rules

Each extracted payment must satisfy:

**Required Fields**
- `category`: Non-empty string
- `total_amount`: Numeric value >= 0

**Optional Fields**
- All other fields (payment_date, parties, property info, etc.)

**Constraint Validation**
- `totalAmount >= 0`
- `principalAmount >= 0` (if present)
- `interestAmount >= 0` (if present)
- `confidence`: 0.0 to 1.0 (auto-clamped)

**Failure Handling**
- Invalid payments are **skipped** without failing entire extraction
- Extraction continues with remaining valid payments
- Extraction run marked as COMPLETED (not FAILED)

## Database Tables

### payment_records
Stores extracted payments from bank statement PDFs.

Key columns:
- `id` (BIGSERIAL): Primary key
- `pdf_document_id` (BIGINT FK): Reference to source PDF (NOT chunk-based)
- `statement_index` (INTEGER): Which statement in PDF (1-based)
- `payment_date`, `category`, `total_amount`: Core payment info
- `principal_amount`, `interest_amount`, `escrow_amount`, etc.: Amount breakdowns
- `payer_name`, `payee_name`: Parties
- `property_address`, `property_city`, `property_state`, `property_zip`: Location
- `source_page`, `source_snippet`, `confidence`: Provenance
- `raw_llm_json` (JSONB): Full LLM response for audit
- `created_at`: Auto-timestamp

Indexes: 7 specialized indexes for query performance

### pdf_payment_extraction_runs
Tracks LLM extraction attempts per PDF.

Key columns:
- `id` (BIGSERIAL): Primary key
- `pdf_document_id` (BIGINT FK UNIQUE): One run per PDF
- `status` (VARCHAR): PENDING, PROCESSING, COMPLETED, FAILED
- `model_name`: Model used (e.g., "gpt-4o-mini")
- `statement_count`, `payment_count`: Result metrics
- `is_scanned`: Boolean flag for scanned PDFs
- `error_msg`: Failure reason
- `raw_llm_response` (JSONB): Full OpenAI response
- `created_at`, `completed_at`: Timing

## Performance Considerations

### OpenAI API Costs
- **gpt-4o-mini**: ~$0.00015 per input token, $0.0006 per output token
- **gpt-4**: ~10x more expensive than gpt-4o-mini
- Average bank statement: 2,000-5,000 tokens input, 1,000-2,000 tokens output

### Timeout Settings
- Default: 120 seconds
- Adjust in OpenAiLlmClient if needed for large PDFs

### Database Insertion
- Batch insert via `PaymentRecordRepository.batchInsert()`
- Typical batch size: hundreds to thousands of records

## Testing

### Unit Testing (Mocked LLM)
```java
// Create mock LLM client
LlmClient mockClient = new LlmClient() {
    @Override
    public String complete(String prompt) {
        return "{\"document_summary\": {...}, \"statements\": [...]}";
    }
    
    @Override
    public String complete(String prompt, String model) {
        return complete(prompt);
    }
};

// Use in service
PdfPaymentExtractionService service = new PdfPaymentExtractionService(mockClient);
```

### Integration Testing (Real OpenAI)
```java
// Use real OpenAI client
LlmClient client = OpenAiConfig.createLlmClient();
PdfPaymentExtractionService service = new PdfPaymentExtractionService(client);

// Test with sample PDF
String testPdfText = "..."; // From sample PDF
PaymentExtractionResult result = service.extractPayments(testPdfText);
```

## Troubleshooting

### "OPENAI_API_KEY not configured"
1. Check if `.env` file exists in project root
2. Verify OPENAI_API_KEY is in `.env` without extra quotes
3. Or set as system environment variable: `export OPENAI_API_KEY=sk-...`
4. Restart Java application after setting env var

### "Invalid API key"
1. Get fresh API key from https://platform.openai.com/api-keys
2. Ensure no extra spaces or quotes in `.env`
3. Check that key starts with `sk-`

### "No JSON in response" or "Invalid JSON"
1. Check that prompt is being sent correctly
2. Verify model has JSON capabilities (all OpenAI models do)
3. Try with gpt-4o-mini or gpt-4 for better JSON compliance
4. Invalid payments are skipped; check `PaymentExtractionResult.getStatements()`

### "PDF text is empty"
1. Verify PDF file exists at path in pdf_documents
2. Check PDF file is readable and not corrupted
3. Ensure PDF has text (not scanned without OCR)

### Slow extraction
1. Check OpenAI API status at https://status.openai.com
2. Reduce PDF document size if < 10KB of text
3. Use gpt-4o-mini instead of gpt-4 for faster processing

## Production Deployment

### Configuration
1. Set OPENAI_API_KEY as system environment variable (not in `.env`)
2. Set OPENAI_MODEL if using non-default model
3. Consider using secrets management (e.g., HashiCorp Vault, AWS Secrets Manager)

### Error Handling
- Wrap processPdfDocument() in try-catch
- Log failures to application logging system
- Implement retry logic with exponential backoff
- Monitor OpenAI API usage and costs

### Monitoring
- Track extraction success rate
- Monitor OpenAI API response times
- Alert on payment_count anomalies
- Log raw_llm_response for debugging

### Constraints
- OpenAI only (no other LLM providers)
- Full-document level (no chunk-based extraction)
- No dependency on pdf_chunks table
- No deduplication at extraction stage
