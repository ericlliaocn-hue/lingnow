package cc.lingnow.service;

import cc.lingnow.llm.LlmClient;
import cc.lingnow.model.ProjectManifest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Deployment Agent - Responsible for creating Docker, Docker-Compose, and README.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeploymentAgent {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public void generate(ProjectManifest manifest) {
        log.info("Deployment Agent is generating configs for: {}", manifest.getUserIntent());
        
        try {
            StringBuilder context = new StringBuilder();
            context.append("Features: ");
            manifest.getFeatures().forEach(f -> context.append(f.getName()).append(", "));
            
            String systemPrompt = """
                You are a DevOps Engineer. Generate deployment configurations for a full-stack Spring Boot + Vue 3 application.
                
                RULES:
                1. Output ONLY pure JSON.
                2. Generate Dockerfile (multi-stage), docker-compose.yml, and a professional README.md.
                3. JSON Schema: {"dockerfile": "string", "dockerCompose": "string", "readme": "string"}
                """;
            
            String userPrompt = "Create deployment artifacts for project: " + manifest.getUserIntent() + "\nContext: " + context.toString();
            
            String response = llmClient.chat(systemPrompt, userPrompt);
            JsonNode root = objectMapper.readTree(cleanJsonResponse(response));

            StringBuilder sb = new StringBuilder();
            sb.append("### Dockerfile\n```dockerfile\n").append(root.path("dockerfile").asText()).append("\n```\n\n");
            sb.append("### Docker Compose\n```yaml\n").append(root.path("dockerCompose").asText()).append("\n```\n\n");
            sb.append("### README.md\n").append(root.path("readme").asText());

            manifest.setDeploymentConfig(sb.toString());
            log.info("Deployment artifacts generated successfully.");

        } catch (Exception e) {
            log.error("Deployment phase failed", e);
            manifest.setDeploymentConfig("### Deployment failed\n" + e.getMessage());
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
