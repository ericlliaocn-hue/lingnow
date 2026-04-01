package cc.lingnow.service;

import cc.lingnow.llm.LlmClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;

    /**
     * Scan and repair common UI generation failures using both syntax checks and functional audit reports.
     */
    public String checkAndFix(String html, String userIntent, String mockData, String auditReport) {
        log.info("[AutoRepair] Scanning prototype for logical or syntax errors using Audit Report...");

        try {
            if (shouldUseTargetedRepair(html, auditReport)) {
                log.info("[AutoRepair] Using targeted repair mode for large prototype ({} chars).", html.length());
                return runTargetedRepair(html, userIntent, mockData, auditReport);
            }

            String response = llmClient.chat(buildFullRepairSystemPrompt(), buildFullRepairUserPrompt(html, userIntent, mockData, auditReport));
            return parseHtmlSnippet(response, html);
        } catch (Exception e) {
            log.error("[AutoRepair] Repair pass failed, returning original HTML", e);
            return html;
        }
    }

    private boolean shouldUseTargetedRepair(String html, String auditReport) {
        if (html == null) {
            return false;
        }
        if (html.length() >= 40000) {
            return true;
        }
        String normalizedAudit = auditReport == null ? "" : auditReport.toLowerCase();
        return normalizedAudit.contains("primary views rendered only")
                || normalizedAudit.contains("content-first homepage does not expose enough feed cards")
                || normalizedAudit.contains("community homepage");
    }

    private String runTargetedRepair(String html, String userIntent, String mockData, String auditReport) throws Exception {
        String response = llmClient.chat(buildTargetedRepairSystemPrompt(), buildTargetedRepairUserPrompt(html, userIntent, mockData, auditReport));
        JsonNode root = objectMapper.readTree(cleanStructuredResponse(response));

        String repaired = html;
        repaired = replaceFirstTagBlock(repaired, "header", root.path("header").asText(""));
        repaired = replaceFirstTagBlock(repaired, "main", root.path("main").asText(""));
        repaired = replaceDetailTemplate(repaired, root.path("detailTemplate").asText(""));

        return repaired;
    }

    private String buildFullRepairSystemPrompt() {
        return """
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
    }

    private String buildFullRepairUserPrompt(String html, String userIntent, String mockData, String auditReport) {
        return String.format("""
                User Intent: %s
                Audit Report: %s
                Target MockData Example: %s

                Current HTML:
                %s
                """, userIntent, auditReport, summarizeMockData(mockData), html);
    }

    private String buildTargetedRepairSystemPrompt() {
        return """
                You are a Senior Frontend Quality Engineer fixing a large Alpine.js prototype.
                
                GOAL:
                - Repair ONLY the provided fragments so the prototype passes the Audit Report.
                - Preserve Alpine.js state names and interaction loops.
                - Keep all ids / hashes / `selectedItem` bindings consistent.
                - Do not invent a new page architecture.
                
                OUTPUT RULES:
                - Respond with PURE JSON only.
                - Keys: "header", "main", "detailTemplate".
                - Each value must be a complete HTML fragment for that region.
                - If a region does not need changes, return the original fragment unchanged.
                - Do not include markdown fences.
                """;
    }

    private String buildTargetedRepairUserPrompt(String html, String userIntent, String mockData, String auditReport) {
        return String.format("""
                        User Intent: %s
                        Audit Report: %s
                        Target MockData Example: %s
                        
                        HEADER FRAGMENT:
                        %s
                        
                        MAIN FRAGMENT:
                        %s
                        
                        DETAIL TEMPLATE FRAGMENT:
                        %s
                        """,
                userIntent,
                auditReport,
                summarizeMockData(mockData),
                extractFirstTagBlock(html, "header"),
                extractFirstTagBlock(html, "main"),
                extractDetailTemplate(html));
    }

    private String summarizeMockData(String mockData) {
        if (mockData == null || mockData.isBlank()) {
            return "[]";
        }
        return mockData.length() > 600 ? mockData.substring(0, 600) + "..." : mockData;
    }

    private String cleanStructuredResponse(String response) {
        if (response == null) {
            return "{}";
        }
        String cleaned = response.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned.trim();
    }

    private String extractFirstTagBlock(String html, String tagName) {
        if (html == null) {
            return "";
        }
        int start = html.indexOf("<" + tagName);
        if (start == -1) {
            return "";
        }
        int openEnd = html.indexOf(">", start);
        int end = html.indexOf("</" + tagName + ">", openEnd);
        if (openEnd == -1 || end == -1) {
            return "";
        }
        return html.substring(start, end + tagName.length() + 3);
    }

    private String replaceFirstTagBlock(String html, String tagName, String replacement) {
        if (replacement == null || replacement.isBlank()) {
            return html;
        }
        String current = extractFirstTagBlock(html, tagName);
        if (current.isBlank()) {
            return html;
        }
        return html.replace(current, replacement.trim());
    }

    private String extractDetailTemplate(String html) {
        if (html == null) {
            return "";
        }
        String marker = "<template x-if=\"selectedItem\">";
        int start = html.indexOf(marker);
        if (start == -1) {
            return "";
        }
        int end = html.indexOf("</template>", start);
        if (end == -1) {
            return "";
        }
        return html.substring(start, end + "</template>".length());
    }

    private String replaceDetailTemplate(String html, String replacement) {
        if (replacement == null || replacement.isBlank()) {
            return html;
        }
        String current = extractDetailTemplate(html);
        if (current.isBlank()) {
            return html;
        }
        return html.replace(current, replacement.trim());
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
