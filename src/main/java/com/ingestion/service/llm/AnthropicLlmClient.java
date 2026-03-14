package com.ingestion.service.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * DEPRECATED: OpenAI is the only supported LLM provider.
 * This class is kept for reference only.
 * 
 * Use OpenAiLlmClient instead:
 *   LlmClient client = OpenAiConfig.createLlmClient();
 * 
 * Or use the factory method on PaymentExtractionPipeline:
 *   var pipeline = PaymentExtractionPipeline.withDefaultOpenAi(connection);
 */
@Deprecated(since = "1.0", forRemoval = true)
public class AnthropicLlmClient implements LlmClient {

    private static final String ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String DEFAULT_MODEL = "claude-3-5-sonnet-20241022";
    private static final int MAX_TOKENS = 4096;

    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AnthropicLlmClient(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String complete(String prompt) throws Exception {
        return complete(prompt, DEFAULT_MODEL);
    }

    @Override
    public String complete(String prompt, String model) throws Exception {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new Exception("Anthropic API key is not set");
        }

        if (prompt == null || prompt.isEmpty()) {
            throw new Exception("Prompt cannot be empty");
        }

        // Build request payload
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", model != null ? model : DEFAULT_MODEL);
        requestBody.put("max_tokens", MAX_TOKENS);
        
        // Messages array with single user message
        ObjectNode messageContent = objectMapper.createObjectNode();
        messageContent.put("role", "user");
        messageContent.put("content", prompt);
        
        requestBody.putArray("messages").add(messageContent);
        
        String payload = objectMapper.writeValueAsString(requestBody);

        // Create HTTP request
        HttpRequest request = HttpRequest.newBuilder()
            .uri(new URI(ANTHROPIC_API_URL))
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        // Execute request
        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() != 200) {
            throw new Exception("Anthropic API error: " + response.statusCode() + " - " + response.body());
        }

        // Parse response - Claude returns response in .content[0].text
        ObjectNode responseNode = (ObjectNode) objectMapper.readTree(response.body());
        
        if (!responseNode.has("content") || responseNode.get("content").size() == 0) {
            throw new Exception("Unexpected response format from Anthropic API");
        }

        String responseText = responseNode.get("content").get(0).get("text").asText();
        
        if (responseText == null || responseText.isEmpty()) {
            throw new Exception("Empty response from LLM");
        }

        return responseText;
    }
}
