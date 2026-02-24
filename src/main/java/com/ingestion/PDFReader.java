package com.ingestion;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * A utility class to read and extract text from PDF files in a directory.
 * State is now managed by the database (SHA256 deduplication, status tracking).
 */
public class PDFReader {

	private static final String CONFIG_FILE = "config.properties";
	private static final long DEFAULT_MAX_FILE_SIZE = 1024 * 1024; // 1 MB fallback
	private static final long MAX_FILE_SIZE = loadMaxFileSize();

	public PDFReader() {
	}

	/**
	 * Load the maximum file size from config.properties.
	 *
	 * @return the maximum file size in bytes from config, or default if not found
	 */
	private static long loadMaxFileSize() {
		Properties properties = new Properties();
		try (InputStream input = PDFReader.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
			if (input == null) {
				System.out.println("Warning: " + CONFIG_FILE + " not found. Using default max file size.");
				return DEFAULT_MAX_FILE_SIZE;
			}
			properties.load(input);
			String maxSizeStr = properties.getProperty("max.file.size");
			if (maxSizeStr != null && !maxSizeStr.trim().isEmpty()) {
				try {
					long maxSize = Long.parseLong(maxSizeStr);
					System.out.println("Loaded max file size from config: " + formatFileSize(maxSize));
					return maxSize;
				} catch (NumberFormatException e) {
					System.err.println("Invalid max.file.size value: " + maxSizeStr + ". Using default.");
				}
			}
		} catch (IOException e) {
			System.err.println("Error loading config file: " + e.getMessage());
		}
		System.out.println("Using default max file size: " + formatFileSize(DEFAULT_MAX_FILE_SIZE));
		return DEFAULT_MAX_FILE_SIZE;
	}

	/**
	 * Finds all PDF files in the given directory.
	 *
	 * @param directoryPath the path to the directory
	 * @return a list of PDF file paths
	 * @throws IOException if an error occurs while reading the directory
	 */
	public List<Path> findPDFFiles(String directoryPath) throws IOException {
		List<Path> pdfFiles = new ArrayList<>();
		Path directory = Paths.get(directoryPath);

		if (!Files.isDirectory(directory)) {
			throw new IllegalArgumentException("Path is not a directory: " + directoryPath);
		}

		Files.walk(directory)
			.filter(Files::isRegularFile)
			.filter(path -> path.toString().toLowerCase().endsWith(".pdf"))
			.forEach(pdfFiles::add);

		return pdfFiles;
	}

	/**
	 * Extracts text from a single PDF file.
	 *
	 * @param pdfFilePath the path to the PDF file
	 * @return the extracted text
	 * @throws IOException if an error occurs while reading the PDF
	 */
	public String extractTextFromPdf(String pdfFilePath) throws IOException {
		try (PDDocument document = PDDocument.load(new File(pdfFilePath))) {
			PDFTextStripper stripper = new PDFTextStripper();
			return stripper.getText(document);
		}
	}

	/**
	 * Extracts text and basic information from all PDF files in a directory.
	 * Only processes files smaller than 1MB.
	 *
	 * @param directoryPath the path to the directory
	 * @return a list of PDF information objects
	 * @throws IOException if an error occurs while reading the directory or PDF files
	 */
	public List<PDFInfo> readAllPdfsFromDirectory(String directoryPath) throws IOException {
		List<PDFInfo> pdfInfoList = new ArrayList<>();
		List<Path> pdfFiles = findPDFFiles(directoryPath);

		for (Path pdfFile : pdfFiles) {
			try {
				String filePath = pdfFile.toString();
				long fileSize = Files.size(pdfFile);
				
				if (fileSize > MAX_FILE_SIZE) {
					System.out.println("Skipping file (size > 1MB): " + pdfFile.getFileName() + " (" + formatFileSize(fileSize) + ")");
					continue;
				}
				
				String text = extractTextFromPdf(filePath);
				PDFInfo info = new PDFInfo(
					pdfFile.getFileName().toString(),
					filePath,
					text.length(),
					text.substring(0, Math.min(100, text.length())),
					fileSize
				);
				pdfInfoList.add(info);
			} catch (IOException e) {
				System.err.println("Error reading PDF: " + pdfFile + " - " + e.getMessage());
			}
		}

		return pdfInfoList;
	}

	/**
	 * Format file size in human-readable format (B, KB, MB, GB).
	 *
	 * @param bytes the file size in bytes
	 * @return formatted file size string
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

	/**
	 * Extract text from each page of a PDF file.
	 *
	 * @param pdfFilePath the path to the PDF file
	 * @return a list of PageText objects, one per page (1-based page numbering)
	 * @throws IOException if an error occurs while reading the PDF
	 */
	public List<PageText> extractPages(String pdfFilePath) throws IOException {
		List<PageText> pages = new ArrayList<>();
		try (PDDocument document = PDDocument.load(new File(pdfFilePath))) {
			int pageCount = document.getNumberOfPages();
			
			for (int pageNum = 1; pageNum <= pageCount; pageNum++) {
				try {
					PDFTextStripper stripper = new PDFTextStripper();
					stripper.setStartPage(pageNum);
					stripper.setEndPage(pageNum);
					String pageText = stripper.getText(document);
					
					// Treat null or empty text as empty string
					if (pageText == null) {
						pageText = "";
					}
					
					pages.add(new PageText(pageNum, pageText));
				} catch (Exception e) {
					// If we fail to extract a page, add empty text for that page
					System.err.println("Warning: Failed to extract text from page " + pageNum + " of " + pdfFilePath + ": " + e.getMessage());
					pages.add(new PageText(pageNum, ""));
				}
			}
		}
		return pages;
	}

	/**
	 * A simple data class to hold PDF information.
	 */
	public static class PDFInfo {
		public final String fileName;
		public final String filePath;
		public final int textLength;
		public final String preview;
		public final long fileSize;

		public PDFInfo(String fileName, String filePath, int textLength, String preview, long fileSize) {
			this.fileName = fileName;
			this.filePath = filePath;
			this.textLength = textLength;
			this.preview = preview;
			this.fileSize = fileSize;
		}

		@Override
		public String toString() {
			return "PDFInfo{" +
				"fileName='" + fileName + '\'' +
				", filePath='" + filePath + '\'' +
				", textLength=" + textLength +
				", fileSize=" + fileSize +
				", preview='" + preview + '\'' +
				'}';
		}
	}

	/**
	 * Data class to hold a page's text with citation (1-based page number).
	 */
	public static class PageText {
		public final int pageNo;  // 1-based page number for legal citation
		public final String text;

		public PageText(int pageNo, String text) {
			this.pageNo = pageNo;
			this.text = text != null ? text : "";
		}

		@Override
		public String toString() {
			return "PageText{" +
				"pageNo=" + pageNo +
				", textLength=" + text.length() +
				'}';
		}
	}
}
