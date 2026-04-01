package cc.lingnow.service;

import cc.lingnow.llm.LlmClient;
import cc.lingnow.model.ProjectManifest;
import cc.lingnow.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;


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
                    
                    GOAL: Synthesize Application Ecosystem with process-aware navigation.
                    
                    CONSTRAINTS:
                    - Define navRole (PRIMARY, UTILITY, OVERLAY, PERSONAL).
                    - High DATA DENSITY (5+ components per surface).
                    - features: 6-8 max, pages: 10 max, primary pages: 5 max.
                    
                    LANGUAGE: %s
                    STRATEGY: %s
                    
                    STRICT JSON SCHEMA: {
                        "archetype": "string",
                        "overview": "string",
                        "mindMap": "string",
                        "features": [{"name": "string", "description": "string", "priority": "HIGH|MEDIUM|LOW"}],
                        "pages": [{"route": "string", "description": "string", "navType": "NAV_ANCHOR|CONTEXT_WIDGET|LEAF_DETAIL", "navRole": "PRIMARY|UTILITY|OVERLAY|PERSONAL", "components": ["List of names"]}],
                        "taskFlows": [{"id": "id", "description": "desc", "steps": ["Entry -> Action -> Success"]}]
                    }
                    """, handbook, langInstruction, uxStrategyContext);
            
            String userPrompt = isMod
                    ? "Update the PRD based on: " + manifest.getUserIntent()
                : "Create a PRD for: " + manifest.getUserIntent();
            
            String response = llmClient.chat(systemPrompt, userPrompt + (isMod ? "\nContext:\n" + context : ""));
            log.debug("Architect LLM raw response: {}", response);
            JsonNode root = objectMapper.readTree(cleanJsonResponse(response));

            applyArchitecture(manifest, root);
            compressArchitecture(manifest);
            
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

        DeterministicAudit audit = runDeterministicAudit(manifest);
        if (audit.stable()) {
            log.info("[Architect] Structural audit passed: PRD is mission-stable.");
            return;
        }

        applyDeterministicPatches(manifest, audit.gaps());

        // Final structural check after patching
        DeterministicAudit patchedAudit = runDeterministicAudit(manifest);
        if (patchedAudit.stable()) {
            log.info("[Architect] PRD patched and stabilized without LLM refinement.");
            return;
        }

        log.info("[Architect] Gaps remain after deterministic patching: {}. Triggering LLM refinement.", patchedAudit.gaps());

        String auditPrompt = String.format("""
                You are a Senior Product Quality Auditor.
                        
                        YOUR GOAL: Review the PRD only for the unresolved gaps listed below and return a corrected JSON if needed.
                        
                        UNRESOLVED GAPS:
                        %s
                        
                        CURRENT PRD SUMMARY:
                User Intent: %s
                Mindmap: %s
                        Pages Summary: %s
                        Task Flows Summary: %s
                        
                        OUTPUT:
                        - If already acceptable: respond with "STABLE"
                        - Otherwise: respond with UPDATED JSON only (same schema as Architect)
                        LANGUAGE: %s
                        """,
                String.join("\n", audit.gaps()),
                manifest.getUserIntent(),
                manifest.getMindMap(),
                summarizePages(manifest),
                summarizeTaskFlows(manifest),
                langInstruction);

        try {
            String response = llmClient.chat(auditPrompt, "Audit and Refine requested.");
            if (response.contains("STABLE")) {
                log.info("[Architect] Audit passed: PRD is mission-stable.");
                return;
            }

            // Apply refined architecture
            log.info("[Architect] Audit found gaps. Applying Genetic Patch to PRD...");
            JsonNode root = objectMapper.readTree(cleanJsonResponse(response));

            applyArchitecture(manifest, root);
            compressArchitecture(manifest);
            log.info("[Architect] PRD Refined successfully.");

        } catch (Exception e) {
            log.warn("[Architect] Refinement pass skipped or failed: {}", e.getMessage());
        }
    }

    private DeterministicAudit runDeterministicAudit(ProjectManifest manifest) {
        List<String> gaps = new ArrayList<>();
        String source = ((manifest.getUserIntent() == null ? "" : manifest.getUserIntent()) + " "
                + (manifest.getArchetype() == null ? "" : manifest.getArchetype()) + " "
                + (manifest.getOverview() == null ? "" : manifest.getOverview())).toLowerCase(Locale.ROOT);
        boolean contentOrSocial = containsAny(source, "community", "content", "social", "feed", "小红书", "社区", "内容", "发现", "分享", "种草");

        boolean hasOverlay = manifest.getPages() != null && manifest.getPages().stream()
                .anyMatch(page -> "OVERLAY".equalsIgnoreCase(page.getNavRole()));
        if (!hasOverlay) {
            gaps.add("Missing detail overlay page.");
        }

        boolean hasPublishAction = manifest.getPages() != null && manifest.getPages().stream()
                .anyMatch(page -> "UTILITY".equalsIgnoreCase(page.getNavRole())
                        && containsAny(page.getRoute(), "publish", "upload", "post", "发布"));
        if (contentOrSocial && !hasPublishAction) {
            gaps.add("Content/social product is missing a utility publish action.");
        }

        boolean hasCommentFlow = manifest.getTaskFlows() != null && manifest.getTaskFlows().stream()
                .anyMatch(flow -> containsAny(flow.getDescription(), "comment", "reply", "评论", "回复")
                        || (flow.getSteps() != null && flow.getSteps().stream().anyMatch(step -> containsAny(step, "comment", "reply", "评论", "回复"))));
        if (contentOrSocial && !hasCommentFlow) {
            gaps.add("Content/social product is missing a comment or reply loop.");
        }

        boolean hasReturnToFeed = manifest.getTaskFlows() != null && manifest.getTaskFlows().stream()
                .anyMatch(flow -> flow.getSteps() != null && flow.getSteps().stream().anyMatch(step -> containsAny(step, "返回", "back", "return", "继续浏览", "continue browsing", "推荐流", "discovery feed")));
        if (!hasReturnToFeed) {
            gaps.add("Task flows do not clearly describe return-to-feed continuity from detail views.");
        }

        boolean dataDenseEnough = manifest.getPages() != null && manifest.getPages().stream()
                .allMatch(page -> page.getComponents() != null && page.getComponents().size() >= 5);
        if (!dataDenseEnough) {
            gaps.add("One or more pages do not expose enough component/data hints for downstream data generation.");
        }

        return new DeterministicAudit(gaps.isEmpty(), gaps);
    }

    private void applyDeterministicPatches(ProjectManifest manifest, List<String> gaps) {
        if (gaps == null || gaps.isEmpty()) {
            return;
        }
        if (manifest.getPages() == null) {
            manifest.setPages(new ArrayList<>());
        }
        if (manifest.getTaskFlows() == null) {
            manifest.setTaskFlows(new ArrayList<>());
        }

        if (gaps.contains("Missing detail overlay page.")) {
            manifest.getPages().add(ProjectManifest.PageSpec.builder()
                    .route("/detail")
                    .description("Universal detail overlay for immersive viewing and interaction continuity.")
                    .navType("LEAF_DETAIL")
                    .navRole("OVERLAY")
                    .components(List.of("Hero Media", "Content Body", "Interaction Bar", "Comments", "Related Items"))
                    .build());
        }

        if (gaps.contains("Content/social product is missing a utility publish action.")) {
            manifest.getPages().add(ProjectManifest.PageSpec.builder()
                    .route("/publish")
                    .description("Utility publishing entry for creating posts or articles without occupying primary navigation.")
                    .navType("CONTEXT_WIDGET")
                    .navRole("UTILITY")
                    .components(List.of("Title Input", "Content Editor", "Media Upload", "Tag Picker", "Publish CTA"))
                    .build());
        }

        if (gaps.contains("Content/social product is missing a comment or reply loop.")) {
            manifest.getTaskFlows().add(ProjectManifest.TaskFlow.builder()
                    .id("flow_comment_reply")
                    .description("Users can enter a detail view, comment, reply, and see the thread update immediately.")
                    .steps(List.of(
                            "ENTRY: Open the detail view from a discovery or content card",
                            "ACTION: Add a comment or reply to an existing thread",
                            "FEEDBACK: Comment thread updates instantly and interaction counts increase"
                    ))
                    .build());
        }

        if (gaps.contains("Task flows do not clearly describe return-to-feed continuity from detail views.")) {
            manifest.getTaskFlows().add(ProjectManifest.TaskFlow.builder()
                    .id("flow_return_to_feed")
                    .description("Users return from detail view back into the discovery/feed context without losing browsing continuity.")
                    .steps(List.of(
                            "ENTRY: Open a detail view from the main discovery feed",
                            "ACTION: Close the detail overlay or navigate back",
                            "FEEDBACK: The user returns to the original feed context and can continue browsing"
                    ))
                    .build());
        }
    }

    private String summarizePages(ProjectManifest manifest) {
        if (manifest.getPages() == null || manifest.getPages().isEmpty()) {
            return "No pages";
        }
        return manifest.getPages().stream()
                .map(page -> page.getRoute() + " [" + page.getNavRole() + "/" + page.getNavType() + "] components="
                        + (page.getComponents() == null ? 0 : page.getComponents().size()))
                .reduce((a, b) -> a + "\n- " + b)
                .map(summary -> "- " + summary)
                .orElse("No pages");
    }

    private String summarizeTaskFlows(ProjectManifest manifest) {
        if (manifest.getTaskFlows() == null || manifest.getTaskFlows().isEmpty()) {
            return "No task flows";
        }
        return manifest.getTaskFlows().stream()
                .map(flow -> flow.getId() + ": " + flow.getDescription())
                .reduce((a, b) -> a + "\n- " + b)
                .map(summary -> "- " + summary)
                .orElse("No task flows");
    }

    private boolean containsAny(String source, String... tokens) {
        if (source == null) {
            return false;
        }
        String normalized = source.toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (normalized.contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private List<ProjectManifest.Feature> parseFeatures(JsonNode featureNodes) {
        List<ProjectManifest.Feature> features = new ArrayList<>();
        featureNodes.forEach(featureNode -> {
            features.add(ProjectManifest.Feature.builder()
                    .name(featureNode.path("name").asText())
                    .description(featureNode.path("description").asText())
                    .priority(ProjectManifest.Feature.Priority.valueOf(featureNode.path("priority").asText("MEDIUM").toUpperCase()))
                    .build());
        });
        return features.stream().limit(8).collect(Collectors.toCollection(ArrayList::new));
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

    private void applyArchitecture(ProjectManifest manifest, JsonNode root) {
        if (!root.path("archetype").isMissingNode()) {
            manifest.setArchetype(root.path("archetype").asText("DASHBOARD"));
        }
        if (!root.path("overview").isMissingNode()) {
            manifest.setOverview(root.path("overview").asText("Application Overview"));
        }
        if (!root.path("mindMap").isMissingNode()) {
            manifest.setMindMap(root.path("mindMap").asText());
        }
        if (!root.path("features").isMissingNode()) {
            manifest.setFeatures(parseFeatures(root.path("features")));
        }
        if (!root.path("pages").isMissingNode()) {
            manifest.setPages(parsePages(root.path("pages")));
        }
        if (!root.path("taskFlows").isMissingNode()) {
            List<ProjectManifest.TaskFlow> taskFlows = parseTaskFlows(root.path("taskFlows"));
            manifest.setTaskFlows(taskFlows);
            if (manifest.getMetaData() == null) {
                manifest.setMetaData(new HashMap<>());
            }
            manifest.getMetaData().put("taskFlows", JsonUtils.toJson(taskFlows));
            log.info("[Architect] Defined {} Task Flows for verification.", taskFlows.size());
        }
    }

    private List<ProjectManifest.PageSpec> parsePages(JsonNode pageNodes) {
        List<ProjectManifest.PageSpec> pages = new ArrayList<>();
        pageNodes.forEach(pageNode -> {
            List<String> components = new ArrayList<>();
            pageNode.path("components").forEach(componentNode -> {
                if (components.size() < 6) {
                    components.add(componentNode.asText());
                }
            });

            pages.add(ProjectManifest.PageSpec.builder()
                    .route(pageNode.path("route").asText())
                    .description(pageNode.path("description").asText())
                    .navType(pageNode.path("navType").asText("NAV_ANCHOR"))
                    .navRole(pageNode.path("navRole").asText("PRIMARY"))
                    .components(components)
                    .build());
        });
        return pages;
    }

    private List<ProjectManifest.TaskFlow> parseTaskFlows(JsonNode taskFlowNodes) {
        List<ProjectManifest.TaskFlow> taskFlows = new ArrayList<>();
        taskFlowNodes.forEach(taskFlowNode -> {
            List<String> steps = new ArrayList<>();
            taskFlowNode.path("steps").forEach(stepNode -> steps.add(stepNode.asText()));
            taskFlows.add(ProjectManifest.TaskFlow.builder()
                    .id(taskFlowNode.path("id").asText())
                    .description(taskFlowNode.path("description").asText())
                    .steps(steps)
                    .build());
        });
        return taskFlows.stream().limit(5).collect(Collectors.toCollection(ArrayList::new));
    }

    private void compressArchitecture(ProjectManifest manifest) {
        if (manifest.getFeatures() != null) {
            manifest.setFeatures(manifest.getFeatures().stream().limit(8).collect(Collectors.toCollection(ArrayList::new)));
        }

        if (manifest.getPages() != null && !manifest.getPages().isEmpty()) {
            List<ProjectManifest.PageSpec> primary = manifest.getPages().stream()
                    .filter(page -> "PRIMARY".equalsIgnoreCase(page.getNavRole()))
                    .limit(5)
                    .collect(Collectors.toCollection(ArrayList::new));
            List<ProjectManifest.PageSpec> utility = manifest.getPages().stream()
                    .filter(page -> "UTILITY".equalsIgnoreCase(page.getNavRole()))
                    .limit(2)
                    .collect(Collectors.toCollection(ArrayList::new));
            List<ProjectManifest.PageSpec> overlay = manifest.getPages().stream()
                    .filter(page -> "OVERLAY".equalsIgnoreCase(page.getNavRole()))
                    .limit(2)
                    .collect(Collectors.toCollection(ArrayList::new));
            List<ProjectManifest.PageSpec> personal = manifest.getPages().stream()
                    .filter(page -> "PERSONAL".equalsIgnoreCase(page.getNavRole()))
                    .limit(1)
                    .collect(Collectors.toCollection(ArrayList::new));

            List<ProjectManifest.PageSpec> compacted = new ArrayList<>();
            compacted.addAll(primary);
            compacted.addAll(utility);
            compacted.addAll(overlay);
            compacted.addAll(personal);
            manifest.setPages(compacted.stream()
                    .map(page -> ProjectManifest.PageSpec.builder()
                            .route(page.getRoute())
                            .description(page.getDescription())
                            .navType(page.getNavType())
                            .navRole(page.getNavRole())
                            .components(page.getComponents() == null ? List.of() : page.getComponents().stream().distinct().limit(6).toList())
                            .build())
                    .collect(Collectors.toCollection(ArrayList::new)));
            manifest.setMindMap(buildCompactMindMap(manifest.getPages()));
        }

        if (manifest.getTaskFlows() != null) {
            manifest.setTaskFlows(manifest.getTaskFlows().stream().limit(5).collect(Collectors.toCollection(ArrayList::new)));
        }
    }

    private String buildCompactMindMap(List<ProjectManifest.PageSpec> pages) {
        if (pages == null || pages.isEmpty()) {
            return "";
        }
        return pages.stream()
                .filter(page -> "PRIMARY".equalsIgnoreCase(page.getNavRole()))
                .map(page -> presentableRoute(page.getRoute(), page.getDescription()))
                .distinct()
                .limit(5)
                .collect(Collectors.joining("\n"));
    }

    private String presentableRoute(String route, String description) {
        if (route != null && !route.isBlank()) {
            String cleaned = route.replace("/", "").replace(":", "").trim();
            if (!cleaned.isBlank()) {
                return cleaned;
            }
        }
        return description == null ? "overview" : description;
    }

    private record DeterministicAudit(boolean stable, List<String> gaps) {
    }
}
