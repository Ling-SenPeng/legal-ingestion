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
   mvn exec:java -Dexec.mainClass="com.ingestion.AppMain" -Dexec.args="ingest"
   ```

3. **Run the application with a custom directory path**
   ```bash
   mvn exec:java -Dexec.mainClass="com.ingestion.AppMain" -Dexec.args="ingest /path/to/pdf/directory"
   ```
   
   Example:
   ```bash
   mvn exec:java -Dexec.mainClass="com.ingestion.AppMain" -Dexec.args="ingest ~/Documents/PDFs"
   ```

4. **For development (using Maven with classpath)**
   ```bash
   mvn compile exec:java -Dexec.mainClass="com.ingestion.AppMain" -Dexec.args="ingest"
   ```
   - Find all PDF files in the specified directory (or from config.properties)
   - Extract text content from each PDF by page
   - Calculate SHA256 hash for deduplication
   - Store document metadata and page chunks in PostgreSQL

## 🚀 Vector Search Feature

The application now supports semantic search using OpenAI embeddings and PostgreSQL pgvector:

### Commands

#### 1. **ingest** - Process and store PDFs
```bash
mvn exec:java -Dexec.mainClass="com.ingestion.AppMain" -Dexec.args="ingest [directory]"
```
Extracts text by page, stores in database with embeddings=NULL (ready for embedding generation).

#### 2. **embed-missing** - Generate embeddings for chunks
```bash
# Generate embeddings for all chunks without embeddings
mvn exec:java -Dexec.mainClass="com.ingestion.AppMain" -Dexec.args="embed-missing"

# Custom limit
mvn exec:java -Dexec.mainClass="com.ingestion.AppMain" -Dexec.args="embed-missing --limit 200 --batchSize 50"
```
**Requires:** Set `OPENAI_API_KEY` environment variable or add to `.env` file

#### 3. **search** - Semantic search across PDFs
```bash
# Basic search
mvn exec:java -Dexec.mainClass="com.ingestion.AppMain" -Dexec.args="search --query 'breach of contract'"

# Custom top-K results
mvn exec:java -Dexec.mainClass="com.ingestion.AppMain" -Dexec.args="search --query 'liability' --topK 20"
```
**Requires:** Set `OPENAI_API_KEY` environment variable or add to `.env` file

**Returns legal citations:** file_name, file_path, page_no, similarity score

### Setup Vector Search

```bash
# 1. Create .env file with OpenAI API key
echo 'OPENAI_API_KEY=sk-your-api-key' > .env

# 2. Build the project
mvn clean package

# 3. Ingest PDFs (creates chunks with embedding=NULL)
mvn exec:java -Dexec.mainClass="com.ingestion.AppMain" -Dexec.args="ingest"

# 4. Generate embeddings for all chunks
mvn exec:java -Dexec.mainClass="com.ingestion.AppMain" -Dexec.args="embed-missing --limit 1000"

# 5. Search for documents
mvn exec:java -Dexec.mainClass="com.ingestion.AppMain" -Dexec.args="search --query 'your search term' --topK 10"
```

## Configuration

The application reads the PDF directory and file size limits from `src/main/resources/config.properties`:

```properties
pdf.ingestion.directory=/Users/ling-senpeng/Documents/divorce 2026
max.file.size=1048576
```

**Priority order for directory selection:**
1. Command-line argument: `mvn compile exec:java -Dexec.mainClass="com.ingestion.PDFIngestionApp" -Dexec.args="/custom/path"`
2. Config file: `config.properties` (built into the JAR)
3. Fallback default: Hardcoded in the code

**Alternative (using Maven):**
- `mvn exec:java -Dexec.mainClass="com.ingestion.AppMain" -Dexec.args="/custom/path"`

## 📋 MVP Level 1 - Page-based Chunking & Database Ingestion

The ingestion pipeline now extracts PDF text by page and stores chunks in PostgreSQL with citation support.

### Key Features (MVP Level 1)

- ✅ **Page-level extraction**: Extract text from each page separately (1-based page numbering for legal citations)
- ✅ **SHA256 deduplication**: Calculate file hash to prevent duplicate processing (database-managed)
- ✅ **Idempotent pipeline**: SHA256 + status tracking ensures no duplicates on re-runs
- ✅ **Database persistence**: Store document metadata and page chunks in PostgreSQL
- ✅ **Status tracking**: Track processing pipeline (NEW → PROCESSING → DONE/FAILED)
- ✅ **Error resilience**: Single PDF failure doesn't halt entire pipeline
- ✅ **Metadata storage**: JSONB metadata per chunk (extractor type, character count)
- ⏳ **Embeddings**: NULL for MVP (ready for future OpenAI integration)

### Run the Ingestion Pipeline

#### 1. Ensure PostgreSQL is running (Docker):

```bash
# Start PostgreSQL with pgvector
docker-compose up -d

