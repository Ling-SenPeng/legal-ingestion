package com.ingestion;

import java.io.InputStream;
import java.util.Properties;

/**
 * Main application entry point supporting multiple subcommands.
 * Subcommands: ingest, embed-missing, search
 */
public class AppMain {

	private static final String CONFIG_FILE = "config.properties";

	public static void main(String[] args) {
		// Load environment variables from .env file (if present)
		EnvLoader.load();

		if (args.length == 0) {
			printUsage();
			System.exit(1);
		}

		String command = args[0].toLowerCase();

		try {
			switch (command) {
				case "ingest":
					runIngestion(args);
					break;

				case "embed-missing":
					runEmbedMissing(args);
					break;

				case "search":
					runSearch(args);
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

	private static void runIngestion(String[] args) throws Exception {
		// Delegate to PDFIngestionApp (was the old main)
		String[] ingestArgs = new String[args.length - 1];
		System.arraycopy(args, 1, ingestArgs, 0, args.length - 1);
		PDFIngestionApp.main(ingestArgs);
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

	private static void printUsage() {
		System.out.println("=== Legal Ingestion Tool ===");
		System.out.println();
		System.out.println("Usage:");
		System.out.println("  java -jar legal-ingestion.jar ingest [directory] [dbUrl] [dbUser] [dbPassword]");
		System.out.println("  java -jar legal-ingestion.jar embed-missing [--limit 100] [--batchSize 50]");
		System.out.println("  java -jar legal-ingestion.jar search --query \"your search\" [--topK 10]");
		System.out.println();
		System.out.println("Environment Variables:");
		System.out.println("  OPENAI_API_KEY - Required for embed-missing and search commands");
		System.out.println();
		System.out.println("Configuration:");
		System.out.println("  See config.properties for database and application settings");
		System.out.println();
	}
}
