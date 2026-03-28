package cc.lingnow.llm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LLM Configuration Properties
 */
@Data
@Component
@ConfigurationProperties(prefix = "lingnow.llm")
public class LlmProperties {
    /**
     * API Key for metapi
     */
    private String apiKey;

    /**
     * Base URL for the OpenAI compatible API
     */
    private String baseUrl = "https://codex.metapi.cc/v1";

    /**
     * Model name to use (e.g., gpt-5.4)
     */
    private String model = "gpt-5.4";

    /**
     * Request timeout in seconds
     */
    private int timeoutSeconds = 1800;  // 30 minutes for complex tasks
}
