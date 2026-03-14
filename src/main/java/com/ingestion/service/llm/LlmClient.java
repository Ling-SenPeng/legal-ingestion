package com.ingestion.service.llm;

/**
 * Abstraction for LLM completion requests.
 * Implementations can use different LLM providers (Claude, OpenAI, etc.).
 * Returns raw string response from the LLM.
 */
public interface LlmClient {

    /**
     * Send a prompt to the LLM and get the response.
     *
     * @param prompt the prompt/instruction to send to the LLM
     * @return the raw string response from the LLM
     * @throws Exception if the LLM call fails
     */
    String complete(String prompt) throws Exception;

    /**
     * Send a prompt to the LLM with a specific model context/parameters.
     *
     * @param prompt the prompt/instruction to send to the LLM
     * @param model the model name to use (e.g., "claude-3-5-sonnet")
     * @return the raw string response from the LLM
     * @throws Exception if the LLM call fails
     */
    String complete(String prompt, String model) throws Exception;
}
