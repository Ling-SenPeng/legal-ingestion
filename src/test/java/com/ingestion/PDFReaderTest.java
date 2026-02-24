package com.ingestion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PDFReaderTest {

	private PDFReader pdfReader;
	private String testDirectory;

	@BeforeEach
	void setUp() throws IOException {
		pdfReader = new PDFReader();
		// Create a temporary test directory
		testDirectory = Files.createTempDirectory("pdf-test-").toString();
	}

	@Test
	void testFindPdfFilesInNonExistentDirectory() {
		assertThrows(IllegalArgumentException.class, () -> {
			pdfReader.findPDFFiles("/nonexistent/directory");
		});
	}

	@Test
	void testFindPdfFilesInEmptyDirectory() throws IOException {
		List<Path> pdfFiles = pdfReader.findPDFFiles(testDirectory);
		assertTrue(pdfFiles.isEmpty(), "Should return empty list when no PDFs exist");
	}

	@Test
	void testPDFInfoToString() {
		PDFReader.PDFInfo info = new PDFReader.PDFInfo(
			"test.pdf",
			"/path/to/test.pdf",
			100,
			"This is a preview"
		);

		assertNotNull(info.toString());
		assertTrue(info.toString().contains("test.pdf"));
		assertEquals("test.pdf", info.fileName);
		assertEquals(100, info.textLength);
	}

	@Test
	void testPDFReaderInstantiation() {
		assertNotNull(pdfReader);
	}
}
