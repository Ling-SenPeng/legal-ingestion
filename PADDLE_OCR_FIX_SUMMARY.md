# PaddleOCR Integration - Stderr & Exception Handling Fixes

## Issue Resolved
**Previous Problem**: PaddleOCR integration was failing with exit code 3 even though the Python script was executing successfully. Internal PaddleOCR library messages (especially connectivity check logs) were interfering with successful execution.

## Changes Made (Message 12)

### 1. **Stderr Redirection** (Python Script)
- **What**: Capture stderr immediately at script start to prevent internal messages from leaking to stdout
- **Why**: PaddleOCR writes informational messages to stderr; these were confusing Java's error detection
- **How**:
```python
import io
_original_stderr = sys.stderr
sys.stderr = io.StringIO()  # Capture all stderr output
```
- **Benefit**: Only pure JSON output reaches stdout, making parsing reliable

### 2. **Robust Exception Handling** (Python Script)
- **What**: Added intelligent exception handling that distinguishes between fatal and non-fatal errors
- **Why**: PaddleOCR can throw connectivity exceptions that are not actually failures
- **How**:
```python
# Check error type - ignore connectivity issues
error_msg = str(init_error).lower()
if 'connectivity' in error_msg or 'hosters' in error_msg:
    continue  # Not fatal, try again
else:
    raise  # Real error, exit with code 3
```
- **Benefit**: Transient connectivity messages don't cause premature failure

### 3. **Retry Logic** (Python Script)
- **What**: Attempt PaddleOCR initialization up to 3 times, ignoring connectivity errors
- **Why**: Initial connection attempts may encounter harmless connectivity check messages
- **How**:
```python
for attempt in range(1, max_init_attempts + 1):
    try:
        ocr = PaddleOCR(use_textline_orientation=True, lang='en', verbose=False)
        break  # Success
    except Exception as init_error:
        if 'connectivity' in str(init_error).lower():
            if attempt < max_init_attempts:
                continue  # Try again
```
- **Benefit**: Transient issues don't block initialization

### 4. **Stderr Restoration** (Python Script)
- **What**: Restore original stderr before printing JSON output
- **Why**: Ensures clean output stream separation
- **How**:
```python
sys.stderr = _original_stderr
print(json.dumps(output))
```
- **Benefit**: Debug logging can be re-enabled if needed without breaking JSON parsing

## Related Configuration (Previous Messages)

### Environment Variables (Message 9)
- **PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK=True**: Disables 30-second connectivity check
- Set before importing PaddleOCR to take effect

### Logging Suppression (Message 10)
- **verbose=False**: Suppresses PaddleOCR internal logging
- **warnings.filterwarnings('ignore')**: Suppresses Python warnings

## Testing the Fix

### Quick Test (Recommended)
```bash
# Test with an existing PDF
mvn exec:java \
  -Dexec.mainClass="com.ingestion.AppMain" \
  ingest /path/to/test/pdfs

# Expected output:
# [OCR] Auto-detected: PaddleOCR
# [OCR] Page 1 processed by paddle (XX lines detected)
# [OCR] Page 2 processed by paddle (XX lines detected)
```

### Individual Script Test
```bash
# Test Python script directly with an image
python3 /path/to/scripts/paddle_ocr_runner.py /path/to/test/image.jpg

# Should output valid JSON with:
# - "pageNumber": 1
# - "text": "extracted text..."
# - "lines": [{ "text": "...", "bbox": [...], "confidence": ... }]
```

## Exit Code Reference
- **0**: Success - JSON returned with extracted text
- **1**: Image file not found
- **2**: paddleocr module not installed
- **3**: Actual OCR processing error (not transient connectivity issues)

## Expected Behavior After Fix
1. PaddleOCR initializes silently (no internal messages leak out)
2. All pages process successfully with "paddle" provider name
3. No more "exit code 3" errors from harmless connectivity messages
4. JSON output reliably parsed by Java code
5. Graceful fallback to Tesseract only if actual OCR fails

## Architecture Overview
```
AppMain → PDFReader → OcrProviderFactory 
       → [PaddleOcrService | TesseractOcrService]
              ↓
          ProcessBuilder
              ↓
       paddle_ocr_runner.py
              ↓
         PaddleOCR Library
              ↓
        JSON stdout output
```

## Files Modified
- **scripts/paddle_ocr_runner.py**: Improved exit handling, stderr redirection
- **src/main/java/com/ingestion/PaddleOcrService.java**: No changes (already compatible)
- All changes backward-compatible; Tesseract fallback still available

## Next Steps if Issues Persist
1. Check PaddleOCR installation: `python3 -c "import paddleocr; print('ok')"`
2. Verify image source PDFs are readable
3. Check disk space for model cache (~143MB for English model first run)
4. Monitor Java logs for `[OCR]` prefix messages
5. Review [PADDLE_OCR_SETUP.md](PADDLE_OCR_SETUP.md) for environment setup details
