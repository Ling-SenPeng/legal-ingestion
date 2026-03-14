package com.ingestion.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * LLM client implementation using OpenAI Chat Completions API.
 * Reads API key and model name from environment configuration.
 *
 * Environment variables:
 * - OPENAI_API_KEY (required): OpenAI API key
 * - OPENAI_MODEL (optional): Model name (default: gpt-4o-mini)
 */
public class OpenAiLlmClient implements LlmClient {

    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String DEFAULT_MODEL = "gpt-4o-mini";
    private static final int MAX_TOKENS = 4096;
    private static final int TIMEOUT_SECONDS = 120;

    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * Create OpenAI client using system properties (set by Dotenv).
     * Reads OPENAI_API_KEY and OPENAI_MODEL from system properties.
     *
     * @throws Exception if OPENAI_API_KEY is not set
     */
    public OpenAiLlmClient() throws Exception {
        this.apiKey = System.getProperty("OPENAI_API_KEY");
        
        if (apiKey == null || apiKey.isEmpty()) {
            throw new Exception(
                "OPENAI_API_KEY system property not set. " +
                "Set it in .env file or as system environment variable."
            );
        }

        // Model from system property or default
        String envModel = System.getProperty("OPENAI_MODEL");
        this.model = envModel != null && !envModel.isEmpty() ? envModel : DEFAULT_MODEL;

        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Create OpenAI client with explicit API key and model.
     * Primarily for testing. Prefer the no-arg constructor for production.
     */
    public OpenAiLlmClient(String apiKey, String model) throws Exception {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new Exception("API key cannot be null or empty");
        }

        this.apiKey = apiKey;
        this.model = model != null && !model.isEmpty() ? model : DEFAULT_MODEL;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String complete(String prompt) throws Exception {
        return complete(prompt, this.model);
    }

    @Override
    public String complete(String prompt, String modelOverride) throws Exception {
        if (prompt == null || prompt.isEmpty()) {
            throw new Exception("Prompt cannot be null or empty");
        }

        String modelToUse = modelOverride != null && !modelOverride.isEmpty() ? modelOverride : this.model;

        // Build request payload
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", modelToUse);
        requestBody.put("max_tokens", MAX_TOKENS);
        requestBody.put("temperature", 0);  // Deterministic for extraction
        
        // Messages array with system and user messages
        ArrayNode messagesArray = requestBody.putArray("messages");
        
        // System message for strict output
        ObjectNode systemMessage = objectMapper.createObjectNode();
        systemMessage.put("role", "system");
        systemMessage.put("content", 
            "You are a financial document analysis expert. Return ONLY valid JSON, " +
            "no markdown formatting, no code blocks, no additional text. " +
            "Return exactly the JSON structure requested, nothing more."
        );
        messagesArray.add(systemMessage);
        
        // User message with the prompt
        ObjectNode userMessage = objectMapper.createObjectNode();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);
        messagesArray.add(userMessage);
        
        String payload = objectMapper.writeValueAsString(requestBody);

        // Create HTTP request
        HttpRequest request = HttpRequest.newBuilder()
            .uri(new URI(OPENAI_API_URL))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        // Execute request
        HttpResponse<String> response;
        try {
            response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new Exception("Failed to connect to OpenAI API: " + e.getMessage(), e);
        }

        if (response.statusCode() != 200) {
            String errorBody = response.body();
            String message = "OpenAI API error: " + response.statusCode();
            
            // Try to extract error message from response
            try {
                JsonNode errorNode = objectMapper.readTree(errorBody);
                if (errorNode.has("error") && errorNode.get("error").has("message")) {
                    String apiErrorMsg = errorNode.get("error").get("message").asText();
                    message += " - " + apiErrorMsg;
                }
            } catch (Exception e) {
                message += " - " + errorBody;
            }
            
            throw new Exception(message);
        }

        // Parse response
        JsonNode responseNode = objectMapper.readTree(response.body());
        
        if (!responseNode.has("choices") || responseNode.get("choices").size() == 0) {
            throw new Exception("Unexpected response format from OpenAI API: no choices in response");
        }

        JsonNode firstChoice = responseNode.get("choices").get(0);
        if (!firstChoice.has("message") || !firstChoice.get("message").has("content")) {
            throw new Exception("Unexpected response format from OpenAI API: no message content");
        }

        String responseText = firstChoice.get("message").get("content").asText();
        
        if (responseText == null || responseText.isEmpty()) {
            throw new Exception("Empty response from OpenAI API");
        }

        return responseText;
    }

    /**
     * Get the model name being used.
     */
    public String getModelName() {
        return model;
    }
}
