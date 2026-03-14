package com.ingestion;

import io.github.cdimascio.dotenv.Dotenv;
import java.io.InputStream;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Properties;
import com.ingestion.entity.PdfDocument;
import com.ingestion.repository.PdfDocumentRepository;
import com.ingestion.service.payment.PaymentExtractionPipeline;

/**
 * Main application entry point supporting multiple subcommands.
 * Subcommands: ingest, embed-missing, search
 */
public class AppMain {

	private static final String CONFIG_FILE = "config.properties";

	public static void main(String[] args) {
		// Load environment variables from .env file
		Dotenv dotenv = Dotenv.configure()
			.ignoreIfMissing()
			.load();
		
		// Populate system properties from .env
		dotenv.entries().forEach(entry -> 
			System.setProperty(entry.getKey(), entry.getValue())
		);

		if (args.length == 0) {
			printUsage();
			System.exit(1);
		}

		String command = args[0].toLowerCase();

		try {
			switch (command) {
				case "ingest":
					runingestion(args);
					break;

				case "embed-missing":
					runEmbedMissing(args);
					break;

				case "search":
					runSearch(args);
					break;

				case "hybrid-search":
					runHybridSearch(args);
					break;

				case "extract-payments":
					runExtractPayments(args);
					break;

				default:
					System.err.println("Unknown command: " + command);
					printUsage();
					System.exit(1);
			}
		} catch (Exception e) {
			System.err.println("Error: " + e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}
	}

	private static void runingestion(String[] args) throws Exception {
		// Delegate to PDFingestionApp (was the old main)
		String[] ingestArgs = new String[args.length - 1];
		System.arraycopy(args, 1, ingestArgs, 0, args.length - 1);
		PDFingestionApp.main(ingestArgs);
	}

	private static void runEmbedMissing(String[] args) throws Exception {
		System.out.println("=== Embed Missing Command ===");

		// Parse arguments
		int limit = 100;
		int batchSize = 50;

		for (int i = 1; i < args.length; i++) {
			if (args[i].equals("--limit") && i + 1 < args.length) {
				limit = Integer.parseInt(args[i + 1]);
				i++;
			} else if (args[i].equals("--batchSize") && i + 1 < args.length) {
				batchSize = Integer.parseInt(args[i + 1]);
				i++;
			}
		}

		// Load config
		Properties props = loadConfig();
		String dbUrl = props.getProperty("db.url", "jdbc:postgresql://localhost:5432/legal_ingestion");
		String dbUser = props.getProperty("db.user", "ingestion_user");
		String dbPassword = props.getProperty("db.password", "ingestion_pass");

		// Get OpenAI API key from environment
		String apiKey = OpenAIEmbeddingClient.getApiKeyFromEnv();

		// Execute command
		EmbedMissingCommand cmd = new EmbedMissingCommand(apiKey, dbUrl, dbUser, dbPassword);
		cmd.execute(limit, batchSize);
	}

	private static void runSearch(String[] args) throws Exception {
		System.out.println("=== Search Command ===");

		// Parse arguments
		String query = null;
		int topK = 10;

		for (int i = 1; i < args.length; i++) {
			if (args[i].equals("--query") && i + 1 < args.length) {
				query = args[i + 1];
				i++;
			} else if (args[i].equals("--topK") && i + 1 < args.length) {
				topK = Integer.parseInt(args[i + 1]);
				i++;
			}
		}

		if (query == null || query.trim().isEmpty()) {
			System.err.println("Error: --query parameter is required");
			System.err.println("Usage: java -jar legal-ingestion.jar search --query \"your query\" [--topK 10]");
			System.exit(1);
		}

		// Load config
		Properties props = loadConfig();
		String dbUrl = props.getProperty("db.url", "jdbc:postgresql://localhost:5432/legal_ingestion");
		String dbUser = props.getProperty("db.user", "ingestion_user");
		String dbPassword = props.getProperty("db.password", "ingestion_pass");

		// Get OpenAI API key from environment
		String apiKey = OpenAIEmbeddingClient.getApiKeyFromEnv();

		// Execute command
		SearchCommand cmd = new SearchCommand(apiKey, dbUrl, dbUser, dbPassword);
		cmd.execute(query, topK);
	}

	private static void runHybridSearch(String[] args) throws Exception {
		System.out.println("=== Hybrid Search Command ===");

		// Parse arguments
		String query = null;
		int topK = 10;
		int vectorTopN = 20;
		int keywordTopN = 20;
		double alpha = 0.7;

		for (int i = 1; i < args.length; i++) {
			if (args[i].equals("--query") && i + 1 < args.length) {
				query = args[i + 1];
				i++;
			} else if (args[i].equals("--topK") && i + 1 < args.length) {
				topK = Integer.parseInt(args[i + 1]);
				i++;
			} else if (args[i].equals("--vectorTopN") && i + 1 < args.length) {
				vectorTopN = Integer.parseInt(args[i + 1]);
				i++;
			} else if (args[i].equals("--keywordTopN") && i + 1 < args.length) {
				keywordTopN = Integer.parseInt(args[i + 1]);
				i++;
			} else if (args[i].equals("--alpha") && i + 1 < args.length) {
				alpha = Double.parseDouble(args[i + 1]);
				i++;
			}
		}

		if (query == null || query.trim().isEmpty()) {
			System.err.println("Error: --query parameter is required");
			System.err.println("Usage: mvn exec:java -Dexec.args=\"hybrid-search --query \\\"query\\\" [--topK 10] [--vectorTopN 20] [--keywordTopN 20] [--alpha 0.7]\"");
			System.exit(1);
		}

		// Load config
		Properties props = loadConfig();
		String dbUrl = props.getProperty("db.url", "jdbc:postgresql://localhost:5432/legal_ingestion");
		String dbUser = props.getProperty("db.user", "ingestion_user");
		String dbPassword = props.getProperty("db.password", "ingestion_pass");

		// Get OpenAI API key from environment
		String apiKey = OpenAIEmbeddingClient.getApiKeyFromEnv();

		// Execute command
		try (java.sql.Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
			HybridSearchCommand cmd = new HybridSearchCommand(conn, apiKey, query, topK, vectorTopN, keywordTopN, alpha);
			java.util.List<HybridSearchHit> results = cmd.execute();

			if (results.isEmpty()) {
				System.out.println("No results found.");
			} else {
				System.out.println("\nTop-" + Math.min(topK, results.size()) + " results:");
				for (int i = 0; i < results.size(); i++) {
					System.out.println((i + 1) + ". " + results.get(i));
				}
			}
		}
	}

	private static Properties loadConfig() {
		Properties properties = new Properties();
		try (InputStream input = AppMain.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
			if (input != null) {
				properties.load(input);
				return properties;
			}
		} catch (Exception e) {
			System.out.println("Warning: Could not load config.properties: " + e.getMessage());
		}
		return properties;
	}

	private static void runExtractPayments(String[] args) throws Exception {
		System.out.println("=== Payment Extraction Command ===");

		// Parse arguments: extract-payments [<pdf_document_id>] [--all] [--model model_name] [--status STATUS]
		Long pdfDocumentId = null;
		boolean processAll = false;
		String modelOverride = null;
		String statusFilter = null;

		// Parse optional arguments
		for (int i = 1; i < args.length; i++) {
			if (args[i].equals("--all")) {
				processAll = true;
			} else if (args[i].equals("--model") && i + 1 < args.length) {
				modelOverride = args[i + 1];
				i++;
			} else if (args[i].equals("--status") && i + 1 < args.length) {
				statusFilter = args[i + 1];
				i++;
			} else if (!args[i].startsWith("--")) {
				// Treat as PDF document ID
				if (pdfDocumentId == null) {
					try {
						pdfDocumentId = Long.parseLong(args[i]);
					} catch (NumberFormatException e) {
						System.err.println("Error: PDF document ID must be a valid number");
						System.exit(1);
						return;
					}
				}
			}
		}

		// Load config
		Properties props = loadConfig();
		String dbUrl = props.getProperty("db.url", "jdbc:postgresql://localhost:5432/legal_ingestion");
		String dbUser = props.getProperty("db.user", "ingestion_user");
		String dbPassword = props.getProperty("db.password", "ingestion_pass");

		// Create database connection
		try (java.sql.Connection dbConn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
			// Create pipeline with OpenAI configuration
			PaymentExtractionPipeline pipeline;
			if (modelOverride != null) {
				pipeline = PaymentExtractionPipeline.withOpenAi(dbConn, modelOverride);
				System.out.println("Using model override: " + modelOverride);
			} else {
				pipeline = PaymentExtractionPipeline.withDefaultOpenAi(dbConn);
			}

			// Determine which PDFs to process
			java.util.List<PdfDocument> pdfsToProcess = new ArrayList<>();

			if (pdfDocumentId != null) {
				// Process specific PDF
				PdfDocument pdfDoc = PdfDocumentRepository.findById(dbConn, pdfDocumentId);
				if (pdfDoc == null) {
					System.err.println("Error: PDF document not found with ID: " + pdfDocumentId);
					System.exit(1);
					return;
				}
				pdfsToProcess.add(pdfDoc);
			} else if (processAll) {
				// Process all PDFs (or filter by payment extraction status)
				if (statusFilter != null) {
					// Custom query needed for payment_extraction_status filter
					// For now, fetch all and filter in memory (or add method to repository)
					java.util.List<PdfDocument> allDocs = PdfDocumentRepository.findAll(dbConn);
					for (PdfDocument doc : allDocs) {
						if (statusFilter.equals(doc.getPaymentExtractionStatus())) {
							pdfsToProcess.add(doc);
						}
					}
					System.out.println("Processing all PDFs with payment extraction status: " + statusFilter);
				} else {
					// Process only unprocessed PDFs (payment_extraction_status: NEW or FAILED)
					java.util.List<PdfDocument> allDocs = PdfDocumentRepository.findAll(dbConn);
					for (PdfDocument doc : allDocs) {
						String peStatus = doc.getPaymentExtractionStatus();
						if (peStatus == null || peStatus.equals("NEW") || peStatus.equals("FAILED")) {
							pdfsToProcess.add(doc);
						}
					}
					System.out.println("Processing all unprocessed PDFs (payment extraction status: NEW or FAILED)");
				}
			} else {
				// No PDF ID and no --all flag: print usage and exit
				System.err.println("Error: PDF document ID is required or use --all flag");
				System.err.println();
				System.err.println("Usage:");
				System.err.println("  java AppMain extract-payments <pdf_id> [--model gpt-4o-mini]");
				System.err.println("  java AppMain extract-payments --all [--status STATUS] [--model gpt-4o-mini]");
				System.err.println();
				System.err.println("Options:");
				System.err.println("  <pdf_id>           : Process a specific PDF document by ID");
				System.err.println("  --all              : Process all unprocessed PDFs (status: NEW or FAILED)");
				System.err.println("  --status STATUS    : With --all, filter by status (e.g., NEW, FAILED, COMPLETED)");
				System.err.println("  --model MODEL      : Override OpenAI model (default: gpt-4o-mini)");
				System.exit(1);
				return;
			}

			if (pdfsToProcess.isEmpty()) {
				System.out.println("No PDFs found to process");
				return;
			}

			// Process each PDF
			System.out.println();
			System.out.println("Found " + pdfsToProcess.size() + " PDF(s) to process");
			System.out.println();

			int successCount = 0;
			int failureCount = 0;

			for (int idx = 0; idx < pdfsToProcess.size(); idx++) {
				PdfDocument pdfDoc = pdfsToProcess.get(idx);
				System.out.println("[" + (idx + 1) + "/" + pdfsToProcess.size() + "] Processing PDF ID: " + pdfDoc.getId() + " - " + pdfDoc.getFileName());

				try {
					// Process the PDF
					PaymentExtractionPipeline.PaymentExtractionPipelineResult result = pipeline.processPdfDocument(pdfDoc.getId());

					// Display results
					if (result.isSuccess()) {
						System.out.println("  ✓ Success");
						System.out.println("    Statements: " + (result.getExtractionRun() != null ? result.getExtractionRun().getStatementCount() : 0));
						System.out.println("    Payments: " + result.getPaymentCount());
						System.out.println("    Inserted: " + result.getInsertedCount());
						if (result.getExtractionRun() != null && result.getExtractionRun().getIsScanned()) {
							System.out.println("    Note: Scanned (OCR) document detected");
						}

						// Update PDF document payment extraction status to SUCCEEDED
						pdfDoc.setPaymentExtractionStatus("SUCCEEDED");
						pdfDoc.setPaymentExtractionCompletedAt(java.time.Instant.now());
						pdfDoc.setPaymentExtractionErrorMsg(null);
						PdfDocumentRepository.update(dbConn, pdfDoc);
						
						successCount++;
					} else {
						System.out.println("  ✗ Failed: " + result.getErrorMessage());

						// Update PDF document payment extraction status to FAILED
						pdfDoc.setPaymentExtractionStatus("FAILED");
						pdfDoc.setPaymentExtractionErrorMsg(result.getErrorMessage());
						pdfDoc.setPaymentExtractionCompletedAt(java.time.Instant.now());
						PdfDocumentRepository.update(dbConn, pdfDoc);
						
						failureCount++;
					}
				} catch (Exception e) {
					System.out.println("  ✗ Exception: " + e.getMessage());

					// Update PDF document payment extraction status to FAILED
					pdfDoc.setPaymentExtractionStatus("FAILED");
					pdfDoc.setPaymentExtractionErrorMsg(e.getMessage());
					pdfDoc.setPaymentExtractionCompletedAt(java.time.Instant.now());
					try {
						PdfDocumentRepository.update(dbConn, pdfDoc);
					} catch (Exception updateEx) {
						System.err.println("    Warning: Failed to update PDF payment extraction status in database: " + updateEx.getMessage());
					}
					
					failureCount++;
				}

				System.out.println();
			}

			// Summary
			System.out.println("=== Extraction Summary ===");
			System.out.println("Total: " + pdfsToProcess.size());
			System.out.println("Successful: " + successCount);
			System.out.println("Failed: " + failureCount);

			if (failureCount > 0) {
				System.exit(1);
			}
		}
	}

	private static void printUsage() {
		System.out.println("=== Legal ingestion Tool ===");
		System.out.println();
		System.out.println("Usage:");
		System.out.println("  mvn exec:java -Dexec.args=\"ingest [directory] [dbUrl] [dbUser] [dbPassword]\"");
		System.out.println("  mvn exec:java -Dexec.args=\"embed-missing [--limit 100] [--batchSize 50]\"");
		System.out.println("  mvn exec:java -Dexec.args=\"search --query \\\"your search\\\" [--topK 10]\"");
		System.out.println("  mvn exec:java -Dexec.args=\"hybrid-search --query \\\"your search\\\" [--topK 10] [--vectorTopN 20] [--keywordTopN 20] [--alpha 0.7]\"");
		System.out.println("  java AppMain extract-payments <pdf_id> [--model gpt-4o-mini]");
		System.out.println("  java AppMain extract-payments --all [--status STATUS] [--model gpt-4o-mini]");
		System.out.println();
		System.out.println("Hybrid Search:");
		System.out.println("  Combines vector similarity and keyword (full-text) search for legal documents.");
		System.out.println("  --query         : Search query (required)");
		System.out.println("  --topK          : Top-K final results (default: 10)");
		System.out.println("  --vectorTopN    : Number of vector search results to fetch (default: 20)");
		System.out.println("  --keywordTopN   : Number of keyword search results to fetch (default: 20)");
		System.out.println("  --alpha         : Weight for vector score in [0,1] (default: 0.7)");
		System.out.println("                    finalScore = alpha*vectorScore + (1-alpha)*keywordScore");
		System.out.println("Payment Extraction:");
		System.out.println("  Extracts payment records from PDF bank statements using OpenAI API.");
		System.out.println("  <pdf_id>      : Document ID from pdf_documents table (optional)");
		System.out.println("  --all         : Process all unprocessed PDFs (payment extraction status: NEW or FAILED)");
		System.out.println("  --status      : Filter by payment extraction status when using --all (e.g., NEW, FAILED, SUCCEEDED)");
		System.out.println("  --model       : Override OpenAI model (default: gpt-4o-mini)");
		System.out.println();
		System.out.println("PDF Status Values:");
		System.out.println("  Ingestion Status (status column):");
		System.out.println("    NEW           : Not yet ingested");
		System.out.println("    PROCESSING    : Currently ingesting");
		System.out.println("    DONE          : Successfully ingested");
		System.out.println("    FAILED        : Ingestion failed");
		System.out.println("  Payment Extraction Status (payment_extraction_status column):");
		System.out.println("    NEW           : Not yet extracted");
		System.out.println("    RUNNING       : Currently extracting");
		System.out.println("    SUCCEEDED     : Successfully extracted");
		System.out.println("    FAILED        : Extraction failed");
		System.out.println();
		System.out.println("Environment Variables:");
		System.out.println("  OPENAI_API_KEY - Required for embed-missing, search, hybrid-search, and extract-payments commands");
		System.out.println("  OPENAI_MODEL   - OpenAI model for extract-payments (default: gpt-4o-mini)");
		System.out.println();
		System.out.println("Configuration:");
		System.out.println("  See config.properties for database and application settings");
		System.out.println();
	}
}
