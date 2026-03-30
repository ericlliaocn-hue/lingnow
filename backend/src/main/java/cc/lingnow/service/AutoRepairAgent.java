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
     * Scan and repair common UI generation failures.
     */
    public String checkAndFix(String html, String userIntent, String mockData) {
        log.info("[AutoRepair] Scanning prototype for logical or syntax errors...");

        String systemPrompt = """
                You are a Senior Frontend Quality Engineer.
                
                YOUR GOAL: Fix any broken Alpine.js logic or malformed HTML in the provided prototype.
                
                CHECKLIST:
                1. MISSING MOCKDATA: Ensure any usage of `mockData` is correctly bound to the global scope.
                2. ALPINE ATTRIBUTES: Fix unclosed quotes in `x-data`, `x-show`, or `@click`.
                3. CONTAINER BREAKS: Ensure all <div> tags are balanced.
                4. SCRIPT ERRORS: Look for `ReferenceError` candidates in the Alpine definitions.
                5. SECURITY ATTRIBUTES: REMOVE all `integrity` and `crossorigin` attributes from `<script>` and `<link>` tags.
                6. ROUTING AUDIT: Verify that every `href="#id"` in the sidebar has a corresponding `<div x-show="hash === '#id'">`. If a container is missing or empty, synthesize its content based on the node name.
                
                OUTPUT: Respond ONLY with the repaired RAW HTML wrapped in ```html markers.
                """;

        String userPrompt = String.format("""
                User Intent: %s
                Target MockData Example: %s
                
                Current HTML:
                %s
                """, userIntent, (mockData != null && mockData.length() > 50 ? mockData.substring(0, 50) + "..." : mockData), html);

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
