package cc.lingnow.service;

import cc.lingnow.llm.LlmClient;
import cc.lingnow.model.IndustryCollisionEntity;
import cc.lingnow.repository.IndustryCollisionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * UI Evolution Agent - The Self-Healing Heart of LingNow 3.x
 * Analyzes collisions and generates "Genetic Patches" for hard-coded logic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UiEvolutionAgent {

    private final IndustryCollisionRepository collisionRepository;
    private final LlmClient llmClient;

    /**
     * Study the collision logs and output a Genetic Patch recommendation.
     */
    public String studyAndEvolve() {
        log.info("[EvolutionAgent] Starting Genetic Study...");
        List<IndustryCollisionEntity> collisions = collisionRepository.findAllByOrderByHitCountDesc();

        if (collisions.isEmpty()) {
            return "NO_COLLISIONS_DETECTED: System is perfectly adjusted.";
        }

        StringBuilder report = new StringBuilder("# LINGNOW GENETIC PATCH REPORT\n\n");
        report.append("## Detected Collision Cases (Top 5)\n");

        for (int i = 0; i < Math.min(5, collisions.size()); i++) {
            IndustryCollisionEntity c = collisions.get(i);
            report.append(String.format("- **Intent:** `%s` (Hits: %d, Hash: %s)\n",
                    c.getIntentText(), c.getHitCount(), c.getIntentHash()));
        }

        report.append("\n## Genetic Patch (Regex Recommendations)\n");
        String prompt = "Review these unsupported app intents and provide a Java Regex snippet to enhance 'detectIndustry' logic.\n" +
                "Intents: " + report.toString();

        try {
            String regexPatch = llmClient.chat(prompt, "Generate Regex optimization.");
            report.append("```java\n").append(regexPatch).append("\n```\n");

            report.append("\n## Evolution Status\n");
            report.append("> [!TIP]\n");
            report.append("> Apply these regexes to `UiDesignerAgent.detectIndustry` to eliminate future downgrades for these categories.");

        } catch (Exception e) {
            log.error("[EvolutionAgent] Study failed", e);
            report.append("\n**LLM Study Failed: AI is still learning.**");
        }

        return report.toString();
    }
}
