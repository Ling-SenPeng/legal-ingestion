package com.injestion;

import java.io.InputStream;
import java.sql.DriverManager;
import java.util.Properties;

/**
 * Main application entry point supporting multiple subcommands.
 * Subcommands: injest, embed-missing, search
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
				case "injest":
					runinjestion(args);
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

	private static void runinjestion(String[] args) throws Exception {
		// Delegate to PDFinjestionApp (was the old main)
		String[] injestArgs = new String[args.length - 1];
		System.arraycopy(args, 1, injestArgs, 0, args.length - 1);
		PDFinjestionApp.main(injestArgs);
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
		String dbUrl = props.getProperty("db.url", "jdbc:postgresql://localhost:5432/legal_injestion");
		String dbUser = props.getProperty("db.user", "injestion_user");
		String dbPassword = props.getProperty("db.password", "injestion_pass");

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
			System.err.println("Usage: java -jar legal-injestion.jar search --query \"your query\" [--topK 10]");
			System.exit(1);
		}

		// Load config
		Properties props = loadConfig();
		String dbUrl = props.getProperty("db.url", "jdbc:postgresql://localhost:5432/legal_injestion");
		String dbUser = props.getProperty("db.user", "injestion_user");
		String dbPassword = props.getProperty("db.password", "injestion_pass");

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
		String dbUrl = props.getProperty("db.url", "jdbc:postgresql://localhost:5432/legal_injestion");
		String dbUser = props.getProperty("db.user", "injestion_user");
		String dbPassword = props.getProperty("db.password", "injestion_pass");

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

	private static void printUsage() {
		System.out.println("=== Legal injestion Tool ===");
		System.out.println();
		System.out.println("Usage:");
		System.out.println("  mvn exec:java -Dexec.args=\"injest [directory] [dbUrl] [dbUser] [dbPassword]\"");
		System.out.println("  mvn exec:java -Dexec.args=\"embed-missing [--limit 100] [--batchSize 50]\"");
		System.out.println("  mvn exec:java -Dexec.args=\"search --query \\\"your search\\\" [--topK 10]\"");
		System.out.println("  mvn exec:java -Dexec.args=\"hybrid-search --query \\\"your search\\\" [--topK 10] [--vectorTopN 20] [--keywordTopN 20] [--alpha 0.7]\"");
		System.out.println();
		System.out.println("Hybrid Search:");
		System.out.println("  Combines vector similarity and keyword (full-text) search for legal documents.");
		System.out.println("  --query         : Search query (required)");
		System.out.println("  --topK          : Top-K final results (default: 10)");
		System.out.println("  --vectorTopN    : Number of vector search results to fetch (default: 20)");
		System.out.println("  --keywordTopN   : Number of keyword search results to fetch (default: 20)");
		System.out.println("  --alpha         : Weight for vector score in [0,1] (default: 0.7)");
		System.out.println("                    finalScore = alpha*vectorScore + (1-alpha)*keywordScore");
		System.out.println();
		System.out.println("Environment Variables:");
		System.out.println("  OPENAI_API_KEY - Required for embed-missing, search, and hybrid-search commands");
		System.out.println();
		System.out.println("Configuration:");
		System.out.println("  See config.properties for database and application settings");
		System.out.println();
	}
}
