package cc.lingnow.service;

import cc.lingnow.llm.LlmClient;
import cc.lingnow.model.ProjectManifest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Data Engineer Agent - Responsible for generating high-fidelity,
 * context-aware mock data ecosystems for prototypes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataEngineerAgent {

    private final LlmClient llmClient;

    /**
     * Generate a robust JSON dataset based on the architectural plan.
     */
    public void generateData(ProjectManifest manifest) {
        log.info("Data Engineer is synthesizing mock records for: {}", manifest.getUserIntent());

        String lang = manifest.getMetaData() != null ? manifest.getMetaData().getOrDefault("lang", "EN") : "EN";
        String langInstruction = "ZH".equalsIgnoreCase(lang)
                ? "CRITICAL: USE CHINESE for all data values (names, categories, statuses)."
                : "Use realistic English data.";

        String systemPrompt = String.format("""
                You are a World-Class Data Engineer specializing in SaaS data modeling.
                
                YOUR GOAL: Generate a high-fidelity, realistic JSON dataset that brings a prototype to life.
                
                RULES:
                1. VOLUME: Generate 15-20 diverse records.
                2. FIDELITY: Data must be context-aware (e.g., if it's a fitness app, muscle groups and heart rates must be medically plausible).
                3. HIERARCHY: If the features include 'Orders' and 'Customers', ensure they are cross-referenced or part of a rich object.
                4. FORMAT: Output ONLY a raw JSON array of objects. No markdown markers.
                5. LANGUAGE: %s
                """, langInstruction);

        String userPrompt = String.format("""
                        Requirement: %s
                        Architectural Plan (Mindmap): %s
                        Planned Pages & Field Requirements: %s
                        
                        Please output a robust JSON array of objects representing the primary business entity. 
                        CRITICAL: The objects must include ALL high-fidelity metadata fields suggested in the 'Planned Pages' section to ensure the UI components can find and render the data.
                        """,
                manifest.getUserIntent(),
                manifest.getMindMap(),
                manifest.getPages() != null ? manifest.getPages().toString() : "N/A"
        );

        try {
            String response = llmClient.chat(systemPrompt, userPrompt);
            String cleanedJson = cleanJsonResponse(response);
            manifest.setMockData(cleanedJson);
            log.info("Data synthesis complete ({} chars).", cleanedJson.length());
        } catch (Exception e) {
            log.error("Data synthesis failed", e);
            // Fallback to a safe empty array
            manifest.setMockData("[]");
        }
    }

    private String cleanJsonResponse(String response) {
        if (response == null) return "[]";
        String cleaned = response.trim();
        int firstBracket = cleaned.indexOf("[");
        int lastBracket = cleaned.lastIndexOf("]");
        if (firstBracket != -1 && lastBracket != -1 && lastBracket >= firstBracket) {
            return cleaned.substring(firstBracket, lastBracket + 1);
        }
        return "[]";
    }
}
