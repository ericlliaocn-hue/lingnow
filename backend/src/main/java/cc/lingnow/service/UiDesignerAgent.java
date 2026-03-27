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

        String systemPrompt = "You are an expert UI/UX Designer. Create a premium, interactive HTML prototype based on requirements. "
                + "Use Tailwind CSS for styling. "
                + "- Use alpine.js for interactive logic (click, hover, toggle).\n"
                + "- CRITICAL FOR INSPECT MODE: Every visual component (div, button, input) MUST have a 'data-lingnow-id' attribute matching its functional name or feature id.\n"
                + "- Example: <button data-lingnow-id=\"submit-order-btn\" @click=\"...\">Submit</button>\n"
                + "- Ensure the design matches: " + (lang.equals("ZH") ? "Chinese commercial aesthetics (Clean, Modern, Dark/Glassmorphism)" : "Western modern design trends") + "\n"
                + "Respond with a single JSON object: {\"prototypeHtml\": \"...\"}. "
                + "IMPORTANT: All HTML must follow responsive design and include Desktop, Tablet, and Mobile layouts. ";
        
        // Language awareness for content
        if ("ZH".equalsIgnoreCase(lang)) {
            systemPrompt += "CRITICAL: The user interface content (texts, labels, descriptions) MUST BE IN CHINESE.";
        } else {
            systemPrompt += "The user interface content MUST BE IN ENGLISH.";
        }
        
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
        
        String systemPrompt = "You are an expert UI/UX Refinement Agent. You will receive an existing HTML prototype and modification instructions. "
                + "Your goal is to EVOLVE the existing design without breaking current styles or Alpine.js logic, unless requested. "
                + "Maintain consistency with the existing design language. "
                + "Respond ONLY with a single JSON object: {\"prototypeHtml\": \"... updated html ...\"}. ";

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
