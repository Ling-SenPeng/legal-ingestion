package com.ingestion;

import java.io.IOException;
import java.util.List;

/**
 * Main application to read and process PDF files from a directory.
 */
public class PDFIngestionApp {

	public static void main(String[] args) {
		if (args.length == 0) {
			System.err.println("Usage: java -jar legal-ingestion-0.0.1-SNAPSHOT.jar <directory-path>");
			System.err.println("Example: java -jar legal-ingestion-0.0.1-SNAPSHOT.jar /path/to/pdf/directory");
			System.exit(1);
		}

		String directoryPath = args[0];
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
	 * Truncate text to a maximum length and add ellipsis if truncated.
	 */
	private static String truncateText(String text, int maxLength) {
		if (text.length() > maxLength) {
			return text.substring(0, maxLength) + "...";
		}
		return text;
	}
}
