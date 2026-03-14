package com.ingestion.service.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ingestion.entity.ExtractedPayment;
import com.ingestion.entity.PaymentExtractionResult;
import com.ingestion.entity.PaymentRecord;
import com.ingestion.entity.PdfDocument;
import com.ingestion.entity.PdfPaymentExtractionRun;
import com.ingestion.entity.StatementSummary;
import com.ingestion.repository.PaymentRecordRepository;
import com.ingestion.repository.PdfDocumentRepository;
import com.ingestion.repository.PdfPaymentExtractionRunRepository;
import com.ingestion.service.llm.LlmClient;
import com.ingestion.service.llm.OpenAiConfig;

import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Main orchestrator for payment extraction pipeline.
 * Coordinates:
 * 1. Document loading
 * 2. Text extraction from PDF
 * 3. LLM-based payment extraction
 * 4. Flattening statements to payment records
 * 5. Database insertion
 * 6. Status tracking
 *
 * Designed for full-document level processing, not chunk-based.
 */
public class PaymentExtractionPipeline {

    private final Connection databaseConnection;
    private final PdfPaymentExtractionService extractionService;
    private final ObjectMapper objectMapper;

    public PaymentExtractionPipeline(
            Connection databaseConnection,
            PdfPaymentExtractionService extractionService) {
        this.databaseConnection = databaseConnection;
        this.extractionService = extractionService;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Create pipeline with default OpenAI client from configuration.
     * Uses OPENAI_API_KEY and OPENAI_MODEL from environment.
     */
    public static PaymentExtractionPipeline withDefaultOpenAi(Connection databaseConnection) throws Exception {
        LlmClient llmClient = OpenAiConfig.createLlmClient();
        PdfPaymentExtractionService extractionService = new PdfPaymentExtractionService(llmClient);
        return new PaymentExtractionPipeline(databaseConnection, extractionService);
    }

    /**
     * Create pipeline with OpenAI client, overriding the model name.
     */
    public static PaymentExtractionPipeline withOpenAi(Connection databaseConnection, String modelOverride) throws Exception {
        LlmClient llmClient = OpenAiConfig.createLlmClient(modelOverride);
        PdfPaymentExtractionService extractionService = new PdfPaymentExtractionService(llmClient);
        return new PaymentExtractionPipeline(databaseConnection, extractionService);
    }

    /**
     * Process a PDF document for payment extraction.
     * 
     * Rerun strategy: When reprocessing a PDF, deletes old payment records and inserts new ones.
     * No complex deduplication or versioning.
     * 
     * Steps:
     * 1. Load PDF document metadata
     * 2. Create extraction run record (PENDING)
     * 3. Mark run as RUNNING
     * 4. Load full document text from PDF
     * 5. Call LLM and parse JSON result
     * 6. Flatten statements → payment records
     * 
     * On success:
     * 7. Start transaction
     * 8. DELETE old payment_records for this pdf_document_id
     * 9. INSERT new payment_records
     * 10. Commit transaction
     * 11. Mark extraction run as SUCCEEDED
     * 12. Store statement_count and payment_count
     * 
     * On failure:
     * - Mark extraction run FAILED
     * - Keep previous payment_records unchanged
     *
     * @param pdfDocumentId the ID of the PDF document to process
     * @return extraction result with counts of inserted payments
     * @throws Exception if any step of the pipeline fails
     */
    public PaymentExtractionPipelineResult processPdfDocument(Long pdfDocumentId) throws Exception {
        if (pdfDocumentId == null || pdfDocumentId <= 0) {
            throw new IllegalArgumentException("Invalid PDF document ID");
        }

        PaymentExtractionPipelineResult result = new PaymentExtractionPipelineResult();
        result.setPdfDocumentId(pdfDocumentId);

        PdfPaymentExtractionRun extractionRun = null;

        try {
            // Step 1: Load PDF document metadata
            PdfDocument pdfDoc = PdfDocumentRepository.findById(databaseConnection, pdfDocumentId);
            if (pdfDoc == null) {
                throw new Exception("PDF document not found: " + pdfDocumentId);
            }
            result.setPdfDocument(pdfDoc);

            // Step 2: Create extraction run record (PENDING)
            extractionRun = new PdfPaymentExtractionRun(pdfDocumentId);
            extractionRun.setStatus("PENDING");
            extractionRun.setModelName(getModelName());
            extractionRun.setPromptVersion(PdfPaymentPromptBuilder.getPromptVersion());
            
            long runId = PdfPaymentExtractionRunRepository.create(databaseConnection, extractionRun);
            extractionRun.setId(runId);
            result.setExtractionRun(extractionRun);

            // Step 3: Mark extraction as RUNNING
            extractionRun.setStatus("RUNNING");
            PdfPaymentExtractionRunRepository.update(databaseConnection, extractionRun);

            // Step 4: Load full document text from PDF
            String documentText = PdfTextLoader.loadTextFromPdf(pdfDoc.getFilePath());
            
            // Step 4.5: If no text, try vision-based extraction for scanned PDFs
            PaymentExtractionResult extractionResult = null;
            
            if (documentText == null || documentText.isEmpty()) {
                System.out.println("  [Vision] Detected scanned/image-based PDF. Using vision-based extraction (GPT-4o)...");
                try {
                    // Use vision-based extraction for scanned PDFs
                    extractionResult = extractionService.extractPaymentsFromScannedPdf(pdfDoc.getFilePath());
                    extractionRun.setInputTextLength(0);  // Vision-based, no text input
                } catch (Exception visionException) {
                    throw new Exception(
                        "PDF is scanned and vision-based extraction failed: " + visionException.getMessage()
                    );
                }
            } else {
                // Use text-based extraction for digital PDFs
                extractionRun.setInputTextLength(documentText.length());
                result.setDocumentTextLength(documentText.length());
                
                // Step 5 & 6 & 7: Call LLM with text
                extractionResult = extractionService.extractPayments(documentText);
            }

            if (extractionResult == null) {
                throw new Exception("Extraction returned null result");
            }

            result.setExtractionResult(extractionResult);

            // Step 6: Flatten statements and payments into PaymentRecord entities
            List<PaymentRecord> paymentRecords = flattenToPaymentRecords(pdfDocumentId, extractionResult);
            result.setPaymentCount(paymentRecords.size());

            // Steps 7-10: Start transaction, delete old records, insert new records, commit
            boolean autoCommitOld = databaseConnection.getAutoCommit();
            try {
                databaseConnection.setAutoCommit(false);

                // Delete existing payment records for this PDF document
                int deletedCount = PaymentRecordRepository.deleteByPdfDocumentId(databaseConnection, pdfDocumentId);
                System.out.println("Deleted " + deletedCount + " old payment records for PDF " + pdfDocumentId);

                // Insert new payment records
                int insertedCount = 0;
                if (!paymentRecords.isEmpty()) {
                    int[] batchResults = PaymentRecordRepository.batchInsert(databaseConnection, paymentRecords);
                    insertedCount = batchResults.length;
                }
                result.setInsertedCount(insertedCount);

                // Commit transaction
                databaseConnection.commit();
                System.out.println("Transaction committed: Inserted " + insertedCount + " new payment records");

            } catch (Exception txException) {
                databaseConnection.rollback();
                System.err.println("Transaction rolled back due to error: " + txException.getMessage());
                throw txException;
            } finally {
                databaseConnection.setAutoCommit(autoCommitOld);
            }

            // Step 11: Mark extraction run as SUCCEEDED
            extractionRun.setStatus("SUCCEEDED");
            extractionRun.setPaymentCount(result.getInsertedCount());
            extractionRun.setStatementCount(
                extractionResult.getStatements() != null ? extractionResult.getStatements().size() : 0
            );
            extractionRun.setIsScanned(
                extractionResult.getDocumentSummary() != null && 
                extractionResult.getDocumentSummary().getIsScannedDocument() != null ?
                extractionResult.getDocumentSummary().getIsScannedDocument() : false
            );
            extractionRun.setCompletedAt(Instant.now());
            
            // Store full response for audit trail
            extractionRun.setRawLlmResponse(objectMapper.valueToTree(extractionResult));
            
            PdfPaymentExtractionRunRepository.update(databaseConnection, extractionRun);

            result.setSuccess(true);
            result.setErrorMessage(null);

            return result;

        } catch (Exception e) {
            // On failure: mark extraction run FAILED, keep previous payment_records unchanged
            if (extractionRun != null) {
                extractionRun.setStatus("FAILED");
                extractionRun.setErrorMsg(e.getMessage());
                extractionRun.setCompletedAt(Instant.now());
                
                try {
                    PdfPaymentExtractionRunRepository.update(databaseConnection, extractionRun);
                    System.err.println("Extraction marked FAILED: " + e.getMessage());
                } catch (Exception updateEx) {
                    System.err.println("Failed to update extraction run status to FAILED: " + updateEx.getMessage());
                }
            }

            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            
            // Re-throw for caller to handle
            throw e;
        }
    }

    /**
     * Flatten extraction result statements into individual PaymentRecord entities.
     * Maps each payment in each statement to a PaymentRecord with statement context.
     *
     * @param pdfDocumentId the source PDF document ID
     * @param result the extraction result with statements
     * @return list of PaymentRecord entities ready for insertion
     */
    private List<PaymentRecord> flattenToPaymentRecords(Long pdfDocumentId, PaymentExtractionResult result) {
        List<PaymentRecord> records = new ArrayList<>();

        if (result == null || result.getStatements() == null) {
            return records;
        }

        for (StatementSummary statement : result.getStatements()) {
            if (statement.getPayments() == null || statement.getPayments().isEmpty()) {
                continue;
            }

            for (ExtractedPayment payment : statement.getPayments()) {
                PaymentRecord record = new PaymentRecord();
                record.setPdfDocumentId(pdfDocumentId);

                // Statement context
                record.setStatementIndex(statement.getStatementIndex());
                record.setStatementPeriodStart(statement.getStatementPeriodStart());
                record.setStatementPeriodEnd(statement.getStatementPeriodEnd());

                // Payment details
                record.setPaymentDate(payment.getPaymentDate());
                record.setCategory(payment.getCategory());
                record.setTotalAmount(payment.getTotalAmount());
                record.setPrincipalAmount(payment.getPrincipalAmount());
                record.setInterestAmount(payment.getInterestAmount());
                record.setEscrowAmount(payment.getEscrowAmount());
                record.setTaxAmount(payment.getTaxAmount());
                record.setInsuranceAmount(payment.getInsuranceAmount());

                // Currency
                record.setCurrency("USD");

                // Party information
                record.setPayerName(payment.getPayerName());
                record.setPayeeName(payment.getPayeeName());
                
                // Loan information
                record.setLoanNumber(payment.getLoanNumber());

                // Property information
                record.setPropertyAddress(payment.getPropertyAddress());
                record.setPropertyCity(payment.getPropertyCity());
                record.setPropertyState(payment.getPropertyState());
                record.setPropertyZip(payment.getPropertyZip());

                // Description
                record.setDescription(payment.getDescription());

                // Provenance
                record.setSourcePage(payment.getSourcePage());
                record.setSourceSnippet(payment.getSourceSnippet());
                record.setConfidence(payment.getConfidence());

                // Raw LLM JSON (for audit trail)
                record.setRawLlmJson(objectMapper.valueToTree(payment));

                // Timestamp
                record.setCreatedAt(Instant.now());

                records.add(record);
            }
        }

        return records;
    }

    /**
     * Get the model name being used for extraction.
     */
    private String getModelName() {
        try {
            return OpenAiConfig.getModel();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Result container for pipeline execution.
     */
    public static class PaymentExtractionPipelineResult {
        private Long pdfDocumentId;
        private PdfDocument pdfDocument;
        private PdfPaymentExtractionRun extractionRun;
        private PaymentExtractionResult extractionResult;
        private Integer documentTextLength;
        private Integer paymentCount;
        private Integer insertedCount;
        private boolean success;
        private String errorMessage;

        // Getters and Setters
        public Long getPdfDocumentId() {
            return pdfDocumentId;
        }

        public void setPdfDocumentId(Long pdfDocumentId) {
            this.pdfDocumentId = pdfDocumentId;
        }

        public PdfDocument getPdfDocument() {
            return pdfDocument;
        }

        public void setPdfDocument(PdfDocument pdfDocument) {
            this.pdfDocument = pdfDocument;
        }

        public PdfPaymentExtractionRun getExtractionRun() {
            return extractionRun;
        }

        public void setExtractionRun(PdfPaymentExtractionRun extractionRun) {
            this.extractionRun = extractionRun;
        }

        public PaymentExtractionResult getExtractionResult() {
            return extractionResult;
        }

        public void setExtractionResult(PaymentExtractionResult extractionResult) {
            this.extractionResult = extractionResult;
        }

        public Integer getDocumentTextLength() {
            return documentTextLength;
        }

        public void setDocumentTextLength(Integer documentTextLength) {
            this.documentTextLength = documentTextLength;
        }

        public Integer getPaymentCount() {
            return paymentCount;
        }

        public void setPaymentCount(Integer paymentCount) {
            this.paymentCount = paymentCount;
        }

        public Integer getInsertedCount() {
            return insertedCount;
        }

        public void setInsertedCount(Integer insertedCount) {
            this.insertedCount = insertedCount;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        @Override
        public String toString() {
            return "PaymentExtractionPipelineResult{" +
                    "pdfDocumentId=" + pdfDocumentId +
                    ", paymentCount=" + paymentCount +
                    ", insertedCount=" + insertedCount +
                    ", success=" + success +
                    ", errorMessage='" + errorMessage + '\'' +
                    '}';
        }
    }
}
