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

1. **Build the project with Maven**
   ```bash
   mvn clean package
   ```
   This creates a fat JAR with all dependencies included.

2. **Run the application using default directory (from config.properties)**
   ```bash
   java -jar target/legal-ingestion-0.0.1-SNAPSHOT.jar
   ```

3. **Run the application with a custom directory path**
   ```bash
   java -jar target/legal-ingestion-0.0.1-SNAPSHOT.jar /path/to/pdf/directory
   ```
   
   Example:
   ```bash
   java -jar target/legal-ingestion-0.0.1-SNAPSHOT.jar ~/Documents/PDFs
   ```

4. **The application will:**
   - Find all PDF files in the specified directory (or from config.properties)
   - Extract text content from each PDF
   - Display file information and a preview of the content

## Configuration

The application reads the PDF directory from `src/main/resources/config.properties`:

```properties
pdf.ingestion.directory=/Users/ling-senpeng/Documents/divorce 2026
```

**Priority order for directory selection:**
1. Command-line argument: `java -jar legal-ingestion-0.0.1-SNAPSHOT.jar /custom/path`
2. Config file: `config.properties` (built into the JAR)
3. Fallback default: Hardcoded in the code

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
│   │       ├── config.properties         (Configuration)
│   │       └── application.properties
│   └── test/
│       └── java/com/ingestion/
│           ├── PDFReaderTest.java        (PDF reader tests)
│           └── HelloWorldTest.java       (Hello World tests)
├── pom.xml
└── README.md
```

## Development

### Maven Commands

**Clean and Build**
```bash
mvn clean package
```

**Run Tests**
```bash
mvn test
```

**Compile Only (without packaging)**
```bash
mvn compile
```

**Run Application**
```bash
# Using default config.properties directory
mvn exec:java -Dexec.mainClass="com.ingestion.PDFIngestionApp"

# Using custom directory
mvn exec:java -Dexec.mainClass="com.ingestion.PDFIngestionApp" -Dexec.args="/path/to/pdfs"
```

**Run Tests with Coverage**
```bash
mvn test -DargLine="-Xmx1024m"
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
