-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Drop tables if they exist (in reverse order of creation due to foreign keys)
DROP TABLE IF EXISTS pdf_chunks;
DROP TABLE IF EXISTS pdf_documents;

-- Create table for PDF documents
CREATE TABLE IF NOT EXISTS pdf_documents (
  id BIGSERIAL PRIMARY KEY,
  file_name VARCHAR(255) NOT NULL,
  file_path TEXT NOT NULL,
  sha256 CHAR(64) UNIQUE,                 -- File content hash for deduplication
  file_size BIGINT NOT NULL,

  status VARCHAR(20) NOT NULL DEFAULT 'NEW',  -- NEW | PROCESSING | DONE | FAILED
  error_msg TEXT,

  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  processed_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pdf_documents_status ON pdf_documents(status);

-- Create table for PDF chunks (pages/sections)
CREATE TABLE IF NOT EXISTS pdf_chunks (
  id BIGSERIAL PRIMARY KEY,
  doc_id BIGINT NOT NULL REFERENCES pdf_documents(id) ON DELETE CASCADE,

  page_no INT NOT NULL,                      -- Legal citation reference
  chunk_index INT NOT NULL DEFAULT 0,         -- Multiple chunks per page support (MVP: all 0)
  text TEXT NOT NULL,

  embedding vector(1536),                    -- OpenAI 1536-dimensional vector
  meta JSONB NOT NULL DEFAULT '{}'::jsonb,   -- Reserved: char_count, extractor, language...

  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

  UNIQUE (doc_id, page_no, chunk_index)
);

CREATE INDEX IF NOT EXISTS idx_pdf_chunks_doc_page ON pdf_chunks(doc_id, page_no);
