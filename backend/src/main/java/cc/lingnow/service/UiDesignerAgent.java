package cc.lingnow.service;

import cc.lingnow.llm.LlmClient;
import cc.lingnow.model.ProjectManifest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * UI Designer Agent - Responsible for creating and refining high-fidelity prototypes (HTML/Tailwind).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UiDesignerAgent {

    private final LlmClient llmClient;

    private String getDynamicDNA(ProjectManifest manifest) {
        Map<String, String> meta = manifest.getMetaData();
        if (meta == null) return """
                - Background: bg-slate-50
                - Cards: bg-white border border-slate-200 shadow-sm rounded-2xl p-6
                - Buttons: rounded-lg font-medium transition-all
                - Primary: indigo-600
                """;

        return String.format("""
                        - Background: %s
                        - Cards: %s
                        - Buttons: %s
                        - Primary: %s
                        - Font: %s
                        - Reasoning: %s
                        """,
                meta.getOrDefault("visual_bgClass", "bg-slate-50"),
                meta.getOrDefault("visual_cardClass", "bg-white border shadow-sm rounded-2xl p-6"),
                meta.getOrDefault("visual_buttonClass", "rounded-lg font-medium transition-all"),
                meta.getOrDefault("visual_primaryColor", "indigo-600"),
                meta.getOrDefault("visual_fontFamily", "font-sans"),
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

            // Simple logic: treat all indented nodes as feature pages for SPA
            String name = current.replace("- ", "").trim();
            if (name.isEmpty()) continue;

            // Stable ID Generation: use index to ensure CJK support in hash
            String id = "pg" + (i + 1);
            routes.add(new Route(id, name));
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

    private String generateShell(ProjectManifest manifest, List<Route> routes, String lang) {
        StringBuilder routeContext = new StringBuilder("ENFORCED ROUTES (Exact hashes to use in Sidebar links):\n");
        for (Route r : routes) {
            routeContext.append(String.format("- Name: %s, ID: %s (Link: #%s)\n", r.name, r.id, r.id));
        }

        String systemPrompt = "You are a World-Class UI/UX Architect. Your goal is a Professional Light-Themed SaaS Layout Shell (Apple/Stripe style).\n"
                + "RULES:\n"
                + "1. FULL HTML: Use `bg-slate-50/50 text-slate-900 antialiased font-sans`.\n"
                + "2. CDNs: Include Tailwind, Alpine.js, and FontAwesome. DO NOT use `integrity` or `crossorigin` attributes.\n"
                + "   - Tailwind: <script src=\"https://cdn.tailwindcss.com\"></script>\n"
                + "   - Alpine.js: <script defer src=\"https://cdn.jsdelivr.net/npm/alpinejs@3.x.x/dist/cdn.min.js\"></script>\n"
                + "3. STATE: Root tag: `<div x-data=\"{ hash: window.location.hash || '#" + routes.get(0).id + "', mockData: {{MOCK_DATA}}, selectedItem: null, drawerOpen: false, search: '' }\" @hashchange.window=\"hash = window.location.hash\" class=\"flex h-screen overflow-hidden\">`.\n"
                + "4. PREMIUM SIDEBAR: Fixed, white sidebar. Grouped nav with solid icons.\n"
                + "   DESIGN DNA:\n" + getDynamicDNA(manifest)
                + "   CRITICAL: Sidebar links MUST use the exact IDs provided in the context (href=\"#id\").\n"
                + "5. GLOBAL DRAWER: Include a transition-friendly side drawer (x-show=\"drawerOpen\") for detail views.\n"
                + "6. CONTENT SLOT: Inside `<main class=\"flex-1 overflow-auto p-12\">`, leave `{{CONTENT_SLOTS}}`.\n"
                + "7. LANGUAGE: " + (lang.equals("ZH") ? "CHINESE" : "ENGLISH") + ".\n"
                + "8. OUTPUT: RAW HTML. NO JSON.";

        String userPrompt = String.format("User Intent: %s\n\n%s", manifest.getUserIntent(), routeContext.toString());

        try {
            String response = llmClient.chat(systemPrompt, userPrompt);
            return parseHtmlSnippet(response);
        } catch (java.io.IOException e) {
            throw new RuntimeException("LLM Shell generation failed", e);
        }
    }

    private String generateComponent(ProjectManifest manifest, Route route, ProjectManifest.PageSpec pageSpec, String lang) {
        String contextDescription = pageSpec != null ?
                "ARCHITECT'S PLAN: " + pageSpec.getDescription() + "\nEXPECTED COMPONENTS: " + String.join(", ", pageSpec.getComponents()) :
                "Generate a standard dashboard view for this feature.";

        String taskFlows = manifest.getMetaData() != null ? manifest.getMetaData().getOrDefault("taskFlows", "No explicit task flows defined.") : "No explicit task flows defined.";
        
        String systemPrompt = "You are a World-Class UI/UX Component Designer. Goal: Standalone Dashboard Page.\n"
                + "RULES:\n"
                + "1. WRAPPER: `<div x-show=\"hash === '#" + route.id + "'\" class=\"space-y-8 animate-fade-in\">`.\n"
                + "2. INTERACTION: Every list item MUST have `@click=\"selectedItem = item; drawerOpen = true\"`. Use interactive cursor and hover states.\n"
                + "3. TASK FLOWS: Ensure this page supports these specific user journeys: " + taskFlows + "\n"
                + "   CRITICAL: If this page is part of a flow, include functional buttons with the correct `#hash-links` to fulfill the journey.\n"
                + "4. DATA: Loop through `mockData` using `<template x-for=\"item in mockData\">`.\n"
                + "   CRITICAL: Use the high-fidelity fields suggested in the architectural plan (e.g. item.recoveryScore).\n"
                + "5. DESIGN DNA:\n" + getDynamicDNA(manifest)
                + "6. DENSITY: Use multi-column grids, status badges, and large-card analytics.\n"
                + "7. OUTPUT: RAW HTML. NO JSON.";

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
    private record Route(String id, String name) {
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
