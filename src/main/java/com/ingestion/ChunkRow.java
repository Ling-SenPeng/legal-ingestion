package com.ingestion;

/**
 * Data class representing a chunk row from the database.
 * Used for fetching chunks missing embeddings.
 */
public class ChunkRow {
	public final long id;
	public final String text;

	public ChunkRow(long id, String text) {
		this.id = id;
		this.text = text;
	}

	@Override
	public String toString() {
		return "ChunkRow{" +
			"id=" + id +
			", textLength=" + (text != null ? text.length() : 0) +
			'}';
	}
}
