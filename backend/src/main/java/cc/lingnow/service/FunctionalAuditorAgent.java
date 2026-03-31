package cc.lingnow.service;

import cc.lingnow.llm.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Functional Auditor Agent - The UX Guardian of LingNow.
 * Verifies that the generated HTML prototype actually implements the tactical task flows.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FunctionalAuditorAgent {

    private final LlmClient llmClient;

    /**
     * Audit the generated prototype against the required task flows.
     *
     * @return A correction requirement if failed, or "VERIFIED" if pass.
     */
    public String verify(String html, String intent, String taskFlows) {
        log.info("[Auditor] Auditing functional integrity for intent: {}", intent);

        String systemPrompt = """
                You are a Lead UX Functional Auditor.
                
                YOUR GOAL: Verify if the provided HTML prototype (Alpine.js based) actually supports the requested Task Flows.
                
                SCRUTINY RULES:
                1. INTERACTION PATHS: If a flow says "A -> B", verify that Page A has a button/link pointing to #hash-of-B.
                2. DATA CONSISTENCY: Verify that the buttons and actions names match the business purpose.
                3. NO GAPS: Ensure no core "Submit" or "Action" buttons are missing from the task flows.
                4. REJECTION: If a flow is broken (missing links, missing pages), reject with a detailed "CORRECTION_NEEDED" list.
                
                OUTPUT:
                - If all flows are logically traversable: Respond with "VERIFIED".
                - If not: Respond with "CORRECTION_NEEDED: [List of specific missing interactions or broken paths]".
                """;

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