# Initialize database schema
docker-compose exec -T postgres psql -U ingestion_user -d legal_ingestion < init.sql

# Verify tables are created
docker-compose exec postgres psql -U ingestion_user -d legal_ingestion -c "\dt"
```

Expected output:
```
               List of relations
 Schema |      Name      | Type  |       Owner       
--------+----------------+-------+-------------------
 public | pdf_chunks     | table | ingestion_user
 public | pdf_documents  | table | ingestion_user
```

#### 2. Run the ingestion app:

**Using Maven:**
```bash
# Default config.properties directory
mvn clean package
mvn exec:java -Dexec.mainClass="com.ingestion.AppMain" -Dexec.args="ingest"

# Custom directory
mvn exec:java -Dexec.mainClass="com.ingestion.AppMain" -Dexec.args="ingest /path/to/pdfs"
```

#### 3. Expected output:

```
=== PDF Ingestion Pipeline (MVP Level 1) ===
Input Directory: /Users/ling-senpeng/Documents/divorce 2026
Database URL: jdbc:postgresql://localhost:5432/legal_ingestion

Found 33 PDF file(s) in /Users/ling-senpeng/Documents/divorce 2026

[1] Processing: document1.pdf
  • Computing SHA256...
  • Upserting document record...
  • Document ID: 1
  • Extracting text by page...
  • Pages extracted: 5
  • Storing chunks with page citations...
  Inserted/updated 5 chunks
  ✓ Success: 5 page(s) ingested

[2] Processing: document2.pdf
...

=== Summary ===
Total files processed: 33
Total files skipped: 0
Total files failed: 0
Total files encountered: 33
```

### Verify Results in Database

After running the ingestion pipeline, verify the results:

#### Check documents were stored:

```bash
docker-compose exec postgres psql -U ingestion_user -d legal_ingestion -c \
  "SELECT id, file_name, sha256, status FROM pdf_documents LIMIT 5;"
```

Expected output:
```
 id |      file_name      |                              sha256                             | status  
----+---------------------+--------------------------------+--------
  1 | document1.pdf       | 3a5f2b8c1d9e4f7a2b8c1d9e4f7a2b8c1d9e4f7a2b8c1d9e4f7a2b8c1d9e | DONE
  2 | document2.pdf       | 2b4e1c7d8f9a3b6c2d5e8f1a4b7c0d3e6f9a2b5c8d1e4f7a0b3c6d9e2 | DONE
  3 | document3.pdf       | 5c7f2a9d8e1b6c3f4a7d2e9b1c8f5a2d3e0f1b2c4d7e9f1a3b6c8d0e2 | DONE
```

#### Check chunks by document ID (with page numbers):

```bash
docker-compose exec postgres psql -U ingestion_user -d legal_ingestion -c \
  "SELECT id, page_no, chunk_index, LENGTH(text) as char_count, meta FROM pdf_chunks WHERE doc_id = 1 ORDER BY page_no;"
```

Expected output:
```
 id | page_no | chunk_index | char_count |                    meta                     
----+---------+-------------+------------+---------------------------------------------
  1 |       1 |           0 |       1523 | {"char_count": 1523, "extractor": "pdfbox"}
  2 |       2 |           0 |       2018 | {"char_count": 2018, "extractor": "pdfbox"}
  3 |       3 |           0 |       1876 | {"char_count": 1876, "extractor": "pdfbox"}
  4 |       4 |           0 |       1645 | {"char_count": 1645, "extractor": "pdfbox"}
  5 |       5 |           0 |       1234 | {"char_count": 1234, "extractor": "pdfbox"}
```

#### Count total chunks:

```bash
docker-compose exec postgres psql -U ingestion_user -d legal_ingestion -c \
  "SELECT COUNT(*) as total_chunks FROM pdf_chunks;"
```

#### Get a specific page chunk for legal citation:

```bash
docker-compose exec postgres psql -U ingestion_user -d legal_ingestion -c \
  "SELECT id, page_no, LEFT(text, 100) as preview FROM pdf_chunks WHERE doc_id = 1 AND page_no = 3;"
