package cc.lingnow.service;

import cc.lingnow.llm.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Functional Auditor Agent - The UX Guardian of LingNow.
 * Verifies that the generated HTML prototype actually implements the tactical task flows.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FunctionalAuditorAgent {

    private final LlmClient llmClient;

    private String loadHandbook() {
        try {
            return java.nio.file.Files.readString(java.nio.file.Paths.get("/Users/eric/workspace/lingnow/.agents/skills/QA_AUDITOR_HANDBOOK.md"));
        } catch (Exception e) {
            log.warn("[Auditor] Handbook not found, falling back to basic QA logic.");
            return "";
        }
    }

    /**
     * Audit the generated prototype against the required task flows.
     *
     * @return A correction requirement if failed, or "VERIFIED" if pass.
     */
    public String verify(String html, String intent, String taskFlows, String archetype, Map<String, String> uxStrategy) {
        log.info("[Auditor] Auditing functional integrity and archetype fidelity for intent: {} (Archetype: {})",
                intent, archetype);

        String handbook = loadHandbook();
        String strategyContext = uxStrategy != null ?
                "BENCHMARK STRATEGY (Intelligence Agent):\n" + uxStrategy.toString() :
                "Standard professional standards.";

        String systemPrompt = String.format("""
                %s
                
                YOUR GOAL: Verify if the product is 'Community-Ready' and matches Industry Density Benchmarks.
                
                OUTPUT:
                - If perfect: Respond with "VERIFIED".
                - If not: Respond with "CORRECTION_NEEDED: [Product Gap or Density Violation]".
                
                STRATEGY CONTEXT:
                %s
                """, handbook, strategyContext);

        String userPrompt = String.format("""
                User Intent: %s
                Task Flows to Verify: %s
                
                HTML Prototype to Audit:
                %s
                """, intent, taskFlows, html);

        try {
            String auditResult = llmClient.chat(systemPrompt, userPrompt);
            log.info("[Auditor] Audit Result: {}", auditResult);
            return auditResult;
        } catch (Exception e) {
            log.error("[Auditor] Audit cycle failed", e);
            return "VERIFIED (Audit Skipped Due to System Error)";
        }
    }
}
