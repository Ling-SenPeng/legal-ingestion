# OCR Pipeline - Debug Reference Guide

**Status:** ✅ Production Ready (Debug code removed from source)

This guide documents how to troubleshoot the OCR pipeline if issues are encountered in the future.

> **Note:** Debug code has been removed from the main source files for production. This guide serves as a reference for re-enabling debugging if needed.

---

## Prerequisites & System Requirements

### Tesseract OCR Engine

The OCR pipeline requires Tesseract to be installed and accessible on your system.

**Installation Instructions:**

| OS | Command |
|---|---|
| **macOS** | `brew install tesseract` |
| **Ubuntu/Debian** | `sudo apt-get install tesseract-ocr` |
| **Windows** | Install from https://github.com/UB-Mannheim/tesseract/wiki |
| **Other** | Consult https://github.com/UB-Mannheim/tesseract/wiki |

**Verify Installation:**
```bash
tesseract --version
# Output should show version 5.x.x
```

### Common Installation Issues

**❌ Error: Cannot run program "tesseract"**
```
IOException: Cannot run program "tesseract": Exec failed, error: 2 (No such file or directory)
```
**Solution:** Tesseract is not installed or not in system PATH. Follow installation instructions above.

**❌ Error: tesseract: command not found**
**Solution:** Tesseract is installed but not in PATH. Verify installation with `which tesseract`.

### Optional: JBIG2 Image Support

**⚠️ Warning: Cannot read JBIG2 image: jbig2-imageio is not installed**

This warning appears when processing PDFs with JBIG2 compression format. It is **non-critical** - the pipeline will:
- Continue processing other images in the PDF
- Fall back to OCR if text extraction fails
- Produce valid output despite the warning

#### Why You See This
- JBIG2 is a specialized compression format used in some scanned PDFs
- The jbig2-imageio ImageIO plugin is optional and not bundled with PDFBox
- PDFBox will log a warning but continue processing normally

#### Suppressing the Warning ✅

The project has logging configuration files that automatically suppress this warning:

**Using the built JAR (Recommended):**
```bash
java -jar target/legal-injestion-0.0.1-SNAPSHOT.jar
# JBIG2 warning will NOT appear
```

**Logging configuration files included:**
- `src/main/resources/logback.xml` - Logback configuration (if using Logback)
- `src/main/resources/logging.properties` - Java Util Logging configuration

**In your IDE (Eclipse/IntelliJ):**
- Configure to use the logging configuration from `src/main/resources/logback.xml`
- Refer to your IDE's logging configuration documentation

#### Advanced: Adding JBIG2 Support (Optional)

If you need actual JBIG2 image processing (not just suppressing warnings):
```bash
# This library needs external installation
# Download from: https://github.com/levigo/imageio-jbig2
# Or find a Maven repository that hosts com.levigo.jbig2:levigo-jbig2-imageio
```

Note: JBIG2 licensing restrictions mean it's often not available in standard Maven repositories.

---

## Quick Start: Enabling Debug Output

If you need to troubleshoot the OCR pipeline, you can re-enable detailed logging by adding the debug prints back to:

## Debug Output Sections

### 1. PDFReader - Main Orchestration Layer
```
  [PDFReader] extractPages() starting for: /path/to/MC-30-1.pdf
  [PDFReader] PDF loaded, total pages: N
  [PDFReader] >>>>>>> PAGE M/N <<<<<<<  (highlighted for MC-30-1.pdf)
```

**What this tells you:**
- File is being loaded successfully
- Total page count detected  
- Each page processing is clearly marked

### 2. PDFBox Text Extraction
```
  [PDFReader] PDFBox extracted NNN characters
  [PDFReader] First 100 chars: [sample text...]
```

**What this tells you:**
- How many characters PDFBox extracted
- Sample of the actual text extracted (first 100 chars)

