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
            // STEP 1: Generate App Shell
            log.info("Step 1: Generating Application Layout Shell...");
            String shellHtml = generateShell(manifest, lang);

            // STEP 2: Generate Page Components
            log.info("Step 2: Generating Feature Components...");
            StringBuilder contentSlots = new StringBuilder();

            try {
                // Parse text-based mindmap to extract features (leaves or significant nodes)
                String mindMap = manifest.getMindMap();
                if (mindMap == null || mindMap.trim().isEmpty()) {
                    log.error("Mindmap is missing in manifest for session: {}", manifest.getId());
                    contentSlots.append("<div class='p-8 text-center text-red-500'>Error: Architectural plan (mindmap) is missing. Please regenerate the plan.</div>");
                    throw new RuntimeException("Mindmap is missing. Visual design requires an architectural plan.");
                }

                String[] lines = mindMap.split("\\n");
                List<String> featureNodes = new ArrayList<>();

                for (int i = 0; i < lines.length; i++) {
                    String current = lines[i];
                    if (current.trim().isEmpty() || current.trim().startsWith("```")) continue;

                    int currentIndent = current.length() - current.replaceAll("^\\s+", "").length();
                    boolean isLeaf = true;

                    if (i + 1 < lines.length) {
                        String next = lines[i + 1];
                        if (next.trim().startsWith("```")) {
                            isLeaf = true; // end of markdown block
                        } else {
                            int nextIndent = next.length() - next.replaceAll("^\\s+", "").length();
                            if (nextIndent > currentIndent) {
                                isLeaf = false; // it has children
                            }
                        }
                    }

                    if (isLeaf) {
                        featureNodes.add(current.replace("- ", "").trim());
                    }
                }

                if (featureNodes.isEmpty()) {
                    // Fallback to whatever is there
                    for (String line : lines) {
                        if (!line.trim().isEmpty() && !line.trim().startsWith("```")) {
                            featureNodes.add(line.replace("- ", "").trim());
                        }
                    }
                }

                int count = 0;
                for (String nodeName : featureNodes) {
                    if (count >= 5) {
                        log.info("Reached maximum of 5 pages for initial generation to save time/tokens. Skipping the rest.");
                        break;
                    }
                    // Use a sanitized node name as the route ID so the Shell LLM and Component LLM match
                    String routeId = java.net.URLEncoder.encode(nodeName.replace(" ", ""), java.nio.charset.StandardCharsets.UTF_8);

                    log.info("Generating component for: {} ({})", nodeName, routeId);
                    String componentHtml = generateComponent(manifest, routeId, nodeName, lang);
                    contentSlots.append(componentHtml).append("\n");
                    count++;
                }

            } catch (Exception e) {
                log.error("Failed to parse mindMap text tree, skipping components", e);
                contentSlots.append("<div class='p-8 text-center text-red-500'>Failed to parse mindmap nodes for individual pages: ").append(e.getMessage()).append("</div>");
            }

            // STEP 3: Assembly
            log.info("Step 3: Assembling prototype...");
            String finalHtml;
            if (shellHtml.contains("{{CONTENT_SLOTS}}")) {
                finalHtml = shellHtml.replace("{{CONTENT_SLOTS}}", contentSlots.toString());
            } else if (shellHtml.contains("{{CONTENT_SLOT}}")) {
                finalHtml = shellHtml.replace("{{CONTENT_SLOT}}", contentSlots.toString());
            } else {
                // Fallback in case LLM didn't put the exact placeholder
                log.warn("Shell missing {{CONTENT_SLOTS}} tag. Appending blocks manually.");
                finalHtml = shellHtml + "\n<!-- ASSEMBLED COMPONENTS -->\n" + contentSlots.toString();
            }

            manifest.setPrototypeHtml(finalHtml);
            log.info("Multi-step prototype created successfully ({} chars).", finalHtml.length());

        } catch (Exception e) {
            log.error("Prototype multi-step design failed", e);
            throw new RuntimeException("UI Design pipeline failed: " + e.getMessage());
        }
    }

    private String generateShell(ProjectManifest manifest, String lang) {
        String systemPrompt = "You are a World-Class UI/UX Architect. Your goal is to deliver an industrial-grade SPA Application Layout Shell.\n"
                + "RULES:\n"
                + "1. FULL HTML: You MUST output a complete HTML5 document (`<html><head>...</head><body class=\"bg-gray-50 text-gray-900 antialiased\">...</body></html>`).\n"
                + "2. CDNs (CRITICAL): Inside `<head>`, you MUST include: Tailwind CSS `<script src=\"https://cdn.tailwindcss.com\"></script>`, Alpine.js `<script defer src=\"https://cdn.jsdelivr.net/npm/alpinejs@3.x.x/dist/cdn.min.js\"></script>`, and FontAwesome `<link href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.0.0/css/all.min.css\" rel=\"stylesheet\">`.\n"
                + "3. HASH-ROUTER: Globally define Alpine routing on the body or main wrapper: `<div x-data=\"{ hash: window.location.hash || '#/' }\" @hashchange.window=\"hash = window.location.hash\" class=\"flex h-screen overflow-hidden\">`.\n"
                + "4. SKELETON ONLY: Build the Sidebar, Top Navigation, and Layout wrappers using the provided 'mindMap' text tree for navigation links. The href MUST be the EXACT URL-encoded node name with spaces removed (e.g. `href=\"#UserManagement\"` and `:class=\"hash === '#UserManagement' ? 'bg-primary-500 text-white' : 'text-gray-400'\"`).\n"
                + "5. CONTENT SLOT: Inside your `<main>` tag, you MUST leave EXACTLY the string `{{CONTENT_SLOTS}}` without any HTML around it.\n"
                + "6. PREMIUM AESTHETICS: Use gorgeous Tailwind classes. Add glassmorphism (`backdrop-blur-md bg-white/70`), shadows (`shadow-sm`), and soft borders. NEVER generate giant unconstrained SVGs; all icons MUST have `w-5 h-5` or similar sizes.\n"
                + "7. LANGUAGE: All UI text must be in " + (lang.equals("ZH") ? "CHINESE" : "ENGLISH") + ".\n"
                + "8. OUTPUT: Respond ONLY with the raw HTML string wrapped in ```html and ``` markers. DO NOT wrap it in JSON.";

        String userPrompt = String.format("User Intent: %s\nPlanned Routing/Mindmap: %s", manifest.getUserIntent(), manifest.getMindMap());

        try {
            String response = llmClient.chat(systemPrompt, userPrompt);
            return parseHtmlSnippet(response);
        } catch (java.io.IOException e) {
            throw new RuntimeException("LLM Shell generation failed", e);
        }
    }

    private String generateComponent(ProjectManifest manifest, String routeId, String routeName, String lang) {
        String systemPrompt = "You are a World-Class UI/UX Component Designer. Your goal is to design a single, standalone high-fidelity feature view.\n"
                + "RULES:\n"
                + "1. VIEW WRAPPER (CRITICAL): The OUTERMOST tag of your entire HTML must be EXACTLY: `<div x-show=\"hash === '#" + routeId + "'\" class=\"h-full overflow-y-auto animate-fade-in\">`.\n"
                + "2. FOCUS ON DETAIL: Make it incredibly detailed, realistic, and highly polished with Tailwind CSS. Use grids (`grid-cols-1 md:grid-cols-3`), beautiful hover effects, and nice padding (e.g., `p-6`). Use FontAwesome classes for icons (e.g., `fas fa-chart-line`).\n"
                + "3. NO SHELL: Do NOT generate `<html>`, `<body>`, `x-data` routers, sidebars, or headers. Just generate the inner page content `div`.\n"
                + "4. DATA-DRIVEN: Use the 'mockData' provided conceptually to populate beautiful tables, metric cards (like Stripe/Vercel dashboards), charts placeholders, or lists. No empty generic lorem ipsum.\n"
                + "5. SVG/IMAGE RULES: NEVER generate massive unconstrained svgs. If using svgs, adding `w-6 h-6` or similar is mandatory. Use Unsplash for placeholder avatars.\n"
                + "6. OUTPUT: Respond ONLY with the raw HTML string wrapped in ```html and ``` markers. DO NOT wrap it in JSON.";

        String userPrompt = String.format("Generate the highly-detailed view for feature node: %s (Route Hash: #%s).\nUser Intent: %s\nMock Data: %s",
                routeName, routeId, manifest.getUserIntent(), manifest.getMockData());

        try {
            String response = llmClient.chat(systemPrompt, userPrompt);
            return parseHtmlSnippet(response);
        } catch (java.io.IOException e) {
            log.error("Failed to generate component for {}", routeId, e);
            return "<!-- Error generating " + routeId + " -->";
        }
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
