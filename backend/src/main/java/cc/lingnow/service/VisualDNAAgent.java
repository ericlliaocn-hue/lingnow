package cc.lingnow.service;

import cc.lingnow.llm.LlmClient;
import cc.lingnow.model.ProjectManifest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
// import java.util.Map; (Deleted)

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
        log.info("[VisualDNA] Synthesizing aesthetic personality for: {}", manifest.getUserIntent());

        String systemPrompt = """
                You are a World-Class UI/UX Strategist.
                
                YOUR GOAL: Devise a unique "Visual DNA" for a SaaS application based on its industry context.
                
                RULES:
                1. AVOID TEMPLATES: Do not use `bg-slate-50` for everything.
                2. SENSE OF PLACE:
                   - If it's a NIGHT/BAR/CRYPTO app, use DARK MODE (`bg-slate-950`).
                   - If it's a LUXURY/MINIMAL app, use ULTRA-WHITE (`bg-white`) with minimal borders.
                   - If it's a NATURE/HEALTH app, use organic tones (`bg-stone-50`, `bg-emerald-50`).
                3. ROUNDNESS: Adjust `rounded-none` (Industrial/Brute) to `rounded-3xl` (Playful/Consumer).
                4. DENSITY: Map high-precision apps to high-density layouts.
                
                OUTPUT: Respond ONLY with a pure JSON object.
                
                JSON Schema:
                {
                    "bgClass": "tailwind background class",
                    "cardClass": "tailwind card classes (bg, border, shadow)",
                    "primaryColor": "tailwind color name (e.g. indigo-600)",
                    "buttonClass": "tailwind button shape/style",
                    "fontFamily": "font-sans | font-mono | font-serif",
                    "themeReasoning": "Briefly why this style was chosen"
                }
                """;

        try {
            String response = llmClient.chat(systemPrompt, "Industry Intent: " + manifest.getUserIntent());
            JsonNode root = objectMapper.readTree(cleanJsonResponse(response));

            if (manifest.getMetaData() == null) manifest.setMetaData(new HashMap<>());

            // Map JSON results to Manifest Metadata
            manifest.getMetaData().put("visual_bgClass", root.path("bgClass").asText("bg-slate-50"));
            manifest.getMetaData().put("visual_cardClass", root.path("cardClass").asText("bg-white border border-slate-200 shadow-sm rounded-2xl p-6"));
            manifest.getMetaData().put("visual_primaryColor", root.path("primaryColor").asText("indigo-600"));
            manifest.getMetaData().put("visual_buttonClass", root.path("buttonClass").asText("rounded-lg font-medium transition-all"));
            manifest.getMetaData().put("visual_fontFamily", root.path("fontFamily").asText("font-sans"));
            manifest.getMetaData().put("visual_reasoning", root.path("themeReasoning").asText("Standard professional style."));

            log.info("[VisualDNA] Style Synthesized: {}", root.path("themeReasoning").asText());

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
