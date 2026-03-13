# PaddleOCR Integration Guide

## Overview

This project now features **pluggable OCR providers** with built-in support for:
- **PaddleOCR** (fast, layout-aware, recommended for scanned legal PDFs) 
- **Tesseract** (fallback, widely available)

The system automatically detects which engine is available and uses the best option, with graceful fallback support.

## Quick Start

### 1. Install PaddleOCR (Recommended)

```bash
# Prerequisites: Python 3.7+
python3 --version

# Install BOTH required packages:
# 1. paddlepaddle = core Paddle framework (REQUIRED)
# 2. paddleocr = OCR wrapper library (REQUIRED)
pip3 install paddlepaddle paddleocr

# Verify installation
python3 -c "import paddle; from paddleocr import PaddleOCR; print('OK')"
```

**Important:** Installing `paddleocr` alone is NOT sufficient. The package depends on the core `paddlepaddle` framework. If you see `No module named 'paddle'`, run `pip3 install paddlepaddle`.

**First Run:** The first invocation of PaddleOCR will download the model (~300MB). This is cached locally, so subsequent runs are fast.

### 2. Configure the OCR Provider

Edit `src/main/resources/config.properties`:

```properties
# Options: "paddle", "tesseract", "auto" (default)
# "auto" = use PaddleOCR if available, fall back to Tesseract
ocr.provider=auto
```

**Provider Options:**
- `auto` - Auto-detect best available (recommended)
- `paddle` - Force PaddleOCR (requires Python + paddleocr)
- `tesseract` - Force Tesseract (requires tesseract binary)

### 3. Build and Run

```bash
mvn clean compile
mvn exec:java -Dexec.mainClass="com.ingestion.AppMain" ingest
```

## Architecture

### OCR Provider Abstraction

All OCR backends implement `OcrProvider` interface:

```java
public interface OcrProvider {
    OcrPage extractPage(Path pdfPath, int pageIndex) throws Exception;
    String getProviderName();
    void shutdown();
    boolean isAvailable();
}
```

### Data Model

#### OcrPage
```java
public class OcrPage {
    int pageNumber;           // 1-based page number
    String text;              // Full normalized page text
    List<OcrLine> lines;      // Structured line-level blocks
    String providerName;      // Which engine produced this
}
```

#### OcrLine (Layout-Aware)
```java
public class OcrLine {
    String text;              // Detected text
    int[] bbox;               // [x1, y1, x2, y2] in pixels
    double confidence;        // 0.0 to 1.0 confidence score
}
```

### PaddleOCR Service

The `PaddleOcrService` class:
1. Renders PDF pages to JPEG images (300 DPI)
2. Calls Python subprocess: `python3 scripts/paddle_ocr_runner.py <image_path>`
3. Parses structured JSON output including bounding boxes
4. Creates `OcrPage` objects with full layout metadata
5. Automatically falls back to Tesseract on error

## Performance Characteristics

### PaddleOCR
- **Speed:** ~2-5 seconds per page (including model download on first run)
- **Accuracy:** 95-98% on clean scanned legal documents
- **Strengths:** Layout awareness, handles rotated text, column detection
- **Memory:** ~2-4 GB (model stays in memory between calls)

### Tesseract
- **Speed:** ~1-2 seconds per page
- **Accuracy:** 85-95% depending on document quality
- **Strengths:** Lightweight, no model download, widely available
- **Memory:** ~200 MB

## Usage Examples

### Automatic (Recommended)
```java
PDFReader reader = new PDFReader();
List<PageText> pages = reader.extractPages("/path/to/pdf.pdf");
// Automatically selects best OCR provider and processes
```

### Manual Provider Selection
```java
OcrProvider provider = OcrProviderFactory.selectProvider("paddle");
if (provider != null) {
    OcrPage page = provider.extractPage(Paths.get("document.pdf"), 0);
    System.out.println("Text: " + page.getText());
    System.out.println("Lines: " + page.getLines().size());
}
```

### With Fallback
```java
// Try PaddleOCR, fall back to Tesseract
OcrProvider provider = OcrProviderFactory.selectProviderWithFallback("paddle", "tesseract");
```

## Troubleshooting

### "paddleocr module not installed"
```bash
pip3 install paddleocr
# If that fails, try:
pip3 install paddleocr --upgrade
```

### "No module named 'paddle'" (Missing paddlepaddle)
**This is a common issue!** Installing `paddleocr` alone is insufficient. You must also install the core `paddlepaddle` framework:
```bash
pip3 install paddlepaddle
# Verify with:
python3 -c "import paddle; print('OK')"
```

This single command installs the missing dependency that `paddleocr` requires to function.

### "Python not found in PATH"
Ensure Python 3 is installed and in your system PATH:
```bash
which python3
export PATH="/usr/local/bin:$PATH"  # Add python3 to PATH if needed
```

### "Helper script not found: scripts/paddle_ocr_runner.py"
Ensure you're running from the project root directory. The Python helper script is at:
```
projectRoot/scripts/paddle_ocr_runner.py
```

### PaddleOCR is slow on first run
This is normal - the model (~300MB) is downloaded and cached locally. Subsequent runs are much faster.

### Out of Memory errors
PaddleOCR keeps the model in memory. If processing many PDFs:
```java
PaddleOcrService paddle = (PaddleOcrService) provider;
paddle.shutdown();  // Release model memory
```

## Advanced Configuration

### Custom Thread Pool Size
```java
PaddleOcrService paddle = new PaddleOcrService(4, Paths.get("."));  // 4 concurrent tasks
```

### Detect Availability Without Initializing
```java
OcrProvider provider = OcrProviderFactory.selectProvider("paddle");
if (provider.isAvailable()) {
    System.out.println("PaddleOCR is ready");
}
```

## Integration with Ingestion Pipeline

The OCR pipeline integrates seamlessly with the existing PDF ingestion:

1. **PDFReader** detects pages needing OCR via `OcrDecider` (< 30 chars)
2. **OcrProviderFactory** selects the best available provider
3. **OcrProvider** (PaddleOCR or Tesseract) processes the page
4. **Structured output** (text + bboxes) is used for downstream processing
5. **Fallback** to PDFBox text if OCR fails

## Future Enhancements

Planned improvements (not yet implemented):
- [ ] Table detection and reconstruction from bboxes
- [ ] Column-aware text ordering
- [ ] Batch processing optimization
- [ ] OCR confidence thresholding
- [ ] Multi-language support
- [ ] REST API wrapper for PaddleOCR (for CPU/GPU sharing)

## JSON Output Format (Internal)

The Python helper script returns JSON with this structure:

```json
{
  "pageNumber": 1,
  "text": "1039 S MAIN ST NEWARK CA 94560...",
  "lines": [
    {
      "text": "1039 S MAIN ST",
      "bbox": [120, 340, 280, 360],
      "confidence": 0.98
    },
    {
      "text": "NEWARK CA 94560",
      "bbox": [120, 365, 280, 380],
      "confidence": 0.97
    }
  ]
}
```

## References

- [PaddleOCR Documentation](https://www.paddleocr.org/)
- [Tesseract OCR](https://github.com/UB-Mannheim/tesseract/wiki)

---

**Version:** 1.0  
**Last Updated:** March 2026
