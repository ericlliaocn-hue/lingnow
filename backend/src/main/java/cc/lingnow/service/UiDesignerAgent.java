package cc.lingnow.service;

import cc.lingnow.llm.LlmClient;
import cc.lingnow.model.ProjectManifest;
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

            // STEP 2: Generate Page Components
            log.info("Step 2: Generating Feature Components...");
            StringBuilder contentSlots = new StringBuilder();

            int count = 0;
            for (Route route : routes) {
                if (count >= 6) { // Increased to 6 to cover more ground
                    log.info("Reached maximum of 6 pages for initial generation. Skipping the rest.");
                    break;
                }
                log.info("Generating component for: {} (#{})", route.name, route.id);
                String componentHtml = generateComponent(manifest, route, lang);
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
            if (current.trim().isEmpty() || current.trim().startsWith("```")) continue;

            int currentIndent = current.length() - current.replaceAll("^\\s+", "").length();
            boolean isLeaf = true;

            if (i + 1 < lines.length) {
                String next = lines[i + 1];
                if (next.trim().startsWith("```")) {
                    isLeaf = true;
                } else {
                    int nextIndent = next.length() - next.replaceAll("^\\s+", "").length();
                    if (nextIndent > currentIndent) {
                        isLeaf = false;
                    }
                }
            }

            if (isLeaf) {
                String name = current.replace("- ", "").trim();
                // Generate a clean, lowercase alphanumeric ID for the hash
                String id = name.toLowerCase().replaceAll("[^a-z0-9]", "");
                if (id.isEmpty()) id = "page" + i;
                routes.add(new Route(id, name));
            }
        }
        return routes;
    }

    private String generateShell(ProjectManifest manifest, List<Route> routes, String lang) {
        StringBuilder routeContext = new StringBuilder("ENFORCED ROUTES (Exact hashes to use in Sidebar links):\n");
        for (Route r : routes) {
            routeContext.append(String.format("- Name: %s, ID: %s (Link: #%s)\n", r.name, r.id, r.id));
        }

        String systemPrompt = "You are a World-Class UI/UX Architect. Your goal is a Professional Light-Themed SaaS Layout Shell (Apple/Stripe style).\n"
                + "RULES:\n"
                + "1. FULL HTML: Use `bg-slate-50/50 text-slate-900 antialiased font-sans`.\n"
                + "2. CDNs: Include Tailwind, Alpine.js, and FontAwesome. IMPORTANT: DO NOT use `integrity` or `crossorigin` attributes on script/link tags.\n"
                + "   - Tailwind: <script src=\"https://cdn.tailwindcss.com\"></script>\n"
                + "   - FontAwesome: <link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\">\n"
                + "   - Alpine.js: <script defer src=\"https://cdn.jsdelivr.net/npm/alpinejs@3.x.x/dist/cdn.min.js\"></script>\n"
                + "3. STATE: Root tag: `<div x-data=\"{ hash: window.location.hash || '#" + routes.get(0).id + "', mockData: {{MOCK_DATA}}, selectedItem: null, drawerOpen: false }\" @hashchange.window=\"hash = window.location.hash\" class=\"flex h-screen overflow-hidden\">`.\n"
                + "4. PREMIUM SIDEBAR: Fixed, white sidebar. Grouped nav with solid icons.\n"
                + "   CRITICAL: Sidebar links MUST use the exact IDs provided in the context (e.g. href=\"#dashboard\"). DO NOT invent your own hashes.\n"
                + "   ACTIVE STATE: If `hash === '#id'`, apply `bg-slate-100 text-indigo-600` for visual feedback.\n"
                + "5. GLOBAL DRAWER: Include a fixed-right side drawer (`x-show=\"drawerOpen\"`) for detail viewing.\n"
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

    private String generateComponent(ProjectManifest manifest, Route route, String lang) {
        String systemPrompt = "You are a World-Class UI/UX Component Designer. Goal: Standalone Dashboard Page.\n"
                + "RULES:\n"
                + "1. WRAPPER: `<div x-show=\"hash === '#" + route.id + "'\" class=\"space-y-8 animate-fade-in\">`.\n"
                + "2. INTERACTION: List items/rows MUST have `@click=\"selectedItem = item; drawerOpen = true\"`.\n"
                + "3. DATA: Loop through `mockData` using `<template x-for=\"item in mockData\">`.\n"
                + "4. AESTHETICS: White cards (`bg-white border border-slate-200 shadow-sm rounded-2xl p-6`). High-contrast typography.\n"
                + "5. DENSITY: Use multi-column grids and status badges.\n"
                + "6. OUTPUT: RAW HTML. NO JSON.";

        String userPrompt = String.format("Generate the highly-detailed view for feature: %s (Route Hash: #%s).\nUser Intent: %s\nMock Data example: %s",
                route.name, route.id, manifest.getUserIntent(), manifest.getMockData());

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
