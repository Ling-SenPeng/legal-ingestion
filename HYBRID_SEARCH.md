# Hybrid Search Implementation (Phase 10)

## Overview

Implemented **hybrid search** combining vector similarity and PostgreSQL full-text search for legal document retrieval. This approach combines semantic understanding (vector embeddings) with precise keyword matching (full-text search) to handle both fuzzy legal queries and exact references.

## Architecture

### Components

1. **ChunkRepo.searchByVectorDetailed()** - Vector search with detailed chunk information
   - Uses pgvector cosine distance: `1 - (embedding <=> queryVector)`
   - Returns: chunkId, docId, fileName, filePath, pageNo, text, vectorScore

2. **ChunkRepo.searchByKeyword()** - PostgreSQL full-text search
   - Uses tsvector + plainto_tsquery for English language processing
   - Ranks with ts_rank (normalized TF-IDF-like scoring)
   - Returns: chunkId, docId, fileName, filePath, pageNo, text, keywordScore

3. **HybridSearchCommand** - Score fusion and result merging
   - Generates query embedding using OpenAI API
   - Calls both search methods
   - Merges results by chunk_id (de-duplication)
   - Applies weighted scoring formula:
     ```
     finalScore = alpha * vectorScore + (1 - alpha) * keywordScore
     ```
   - Returns top-K results sorted by finalScore

4. **HybridSearchHit** - Result data class
   - Fields: chunkId, docId, fileName, filePath, pageNo, textPreview
   - Scores: vectorScore, keywordScore, finalScore
   - String format: `Score:0.789 [V:0.82 K:0.71] document.pdf (Page 42) | preview...`

### Database Schema

Enhanced `pdf_chunks` table with full-text search support:

```sql
-- Generated tsvector column for full-text search (English)
ts tsvector GENERATED ALWAYS AS (to_tsvector('english', coalesce(text,''))) STORED

-- GIN index for efficient full-text search
CREATE INDEX IF NOT EXISTS idx_pdf_chunks_ts ON pdf_chunks USING GIN (ts);
```

## Usage

### CLI Command

```bash
mvn exec:java -Dexec.args="hybrid-search --query \"case 2024-001\" --topK 10 --vectorTopN 20 --keywordTopN 20 --alpha 0.7"
```

### Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `--query` | required | Search query (keywords, names, case numbers, semantic phrases) |
| `--topK` | 10 | Number of final merged results to return |
| `--vectorTopN` | 20 | Number of vector search results to fetch before merging |
| `--keywordTopN` | 20 | Number of keyword search results to fetch before merging |
| `--alpha` | 0.7 | Vector weight in [0, 1]; 0.7 = 70% vector, 30% keyword |

### Configuration

Environment variable required:
```bash
export OPENAI_API_KEY="sk-..."
```

Or use `.env` file:
```
OPENAI_API_KEY=sk-...
```

## Search Behavior

### Vector-Weighted (alpha=0.7, default)
- Favors semantic similarity
- Good for: conceptual queries, complex legal interpretations
- Example: `--query "unfair competition laws"`

### Keyword-Weighted (alpha=0.3)
- Favors exact matches in names, citations, references
- Good for: case numbers, party names, statute sections
- Example: `--query "United States v. Microsoft" --alpha 0.3`

### Balanced (alpha=0.5)
- Equal weight to both approaches
- Good for: mixed queries
- Example: `--query "antitrust practices" --alpha 0.5`

## Examples

### Search for Case References
```bash
mvn exec:java -Dexec.args="hybrid-search --query \"Case 2024-001\" --alpha 0.3 --topK 10"
```

### Search for Legal Concepts
```bash
mvn exec:java -Dexec.args="hybrid-search --query \"tort liability negligence\" --alpha 0.8 --topK 10"
```

### Search for Party Names
```bash
mvn exec:java -Dexec.args="hybrid-search --query \"Apple Inc v. Samsung\" --alpha 0.4 --topK 5"
```

### Fetch More Results for Analysis
```bash
mvn exec:java -Dexec.args="hybrid-search --query \"contract interpretation\" --vectorTopN 50 --keywordTopN 50 --topK 20"
```

## Implementation Details

### Score Merging Logic

1. **Vector-only hits**: Use vector score; keyword score = 0
   ```
   finalScore = 0.7 * vectorScore
   ```

2. **Keyword-only hits**: Use keyword score; vector score = 0
   ```
   finalScore = 0.3 * keywordScore
   ```

3. **Merged hits** (in both vector and keyword results):
   ```
   finalScore = 0.7 * vectorScore + 0.3 * keywordScore
   ```

### Result De-duplication

- Same chunk can appear in both vector and keyword results
- Merged by `chunkId` - only one instance per chunk in final results
- Both scores combined using the weighted formula

### Performance Considerations

- **vectorTopN=20, keywordTopN=20**: Typical usage (fast, ~200ms per query)
- **vectorTopN=50, keywordTopN=50**: Extended analysis (~500ms per query)
- **vectorTopN=5, keywordTopN=5**: Quick preview (~100ms per query)

## Recent Commits

- **81764b8**: "Implement hybrid search (vector + keyword fusion) with configurable alpha weighting"
  - Added searchByVectorDetailed() and searchByKeyword() to ChunkRepo
  - Created HybridSearchCommand for score fusion
  - Registered hybrid-search subcommand in AppMain
  - Enhanced HybridSearchHit with score setters

- **76f576d**: "Implement sliding window paragraph chunking for Legal RAG (windowSize=2, stride=1)"
  - Changed from paragraph overlap to sliding window strategy
  - Maintains context windows across paragraph boundaries

## Related Features

- **Vector Search** (Phase 4): `search` command with pure cosine distance
- **Keyword Search**: Embedded in hybrid search via tsvector+ts_rank
- **Paragraph Chunking** (Phase 7-9): Sliding window chunks with 2-paragraph context
- **Embedding Pipeline** (Phase 4-5): Automatic embedding generation and OpenAI integration

## Future Enhancements

- [ ] Support for multiple query expansion strategies
- [ ] Learning-based alpha optimization based on usage patterns
- [ ] Configurable keyword/vector preprocessing (stemming, lemmatization)
- [ ] Query time weighting based on field importance (names > descriptions)
- [ ] Result explanation (which scores contributed to final ranking)
