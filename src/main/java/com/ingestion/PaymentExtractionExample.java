package com.ingestion;

import com.ingestion.service.payment.PaymentExtractionPipeline;
import java.sql.Connection;
import java.sql.DriverManager;

/**
 * Example usage of the payment extraction pipeline with OpenAI.
 *
 * This example shows how to:
 * 1. Set up OpenAI configuration from environment
 * 2. Create a payment extraction pipeline
 * 3. Process a PDF document for payment extraction
 * 4. Handle the extraction results
 *
 * Prerequisites:
 * - Set OPENAI_API_KEY environment variable or .env file
 * - PDF document must exist in pdf_documents table
 * - Database connection configured
 */
public class PaymentExtractionExample {

    /**
     * Main entry point demonstrating payment extraction pipeline.
     *
     * Usage:
     *   java -DOPENAI_API_KEY=sk-... com.ingestion.PaymentExtractionExample <pdf_document_id>
     *
     * Or set via .env file and run with:
     *   java com.ingestion.PaymentExtractionExample <pdf_document_id>
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java PaymentExtractionExample <pdf_document_id>");
            System.err.println("Example: java PaymentExtractionExample 123");
            System.exit(1);
        }

        Long pdfDocumentId;
        try {
            pdfDocumentId = Long.parseLong(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("Invalid PDF document ID: " + args[0]);
            System.exit(1);
            return;
        }

        // Connect to database
        Connection dbConnection = null;
        try {
            dbConnection = getDbConnection();
            processPaymentExtraction(dbConnection, pdfDocumentId);
        } finally {
            if (dbConnection != null) {
                dbConnection.close();
            }
        }
    }

    /**
     * Process a single PDF for payment extraction.
     */
    private static void processPaymentExtraction(Connection dbConnection, Long pdfDocumentId) throws Exception {
        System.out.println("Starting payment extraction for PDF document: " + pdfDocumentId);

        try {
            // Create pipeline with default OpenAI configuration
            // Reads OPENAI_API_KEY and OPENAI_MODEL from environment
            PaymentExtractionPipeline pipeline = PaymentExtractionPipeline.withDefaultOpenAi(dbConnection);

            // Process the PDF document
            System.out.println("Processing PDF...");
            PaymentExtractionPipeline.PaymentExtractionPipelineResult result = 
                pipeline.processPdfDocument(pdfDocumentId);

            // Handle results
            if (result.isSuccess()) {
                System.out.println("✓ Payment extraction successful!");
                System.out.println("  - Statements found: " + result.getExtractionRun().getStatementCount());
                System.out.println("  - Payments extracted: " + result.getPaymentCount());
                System.out.println("  - Inserted to database: " + result.getInsertedCount());
                System.out.println("  - Model used: " + result.getExtractionRun().getModelName());
                System.out.println("  - Completed at: " + result.getExtractionRun().getCompletedAt());
            } else {
                System.err.println("✗ Payment extraction failed!");
                System.err.println("  Error: " + result.getErrorMessage());
                System.exit(1);
            }

        } catch (Exception e) {
            System.err.println("✗ Pipeline error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Example: Process multiple PDFs in sequence.
     */
    public static void processMultiplePdfs(Connection dbConnection, Long[] pdfDocumentIds) throws Exception {
        PaymentExtractionPipeline pipeline = PaymentExtractionPipeline.withDefaultOpenAi(dbConnection);

        int successCount = 0;
        int failureCount = 0;

        for (Long pdfId : pdfDocumentIds) {
            try {
                System.out.println("\nProcessing PDF: " + pdfId);
                var result = pipeline.processPdfDocument(pdfId);
                
                if (result.isSuccess()) {
                    System.out.println("  ✓ Success: " + result.getPaymentCount() + " payments extracted");
                    successCount++;
                } else {
                    System.err.println("  ✗ Failed: " + result.getErrorMessage());
                    failureCount++;
                }
            } catch (Exception e) {
                System.err.println("  ✗ Error: " + e.getMessage());
                failureCount++;
            }
        }

        System.out.println("\n--- Summary ---");
        System.out.println("Successful: " + successCount);
        System.out.println("Failed: " + failureCount);
    }

    /**
     * Example: Use a specific OpenAI model instead of default.
     */
    public static void processWithCustomModel(Connection dbConnection, Long pdfDocumentId, String model) throws Exception {
        System.out.println("Using custom model: " + model);
        
        PaymentExtractionPipeline pipeline = PaymentExtractionPipeline.withOpenAi(dbConnection, model);
        var result = pipeline.processPdfDocument(pdfDocumentId);

        if (result.isSuccess()) {
            System.out.println("✓ Extraction successful with " + result.getExtractionRun().getModelName());
            System.out.println("  Payments: " + result.getPaymentCount());
        } else {
            System.err.println("✗ Extraction failed: " + result.getErrorMessage());
        }
    }

    /**
     * Get database connection.
     * Implement based on your connection management strategy.
     */
    private static Connection getDbConnection() throws Exception {
        // Option 1: Direct JDBC connection (for testing)
        // String url = "jdbc:postgresql://localhost:5432/legal_ingestion";
        // String user = "postgres";
        // String password = "...";
        // return DriverManager.getConnection(url, user, password);

        // Option 2: Use connection pool (in production)
        // return getConnectionPool().getConnection();

        // For now, return a dummy connection for demonstration
        // In real usage, implement this based on your database setup
        throw new Exception("Database connection not configured. Implement getDbConnection() method.");
    }

    /**
     * Configuration Example (in application properties or config class)
     *
     * In .env file:
     *   OPENAI_API_KEY=sk-...
     *   OPENAI_MODEL=gpt-4o-mini
     *
     * Or in application.properties:
     *   openai.api.key=${OPENAI_API_KEY}
     *   openai.model=gpt-4o-mini
     *
     * Or as system environment variables:
     *   export OPENAI_API_KEY=sk-...
     *   export OPENAI_MODEL=gpt-4o-mini
     */

    /**
     * Error Handling Example
     */
    public static void demonstrateErrorHandling() {
        System.out.println("Error handling scenarios:");
        System.out.println();

        System.out.println("1. Missing OPENAI_API_KEY:");
        System.out.println("   Error: OPENAI_API_KEY not configured");
        System.out.println("   Fix: Set OPENAI_API_KEY in .env or env vars");
        System.out.println();

        System.out.println("2. Invalid API Key:");
        System.out.println("   Error: OpenAI API error: 401");
        System.out.println("   Fix: Verify API key at https://platform.openai.com/api-keys");
        System.out.println();

        System.out.println("3. Invalid JSON from LLM:");
        System.out.println("   Behavior: Invalid payments are skipped");
        System.out.println("   Result: Extraction continues with valid payments");
        System.out.println();

        System.out.println("4. PDF text loading failed:");
        System.out.println("   Error: Failed to load text from PDF");
        System.out.println("   Fix: Verify PDF file path and permissions");
        System.out.println();

        System.out.println("5. OpenAI rate limiting (429):");
        System.out.println("   Behavior: Exception thrown");
        System.out.println("   Fix: Implement exponential backoff retry");
    }
}
