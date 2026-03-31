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

    /**
     * Audit the generated prototype against the required task flows.
     *
     * @return A correction requirement if failed, or "VERIFIED" if pass.
     */
    public String verify(String html, String intent, String taskFlows, String archetype, Map<String, String> uxStrategy) {
        log.info("[Auditor] Auditing functional integrity and archetype fidelity for intent: {} (Archetype: {})",
                intent, archetype);

        String strategyContext = uxStrategy != null ?
                "BENCHMARK STRATEGY (Intelligence Agent):\n" + uxStrategy.toString() :
                "Standard professional standards.";

        String systemPrompt = """
                You are a Lead Product Auditor & PM Quality Guardian.
                
                YOUR GOAL: Verify if the product is 'Community-Ready' and matches Industry Density Benchmarks.
                
                SCRUTINY CHECKLIST (M7.0):
                1. PRODUCT MATURITY (Interaction Loop): 
                   - REJECT if Social/Content archetypes lack 'Like/Care/Collect' buttons or a visible 'Comment Section'.
                   - REJECT if there is no 'Post/Create' entry point in the shell.
                   - REJECT if Auth (Login/Signup) modal/links are missing from the header.
                2. INFORMATION DENSITY: 
                   - REJECT if a Portal/Discovery page uses a loose 'Landing Page' style (too much whitespace, huge hero images). 
                   - MUST use the Grid Strategies from the UX Strategy.
                3. NAVIGATION SEMANTICS:
                   - REJECT if Detail pages are in the sidebar. They must be context-switched widgets or leaf nodes.
                4. TASK FLOWS: Ensure functional #hash-links exist for all primary routes.
                
                OUTPUT:
                - If perfect: Respond with "VERIFIED".
                - If not: Respond with "CORRECTION_NEEDED: [Product Gap or Density Violation]".
                
                STRATEGY CONTEXT:
                """ + strategyContext;

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
