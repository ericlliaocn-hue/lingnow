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
 * Frontend Developer Agent - Specialized in Vue 3, Tailwind CSS, and Lucide icons.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FrontendDeveloperAgent {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public Map<String, String> develop(ProjectManifest manifest) {
        log.info("Frontend Agent is coding for: {} (Mod: {})", manifest.getUserIntent(), manifest.getGeneratedFiles() != null && !manifest.getGeneratedFiles().isEmpty());
        
        try {
            boolean isMod = manifest.getGeneratedFiles() != null && !manifest.getGeneratedFiles().isEmpty();
            
            StringBuilder context = new StringBuilder();
            context.append("Features: ");
            if (manifest.getFeatures() != null) {
                manifest.getFeatures().forEach(f -> context.append(f.getName()).append(", "));
            }
            
            if (isMod) {
                context.append("\nEXISTING CODE DETECTED. Perform an incremental update.\n");
                manifest.getGeneratedFiles().forEach((path, content) -> {
                    if (path.endsWith(".vue")) {
                        context.append("Current ").append(path).append(":\n").append(content).append("\n");
                    }
                });
            }

            String systemPrompt = """
                You are a Senior Frontend Engineer.
                
                RULES:
                1. Output ONLY pure JSON.
                2. JSON Schema: {"fileName": "App.vue", "code": "string"}
                3. If EXISTING CODE is provided, apply the requested changes precisely. Do not remove existing features unless asked.
                """;
            
            String userPrompt = isMod 
                ? "Apply the following modification to the existing code: " + manifest.getUserIntent()
                : "Generate Frontend code for: " + manifest.getUserIntent();
            
            String response = llmClient.chat(systemPrompt, userPrompt + "\nContext:\n" + context.toString());
            JsonNode root = objectMapper.readTree(cleanJsonResponse(response));

            Map<String, String> files = new HashMap<>();
            String fileName = root.path("fileName").asText("App.vue");
            String code = root.path("code").asText();
            
            files.put("/" + fileName, code);
            files.put("/main.js", "import { createApp } from 'vue'; import App from './App.vue'; import './style.css'; createApp(App).mount('#app');");
            files.put("/style.css", "@tailwind base; @tailwind components; @tailwind utilities;");

            return files;

        } catch (Exception e) {
            log.error("Frontend development failed", e);
            throw new RuntimeException("Frontend development failed: " + e.getMessage());
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