```

Expected output:
```
 id | page_no |                             preview                              
----+---------+--------------------------------------------------------------------
  3 |       3 | This is the text content from page 3 of the document. It contains...
```

#### Idempotency check (run ingestion twice):

First run processes new files, second run checks for duplicates:

```bash
# First run
mvn clean compile exec:java -Dexec.mainClass="com.ingestion.PDFIngestionApp"

# Second run
mvn clean compile exec:java -Dexec.mainClass="com.ingestion.PDFIngestionApp"

# Check if documents are marked as existing
docker-compose exec postgres psql -U ingestion_user -d legal_ingestion -c \
  "SELECT COUNT(*) FROM pdf_documents WHERE status = 'DONE';"
```

The second run should skip processing (or upsert existing chunks without duplicating).

### Pagination & Status Filtering

#### Get all documents by status:

```bash
docker-compose exec postgres psql -U ingestion_user -d legal_ingestion -c \
  "SELECT id, file_name, file_size, status FROM pdf_documents WHERE status = 'DONE' ORDER BY processed_at DESC LIMIT 10;"
```

#### Get failed documents with error messages:

```bash
docker-compose exec postgres psql -U ingestion_user -d legal_ingestion -c \
  "SELECT id, file_name, status, error_msg FROM pdf_documents WHERE status = 'FAILED';"
```

### Document and Schema

The ingestion pipeline performs these operations:

1. Discover all PDF files in input directory (filter > 1MB)
2. Calculate SHA256 hash of each PDF
3. Upsert document record in `pdf_documents` table
4. Extract text by page using PDFBox (`PDFTextStripper` with setStartPage/setEndPage)
5. Store each page as a chunk in `pdf_chunks` table with:
   - `page_no` (1-based for legal citation)
   - `chunk_index` = 0 for MVP (future: multiple chunks per page)
   - `text` (page content)
   - `meta` JSON including extractor type and character count
   - `embedding` (NULL for MVP, ready for OpenAI integration)
6. Mark document as DONE or FAILED
7. Continue with next file (no early exit on failure)



This application can store extracted PDF content in a PostgreSQL database.

### Prerequisites

- **Docker** - [Install Docker](https://docs.docker.com/get-docker/)
- **Docker Compose** - Usually included with Docker Desktop

### Start PostgreSQL Container

This project includes a `docker-compose.yml` file for running PostgreSQL with pgvector. 

**To get started with Docker:**

1. Ensure Docker and Docker Compose are installed
2. A `docker-compose.yml` file is included in the project root with the required configuration

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

### Initialize Database with pgvector

The database will automatically initialize on first startup with the `init.sql` schema (since the volume mount is enabled in `docker-compose.yml`).

If you need to manually initialize or re-initialize the database:

**Simple command:**
```bash
docker-compose exec -T postgres psql -U ingestion_user -d legal_ingestion < init.sql
```

**Alternative methods:**

1. **Using psql interactively:**
   ```bash
   docker-compose exec postgres psql -U ingestion_user -d legal_ingestion
   # Then paste or run the contents of init.sql
   ```

2. **Using heredoc:**
   ```bash
   docker-compose exec -T postgres psql -U ingestion_user -d legal_ingestion << EOF
   $(cat init.sql)
   EOF
   ```

### Verify Docker Setup

Check if PostgreSQL and pgvector are running correctly:

**Check container status:**
```bash
docker-compose ps
```

**Check pgvector is installed:**
```bash
docker-compose exec postgres psql -U ingestion_user -d legal_ingestion -c "CREATE EXTENSION IF NOT EXISTS vector; SELECT extname FROM pg_extension WHERE extname = 'vector';"
```

**Check tables were created:**
```bash
docker-compose exec postgres psql -U ingestion_user -d legal_ingestion -c "\dt"
```

**Stop and clean up:**
```bash
docker-compose down -v  # -v removes volumes; omit to keep data
```

### Database Schema

The database schema (pgvector extension, tables, and indexes) is defined in `init.sql`.

**pdf_documents table** - Stores PDF file metadata:
- `id` - BIGSERIAL primary key for large-scale data
- `file_name` - PDF file name
- `file_path` - Full path to the file
- `sha256` - SHA256 hash of file content (unique, for deduplication)
- `file_size` - Size in bytes
- `status` - Processing status: NEW | PROCESSING | DONE | FAILED
- `error_msg` - Error message if processing failed
- `created_at` - Timestamp when record was created
- `processed_at` - Timestamp when file was processed

**pdf_chunks table** - Stores text chunks/pages from PDFs with embeddings:
- `id` - BIGSERIAL primary key
- `doc_id` - Foreign key to pdf_documents (with CASCADE delete)
- `page_no` - Page or section number (for legal citation reference)
- `chunk_index` - Multiple chunks per page support (default 0)
- `text` - Extracted text content
- `embedding` - Vector embeddings (1536 dimensions) for semantic search (OpenAI compatible)
- `meta` - JSON metadata (char_count, extractor, language, etc.)
- `created_at` - Timestamp when chunk was created
- **Unique constraint:** (doc_id, page_no, chunk_index) - Prevents duplicate chunks

**Key features:**
- **Deduplication**: SHA256 hash prevents reprocessing identical files
- **Status tracking**: Monitor processing pipeline (NEW → PROCESSING → DONE/FAILED)
- **Error handling**: error_msg stores failure reasons
- **Chunk management**: Support for splitting pages into multiple chunks
- **Legal citations**: page_no enables precise legal document references
- **Cascade delete**: Deleting a document automatically removes its chunks
- **Flexible metadata**: JSONB for storing extraction metadata

### Database Connection Configuration

Add database connection details to `src/main/resources/config.properties`:

```properties
# PDF Ingestion Directory Configuration
pdf.ingestion.directory=/path/to/pdfs

