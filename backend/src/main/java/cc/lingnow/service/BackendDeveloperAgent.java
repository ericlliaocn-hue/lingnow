package cc.lingnow.service;

import cc.lingnow.llm.LlmClient;
import cc.lingnow.model.ProjectManifest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Backend Developer Agent - Specialized in Spring Boot, REST APIs, and JPA.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackendDeveloperAgent {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public Map<String, String> develop(ProjectManifest manifest) {
        log.info("Backend Agent is coding for: {}", manifest.getUserIntent());
        
        try {
            boolean isMod = manifest.getGeneratedFiles() != null && manifest.getGeneratedFiles().keySet().stream().anyMatch(k -> k.endsWith(".java"));
            
            StringBuilder context = new StringBuilder();
            context.append("Features: ");
            if (manifest.getFeatures() != null) {
                manifest.getFeatures().forEach(f -> context.append(f.getName()).append(", "));
            }
            
            if (isMod) {
                context.append("\nEXISTING BACKEND CODE DETECTED:\n");
                manifest.getGeneratedFiles().forEach((path, content) -> {
                    if (path.endsWith(".java")) {
                        context.append("File: ").append(path).append("\n").append(content).append("\n");
                    }
                });
            }

            String systemPrompt = """
                You are a Lead Backend Engineer.
                
                RULES:
                1. Output ONLY pure JSON.
                2. If EXISTING CODE is provided, apply modifications precisely.
                3. JSON Schema: {
                    "files": {"path/to/File.java": "content"}, 
                    "apiSchema": "Full OpenAPI YAML/JSON string",
                    "databaseSchema": "SQL DDL string"
                }
                """;
            
            String userPrompt = isMod 
                ? "Apply the following backend iteration: " + manifest.getUserIntent()
                : "Generate Backend code and Schemas for: " + manifest.getUserIntent();
            
            String response = llmClient.chat(systemPrompt, userPrompt + (isMod ? "\nContext:\n" + context : ""));
            JsonNode root = objectMapper.readTree(cleanJsonResponse(response));

            Map<String, String> files = new HashMap<>();
            JsonNode filesNode = root.path("files");
            filesNode.fields().forEachRemaining(entry -> {
                files.put(entry.getKey(), entry.getValue().asText());
            });

            // Update schemas in manifest
            manifest.setApiSchema(root.path("apiSchema").asText());
            manifest.setDatabaseSchema(root.path("databaseSchema").asText());

            return files;

        } catch (Exception e) {
            log.error("Backend development failed", e);
            return new HashMap<>();
        }
    }

    private String cleanJsonResponse(String response) {
        if (response == null) return "{}";
        String cleaned = response.trim();
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf("\n");
            int lastBackticks = cleaned.lastIndexOf("```");
            if (firstNewline != -1 && lastBackticks > firstNewline) {
                cleaned = cleaned.substring(firstNewline, lastBackticks).trim();
            }
        }
        return cleaned;
    }
}
