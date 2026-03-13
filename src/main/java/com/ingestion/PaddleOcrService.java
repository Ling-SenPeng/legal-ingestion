package com.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

/**
 * PaddleOcrService integrates PaddleOCR via a Python helper script.
 *
 * Features:
 * - Renders PDF page to JPEG image
 * - Calls Python subprocess (scripts/paddle_ocr_runner.py) for fast OCR
 * - Parses structured JSON output with bboxes and confidence scores
 * - Handles layout-aware text extraction
 * - Supports concurrent OCR with configurable thread pool
 * - Graceful fallback on errors (logs, doesn't crash)
 *
 * Prerequisites:
 * - Python 3 installed (in PATH)
	 * - paddleocr package installed: pip3 install paddleocr
 * - scripts/paddle_ocr_runner.py exists in project root
 *
 * JSON Output Format:
 * {
 *   "pageNumber": 1,
 *   "text": "...full normalized text...",
 *   "lines": [
 *     {
 *       "text": "line text",
 *       "bbox": [x1, y1, x2, y2],
 *       "confidence": 0.98
 *     }
 *   ]
 * }
 */
public class PaddleOcrService implements OcrProvider {

	private static final String PYTHON_CMD = "python3";
	private static final String PYTHON_SCRIPT = "scripts/paddle_ocr_runner.py";
	private static final int OCR_DPI = 200;
	private static final int MAX_RETRIES = 1;  // PaddleOCR is usually more reliable
	private static final long OCR_TIMEOUT_SECONDS = 120;  // PaddleOCR slower, but powerful

	private final ExecutorService ocrExecutor;
	private final ObjectMapper objectMapper;
	private final Path projectRoot;

	/**
	 * Constructor with configurable thread pool size.
	 *
	 * @param threadPoolSize number of concurrent OCR tasks
	 * @param projectRoot the project root directory (used to locate Python script)
	 */
	public PaddleOcrService(int threadPoolSize, Path projectRoot) {
		this.ocrExecutor = Executors.newFixedThreadPool(threadPoolSize);
		this.objectMapper = new ObjectMapper();
		this.projectRoot = projectRoot;
	}

	/**
	 * Constructor using default thread pool size (1).
	 *
	 * @param projectRoot the project root directory
	 */
	public PaddleOcrService(Path projectRoot) {
		this(1, projectRoot);
	}

