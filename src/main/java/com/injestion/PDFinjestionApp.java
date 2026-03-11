package com.injestion;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Properties;

/**
 * Main application to injest PDF files: extract text by page, calculate SHA256,
 * and persist to PostgreSQL database with page-level chunking (citation-aware).
 *
 * MVP Level 1 optimizations:
 * - Single UPSERT SQL for document insertion/update
 * - Minimal database connections (one per PDF)
 * - Error-aware: reuse SHA256 hash, no redundant calculations
 *
 * Usage:
 *   mvn compile exec:java -Dexec.mainClass="com.injestion.PDFinjestionApp"
 *   mvn compile exec:java -Dexec.mainClass="com.injestion.PDFinjestionApp" \
 *       -Dexec.args="/path/to/pdfs jdbc:postgresql://localhost:5432/db user pass"
 */
public class PDFinjestionApp {

	private static final String CONFIG_FILE = "config.properties";
	private static final String DEFAULT_DIRECTORY = "/Users/ling-senpeng/Documents/divorce 2026";
	private static final String DEFAULT_DB_URL = "jdbc:postgresql://localhost:5432/legal_injestion";
	private static final String DEFAULT_DB_USER = "injestion_user";
	private static final String DEFAULT_DB_PASSWORD = "injestion_pass";

	public static void main(String[] args) {
		// Parse command-line arguments or load from config
		String directoryPath = DEFAULT_DIRECTORY;
		String dbUrl = DEFAULT_DB_URL;
		String dbUser = DEFAULT_DB_USER;
		String dbPassword = DEFAULT_DB_PASSWORD;

		if (args.length > 0) {
			directoryPath = args[0];
		}
		if (args.length > 1) {
			dbUrl = args[1];
		}
		if (args.length > 2) {
			dbUser = args[2];
		}
		if (args.length > 3) {
			dbPassword = args[3];
		}

		// Try to load from config if not provided via args
		Properties props = loadConfig();
		if (props != null) {
			if (args.length == 0) {
				directoryPath = props.getProperty("pdf.injestion.directory", directoryPath);
			}
			dbUrl = props.getProperty("db.url", dbUrl);
			dbUser = props.getProperty("db.user", dbUser);
			dbPassword = props.getProperty("db.password", dbPassword);
		}

		// Ensure database driver is loaded
		DocumentRepo.ensureDriverLoaded();

		System.out.println("=== PDF injestion Pipeline (MVP Level 1) ===");
		System.out.println("Input Directory: " + directoryPath);
		System.out.println("Database URL: " + dbUrl);
		System.out.println();

		PDFReader pdfReader = new PDFReader();

		try {
			// Discover all PDF files
			List<Path> pdfFiles = pdfReader.findPDFFiles(directoryPath);
			System.out.println("Found " + pdfFiles.size() + " PDF file(s) in " + directoryPath);
			System.out.println();

			if (pdfFiles.isEmpty()) {
				System.out.println("No PDF files found.");
				return;
			}

			// NOTE: File size limits removed - all PDFs will be processed
			int processedCount = 0;
			int skippedCount = 0;
			int failedCount = 0;

			for (Path pdfPath : pdfFiles) {
				String filePath = pdfPath.toString();
				String fileName = pdfPath.getFileName().toString();
				long fileSize = Files.size(pdfPath);

				System.out.println("[" + (processedCount + skippedCount + failedCount + 1) + "] Processing: " + fileName);

				// Calculate sha256 once per PDF
				String sha256 = null;
				long docId = -1;

				try {
					// Calculate SHA256 hash (first I/O operation)
					System.out.println("  • Computing SHA256...");
					sha256 = Sha256Hasher.computeHash(filePath);

					// Open connection for this PDF and reuse it
					try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
						// Upsert document and get ID (sets status = 'PROCESSING')
						System.out.println("  • Upserting document record...");
						docId = DocumentRepo.upsertAndGetId(conn, fileName, filePath, sha256, fileSize);
						System.out.println("  • Document ID: " + docId);

						// Extract pages
						System.out.println("  • Extracting text by page...");
						List<PDFReader.PageText> pages = pdfReader.extractPages(filePath);
						System.out.println("  • Pages extracted: " + pages.size());

						// Insert chunks with citations (same connection)
						System.out.println("  • Storing chunks with page citations...");
						int chunkCount = ChunkRepo.insertPageChunks(conn, docId, pages);
						System.out.println("  • Inserted/updated " + chunkCount + " chunks");

						// Mark as done (same connection)
						DocumentRepo.markDone(conn, docId);
						System.out.println("  ✓ Success: " + pages.size() + " page(s) injested");
						processedCount++;
					}

				} catch (Exception e) {
					System.err.println("  ✗ Error: " + e.getMessage());
					failedCount++;

					// Try to mark as failed in database (reuse sha256, get docId if needed)
					try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
						// If docId was not obtained yet, upsert to get it
						if (docId < 0 && sha256 != null) {
							docId = DocumentRepo.upsertAndGetId(conn, fileName, filePath, sha256, fileSize);
						}

						// Mark as failed
						if (docId >= 0) {
							DocumentRepo.markFailed(conn, docId, truncateErrorMsg(e.getMessage()));
							System.err.println("  • Marked as FAILED in database");
						}
					} catch (Exception ex) {
						System.err.println("  ⚠ Could not mark document as failed in database: " + ex.getMessage());
					}
				}

				System.out.println();
			}

			// Summary
			System.out.println("=== Summary ===");
			System.out.println("Total files processed: " + processedCount);
			System.out.println("Total files skipped: " + skippedCount);
			System.out.println("Total files failed: " + failedCount);
			System.out.println("Total files encountered: " + pdfFiles.size());

		} catch (IOException e) {
			System.err.println("Error reading PDFs: " + e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * Load configuration from config.properties.
	 */
	private static Properties loadConfig() {
		Properties properties = new Properties();
		try (InputStream input = PDFinjestionApp.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
			if (input != null) {
				properties.load(input);
				return properties;
			}
		} catch (IOException e) {
			System.out.println("Warning: Could not load config.properties: " + e.getMessage());
		}
		return null;
	}

	/**
	 * Truncate error message to fit in database (255 chars limit if needed).
	 */
	private static String truncateErrorMsg(String msg) {
		if (msg == null) return "";
		if (msg.length() > 1000) {
			return msg.substring(0, 1000) + "...";
		}
		return msg;
	}

	/**
	 * Format file size in human-readable format.
	 */
	private static String formatFileSize(long bytes) {
		if (bytes <= 0) return "0 B";
		int unitIndex = 0;
		double size = bytes;
		String[] units = {"B", "KB", "MB", "GB"};
		
		while (size >= 1024 && unitIndex < units.length - 1) {
			size /= 1024;
			unitIndex++;
		}
		
		return String.format("%.2f %s", size, units[unitIndex]);
	}
}
