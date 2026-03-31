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

            // Separate PRIMARY (sidebar) routes from OVERLAY (modal) routes
            List<Route> primaryRoutes = new ArrayList<>();
            List<Route> overlayRoutes = new ArrayList<>();
            for (Route r : routes) {
                ProjectManifest.PageSpec spec = findPageSpec(manifest, r);
                String role = (spec != null && spec.getNavRole() != null) ? spec.getNavRole() : "PRIMARY";
                if ("OVERLAY".equals(role)) {
                    overlayRoutes.add(r);
                } else {
                    primaryRoutes.add(r);
                }
            }
            // Fallback: if no OVERLAY defined by architect, synthesize a generic detail
            if (overlayRoutes.isEmpty()) {
                overlayRoutes.add(new Route("detail", "内容详情", "OVERLAY"));
            }

            // STEP 1: Generate App Shell
            log.info("Step 1: Generating Application Layout Shell with {} primary routes...", primaryRoutes.size());
            String shellHtml = generateShell(manifest, primaryRoutes, lang);

            // STEP 2: Generate Page Components
            log.info("Step 2: Generating Feature Components (Context Bridge Active)...");
            StringBuilder contentSlots = new StringBuilder();
            int count = 0;
            for (Route route : primaryRoutes) {
                if (count >= 6) {
                    log.info("Reached maximum of 6 primary pages. Skipping the rest.");
                    break;
                }
                ProjectManifest.PageSpec pageSpec = findPageSpec(manifest, route);
                log.info("Generating component for: {} (#{}) using Context: {}", route.name, route.id, (pageSpec != null));
                String componentHtml = generateComponent(manifest, route, pageSpec, lang);
                contentSlots.append(componentHtml).append("\n");
                count++;
            }

            // STEP 2b: Generate Detail Modal Component (OVERLAY slot)
            log.info("Step 2b: Generating Detail Modal for OVERLAY routes...");
            String modalHtml = generateDetailModal(manifest, overlayRoutes.get(0), lang);

            // STEP 3: Assembly
            log.info("Step 3: Assembling prototype...");
            String processedShell = shellHtml;
            String mockJson = manifest.getMockData() != null ? manifest.getMockData() : "[]";
            String safeMockJson = mockJson.replace("\"", "&quot;");
            processedShell = processedShell.replace("{{MOCK_DATA}}", safeMockJson);

            // Inject MODAL_SLOT
            processedShell = processedShell.replace("{{MODAL_SLOT}}", modalHtml);

            String finalHtml;
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
            log.warn("[Designer] Mindmap is empty. Attempting to fall back to page titles.");
            return manifest.getPages() != null ?
                    manifest.getPages().stream()
                            .filter(p -> "PRIMARY".equals(p.getNavRole()))
                            .map(p -> new Route("pg_" + p.getRoute().hashCode(), p.getRoute(), p.getNavType()))
                            .toList() : new ArrayList<>();
        }

        String[] lines = mindMap.split("\\n");
        List<Route> routes = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String current = lines[i].trim();
            // Skip empty lines or technical markers
            if (current.isEmpty() || current.startsWith("```") || current.equalsIgnoreCase("mindmap"))
                continue;

            // Robust extraction: removes common markdown list prefixes (-, *, 1., etc.)
            String name = current.replaceAll("^[\\-\\s\\*\\d\\.]+", "").trim();
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

    private String loadHandbook() {
        try {
            return java.nio.file.Files.readString(java.nio.file.Paths.get("/Users/eric/workspace/lingnow/.agents/skills/UI_DESIGN_HANDBOOK.md"));
        } catch (Exception e) {
            log.warn("[Designer] Handbook not found, falling back to core logic.");
            return "";
        }
    }

    private String generateShell(ProjectManifest manifest, List<Route> routes, String lang) {
        String template = readResource("/templates/StandardShell.html");
        String handbook = loadHandbook();

        // Categorize routes by navRole
        StringBuilder navContext = new StringBuilder("PAGE ROLES & ROUTES:\n");
        for (Route r : routes) {
            ProjectManifest.PageSpec spec = findPageSpec(manifest, r);
            String role = (spec != null && spec.getNavRole() != null) ? spec.getNavRole() : "PRIMARY";
            navContext.append(String.format("- Route: #%s, Name: %s, Role: %s\n", r.id, r.name, role));
        }

        String systemPrompt = String.format("""
                %s
                
                YOUR GOAL: Generate Application Shell Fragments as a JSON object.
                
                CRITICAL - YOU MUST OUTPUT PURE JSON ONLY:
                - Do NOT output <!DOCTYPE>, <html>, <head>, <body> tags anywhere.
                - Output ONLY a JSON object with these four string keys: logo, sidebar, utility, personal.
                - Each value is an HTML FRAGMENT (a few tags, not a full document).
                
                SIDEBAR PURITY RULE:
                - ONLY generate <a> tags for routes with 'Role: PRIMARY'.
                - UTILITY and PERSONAL roles MUST NOT appear in the sidebar.
                - Each sidebar link: <a @click="hash='#ID'" :class="hash==='#ID'?'bg-rose-50 text-rose-600 font-semibold':''" class="flex items-center gap-3 px-4 py-2.5 rounded-xl text-slate-700 hover:bg-slate-100 transition-all text-sm">
                
                DESIGN DNA: %s
                
                JSON OUTPUT FORMAT (respond ONLY with this, no extra text):
                {"logo": "<HTML_FRAGMENT>", "sidebar": "<HTML_FRAGMENT>", "utility": "<HTML_FRAGMENT>", "personal": "<HTML_FRAGMENT>"}
                """, handbook, getDynamicDNA(manifest));

        String userPrompt = String.format("Architecture Context:\n%s\nLanguage: %s", navContext, lang);

        try {
            String response = llmClient.chat(systemPrompt, userPrompt);
            JsonNode root = objectMapper.readTree(cleanJsonResponse(response));

            String logoHtml = root.path("logo").asText();
            String sidebarHtml = root.path("sidebar").asText();
            String utilityHtml = root.path("utility").asText();
            String personalHtml = root.path("personal").asText();

            // Validate: if sidebar is empty or JSON parse failed, use a safe fallback
            if (sidebarHtml.isBlank()) {
                log.warn("[Designer] Shell JSON parse returned empty sidebar. Using safe fallback nav.");
                StringBuilder fallbackNav = new StringBuilder();
                for (Route r : routes) {
                    fallbackNav.append(String.format(
                            "<a @click=\"hash='#%s'\" :class=\"hash==='#%s'?'bg-rose-50 text-rose-600 font-semibold':''\" class=\"flex items-center gap-3 px-4 py-2.5 rounded-xl text-slate-700 hover:bg-slate-100 transition-all text-sm\">%s</a>\n",
                            r.id, r.id, r.name));
                }
                sidebarHtml = fallbackNav.toString();
            }
            if (logoHtml.isBlank())
                logoHtml = "<span class=\"text-xl font-bold text-rose-500\">" + (manifest.getOverview() != null ? manifest.getOverview() : "LingNow") + "</span>";

            String shell = template
                    .replace("{{TITLE}}", manifest.getOverview() != null ? manifest.getOverview() : "LingNow App")
                    .replace("{{LOGO_AREA}}", logoHtml)
                    .replace("{{SIDEBAR_NAV}}", sidebarHtml)
                    .replace("{{UTILITY_BUTTONS}}", utilityHtml)
                    .replace("{{PERSONAL_LINKS}}", personalHtml);

            return shell;
        } catch (Exception e) {
            log.error("Shell fragment generation failed, using minimal safe fallback shell", e);
            // Safe fallback: build sidebar from routes list directly
            StringBuilder fallbackNav = new StringBuilder();
            for (Route r : routes) {
                fallbackNav.append(String.format(
                        "<a @click=\"hash='#%s'\" :class=\"hash==='#%s'?'bg-rose-50 text-rose-600 font-semibold':''\" class=\"flex items-center gap-3 px-4 py-2.5 rounded-xl text-slate-700 hover:bg-slate-100 transition-all text-sm\">%s</a>\n",
                        r.id, r.id, r.name));
            }
            String safeLogo = "<span class=\"text-xl font-bold text-rose-500\">" + (manifest.getOverview() != null ? manifest.getOverview() : "LingNow") + "</span>";
            return template
                    .replace("{{TITLE}}", manifest.getOverview() != null ? manifest.getOverview() : "LingNow")
                    .replace("{{LOGO_AREA}}", safeLogo)
                    .replace("{{SIDEBAR_NAV}}", fallbackNav.toString())
                    .replace("{{UTILITY_BUTTONS}}", "")
                    .replace("{{PERSONAL_LINKS}}", "");
        }
    }

    private String generateComponent(ProjectManifest manifest, Route route, ProjectManifest.PageSpec pageSpec, String lang) {
        String handbook = loadHandbook();
        String contextDescription = pageSpec != null ?
                "ARCHITECT'S PLAN: " + pageSpec.getDescription() + "\nEXPECTED COMPONENTS: " + String.join(", ", pageSpec.getComponents()) :
                "Generate a standard view for this feature.";

        String systemPrompt = String.format("""
                %s
                
                YOUR GOAL: Generate a high-density, pro-grade view for route: #%s.
                
                ⚠️ CRITICAL FORBIDDEN RULES (VIOLATION = BROKEN PAGE):
                - NEVER output <!DOCTYPE>, <html>, <head>, <body>, <script src=...> tags.
                - Output ONLY an HTML fragment starting with: <div x-show="hash === '#%s'">
                - NEVER start response with ```html or any markdown.
                
                MANDATORY INTERACTION LOGIC (MODAL BINDING):
                - If this is a Feed/Grid/List view, every item/card MUST have:
                  @click="selectedItem = item; hash = '#detail'" class="cursor-pointer"
                - Use Alpine.js x-for loop to render from mockData array.
                
                REQUIRED WRAPPER (start your output with exactly this):
                <div x-show="hash === '#%s'" class="animate-fade-in pb-8">
                """, handbook, route.id, route.id, route.id);

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
     * Phase 7.3.2: Generate a dedicated Detail Modal component for the OVERLAY slot.
     * This closes the interaction loop: card click → selectedItem set → modal renders.
     */
    private String generateDetailModal(ProjectManifest manifest, Route overlayRoute, String lang) {
        String handbook = loadHandbook();
        String langInstruction = "ZH".equalsIgnoreCase(lang) ? "Use Chinese labels." : "Use English labels.";

        String systemPrompt = String.format("""
                %s
                
                YOUR GOAL: Generate a beautiful FULL-SCREEN Detail Modal Panel for a community prototype.
                
                RULES:
                - The modal is ALREADY wrapped by `<template x-if="selectedItem">` in the shell. DO NOT add x-if.
                - Render fields from `selectedItem` using Alpine.js expressions: `x-text="selectedItem.fieldName"`.
                - MUST include: large hero image/cover, title, author info (avatar + name), stats (likes, comments, collects), full body content/description, tags, a comment input area, and a close button.
                - Close button MUST: @click="selectedItem = null; hash = '#pg1'".
                - Style: premium card, rounded-3xl, bg-white, shadow-2xl, overflow-y-auto max-h-[90vh].
                - %s
                OUTPUT: Return ONLY a raw HTML snippet (no ```html markers). Start with a <div class="relative ...">
                """, handbook, langInstruction);

        String userPrompt = String.format(
                "User Intent: %s\nMock Data sample: %s\nGenerate the detail modal inner content.",
                manifest.getUserIntent(), manifest.getMockData());

        try {
            String response = llmClient.chat(systemPrompt, userPrompt);
            // parseHtmlSnippet handles both raw HTML and ```html``` wrapped responses
            return parseHtmlSnippet(response);
        } catch (java.io.IOException e) {
            log.error("Failed to generate detail modal", e);
            // Hardcoded safe fallback modal
            return """       
                    <div class="relative bg-white rounded-3xl p-8 max-w-2xl mx-auto shadow-2xl">
                      <button @click="selectedItem = null; hash = '#pg1'" class="absolute top-4 right-4 text-slate-400 hover:text-slate-700 text-2xl">&times;</button>
                      <h2 class="text-2xl font-bold mb-2" x-text="selectedItem.title || selectedItem.标题 || '详情'"></h2>
                      <p class="text-slate-500 text-sm mb-4" x-text="selectedItem.author || selectedItem.作者 || ''"></p>
                      <p class="text-slate-700 leading-relaxed" x-text="selectedItem.content || selectedItem.内容 || selectedItem.description || ''"></p>
                    </div>
                    """;
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
