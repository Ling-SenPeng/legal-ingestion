import com.injestion.PDFReader;
import com.injestion.TesseractOcrService;

import java.util.List;
import java.util.Scanner;

/**
 * Debug test runner for MC-30-1.pdf OCR pipeline.
 * 
 * Run with: javac -cp target/classes:target/lib/* OcrDebugTest.java && \
 *           java -cp target/classes:target/lib/*:. OcrDebugTest
 */
public class OcrDebugTest {
    public static void main(String[] args) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("OCR PIPELINE DEBUG TEST - MC-30-1.pdf");
        System.out.println("=".repeat(80) + "\n");

        // Step 1: Check tesseract availability
        System.out.println("\n[STEP 1] Checking Tesseract availability...");
        boolean tesseractAvailable = TesseractOcrService.isTesseractAvailable();
        if (!tesseractAvailable) {
            System.err.println("ERROR: Tesseract is not available on this system!");
            System.err.println("Please install Tesseract OCR: https://github.com/UB-Mannheim/tesseract/wiki");
            System.exit(1);
        }

        // Step 2: Find MC-30-1.pdf
        System.out.println("\n[STEP 2] Looking for MC-30-1.pdf...");
        String pdfPath = findMC30PDF();
        if (pdfPath == null) {
            System.err.println("ERROR: MC-30-1.pdf not found in workspace!");
            System.err.println("Please ensure MC-30-1.pdf exists in a test or samples directory.");
            System.exit(1);
        }
        System.out.println("Found: " + pdfPath);

        // Step 3: Extract pages with OCR
        System.out.println("\n[STEP 3] Processing PDF pages...");
        try {
            PDFReader reader = new PDFReader();
            List<PDFReader.PageText> pages = reader.extractPages(pdfPath);
            
            System.out.println("\n[RESULT] Extracted " + pages.size() + " pages");
            for (PDFReader.PageText page : pages) {
                System.out.println("  Page " + page.pageNo + ": " + page.text.length() + " characters");
            }
            
            System.out.println("\n" + "=".repeat(80));
            System.out.println("DEBUG TEST COMPLETE");
            System.out.println("=".repeat(80) + "\n");

        } catch (Exception e) {
            System.err.println("ERROR during OCR processing: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static String findMC30PDF() {
        // Common locations to search
        String[] searchPaths = {
            "MC-30-1.pdf",
            "test/MC-30-1.pdf",
            "src/test/resources/MC-30-1.pdf",
            "samples/MC-30-1.pdf",
            "/tmp/MC-30-1.pdf"
        };

        for (String path : searchPaths) {
            java.nio.file.Path p = java.nio.file.Paths.get(path);
            if (java.nio.file.Files.exists(p)) {
                return p.toAbsolutePath().toString();
            }
        }

        // Ask user for path
        System.out.print("Enter full path to MC-30-1.pdf: ");
        Scanner scanner = new Scanner(System.in);
        String userPath = scanner.nextLine().trim();
        if (java.nio.file.Files.exists(java.nio.file.Paths.get(userPath))) {
            return userPath;
        }

        return null;
    }
}
