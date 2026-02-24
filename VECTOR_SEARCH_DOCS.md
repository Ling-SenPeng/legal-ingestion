## Vector Search Feature - Embed Missing & Search Commands

### Overview

Added full vector search capability to the PDF ingestion system using OpenAI embeddings (1536-dimensional) and PostgreSQL pgvector. The system now supports two new commands:

1. **embed-missing** - Batch generate and store embeddings for chunks missing embeddings
2. **search** - Semantic search across ingested PDFs using vector similarity

### Architecture

```
OpenAI Embeddings API (text-embedding-3-small)
         ↓
1. embed-missing: PDF chunks (text) → embeddings → PostgreSQL
2. search: User query → embedding → pgvector cosine search → legal citations
         ↓
PostgreSQL pgvector + custom SQL with cosine distance
```

### New Files Created

#### 1. **OpenAIEmbeddingClient.java**
- HTTP client for OpenAI embeddings API
- Model: `text-embedding-3-small` (1536 dimensions)
- Features:
  - Error handling and API error responses
  - Text preprocessing (min 1 char, max 8192 chars)
  - Returns `float[]` of size 1536
  - API key from environment variable: `OPENAI_API_KEY`
- Key Method: `embed(String text) → float[]`

#### 2. **AppMain.java**
- Main entry point with subcommand routing
- Supports: `ingest`, `embed-missing`, `search`
- Loads configuration from `config.properties`
- Handles command-line arguments parsing

#### 3. **EmbedMissingCommand.java**
- Batch processes chunks with NULL embeddings
- Resilient to errors (continues on failure)
- Parameters: `--limit 100`, `--batchSize 50`
- Prints progress and summary
- Tracks success/failure counts

#### 4. **SearchCommand.java**
- Semantic search via vector similarity
- Parameters: `--query "..."`, `--topK 10`
- Outputs results with legal citations (filename, path, page number)
- Shows similarity scores and text previews

#### 5. **Data Classes**
- **ChunkRow.java** - Represents a chunk from DB (id, text)
- **SearchHit.java** - Search result (fileName, filePath, pageNo, text, score)

### Modified Files

#### 1. **ChunkRepo.java**
Added three new static methods:

```java
// Fetch chunks with NULL embeddings
public static List<ChunkRow> fetchChunksMissingEmbedding(Connection conn, int limit)

// Store embedding vector for a chunk
public static void updateEmbedding(Connection conn, long chunkId, float[] embedding)

// Search by vector similarity
public static List<SearchHit> searchByVector(Connection conn, float[] queryVec, int topK)
```

Search query:
```sql
SELECT d.file_name, d.file_path, c.page_no, c.text, 
       (1 - (c.embedding <=> ?::vector)) as score
FROM pdf_chunks c
JOIN pdf_documents d ON d.id = c.doc_id
WHERE c.embedding IS NOT NULL
ORDER BY c.embedding <=> ?::vector
LIMIT ?;
```

#### 2. **pom.xml**
- Added `jackson-databind:2.16.1` for JSON serialization
- Changed mainClass from `PDFIngestionApp` to `AppMain`

#### 3. **config.properties**
- Updated embeddings configuration
- Added note about OPENAI_API_KEY environment variable

### Usage

#### Prerequisites
```bash
# Create .env file with OpenAI API key (required for embed-missing and search)
echo 'OPENAI_API_KEY=sk-...' > .env

# Build the project
mvn clean package
```

#### 1. Embed Missing Embeddings
```bash
# Generate embeddings for all chunks without embeddings (default: limit=100)
mvn exec:java -Dexec.mainClass="com.ingestion.AppMain" -Dexec.args="embed-missing"

# Custom limits
mvn exec:java -Dexec.mainClass="com.ingestion.AppMain" -Dexec.args="embed-missing --limit 200 --batchSize 50"
```

**Output:**
```
=== Embed Missing Embeddings ===
Limit: 50, Batch Size: 25
Fetching chunks with missing embeddings (limit=50)...
Found 45 chunk(s) missing embeddings

Progress: 25/45 chunks embedded (success=25, failed=0)
Progress: 45/45 chunks embedded (success=45, failed=0)

=== Summary ===
Success: 45
Failed: 0
Total processed: 45
Elapsed time: 12.34s
```

#### 2. Semantic Search
```bash
# Basic search (default topK=10)
mvn exec:java -Dexec.mainClass="com.ingestion.AppMain" -Dexec.args="search --query 'breach of contract'"

# Custom top-K
mvn exec:java -Dexec.mainClass="com.ingestion.AppMain" -Dexec.args="search --query 'liability' --topK 20"
```

**Output:**
```
=== Vector Search ===
Query: breach of contract
TopK: 10

Generating embedding for query...
Query embedding generated (1536 dimensions)

Searching chunks...
Found 10 result(s):

[1] Score: 0.8234 | File: contract_2024.pdf | Page: 5
    Path: /path/to/contract_2024.pdf
    Preview: "In case of breach by either party, the breaching party shall be liable..."
    Full text length: 1250 chars

[2] Score: 0.7896 | File: terms_conditions.pdf | Page: 12
    Path: /path/to/terms_conditions.pdf
    Preview: "Any material breach of this agreement shall result in immediate..."
    Full text length: 980 chars

...

=== Summary ===
Total results: 10
Top score: 0.8234
Bottom score: 0.6521
```

### Database Schema (Read-Only - No Changes)

