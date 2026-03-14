-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Drop tables if they exist (in reverse order of creation due to foreign keys)
DROP TABLE IF EXISTS pdf_payment_extraction_runs;
DROP TABLE IF EXISTS payment_records;
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
  ts tsvector GENERATED ALWAYS AS (to_tsvector('english', coalesce(text,''))) STORED,  -- Full-text search index
  meta JSONB NOT NULL DEFAULT '{}'::jsonb,   -- Reserved: char_count, extractor, language...

  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

  UNIQUE (doc_id, page_no, chunk_index)
);

CREATE INDEX IF NOT EXISTS idx_pdf_chunks_doc_page ON pdf_chunks(doc_id, page_no);

-- Full-text search index (for hybrid search keyword matching)
CREATE INDEX IF NOT EXISTS idx_pdf_chunks_ts ON pdf_chunks USING GIN (ts);

-- Cosine distance index (commonly used for OpenAI embeddings)
CREATE INDEX IF NOT EXISTS idx_pdf_chunks_embedding
ON pdf_chunks USING ivfflat (embedding vector_cosine_ops)
WITH (lists = 100);

CREATE TABLE IF NOT EXISTS payment_records (
  id BIGSERIAL PRIMARY KEY,

  pdf_document_id BIGINT NOT NULL
      REFERENCES pdf_documents(id) ON DELETE CASCADE,

  statement_index INTEGER,
  statement_period_start DATE,
  statement_period_end DATE,

  payment_date DATE,
  category VARCHAR(50) NOT NULL,

  total_amount NUMERIC(12,2) NOT NULL,
  principal_amount NUMERIC(12,2),
  interest_amount NUMERIC(12,2),
  escrow_amount NUMERIC(12,2),
  tax_amount NUMERIC(12,2),
  insurance_amount NUMERIC(12,2),

  currency VARCHAR(10) NOT NULL DEFAULT 'USD',

  payer_name TEXT,
  payee_name TEXT,

  property_address TEXT,
  property_city VARCHAR(100),
  property_state VARCHAR(50),
  property_zip VARCHAR(20),

  description TEXT,

  source_page INTEGER,
  source_snippet TEXT,
  confidence NUMERIC(4,3),
  raw_llm_json JSONB,

  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS pdf_payment_extraction_runs (
  id BIGSERIAL PRIMARY KEY,

  pdf_document_id BIGINT NOT NULL
      REFERENCES pdf_documents(id) ON DELETE CASCADE,

  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  model_name VARCHAR(100),
  prompt_version VARCHAR(50),

  statement_count INTEGER,
  payment_count INTEGER,

  is_scanned BOOLEAN,
  error_msg TEXT,
  raw_llm_response JSONB,

  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_payment_records_pdf_document_id
  ON payment_records(pdf_document_id);

CREATE INDEX IF NOT EXISTS idx_payment_records_payment_date
  ON payment_records(payment_date);

CREATE INDEX IF NOT EXISTS idx_payment_records_category
  ON payment_records(category);

CREATE INDEX IF NOT EXISTS idx_payment_records_property_city
  ON payment_records(property_city);

CREATE INDEX IF NOT EXISTS idx_payment_records_pdf_statement
  ON payment_records(pdf_document_id, statement_index);