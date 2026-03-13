#!/usr/bin/env python3
"""
PaddleOCR Helper Script

Usage:
    python3 paddle_ocr_runner.py <image_path>

Output:
    Prints structured JSON with:
    - pageNumber (always 1 since input is single image)
    - text: full normalized page text
    - lines: array of detected lines with text, bbox, and confidence

JSON Format:
{
  "pageNumber": 1,
  "text": "full text from all lines...",
  "lines": [
    {
      "text": "line text",
      "bbox": [x1, y1, x2, y2],
      "confidence": 0.98
    },
    ...
  ]
}

Exit codes:
    0: Valid JSON response returned (check for "error" field to distinguish success vs. failure)
    1: Script usage error (no image_path provided)
"""

import sys
import json
import os
import warnings
import io

# CRITICAL: Set environment variables BEFORE importing paddle libraries
# This disables the model connectivity check that was causing timeouts
os.environ['PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK'] = 'True'

# Suppress all warnings (harmless on macOS with LibreSSL)
warnings.filterwarnings('ignore')

def main():
    if len(sys.argv) < 2:
        print(json.dumps({
            "error": "Usage: paddle_ocr_runner.py <image_path>",
            "pageNumber": 1,
            "text": "",
            "lines": []
        }))
        sys.exit(1)

    image_path = sys.argv[1]

    # Check if file exists
    if not os.path.exists(image_path):
        print(json.dumps({
            "error": f"Image file not found: {image_path}",
            "pageNumber": 1,
            "text": "",
            "lines": []
        }))
        sys.exit(0)  # Return 0 because we generated valid JSON response

    try:
        from paddleocr import PaddleOCR
    except ImportError as e:
        print(json.dumps({
            "error": f"paddleocr not installed. Run: pip3 install paddleocr",
            "pageNumber": 1,
            "text": "",
            "lines": []
        }))
        sys.exit(0)  # Return 0 because we generated valid JSON response

    # Capture stderr ONLY during PaddleOCR initialization to suppress verbose startup messages
    original_stderr = sys.stderr
    sys.stderr = io.StringIO()

    # Try to initialize PaddleOCR
    ocr = None
    max_init_attempts = 3
    init_success = False
    last_error = None
    
    for attempt in range(1, max_init_attempts + 1):
        try:
            # Initialize PaddleOCR (downloads model on first run, cached thereafter)
            # use_textline_orientation=True improves handling of rotated text
            ocr = PaddleOCR(use_textline_orientation=True, lang='en')
            init_success = True
            break  # Success
        except Exception as init_error:
            last_error = init_error
            error_msg = str(init_error).lower()
            # Ignore connectivity/model check errors - they're not fatal, try again
            if 'connectivity' in error_msg or 'hosters' in error_msg or 'model_source' in error_msg:
                if attempt < max_init_attempts:
                    continue  # Try again
                # If all attempts failed, we'll restore stderr and report below

    # Restore stderr before any further output
    sys.stderr = original_stderr

    if not init_success:
        # Initialization failed after all retries
        error_msg = str(last_error) if last_error else "Unknown error during initialization"
        print(json.dumps({
            "error": f"PaddleOCR initialization failed: {error_msg}",
            "pageNumber": 1,
            "text": "",
            "lines": []
        }))
        sys.exit(0)  # Return 0 because we generated valid JSON response

    try:
        # Run OCR on the image
        result = ocr.ocr(image_path)

        # Parse results
        lines = []
        full_text_parts = []

        # PaddleOCR returns a list containing OCRResult objects (dict-like)
        # result[0] is the OCRResult for the first (and only) page
        if result and len(result) > 0:
            ocr_result = result[0]
            
            # Extract texts, scores, and polygons from the OCRResult
            texts = ocr_result.get('rec_texts', [])
            scores = ocr_result.get('rec_scores', [])
            polys = ocr_result.get('rec_polys', [])
            
            # Process each detected text region
            for i, (text, score, poly) in enumerate(zip(texts, scores, polys)):
                try:
                    # Skip empty text
                    if not text or not str(text).strip():
                        continue
                    
                    text = str(text)
                    confidence = float(score) if score is not None else 0.0
                    
                    # Convert polygon (numpy array or list of points) to axis-aligned bbox
                    if poly is not None and hasattr(poly, '__len__') and len(poly) >= 2:
                        xs = [p[0] for p in poly]
                        ys = [p[1] for p in poly]
                        
                        x1, y1, x2, y2 = int(min(xs)), int(min(ys)), int(max(xs)), int(max(ys))
                        
                        lines.append({
                            "text": text,
                            "bbox": [x1, y1, x2, y2],
                            "confidence": round(confidence, 4)
                        })
                        
                        full_text_parts.append(text)
                
                except (IndexError, ValueError, TypeError) as e:
                    # Skip malformed results for this line
                    continue

        # Construct full normalized text by joining lines
        full_text = " ".join(full_text_parts)

        # Return structured JSON
        output = {
            "pageNumber": 1,
            "text": full_text,
            "lines": lines
        }

        print(json.dumps(output))
        sys.exit(0)

    except FileNotFoundError:
        print(json.dumps({"error": f"Image file not found: {image_path}"}))
        sys.exit(0)
    except ImportError:
        print(json.dumps({"error": "paddleocr not installed"}))
        sys.exit(0)
    except Exception as e:
        print(json.dumps({
            "error": f"OCR processing failed: {str(e)}",
            "pageNumber": 1,
            "text": "",
            "lines": []
        }))
        sys.exit(0)

if __name__ == "__main__":
    main()
