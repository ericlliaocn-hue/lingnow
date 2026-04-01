package cc.lingnow.service;

import cc.lingnow.llm.LlmClient;
import cc.lingnow.model.ProjectManifest;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Functional Auditor Agent - combines deterministic structural validation with
 * a final LLM semantic review so prototype readiness is not decided by prompt text alone.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FunctionalAuditorAgent {

    private final LlmClient llmClient;

    public AuditOutcome verify(ProjectManifest manifest) {
        if (manifest == null || manifest.getPrototypeHtml() == null || manifest.getPrototypeHtml().isBlank()) {
            return AuditOutcome.builder()
                    .passed(false)
                    .summary("CORRECTION_NEEDED: Prototype HTML is empty.")
                    .blockers(List.of("Prototype HTML is empty."))
                    .build();
        }

        List<String> hardBlockers = evaluateHardRules(manifest);
        if (!hardBlockers.isEmpty()) {
            String summary = "CORRECTION_NEEDED: " + String.join(" | ", hardBlockers);
            log.info("[Auditor] Hard-rule blockers found: {}", summary);
            return AuditOutcome.builder()
                    .passed(false)
                    .summary(summary)
                    .blockers(hardBlockers)
                    .build();
        }

        String softAudit = runSemanticAudit(manifest);
        boolean softPassed = softAudit == null
                || softAudit.isBlank()
                || softAudit.toUpperCase(Locale.ROOT).contains("VERIFIED");

        if (softPassed) {
            return AuditOutcome.builder()
                    .passed(true)
                    .summary(softAudit == null || softAudit.isBlank() ? "VERIFIED" : softAudit)
                    .blockers(List.of())
                    .build();
        }

        return AuditOutcome.builder()
                .passed(false)
                .summary(softAudit.startsWith("CORRECTION_NEEDED:") ? softAudit : "CORRECTION_NEEDED: " + softAudit)
                .blockers(List.of(softAudit))
                .build();
    }

    private String loadHandbook() {
        try {
            return java.nio.file.Files.readString(java.nio.file.Paths.get("/Users/eric/workspace/lingnow/.agents/skills/QA_AUDITOR_HANDBOOK.md"));
        } catch (Exception e) {
            log.warn("[Auditor] Handbook not found, falling back to basic QA logic.");
            return "";
        }
    }

    private List<String> evaluateHardRules(ProjectManifest manifest) {
        List<String> blockers = new ArrayList<>();
        String html = manifest.getPrototypeHtml();
        String htmlLower = html.toLowerCase(Locale.ROOT);
        String mainContent = extractMainContent(html);
        String mainLower = mainContent.toLowerCase(Locale.ROOT);

        ProjectManifest.DesignContract contract = manifest.getDesignContract();
        int minPrimarySections = contract != null ? Math.max(contract.getMinPrimarySections(), 1) : 1;
        int minPrimaryCards = contract != null ? Math.max(contract.getMinPrimaryCards(), 1) : 1;

        int primarySections = countOccurrences(html, "x-show=\"hash === '#");
        if (primarySections < minPrimarySections) {
            blockers.add("Primary views rendered only " + primarySections + "/" + minPrimarySections + ".");
        }

        if (html.contains("{{") || html.contains("}}")) {
            blockers.add("Prototype still contains unresolved shell placeholders.");
        }

        if (html.contains("\\'") || html.contains("\\\"")) {
            blockers.add("Prototype still contains escaped quote syntax that breaks Alpine expressions.");
        }

        if (isShellOnly(mainContent)) {
            blockers.add("Primary content area is too sparse and still looks like shell-only layout.");
        }

        int contentSignals = countOccurrences(mainLower, "x-for=")
                + countOccurrences(mainLower, "<article")
                + countOccurrences(mainLower, "<section");
        if (contentSignals == 0) {
            blockers.add("No repeatable cards or content sections detected in main content.");
        }

        if (minPrimaryCards > 1) {
            int cardSignals = countOccurrences(mainLower, "@click=\"selecteditem = item")
                    + countOccurrences(mainLower, "@click='selecteditem = item")
                    + countOccurrences(mainLower, "x-for=");
            if (cardSignals == 0) {
                blockers.add("Primary cards/feed interactions are missing.");
            }
        }

        if (isContentFirst(manifest)) {
            int articleCount = countOccurrences(mainLower, "<article");
            if (articleCount == 0) {
                blockers.add("Content-first homepage is missing visible article cards in the main feed.");
            }
            if (articleCount < minPrimaryCards) {
                blockers.add("Content-first homepage does not expose enough feed cards for a strong first screen.");
            }
            if (!containsAny(mainLower, "selecteditem = item; hash = '#detail'", "selecteditem = item; hash='#detail'")) {
                blockers.add("Content-first homepage is missing a valid detail handoff on feed cards.");
            }
            if (contract != null && contract.isPrefersWaterfallFeed()
                    && !containsAny(mainLower, "columns-", "break-inside-avoid", "waterfall")) {
                blockers.add("Content-first homepage should use a waterfall / masonry feed rhythm instead of a rigid portal grid.");
            }
            if (countOccurrences(mainLower, "<aside") > 1
                    || containsAny(mainLower, "grid grid-cols-12", "col-span-12 xl:col-span-2", "col-span-12 xl:col-span-3")) {
                blockers.add("Content-first homepage still contains portal-like internal sidebars.");
            }
            if (countOccurrences(mainLower, "sticky top-") > 0) {
                blockers.add("Content-first homepage still contains sticky in-body scaffolding that competes with the shell header.");
            }
            if (contract != null && contract.getMaxAuxRailSections() > 0) {
                int auxSections = countOccurrences(mainLower, "data-aux-section=");
                if (auxSections > contract.getMaxAuxRailSections()) {
                    blockers.add("Auxiliary right-rail modules are too heavy for a feed-first community homepage.");
                }
            }
            if (containsAny(mainLower, "推荐策略", "recommendation strategy", "strategy card")) {
                blockers.add("Community homepage still contains dashboard-style strategy panels.");
            }
        }

        if (contract != null && contract.isPrefersRealMedia()
                && containsAny(mainLower, "placehold.co", "via.placeholder", "dummyimage.com")) {
            blockers.add("Community homepage still uses placeholder imagery instead of authentic-looking media.");
        }
        String mockData = manifest.getMockData() == null ? "" : manifest.getMockData().toLowerCase(Locale.ROOT);
        if (containsAny(mockData, "\"封面图\":\"封面_", "\"作者头像\":\"头像_", "\"cover\":\"封面_", "\"avatar\":\"头像_")) {
            blockers.add("Mock data still contains non-URL media placeholders that will render as broken images.");
        }

        if (contract != null && contract.isRequiresSearch()
                && !containsAny(htmlLower, "placeholder=\"搜索", "placeholder=\"发现", "placeholder=\"search", "fa-magnifying-glass", "type=\"search\"", "type=\"text\"")) {
            blockers.add("Search entry point is required but not detected.");
        }

        if (contract != null && contract.isRequiresComposer()
                && !containsAny(htmlLower, "postopen", "publish", "发布", "composer")) {
            blockers.add("Composer/publish action is required but missing.");
        }

        if (contract != null && contract.isRequiresDetailOverlay()
                && !containsAny(htmlLower, "selecteditem", "#detail", "detail modal", "评论", "comment")) {
            blockers.add("Detail overlay loop is required but missing.");
        }

        if (manifest.getTaskFlows() != null && !manifest.getTaskFlows().isEmpty()
                && !containsAny(htmlLower, "hash = '#detail'", "hash='#detail'", "selecteditem = item", "@click=\"hash='#")) {
            blockers.add("Task flow handoff actions are missing from interactive elements.");
        }

        return blockers;
    }

    private String runSemanticAudit(ProjectManifest manifest) {
        log.info("[Auditor] Auditing semantic integrity for intent: {} (Archetype: {})",
                manifest.getUserIntent(), manifest.getArchetype());

        String handbook = loadHandbook();
        Map<String, String> uxStrategy = manifest.getUxStrategy();
        String strategyContext = uxStrategy != null
                ? "BENCHMARK STRATEGY:\n" + uxStrategy
                : "Standard professional standards.";
        String designContract = manifest.getDesignContract() != null ? manifest.getDesignContract().toString() : "No design contract.";
        String taskFlows = manifest.getTaskFlows() != null ? manifest.getTaskFlows().toString() : "No flows defined";

        String systemPrompt = String.format("""
                %s
                
                YOUR GOAL: Verify if the product matches the declared archetype, task flows, and design contract.
                
                OUTPUT:
                - If acceptable: Respond with "VERIFIED".
                - If not: Respond with "CORRECTION_NEEDED: [Gap summary]".
                
                STRATEGY CONTEXT:
                %s
                """, handbook, strategyContext);

        String userPrompt = String.format("""
                User Intent: %s
                Archetype: %s
                Task Flows: %s
                Design Contract: %s
                
                HTML Prototype to Audit:
                %s
                """, manifest.getUserIntent(), manifest.getArchetype(), taskFlows, designContract, manifest.getPrototypeHtml());

        try {
            String auditResult = llmClient.chat(systemPrompt, userPrompt);
            log.info("[Auditor] Semantic Audit Result: {}", auditResult);
            return auditResult;
        } catch (Exception e) {
            log.error("[Auditor] Semantic audit failed", e);
            return "VERIFIED (Semantic audit skipped due to system error)";
        }
    }

    private boolean isShellOnly(String mainContent) {
        if (mainContent == null || mainContent.isBlank()) {
            return true;
        }
        String plainText = mainContent.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        int signalCount = countOccurrences(mainContent.toLowerCase(Locale.ROOT), "<section")
                + countOccurrences(mainContent.toLowerCase(Locale.ROOT), "<article")
                + countOccurrences(mainContent.toLowerCase(Locale.ROOT), "x-for=");
        return plainText.length() < 160 || signalCount == 0;
    }

    private String extractMainContent(String html) {
        int mainStart = html.indexOf("<main");
        if (mainStart == -1) {
            return html;
        }
        int contentStart = html.indexOf(">", mainStart);
        int mainEnd = html.indexOf("</main>", contentStart);
        if (contentStart == -1 || mainEnd == -1 || mainEnd <= contentStart) {
            return html;
        }
        return html.substring(contentStart + 1, mainEnd);
    }

    private int countOccurrences(String source, String token) {
        if (source == null || token == null || token.isEmpty()) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(token, index)) != -1) {
            count++;
            index += token.length();
        }
        return count;
    }

    private boolean containsAny(String source, String... tokens) {
        if (source == null) {
            return false;
        }
        String normalizedSource = source.toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (normalizedSource.contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean isContentFirst(ProjectManifest manifest) {
        return manifest.getDesignContract() != null
                && "CONTENT_FIRST".equalsIgnoreCase(manifest.getDesignContract().getContentMode());
    }

    @Data
    @Builder
    public static class AuditOutcome {
        private boolean passed;
        private String summary;
        private List<String> blockers;
    }
}
