package com.ingestion;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * A utility class to read and extract text from PDF files in a directory.
 */
public class PDFReader {

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
				String text = extractTextFromPdf(pdfFile.toString());
				PDFInfo info = new PDFInfo(
					pdfFile.getFileName().toString(),
					pdfFile.toString(),
					text.length(),
					text.substring(0, Math.min(100, text.length())) // First 100 chars
				);
				pdfInfoList.add(info);
			} catch (IOException e) {
				System.err.println("Error reading PDF: " + pdfFile + " - " + e.getMessage());
			}
		}

		return pdfInfoList;
	}

	/**
	 * A simple data class to hold PDF information.
	 */
	public static class PDFInfo {
		public final String fileName;
		public final String filePath;
		public final int textLength;
		public final String preview;

		public PDFInfo(String fileName, String filePath, int textLength, String preview) {
			this.fileName = fileName;
			this.filePath = filePath;
			this.textLength = textLength;
			this.preview = preview;
		}

		@Override
		public String toString() {
			return "PDFInfo{" +
				"fileName='" + fileName + '\'' +
				", filePath='" + filePath + '\'' +
				", textLength=" + textLength +
				", preview='" + preview + '\'' +
				'}';
		}
	}
}