### 3. OcrDecider - OCR Decision Logic
```
      [OcrDecider] text length=N (threshold=30) → OCR needed
      [OcrDecider] text length=N (threshold=30) → skip OCR
```

**What this tells you:**
- Why OCR decision was made (text too short or null)
- The threshold (30 chars) and actual character count
- Whether OCR will be invoked for this page

### 4. OCR Service Initialization
```
  [Check Tesseract] Running: tesseract --version
  [Check Tesseract] ✓ Available (exit code 0)
  [OCR] Tesseract initialized with 2 threads
```

**What this tells you:**
- Tesseract availability check at startup
- Thread pool size for concurrent OCR
- If initialization failed: "WARNING: Tesseract not available..."

### 5. OCR Invocation
```
  [PDFReader] >>> Invoking OCR service for page 1 (0-based index=0)...
  [OCR] Starting OCR for pageIndex=0 (0-based), DPI=300
```

**What this tells you:**
- OCR is being called for specific page
- 0-based page indexing used internally (but display is 1-based)
- DPI setting (300 DPI for OCR quality)

### 6. PDF Page Rendering
```
        [Render] Loading PDF: /path/to/MC-30-1.pdf
        [Render] PDF loaded, pages=N, rendering page 0 (0-based)
        [Render] Scale factor: 4.166667 (DPI=300/72.0)
        [Render] OK: Image 2550x3300 pixels, type=1
```

**What this tells you:**
- PDF being loaded for rendering
- Scale factor calculation (DPI conversion)
- Final image dimensions and type (1 = RGB)

**Common issues:**
- "Page index X out of bounds" → Page number incorrect
- Image very small → DPI/scale factor too low

### 7. Temporary Image File Creation
```
  [OCR] Step 2: Saving temporary PNG...
  [OCR] Step 2 OK: Saved to /tmp/ocr_XXXX.png (writeSuccess=true, size=500000 bytes)
```

**What this tells you:**
- PNG file created successfully
- File size indicates image quality (larger = more detail)
- writeSuccess=true confirms ImageIO write succeeded

### 8. Tesseract Execution
```
        [Tesseract] Building command...
        [Tesseract] Command: tesseract /tmp/ocr_XXXX.png stdout -l eng --dpi 300
        [Tesseract] Image: /tmp/ocr_XXXX.png (exists=true, size=500000 bytes)
        [Tesseract] Starting process...
        [Tesseract] Process started, PID=12345
        [Tesseract] Read 50 lines from stdout
        [Tesseract] Process exited with code: 0
        [Tesseract] SUCCESS: Got 5000 characters from OCR
```

**What this tells you:**
- Exact tesseract command being executed
- Image file verification (exists=true, correct size)
- Process started successfully and completed
- Exit code 0 = success, non-zero = failure
- Number of lines/characters extracted

**Common issues:**
- "Process exited with code: 1" → Tesseract failed (check stderr)
- "TIMEOUT: Process did not complete in 60s" → Tesseract hung (likely bad image)
- "0 characters from OCR" → Tesseract couldn't read the image

### 9. Cleanup
```
  [OCR] Cleaned up temp image: /tmp/ocr_XXXX.png (was 500000 bytes)
```

**What this tells you:**
- Temporary PNG file cleaned up successfully
- File size for reference

### 10. Final Results
```
  [PDFReader] >>> Page 1 complete: source=ocr, finalLength=5000
  [PDFReader] extractPages() complete. Total pages processed: 10
```

**What this tells you:**
- Source: "ocr" (OCR was used) or "pdfbox" (native text extraction)
- Final character count for the page
- Total pages processed successfully

---

## Running the Debug Test

### Option 1: Run the helper script
```bash
chmod +x run_ocr_debug.sh
./run_ocr_debug.sh
```

When prompted, provide the path to MC-30-1.pdf.

