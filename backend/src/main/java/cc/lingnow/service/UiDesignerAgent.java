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
 * UI Designer Agent - Responsible for creating and refining high-fidelity prototypes (HTML/Tailwind).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UiDesignerAgent {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    private String getDynamicDNA(ProjectManifest manifest) {
        var meta = manifest.getMetaData();
        if (meta == null) return "";
        
        return String.format("""
                        - Background: %s
                        - Cards: %s
                        - Primary: %s
                        - Accent: %s
                        - Shadow Style: %s
                        - Border Style: %s
                        - Glass Effect: %s
                        - Font: %s
                        - Line Height: %s
                        - Letter Spacing: %s
                        - Serif Bias (High Fidelity): %s
                        - Reasoning: %s
                        """,
                meta.getOrDefault("visual_bgClass", "bg-slate-50"),
                meta.getOrDefault("visual_cardClass", "bg-white shadow-sm rounded-2xl p-6"),
                meta.getOrDefault("visual_primaryColor", "indigo-600"),
                meta.getOrDefault("visual_accentColor", "pink-500"),
                meta.getOrDefault("visual_shadowStrategy", "shadow-sm"),
                meta.getOrDefault("visual_borderAccent", "border-slate-200"),
                meta.getOrDefault("visual_glassIntensity", "backdrop-blur-none"),
                meta.getOrDefault("visual_fontFamily", "font-sans"),
                meta.getOrDefault("visual_lineHeight", "leading-normal"),
                meta.getOrDefault("visual_letterSpacing", "tracking-normal"),
                meta.getOrDefault("visual_serifBias", "false"),
                meta.getOrDefault("visual_reasoning", "Standard professional style."));
    }

    /**
     * Generate a high-fidelity HTML prototype based on the manifest using a Multi-Step Pipeline.
     */
    public void design(ProjectManifest manifest) {
        log.info("Designer is starting multi-step pipeline for: {}", manifest.getUserIntent());
        String lang = manifest.getMetaData() != null ? manifest.getMetaData().getOrDefault("lang", "EN") : "EN";

        try {
            // STEP 0: Pre-calculate Routes from Mindmap
            List<Route> routes = extractRoutes(manifest);
            if (routes.isEmpty()) {
                throw new RuntimeException("No valid feature nodes found in mindmap. Cannot design UI.");
            }

            // STEP 1: Generate App Shell
            log.info("Step 1: Generating Application Layout Shell with {} routes...", routes.size());
            String shellHtml = generateShell(manifest, routes, lang);

            // STEP 2: Generate Page Components with Deep Context
            log.info("Step 2: Generating Feature Components (Context Bridge Active)...");
            StringBuilder contentSlots = new StringBuilder();

            int count = 0;
            for (Route route : routes) {
                if (count >= 6) { // Increased to 6 to cover more ground
                    log.info("Reached maximum of 6 pages for initial generation. Skipping the rest.");
                    break;
                }

                // Context Bridge: Match Route to PageSpec (Architect's Intent)
                ProjectManifest.PageSpec pageSpec = findPageSpec(manifest, route);

                log.info("Generating component for: {} (#{}) using Context: {}", route.name, route.id, (pageSpec != null));
                String componentHtml = generateComponent(manifest, route, pageSpec, lang);
                contentSlots.append(componentHtml).append("\n");
                count++;
            }

            // STEP 3: Assembly
            log.info("Step 3: Assembling prototype...");
            String finalHtml;

            // Inject Mock Data into Shell
            String processedShell = shellHtml;
            String mockJson = manifest.getMockData() != null ? manifest.getMockData() : "[]";

            // Ensure JSON is safe for HTML attribute insertion
            String safeMockJson = mockJson.replace("\"", "&quot;");
            processedShell = processedShell.replace("{{MOCK_DATA}}", safeMockJson);

            if (processedShell.contains("{{CONTENT_SLOTS}}")) {
                finalHtml = processedShell.replace("{{CONTENT_SLOTS}}", contentSlots.toString());
            } else if (processedShell.contains("{{CONTENT_SLOT}}")) {
                finalHtml = processedShell.replace("{{CONTENT_SLOT}}", contentSlots.toString());
            } else {
                log.warn("Shell missing {{CONTENT_SLOTS}} tag. Appending blocks manually.");
                finalHtml = processedShell + "\n<!-- ASSEMBLED COMPONENTS -->\n" + contentSlots.toString();
            }

            manifest.setPrototypeHtml(finalHtml);
            log.info("Multi-step prototype created successfully ({} chars).", finalHtml.length());

        } catch (Exception e) {
            log.error("Prototype multi-step design failed", e);
            throw new RuntimeException("UI Design pipeline failed: " + e.getMessage());
        }
    }

    private List<Route> extractRoutes(ProjectManifest manifest) {
        String mindMap = manifest.getMindMap();
        if (mindMap == null || mindMap.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String[] lines = mindMap.split("\\n");
        List<Route> routes = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String current = lines[i];
            if (current.trim().isEmpty() || current.trim().startsWith("```") || current.trim().equalsIgnoreCase("mindmap"))
                continue;

            String name = current.replace("- ", "").trim();
            if (name.isEmpty()) continue;

            String id = "pg" + (i + 1);

            // Match with PageSpec to get navType
            ProjectManifest.PageSpec spec = findPageSpec(manifest, new Route(id, name, "NAV_ANCHOR"));
            String navType = spec != null ? spec.getNavType() : "NAV_ANCHOR";

            routes.add(new Route(id, name, navType));
        }
        return routes;
    }

    private ProjectManifest.PageSpec findPageSpec(ProjectManifest manifest, Route route) {
        if (manifest.getPages() == null) return null;
        return manifest.getPages().stream()
                .filter(p -> {
                    String specRoute = p.getRoute().toLowerCase().replace("/", "");
                    String description = p.getDescription() != null ? p.getDescription().toLowerCase() : "";
                    String routeName = route.name.toLowerCase();
                    // Multi-layer fuzzy match: Route mapping + Description scanning
                    return specRoute.contains(routeName) || routeName.contains(specRoute) || description.contains(routeName);
                })
                .findFirst()
                .orElse(null);
    }

    private String cleanJsonResponse(String response) {
        if (response == null) return "{}";
        String cleaned = response.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned.trim();
    }

    private String readResource(String path) {
        try (java.io.InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) return "";
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            log.warn("Failed to read resource: {}", path);
            return "";
        }
    }

    private String generateShell(ProjectManifest manifest, List<Route> routes, String lang) {
        String template = readResource("/templates/StandardShell.html");

        // Categorize routes by navRole
        StringBuilder navContext = new StringBuilder("PAGE ROLES & ROUTES:\n");
        for (Route r : routes) {
            ProjectManifest.PageSpec spec = findPageSpec(manifest, r);
            String role = (spec != null && spec.getNavRole() != null) ? spec.getNavRole() : "PRIMARY";
            navContext.append(String.format("- Route: #%s, Name: %s, Role: %s\n", r.id, r.name, role));
        }

        String systemPrompt = """
                You are a UI Architect. Fill the slots for a high-fidelity application shell. 
                
                CONSTRAINTS:
                1. LOGO_AREA: Return 1-2 lines of Tailwind HTML.
                2. SIDEBAR_NAV: Return <a> tags for PRIMARY roles only. Use `x-bind:class="hash==='#pgX' ? 'active' : ''"`.
                3. UTILITY_BUTTONS: Icons for UTILITY roles (Search, Notif).
                4. PERSONAL_LINKS: <a> tags for PERSONAL roles.
                5. DESIGN DNA: %s
                6. OUTPUT: Return a JSON with keys: "logo", "sidebar", "utility", "personal".
                """.formatted(getDynamicDNA(manifest));

        String userPrompt = String.format("Architecture Context:\n%s\nLanguage: %s", navContext, lang);

        try {
            String response = llmClient.chat(systemPrompt, userPrompt);
            JsonNode root = objectMapper.readTree(cleanJsonResponse(response));

            String shell = template
                    .replace("{{TITLE}}", manifest.getOverview() != null ? manifest.getOverview() : "LingNow App")
                    .replace("{{LOGO_AREA}}", root.path("logo").asText())
                    .replace("{{SIDEBAR_NAV}}", root.path("sidebar").asText())
                    .replace("{{UTILITY_BUTTONS}}", root.path("utility").asText())
                    .replace("{{PERSONAL_LINKS}}", root.path("personal").asText());

            return shell;
        } catch (java.io.IOException e) {
            log.error("Shell fragment generation failed", e);
            return "<html><body>Error generating shell. Check logs.</body></html>";
        }
    }

    private String generateComponent(ProjectManifest manifest, Route route, ProjectManifest.PageSpec pageSpec, String lang) {
        String contextDescription = pageSpec != null ?
                "ARCHITECT'S PLAN: " + pageSpec.getDescription() + "\nEXPECTED COMPONENTS: " + String.join(", ", pageSpec.getComponents()) :
                "Generate a standard view for this feature.";

        String archetype = manifest.getArchetype() != null ? manifest.getArchetype() : "DASHBOARD";

        String systemPrompt = "You are a World-Class UI/UX Component Designer. Goal: High-Density Pro-Grade View.\n"
                + "RULES:\n"
                + "1. WRAPPER: `<div x-show=\"hash === '#" + route.id + "'\" class=\"animate-fade-in\">`.\n"
                + "2. PORTAL vs DETAIL (M7.0 Strategy-Aware):\n"
                + "   - IF PORTAL (navType: " + route.navType + "): USE THE GRID STRATEGY. High density. Show multiple content cards. Thumbnail right, metadata bottom.\n"
                + "   - IF LEAF_DETAIL: Centered reading layout (`max-w-3xl mx-auto`). MUST include 'Like/Save/Share' floating tools and a 'Comment Section' at the bottom.\n"
                + "   - MIXED CONTENT: If the intent is social (XiaoHongShu style), design for a Modal-ready detail view (Media Gallery | Side Info Panel).\n"
                + "3. ESSENTIALS: Support the 'essential_modules' list: " + (manifest.getUxStrategy() != null ? manifest.getUxStrategy().get("essential_modules") : "Standard") + "\n"
                + "4. DATA: Loop through `mockData` using `<template x-for=\"item in mockData\">`.\n"
                + "5. DESIGN DNA (Archetype: " + archetype + "):\n" + getDynamicDNA(manifest)
                + "6. OUTPUT: RAW HTML. NO JSON.";

        String userPrompt = String.format("Feature: %s (Route: #%s)\n%s\nUser Intent: %s\nMock Data example: %s",
                route.name, route.id, contextDescription, manifest.getUserIntent(), manifest.getMockData());

        try {
            String response = llmClient.chat(systemPrompt, userPrompt);
            return parseHtmlSnippet(response);
        } catch (java.io.IOException e) {
            log.error("Failed to generate component for {}", route.id, e);
            return "<!-- Error generating " + route.id + " -->";
        }
    }

    /**
     * Internal Route metadata
     */
    private record Route(String id, String name, String navType) {
    }

    /**
     * M6: Iterative Redesign - Refines the existing prototype based on user instructions.
     */
    public void redesign(ProjectManifest manifest, String instructions) {
        log.info("Designer is refining prototype based on instructions: {}", instructions);
        String existingHtml = manifest.getPrototypeHtml();

        String systemPrompt = "You are an expert UI/UX Refinement Agent. You will receive an existing SPA prototype (with Hash-based Alpine.js logic) and modification instructions.\n"
                + "YOUR GOAL: Evolve the existing design without breaking the 'mindmap' node coverage or 'mockData' consistency.\n"
                + "1. MAINTAIN FLOW: Ensure navigation (Hash changes) and state management still works perfectly.\n"
                + "2. CONSISTENT AESTHETICS: Keep the high-density list flow and technical blog aesthetics.\n"
                + "3. OUTPUT: Respond ONLY with the raw HTML string wrapped in ```html and ``` markers. DO NOT wrap it in JSON.";

        String lang = manifest.getMetaData() != null ? manifest.getMetaData().getOrDefault("lang", "EN") : "EN";
        if ("ZH".equalsIgnoreCase(lang)) {
            systemPrompt += "CRITICAL: If you add new UI elements or update texts, USE CHINESE.";
        }

        String userPrompt = String.format("Existing HTML: \n%s\n\nModification Instructions: %s", existingHtml, instructions);
                
        try {
            String response = llmClient.chat(systemPrompt, userPrompt);
            String html = parseHtmlSnippet(response);
            manifest.setPrototypeHtml(html);
            log.info("Prototype refinement complete ({} chars).", html.length());
        } catch (Exception e) {
            log.error("Prototype refinement failed", e);
            throw new RuntimeException("UI Redesign phase failed: " + e.getMessage());
        }
    }

    private String parseHtmlSnippet(String response) {
        if (response == null) return "";
        try {
            int startIndex = response.indexOf("```html");
            if (startIndex != -1) {
                startIndex += 7; // Length of ```html
                int endIndex = response.lastIndexOf("```");
                if (endIndex != -1 && endIndex > startIndex) {
                    return response.substring(startIndex, endIndex).trim();
                }
            }
            // Fallback: If no markers found, but it might be pure HTML anyway
            if (response.trim().startsWith("<")) {
                return response.trim();
            }
            log.warn("No valid HTML code block found in response.");
            return response;
        } catch (Exception e) {
            log.warn("Failed to extract HTML snippet from LLM output. Returning raw response.");
            return response;
        }
    }
}
