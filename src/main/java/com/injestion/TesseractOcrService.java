package com.injestion;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

/**
 * TesseractOcrService wraps the tesseract command-line tool for OCR processing.
 *
 * Features:
 * - Renders PDF page to PNG using PDFBox at 300 DPI
 * - Calls tesseract CLI with ProcessBuilder
 * - Supports concurrent OCR with configurable thread pool
 * - Retries on transient failures
 * - Cleans up temporary image files
 */
public class TesseractOcrService {

	private static final String TESSERACT_CMD = "tesseract";
	private static final String OCR_LANGUAGE = "eng";
	private static final int OCR_DPI = 300;
	private static final int MAX_RETRIES = 2;
	private static final long OCR_TIMEOUT_SECONDS = 60;  // Timeout per page

	private final ExecutorService ocrExecutor;

	/**
	 * Constructor with configurable thread pool size.
	 * Default: 2 threads to avoid CPU overload.
	 *
	 * @param threadPoolSize number of concurrent OCR tasks
	 */
	public TesseractOcrService(int threadPoolSize) {
		this.ocrExecutor = Executors.newFixedThreadPool(threadPoolSize);
	}

	/**
	 * Constructor using default thread pool size (2).
	 */
	public TesseractOcrService() {
		this(2);
	}

	/**
	 * Perform OCR on a specific page in a PDF document.
	 *
	 * Process:
	 * 1. Render page to BufferedImage (300 DPI)
	 * 2. Save to temporary PNG
	 * 3. Call tesseract on the PNG
	 * 4. Return OCR text
	 * 5. Clean up temporary file
	 *
	 * @param pdfPath the path to the PDF file
	 * @param pageIndex the 0-based page index
	 * @return the OCR text, or empty string if OCR fails
	 * @throws Exception if rendering or OCR fails after retries
	 */
	public String ocrPage(String pdfPath, int pageIndex) throws Exception {
		for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
			try {
				return ocrPageInternal(pdfPath, pageIndex);
			} catch (Exception e) {
				if (attempt < MAX_RETRIES) {
					long backoffMs = 1000L * (attempt + 1);  // 1s, 2s backoff
					Thread.sleep(backoffMs);
				} else {
					throw e;
				}
			}
		}
		return "";
	}

	/**
	 * Internal OCR implementation (single attempt).
	 */
	private String ocrPageInternal(String pdfPath, int pageIndex) throws Exception {
		Path tempImagePath = null;
		try {
			// Step 1: Render page to BufferedImage
			BufferedImage image = renderPageToImage(pdfPath, pageIndex);

			// Step 2: Save to temporary PNG
			tempImagePath = Files.createTempFile("ocr_", ".png");
			ImageIO.write(image, "png", tempImagePath.toFile());

			// Step 3: Call tesseract
			String ocrText = runTesseract(tempImagePath.toString());

			return ocrText;

		} finally {
			// Step 5: Clean up temporary file
			if (tempImagePath != null && Files.exists(tempImagePath)) {
				try {
					Files.delete(tempImagePath);
				} catch (IOException e) {
					System.err.println("Warning: Failed to delete temporary image: " + tempImagePath);
				}
			}
		}
	}

	/**
	 * Render a PDF page to BufferedImage at OCR_DPI resolution.
	 *
	 * @param pdfPath the path to the PDF file
	 * @param pageIndex the 0-based page index
	 * @return the rendered image
	 * @throws IOException if rendering fails
	 */
	private BufferedImage renderPageToImage(String pdfPath, int pageIndex) throws IOException {
		try (PDDocument document = PDDocument.load(new File(pdfPath))) {
			PDFRenderer renderer = new PDFRenderer(document);
			// PDFRenderer.renderImage uses DPI; higher DPI = better quality
			// dpi parameter is approximate; 300 is typical for OCR
			return renderer.renderImage(pageIndex, OCR_DPI / 72.0f);  // Convert DPI to scale factor
		}
	}

	/**
	 * Run tesseract command via ProcessBuilder and capture output.
	 *
	 * Command: tesseract <imagePath> stdout -l eng --dpi 300
	 *
	 * @param imagePath path to the image file
	 * @return the OCR text output
	 * @throws Exception if tesseract fails or times out
	 */
	private String runTesseract(String imagePath) throws Exception {
		ProcessBuilder pb = new ProcessBuilder(
			TESSERACT_CMD,
			imagePath,
			"stdout",
			"-l", OCR_LANGUAGE,
			"--dpi", String.valueOf(OCR_DPI)
		);

		pb.redirectErrorStream(false);  // Keep stderr separate

		Process process = pb.start();

		// Use a simple pipe reader to get output
		StringBuilder output = new StringBuilder();
		try (java.io.BufferedReader reader =
			new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
			String line;
			while ((line = reader.readLine()) != null) {
				output.append(line).append("\n");
			}
		}

		// Wait for process with timeout
		boolean finished = process.waitFor(OCR_TIMEOUT_SECONDS, TimeUnit.SECONDS);

		if (!finished) {
			process.destroyForcibly();
			throw new Exception("Tesseract timed out after " + OCR_TIMEOUT_SECONDS + " seconds");
		}

		int exitCode = process.exitValue();
		if (exitCode != 0) {
			// Read stderr for error details
			StringBuilder stderr = new StringBuilder();
			try (java.io.BufferedReader reader =
				new java.io.BufferedReader(new java.io.InputStreamReader(process.getErrorStream()))) {
				String line;
				while ((line = reader.readLine()) != null) {
					stderr.append(line).append("\n");
				}
			}
			throw new Exception("Tesseract exit code " + exitCode + ": " + stderr.toString());
		}

		return output.toString().trim();
	}

	/**
	 * Check if tesseract command is available on the system.
	 *
	 * @return true if tesseract is available, false otherwise
	 */
	public static boolean isTesseractAvailable() {
		try {
			ProcessBuilder pb = new ProcessBuilder(TESSERACT_CMD, "--version");
			pb.redirectErrorStream(true);
			Process process = pb.start();
			boolean finished = process.waitFor(5, TimeUnit.SECONDS);
			return finished && process.exitValue() == 0;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Gracefully shutdown the OCR executor.
	 */
	public void shutdown() {
		ocrExecutor.shutdown();
		try {
			if (!ocrExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
				ocrExecutor.shutdownNow();
			}
		} catch (InterruptedException e) {
			ocrExecutor.shutdownNow();
		}
	}

	/**
	 * Get the executor service for async OCR tasks (optional).
	 */
	public ExecutorService getExecutor() {
		return ocrExecutor;
	}
}
