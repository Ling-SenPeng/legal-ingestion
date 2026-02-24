package com.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * OpenAI Embedding Client for generating 1536-dimensional embeddings.
 * Uses text-embedding-3-small model.
 */
public class OpenAIEmbeddingClient {

	private static final String OPENAI_API_URL = "https://api.openai.com/v1/embeddings";
	private static final String MODEL = "text-embedding-3-small";
	private static final int EMBEDDING_DIMENSION = 1536;
	private static final int MIN_TEXT_LENGTH = 1;
	private static final int MAX_TEXT_LENGTH = 8192;  // OpenAI limit

	private final String apiKey;
	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;

	public OpenAIEmbeddingClient(String apiKey) {
		this.apiKey = apiKey;
		this.httpClient = HttpClient.newHttpClient();
		this.objectMapper = new ObjectMapper();
	}

	/**
	 * Generate an embedding for the given text.
	 *
	 * @param text the text to embed
	 * @return a float array of size 1536, or null if text is empty/too short
	 * @throws Exception if the API call fails
	 */
	public float[] embed(String text) throws Exception {
		if (text == null || text.trim().isEmpty()) {
			return null;
		}

		String trimmedText = text.trim();
		if (trimmedText.length() < MIN_TEXT_LENGTH) {
			return null;
		}

		// Truncate if too long
		if (trimmedText.length() > MAX_TEXT_LENGTH) {
			trimmedText = trimmedText.substring(0, MAX_TEXT_LENGTH);
		}

		// Build request payload
		String payload = String.format(
			"{\"model\": \"%s\", \"input\": %s}",
			MODEL,
			objectMapper.writeValueAsString(trimmedText)
		);

		// Create HTTP request
		HttpRequest request = HttpRequest.newBuilder()
			.uri(new URI(OPENAI_API_URL))
			.header("Authorization", "Bearer " + apiKey)
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(payload))
			.build();

		// Execute request with timeout
		HttpResponse<String> response = httpClient.send(request,
			HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

		if (response.statusCode() != 200) {
			throw new Exception("OpenAI API error: " + response.statusCode() + " - " + response.body());
		}

		// Parse response
		JsonNode root = objectMapper.readTree(response.body());
		JsonNode dataArray = root.get("data");
		if (dataArray == null || !dataArray.isArray() || dataArray.size() == 0) {
			throw new Exception("Invalid response from OpenAI API: no data array");
		}

		JsonNode embedding = dataArray.get(0).get("embedding");
		if (embedding == null || !embedding.isArray()) {
			throw new Exception("Invalid embedding in response");
		}

		// Convert to float array
		float[] result = new float[EMBEDDING_DIMENSION];
		for (int i = 0; i < embedding.size() && i < EMBEDDING_DIMENSION; i++) {
			result[i] = (float) embedding.get(i).asDouble();
		}

		return result;
	}

	/**
	 * Get the API key from environment variable OPENAI_API_KEY or throw exception.
	 *
	 * @return the API key
	 * @throws Exception if the environment variable is not set
	 */
	public static String getApiKeyFromEnv() throws Exception {
		String apiKey = System.getenv("OPENAI_API_KEY");
		if (apiKey == null || apiKey.trim().isEmpty()) {
			throw new Exception("OPENAI_API_KEY environment variable is not set. Please set it before running this command.");
		}
		return apiKey.trim();
	}
}
