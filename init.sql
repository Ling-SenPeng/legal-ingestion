-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Create table for PDF documents
CREATE TABLE IF NOT EXISTS pdf_documents (
    id SERIAL PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    file_path TEXT NOT NULL,
    sha256 CHAR(64) UNIQUE,
    file_size BIGINT NOT NULL,
    status VARCHAR(20) DEFAULT 'NEW',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP
);

-- Create table for PDF chunks (pages/sections)
CREATE TABLE IF NOT EXISTS pdf_chunks (
    id SERIAL PRIMARY KEY,
    doc_id INT REFERENCES pdf_documents(id),
    page_no INT,
    text TEXT,
    embedding vector(1536),
    meta JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for better query performance
CREATE INDEX idx_file_path ON pdf_documents(file_path);
CREATE INDEX idx_sha256 ON pdf_documents(sha256);
CREATE INDEX idx_status ON pdf_documents(status);
CREATE INDEX idx_processed_at ON pdf_documents(processed_at);
CREATE INDEX idx_doc_id ON pdf_chunks(doc_id);
CREATE INDEX idx_page_no ON pdf_chunks(page_no);
CREATE INDEX idx_chunk_embedding ON pdf_chunks USING ivfflat (embedding vector_cosine_ops);
