# PaddleOCR Integration - Architecture Diagram

## System Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        PDF Ingestion Pipeline                            │
└─────────────────────────────────────────────────────────────────────────┘

                              PDFingestionApp
                                   │
                                   ▼
                              PDFReader
                                   │
                    ┌──────────────┴──────────────┐
                    ▼                             ▼
            Extract via PDFBox          OcrDecider.shouldOcr()
            (< 30 chars?)                Check needed?
                    │                             │
                    └──────────────┬──────────────┘
                                   ▼
                          OcrProviderFactory
                        .selectProvider(name)
                                   │
                ┌──────────────────┼──────────────────┐
                ▼                  ▼                  ▼
           PaddleOCR         Tesseract           Auto-Detect
           (NEW)             (Refactored)        (Best Available)
                │                 │                  │
                └─────────────────┴──────────────────┘
                                   ▼
                          OcrProvider Interface
                                   │
                ┌──────────────────┼──────────────────┐
                ▼                  ▼
             extractPage()    getProviderName()
             isAvailable()    shutdown()
                                   │
                                   ▼
                              OcrPage Object
                                   │
                ┌──────────────────┼──────────────────┐
                ▼                  ▼
            Full Text         OcrLine[] (with bboxes)
            (normalized)       - text
                               - bbox: [x1,y1,x2,y2]
                               - confidence: 0.0-1.0
```

## PaddleOCR Call Chain

```
java: PDFReader.extractPages()
  │
  ├─→ PDFTextStripper (extract via PDFBox)
  │
  ├─→ OcrDecider.shouldOcr() (check if OCR needed)
  │
  ├─→ OcrProviderFactory.selectProvider()
  │   │
  │   └─→ Check PaddleOCR availability
  │       └─→ PaddleOcrService.isAvailable()
  │           ├─ Python installed?
  │           ├─ Script file exists?
  │           └─ paddleocr module installed?
  │
  ├─→ PaddleOcrService.extractPage(pdf, pageIndex)
  │   │
  │   ├─→ Render PDF page to JPEG (300 DPI)
  │   │
  │   ├─→ Call Python subprocess:
  │   │   python3 scripts/paddle_ocr_runner.py <image_path>
  │   │
  │   └─→ Parse JSON response
  │       └─→ Create OcrPage with OcrLine objects
  │
  └─→ return List<PageText> (normalized text per page)
```

## Provider Selection Decision Tree

```
                    selectProvider(name)
                           │
                    ┌──────┴───────┐
                    ▼              ▼
            Explicit name?     "auto" mode?
                    │              │
            name="paddle"?    ┌─────┴─────┐
                    │         ▼           ▼
                ┌───┴─────┐ Try Paddle Try
                ▼         ▼  First     Tesseract
            Try Init Available? Second
            Paddle    │       │        │
                │   ├─Yes→   ├─Yes→  ├─Yes→
                │   │ Use    │ Use   │ Use
                │   │        │       │
                └───┴────────┴────┴──┴──→ Return Provider
```

## Data Flow: PDF → OCR → Structured Output

```
Input PDF File
      │
      ▼
  ┌─────────────────────────────┐
  │ Page 1 (Scanned)            │
  │ - Contains mortgage document│
  │ - Image-based, needs OCR    │
  └─────────────────────────────┘
      │
      ├─→ PDFBox Extract (< 30 chars)
      │
      ├─→ OcrDecider: shouldOcr = true
      │
      ├─→ Render to JPEG (300 DPI)
      │
      ├─→ Call Python:
      │   python3 scripts/paddle_ocr_runner.py page1.jpg
      │
      ▼
  ┌─────────────────────────────────┐
  │ Python Process (PaddleOCR)      │
  │ - Load model                    │
  │ - Detect text regions           │
  │ - Extract with coordinates      │
  │ - Return JSON                   │
  └─────────────────────────────────┘
      │
      └──→ JSON Response:
          {
            "pageNumber": 1,
            "text": "25 MAIN ST NEWARK CA...",
            "lines": [
              {
                "text": "25 MAIN ST",
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
      │
      ▼
  ┌─────────────────────────────────────┐
  │ OcrPage Object (Java)               │
  │ - pageNumber: 1                     │
  │ - text: "25 MAIN ST NEWARK CA..."  │
  │ - lines: [OcrLine, OcrLine, ...]   │
  │ - providerName: "paddle"            │
  └─────────────────────────────────────┘
      │
      ▼
  ┌─────────────────────────────────────┐
  │ Downstream Processing               │
  │ - Text insertion to DB              │
  │ - Table reconstruction from bboxes  │
  │ - Embedding generation              │
  │ - Vector search                     │
  └─────────────────────────────────────┘
```

## Class Diagram (Core OCR Components)

```
┌──────────────────────────────┐
│     OcrProvider (I)          │
├──────────────────────────────┤
│ extractPage(pdf, idx)        │
│ getProviderName(): String    │
│ shutdown()                   │
│ isAvailable(): boolean       │
└──────────────┬───────────────┘
               │
        ┌──────┴──────┐
        ▼             ▼
┌─────────────────┐ ┌──────────────────────┐
│ TesseractOcr    │ │  PaddleOcrService    │
│ Service         │ │  (NEW)               │
├─────────────────┤ ├──────────────────────┤
│ + ocrPage()     │ │ - PythonCmd: String  │
│ + renderImage() │ │ - projectRoot: Path  │
│ + runTesseract()│ │ - objectMapper       │
└─────────────────┘ │                      │
                    │ + renderPageToImage()│
                    │ + runPaddleOcrScript()
                    │ + parseOcrJson()     │
                    └──────────────────────┘

┌──────────────────────┐
│    OcrPage           │
├──────────────────────┤
│ - pageNumber: int    │
│ - text: String       │
│ - lines: List<Ocr>   │
│ - providerName: Str  │
└──────────────────────┘
        │
        ▼
┌──────────────────────┐
│    OcrLine           │
├──────────────────────┤
│ - text: String       │
│ - bbox: int[]        │
│ - confidence: double │
└──────────────────────┘
```

## Error Handling Flow

```
extractPage() call
│
├─→ Attempt 1: Run PaddleOCR
│   │
│   ├─→ Success? → Return OcrPage ✓
│   │
│   └─→ Failure?
│       │
│       ├─→ Retry (configurable)
│       │
│       └─→ Fall back to Tesseract
│           │
│           ├─→ Tesseract Success? → Return OcrPage ✓
│           │
│           └─→ Tesseract Failure?
│               │
│               └─→ Return null or throw exception
│                   (upstream falls back to PDFBox text)
│
└─→ Caller gets text via fallback chain
    PDFBox → PaddleOCR → Tesseract → PDFBox (fallback)
```

## Configuration Hierarchy

```
config.properties (ocr.provider=auto)
           │
           ▼
    OcrProviderFactory
           │
    ┌──────┴─────┐
    ▼            ▼
  "auto"   Explicit name
    │            │
    ├→ Check    └→ Try requested
      Paddle      provider
      │          │
      ├→ Check  └→ Found?
        Tess      │
        │        Yes: Use it
        ├→ Use   │
          best   No: Try
                 fallback
```

---

**Scale:** All components fit in a single JAR file  
**Integration:** Seamless with existing PDF pipeline  
**Fallback:** Automatic, no code changes needed  
