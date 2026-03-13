# PaddleOCR Integration - Verification & Testing Guide

## Checklist: Verify the Integration

### 1. Code Structure
- [x] `OcrProvider.java` - Interface for all OCR backends
- [x] `OcrPage.java` - Structured page output (text + layout)
- [x] `OcrLine.java` - Line-level blocks with bboxes & confidence
- [x] `OcrProviderFactory.java` - Runtime provider selection
- [x] `PaddleOcrService.java` - PaddleOCR implementation
- [x] `TesseractOcrService.java` - Refactored to implement OcrProvider
- [x] `PDFReader.java` - Updated to use OcrProvider interface
- [x] `scripts/paddle_ocr_runner.py` - Python helper for PaddleOCR
- [x] `config.properties` - Added `ocr.provider` setting

### 2. Compilation

```bash
mvn clean compile
# Expected: BUILD SUCCESS
```

### 3. Test Installation

#### Test 1: Verify Python Setup
```bash
python3 --version
python3 -c "from paddleocr import PaddleOCR; print('PaddleOCR OK')"
```

Expected output:
```
Python 3.x.x
PaddleOCR OK
```

#### Test 2: Verify Python Helper Script
```bash
# Create a test image or use an existing one
python3 scripts/paddle_ocr_runner.py /path/to/test/image.jpg

# Expected output: JSON with pages, text, and lines
# Example:
{
  "pageNumber": 1,
  "text": "Detected text from image",
  "lines": [
    {
      "text": "Line 1",
      "bbox": [100, 200, 300, 250],
      "confidence": 0.95
    }
  ]
}
```

#### Test 3: Verify Provider Factory (Manual)

Create a simple test file:

```java
// TestOcrFactory.java
import java.nio.file.Paths;

public class TestOcrFactory {
    public static void main(String[] args) {
        OcrProvider provider = OcrProviderFactory.selectProvider("auto");
        if (provider != null) {
            System.out.println("Selected provider: " + provider.getProviderName());
            System.out.println("Available: " + provider.isAvailable());
        } else {
            System.out.println("No OCR provider available");
        }
    }
}
```

Run: 
```bash
mvn compile exec:java -Dexec.mainClass="TestOcrFactory"
```

#### Test 4: Verify Integration with PDFReader

```bash
# Create a test PDF or use an existing scanned document

# Run the ingest command (it will try to OCR scanned pages automatically)
mvn compile exec:java -Dexec.mainClass="com.ingestion.AppMain" \
    -Dexec.args="ingest /path/to/test/pdfs"
```

Watch the output for OCR logs:
```
[OCR] Selected: PaddleOCR
[OCR] Page 1 processed by paddle (45 lines detected)
[OCR] Page 2 processed by paddle (32 lines detected)
```

### 4. Configuration Testing

#### Test with Force-PaddleOCR
```properties
# In config.properties
ocr.provider=paddle
```

Run ingestion - should use PaddleOCR only.

#### Test with Force-Tesseract
```properties
# In config.properties
ocr.provider=tesseract
```

Run ingestion - should use Tesseract only.

#### Test with Auto-Detect
```properties
# In config.properties
ocr.provider=auto
```

Run ingestion - should select the best available.

### 5. Error Handling Tests

#### Test 5A: Missing PaddleOCR with Fallback

```bash
# Temporarily break paddleocr installation (or rename scripts/paddle_ocr_runner.py)
# Then run with ocr.provider=auto

# Expected behavior:
# 1. PaddleOCR check fails
# 2. Falls back to Tesseract
# 3. Ingestion completes using Tesseract
```

#### Test 5B: Missing Both OCR Engines

```bash
# Disable both paddleocr and tesseract
# Then run

# Expected output:
# [OCR] WARNING: No OCR provider available. Scanned pages will not be OCR'd.
# (Ingestion continues with PDFBox text only)
```

## Performance Baseline

### Sample Test Document
- 10-page scanned PDF (legal document)
- Each page ~3 MB when rendered to image

### Expected Performance

| Provider | Total Time | Time/Page | Memory |
|----------|-----------|-----------|--------|
| PaddleOCR | ~30-50s (first run: +5min for model) | 3-5s | 2-4 GB |
| Tesseract | ~15-20s | 1.5-2s | 200 MB |

## Common Issues & Solutions

| Issue | Solution |
|-------|----------|
| `ModuleNotFoundError: No module named 'paddleocr'` | `pip3 install paddleocr` |
| `scripts/paddle_ocr_runner.py not found` | Run from project root directory |
| `Python not found in PATH` | Add Python to PATH: `export PATH="/usr/local/bin:$PATH"` |
| OutOfMemory after processing many PDFs | Call `provider.shutdown()` between batches |
| PaddleOCR very slow first run | Normal - downloading model. Subsequent runs faster. |
| Tesseract not found | `brew install tesseract` (Mac) or `apt install tesseract-ocr` (Linux) |

## Code Quality Checks

```bash
# Compile check
mvn clean compile

# Build JAR (with dependencies)
mvn clean package
```

Expected: Zero compilation errors

## Next Steps (Not Yet Implemented)

1. **Table Detection from BBoxes** - Use line bboxes to reconstruct tables
2. **Column Detection** - Reorder text by reading columns left-to-right
3. **Confidence Filtering** - Skip lines with low confidence scores
4. **Batch Optimization** - Process multiple PDFs in parallel efficiently
5. **REST API Wrapper** - Share PaddleOCR model across multiple JVM instances

---

**Last Updated:** March 2026
