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
 * UI Designer Agent - Creating high-fidelity, autonomous, and functional prototypes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UiDesignerAgent {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public void design(ProjectManifest manifest) {
        log.info("Designer is initiating Autonomous Product Logic for: {}", manifest.getUserIntent());
        String lang = manifest.getMetaData() != null ? manifest.getMetaData().getOrDefault("lang", "EN") : "EN";

        try {
            List<String> featureNodes = parseFeatureNodes(manifest.getMindMap());

            // STEP 1: Generate Autonomous App Shell (Includes Global State + Data Injection)
            log.info("Step 1: Generating Autonomous Shell with Data Injection...");
            String shellHtml = generateShell(manifest, featureNodes, lang);

            // STEP 2: Generate Components (Dashboard + Dynamic Views)
            log.info("Step 2: Generating Functional Components...");
            StringBuilder contentSlots = new StringBuilder();

            // Index/Dashboard
            contentSlots.append(generateComponent(manifest, "INDEX", "Aggregated Dashboard", lang, true)).append("\n");

            // Individual Sub-pages & Detail View Logic
            for (int i = 0; i < Math.min(featureNodes.size(), 8); i++) {
                String nodeName = featureNodes.get(i);
                String routeId = java.net.URLEncoder.encode(nodeName.replace(" ", ""), java.nio.charset.StandardCharsets.UTF_8);
                contentSlots.append(generateComponent(manifest, routeId, nodeName, lang, false)).append("\n");
            }

            // Implicit Module: Post Detail View (Self-thought)
            contentSlots.append(generateDetailView(lang)).append("\n");

            // STEP 3: Final Assembly
            String finalHtml = assemble(shellHtml, contentSlots.toString());
            manifest.setPrototypeHtml(finalHtml);
            log.info("Autonomous Prototype generated successfully.");

        } catch (Exception e) {
            log.error("Prototype design failed", e);
            throw new RuntimeException("UI Design failed: " + e.getMessage());
        }
    }

    private List<String> parseFeatureNodes(String mindMap) {
        if (mindMap == null || mindMap.trim().isEmpty()) return new ArrayList<>();
        List<String> featureNodes = new ArrayList<>();
        String[] lines = mindMap.split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("```") || !trimmed.startsWith("- ")) continue;
            featureNodes.add(trimmed.substring(2).trim());
        }
        return featureNodes;
    }

    private String generateShell(ProjectManifest manifest, List<String> featureNodes, String lang) {
        // Prepare Mock Data for injection
        String rawMockData = manifest.getMockData() != null ? manifest.getMockData() : "[]";

        String systemPrompt = "You are a World-Class Product Architect. Generate a FULLY FUNCTIONAL Industrial SPA Shell.\n\n"
                + "AUTONOMOUS THINKING:\n"
                + "- Even if not requested, ALWAYS add: 1) Notification Center 2) User Profile/Dashboard 3) Auth Modal 4) Search-to-Filter logic.\n\n"
                + "DATA INJECTION (CRITICAL):\n"
                + "- Use `<div x-data=\"{ activeTab: '#INDEX', searchQuery: '', isLoggedIn: false, showAuthModal: false, showNotif: false, selectedPost: null, allData: " + rawMockData.replace("\"", "&quot;") + " }\">`.\n\n"
                + "INTERACTIVE RULES:\n"
                + "1. LOG IN FLOW: Login button -> `showAuthModal = true`. Inside modal, 'Confirm' -> `isLoggedIn = true`.\n"
                + "2. NAV FLOW: Use `#HASH` for `activeTab`. Highlight active state.\n"
                + "3. NOTIFICATION FLOW: Bell icon -> `showNotif = !showNotif` (Drawer/Popout).\n"
                + "4. SHELL UI: Use Tailwind, Alpine, FontAwesome. Premium dark theme by default unless intent is different.\n"
                + "5. CDNs: Include Tailwind, Alpine, FontAwesome in <head>.\n"
                + "6. CONTENT: Leave `{{CONTENT_SLOTS}}` tag.\n"
                + "7. OUTPUT: Raw HTML in ```html.";

        String userPrompt = "App Intent: " + manifest.getUserIntent() + "\nCore Features: " + String.join(", ", featureNodes);

        try {
            return parseHtmlSnippet(llmClient.chat(systemPrompt, userPrompt));
        } catch (java.io.IOException e) {
            throw new RuntimeException("Shell Gen Failed", e);
        }
    }

    private String generateComponent(ProjectManifest manifest, String routeId, String routeName, String lang, boolean isIndex) {
        String systemPrompt = "You are a World-Class Component Designer. Design a " + (isIndex ? "MASTER DASHBOARD" : "FEATURE VIEW") + ".\n\n"
                + "INTERACTION RULES:\n"
                + "1. VISIBILITY: Use `x-show=\"activeTab === '#" + routeId + "'\"`.\n"
                + "2. DATA BINDING: Card list MUST use `x-for=\"item in allData\"` and `x-show=\"!searchQuery || JSON.stringify(item).toLowerCase().includes(searchQuery.toLowerCase())\"`.\n"
                + "3. CLICK-TO-DETAIL: Clicking a card -> `selectedPost = item; activeTab = '#PostDetail'`.\n"
                + "4. PREMIUM DESIGN: Use CSS transitions, glassmorphism, and high-density layouts.\n"
                + "5. OUTPUT: Raw HTML in ```html.";

        String userPrompt = String.format("Component: %s (#%s)\nContext: %s", routeName, routeId, manifest.getUserIntent());
        try {
            return parseHtmlSnippet(llmClient.chat(systemPrompt, userPrompt));
        } catch (java.io.IOException e) {
            return "<!-- Gen Error -->";
        }
    }

    private String generateDetailView(String lang) {
        String systemPrompt = "Generate a 'Post Detail' view component.\n"
                + "1. Use `x-show=\"activeTab === '#PostDetail'\"`.\n"
                + "2. Display `:src=\"selectedPost?.image\"`, `:text=\"selectedPost?.title\"`, etc.\n"
                + "3. Add a 'Comments' section and a 'Back to Feed' button (`@click=\"activeTab = '#INDEX'\"`).\n"
                + "4. Premium layout like Medium or LinuxDo.\n"
                + "5. OUTPUT: Raw HTML in ```html.";
        try {
            return parseHtmlSnippet(llmClient.chat(systemPrompt, "Language: " + lang));
        } catch (java.io.IOException e) {
            return "";
        }
    }

    private String assemble(String shell, String slots) {
        if (shell.contains("{{CONTENT_SLOTS}}")) return shell.replace("{{CONTENT_SLOTS}}", slots);
        return shell + "\n" + slots;
    }

    private String parseHtmlSnippet(String response) {
        if (response == null) return "";
        try {
            int start = response.indexOf("```html");
            if (start != -1) {
                start += 7;
                int end = response.lastIndexOf("```");
                if (end != -1 && end > start) return response.substring(start, end).trim();
            }
            if (response.trim().startsWith("<")) return response.trim();
        } catch (Exception ignored) {
        }
        return response;
    }

    public void redesign(ProjectManifest manifest, String instructions) {
        log.info("Autonomous Redesign: {}", instructions);
        String existingHtml = manifest.getPrototypeHtml();
        String systemPrompt = "You are a UIUX Refinement Agent. Instructions: " + instructions + "\n"
                + "1. Maintain all interactive global states and data injection.\n"
                + "2. Output Raw HTML only.";

        try {
            String response = llmClient.chat(systemPrompt, "Current: " + existingHtml);
            manifest.setPrototypeHtml(parseHtmlSnippet(response));
        } catch (Exception e) {
            throw new RuntimeException("UI Redesign Failed", e);
        }
    }
}


