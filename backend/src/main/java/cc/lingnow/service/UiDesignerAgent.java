package cc.lingnow.service;

import cc.lingnow.llm.LlmClient;
import cc.lingnow.model.ProjectManifest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * UI Designer Agent - Responsible for creating high-fidelity prototypes (HTML/Tailwind).
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
        log.info("Designer is creating prototype for: {}", manifest.getUserIntent());
        
        try {
            StringBuilder planSummary = new StringBuilder();
            if (manifest.getFeatures() != null) {
                manifest.getFeatures().forEach(f -> planSummary.append("- ").append(f.getName()).append(": ").append(f.getDescription()).append("\n"));
            }
            if (manifest.getPages() != null) {
                manifest.getPages().forEach(p -> planSummary.append("- Page: ").append(p.getRoute()).append(" (").append(p.getDescription()).append(")\n"));
            }

            String systemPrompt = """
                You are a senior UI/UX Designer. Create a single-file high-fidelity INTERACTIVE HTML prototype.
                
                RULES:
                1. Output ONLY pure JSON: {"prototypeHtml": "..."}.
                2. Use CDNs:
                   - TailwindCSS: <script src="https://cdn.tailwindcss.com"></script>
                   - Alpine.js: <script defer src="https://unpkg.com/alpinejs@3.x.x/dist/cdn.min.js"></script>
                3. Design MUST be: Premium, Modern, and fully RESPONSIVE (works on mobile/tablet/desktop).
                4. INTERACTION: Implement simple UI logic using Alpine.js (e.g., opening modals, tab switching, hover effects, mock button clicks).
                5. Use high-quality placeholder images from Unsplash.
                """;
            
            String userPrompt = "Create a prototype for: " + manifest.getUserIntent() + "\n\nPlanned Features:\n" + planSummary.toString();
            
            String response = llmClient.chat(systemPrompt, userPrompt);
            JsonNode root = objectMapper.readTree(cleanJsonResponse(response));

            String prototype = root.path("prototypeHtml").asText();
            if (prototype == null || prototype.isEmpty()) {
                throw new RuntimeException("Designer produced empty prototype.");
            }
            
            manifest.setPrototypeHtml(prototype);
            log.info("Designer prototype generation complete. Length: {} chars.", prototype.length());

        } catch (Exception e) {
            log.error("Designer phase failed", e);
            throw new RuntimeException("Designer phase failed: " + e.getMessage());
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
        
        // Fallback to basic trimming
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf("\n");
            int lastBackticks = cleaned.lastIndexOf("```");
            if (firstNewline != -1 && lastBackticks > firstNewline) {
                cleaned = cleaned.substring(firstNewline, lastBackticks).trim();
            }
        }
        return cleaned;
    }
}
