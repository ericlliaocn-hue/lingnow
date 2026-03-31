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

    /**
     * Synthesize a UX Strategy based on the user's intent by researching industry benchmarks.
     */
    public void synthesizeStrategy(ProjectManifest manifest) {
        log.info("[Intelligence] Analyzing industry strategic benchmarks for: {}", manifest.getUserIntent());

        String systemPrompt = """
                You are a World-Class Industry Intelligence Agent. 
                
                YOUR GOAL: Analyze the user's intent and synthesize a "UX Strategy" based on industry-leading benchmarks (e.g., Tesla for Automotive, Medium for Blogs, Stripe for FinTech).
                
                THINKING PROCESS (PM MODE):
                1. Identify the 'North Star' products and their CORE PRODUCT ECOSYSTEM (Implicit workflows).
                2. Select a SHELL PATTERN: 
                   - PERSISTENT_TOP_DYNAMIC_SIDEBAR (Tech Communities like Juejin)
                   - MINIMAL_HEADER_DRAWER_ONLY (Luxury/Automotive like Porsche)
                   - SIDEBAR_PRIMARY_NAV (SaaS Dashboards like Stripe)
                3. Define ATOMIC GRID SPECS (Pixel-perfect width benchmarks).
                4. Define ESSENTIAL MODULES (Implicit 'must-have' features: Social Interaction, UGC Posting, Notifications, Auth).
                
                OUTPUT: Respond ONLY with a pure JSON object.
                
                JSON Schema:
                {
                    "industry_soul": "Description of the atmospheric goal",
                    "benchmarks": ["Site A", "Site B"],
                    "shell_pattern": "PERSISTENT_TOP_DYNAMIC_SIDEBAR | MINIMAL_HEADER_DRAWER_ONLY | SIDEBAR_PRIMARY_NAV",
                    "grid_specs": { "max_width": "1200px", "columns": "180:700:300" },
                    "essential_modules": ["PostBtn", "LikeSystem", "NotificationBell", "AuthOverlay", "Search", "TOC"],
                    "visual_density": "LOW | NORMAL | HIGH",
                    "strategy_reasoning": "Reasoning focusing on information architecture and user flow"
                }
                """;

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
