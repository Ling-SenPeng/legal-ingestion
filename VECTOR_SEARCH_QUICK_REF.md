# Vector Search Quick Reference

## Setup

```bash
# 1. Create .env file with OpenAI API key
echo 'OPENAI_API_KEY=sk-your-key-here' > .env

# 2. Build
mvn clean package

# 3. Start PostgreSQL (if using Docker)
docker-compose up -d
```

## Commands

### ingest PDFs
```bash
# Default directory from config.properties
mvn exec:java -Dexec.mainClass="com.ingestion.AppMain" -Dexec.args="ingest"

# Custom directory
mvn exec:java -Dexec.mainClass="com.ingestion.AppMain" -Dexec.args="ingest /path/to/pdfs"
```

### Generate Embeddings
```bash
# Default: limit=100
mvn exec:java -Dexec.mainClass="com.ingestion.AppMain" -Dexec.args="embed-missing"

# Specific limit (get 500 chunks)
mvn exec:java -Dexec.mainClass="com.ingestion.AppMain" -Dexec.args="embed-missing --limit 500"

# Custom batch size
mvn exec:java -Dexec.mainClass="com.ingestion.AppMain" -Dexec.args="embed-missing --limit 500 --batchSize 100"
```

### Search
```bash
# Default topK=10
mvn exec:java -Dexec.mainClass="com.ingestion.AppMain" -Dexec.args="search --query 'your search term'"

# Get top 20 results
mvn exec:java -Dexec.mainClass="com.ingestion.AppMain" -Dexec.args="search --query 'breach of contract' --topK 20"

# Multi-word query (must use quotes)
mvn exec:java -Dexec.mainClass="com.ingestion.AppMain" -Dexec.args="search --query 'payment terms and conditions' --topK 5"
```

## Verify Progress

### Check ingestion status
```bash
docker-compose exec postgres psql -U ingestion_user -d legal_ingestion \
  -c "SELECT count(*), status FROM pdf_documents GROUP BY status;"

# Output:
#  count | status
# -------+-----------
#     10 | DONE
#      2 | FAILED
```

### Check embedding progress
```bash
docker-compose exec postgres psql -U ingestion_user -d legal_ingestion \
  -c "SELECT 
       COUNT(*) as total_chunks,
       COUNT(CASE WHEN embedding IS NOT NULL THEN 1 END) as embedded,
       COUNT(CASE WHEN embedding IS NULL THEN 1 END) as missing
     FROM pdf_chunks;"

# Output:
#  total_chunks | embedded | missing
# ---------------+----------+---------
#            150 |      120 |      30
```

### Check recent search results
```bash
docker-compose exec postgres psql -U ingestion_user -d legal_ingestion \
  -c "SELECT c.id, d.file_name, c.page_no, 
             LEFT(c.text, 50) as preview,
             c.embedding IS NOT NULL as has_embedding
      FROM pdf_chunks c
      JOIN pdf_documents d ON d.id = c.doc_id
      LIMIT 10;"
```

## API Costs (OpenAI)

- text-embedding-3-small: ~$0.02 per 1M input tokens
- Typical legal document page: ~1000 tokens
- Cost estimates:
  - 100 pages: ~$0.002
  - 1000 pages: ~$0.02
  - 10000 pages: ~$0.20

## Troubleshooting

| Problem | Solution |
|---------|----------|
| `.env` file not found | Create with: `echo 'OPENAI_API_KEY=sk-...' > .env` |
| `OPENAI_API_KEY not set` | Add to `.env` file in project root |
| `No results from search` | Run `embed-missing` first |
| `PostgreSQL connection refused` | Start Docker: `docker-compose up -d` |
| `vector type does not exist` | Run: `docker-compose exec -T postgres psql -U ingestion_user -d legal_ingestion < init.sql` |
| `API rate limit exceeded` | Reduce `--limit` or wait before retrying |
| `Out of memory` | Reduce `--limit` and `--batchSize` |

## Database Schema Additions

The `pdf_chunks` table now has:
- `embedding vector(1536)` - OpenAI embedding (NULL until embed-missing runs)
- `IVFFLAT index` - Fast approximate nearest neighbor search

## Project Files

- `OpenAIEmbeddingClient.java` - OpenAI API integration
- `AppMain.java` - Multi-command CLI entry point  
- `EmbedMissingCommand.java` - Batch embedding generator
- `SearchCommand.java` - Semantic search
- `ChunkRow.java` - Data class for chunks
- `SearchHit.java` - Data class for search results
- `ChunkRepo.java` - Updated with embedding methods
- `config.properties` - Updated configuration
