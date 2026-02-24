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

The application reads the PDF directory and file size limits from `src/main/resources/config.properties`:

```properties
pdf.ingestion.directory=/Users/ling-senpeng/Documents/divorce 2026
max.file.size=1048576
```

**Priority order for directory selection:**
1. Command-line argument: `java -jar legal-ingestion-0.0.1-SNAPSHOT.jar /custom/path`
2. Config file: `config.properties` (built into the JAR)
3. Fallback default: Hardcoded in the code

### Processed Files Tracking

The application automatically tracks which PDF files have been processed to avoid duplicate processing. The tracking information is stored in:

```
~/.pdf-ingestion/processed_files.txt
```

Each line contains the full path of a processed PDF file. On subsequent runs, the application will:
- Load the list of previously processed files
- Skip any files that have already been processed
- Only process new PDF files

To reset the tracking (if you want to reprocess all PDFs), simply delete the `~/.pdf-ingestion/processed_files.txt` file.

## PostgreSQL Database Setup (Docker)

This application can store extracted PDF content in a PostgreSQL database.

### Prerequisites

- **Docker** - [Install Docker](https://docs.docker.com/get-docker/)
- **Docker Compose** - Usually included with Docker Desktop

### Start PostgreSQL Container

Create a `docker-compose.yml` file in the project root:

```yaml
version: '3.8'

services:
  postgres:
    image: pgvector/pgvector:pg15
    container_name: legal-ingestion-db
    environment:
      POSTGRES_USER: ingestion_user
      POSTGRES_PASSWORD: ingestion_pass
      POSTGRES_DB: legal_ingestion
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ingestion_user"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  postgres_data:
```

**Start the database:**
```bash
docker-compose up -d
```

**Stop the database:**
```bash
docker-compose down
```

**View database logs:**
```bash
docker-compose logs postgres
```

### Database Schema

The application uses the following schema to store PDF content with vector embeddings:

```sql
-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Create table for PDF documents
CREATE TABLE IF NOT EXISTS pdf_documents (
    id SERIAL PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    file_path TEXT NOT NULL UNIQUE,
    file_size BIGINT NOT NULL,
    text_content TEXT,
    preview VARCHAR(255),
    embedding vector(1536),
    processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for better query performance
CREATE INDEX idx_file_path ON pdf_documents(file_path);
CREATE INDEX idx_processed_at ON pdf_documents(processed_at);
CREATE INDEX idx_embedding ON pdf_documents USING ivfflat (embedding vector_cosine_ops);
```

**Note:** The `embedding` column stores 1536-dimensional vectors (suitable for OpenAI embeddings). Adjust the dimension based on your embedding model.

### Database Connection Configuration

Add database connection details to `src/main/resources/config.properties`:

```properties
# Database Configuration
db.host=localhost
db.port=5432
db.name=legal_ingestion
db.user=ingestion_user
db.password=ingestion_pass
db.enabled=false

# Vector Embeddings Configuration (optional)
embeddings.enabled=false
embeddings.model=text-embedding-3-small
embeddings.api.key=your-openai-api-key
```

Set `db.enabled=true` to enable database persistence.
Set `embeddings.enabled=true` to store vector embeddings for semantic search.

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
mvn compile exec:java -Dexec.mainClass="com.ingestion.PDFIngestionApp"

# Using custom directory
mvn compile exec:java -Dexec.mainClass="com.ingestion.PDFIngestionApp" -Dexec.args="/path/to/pdfs"
```

**Note:** The `mvn compile` phase is required before `exec:java` to ensure the classes are compiled and available.

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
- **Track processed files** to avoid duplicate processing

### Processed Files Tracking
- Automatically tracks processed PDF files in `~/.pdf-ingestion/processed_files.txt`
- Skips previously processed files on subsequent runs
- Can be reset by deleting the tracking file if needed

### Database Storage (Optional)
- Store extracted PDF content and metadata in PostgreSQL (Docker)
- Vector embeddings support with pgvector extension
- Semantic search capabilities using vector similarity
- Searchable text content for full-text queries
- Timestamps for tracking processing history

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
- **PostgreSQL JDBC Driver** (optional) - For database persistence
- **pgvector** (optional) - PostgreSQL extension for vector similarity search
- **Docker & Docker Compose** (optional) - For PostgreSQL database container with pgvector

## License

Apache-2.0 License