# Maximum file size to process (in bytes)
max.file.size=1048576

# Database Configuration
db.url=jdbc:postgresql://localhost:5432/legal_ingestion
db.user=ingestion_user
db.password=ingestion_pass

# Vector Search / Embeddings Configuration
embeddings.enabled=true
embeddings.model=text-embedding-3-small
# IMPORTANT: OpenAI API key should be set in .env file:
#   echo 'OPENAI_API_KEY=sk-...' > .env
```

**OpenAI API Key Setup:**
```bash
# Create .env file with your API key (required for embed-missing and search commands)
echo 'OPENAI_API_KEY=sk-your-actual-api-key' > .env

# Verify it's set
cat .env
```

**Note:** The `.env` file takes precedence when set, but you can still override with environment variable:
```bash
export OPENAI_API_KEY="sk-override-value"  # This takes precedence over .env
```

## Project Structure

```
legal-ingestion/
├── src/
│   ├── main/
│   │   ├── java/com/ingestion/
│   │   │   ├── AppMain.java                (Main CLI entry point, subcommand routing)
│   │   │   ├── PDFIngestionApp.java        (PDF ingestion pipeline)
│   │   │   ├── PDFReader.java              (PDF reading & text extraction)
│   │   │   ├── DocumentRepo.java           (PDF document persistence)
│   │   │   ├── ChunkRepo.java              (PDF chunk + embedding persistence)
│   │   │   ├── Sha256Hasher.java           (SHA256 file hashing)
│   │   │   ├── OpenAIEmbeddingClient.java  (OpenAI embeddings API)
│   │   │   ├── EmbedMissingCommand.java    (embed-missing command)
│   │   │   ├── SearchCommand.java          (search command)
│   │   │   ├── ChunkRow.java               (Data class for chunks)
│   │   │   ├── SearchHit.java              (Data class for search results)
│   │   │   └── HelloWorld.java             (Hello World example)
│   │   └── resources/
│   │       ├── config.properties           (Configuration)
│   │       └── application.properties
│   └── test/
│       └── java/com/ingestion/
│           ├── PDFReaderTest.java          (PDF reader tests)
│           └── HelloWorldTest.java         (Hello World tests)
├── docker-compose.yml                      (PostgreSQL + pgvector Docker setup)
├── init.sql                                (Database initialization script)
├── pom.xml
├── README.md
├── OPTIMIZATION_SUMMARY.md                 (MVP L1 optimization details)
├── VECTOR_SEARCH_DOCS.md                   (Complete vector search documentation)
├── VECTOR_SEARCH_QUICK_REF.md             (Vector search quick reference)
└── VECTOR_SEARCH_SUMMARY.md               (Implementation summary)
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

