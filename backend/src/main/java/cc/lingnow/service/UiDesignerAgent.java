package cc.lingnow.service;

import cc.lingnow.llm.LlmClient;
import cc.lingnow.model.ProjectManifest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * UI Designer Agent - Creating high-fidelity, industry-aware interactive prototypes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UiDesignerAgent {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    /**
     * Generate a high-fidelity HTML prototype using an Industry-Aware Multi-Step Pipeline.
     */
    public void design(ProjectManifest manifest) {
        log.info("Designer is starting high-fidelity pipeline for: {}", manifest.getUserIntent());
        String lang = manifest.getMetaData() != null ? manifest.getMetaData().getOrDefault("lang", "EN") : "EN";

        try {
            // STEP 1: Parse Features from Mindmap
            List<String> featureNodes = parseFeatureNodes(manifest.getMindMap());

            // STEP 2: Aggregate Features (Define which ones go to the Dashboard vs Sub-pages)
            String dashboardNodes = featureNodes.subList(0, Math.min(3, featureNodes.size()))
                    .stream().collect(Collectors.joining(", "));

            // STEP 3: Generate App Shell (Layout + Nav + Interactive Overlays)
            log.info("Step 3: Generating Industry-Aware Shell...");
            String shellHtml = generateShell(manifest, featureNodes, lang);

            // STEP 4: Generate Clustered Components
            log.info("Step 4: Generating Detailed Feature Components...");
            StringBuilder contentSlots = new StringBuilder();

            // First component is ALWAYS the Dashboard/Index (Aggregates primary context)
            String dashboardHtml = generateComponent(manifest, "INDEX", "Dashboard / Home Feed", lang, true);
            contentSlots.append(dashboardHtml).append("\n");

            // Remaining sub-pages (skip the first few if they are in dashboard, but keep for navigation)
            for (int i = 0; i < Math.min(featureNodes.size(), 8); i++) {
                String nodeName = featureNodes.get(i);
                String routeId = java.net.URLEncoder.encode(nodeName.replace(" ", ""), java.nio.charset.StandardCharsets.UTF_8);
                log.info("Generating view for: {} ({})", nodeName, routeId);
                String componentHtml = generateComponent(manifest, routeId, nodeName, lang, false);
                contentSlots.append(componentHtml).append("\n");
            }

            // STEP 5: Final Assembly
            log.info("Step 5: Finalizing Assembly...");
            String finalHtml = assemble(shellHtml, contentSlots.toString());

            manifest.setPrototypeHtml(finalHtml);
            log.info("Industrial-grade prototype created successfully ({} chars).", finalHtml.length());

        } catch (Exception e) {
            log.error("Prototype design pipeline failed", e);
            throw new RuntimeException("UI Design pipeline failed: " + e.getMessage());
        }
    }

    private List<String> parseFeatureNodes(String mindMap) {
        if (mindMap == null || mindMap.trim().isEmpty()) return new ArrayList<>();
        List<String> featureNodes = new ArrayList<>();
        String[] lines = mindMap.split("\\n");
        for (int i = 0; i < lines.length; i++) {
            String current = lines[i];
            if (current.trim().isEmpty() || current.trim().startsWith("```")) continue;
            int currentIndent = current.length() - current.replaceAll("^\\s+", "").length();
            boolean isLeaf = true;
            if (i + 1 < lines.length) {
                String next = lines[i + 1];
                if (!next.trim().startsWith("```")) {
                    int nextIndent = next.length() - next.replaceAll("^\\s+", "").length();
                    if (nextIndent > currentIndent) isLeaf = false;
                }
            }
            if (isLeaf) featureNodes.add(current.replace("- ", "").trim());
        }
        return featureNodes;
    }

    private String generateShell(ProjectManifest manifest, List<String> featureNodes, String lang) {
        String systemPrompt = "You are a World-Class UX Architect. Your task is to generate a STUNNING, industry-aware SPA Layout Shell.\n\n"
                + "CONTEXT SENSING:\n"
                + "Extract the industry from User Intent: '" + manifest.getUserIntent() + "'.\n"
                + "- If TECH/BLOG: Use clean typography, high information density, Github/Linear-style sleekness, subtle borders.\n"
                + "- If SOCIAL/PET: Use vibrant colors, large soft-rounded cards (3xl), playful gradients, friendly avatars.\n"
                + "- If SAAS/DASHBOARD: Use professional deep-blue/gray palette, sidebar focus, dense metric cards.\n\n"
                + "TECHNICAL RULES:\n"
                + "1. FULL HTML5: Include Tailwind CSS, Alpine.js, and FontAwesome 6.\n"
                + "2. INTERACTIVE ROUTING: Use Alpine.js `x-data=\"{ activeTab: '#INDEX', showPostModal: false, isLoggedIn: false }\"`.\n"
                + "3. SHELL STRUCTURE:\n"
                + "   - TOP NAV: Logo (with industry relevant icon), Search Bar, User Profile (with Login/Register triggers), and Global 'Action' button (e.g., 'New Post').\n"
                + "   - SIDEBAR: Hierarchical navigation. Highlight active tab. Use URLEncoded hashes like `#PostDetail`.\n"
                + "   - MAIN: Body with smooth scrolling. Leave EXACTLY `{{CONTENT_SLOTS}}` tag for component injection.\n"
                + "4. PREMIUM UI: Use glassmorphism, soft shadows, and CSS animations (`animate-fade-in`). Icons MUST have sizes (e.g. `w-5 h-5`).\n"
                + "5. LANGUAGE: " + (lang.equals("ZH") ? "Chinese (Mandarin)" : "English") + ".\n"
                + "6. OUTPUT: Raw HTML ONLY, no JSON, wrapped in ```html.";

        StringBuilder navItems = new StringBuilder();
        for (String node : featureNodes.subList(0, Math.min(featureNodes.size(), 8))) {
            String encoded = java.net.URLEncoder.encode(node.replace(" ", ""), java.nio.charset.StandardCharsets.UTF_8);
            navItems.append(String.format("Node: %s (Hash: #%s), ", node, encoded));
        }

        String userPrompt = "Generate the Shell for: " + manifest.getUserIntent() + "\nNavigation nodes: " + navItems.toString();

        try {
            return parseHtmlSnippet(llmClient.chat(systemPrompt, userPrompt));
        } catch (java.io.IOException e) {
            throw new RuntimeException("Shell generation failed", e);
        }
    }

    private String generateComponent(ProjectManifest manifest, String routeId, String routeName, String lang, boolean isIndex) {
        String industryFocus = manifest.getUserIntent();
        String systemPrompt = "You are a World-Class UI Component Designer. Design a " + (isIndex ? "MASTER AGGREGATED DASHBOARD" : "DETAILED FEATURE VIEW") + ".\n\n"
                + "INDUSTRY: " + industryFocus + "\n"
                + "RULES:\n"
                + "1. OUTER WRAPPER: Must be `<div x-show=\"activeTab === '#" + routeId + "'\" class=\"h-full p-4 md:p-8 animate-in fade-in slide-in-from-bottom-4 duration-500\">`.\n"
                + "2. VISUAL QUALITY: Match the peak industry standards (e.g., if Tech Blog, look like LinuxDo/Medium; if Social, look like PawPal). Use rich mock data provided below.\n"
                + "3. " + (isIndex ? "AGGREGATION: Combine 3-4 key sub-features into a cohesive multi-column dashboard layout. Eliminate any empty vertical space." : "FOCUS: Deep dive into the specific feature functionality with rich tables/forms.") + "\n"
                + "4. INTERACTIVE MOCKUP: Use Alpine.js commands (like `@click`) to simulate real flows (e.g., clicking a post 'likes' it or opens a detail view).\n"
                + "5. MOCK DATA BINDING: Use the 'mockData' provided. DO NOT use Lorem Ipsum. Render REAL technical titles, comments, and stats.\n"
                + "6. ASSETS: Use relevant FontAwesome icons and Unsplash images (e.g., `https://images.unsplash.com/photo-1542831371-29b0f74f9713?auto=format&fit=crop&w=800` for code/tech).\n"
                + "7. OUTPUT: Raw HTML ONLY, wrapped in ```html.";

        String userPrompt = String.format("Design the %s component: %s (activeTab: #%s).\nUser Intent: %s\nMock Data JSON (MANDATORY USE): %s",
                isIndex ? "Index/Home" : "Feature", routeName, routeId, manifest.getUserIntent(), manifest.getMockData());

        try {
            return parseHtmlSnippet(llmClient.chat(systemPrompt, userPrompt));
        } catch (java.io.IOException e) {
            log.error("Failed to generate component {}", routeId, e);
            return "<!-- Gen Error for " + routeId + " -->";
        }
    }

    private String assemble(String shell, String slots) {
        if (shell.contains("{{CONTENT_SLOTS}}")) return shell.replace("{{CONTENT_SLOTS}}", slots);
        if (shell.contains("{{CONTENT_SLOT}}")) return shell.replace("{{CONTENT_SLOT}}", slots);
        return shell + "\n<!-- CONTENT -->\n" + slots;
    }

    private String parseHtmlSnippet(String response) {
        if (response == null) return "";
        try {
            int startIndex = response.indexOf("```html");
            if (startIndex != -1) {
                startIndex += 7;
                int endIndex = response.lastIndexOf("```");
                if (endIndex != -1 && endIndex > startIndex) return response.substring(startIndex, endIndex).trim();
            }
            if (response.trim().startsWith("<")) return response.trim();
            return response;
        } catch (Exception e) {
            return response;
        }
    }

    public void redesign(ProjectManifest manifest, String instructions) {
        log.info("Refining prototype: {}", instructions);
        String existingHtml = manifest.getPrototypeHtml();
        String lang = manifest.getMetaData() != null ? manifest.getMetaData().getOrDefault("lang", "EN") : "EN";

        String systemPrompt = "You are a UIUX Refinement Expert. Update the following SPA prototype.\n"
                + "1. PRESERVE STRUCTURE: Keep the Shell, Alpine.js routing, and high-fidelity aesthetics.\n"
                + "2. APPLY CHANGES: " + instructions + "\n"
                + "3. LANGUAGE: Use " + (lang.equals("ZH") ? "Chinese" : "English") + ".\n"
                + "4. OUTPUT: Raw HTML ONLY, wrapped in ```html.";

        String userPrompt = "Existing Code: \n" + existingHtml;

        try {
            String response = llmClient.chat(systemPrompt, userPrompt);
            manifest.setPrototypeHtml(parseHtmlSnippet(response));
        } catch (Exception e) {
            log.error("Redesign failed", e);
            throw new RuntimeException("UI Redesign phase failed: " + e.getMessage());
        }
    }
}

