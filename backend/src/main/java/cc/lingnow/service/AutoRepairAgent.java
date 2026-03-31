package cc.lingnow.service;

import cc.lingnow.llm.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Auto Repair & Visual Auditor Agent - The Dual-Pass Quality Layer of LingNow.
 * 1. Logic Pass: Scans for syntax errors or broken Alpine bindings.
 * 2. Visual Pass: Ensures the "Professional White" DNA and high-density layout.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoRepairAgent {

    private final LlmClient llmClient;

    /**
     * Scan and repair common UI generation failures using both syntax checks and functional audit reports.
     */
    public String checkAndFix(String html, String userIntent, String mockData, String auditReport) {
        log.info("[AutoRepair] Scanning prototype for logical or syntax errors using Audit Report...");

        String systemPrompt = """
                You are a Senior Frontend Quality Engineer & UX Specialist.
                
                YOUR GOAL: Fix any broken Alpine.js logic, malformed HTML, OR functional logic gaps identified in the Audit Report.
                
                FIX INSTRUCTIONS:
                1. LOGIC AUDIT REPAIR: Read the provided `Audit Report`. If it says a flow is broken (e.g. missing links, wrong hashes), YOU MUST FIX the HTML `href="#id"` and `@click` attributes to restore the journey.
                2. ROUTING ALIGNMENT: Ensure the Sidebar links match the Content IDs precisely.
                3. ALPINE ATTRIBUTES: Fix unclosed quotes in `x-data`, `x-show`, or `@click`.
                4. CONTAINER BREAKS: Ensure all <div> tags are balanced.
                5. SECURITY ATTRIBUTES: REMOVE all `integrity` and `crossorigin` attributes from `<script>` and `<link>` tags.
                6. FIELD CONSISTENCY: Verify that Alpine.js data bindings (e.g. `item.score`) match the keys in the provided `Target MockData Example`. 
                
                OUTPUT: Respond ONLY with the repaired RAW HTML wrapped in ```html markers.
                """;

        String userPrompt = String.format("""
                User Intent: %s
                Audit Report: %s
                Target MockData Example: %s
                
                Current HTML:
                %s
                """, userIntent, auditReport, (mockData != null && mockData.length() > 50 ? mockData.substring(0, 50) + "..." : mockData), html);

        try {
            String response = llmClient.chat(systemPrompt, userPrompt);
            return parseHtmlSnippet(response, html);
        } catch (Exception e) {
            log.error("[AutoRepair] Repair pass failed, returning original HTML", e);
            return html;
        }
    }

    private String parseHtmlSnippet(String response, String fallback) {
        if (response == null) return fallback;
        try {
            int startIndex = response.indexOf("```html");
            if (startIndex != -1) {
                startIndex += 7;
                int endIndex = response.lastIndexOf("```");
                if (endIndex != -1 && endIndex > startIndex) {
                    return response.substring(startIndex, endIndex).trim();
                }
            }
            if (response.trim().startsWith("<")) return response.trim();
            return fallback;
        } catch (Exception e) {
            return fallback;
        }
    }
}