### Option 2: Run manually
```bash
# Build project
mvn clean package -q

# Compile debug test
javac -cp "target/classes:target/legal-injestion-0.0.1-SNAPSHOT.jar" OcrDebugTest.java

# Run with your PDF
java -cp "target/classes:target/legal-injestion-0.0.1-SNAPSHOT.jar:." OcrDebugTest
```

---

## Interpreting Results

### Scenario 1: Successful OCR
```
[PDFReader] PDFBox extracted 20 characters
      [OcrDecider] text length=20 (threshold=30) → OCR needed
  [PDFReader] >>> Invoking OCR service for page 1 (0-based index=0)...
  [OCR] Step 1 OK: Image rendered 2550x3300 pixels
  [Tesseract] SUCCESS: Got 5000 characters from OCR
  [PDFReader] >>> ✓ OCR succeeded, extracted 5000 characters
  [PDFReader] >>> Page 1 complete: source=ocr, finalLength=5000
```

✅ **Interpretation:** OCR worked perfectly. Extracted 5000 chars from image that had only 20 chars of PDFBox text.

### Scenario 2: OCR Not Needed
```
[PDFReader] PDFBox extracted 500 characters
      [OcrDecider] text length=500 (threshold=30) → skip OCR
  [PDFReader] OCR not needed for page 1 (using PDFBox)
  [PDFReader] Page 1 complete: source=pdfbox, finalLength=500
```

✅ **Interpretation:** Page had enough readable text from PDFBox. OCR skipped to save processing time.

### Scenario 3: Tesseract Not Available
```
  [Check Tesseract] ✗ Exception: Exception: [error message]
  [PDFReader] ✗ OCR service not available, using PDFBox fallback
```

❌ **Problem:** Tesseract not installed
**Solution:** `brew install tesseract` (macOS) or Install from https://github.com/UB-Mannheim/tesseract/

### Scenario 4: OCR Timeout
```
        [Tesseract] TIMEOUT: Process did not complete in 60s
```

❌ **Problem:** Tesseract took too long
**Likely causes:**
- Image too large or complex
- System under heavy load
- Tesseract bug

### Scenario 5: Tesseract Crashed
```
        [Tesseract] Process exited with code: 1
        [Tesseract] [error details from stderr]
```

❌ **Problem:** Tesseract failed to process the image
**Common causes:**
- Corrupted image file
- Invalid PDF page
- Missing language data

---

## Key Metrics to Watch

| Metric | Good Range | Problem Range |
|--------|-----------|---|
| Image dimensions | 1500x2000 to 3500x4500 | < 500px or > 5000px |
| Image file size | 100KB - 5MB | > 10MB (likely corrupted) |
| PDFBox text extraction | > 30 chars OR = 0 (image scan) | 1-29 chars (ambiguous) |
| Tesseract lines extracted | > 10 | 0 (no text found) |
| Tesseract exit code | 0 | non-zero (failure) |
| Tesseract time | < 30 seconds | > 60 seconds (timeout) |

---

## Special Highlighting for MC-30-1.pdf

When processing "MC-30-1.pdf", the output includes special markers:

```
════════════════════════════════════════════════════════════════════════════════
DEBUG: MC-30-1.pdf detected - detailed logging enabled
════════════════════════════════════════════════════════════════════════════════

  [PDFReader] >>>>>>> PAGE 1/10 <<<<<<<  (arrows instead of equals)
  [PDFReader] >>> OCR is needed for page 1
  [PDFReader] >>> Invoking OCR service...
  [PDFReader] >>> ✓ OCR succeeded, extracted 5000 characters
  [PDFReader] >>> Page 1 complete: source=ocr, finalLength=5000
```

This makes it easy to visually track MC-30-1.pdf processing in the output.

---

## Next Steps

1. **Prepare MC-30-1.pdf:** Place it in the project root or have path ready
2. **Run debug test:** Use `run_ocr_debug.sh` or manual commands above
3. **Collect full output:** Redirect to file for analysis: `... | tee debug_output.log`
4. **Share results:** If issues found, share the output log for detailed diagnosis