	/**
	 * OcrProvider implementation: Extract a single page with PaddleOCR.
	 *
	 * @param pdfPath absolute path to the PDF file
	 * @param pageIndex 0-based page index
	 * @return OcrPage with normalized text and structured line blocks
	 * @throws Exception if OCR fails after retries
	 */
	@Override
	public OcrPage extractPage(Path pdfPath, int pageIndex) throws Exception {
		for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
			try {
				return extractPageInternal(pdfPath, pageIndex);
			} catch (Exception e) {
				if (attempt < MAX_RETRIES) {
					long backoffMs = 1000L * (attempt + 1);
					System.err.println("    [PaddleOCR] Attempt " + (attempt + 1) + " failed: " + e.getMessage() + ". Retrying in " + backoffMs + "ms...");
					Thread.sleep(backoffMs);
				} else {
					throw e;
				}
			}
		}
		throw new Exception("PaddleOCR exhausted retries");
	}

	/**
	 * Internal OCR implementation (single attempt).
	 */
	private OcrPage extractPageInternal(Path pdfPath, int pageIndex) throws Exception {
		Path tempImagePath = null;
		try {
			// Step 1: Render PDF page to JPEG image
			System.err.println("    [PaddleOCR] Page " + (pageIndex + 1) + ": Rendering PDF to image...");
			long t1 = System.currentTimeMillis();
			BufferedImage image = renderPageToImage(pdfPath.toString(), pageIndex);
			long renderTime = System.currentTimeMillis() - t1;
			System.err.println("    [PaddleOCR] Page " + (pageIndex + 1) + ": Rendered in " + renderTime + "ms");

			// Step 2: Save to temporary JPEG
			System.err.println("    [PaddleOCR] Page " + (pageIndex + 1) + ": Saving image to temp file...");
			tempImagePath = Files.createTempFile("paddle_ocr_", ".jpg");
			ImageIO.write(image, "jpg", tempImagePath.toFile());

			// Step 3: Call Python helper script
			System.err.println("    [PaddleOCR] Page " + (pageIndex + 1) + ": Running PaddleOCR (this may take 30-60 seconds)...");
			long t2 = System.currentTimeMillis();
			String jsonOutput = runPaddleOcrScript(tempImagePath.toString());
			long ocrTime = System.currentTimeMillis() - t2;
			System.err.println("    [PaddleOCR] Page " + (pageIndex + 1) + ": OCR completed in " + ocrTime + "ms");

			// Step 4: Parse JSON and create OcrPage
			System.err.println("    [PaddleOCR] Page " + (pageIndex + 1) + ": Parsing results...");
			OcrPage page = parseOcrJson(jsonOutput, pageIndex);
			System.err.println("    [PaddleOCR] Page " + (pageIndex + 1) + ": Extracted " + page.getLines().size() + " text lines");
			return page;

		} finally {
			// Step 5: Clean up temporary file
			if (tempImagePath != null && Files.exists(tempImagePath)) {
				try {
					Files.delete(tempImagePath);
				} catch (IOException e) {
					System.err.println("    [PaddleOCR] Warning: Failed to delete temporary image: " + tempImagePath);
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
			return renderer.renderImage(pageIndex, OCR_DPI / 72.0f);
		}
	}

	/**
	 * Run the Python helper script via ProcessBuilder.
	 *
	 * Command: python scripts/paddle_ocr_runner.py <imagePath>
	 *
	 * @param imagePath path to the image file
	 * @return the JSON output from the script
	 * @throws Exception if script fails or times out
	 */
	private String runPaddleOcrScript(String imagePath) throws Exception {
		// Construct path to Python script
		Path scriptPath = projectRoot.resolve(PYTHON_SCRIPT);
		if (!Files.exists(scriptPath)) {
			throw new FileNotFoundException("Python helper script not found: " + scriptPath);
		}

		ProcessBuilder pb = new ProcessBuilder(
			PYTHON_CMD,
			scriptPath.toString(),
			imagePath
		);

		pb.redirectErrorStream(false);

		Process process = pb.start();

		// Stream reading to avoid buffer overflows
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
			throw new Exception("PaddleOCR timed out after " + OCR_TIMEOUT_SECONDS + " seconds");
		}

		int exitCode = process.exitValue();
		if (exitCode != 0) {
			// Python script returned non-zero exit code - this is a fatal error
			// (non-zero means the script itself failed, not OCR failure)
			StringBuilder stderr = new StringBuilder();
			try (java.io.BufferedReader reader =
				new java.io.BufferedReader(new java.io.InputStreamReader(process.getErrorStream()))) {
				String line;
				while ((line = reader.readLine()) != null) {
					stderr.append(line).append("\n");
				}
			}
			throw new Exception("Python subprocess error (exit code " + exitCode + "): " + stderr.toString());
		}

		// Exit code 0: Script executed (may contain success or error in JSON)
		String outputStr = output.toString().trim();
		if (outputStr.isEmpty()) {
			throw new Exception("Python script returned empty output");
		}
		
		return outputStr;
	}

	/**
	 * Parse the JSON output from the Python helper script.
	 *
	 * Expected format:
	 * {
	 *   "pageNumber": 1,
	 *   "text": "...",
	 *   "lines": [
	 *     {
	 *       "text": "...",
	 *       "bbox": [x1, y1, x2, y2],
	 *       "confidence": 0.98
	 *     }
	 *   ]
	 * }
	 *
	 * Or on error:
	 * {
	 *   "error": "description of what went wrong",
	 *   "pageNumber": 1,
	 *   "text": "",
	 *   "lines": []
	 * }
	 *
	 * @param jsonOutput the JSON string from the Python script
	 * @param pageIndex the page index (if JSON is missing it)
	 * @return OcrPage object
	 * @throws Exception if JSON is malformed or contains error
	 */
	private OcrPage parseOcrJson(String jsonOutput, int pageIndex) throws Exception {
		JsonNode root = objectMapper.readTree(jsonOutput);
		
		// Check for error response
		if (root.has("error") && !root.get("error").isNull()) {
			String errorMsg = root.get("error").asText();
			throw new Exception("OCR error: " + errorMsg);
		}

		int pageNumber = root.has("pageNumber") ? root.get("pageNumber").asInt() : pageIndex + 1;
		String text = root.has("text") ? root.get("text").asText() : "";

		OcrPage page = new OcrPage(pageNumber, text, getProviderName());

		// Parse lines
		if (root.has("lines") && root.get("lines").isArray()) {
			for (JsonNode lineNode : root.get("lines")) {
				String lineText = lineNode.has("text") ? lineNode.get("text").asText() : "";
				double confidence = lineNode.has("confidence") ? lineNode.get("confidence").asDouble(0.0) : 0.0;

				// Parse bbox if present
				int[] bbox = null;
				if (lineNode.has("bbox") && lineNode.get("bbox").isArray()) {
					JsonNode bboxNode = lineNode.get("bbox");
					if (bboxNode.size() == 4) {
						bbox = new int[4];
						for (int i = 0; i < 4; i++) {
							bbox[i] = bboxNode.get(i).asInt();
						}
					}
				}

				OcrLine ocrLine = new OcrLine(lineText, bbox, confidence);
				page.addLine(ocrLine);
			}
		}

		return page;
	}

	/**
	 * OcrProvider implementation: Get provider name.
	 *
	 * @return "paddle"
	 */
	@Override
	public String getProviderName() {
		return "paddle";
	}

	/**
	 * OcrProvider implementation: Check if provider is available.
	 *
	 * Checks:
	 * - Python is available
	 * - Script file exists
	 * - paddleocr package is installed (via quick test)
	 *
	 * @return true if PaddleOCR is available, false otherwise
	 */
	@Override
	public boolean isAvailable() {
		try {
			// Check 1: Python is available
			ProcessBuilder pb = new ProcessBuilder(PYTHON_CMD, "--version");
			pb.redirectErrorStream(true);
			Process process = pb.start();
			boolean pythonAvailable = process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
			if (!pythonAvailable) {
				System.err.println("    [PaddleOCR] Python not found in PATH");
				return false;
			}

			// Check 2: Script exists
			Path scriptPath = projectRoot.resolve(PYTHON_SCRIPT);
			if (!Files.exists(scriptPath)) {
				System.err.println("    [PaddleOCR] Helper script not found: " + scriptPath);
				return false;
			}

			// Check 3: paddleocr package is installed
			ProcessBuilder pb2 = new ProcessBuilder(
				PYTHON_CMD,
				"-c",
				"import paddleocr; print('ok')"
			);
			pb2.redirectErrorStream(true);
			Process process2 = pb2.start();

			// Read all lines (warnings may come before 'ok')
			StringBuilder allOutput = new StringBuilder();
			try (java.io.BufferedReader reader =
				new java.io.BufferedReader(new java.io.InputStreamReader(process2.getInputStream()))) {
				String line;
				while ((line = reader.readLine()) != null) {
					allOutput.append(line).append(" ");
				}
			}
			process2.waitFor(10, TimeUnit.SECONDS);

			boolean paddleInstalled = allOutput.toString().contains("ok");
			if (!paddleInstalled) {
				System.err.println("    [PaddleOCR] paddleocr module not installed. Run: pip3 install paddleocr");
			}

			return paddleInstalled;

		} catch (Exception e) {
			System.err.println("    [PaddleOCR] Availability check failed: " + e.getMessage());
			return false;
		}
	}

	/**
	 * Gracefully shutdown the OCR executor.
	 */
	@Override
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
