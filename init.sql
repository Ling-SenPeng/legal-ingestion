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
