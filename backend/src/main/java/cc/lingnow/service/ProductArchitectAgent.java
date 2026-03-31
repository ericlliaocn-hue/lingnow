package cc.lingnow.service;

import cc.lingnow.llm.LlmClient;
import cc.lingnow.model.ProjectManifest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
// import java.util.Map; (Deleted)

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
                        You are a World-Class Product Architect specialized in high-performance SaaS ecosystems (Vercel, Linear, Stripe).
                    
                        YOUR GOAL: Design a CURATED application architecture that prioritizes CORE BUSINESS VALUE over infrastructure noise.
                
                RULES:
                        1. CURATED NAVIGATION: Do NOT include 'Login', 'Signup', '404', or 'Loading' in the top-level 'mindMap' navigation. These are system infrastructure and should be handled implicitly. 
                        2. CORE PATHS: Focus the 'mindMap' on 3-5 high-value business modules (e.g., 'Health Dashboard', '3D Body Scan', 'Nutrition Insights').
                        3. DATA-DRIVEN: For every list or table, define 5+ high-fidelity metadata fields (e.g., 'Recovery Score', 'Muscle Engagement %%', 'Heart Rate Variability').
                        4. INDUSTRIAL LAYOUT: Plan for a multi-panel dashboard strategy (Main Content + Contextual Sidebar).
                    5. LANGUAGE: %s
                        6. OUTPUT: Pure JSON.
                    
                    JSON Schema: {
                            "overview": "string describing the curated user journey",
                                "mindMap": "string (A strictly formatted tree for MAIN SIDEBAR navigation only. MUST use \\n and exactly 2 spaces per indentation level. Max depth: 2.)",
                            "features": [{"name": "string", "description": "string", "priority": "HIGH|MEDIUM|LOW"}],
                                    "pages": [{"route": "string", "description": "string", "components": ["List 3+ high-density UI widgets here"]}],
                                "taskFlows": [
                                    {"id": "flow_1", "description": "Flow name (e.g. Booking a Table)", "steps": ["Page A -> Page B -> Action C -> Success Page"]}
                                ]
                        }
                """.formatted(langInstruction);
            
            String userPrompt = isMod 
                ? "Update the PRD based on this new requirement: " + manifest.getUserIntent()
                : "Create a PRD for: " + manifest.getUserIntent();
            
            String response = llmClient.chat(systemPrompt, userPrompt + (isMod ? "\nContext:\n" + context : ""));
            log.debug("Architect LLM raw response: {}", response);
            JsonNode root = objectMapper.readTree(cleanJsonResponse(response));

            // Set Mindmap
            manifest.setMindMap(root.path("mindMap").asText());

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

            // Parse and Save Task Flows to MetaData
            if (!root.path("taskFlows").isMissingNode()) {
                if (manifest.getMetaData() == null) manifest.setMetaData(new HashMap<>());
                manifest.getMetaData().put("taskFlows", root.path("taskFlows").toString());
                log.info("[Architect] Defined {} Task Flows for verification.", root.path("taskFlows").size());
            }
            
            log.info("Architect analysis complete. Total features: {}, pages: {}", 
                manifest.getFeatures() != null ? manifest.getFeatures().size() : 0, 
                manifest.getPages() != null ? manifest.getPages().size() : 0);

            // PHASE 1.2: Integrity Audit & Refinement (Self-Healing Plan)
            refineArchitecture(manifest, langInstruction);

        } catch (Exception e) {
            log.error("Architect analysis failed", e);
            throw new RuntimeException("Architect analysis failed: " + e.getMessage());
        }
    }

    private void refineArchitecture(ProjectManifest manifest, String langInstruction) {
        log.info("[Architect] Starting Integrity Audit (Self-Refinement)...");

        String auditPrompt = String.format("""
                You are a Product Quality Auditor.
                
                YOUR GOAL: Review the generated application architecture (PRD) and ensure 100%% logical completeness.
                
                SCRUTINY CHECKLIST:
                1. MISSION COVERAGE: Does the Mindmap actually solve the core problem for "%s"?
                2. BUSINESS LOOP: Are there obvious missing nodes (e.g., if it's a rental app, is there a way to 'Confirm Booking')?
                3. NAVIGATION FIDELITY: Ensure no system infrastructure nodes (Login, Error) are in the Mindmap.
                4. DATA CONSISTENCY: Ensure every page has 5+ specific data fields that match its business purpose.
                
                CURRENT PRD:
                Mindmap: %s
                Pages: %s
                
                OUTPUT: If perfect, respond with "STABLE". If not, respond with the UPDATED JSON only (same schema).
                5. LANGUAGE: %s
                """, manifest.getUserIntent(), manifest.getMindMap(), manifest.getPages().toString(), langInstruction);

        try {
            String response = llmClient.chat(auditPrompt, "Audit and Refine requested.");
            if (response.contains("STABLE")) {
                log.info("[Architect] Audit passed: PRD is mission-stable.");
                return;
            }

            // Apply refined architecture
            log.info("[Architect] Audit found gaps. Applying Genetic Patch to PRD...");
            JsonNode root = objectMapper.readTree(cleanJsonResponse(response));

            if (!root.path("mindMap").isMissingNode()) {
                manifest.setMindMap(root.path("mindMap").asText());
            }
            // Update pages if refined
            if (!root.path("pages").isMissingNode()) {
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
            }
            log.info("[Architect] PRD Refined successfully.");

        } catch (Exception e) {
            log.warn("[Architect] Refinement pass skipped or failed: {}", e.getMessage());
        }
    }

    private String cleanJsonResponse(String response) {
        if (response == null) return "{}";
        String cleaned = response.trim();
        int firstBrace = cleaned.indexOf("{");
        int lastBrace = cleaned.lastIndexOf("}");
        if (firstBrace != -1 && lastBrace != -1 && lastBrace >= firstBrace) {
            return cleaned.substring(firstBrace, lastBrace + 1);
        }
        return "{}";
    }
}
