package com.ingestion;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

/**
 * Main application to ingest PDF files: extract text by page, calculate SHA256,
 * and persist to PostgreSQL database with page-level chunking (citation-aware).
 *
 * Usage:
 *   mvn compile exec:java -Dexec.mainClass="com.ingestion.PDFIngestionApp"
 *   mvn compile exec:java -Dexec.mainClass="com.ingestion.PDFIngestionApp" \
 *       -Dexec.args="/path/to/pdfs jdbc:postgresql://localhost:5432/db user pass"
 */
public class PDFIngestionApp {

	private static final String CONFIG_FILE = "config.properties";
	private static final String DEFAULT_DIRECTORY = "/Users/ling-senpeng/Documents/divorce 2026";
	private static final String DEFAULT_DB_URL = "jdbc:postgresql://localhost:5432/legal_ingestion";
	private static final String DEFAULT_DB_USER = "ingestion_user";
	private static final String DEFAULT_DB_PASSWORD = "ingestion_pass";

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
				directoryPath = props.getProperty("pdf.ingestion.directory", directoryPath);
			}
			dbUrl = props.getProperty("db.url", dbUrl);
			dbUser = props.getProperty("db.user", dbUser);
			dbPassword = props.getProperty("db.password", dbPassword);
		}

		System.out.println("=== PDF Ingestion Pipeline (MVP Level 1) ===");
		System.out.println("Input Directory: " + directoryPath);
		System.out.println("Database URL: " + dbUrl);
		System.out.println();

		PDFReader pdfReader = new PDFReader();
		DocumentRepo documentRepo = new DocumentRepo(dbUrl, dbUser, dbPassword);
		ChunkRepo chunkRepo = new ChunkRepo(dbUrl, dbUser, dbPassword);

		try {
			// Discover all PDF files
			List<Path> pdfFiles = pdfReader.findPDFFiles(directoryPath);
			System.out.println("Found " + pdfFiles.size() + " PDF file(s) in " + directoryPath);
			System.out.println();

			if (pdfFiles.isEmpty()) {
				System.out.println("No PDF files found.");
				return;
			}

			// Filter files > 1MB and process each
			int processedCount = 0;
			int skippedCount = 0;
			int failedCount = 0;

			for (Path pdfPath : pdfFiles) {
				String filePath = pdfPath.toString();
				String fileName = pdfPath.getFileName().toString();
				long fileSize = Files.size(pdfPath);

				System.out.println("[" + (processedCount + skippedCount + failedCount + 1) + "] Processing: " + fileName);

				try {
					// Check file size
					long maxFileSize = loadMaxFileSize(props);
					if (fileSize > maxFileSize) {
						System.out.println("  ⊘ Skipped: file size (" + formatFileSize(fileSize) + ") > limit (" + formatFileSize(maxFileSize) + ")");
						skippedCount++;
						continue;
					}

					// Calculate SHA256 hash
					System.out.println("  • Computing SHA256...");
					String sha256 = Sha256Hasher.computeHash(filePath);

					// Upsert document and get ID
					System.out.println("  • Upserting document record...");
					long docId = documentRepo.upsertAndGetId(fileName, filePath, sha256, fileSize);
					System.out.println("  • Document ID: " + docId);

					// Extract pages
					System.out.println("  • Extracting text by page...");
					List<PDFReader.PageText> pages = pdfReader.extractPages(filePath);
					System.out.println("  • Pages extracted: " + pages.size());

					// Insert chunks with citations
					System.out.println("  • Storing chunks with page citations...");
					chunkRepo.insertPageChunks(docId, pages);

					// Mark as done
					documentRepo.markDone(docId);
					System.out.println("  ✓ Success: " + pages.size() + " page(s) ingested");
					processedCount++;

				} catch (Exception e) {
					System.err.println("  ✗ Error: " + e.getMessage());
					e.printStackTrace();
					
					// Try to mark as failed in database
					try {
						long docId = documentRepo.upsertAndGetId(fileName, filePath, 
							Sha256Hasher.computeHash(filePath), fileSize);
						documentRepo.markFailed(docId, e.getMessage());
					} catch (Exception ex) {
						System.err.println("  ⚠ Could not mark document as failed in database: " + ex.getMessage());
					}
					
					failedCount++;
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
		try (InputStream input = PDFIngestionApp.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
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
	 * Load maximum file size from config or return default.
	 */
	private static long loadMaxFileSize(Properties props) {
		if (props != null) {
			String maxSizeStr = props.getProperty("max.file.size");
			if (maxSizeStr != null && !maxSizeStr.trim().isEmpty()) {
				try {
					return Long.parseLong(maxSizeStr);
				} catch (NumberFormatException e) {
					System.out.println("Warning: Invalid max.file.size: " + maxSizeStr);
				}
			}
		}
		return 1024 * 1024;  // 1MB default
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