The existing schema supports vector search out-of-the-box:
```sql
CREATE TABLE pdf_chunks (
  id BIGSERIAL PRIMARY KEY,
  doc_id BIGINT NOT NULL,
  page_no INT NOT NULL,
  chunk_index INT NOT NULL DEFAULT 0,
  text TEXT NOT NULL,
  embedding vector(1536),          -- OpenAI embedding
  meta JSONB NOT NULL,
  created_at TIMESTAMP NOT NULL,
  UNIQUE (doc_id, page_no, chunk_index)
);

CREATE INDEX idx_pdf_chunks_embedding
ON pdf_chunks USING ivfflat (embedding vector_cosine_ops)
WITH (lists = 100);
```

### Error Handling

**embed-missing:**
- Resilient to API failures - prints failed chunk IDs and continues
- Skips empty/short text chunks (returns null embedding)
- Database errors fail fast with clear message

**search:**
- Validates query is not empty/too short
- Requires OPENAI_API_KEY environment variable
- Database errors with stack trace
- No results handled gracefully

### Performance Considerations

**embed-missing:**
- Default batch size: 50 chunks (configurable)
- OpenAI API cost: ~$0.02 per 1M tokens (text-embedding-3-small)
- PostgreSQL INSERT batch: all-or-nothing per batch
- Typical throughput: 10-20 chunks/second (depends on API)

**search:**
- pgvector IVFFlat index enables ~10-50x faster search on 1M+ vectors
- Cosine distance calculation: O(1536) per comparison
- Join with pdf_documents adds minimal overhead
- Result limit: typically 10-100 rows returned

### Testing Examples

#### Test 1: Full pipeline with embeddings
```bash
# 1. Ingest PDFs (creates chunks with NULL embeddings)
mvn exec:java -Dexec.mainClass="com.ingestion.AppMain" -Dexec.args="ingest"

# 2. Generate embeddings (requires .env with OPENAI_API_KEY)
mvn exec:java -Dexec.mainClass="com.ingestion.AppMain" -Dexec.args="embed-missing --limit 1000"

# 3. Verify in database
docker-compose exec postgres psql -U ingestion_user -d legal_ingestion \
  -c "SELECT COUNT(*) FROM pdf_chunks WHERE embedding IS NOT NULL;"
# Should show: count = (number of successfully embedded chunks)

# 4. Search
mvn exec:java -Dexec.mainClass="com.ingestion.AppMain" -Dexec.args="search --query 'legal term here' --topK 10"
```

#### Test 2: Error resilience
```bash
# Verify failed chunks are printed but process continues
mvn exec:java -Dexec.mainClass="com.ingestion.AppMain" -Dexec.args="embed-missing --limit 50"

# Output should show individual failures printed but final summary shows attempts
```

#### Test 3: Search quality
```bash
# Test multiple queries with different strategies
mvn exec:java -Dexec.mainClass="com.ingestion.AppMain" -Dexec.args="search --query 'contract' --topK 5"
mvn exec:java -Dexec.mainClass="com.ingestion.AppMain" -Dexec.args="search --query 'payment terms' --topK 10"
mvn exec:java -Dexec.mainClass="com.ingestion.AppMain" -Dexec.args="search --query 'breach liability damages' --topK 20"

# Verify results match expected legal context
```

### Backward Compatibility

✅ **No breaking changes:**
- Existing `ingest` command still works via `AppMain ingest` delegation
- Database schema unchanged (embedding column already existed)
- All existing PDF processing logic intact
- PDFIngestionApp still available for direct use

### Future Enhancements

1. **Batch embedding API** - Use OpenAI batch API for lower cost
2. **Caching** - Cache query embeddings to reduce API calls
3. **Filtering** - Search by date, document type before similarity
4. **Re-ranking** - Use different models to re-rank top results
5. **Multi-field search** - Search document metadata + text
6. **Embedding versioning** - Track embedding model versions
7. **Async processing** - Queue embeddings for background processing
8. **Alternative models** - Support Cohere, Hugging Face, local embeddings

### Troubleshooting

**Issue:** `OPENAI_API_KEY environment variable is not set`
- **Solution:** Create `.env` file: `echo 'OPENAI_API_KEY=sk-...' > .env` or set environment variable: `export OPENAI_API_KEY="sk-..."`

**Issue:** `Search returns no results`
- **Cause:** No embeddings generated yet
- **Solution:** Run `embed-missing` first to populate embeddings

**Issue:** `PostgreSQL vector type not found`
- **Cause:** pgvector extension not installed
- **Solution:** Run `init.sql` against PostgreSQL database

**Issue:** `OpenAI API rate limit exceeded`
- **Cause:** Too many concurrent requests
- **Solution:** Reduce `--batchSize` or add delay between requests

**Issue:** Java heap out of memory
- **Cause:** Large batch size with big texts
- **Solution:** Reduce `--limit` and `--batchSize` parameters

### Build & Deployment

```bash
# Development/Testing
mvn clean compile exec:java -Dexec.mainClass="com.ingestion.AppMain" \
  -Dexec.args="search --query test"

# Production build
mvn clean package

# Run commands (requires .env with OPENAI_API_KEY)
mvn exec:java -Dexec.mainClass="com.ingestion.AppMain" -Dexec.args="embed-missing --limit 500"
mvn exec:java -Dexec.mainClass="com.ingestion.AppMain" -Dexec.args="search --query 'term' --topK 20"
```

### Git Commit Info

**Commit:** `310791f`  
**Message:** Add vector search: embed-missing and search commands with OpenAI integration  
**Files Changed:** 9
- New: OpenAIEmbeddingClient, AppMain, EmbedMissingCommand, SearchCommand, ChunkRow, SearchHit
- Modified: ChunkRepo, pom.xml, config.properties
