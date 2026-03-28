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

        String systemPrompt = "You are a World-Class UI/UX Architect. Your goal is to deliver a bespoke, industrial-grade application prototype.\n"
                + "RULES:\n"
                + "1. BLUEPRINT FIDELITY: You will receive a Mermaid 'mindMap'. You MUST implement a functional view or action for EVERY node in that mindmap. No missing buttons or dead links.\n"
                + "2. DATA-DRIVEN DESIGN: You will receive 'mockData' (JSON). You MUST use this data to populate all lists, feeds, and profiles. Avoid generic 'Lorem Ipsum' or 'Title 1'.\n"
                + "3. HASH-BASED ROUTING: Transition between views (Home, Login, Profile) using 'window.location.hash' and Alpine.js. Ensure 'back' and 'forward' browser buttons work naturally.\n"
                + "4. ANTI-TEMPLATE AESTHETICS: Strictly FORBID card-only grids for main feeds. Use high-density 'Linear List Flows' with 1px dividers, tight padding, and premium typography (Inter/System fonts). Benchmark: Linux.do, Juejin, CSDN.\n"
                + "5. 100% CLICKABLE: Every interactive component needs a 'data-lingnow-id' and visual feedback.\n"
                + "6. LANGUAGE: All content MUST be in " + (lang.equals("ZH") ? "CHINESE" : "ENGLISH") + ".\n"
                + "7. OUTPUT: Respond ONLY with a single JSON object: {\"prototypeHtml\": \"...\"}.";

        String userPrompt = String.format("User Intent: %s\nPlanned Mindmap: %s\nMock Data: %s\nPlanned Pages: %s",
                manifest.getUserIntent(), manifest.getMindMap(), manifest.getMockData(), manifest.getPages());
                
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

        String systemPrompt = "You are an expert UI/UX Refinement Agent. You will receive an existing SPA prototype (with Hash-based Alpine.js logic) and modification instructions.\n"
                + "YOUR GOAL: Evolve the existing design without breaking the 'mindmap' node coverage or 'mockData' consistency.\n"
                + "1. MAINTAIN FLOW: Ensure navigation (Hash changes) and state management still works perfectly.\n"
                + "2. CONSISTENT AESTHETICS: Keep the high-density list flow and technical blog aesthetics (Linux.do/Juejin style).\n"
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
