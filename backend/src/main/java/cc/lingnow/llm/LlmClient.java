package cc.lingnow.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * LLM Client for calling OpenAI-compatible APIs (metapi)
 */
@Slf4j
@Component
public class LlmClient {

    private final LlmProperties properties;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;
    private final int timeoutSeconds;

    public LlmClient(LlmProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.timeoutSeconds = Math.max(30, properties.getTimeoutSeconds());
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Math.min(timeoutSeconds, 60), TimeUnit.SECONDS)
                .writeTimeout(Math.min(timeoutSeconds, 60), TimeUnit.SECONDS)
                .callTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    Request request = chain.request();
                    Exception lastException = null;
                    String lastError = null;
                    for (int tryCount = 1; tryCount <= 5; tryCount++) {
                        try {
                            if (tryCount > 1) {
                                log.warn("Retrying LLM API call... Attempt {}/5", tryCount);
                                Thread.sleep(2000L * tryCount); // 递增避震
                            }
                            long startedAt = System.nanoTime();
                            Response response = chain.proceed(request);
                            long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
                            if (response.isSuccessful()) {
                                return response;
                            }
                            String errorBody = response.body() != null ? response.body().string() : "No error body";
                            lastError = "HTTP " + response.code() + " after " + durationMillis + "ms: " + errorBody;
                            log.warn("LLM API returned non-success (attempt {}/5): {}", tryCount, lastError);
                            response.close();
                        } catch (Exception e) {
                            lastException = e;
                            log.error("LLM attempt {} failed: {}", tryCount, e.getMessage());
                            if (e instanceof InterruptedException) {
                                Thread.currentThread().interrupt();
                                throw new IOException("Retry interrupted", e);
                            }
                        }
                    }
                    throw new IOException("LLM API failed after 5 attempts. Last error: "
                            + (lastError != null ? lastError : (lastException != null ? lastException.getMessage() : "Unknown")),
                            lastException);
                })
                .build();
    }

    /**
     * Chat completion call
     * 
     * @param systemPrompt System instructions
     * @param userPrompt User input
     * @return Raw text response from the model
     */
    public String chat(String systemPrompt, String userPrompt) throws IOException {
        String url = properties.getBaseUrl() + "/chat/completions";
        String apiKey = properties.getApiKey();

        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("METAPI_KEY environment variable is not set");
        }

        // Build Payload
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", properties.getModel());
        payload.put("stream", true);
        
        ArrayNode messages = payload.putArray("messages");
        
        ObjectNode systemMessage = messages.addObject();
        systemMessage.put("role", "system");
        systemMessage.put("content", systemPrompt);
        
        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        userMessage.put("content", userPrompt);

        RequestBody body = RequestBody.create(
                objectMapper.writeValueAsString(payload),
                MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + apiKey)
                .post(body)
                .build();

        log.info("Calling LLM [{}] at {}", properties.getModel(), url);
        log.debug("LLM effective timeout: {}s", timeoutSeconds);
        // Log masked API key for security
        String maskedKey = apiKey.length() > 8 ? apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4) : "****";
        log.debug("Using API Key: {}", maskedKey);

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No error body";
                log.error("LLM API call failed with code {}: {}", response.code(), errorBody);
                throw new IOException("Unexpected code " + response);
            }

            if (response.body() == null) {
                throw new IOException("LLM response body is empty");
            }

            String result = parseStreamingResponse(response.body());
            
            log.debug("LLM Response received (length: {})", result.length());
            return result;
        }
    }

    private String parseStreamingResponse(ResponseBody responseBody) throws IOException {
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(responseBody.byteStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith(":")) {
                    continue;
                }

                String data = line.startsWith("data:") ? line.substring(5).trim() : line.trim();
                if ("[DONE]".equals(data)) {
                    break;
                }

                JsonNode event = objectMapper.readTree(data);
                JsonNode choices = event.path("choices");
                if (!choices.isArray() || choices.isEmpty()) {
                    continue;
                }

                JsonNode choice = choices.get(0);
                String content = choice.path("delta").path("content").asText(null);
                if (content == null || content.isEmpty()) {
                    content = choice.path("message").path("content").asText(null);
                }
                if (content == null || content.isEmpty()) {
                    content = choice.path("text").asText(null);
                }
                if (content != null) {
                    result.append(content);
                }
            }
        }

        if (result.isEmpty()) {
            throw new IOException("LLM stream completed without content");
        }
        return result.toString();
    }
}
