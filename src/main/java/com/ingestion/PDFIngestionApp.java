package com.ingestion;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;

/**
 * Main application to read and process PDF files from a directory.
 */
public class PDFIngestionApp {

	private static final String CONFIG_FILE = "config.properties";
	private static final String DEFAULT_DIRECTORY = "/Users/ling-senpeng/Documents/divorce 2026";

	public static void main(String[] args) {
		String directoryPath = (args.length > 0) ? args[0] : loadDirectoryFromConfig();
		PDFReader pdfReader = new PDFReader();

		try {
			System.out.println("Reading PDF files from: " + directoryPath);
			List<PDFReader.PDFInfo> pdfInfoList = pdfReader.readAllPdfsFromDirectory(directoryPath);

			if (pdfInfoList.isEmpty()) {
				System.out.println("No PDF files found in the directory.");
				return;
			}

			System.out.println("\nFound " + pdfInfoList.size() + " PDF file(s):\n");
			for (int i = 0; i < pdfInfoList.size(); i++) {
				PDFReader.PDFInfo info = pdfInfoList.get(i);
				System.out.println("--- PDF #" + (i + 1) + " ---");
				System.out.println("File Name: " + info.fileName);
				System.out.println("File Path: " + info.filePath);
				System.out.println("Text Length: " + info.textLength + " characters");
				System.out.println("Preview: " + truncateText(info.preview, 80));
				System.out.println();
			}

		} catch (IOException e) {
			System.err.println("Error reading PDFs: " + e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * Load the PDF directory from the config.properties file.
	 *
	 * @return the directory path from config, or default if not found
	 */
	private static String loadDirectoryFromConfig() {
		Properties properties = new Properties();
		try (InputStream input = PDFIngestionApp.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
			if (input == null) {
				System.out.println("Warning: " + CONFIG_FILE + " not found. Using default directory.");
				return DEFAULT_DIRECTORY;
			}
			properties.load(input);
			String directory = properties.getProperty("pdf.ingestion.directory");
			if (directory != null && !directory.trim().isEmpty()) {
				return directory;
			}
		} catch (IOException e) {
			System.err.println("Error loading config file: " + e.getMessage());
		}
		System.out.println("Using default directory from fallback.");
		return DEFAULT_DIRECTORY;
	}

	/**
	 * Truncate text to a maximum length and add ellipsis if truncated.
	 */
	private static String truncateText(String text, int maxLength) {
		if (text.length() > maxLength) {
			return text.substring(0, maxLength) + "...";
		}
		return text;
	}
}