**Run Application (Development)**
```bash
# Ingest PDFs (default config.properties directory)
mvn compile exec:java -Dexec.mainClass="com.ingestion.AppMain" -Dexec.args="ingest"

# Ingest with custom directory
mvn compile exec:java -Dexec.mainClass="com.ingestion.AppMain" -Dexec.args="ingest /path/to/pdfs"

# Generate embeddings (requires OPENAI_API_KEY in .env or environment variable)
mvn compile exec:java -Dexec.mainClass="com.ingestion.AppMain" \
  -Dexec.args="embed-missing --limit 100"

# Search (requires OPENAI_API_KEY in .env or environment variable)
mvn compile exec:java -Dexec.mainClass="com.ingestion.AppMain" \
  -Dexec.args='search --query "your search" --topK 10'
```

**Run Tests with Coverage**
```bash
mvn test -DargLine="-Xmx1024m"
```

## Features

### PDF Ingestion (ingest command)
- **Find all PDF files** in a given directory (including subdirectories)
- **Extract text by page** from PDF files using PDFBox (1-based page numbering for legal citations)
- **Calculate SHA256 hash** for content deduplication  
- **Database persistence** - Store document metadata and page chunks in PostgreSQL
- **Status tracking** - Monitor processing (NEW → PROCESSING → DONE/FAILED)
- **Error resilience** - Single PDF failure doesn't halt pipeline
- **Idempotent processing** - Re-run safely without creating duplicates

### Vector Search (embed-missing & search commands)
- **Generate embeddings** - Call OpenAI text-embedding-3-small (1536 dimensions)
- **Batch processing** - Configurable limits with error resilience
- **Semantic search** - Find documents by meaning using cosine distance similarity
- **Legal citations** - Return file name, path, and page number for each result
- **Flexible scoring** - Similarity scores on search results

### Database Storage
- Store extracted PDF content and metadata in PostgreSQL
- Vector embeddings support with pgvector extension (1536 dimensions)
- Semantic search capabilities using vector similarity
- Searchable text content for full-text queries
- Timestamps for tracking processing history

### Example Usage - Command Line

```bash
# 1. Ingest PDFs
mvn exec:java -Dexec.mainClass="com.ingestion.AppMain" -Dexec.args="ingest ~/Documents/PDFs"

# 2. Generate embeddings for 200 chunks (requires .env with OPENAI_API_KEY)
mvn exec:java -Dexec.mainClass="com.ingestion.AppMain" -Dexec.args="embed-missing --limit 200"

# 3. Search for documents
mvn exec:java -Dexec.mainClass="com.ingestion.AppMain" -Dexec.args="search --query 'contract terms' --topK 10"
```

### Example Usage - Programmatic (Java)

```java
// Using PDF Reader to extract pages
PDFReader pdfReader = new PDFReader();
List<PDFReader.PageText> pages = pdfReader.extractPages("/path/to/file.pdf");

// Using DocumentRepo for persistence
Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
long docId = DocumentRepo.upsertAndGetId(conn, fileName, filePath, sha256, fileSize);

// Using ChunkRepo for chunks
ChunkRepo.insertPageChunks(conn, docId, pages);

// Using search (after embeddings are generated)
OpenAIEmbeddingClient client = new OpenAIEmbeddingClient(apiKey);
float[] queryEmbedding = client.embed("search query");
List<SearchHit> results = ChunkRepo.searchByVector(conn, queryEmbedding, 10);
```

## Dependencies

- **Apache PDFBox** (2.0.29) - For reading and extracting text from PDF files
- **PostgreSQL JDBC Driver** (42.7.3) - For database connectivity
- **Jackson Databind** (2.16.1) - For JSON serialization (OpenAI API integration)
- **JUnit 5** (5.10.0) - For unit testing
- **pgvector** (PostgreSQL extension) - For vector similarity search
- **Docker & Docker Compose** (optional) - For PostgreSQL database container with pgvector

## License

Apache-2.0 License

## Additional Documentation

For detailed information on the vector search feature:

- **[VECTOR_SEARCH_DOCS.md](VECTOR_SEARCH_DOCS.md)** - Complete guide with architecture, examples, and troubleshooting
- **[VECTOR_SEARCH_QUICK_REF.md](VECTOR_SEARCH_QUICK_REF.md)** - Quick reference for CLI commands
- **[VECTOR_SEARCH_SUMMARY.md](VECTOR_SEARCH_SUMMARY.md)** - Implementation summary and acceptance criteria
- **[OPTIMIZATION_SUMMARY.md](OPTIMIZATION_SUMMARY.md)** - MVP Level 1 optimization details (production-ready)
