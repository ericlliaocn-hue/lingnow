package cc.lingnow.llm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for LLM clients
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "lingnow.llm")
public class LlmProperties {

    /**
     * Base URL for the API (e.g., https://api.openai.com/v1)
     */
    private String baseUrl;

    /**
     * API Key
     */
    private String apiKey;

    /**
     * Model name to use (e.g., gpt-4)
     */
    private String model = "gpt-4";

    /**
     * Timeout in seconds for API calls
     */
    private int timeoutSeconds = 1800;

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
