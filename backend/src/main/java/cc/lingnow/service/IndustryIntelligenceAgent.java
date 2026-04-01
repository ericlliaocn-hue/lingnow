package cc.lingnow.service;

import cc.lingnow.llm.LlmClient;
import cc.lingnow.model.ProjectManifest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Industry Intelligence Agent - The "Brain" of LingNow 6.1.
 * Responsible for autonomous industry research and UX strategy synthesis.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IndustryIntelligenceAgent {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    private String loadHandbook() {
        try {
            return java.nio.file.Files.readString(java.nio.file.Paths.get("/Users/eric/workspace/lingnow/.agents/skills/RESEARCHER_HANDBOOK.md"));
        } catch (Exception e) {
            log.warn("[Intelligence] Handbook not found, falling back to basic intelligence logic.");
            return "";
        }
    }

    /**
     * Synthesize a UX Strategy based on the user's intent by researching industry benchmarks.
     */
    public void synthesizeStrategy(ProjectManifest manifest) {
        log.info("[Intelligence] Analyzing industry strategic benchmarks for: {}", manifest.getUserIntent());

        String handbook = loadHandbook();
        String systemPrompt = String.format("""
                %s
                
                GOAL: Analyze user intent and synthesize a JSON UX Strategy.
                
                OUTPUT ONLY PURE JSON:
                {
                    "industry_soul": "string",
                    "benchmarks": ["string"],
                    "shell_pattern": "PERSISTENT_TOP_DYNAMIC_SIDEBAR | MINIMAL_HEADER_DRAWER_ONLY | SIDEBAR_PRIMARY_NAV",
                    "grid_specs": { "max_width": "string", "columns": "string" },
                    "essential_modules": ["string"],
                    "visual_density": "LOW | NORMAL | HIGH",
                    "strategy_reasoning": "string"
                }
                """, handbook);

        try {
            String userPrompt = "User Intent: " + manifest.getUserIntent();
            String response = llmClient.chat(systemPrompt, userPrompt);
            JsonNode root = objectMapper.readTree(cleanJsonResponse(response));

            Map<String, String> strategy = new HashMap<>();
            strategy.put("industry_soul", root.path("industry_soul").asText());
            strategy.put("benchmarks", root.path("benchmarks").toString());
            strategy.put("shell_pattern", root.path("shell_pattern").asText("SIDEBAR_PRIMARY_NAV"));
            strategy.put("grid_specs", root.path("grid_specs").toString());
            strategy.put("essential_modules", root.path("essential_modules").toString());
            strategy.put("visual_density", root.path("visual_density").asText("NORMAL"));
            strategy.put("strategy_reasoning", root.path("strategy_reasoning").asText());

            manifest.setUxStrategy(strategy);
            log.info("[Intelligence] Strategic Synthesis Complete: {} (Pattern: {})",
                    strategy.get("industry_soul"), strategy.get("shell_pattern"));

        } catch (Exception e) {
            log.error("[Intelligence] Strategic synthesis failed", e);
            // Safe fallback
            manifest.setUxStrategy(Map.of("nav_pattern", "SIDEBAR", "layout_strategy", "Grid-based"));
        }
    }

    private String cleanJsonResponse(String response) {
        if (response == null) return "{}";
        String cleaned = response.trim();
        int firstBrace = cleaned.indexOf("{");
        int lastBrace = cleaned.lastIndexOf("}");
        if (firstBrace != -1 && lastBrace != -1 && lastBrace >= firstBrace) {
            return cleaned.substring(firstBrace, lastBrace + 1);
        }
        return "{}";
    }
}
