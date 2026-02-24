# Legal Ingestion

A Java application to read and process PDF files from a directory, built with Maven.

## Requirements

- **Java 17**
- **Maven 3.9+**

## 🛠️ Setup Instructions

### Prerequisites

1. **Java 17**
   - Verify installation: `java -version`
   - Should show Java 17.x.x

2. **Maven**
   - Download and install from [maven.apache.org](https://maven.apache.org/install.html)
   - Verify installation: `mvn --version`

### Build and Run

1. **Build the project**
   ```bash
   mvn clean package
   ```

2. **Run the application with a directory path**
   ```bash
   java -jar target/legal-ingestion-0.0.1-SNAPSHOT.jar /path/to/pdf/directory
   ```
   
   Example:
   ```bash
   java -jar target/legal-ingestion-0.0.1-SNAPSHOT.jar ~/Documents/PDFs
   ```

3. **The application will:**
   - Find all PDF files in the specified directory
   - Extract text content from each PDF
   - Display file information and a preview of the content

## Project Structure

```
legal-ingestion/
├── src/
│   ├── main/
│   │   ├── java/com/ingestion/
│   │   │   ├── PDFIngestionApp.java      (Main application)
│   │   │   ├── PDFReader.java            (PDF reading utility)
│   │   │   └── HelloWorld.java           (Hello World example)
│   │   └── resources/
│   │       └── application.properties
│   └── test/
│       └── java/com/ingestion/
│           ├── PDFReaderTest.java        (PDF reader tests)
│           └── HelloWorldTest.java       (Hello World tests)
├── pom.xml
└── README.md
```

## Development

### Building the Project
```bash
mvn clean package
```

### Running Tests
```bash
mvn test
```

### Running the PDF Ingestion Application
```bash
java -jar target/legal-ingestion-0.0.1-SNAPSHOT.jar /path/to/pdfs
```

## Features

### PDF Reader
The `PDFReader` class provides utilities to:
- **Find all PDF files** in a given directory (including subdirectories)
- **Extract text** from PDF files
- **Get PDF information** including file path, text length, and content preview

### Example Usage

```java
PDFReader pdfReader = new PDFReader();
List<PDFReader.PDFInfo> pdfInfoList = pdfReader.readAllPdfsFromDirectory("/path/to/pdfs");

for (PDFReader.PDFInfo info : pdfInfoList) {
    System.out.println("File: " + info.fileName);
    System.out.println("Text Length: " + info.textLength);
    System.out.println("Preview: " + info.preview);
}
```

## Dependencies

- **Apache PDFBox** (2.0.29) - For reading and extracting text from PDF files
- **JUnit 5** - For unit testing

## License

Apache-2.0 License
