package cc.lingnow.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
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

    public LlmClient(LlmProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(properties.getTimeoutSeconds(), TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    Request request = chain.request();
                    Response response = null;
                    IOException lastException = null;

                    for (int tryCount = 1; tryCount <= 3; tryCount++) {
                        try {
                            if (tryCount > 1) {
                                log.warn("Retrying LLM API call... Attempt {}/3", tryCount);
                                Thread.sleep(500 * tryCount); // Exponential backoff
                            }
                            response = chain.proceed(request);
                            if (response.isSuccessful()) {
                                return response;
                            }
                            // If not successful and we have retries left, close and continue
                            if (tryCount < 3) {
                                response.close();
                            }
                        } catch (IOException e) {
                            log.warn("LLM API attempt {} failed: {}", tryCount, e.getMessage());
                            lastException = e;
                            if (tryCount == 3) throw e;
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IOException("Retry interrupted", e);
                        }
                    }
                    return response;
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
        // Log masked API key for security
        String maskedKey = apiKey.length() > 8 ? apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4) : "****";
        log.debug("Using API Key: {}", maskedKey);

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No error body";
                log.error("LLM API call failed with code {}: {}", response.code(), errorBody);
                throw new IOException("Unexpected code " + response);
            }

            JsonNode responseJson = objectMapper.readTree(response.body().string());
            String result = responseJson.path("choices").get(0).path("message").path("content").asText();
            
            log.debug("LLM Response received (length: {})", result.length());
            return result;
        }
    }
}
