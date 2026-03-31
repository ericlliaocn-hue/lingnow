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

    private String loadHandbook() {
        try {
            return java.nio.file.Files.readString(java.nio.file.Paths.get("/Users/eric/workspace/lingnow/.agents/skills/PRODUCT_MANAGER_HANDBOOK.md"));
        } catch (Exception e) {
            log.warn("[Architect] Handbook not found, falling back to basic PM logic.");
            return "";
        }
    }

    public void analyze(ProjectManifest manifest) {
        log.info("Architect is analyzing requirement: {} (Mod: {})", manifest.getUserIntent(), manifest.getFeatures() != null && !manifest.getFeatures().isEmpty());
        
        try {
            boolean isMod = manifest.getFeatures() != null && !manifest.getFeatures().isEmpty();
            String handbook = loadHandbook();
            
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
                    ? "CRITICAL: All content MUST BE IN CHINESE."
                : "All content MUST BE IN ENGLISH.";

            String uxStrategyContext = manifest.getUxStrategy() != null ?
                    "UX STRATEGY (Intelligence Agent):\n" + manifest.getUxStrategy().toString() :
                    "Standard professional standards.";

            String systemPrompt = String.format("""
                    %s
                    
                    YOUR GOAL: Synthesize a COMPLETE Application Ecosystem with process-aware navigation.
                    
                    TASKS:
                    1. Define semantic navRole (PRIMARY, UTILITY, OVERLAY, PERSONAL).
                    2. Enforce the PROCESS GUARANTEE.
                    3. Ensure high DATA DENSITY (8+ fields).
                    
                    LANGUAGE: %s
                    STRATEGY CONTEXT: %s
                    
                    JSON Schema: {
                        "archetype": "string",
                        "overview": "string",
                        "mindMap": "string (PRIMARY nodes only)",
                        "features": [{"name": "string", "description": "string", "priority": "HIGH|MEDIUM|LOW"}],
                        "pages": [{"route": "string", "description": "string", "navType": "NAV_ANCHOR|CONTEXT_WIDGET|LEAF_DETAIL", "navRole": "PRIMARY|UTILITY|OVERLAY|PERSONAL", "components": ["List of component names"]}],
                        "taskFlows": [{"id": "flow_x", "description": "Loop description", "steps": ["Entry -> Action -> Success"]}]
                    }
                    """, handbook, langInstruction, uxStrategyContext);
            
            String userPrompt = isMod
                    ? "Update the PRD based on: " + manifest.getUserIntent()
                : "Create a PRD for: " + manifest.getUserIntent();
            
            String response = llmClient.chat(systemPrompt, userPrompt + (isMod ? "\nContext:\n" + context : ""));
            log.debug("Architect LLM raw response: {}", response);
            JsonNode root = objectMapper.readTree(cleanJsonResponse(response));

            // Set Archetype and Mindmap
            manifest.setArchetype(root.path("archetype").asText("DASHBOARD"));
            manifest.setOverview(root.path("overview").asText("Application Overview"));
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
                        .navType(p.path("navType").asText("NAV_ANCHOR"))
                        .navRole(p.path("navRole").asText("PRIMARY"))
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
                You are a Senior Product Quality Auditor.
                
                YOUR GOAL: Review the PRD for mission-critical 'Implicit Essentials' and 'Semantic Integrity'.
                
                SCRUTINY CHECKLIST:
                1. IMPLICIT ESSENTIALS: If the industry is social/content, does it have a 'Reply/Comment' flow and 'Post' action? REJECT if it's just a 'Reader' with no UGC.
                2. NAVIGATION SEMANTICS: Are Detail pages correctly labeled as LEAF_DETAIL (not in Mindmap)? 
                3. ECOSYSTEM CONNECTIVITY: Is there a way to get from a detail page back to the discovery feed? 
                4. DATA FIDELITY: Ensure 5+ specific data fields per component (e.g. thumbUrl, authorBadge, readTime).
                
                CURRENT PRD:
                Mindmap: %s
                Pages: %s
                
                OUTPUT: If perfect, respond with "STABLE". If not, respond with the UPDATED JSON only (same schema as Architect).
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
