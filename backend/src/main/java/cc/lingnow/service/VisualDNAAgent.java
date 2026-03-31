package cc.lingnow.service;

import cc.lingnow.llm.LlmClient;
import cc.lingnow.model.ProjectManifest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;


/**
 * Visual DNA Agent - Synthesizes the unique aesthetic personality of a project.
 * It decodes the industry context into Tailwind CSS tokens and layout strategies.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisualDNAAgent {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    /**
     * Synthesize a unique design system for the given intent.
     */
    public void synthesize(ProjectManifest manifest) {
        log.info("[VisualDNA] Synthesizing aesthetic personality for: {} (Archetype: {})",
                manifest.getUserIntent(), manifest.getArchetype());

        String archetype = manifest.getArchetype() != null ? manifest.getArchetype() : "DASHBOARD";
        String uxStrategyContext = manifest.getUxStrategy() != null ?
                "UX STRATEGY (Determined by Intelligence Agent):\n" + manifest.getUxStrategy().toString() :
                "No specific research available. Use general professional standards.";


        String systemPrompt = """
                You are a World-Class UI/UX Strategist & PM.
                
                YOUR GOAL: Synthesize a unique "Visual DNA" that captures the Industry Soul and High-Density requirements.
                
                THINKING PROCESS (PM MODE):
                1. DENSITY RATIO: Define tokens for 'Discovery-Density' (compact margins, tight leading) or 'Reading-Density' (generous gutters).
                2. GRID VISUALS: Define border styles for separating 3-column layouts (e.g., thin hair-line borders vs subtle shadows).
                3. TYPOGRAPHY BONES: Choose font families and weights for large information hierarchies.
                
                STRATEGY CONTEXT: %s
                
                OUTPUT: Respond ONLY with a pure JSON object.
                
                JSON Schema:
                {
                    "bgClass": "Tailwind bg-xxx",
                    "cardClass": "Tailwind classes for content containers (padding, borders, bg)",
                    "primaryColor": "Tailwind color name",
                    "accentColor": "A contrasting accent color",
                    "shadowStrategy": "shadow-none | shadow-sm | shadow-xl",
                    "borderAccent": "border-slate-100 | border-transparent",
                    "glassIntensity": "backdrop-blur-md | none",
                    "fontFamily": "font-sans | font-serif",
                    "lineHeight": "leading-snug | leading-relaxed",
                    "letterSpacing": "tracking-tight | tracking-normal",
                    "serifBias": boolean,
                    "themeReasoning": "Reasoning based on UX Strategy"
                }
                """.formatted(uxStrategyContext);

        try {
            String userPrompt = String.format("Industry Intent: %s\nTarget Archetype: %s",
                    manifest.getUserIntent(), archetype);
            String response = llmClient.chat(systemPrompt, userPrompt);
            JsonNode root = objectMapper.readTree(cleanJsonResponse(response));

            if (manifest.getMetaData() == null) manifest.setMetaData(new HashMap<>());

            // Map NEW Generative Tokens to Manifest Metadata
            manifest.getMetaData().put("visual_bgClass", root.path("bgClass").asText("bg-slate-50"));
            manifest.getMetaData().put("visual_cardClass", root.path("cardClass").asText("bg-white shadow-sm rounded-2xl p-6"));
            manifest.getMetaData().put("visual_primaryColor", root.path("primaryColor").asText("indigo-600"));
            manifest.getMetaData().put("visual_accentColor", root.path("accentColor").asText("pink-500"));
            manifest.getMetaData().put("visual_shadowStrategy", root.path("shadowStrategy").asText("shadow-sm"));
            manifest.getMetaData().put("visual_borderAccent", root.path("borderAccent").asText("border-slate-200"));
            manifest.getMetaData().put("visual_glassIntensity", root.path("glassIntensity").asText("backdrop-blur-none"));
            manifest.getMetaData().put("visual_fontFamily", root.path("fontFamily").asText("font-sans"));
            manifest.getMetaData().put("visual_lineHeight", root.path("lineHeight").asText("leading-normal"));
            manifest.getMetaData().put("visual_letterSpacing", root.path("letterSpacing").asText("tracking-normal"));
            manifest.getMetaData().put("visual_serifBias", root.path("serifBias").asText("false"));
            manifest.getMetaData().put("visual_reasoning", root.path("themeReasoning").asText("Standard professional style."));

            log.info("[VisualDNA] Aesthetic Synthesis: {}", root.path("themeReasoning").asText());

        } catch (Exception e) {
            log.error("[VisualDNA] Synthesis failed, falling back to default.", e);
            // Fallback (Safe defaults)
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
