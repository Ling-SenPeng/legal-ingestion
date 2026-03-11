#!/bin/bash

# OCR Pipeline Debug Script for MC-30-1.pdf
# 
# This script will run the comprehensive debug output for the OCR pipeline
# processing the MC-30-1.pdf file.
#
# SETUP:
# 1. Place MC-30-1.pdf in the injestion project root directory, OR
# 2. Run this script and provide the full path when prompted
#
# REQUIREMENTS:
# - Java 17+
# - Maven (for building)
# - Tesseract OCR installed (brew install tesseract on macOS)

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

echo ""
echo "================================================================================================"
echo "OCR PIPELINE DEBUG TEST - MC-30-1.pdf"
echo "================================================================================================"
echo ""

# Check if Maven is available
if ! command -v mvn &> /dev/null; then
    echo "ERROR: Maven not found. Please install Maven."
    exit 1
fi

# Check if Java is available
if ! command -v java &> /dev/null; then
    echo "ERROR: Java not found. Please install Java 17+."
    exit 1
fi

# Build project
echo "[1/3] Building project with Maven..."
mvn clean package -q

# Compile debug test
echo "[2/3] Compiling debug test..."
javac -cp "target/classes:target/legal-injestion-0.0.1-SNAPSHOT.jar" OcrDebugTest.java

# Run debug test
echo "[3/3] Running OCR debug pipeline..."
echo ""

java -cp "target/classes:target/legal-injestion-0.0.1-SNAPSHOT.jar:." OcrDebugTest

echo ""
echo "================================================================================================"
echo "DEBUG TEST COMPLETE - Check output above for detailed OCR pipeline traces"
echo "================================================================================================"
echo ""
echo "Key sections to look for:"
echo "  [PDFReader] >>>>>>> PAGE N <<<<<<      - Debug sections for MC-30-1.pdf"
echo "  [OcrDecider]                            - OCR decision logic"
echo "  [OCR] Starting OCR for pageIndex=      - OCR service invocation"
echo "  [Render]                                - PDF page rendering"
echo "  [Tesseract]                             - Tesseract CLI execution"
echo ""
