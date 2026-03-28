package cc.lingnow.service;

import cc.lingnow.llm.LlmClient;
import cc.lingnow.model.ProjectManifest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
     * Generate a high-fidelity HTML prototype based on the manifest.
     */
    public void design(ProjectManifest manifest) {
        log.info("Designer is creating initial prototype for: {}", manifest.getUserIntent());
        
        // Language awareness needs to be determined before constructing the prompt for the design aesthetics part
        String lang = manifest.getMetaData() != null ? manifest.getMetaData().getOrDefault("lang", "EN") : "EN";

        String systemPrompt = "You are a World-Class UI/UX Architect specialized in high-density technical platforms. Your goal is to deliver a bespoke, industrial-grade application prototype.\n"
                + "RULES:\n"
                + "1. ANTI-TEMPLATE AESTHETICS: Strictly FORBID using repetitive uniform card grids (the 'templated' look) for main content feeds. Instead, implement high-density 'Linear List Flows' with subtle 1px dividers, zero-to-minimal box shadows, and tight padding.\n"
                + "2. ANALYTICAL DESIGN: Study and reference the structural principles of Linux.do (Discourse), Juejin (掘金), and CSDN. Prioritize INFORMATION DENSITY and VERTICAL READABILITY. Use system fonts (Inter, -apple-system) and clear typographic hierarchies.\n"
                + "3. DYNAMIC SPA ARCHITECTURE: Implement a functional view for EVERY page in the manifest using Alpine.js 'x-show'. Transition logic must be seamless.\n"
                + "4. 100% CLICKABLE: Every navigation link, list item, and button MUST have visual feedback (Cursor/Hover/Active) and functional state transitions.\n"
                + "5. CRITICAL: Every interactive component needs a 'data-lingnow-id' for inspection.\n"
                + "6. LANGUAGE: All content MUST be in " + (lang.equals("ZH") ? "CHINESE" : "ENGLISH") + ".\n"
                + "7. RESPONSIVE: Provide layouts optimized for Desktop, Tablet, and Mobile.\n"
                + "Respond with a single JSON object: {\"prototypeHtml\": \"...\"}.";
        
        String userPrompt = String.format("User Intent: %s\nPlanned Features: %s\nPlanned Pages: %s", 
                manifest.getUserIntent(), manifest.getFeatures(), manifest.getPages());
                
        try {
            String response = llmClient.chat(systemPrompt, userPrompt);
            String jsonStr = cleanJsonResponse(response);
            JsonNode node = objectMapper.readTree(jsonStr);
            String html = node.get("prototypeHtml").asText();
            manifest.setPrototypeHtml(html);
            log.info("Initial prototype created ({} chars).", html.length());
        } catch (Exception e) {
            log.error("Prototype design failed", e);
            throw new RuntimeException("UI Design phase failed: " + e.getMessage());
        }
    }

    /**
     * M6: Iterative Redesign - Refines the existing prototype based on user instructions.
     */
    public void redesign(ProjectManifest manifest, String instructions) {
        log.info("Designer is refining prototype based on instructions: {}", instructions);
        String existingHtml = manifest.getPrototypeHtml();

        String systemPrompt = "You are an expert UI/UX Refinement Agent. You will receive an existing SPA prototype (with multi-view Alpine.js logic) and modification instructions.\n"
                + "YOUR GOAL: Evolve the existing design without breaking the 'x-data' state management or navigation logic.\n"
                + "1. MAINTAIN FLOW: Ensure switching between views (Home, Detail, Login, etc.) still works perfectly.\n"
                + "2. CONSISTENT AESTHETICS: Keep the industrial-grade, non-patterned look while applying updates.\n"
                + "3. OUTPUT: Respond ONLY with a single JSON object: {\"prototypeHtml\": \"...\"}.";

        // Language awareness
        String lang = manifest.getMetaData() != null ? manifest.getMetaData().getOrDefault("lang", "EN") : "EN";
        if ("ZH".equalsIgnoreCase(lang)) {
            systemPrompt += "CRITICAL: If you add new UI elements or update texts, USE CHINESE.";
        }
        
        String userPrompt = String.format("Existing HTML: \n%s\n\nModification Instructions: %s", 
                existingHtml, instructions);
                
        try {
            String response = llmClient.chat(systemPrompt, userPrompt);
            String jsonStr = cleanJsonResponse(response);
            JsonNode node = objectMapper.readTree(jsonStr);
            String html = node.get("prototypeHtml").asText();
            manifest.setPrototypeHtml(html);
            log.info("Prototype refinement complete ({} chars).", html.length());
        } catch (Exception e) {
            log.error("Prototype refinement failed", e);
            throw new RuntimeException("UI Redesign phase failed: " + e.getMessage());
        }
    }

    private String cleanJsonResponse(String response) {
        if (response == null) return "{}";
        String cleaned = response.trim();
        
        // Find JSON block if wrapped in markdown backticks
        if (cleaned.contains("```")) {
            int start = cleaned.indexOf("{");
            int end = cleaned.lastIndexOf("}");
            if (start != -1 && end != -1 && end > start) {
                return cleaned.substring(start, end + 1);
            }
        }
        
        // Fallback for non-JSON responses
        if (!cleaned.startsWith("{")) {
            int start = cleaned.indexOf("{");
            if (start != -1) return cleaned.substring(start);
        }
        
        return cleaned;
    }
}
