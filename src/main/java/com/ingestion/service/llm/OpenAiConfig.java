package com.ingestion.service.llm;

/**
 * Configuration for OpenAI LLM integration.
 * Reads from system properties (set by Dotenv from .env file).
 */
public class OpenAiConfig {

    private static final String API_KEY_ENV_VAR = "OPENAI_API_KEY";
    private static final String MODEL_ENV_VAR = "OPENAI_MODEL";
    private static final String DEFAULT_MODEL = "gpt-4o-mini";

    /**
     * Get OpenAI API key from system properties.
     * Throws exception if not configured.
     */
    public static String getApiKey() throws Exception {
        String apiKey = System.getProperty(API_KEY_ENV_VAR);
        if (apiKey == null || apiKey.isEmpty()) {
            throw new Exception(
                "OPENAI_API_KEY not configured. " +
                "Set OPENAI_API_KEY in .env file or as environment variable."
            );
        }
        return apiKey;
    }

    /**
     * Get OpenAI model name from system properties, or default if not set.
     */
    public static String getModel() {
        String model = System.getProperty(MODEL_ENV_VAR);
        return model != null && !model.isEmpty() ? model : DEFAULT_MODEL;
    }

    /**
     * Get OpenAI model name from system properties with explicit default.
     */
    public static String getModel(String defaultModel) {
        String model = System.getProperty(MODEL_ENV_VAR);
        return model != null && !model.isEmpty() ? model : (defaultModel != null ? defaultModel : DEFAULT_MODEL);
    }

    /**
     * Create a configured LLM client ready for use.
     */
    public static LlmClient createLlmClient() throws Exception {
        return new OpenAiLlmClient();
    }

    /**
     * Create a configured LLM client with explicit model override.
     */
    public static LlmClient createLlmClient(String modelOverride) throws Exception {
        String apiKey = getApiKey();
        return new OpenAiLlmClient(apiKey, modelOverride != null ? modelOverride : getModel());
    }
}
