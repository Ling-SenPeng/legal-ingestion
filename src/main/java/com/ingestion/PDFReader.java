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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * A utility class to read and extract text from PDF files in a directory.
 * State is now managed by the database (SHA256 deduplication, status tracking).
 * 
 * Supports OCR for scanned/image-based pages:
 * - Automatically detects pages needing OCR (< 30 chars text)
 * - Uses pluggable OcrProvider (tesseract, PaddleOCR, etc.)
 * - Preserves layout metadata and confidence scores
 * - Records OCR metadata in page output
 */
public class PDFReader {

	private static final String CONFIG_FILE = "config.properties";
	private static final String DEFAULT_OCR_PROVIDER = "auto";  // auto-detect or use configured provider
	
	private OcrProvider ocrProvider;  // Pluggable OCR provider
	private String configuredProviderName;  // From config.properties

	public PDFReader() {
		this.ocrProvider = null;  // Lazy init on first OCR need
		this.configuredProviderName = loadConfiguredOcrProvider();
	}

	/**
	 * Load the configured OCR provider name from config.properties.
	 * Falls back to DEFAULT_OCR_PROVIDER if not configured.
	 *
	 * @return the configured provider name or default
	 */
	private String loadConfiguredOcrProvider() {
		try {
			InputStream in = PDFReader.class.getClassLoader().getResourceAsStream(CONFIG_FILE);
			if (in != null) {
				Properties props = new Properties();
				props.load(in);
				String provider = props.getProperty("ocr.provider", DEFAULT_OCR_PROVIDER);
				in.close();
				return provider;
			}
		} catch (IOException e) {
			System.err.println("    [Config] Could not load " + CONFIG_FILE + ": " + e.getMessage());
		}
		return DEFAULT_OCR_PROVIDER;
	}

	/**
	 * Get or initialize the OCR provider (lazy initialization).
	 * Uses OcrProviderFactory to select the best available provider.
	 * 
	 * @return the OCR provider, or null if no provider is available
	 */
	private synchronized OcrProvider getOcrProvider() {
		if (ocrProvider == null) {
			Path projectRoot = Paths.get(".");  // Use current directory as project root
			ocrProvider = OcrProviderFactory.selectProvider(configuredProviderName, projectRoot);
			
			if (ocrProvider == null) {
				System.out.println("    [OCR] WARNING: No OCR provider available. Scanned pages will not be OCR'd.");
			}
		}
		return ocrProvider;
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
	 * Get OCR provider for manual use (if needed).
	 * Backward compatibility method - now returns the OcrProvider.
	 */
	public OcrProvider getOcrProviderIfAvailable() {
		return getOcrProvider();
	}

	/**
	 * Legacy method - Get Tesseract service specifically (for backward compatibility).
	 * This will return a TesseractOcrService if the current provider is tesseract, or null otherwise.
	 *
	 * @return TesseractOcrService if available, null otherwise
	 */
	public TesseractOcrService getOcrServiceIfAvailable() {
		OcrProvider provider = getOcrProvider();
		if (provider instanceof TesseractOcrService) {
			return (TesseractOcrService) provider;
		}
		return null;
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
	 * NOTE: File size limits have been removed. Large PDFs will be processed.
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
				
				// NOTE: File size limit removed - all PDFs are processed
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
	 * Extract text from each page of a PDF file, with OCR for pages needing it.
	 *
	 * Process:
	 * 1. Extract text using PDFBox for each page
	 * 2. For each page, check if OCR is needed (using OcrDecider)
	 * 3. If yes and OCR provider available, use pluggable provider (tesseract, paddle, etc.)
	 * 4. Return PageText list with metadata (source: pdfbox/ocr)
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
					
					// Treat null as empty string
					if (pageText == null) {
						pageText = "";
					}
					
					String finalPageText = pageText;
					
					// Check if OCR is needed
					if (OcrDecider.shouldOcr(pageText)) {
						OcrProvider provider = getOcrProvider();
						if (provider != null) {
							try {
								// pageNum is 1-based, but PDFBox page index is 0-based
								int pageIndex = pageNum - 1;
								OcrPage ocrPage = provider.extractPage(Paths.get(pdfFilePath), pageIndex);
								
								if (ocrPage != null && !ocrPage.getText().trim().isEmpty()) {
									finalPageText = ocrPage.getText();
									System.out.println("    [OCR] Page " + pageNum + " processed by " + provider.getProviderName() + " (" + ocrPage.getLines().size() + " lines detected)");
								}
							} catch (Exception e) {
								System.err.println("    [OCR] Warning: OCR failed for page " + pageNum + " (" + e.getMessage() + "), using PDFBox text");
								// Keep pdfbox text as fallback
							}
						}
					}
					
					pages.add(new PageText(pageNum, finalPageText));
				} catch (Exception e) {
					// If we fail to extract a page, add empty text for that page
					System.err.println("    [PDF] Warning: Failed to extract text from page " + pageNum + " of " + pdfFilePath + ": " + e.getMessage());
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
