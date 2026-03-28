package cc.lingnow.service;

import cc.lingnow.llm.LlmClient;
import cc.lingnow.model.ProjectManifest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Product Architect Agent - Responsible for analyzing user requirements
 * and creating a structured PRD (Manifest).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductArchitectAgent {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public void analyze(ProjectManifest manifest) {
        log.info("Architect is analyzing requirement: {} (Mod: {})", manifest.getUserIntent(), manifest.getFeatures() != null && !manifest.getFeatures().isEmpty());
        
        try {
            boolean isMod = manifest.getFeatures() != null && !manifest.getFeatures().isEmpty();
            
            StringBuilder context = new StringBuilder();
            if (isMod) {
                context.append("EXISTING PRD DETECTED:\n");
                StringBuilder planSummary = new StringBuilder();
                if (manifest.getFeatures() != null) {
                    manifest.getFeatures().forEach(f -> planSummary.append("- ").append(f.getName()).append(": ").append(f.getDescription()).append("\n"));
                }
                if (manifest.getPages() != null) {
                    manifest.getPages().forEach(p -> planSummary.append("- Page: ").append(p.getRoute()).append(" (").append(p.getDescription()).append(")\n"));
                }
                context.append(planSummary);
            }

            // Language awareness
            String lang = manifest.getMetaData() != null ? manifest.getMetaData().getOrDefault("lang", "EN") : "EN";
            String langInstruction = "ZH".equalsIgnoreCase(lang) 
                ? "CRITICAL: All content (features names, descriptions, page descriptions) MUST BE IN CHINESE."
                : "All content MUST BE IN ENGLISH.";

            String systemPrompt = """
                    You are a senior Product Architect specialized in high-density technical ecosystems (like linux.do, Juejin, CSDN).
                    
                    YOUR GOAL: Design a COMPLETE application architecture that prioritizes CONTENT and FUNCTIONAL FLOW.
                
                RULES:
                    1. DEDUCTIVE PLANNING: Automatically include all infrastructure (Login, Signup, User Profile, Settings, 404) for any community/SaaS project.
                    2. CONTENT DENSITY: For every feature/page that involves listing (e.g., Article Feed), you MUST define rich metadata fields (e.g., Author Avatar/Bio, View Count, Like Count, Relative Time, Categories, Reading Time).
                    3. USER JOURNEY: Clearly specify the state-to-state navigation in the descriptions.
                    4. INDUSTRIAL STANDARDS: Benchmark against Vercel and Linear for layout strategy.
                    5. LANGUAGE: %s
                    6. OUTPUT: Pure JSON only.
                    
                    JSON Schema: {
                        "overview": "string describing the full journey",
                            "mindMap": "string (Detailed Mermaid mindmap covering all functional nodes and navigation paths)",
                            "mockData": "string (A JSON-formatted string containing realistic sample data for articles, users, etc.)",
                            "features": [{"name": "string", "description": "string", "priority": "HIGH|MEDIUM|LOW"}],
                            "pages": [{"route": "string", "description": "string", "components": ["string"]}]
                        }
                """.formatted(langInstruction);
            
            String userPrompt = isMod 
                ? "Update the PRD based on this new requirement: " + manifest.getUserIntent()
                : "Create a PRD for: " + manifest.getUserIntent();
            
            String response = llmClient.chat(systemPrompt, userPrompt + (isMod ? "\nContext:\n" + context : ""));
            log.debug("Architect LLM raw response: {}", response);
            JsonNode root = objectMapper.readTree(cleanJsonResponse(response));

            // Set Mindmap and MockData
            manifest.setMindMap(root.path("mindMap").asText());
            manifest.setMockData(root.path("mockData").asText());

            // Parse features
            List<ProjectManifest.Feature> features = new ArrayList<>();
            root.path("features").forEach(f -> {
                features.add(ProjectManifest.Feature.builder()
                        .name(f.path("name").asText())
                        .description(f.path("description").asText())
                        .priority(ProjectManifest.Feature.Priority.valueOf(f.path("priority").asText("MEDIUM").toUpperCase()))
                        .build());
            });
            manifest.setFeatures(features);

            // Parse pages
            List<ProjectManifest.PageSpec> pages = new ArrayList<>();
            root.path("pages").forEach(p -> {
                List<String> components = new ArrayList<>();
                p.path("components").forEach(c -> components.add(c.asText()));
                
                pages.add(ProjectManifest.PageSpec.builder()
                        .route(p.path("route").asText())
                        .description(p.path("description").asText())
                        .components(components)
                        .build());
            });
            manifest.setPages(pages);
            
            log.info("Architect analysis complete. Total features: {}, pages: {}", 
                manifest.getFeatures() != null ? manifest.getFeatures().size() : 0, 
                manifest.getPages() != null ? manifest.getPages().size() : 0);

        } catch (Exception e) {
            log.error("Architect analysis failed", e);
            throw new RuntimeException("Architect analysis failed: " + e.getMessage());
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
