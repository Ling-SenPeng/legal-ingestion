package com.injestion;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple .env file loader for reading environment variables from .env file.
 * Supports both .env file and system environment variables (system takes precedence).
 */
public class EnvLoader {

	private static final Map<String, String> envMap = new HashMap<>();
	private static boolean loaded = false;

	/**
	 * Load environment variables from .env file and system environment.
	 * System environment variables take precedence over .env file.
	 */
	public static void load() {
		if (loaded) {
			return;
		}

		// First load from .env file (if it exists)
		File envFile = Paths.get(".env").toFile();
		if (envFile.exists()) {
			try (BufferedReader reader = new BufferedReader(new FileReader(envFile))) {
				String line;
				while ((line = reader.readLine()) != null) {
					line = line.trim();
					// Skip comments and empty lines
					if (line.isEmpty() || line.startsWith("#")) {
						continue;
					}
					// Parse KEY=VALUE
					int equalsIndex = line.indexOf('=');
					if (equalsIndex > 0) {
						String key = line.substring(0, equalsIndex).trim();
						String value = line.substring(equalsIndex + 1).trim();
						// Remove quotes if present
						if (value.startsWith("\"") && value.endsWith("\"")) {
							value = value.substring(1, value.length() - 1);
						}
						envMap.put(key, value);
					}
				}
			} catch (IOException e) {
				System.err.println("Warning: Could not read .env file: " + e.getMessage());
			}
		}

		loaded = true;
	}

	/**
	 * Get an environment variable.
	 * System environment takes precedence, falls back to .env file.
	 *
	 * @param key the environment variable name
	 * @return the value, or null if not found
	 */
	public static String get(String key) {
		load();
		// System environment takes precedence
		String systemValue = System.getenv(key);
		if (systemValue != null) {
			return systemValue;
		}
		// Fall back to .env file
		return envMap.get(key);
	}

	/**
	 * Get an environment variable with a default value.
	 *
	 * @param key the environment variable name
	 * @param defaultValue the default value if not found
	 * @return the value or defaultValue if not found
	 */
	public static String get(String key, String defaultValue) {
		String value = get(key);
		return value != null ? value : defaultValue;
	}

	/**
	 * Check if an environment variable exists.
	 *
	 * @param key the environment variable name
	 * @return true if it exists, false otherwise
	 */
	public static boolean has(String key) {
		load();
		return System.getenv(key) != null || envMap.containsKey(key);
	}
}
