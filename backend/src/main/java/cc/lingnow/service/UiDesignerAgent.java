package cc.lingnow.service;

import cc.lingnow.llm.LlmClient;
import cc.lingnow.model.ProjectManifest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;


/**
 * UI Designer Agent - Responsible for creating and refining high-fidelity prototypes (HTML/Tailwind).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UiDesignerAgent {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    private String getDynamicDNA(ProjectManifest manifest) {
        var meta = manifest.getMetaData();
        if (meta == null) return "";
        
        return String.format("""
                        - Background: %s
                        - Cards: %s
                        - Primary: %s
                        - Accent: %s
                        - Shadow Style: %s
                        - Border Style: %s
                        - Glass Effect: %s
                        - Font: %s
                        - Line Height: %s
                        - Letter Spacing: %s
                        - Serif Bias (High Fidelity): %s
                        - Reasoning: %s
                        """,
                meta.getOrDefault("visual_bgClass", "bg-slate-50"),
                meta.getOrDefault("visual_cardClass", "bg-white shadow-sm rounded-2xl p-6"),
                meta.getOrDefault("visual_primaryColor", "indigo-600"),
                meta.getOrDefault("visual_accentColor", "pink-500"),
                meta.getOrDefault("visual_shadowStrategy", "shadow-sm"),
                meta.getOrDefault("visual_borderAccent", "border-slate-200"),
                meta.getOrDefault("visual_glassIntensity", "backdrop-blur-none"),
                meta.getOrDefault("visual_fontFamily", "font-sans"),
                meta.getOrDefault("visual_lineHeight", "leading-normal"),
                meta.getOrDefault("visual_letterSpacing", "tracking-normal"),
                meta.getOrDefault("visual_serifBias", "false"),
                meta.getOrDefault("visual_reasoning", "Standard professional style."));
    }

    /**
     * Generate a high-fidelity HTML prototype based on the manifest using a Multi-Step Pipeline.
     */
    public void design(ProjectManifest manifest) {
        log.info("Designer is starting multi-step pipeline for: {}", manifest.getUserIntent());
        String lang = manifest.getMetaData() != null ? manifest.getMetaData().getOrDefault("lang", "EN") : "EN";

        try {
            // STEP 0: Pre-calculate Routes from Mindmap
            List<Route> routes = extractRoutes(manifest);
            if (routes.isEmpty()) {
                throw new RuntimeException("No valid feature nodes found in mindmap. Cannot design UI.");
            }

            // Separate PRIMARY (sidebar) routes from OVERLAY (modal) routes
            List<Route> primaryRoutes = new ArrayList<>();
            List<Route> overlayRoutes = new ArrayList<>();
            for (Route r : routes) {
                ProjectManifest.PageSpec spec = findPageSpec(manifest, r);
                String role = (spec != null && spec.getNavRole() != null) ? spec.getNavRole() : "PRIMARY";
                if ("OVERLAY".equals(role)) {
                    overlayRoutes.add(r);
                } else {
                    primaryRoutes.add(r);
                }
            }
            // Fallback: if no OVERLAY defined by architect, synthesize a generic detail
            if (overlayRoutes.isEmpty()) {
                overlayRoutes.add(new Route("detail", "内容详情", "OVERLAY"));
            }

            boolean deterministicContentFirst = shouldUseDeterministicContentFirstPipeline(manifest);
            if (deterministicContentFirst) {
                log.info("[Designer] Using deterministic shape-aligned pipeline for content-first prototype generation.");
            }

            // STEP 1: Generate App Shell
            log.info("Step 1: Generating Application Layout Shell with {} primary routes...", primaryRoutes.size());
            String shellHtml = generateShell(manifest, primaryRoutes, lang);

            // STEP 2: Generate Page Components (Parallel Execution)
            log.info("Step 2: Generating Feature Components (Parallel Bridge Active)...");
            List<java.util.concurrent.CompletableFuture<String>> futures = new ArrayList<>();
            for (Route route : primaryRoutes.stream().limit(6).toList()) {
                ProjectManifest.PageSpec pageSpec = findPageSpec(manifest, route);
                futures.add(java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try {
                        log.info("[Designer] Starting parallel generation for: {}", route.id);
                        String rawHtml = generateComponent(manifest, route, pageSpec, lang);
                        return ensureRenderableComponent(manifest, route, pageSpec, rawHtml);
                    } catch (Exception e) {
                        log.error("[Designer] Parallel component generation failed for {}: {}", route.id, e.getMessage());
                        return buildFallbackComponent(manifest, route, pageSpec);
                    }
                }));
            }

            StringBuilder contentSlots = new StringBuilder();
            for (java.util.concurrent.CompletableFuture<String> future : futures) {
                try {
                    // Timeout after 60 seconds per page (effectively parallel)
                    contentSlots.append(future.get(60, java.util.concurrent.TimeUnit.SECONDS)).append("\n");
                } catch (Exception e) {
                    log.error("[Designer] Feature component future timed out or failed", e);
                }
            }

            // STEP 2b: Generate Detail Modal Component (OVERLAY slot)
            log.info("Step 2b: Generating Detail Modal for OVERLAY routes...");
            String modalHtml = deterministicContentFirst
                    ? buildFallbackDetailModal(lang)
                    : generateDetailModal(manifest, overlayRoutes.get(0), lang);

            // STEP 3: Assembly
            log.info("Step 3: Assembling prototype...");
            String processedShell = shellHtml;
            String mockJson = manifest.getMockData() != null ? manifest.getMockData() : "[]";
            String safeMockJson = mockJson.replace("\"", "&quot;");
            processedShell = processedShell.replace("{{MOCK_DATA}}", safeMockJson);

            // Inject MODAL_SLOT
            processedShell = processedShell.replace("{{MODAL_SLOT}}", modalHtml);

            String finalHtml;
            if (processedShell.contains("{{CONTENT_SLOTS}}")) {
                finalHtml = processedShell.replace("{{CONTENT_SLOTS}}", contentSlots.toString());
            } else if (processedShell.contains("{{CONTENT_SLOT}}")) {
                finalHtml = processedShell.replace("{{CONTENT_SLOT}}", contentSlots.toString());
            } else {
                log.warn("Shell missing {{CONTENT_SLOTS}} tag. Appending blocks manually.");
                finalHtml = processedShell + "\n<!-- ASSEMBLED COMPONENTS -->\n" + contentSlots.toString();
            }

            manifest.setPrototypeHtml(finalHtml);
            log.info("Multi-step prototype created successfully ({} chars).", finalHtml.length());

        } catch (Exception e) {
            log.error("Prototype multi-step design failed", e);
            throw new RuntimeException("UI Design pipeline failed: " + e.getMessage());
        }
    }

    public void rebuildShapeAlignedPrototype(ProjectManifest manifest) {
        log.info("[Designer] Creating industrial-grade instant seed prototype for: {}", manifest.getUserIntent());
        String lang = manifest.getMetaData() != null ? manifest.getMetaData().getOrDefault("lang", "EN") : "EN";
        List<Route> routes = extractRoutes(manifest);

        List<Route> primaryRoutes = routes.stream().filter(r -> !"OVERLAY".equals(r.navType)).toList();
        List<Route> overlayRoutes = routes.stream().filter(r -> "OVERLAY".equals(r.navType)).toList();
        if (overlayRoutes.isEmpty()) {
            overlayRoutes = List.of(new Route("detail", "内容详情", "OVERLAY"));
        }

        boolean contentFirst = isContentFirst(manifest);
        String template = readResource(contentFirst ? "/templates/ContentFirstShell.html" : "/templates/StandardShell.html");

        // Phase 1: Build the Shell (Instant Frame)
        String shell = template
                .replace("{{TITLE}}", manifest.getOverview() != null ? manifest.getOverview() : "LingNow")
                .replace("{{LOGO_AREA}}", "<span class=\"text-xl font-bold text-rose-500\">" + (manifest.getOverview() != null ? manifest.getOverview() : "LingNow") + "</span>")
                .replace("{{SIDEBAR_NAV}}", buildFallbackPrimaryNav(manifest, primaryRoutes, contentFirst))
                .replace("{{UTILITY_BUTTONS}}", "")
                .replace("{{PERSONAL_LINKS}}", "");

        // Add Default Hash Init to prevent blank screen
        if (!primaryRoutes.isEmpty()) {
            String firstId = primaryRoutes.get(0).id;
            shell = shell.replace("hash: window.location.hash || '#pg1'", "hash: window.location.hash || '#" + firstId + "'");
        }

        // Phase 2: Build Deterministic Fallback Pages (Instant Content)
        StringBuilder contentSlots = new StringBuilder();
        for (Route route : primaryRoutes) {
            contentSlots.append(buildFallbackComponent(manifest, route, findPageSpec(manifest, route))).append("\n");
        }

        String modalHtml = buildFallbackDetailModal(lang);
        String finalHtml = shell
                .replace("{{MOCK_DATA}}", "[]")
                .replace("{{MODAL_SLOT}}", modalHtml)
                .replace("{{CONTENT_SLOTS}}", contentSlots.toString())
                .replace("{{CONTENT_SLOT}}", contentSlots.toString());

        manifest.setPrototypeHtml(finalHtml);
    }

    private List<Route> extractRoutes(ProjectManifest manifest) {
        String mindMap = manifest.getMindMap();
        if (mindMap == null || mindMap.trim().isEmpty()) {
            log.warn("[Designer] Mindmap is empty. Attempting to fall back to page titles.");
            return manifest.getPages() != null ?
                    manifest.getPages().stream()
                            .filter(p -> "PRIMARY".equals(p.getNavRole()))
                            .map(p -> new Route("pg_" + p.getRoute().hashCode(), p.getRoute(), p.getNavType()))
                            .toList() : new ArrayList<>();
        }

        String[] lines = mindMap.split("\\n");
        List<Route> routes = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String current = lines[i].trim();
            // Skip empty lines or technical markers
            if (current.isEmpty() || current.startsWith("```") || current.equalsIgnoreCase("mindmap"))
                continue;

            // Robust extraction: removes common markdown list prefixes (-, *, 1., etc.)
            String name = current.replaceAll("^[\\-\\s\\*\\d\\.]+", "").trim();
            if (name.isEmpty()) continue;

            String id = "pg" + (i + 1);

            // Match with PageSpec to get navType
            ProjectManifest.PageSpec spec = findPageSpec(manifest, new Route(id, name, "NAV_ANCHOR"));
            String navType = spec != null ? spec.getNavType() : "NAV_ANCHOR";

            routes.add(new Route(id, name, navType));
        }
        return routes;
    }

    private ProjectManifest.PageSpec findPageSpec(ProjectManifest manifest, Route route) {
        if (manifest.getPages() == null) return null;
        return manifest.getPages().stream()
                .filter(p -> {
                    String specRoute = p.getRoute().toLowerCase().replace("/", "");
                    String description = p.getDescription() != null ? p.getDescription().toLowerCase() : "";
                    String routeName = route.name.toLowerCase();
                    // Multi-layer fuzzy match: Route mapping + Description scanning
                    return specRoute.contains(routeName) || routeName.contains(specRoute) || description.contains(routeName);
                })
                .findFirst()
                .orElse(null);
    }

    private String cleanJsonResponse(String response) {
        if (response == null) return "{}";
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

    private String readResource(String path) {
        try (java.io.InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) return "";
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            log.warn("Failed to read resource: {}", path);
            return "";
        }
    }

    private String loadHandbook() {
        try {
            return java.nio.file.Files.readString(java.nio.file.Paths.get("/Users/eric/workspace/lingnow/.agents/skills/UI_DESIGN_HANDBOOK.md"));
        } catch (Exception e) {
            log.warn("[Designer] Handbook not found, falling back to core logic.");
            return "";
        }
    }

    private String generateShell(ProjectManifest manifest, List<Route> routes, String lang) {
        boolean contentFirst = isContentFirst(manifest);
        String template = readResource(contentFirst ? "/templates/ContentFirstShell.html" : "/templates/StandardShell.html");
        String handbook = loadHandbook();

        if (contentFirst) {
            String safeLogo = "<span class=\"text-xl font-bold text-rose-500\">" + (manifest.getOverview() != null ? manifest.getOverview() : "LingNow") + "</span>";
            return template
                    .replace("{{TITLE}}", manifest.getOverview() != null ? manifest.getOverview() : "LingNow App")
                    .replace("{{LOGO_AREA}}", safeLogo)
                    .replace("{{SIDEBAR_NAV}}", buildFallbackPrimaryNav(manifest, routes, true))
                    .replace("{{UTILITY_BUTTONS}}", "")
                    .replace("{{PERSONAL_LINKS}}", "");
        }

        // Categorize routes by navRole
        StringBuilder navContext = new StringBuilder("PAGE ROLES & ROUTES:\n");
        for (Route r : routes) {
            ProjectManifest.PageSpec spec = findPageSpec(manifest, r);
            String role = (spec != null && spec.getNavRole() != null) ? spec.getNavRole() : "PRIMARY";
            navContext.append(String.format("- Route: #%s, Name: %s, Role: %s\n", r.id, r.name, role));
        }

        String navPlacementInstruction = contentFirst
                ? """
                PRIMARY NAV MODE:
                - Generate compact horizontal pill navigation for PRIMARY routes.
                - Each PRIMARY link SHOULD look like a discover/feed tab instead of a tall sidebar item.
                - Each PRIMARY link: <a @click="hash='#ID'" :class="hash==='#ID'?'bg-rose-500 text-white shadow-lg shadow-rose-100':'bg-white text-slate-600'" class="inline-flex items-center gap-2 rounded-full px-4 py-2 text-sm font-semibold border border-slate-200 transition-all">
                - DO NOT generate extra publish, search, login, register, or profile buttons in utility/personal fragments; the shell already owns those actions.
                """
                : """
                SIDEBAR PURITY RULE:
                - ONLY generate <a> tags for routes with 'Role: PRIMARY'.
                - UTILITY and PERSONAL roles MUST NOT appear in the sidebar.
                - Each sidebar link: <a @click="hash='#ID'" :class="hash==='#ID'?'bg-rose-50 text-rose-600 font-semibold':''" class="flex items-center gap-3 px-4 py-2.5 rounded-xl text-slate-700 hover:bg-slate-100 transition-all text-sm">
                """;

        String systemPrompt = String.format("""
                %s
                
                YOUR GOAL: Generate Application Shell Fragments as a JSON object.
                
                CRITICAL - YOU MUST OUTPUT PURE JSON ONLY:
                - Do NOT output <!DOCTYPE>, <html>, <head>, <body> tags anywhere.
                - Output ONLY a JSON object with these four string keys: logo, sidebar, utility, personal.
                - Each value is an HTML FRAGMENT (a few tags, not a full document).
                
                %s
                
                DESIGN DNA: %s
                
                JSON OUTPUT FORMAT (respond ONLY with this, no extra text):
                {"logo": "<HTML_FRAGMENT>", "sidebar": "<HTML_FRAGMENT>", "utility": "<HTML_FRAGMENT>", "personal": "<HTML_FRAGMENT>"}
                """, handbook, navPlacementInstruction, getDynamicDNA(manifest));

        String userPrompt = String.format("Architecture Context:\n%s\nLanguage: %s", navContext, lang);

        try {
            String response = llmClient.chat(systemPrompt, userPrompt);
            JsonNode root = objectMapper.readTree(cleanJsonResponse(response));

            String logoHtml = root.path("logo").asText();
            String sidebarHtml = root.path("sidebar").asText();
            String utilityHtml = root.path("utility").asText();
            String personalHtml = root.path("personal").asText();

            sidebarHtml = normalizeHtmlFragment(sidebarHtml);
            utilityHtml = normalizeHtmlFragment(utilityHtml);
            personalHtml = normalizeHtmlFragment(personalHtml);
            logoHtml = normalizeHtmlFragment(logoHtml);

            if (sidebarHtml.isBlank()) {
                log.warn("[Designer] Shell JSON parse returned empty sidebar. Using safe fallback nav.");
                sidebarHtml = buildFallbackPrimaryNav(manifest, routes, contentFirst);
            }
            if (logoHtml.isBlank())
                logoHtml = "<span class=\"text-xl font-bold text-rose-500\">" + (manifest.getOverview() != null ? manifest.getOverview() : "LingNow") + "</span>";

            String shell = template
                    .replace("{{TITLE}}", manifest.getOverview() != null ? manifest.getOverview() : "LingNow App")
                    .replace("{{LOGO_AREA}}", logoHtml)
                    .replace("{{SIDEBAR_NAV}}", sidebarHtml)
                    .replace("{{UTILITY_BUTTONS}}", utilityHtml)
                    .replace("{{PERSONAL_LINKS}}", personalHtml);

            return shell;
        } catch (Exception e) {
            log.error("Shell fragment generation failed, using minimal safe fallback shell", e);
            // Safe fallback: build sidebar from routes list directly
            String safeLogo = "<span class=\"text-xl font-bold text-rose-500\">" + (manifest.getOverview() != null ? manifest.getOverview() : "LingNow") + "</span>";
            return template
                    .replace("{{TITLE}}", manifest.getOverview() != null ? manifest.getOverview() : "LingNow")
                    .replace("{{LOGO_AREA}}", safeLogo)
                    .replace("{{SIDEBAR_NAV}}", buildFallbackPrimaryNav(manifest, routes, contentFirst))
                    .replace("{{UTILITY_BUTTONS}}", "")
                    .replace("{{PERSONAL_LINKS}}", "");
        }
    }

    private String generateComponent(ProjectManifest manifest, Route route, ProjectManifest.PageSpec pageSpec, String lang) {
        String handbook = loadHandbook();
        String contextDescription = pageSpec != null ?
                "ARCHITECT'S PLAN: " + pageSpec.getDescription() + "\nEXPECTED COMPONENTS: " + String.join(", ", pageSpec.getComponents()) :
                "Generate a standard view for this feature.";
        boolean contentCommunity = isContentFirst(manifest) && isContentFirstRoute(route);
        String benchmarkInstruction = contentCommunity ? buildShapeInstruction(manifest) : "";

        String systemPrompt = String.format("""
                %s
                
                YOUR GOAL: Generate a high-density, pro-grade view for route: #%s.
                
                ⚠️ CRITICAL FORBIDDEN RULES (VIOLATION = BROKEN PAGE):
                - NEVER output <!DOCTYPE>, <html>, <head>, <body>, <script src=...> tags.
                - Output ONLY an HTML fragment starting with: <div x-show="hash === '#%s'">
                - NEVER start response with ```html or any markdown.
                
                MANDATORY INTERACTION LOGIC (MODAL BINDING):
                - If this is a Feed/Grid/List view, every item/card MUST have:
                  @click="selectedItem = item; hash = '#detail'" class="cursor-pointer"
                - Use Alpine.js x-for loop to render from mockData array.
                
                %s
                
                REQUIRED WRAPPER (start your output with exactly this):
                <div x-show="hash === '#%s'" class="animate-fade-in pb-8">
                """, handbook, route.id, route.id, benchmarkInstruction, route.id);

        String userPrompt = String.format("Feature: %s (Route: #%s)\n%s\nUser Intent: %s\nMock Data example: %s",
                route.name, route.id, contextDescription, manifest.getUserIntent(), manifest.getMockData());

        try {
            String response = llmClient.chat(systemPrompt, userPrompt);
            return parseHtmlSnippet(response);
        } catch (java.io.IOException e) {
            log.error("Failed to generate component for {}", route.id, e);
            return "<!-- Error generating " + route.id + " -->";
        }
    }

    private String ensureRenderableComponent(ProjectManifest manifest, Route route, ProjectManifest.PageSpec pageSpec, String componentHtml) {
        if (shouldUseDeterministicContentFallback(manifest, route)) {
            log.info("[Designer] Route {} is using deterministic shape fallback to preserve layout rhythm.", route.id);
            return buildFallbackComponent(manifest, route, pageSpec);
        }
        if (isRenderableComponent(manifest, route, componentHtml)) {
            return componentHtml;
        }
        log.warn("[Designer] Component for {} returned sparse or invalid markup. Using deterministic fallback.", route.id);
        return buildFallbackComponent(manifest, route, pageSpec);
    }

    private boolean isRenderableComponent(ProjectManifest manifest, Route route, String componentHtml) {
        if (componentHtml == null) {
            return false;
        }
        String trimmed = componentHtml.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("<!-- Error")) {
            return false;
        }
        if (!trimmed.startsWith("<") || !trimmed.contains("hash === '#" + route.id + "'")) {
            return false;
        }
        if (trimmed.contains("\\'") || trimmed.contains("\\\"")) {
            return false;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        String visibleText = trimmed.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        int contentSignals = countOccurrences(lower, "<section")
                + countOccurrences(lower, "<article")
                + countOccurrences(lower, "x-for=");
        boolean hasFeedInteraction = lower.contains("selecteditem = item")
                && (lower.contains("hash = '#detail'") || lower.contains("hash='#detail'"));
        if (isContentFirstRoute(route) && manifest.getDesignContract() != null) {
            int articleCount = countOccurrences(lower, "<article");
            int asideCount = countOccurrences(lower, "<aside");
            int stickyCount = countOccurrences(lower, "sticky top-");
            boolean hasWaterfallRhythm = containsAny(lower, "columns-", "break-inside-avoid", "waterfall");
            boolean hasInteractiveFiltering = containsAny(lower, "activecategory", "activesignal", "searchquery", "getfilteredfeed");
            boolean hasInBodyCategoryStrip = containsAny(lower, "@click=\"activecategory", "@click='activecategory");
            boolean hasPortalBias = asideCount > 1
                    || stickyCount > 0
                    || containsAny(lower, "grid grid-cols-12", "col-span-12 xl:col-span-2", "col-span-12 xl:col-span-3", "backdrop-blur border border-slate-200 shadow-sm p-5");
            int minCards = manifest.getDesignContract() != null ? Math.max(4, manifest.getDesignContract().getMinPrimaryCards() - 1) : 4;
            ProjectManifest.LayoutRhythm layoutRhythm = manifest.getDesignContract().getLayoutRhythm();
            boolean requiresWaterfall = layoutRhythm == ProjectManifest.LayoutRhythm.WATERFALL;
            boolean requiresThreadList = layoutRhythm == ProjectManifest.LayoutRhythm.THREAD || layoutRhythm == ProjectManifest.LayoutRhythm.LIST;
            boolean hasStructuredListSignals = containsAny(lower, "divide-y", "space-y-4", "grid-cols-1", "border-b border-slate");
            return visibleText.length() >= 120
                    && contentSignals > 0
                    && hasFeedInteraction
                    && articleCount >= minCards
                    && (!requiresWaterfall || hasWaterfallRhythm)
                    && (!requiresThreadList || hasStructuredListSignals || articleCount >= minCards)
                    && hasInteractiveFiltering
                    && !hasInBodyCategoryStrip
                    && !hasPortalBias
                    && !hasInternalLanguageLeak(manifest, lower, visibleText);
        }
        return visibleText.length() >= 120 && contentSignals > 0;
    }

    private boolean shouldUseDeterministicContentFallback(ProjectManifest manifest, Route route) {
        if (manifest == null || manifest.getDesignContract() == null || !isContentFirstRoute(route)) {
            return false;
        }
        ProjectManifest.LayoutRhythm rhythm = manifest.getDesignContract().getLayoutRhythm();
        return rhythm == ProjectManifest.LayoutRhythm.LIST
                || rhythm == ProjectManifest.LayoutRhythm.THREAD
                || rhythm == ProjectManifest.LayoutRhythm.EDITORIAL;
    }

    private boolean shouldUseDeterministicContentFirstPipeline(ProjectManifest manifest) {
        if (manifest == null || manifest.getDesignContract() == null || !isContentFirst(manifest)) {
            return false;
        }
        ProjectManifest.LayoutRhythm rhythm = manifest.getDesignContract().getLayoutRhythm();
        return rhythm == ProjectManifest.LayoutRhythm.WATERFALL
                || rhythm == ProjectManifest.LayoutRhythm.LIST
                || rhythm == ProjectManifest.LayoutRhythm.THREAD
                || rhythm == ProjectManifest.LayoutRhythm.EDITORIAL
                || manifest.getDesignContract().getMediaWeight() == ProjectManifest.MediaWeight.VISUAL_HEAVY
                || manifest.getDesignContract().getMediaWeight() == ProjectManifest.MediaWeight.TEXT_HEAVY;
    }

    private String buildFallbackComponent(ProjectManifest manifest, Route route, ProjectManifest.PageSpec pageSpec) {
        if (manifest.getDesignContract() != null && isContentFirst(manifest) && isContentFirstRoute(route)) {
            ShapeSurfaceProfile profile = buildShapeSurfaceProfile(manifest);
            if (profile.layoutRhythm() == ProjectManifest.LayoutRhythm.WATERFALL) {
                return buildWaterfallFallbackComponent(manifest, route, pageSpec, profile);
            }
            return buildStructuredFeedFallbackComponent(manifest, route, pageSpec, profile);
        }
        return buildGenericFallbackComponent(manifest, route, pageSpec);
    }

    private String buildGenericFallbackComponent(ProjectManifest manifest, Route route, ProjectManifest.PageSpec pageSpec) {
        ProjectManifest.DesignContract contract = manifest.getDesignContract();
        boolean zh = manifest.getMetaData() == null || !"EN".equalsIgnoreCase(manifest.getMetaData().getOrDefault("lang", "ZH"));
        String title = escapeHtml(route.name);
        String description = escapeHtml(pageSpec != null && pageSpec.getDescription() != null
                ? pageSpec.getDescription()
                : manifest.getUserIntent());
        String eyebrow = contract != null && contract.getUiTone() == ProjectManifest.UiTone.ENTERPRISE
                ? (zh ? "关键指标总览" : "Key overview")
                : (zh ? "结构化内容页" : "Structured content page");
        return """
                <div x-show="hash === '#__ID__'" class="animate-fade-in pb-8 space-y-6">
                  <section class="rounded-[28px] border border-slate-200 bg-white/95 p-6 shadow-sm">
                    <span class="inline-flex items-center rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-600">__EYEBROW__</span>
                    <h1 class="mt-4 text-3xl font-black tracking-tight text-slate-900">__TITLE__</h1>
                    <p class="mt-3 max-w-3xl text-sm leading-7 text-slate-600">__DESCRIPTION__</p>
                  </section>
                  <section class="rounded-[28px] border border-slate-200 bg-white p-6 shadow-sm">
                    <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
                      <article class="rounded-2xl border border-slate-200 bg-slate-50 p-5">
                        <div class="text-xs font-semibold uppercase tracking-[0.2em] text-slate-400">Overview</div>
                        <div class="mt-3 text-lg font-bold text-slate-900">__TITLE__</div>
                        <p class="mt-2 text-sm leading-6 text-slate-600">__DESCRIPTION__</p>
                      </article>
                      <article class="rounded-2xl border border-slate-200 bg-slate-50 p-5">
                        <div class="text-xs font-semibold uppercase tracking-[0.2em] text-slate-400">Signals</div>
                        <ul class="mt-3 space-y-2 text-sm text-slate-600">
                          <li>• __SIGNAL_A__</li>
                          <li>• __SIGNAL_B__</li>
                          <li>• __SIGNAL_C__</li>
                        </ul>
                      </article>
                      <article class="rounded-2xl border border-slate-200 bg-slate-50 p-5">
                        <div class="text-xs font-semibold uppercase tracking-[0.2em] text-slate-400">Flow</div>
                        <p class="mt-3 text-sm leading-6 text-slate-600">__FLOW__</p>
                      </article>
                    </div>
                  </section>
                </div>
                """
                .replace("__ID__", route.id)
                .replace("__EYEBROW__", eyebrow)
                .replace("__TITLE__", title)
                .replace("__DESCRIPTION__", description)
                .replace("__SIGNAL_A__", zh ? "模块结构清晰" : "Clear module structure")
                .replace("__SIGNAL_B__", zh ? "支持后续迭代扩展" : "Ready for iterative expansion")
                .replace("__SIGNAL_C__", zh ? "可继续补齐真实数据" : "Ready for richer real data")
                .replace("__FLOW__", zh ? "从当前模块进入详情或下一步操作，保持结构稳定并等待更精细的内容生成。" : "Move from this module into detail or the next action while keeping the structure stable for richer content generation.");
    }

    private String buildShapeInstruction(ProjectManifest manifest) {
        ProjectManifest.DesignContract contract = manifest.getDesignContract();
        if (contract == null) {
            return "";
        }
        ShapeSurfaceProfile profile = buildShapeSurfaceProfile(manifest);
        String signalHints = profile.signalOneEn() + ", " + profile.signalTwoEn() + ", " + profile.signalThreeEn();
        return String.format("""
                        SHAPE CONTRACT MODE:
                        - Build the route from the declared product shape, not from any brand benchmark.
                        - Consumption mode: %s.
                        - Media weight: %s.
                        - Layout rhythm: %s.
                        - Content density: %s.
                        - Main loop: %s.
                        - Primary signals to surface: %s.
                        - UI tone: %s.
                        - Category strip should use domain-fit labels such as: %s.
                        - If the layout rhythm is WATERFALL, use varied card heights and strong cover media.
                        - If the layout rhythm is LIST or THREAD, prioritize title, summary, author, tags, discussion, and metadata before oversized imagery.
                        - Keep auxiliary content light and functional.
                        - Never mention internal system language or product benchmark names in visible copy.
                        """,
                safeEnumName(contract.getConsumptionMode()),
                safeEnumName(contract.getMediaWeight()),
                safeEnumName(contract.getLayoutRhythm()),
                safeEnumName(contract.getContentDensity()),
                safeEnumName(contract.getMainLoop()),
                signalHints,
                safeEnumName(contract.getUiTone()),
                String.join(" / ", profile.categoriesZh()));
    }

    private String buildRealMediaArrayJson(boolean cover, ShapeSurfaceProfile profile) {
        List<String> media = cover
                ? (profile.layoutRhythm() == ProjectManifest.LayoutRhythm.WATERFALL
                ? List.of(
                "https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?q=80&w=1200",
                "https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?q=80&w=1200",
                "https://images.unsplash.com/photo-1483985988355-763728e1935b?q=80&w=1200",
                "https://images.unsplash.com/photo-1496747611176-843222e1e57c?q=80&w=1200",
                "https://images.unsplash.com/photo-1511988617509-a57c8a288659?q=80&w=1200",
                "https://images.unsplash.com/photo-1529139574466-a303027c1d8b?q=80&w=1200",
                "https://images.unsplash.com/photo-1500534314209-a25ddb2bd429?q=80&w=1200",
                "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?q=80&w=1200"
        )
                : List.of(
                "https://images.unsplash.com/photo-1515879218367-8466d910aaa4?q=80&w=1200",
                "https://images.unsplash.com/photo-1516321318423-f06f85e504b3?q=80&w=1200",
                "https://images.unsplash.com/photo-1517694712202-14dd9538aa97?q=80&w=1200",
                "https://images.unsplash.com/photo-1517180102446-f3ece451e9d8?q=80&w=1200",
                "https://images.unsplash.com/photo-1451187580459-43490279c0fa?q=80&w=1200",
                "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?q=80&w=1200",
                "https://images.unsplash.com/photo-1461749280684-dccba630e2f6?q=80&w=1200",
                "https://images.unsplash.com/photo-1511376777868-611b54f68947?q=80&w=1200"
        ))
                : List.of(
                "https://images.unsplash.com/photo-1494790108377-be9c29b29330?q=80&w=256",
                "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?q=80&w=256",
                "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?q=80&w=256",
                "https://images.unsplash.com/photo-1438761681033-6461ffad8d80?q=80&w=256",
                "https://images.unsplash.com/photo-1504257432389-52343af06ae3?q=80&w=256",
                "https://images.unsplash.com/photo-1502685104226-ee32379fefbe?q=80&w=256",
                "https://images.unsplash.com/photo-1544005313-94ddf0286df2?q=80&w=256",
                "https://images.unsplash.com/photo-1503023345310-bd7c1de61c7d?q=80&w=256"
        );
        try {
            return objectMapper.writeValueAsString(media);
        } catch (Exception e) {
            log.warn("[Designer] Failed to serialize seeded media array", e);
            return "[]";
        }
    }

    private String buildHotTopicsJson(boolean zh, ShapeSurfaceProfile profile) {
        List<String> topics = zh ? profile.hotTopicsZh() : profile.hotTopicsEn();
        try {
            return objectMapper.writeValueAsString(topics);
        } catch (Exception e) {
            log.warn("[Designer] Failed to serialize hot topics", e);
            return "[]";
        }
    }

    private String buildSeededFeedJson(boolean zh, int count, ShapeSurfaceProfile profile) {
        if (profile.layoutRhythm() == ProjectManifest.LayoutRhythm.WATERFALL) {
            return buildLifestyleSeededFeedJson(zh, count);
        }
        return buildKnowledgeSeededFeedJson(zh, count);
    }

    private String buildLifestyleSeededFeedJson(boolean zh, int count) {
        List<Map<String, Object>> cards = new ArrayList<>();
        cards.add(seedCard(
                "id", "seed-1",
                "title", zh ? "上海法租界一日漫游路线，适合拍照也适合慢慢逛" : "A photo-friendly city walk through Shanghai's French Concession",
                "author", zh ? "小鹿在上海" : "Lulu in Shanghai",
                "avatar", "https://images.unsplash.com/photo-1494790108377-be9c29b29330?q=80&w=256",
                "description", zh ? "把咖啡馆、街角书店和安静老洋房串成一条舒服路线，周末照着走就很出片。" : "A soft weekend route with cafés, bookstores, and old lanes that feels effortless on camera.",
                "cover", "https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?q=80&w=1200",
                "location", zh ? "上海" : "Shanghai",
                "time", zh ? "24分钟前" : "24m ago",
                "category", zh ? "旅行" : "Travel",
                "mediaType", zh ? "图文" : "Photo",
                "likes", "2.9w",
                "comments", "1741",
                "collects", "2098",
                "tags", List.of(zh ? "城市漫游" : "City walk", zh ? "法租界" : "Old lane", zh ? "出片路线" : "Photo route")
        ));
        cards.add(seedCard(
                "id", "seed-2",
                "title", zh ? "三分钟学会韩系清透妆，通勤也能很精神" : "A three-minute clean makeup routine for commuting days",
                "author", zh ? "橘子美妆课" : "Glow Journal",
                "avatar", "https://images.unsplash.com/photo-1544005313-94ddf0286df2?q=80&w=256",
                "description", zh ? "底妆轻一点、眼下提亮一点，通勤也能有干净的镜头感。" : "Lighter base, brighter under-eyes, and enough polish to feel camera-ready.",
                "cover", "https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?q=80&w=1200",
                "location", zh ? "广州" : "Guangzhou",
                "time", zh ? "1小时前" : "1h ago",
                "category", zh ? "彩妆" : "Beauty",
                "mediaType", zh ? "视频" : "Video",
                "likes", "1.8w",
                "comments", "932",
                "collects", "1608",
                "tags", List.of(zh ? "今日妆容" : "Makeup", zh ? "通勤" : "Commute", zh ? "干净感" : "Clean look")
        ));
        cards.add(seedCard(
                "id", "seed-3",
                "title", zh ? "露营小盒子：深圳周末亲子露营清单" : "Weekend camping checklist for young families",
                "author", zh ? "露营小盒子" : "Camp Notes",
                "avatar", "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?q=80&w=256",
                "description", zh ? "轻量装备、好收纳的餐具和夜晚保暖细节，一次整理齐全。" : "A lightweight family-ready camping kit with storage, meals, and nighttime warmth covered.",
                "cover", "https://images.unsplash.com/photo-1529139574466-a303027c1d8b?q=80&w=1200",
                "location", zh ? "深圳" : "Shenzhen",
                "time", zh ? "2小时前" : "2h ago",
                "category", zh ? "旅行" : "Travel",
                "mediaType", zh ? "图文" : "Photo",
                "likes", "9.8k",
                "comments", "614",
                "collects", "1210",
                "tags", List.of(zh ? "亲子露营" : "Family camp", zh ? "装备清单" : "Gear list", zh ? "本地生活" : "Local life")
        ));
        cards.add(seedCard(
                "id", "seed-4",
                "title", zh ? "一杯不晚：新手家庭咖啡角怎么搭" : "How to build a beginner-friendly home coffee corner",
                "author", zh ? "一杯不晚" : "Coffee Route",
                "avatar", "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?q=80&w=256",
                "description", zh ? "半自动和全自动咖啡机怎么选，预算、清洁和噪音一次说清楚。" : "Picking between semi-auto and super-auto with budget, maintenance, and noise in mind.",
                "cover", "https://images.unsplash.com/photo-1511988617509-a57c8a288659?q=80&w=1200",
                "location", zh ? "杭州" : "Hangzhou",
                "time", zh ? "3小时前" : "3h ago",
                "category", zh ? "家居" : "Home",
                "mediaType", zh ? "视频" : "Video",
                "likes", "1.2w",
                "comments", "802",
                "collects", "1740",
                "tags", List.of(zh ? "咖啡角" : "Coffee setup", zh ? "家居改造" : "Home upgrade", zh ? "好物清单" : "Gear picks")
        ));
        cards.add(seedCard(
                "id", "seed-5",
                "title", zh ? "成都人气火锅店排队攻略，少踩雷也能吃得尽兴" : "A better way to plan a hotpot night in Chengdu",
                "author", zh ? "周末城市探索" : "City Notes",
                "avatar", "https://images.unsplash.com/photo-1503023345310-bd7c1de61c7d?q=80&w=256",
                "description", zh ? "营业时间、排号节奏、附近小吃和拍照位，出门前先看这一篇。" : "Timing, queue rhythm, nearby snacks, and where to sit before you head out.",
                "cover", "https://images.unsplash.com/photo-1500534314209-a25ddb2bd429?q=80&w=1200",
                "location", zh ? "成都" : "Chengdu",
                "time", zh ? "5小时前" : "5h ago",
                "category", zh ? "美食" : "Food",
                "mediaType", zh ? "图文" : "Photo",
                "likes", "8.7k",
                "comments", "503",
                "collects", "1180",
                "tags", List.of(zh ? "成都美食" : "Chengdu food", zh ? "排队攻略" : "Queue tips", zh ? "本地生活" : "Local life")
        ));
        cards.add(seedCard(
                "id", "seed-6",
                "title", zh ? "北京胡同咖啡地图：适合午后散步的 6 家店" : "Six hutong cafés perfect for an afternoon walk",
                "author", zh ? "城市咖啡地图" : "Local Picks",
                "avatar", "https://images.unsplash.com/photo-1438761681033-6461ffad8d80?q=80&w=256",
                "description", zh ? "从安静院子到有窗景的吧台，顺着地图慢慢逛会很舒服。" : "A slow café route with courtyards, window seats, and a soft walking pace.",
                "cover", "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?q=80&w=1200",
                "location", zh ? "北京" : "Beijing",
                "time", zh ? "6小时前" : "6h ago",
                "category", zh ? "摄影" : "Photo",
                "mediaType", zh ? "图文" : "Photo",
                "likes", "1.4w",
                "comments", "926",
                "collects", "1863",
                "tags", List.of(zh ? "胡同咖啡" : "Hutong café", zh ? "城市漫游" : "City walk", zh ? "周末去哪儿" : "Weekend picks")
        ));

        try {
            return objectMapper.writeValueAsString(cards.subList(0, Math.min(cards.size(), Math.max(count, 4))));
        } catch (Exception e) {
            log.warn("[Designer] Failed to serialize seeded feed cards", e);
            return "[]";
        }
    }

    private String buildKnowledgeSeededFeedJson(boolean zh, int count) {
        List<Map<String, Object>> cards = new ArrayList<>();
        cards.add(seedCard(
                "id", "knowledge-1",
                "title", zh ? "把 AI Coding Agent 接进 CI：从 PR 检查到自动修复的设计" : "Wiring AI coding agents into CI from PR checks to auto-repair",
                "author", zh ? "林前端" : "Lin Frontend",
                "avatar", "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?q=80&w=256",
                "description", zh ? "把静态检查、集成测试和回归日志串成一个可观察的自动化链路，减少人工兜底。" : "An observable chain for static checks, integration tests, and repair logs that reduces manual cleanup.",
                "cover", "https://images.unsplash.com/photo-1515879218367-8466d910aaa4?q=80&w=1200",
                "location", zh ? "工程实践" : "Engineering",
                "time", zh ? "18分钟前" : "18m ago",
                "category", zh ? "AI 编程" : "AI Coding",
                "mediaType", zh ? "文章" : "Article",
                "likes", "3.6k",
                "comments", "218",
                "collects", "1.4k",
                "tags", List.of(zh ? "Agent" : "Agent", zh ? "CI/CD" : "CI/CD", zh ? "质量门" : "Quality gate")
        ));
        cards.add(seedCard(
                "id", "knowledge-2",
                "title", zh ? "React Compiler 时代，哪些 useMemo 真的该删掉？" : "Which useMemo calls should disappear in the React Compiler era?",
                "author", zh ? "阿泽 React" : "Aze React",
                "avatar", "https://images.unsplash.com/photo-1504257432389-52343af06ae3?q=80&w=256",
                "description", zh ? "结合真实组件拆解哪些缓存是噪音，哪些仍然值得保留。" : "A component-by-component look at which caches are noise and which still matter.",
                "cover", "https://images.unsplash.com/photo-1517694712202-14dd9538aa97?q=80&w=1200",
                "location", zh ? "前端框架" : "Frontend",
                "time", zh ? "1小时前" : "1h ago",
                "category", zh ? "前端" : "Frontend",
                "mediaType", zh ? "文章" : "Article",
                "likes", "4.1k",
                "comments", "356",
                "collects", "2.2k",
                "tags", List.of("React", zh ? "性能优化" : "Performance", zh ? "编译器" : "Compiler")
        ));
        cards.add(seedCard(
                "id", "knowledge-3",
                "title", zh ? "5 个生产环境 Redis 事故复盘：不是缓存，是系统边界问题" : "Five Redis incident postmortems that were really boundary problems",
                "author", zh ? "老韩 SRE" : "Han SRE",
                "avatar", "https://images.unsplash.com/photo-1502685104226-ee32379fefbe?q=80&w=256",
                "description", zh ? "从雪崩、击穿到热点 key，真正需要治理的是依赖关系和退化路径。" : "From cache avalanches to hot keys, the real fix is usually dependency design and graceful degradation.",
                "cover", "https://images.unsplash.com/photo-1451187580459-43490279c0fa?q=80&w=1200",
                "location", zh ? "基础设施" : "Infra",
                "time", zh ? "2小时前" : "2h ago",
                "category", zh ? "架构" : "Architecture",
                "mediaType", zh ? "文章" : "Article",
                "likes", "5.2k",
                "comments", "287",
                "collects", "2.8k",
                "tags", List.of("Redis", zh ? "故障复盘" : "Postmortem", zh ? "高可用" : "Reliability")
        ));
        cards.add(seedCard(
                "id", "knowledge-4",
                "title", zh ? "从 0 到 1 设计可扩展的 BFF 层：权限、聚合与缓存策略" : "Designing a scalable BFF layer with auth, aggregation, and caching",
                "author", zh ? "周后端" : "Zhou Backend",
                "avatar", "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?q=80&w=256",
                "description", zh ? "把前端定制接口、聚合查询与鉴权隔离在 BFF，服务边界会清晰很多。" : "BFF helps isolate front-end specific APIs, aggregation queries, and auth into a cleaner service boundary.",
                "cover", "https://images.unsplash.com/photo-1516321318423-f06f85e504b3?q=80&w=1200",
                "location", zh ? "后端架构" : "Backend",
                "time", zh ? "42分钟前" : "42m ago",
                "category", zh ? "后端" : "Backend",
                "mediaType", zh ? "文章" : "Article",
                "likes", "2.4k",
                "comments", "163",
                "collects", "1.1k",
                "tags", List.of("BFF", zh ? "缓存" : "Caching", zh ? "权限" : "Authorization")
        ));
        try {
            return objectMapper.writeValueAsString(cards.subList(0, Math.min(cards.size(), Math.max(count, 4))));
        } catch (Exception e) {
            log.warn("[Designer] Failed to serialize knowledge feed cards", e);
            return "[]";
        }
    }

    private Map<String, Object> seedCard(Object... keyValues) {
        Map<String, Object> card = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            card.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return card;
    }

    private String buildFallbackPrimaryNav(ProjectManifest manifest, List<Route> routes, boolean contentFirst) {
        if (contentFirst) {
            return buildFallbackCategoryNav(manifest, routes);
        }
        StringBuilder fallbackNav = new StringBuilder();
        for (Route route : routes) {
            fallbackNav.append(String.format(
                    "<a @click=\"hash='#%s'\" :class=\"hash==='#%s'?'bg-rose-50 text-rose-600 font-semibold':''\" class=\"flex items-center gap-3 px-4 py-2.5 rounded-xl text-slate-700 hover:bg-slate-100 transition-all text-sm\">%s</a>\n",
                    route.id, route.id, route.name));
        }
        return fallbackNav.toString();
    }

    private String buildFallbackCategoryNav(ProjectManifest manifest, List<Route> routes) {
        boolean zh = manifest.getMetaData() == null || !"EN".equalsIgnoreCase(manifest.getMetaData().getOrDefault("lang", "ZH"));
        ShapeSurfaceProfile profile = buildShapeSurfaceProfile(manifest);
        String homeRouteId = routes.stream()
                .filter(this::isContentFirstRoute)
                .map(route -> route.id)
                .findFirst()
                .orElse(routes.isEmpty() ? "pg1" : routes.get(0).id);
        List<String> categories = zh ? profile.categoriesZh() : profile.categoriesEn();
        StringBuilder nav = new StringBuilder();
        for (String category : categories) {
            String normalizedCategory = escapeHtml(category);
            String activeValue = ("推荐".equals(category) || "For you".equals(category)) ? "" : normalizedCategory;
            nav.append(String.format(
                    "<button @click=\"activeCategory='%s'; hash='#%s'\" :class=\"((!activeCategory && '%s'==='') || activeCategory === '%s')?'bg-slate-900 text-white shadow-lg shadow-slate-200':'bg-white text-slate-600'\" class=\"inline-flex items-center gap-2 rounded-full border border-slate-200 px-4 py-2 text-sm font-semibold transition-all hover:border-slate-300\">%s</button>\n",
                    activeValue,
                    homeRouteId,
                    activeValue,
                    normalizedCategory,
                    normalizedCategory
            ));
        }
        return nav.toString();
    }

    private boolean isContentFirst(ProjectManifest manifest) {
        return manifest.getDesignContract() != null
                && "CONTENT_FIRST".equalsIgnoreCase(manifest.getDesignContract().getContentMode());
    }

    private boolean isContentFirstRoute(Route route) {
        String lower = route.name == null ? "" : route.name.toLowerCase(Locale.ROOT);
        return containsAny(lower, "首页", "推荐", "发现", "feed", "discover", "home");
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

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String normalizeHtmlFragment(String fragment) {
        if (fragment == null || fragment.isBlank()) {
            return "";
        }
        return fragment
                .replace("\\\"", "\"")
                .replace("\\'", "'")
                .replace("\\/", "/")
                .trim();
    }

    private boolean hasInternalLanguageLeak(ProjectManifest manifest, String htmlLower, String visibleText) {
        String normalizedVisible = visibleText == null ? "" : visibleText.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        String normalizedIntent = manifest.getUserIntent() == null ? "" : manifest.getUserIntent().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        boolean echoesPrompt = !normalizedIntent.isBlank() && normalizedIntent.length() >= 6 && normalizedVisible.contains(normalizedIntent);
        boolean mentionsInternalLanguage = containsAny(htmlLower,
                "小红书",
                "content-first",
                "内容优先布局",
                "灵感发现流",
                "类似小红书",
                "discover feed",
                "pinterest-style",
                "xiaohongshu");
        return echoesPrompt || mentionsInternalLanguage;
    }

    private String buildWaterfallFallbackComponent(ProjectManifest manifest, Route route, ProjectManifest.PageSpec pageSpec, ShapeSurfaceProfile profile) {
        ProjectManifest.DesignContract contract = manifest.getDesignContract();
        boolean zh = manifest.getMetaData() == null || !"EN".equalsIgnoreCase(manifest.getMetaData().getOrDefault("lang", "ZH"));
        int primaryCards = contract != null ? Math.max(contract.getMinPrimaryCards(), 6) : 6;
        String description = escapeHtml(pageSpec != null && pageSpec.getDescription() != null ? pageSpec.getDescription() : manifest.getUserIntent());
        String coverPool = buildRealMediaArrayJson(true, profile);
        String avatarPool = buildRealMediaArrayJson(false, profile);
        String seededFeed = buildSeededFeedJson(zh, Math.max(primaryCards, 6), profile);
        String hotTopics = buildHotTopicsJson(zh, profile);
        return """
                <div x-show="hash === '#__ID__'" class="animate-fade-in pb-8 space-y-6 relative">
                  <!-- AI Hydration Indicator -->
                  <div class="pointer-events-none sticky top-4 z-30 mb-6 flex items-center justify-between">
                    <div class="flex items-center gap-2 px-3 py-1.5 bg-rose-500/90 backdrop-blur shadow-lg shadow-rose-200 border border-rose-400 rounded-full">
                      <span class="flex h-2 w-2 relative">
                        <span class="animate-ping absolute inline-flex h-full w-full rounded-full bg-white opacity-75"></span>
                        <span class="relative inline-flex rounded-full h-2 w-2 bg-white"></span>
                      </span>
                      <span class="text-[10px] font-black text-white uppercase tracking-widest">AI Polishing in Progress</span>
                    </div>
                  </div>
                  <section class="rounded-[28px] border border-slate-200 bg-white/95 p-6 shadow-sm">
                    <div class="flex flex-col gap-5 xl:flex-row xl:items-end xl:justify-between">
                      <div class="space-y-3">
                        <div class="flex flex-wrap items-center gap-3">
                          <span class="inline-flex items-center rounded-full bg-rose-50 px-3 py-1 text-xs font-semibold text-rose-500">__BADGE__</span>
                          <span class="inline-flex items-center rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-500">__SURFACE_LABEL__</span>
                        </div>
                        <h1 class="max-w-3xl text-3xl font-black tracking-tight text-slate-900">__HERO_TITLE__</h1>
                        <p class="max-w-3xl text-sm leading-7 text-slate-600">__HERO_DESCRIPTION__</p>
                        <div class="flex flex-wrap gap-2">
                          <span class="rounded-full border border-slate-200 bg-white px-4 py-2 text-sm text-slate-600">__PILL_ONE__</span>
                          <span class="rounded-full border border-slate-200 bg-white px-4 py-2 text-sm text-slate-600">__PILL_TWO__</span>
                          <span class="rounded-full border border-slate-200 bg-white px-4 py-2 text-sm text-slate-600">__PILL_THREE__</span>
                        </div>
                      </div>
                      <div class="flex flex-wrap gap-3 text-sm">
                        <button @click="activeSignal = activeSignal === 'saved' ? 'all' : 'saved'" :class="activeSignal === 'saved' ? 'bg-slate-900 text-white shadow-lg shadow-slate-200' : 'bg-white text-slate-700'" class="rounded-full px-4 py-2 font-semibold shadow-sm ring-1 ring-slate-200 transition-all">__SIGNAL_ONE__</button>
                        <button @click="activeSignal = activeSignal === 'hot' ? 'all' : 'hot'" :class="activeSignal === 'hot' ? 'bg-slate-900 text-white shadow-lg shadow-slate-200' : 'bg-white text-slate-700'" class="rounded-full px-4 py-2 font-semibold shadow-sm ring-1 ring-slate-200 transition-all">__SIGNAL_TWO__</button>
                        <button @click="activeSignal = activeSignal === 'media' ? 'all' : 'media'" :class="activeSignal === 'media' ? 'bg-slate-900 text-white shadow-lg shadow-slate-200' : 'bg-white text-slate-700'" class="rounded-full px-4 py-2 font-semibold shadow-sm ring-1 ring-slate-200 transition-all">__SIGNAL_THREE__</button>
                      </div>
                    </div>
                  </section>
                  <section class="grid gap-6 xl:grid-cols-[minmax(0,1fr)_300px]">
                    <div class="space-y-5">
                      <div class="flex items-end justify-between gap-4">
                        <div>
                          <h2 class="text-2xl font-black text-slate-900">__RECOMMEND_TITLE__</h2>
                          <p class="mt-1 text-sm text-slate-500">__RECOMMEND_SUBTITLE__</p>
                        </div>
                      </div>
                      <div class="lingnow-waterfall columns-1 gap-5 md:columns-2 2xl:columns-3">
                        <template x-for='(item, index) in getFilteredFeed(__SEEDED_FEED__).slice(0, __PRIMARY_CARDS__)' :key="(item.id || item.title || index) + '-' + index">
                          <article @click="selectedItem = item; hash = '#detail'" class="lingnow-waterfall-card group mb-5 cursor-pointer break-inside-avoid overflow-hidden rounded-[30px] border border-slate-200 bg-white shadow-sm transition duration-300 hover:-translate-y-1 hover:shadow-2xl">
                            <div class="overflow-hidden bg-slate-100" :class="index % 5 === 0 ? 'aspect-[4/6]' : (index % 5 === 1 ? 'aspect-[4/5]' : (index % 5 === 2 ? 'aspect-[4/4.8]' : (index % 5 === 3 ? 'aspect-[4/5.4]' : 'aspect-[4/6.2]')))">
                              <img :src='item.cover || item.image || item.thumbUrl || __COVER_POOL__[index % __COVER_POOL__.length]' class="h-full w-full object-cover transition duration-500 group-hover:scale-105" />
                            </div>
                            <div class="space-y-3 p-4">
                              <div class="flex items-center gap-3">
                                <img :src='item.avatar || item.authorAvatar || __AVATAR_POOL__[index % __AVATAR_POOL__.length]' class="h-10 w-10 rounded-full border border-white object-cover shadow-sm" />
                                <div class="min-w-0">
                                  <div class="truncate text-sm font-semibold text-slate-900" x-text="item.author || item.username || item.creator || '__AUTHOR_FALLBACK__'"></div>
                                  <div class="truncate text-xs text-slate-500"><span x-text="item.location || '__LOCATION_FALLBACK__'"></span><span class="mx-1">·</span><span x-text="item.time || item.publishTime || '__TIME_FALLBACK__'"></span></div>
                                </div>
                                <button class="ml-auto rounded-full bg-rose-50 px-3 py-1 text-xs font-bold text-rose-500">__FOLLOW_LABEL__</button>
                              </div>
                              <div>
                                <h3 class="line-clamp-2 text-lg font-black text-slate-900" x-text="item.title || item.name || '__CARD_TITLE_FALLBACK__'"></h3>
                                <p class="mt-2 line-clamp-3 text-sm leading-6 text-slate-600" x-text="item.description || item.content || item.summary || '__DESCRIPTION__'"></p>
                              </div>
                              <div class="flex flex-wrap gap-2">
                                <span class="rounded-full bg-slate-900/90 px-3 py-1 text-[11px] font-semibold text-white" x-text="item.mediaType || item.contentType || '__SIGNAL_THREE__'"></span>
                                <template x-for="(tag, tagIndex) in ((Array.isArray(item.tags) && item.tags.length ? item.tags.slice(0, 3) : [item.topic || '__TOPIC_FALLBACK__', item.category || '__CATEGORY_FALLBACK__']))" :key="tag + '-' + tagIndex">
                                  <span class="rounded-full bg-rose-50 px-3 py-1 text-[11px] font-semibold text-rose-500" x-text="'#' + tag"></span>
                                </template>
                              </div>
                              <div class="grid grid-cols-3 gap-2 rounded-2xl bg-slate-50/80 px-3 py-2 text-xs text-slate-500">
                                <div class="space-y-1"><div class="font-semibold text-slate-900" x-text="item.likes || item.likeCount || '2.9w'"></div><div>点赞</div></div>
                                <div class="space-y-1"><div class="font-semibold text-slate-900" x-text="item.comments || item.commentCount || '1.6k'"></div><div>评论</div></div>
                                <div class="space-y-1"><div class="font-semibold text-slate-900" x-text="item.collects || item.saves || '8.4k'"></div><div>收藏</div></div>
                              </div>
                            </div>
                          </article>
                        </template>
                      </div>
                    </div>
                    <aside class="space-y-4 xl:sticky xl:top-24">
                      <section data-aux-section="true" class="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm">
                        <div class="flex items-center justify-between">
                          <div><h3 class="text-lg font-black text-slate-900">__HOT_TOPIC_TITLE__</h3><p class="mt-1 text-xs text-slate-500">__HOT_TOPIC_HINT__</p></div>
                          <span class="rounded-full bg-rose-50 px-3 py-1 text-xs font-semibold text-rose-500">Hot</span>
                        </div>
                        <div class="mt-4 space-y-3">
                          <template x-for="topic in __HOT_TOPICS__" :key="topic">
                            <button @click="searchQuery = topic; activeSignal = 'hot'" class="w-full rounded-2xl bg-slate-50 px-4 py-3 text-left text-sm font-semibold text-slate-800 transition hover:bg-slate-100" x-text="'#' + topic"></button>
                          </template>
                        </div>
                      </section>
                    </aside>
                  </section>
                </div>
                """
                .replace("__ID__", route.id)
                .replace("__BADGE__", zh ? "当前社区首页" : "Current community home")
                .replace("__SURFACE_LABEL__", zh ? profile.surfaceLabelZh() : profile.surfaceLabelEn())
                .replace("__HERO_TITLE__", zh ? profile.heroTitleZh() : profile.heroTitleEn())
                .replace("__HERO_DESCRIPTION__", zh ? profile.heroDescriptionZh() : profile.heroDescriptionEn())
                .replace("__PILL_ONE__", zh ? "先看真实内容" : "Browse real content first")
                .replace("__PILL_TWO__", zh ? "轻筛选，轻互动" : "Light filtering, light interaction")
                .replace("__PILL_THREE__", zh ? "打开详情继续转化" : "Open detail for deeper action")
                .replace("__SIGNAL_ONE__", zh ? profile.signalOneZh() : profile.signalOneEn())
                .replace("__SIGNAL_TWO__", zh ? profile.signalTwoZh() : profile.signalTwoEn())
                .replace("__SIGNAL_THREE__", zh ? profile.signalThreeZh() : profile.signalThreeEn())
                .replace("__RECOMMEND_TITLE__", zh ? profile.recommendTitleZh() : profile.recommendTitleEn())
                .replace("__RECOMMEND_SUBTITLE__", zh ? profile.recommendSubtitleZh() : profile.recommendSubtitleEn())
                .replace("__PRIMARY_CARDS__", Integer.toString(primaryCards))
                .replace("__AUTHOR_FALLBACK__", zh ? profile.authorFallbackZh() : profile.authorFallbackEn())
                .replace("__LOCATION_FALLBACK__", zh ? profile.locationFallbackZh() : profile.locationFallbackEn())
                .replace("__CATEGORY_FALLBACK__", zh ? profile.categoryFallbackZh() : profile.categoryFallbackEn())
                .replace("__CARD_TITLE_FALLBACK__", zh ? profile.cardTitleFallbackZh() : profile.cardTitleFallbackEn())
                .replace("__TOPIC_FALLBACK__", zh ? profile.topicFallbackZh() : profile.topicFallbackEn())
                .replace("__TIME_FALLBACK__", zh ? "2小时前" : "2h ago")
                .replace("__FOLLOW_LABEL__", zh ? "关注" : "Follow")
                .replace("__DESCRIPTION__", description)
                .replace("__COVER_POOL__", coverPool)
                .replace("__AVATAR_POOL__", avatarPool)
                .replace("__SEEDED_FEED__", seededFeed)
                .replace("__HOT_TOPIC_TITLE__", zh ? profile.hotTopicTitleZh() : profile.hotTopicTitleEn())
                .replace("__HOT_TOPIC_HINT__", zh ? profile.hotTopicHintZh() : profile.hotTopicHintEn())
                .replace("__HOT_TOPICS__", hotTopics);
    }

    private String buildStructuredFeedFallbackComponent(ProjectManifest manifest, Route route, ProjectManifest.PageSpec pageSpec, ShapeSurfaceProfile profile) {
        ProjectManifest.DesignContract contract = manifest.getDesignContract();
        boolean zh = manifest.getMetaData() == null || !"EN".equalsIgnoreCase(manifest.getMetaData().getOrDefault("lang", "ZH"));
        int primaryCards = contract != null ? Math.max(contract.getMinPrimaryCards(), 4) : 4;
        String description = escapeHtml(pageSpec != null && pageSpec.getDescription() != null ? pageSpec.getDescription() : manifest.getUserIntent());
        String seededFeed = buildSeededFeedJson(zh, Math.max(primaryCards, 4), profile);
        String hotTopics = buildHotTopicsJson(zh, profile);
        String html = """
                <div x-show="hash === '#__ID__'" class="animate-fade-in pb-8 space-y-6 relative">
                  <!-- AI Hydration Indicator -->
                  <div class="pointer-events-none sticky top-4 z-30 mb-6 flex items-center gap-2 px-3 py-1.5 w-fit bg-slate-900/90 backdrop-blur shadow-lg border border-slate-700 rounded-full">
                    <span class="flex h-2 w-2 relative">
                      <span class="animate-ping absolute inline-flex h-full w-full rounded-full bg-indigo-400 opacity-75"></span>
                      <span class="relative inline-flex rounded-full h-2 w-2 bg-indigo-400"></span>
                    </span>
                    <span class="text-[10px] font-black text-white uppercase tracking-widest">AI Hydrating Details</span>
                  </div>
                  <section class="rounded-[28px] border border-slate-200 bg-white/95 p-6 shadow-sm">
                    <div class="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
                      <div class="space-y-3">
                        <span class="inline-flex items-center rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-600">__SURFACE_LABEL__</span>
                        <h1 class="max-w-4xl text-3xl font-black tracking-tight text-slate-900">__HERO_TITLE__</h1>
                        <p class="max-w-4xl text-sm leading-7 text-slate-600">__HERO_DESCRIPTION__</p>
                      </div>
                      <div class="flex flex-wrap gap-3 text-sm">
                        <button @click="activeSignal = activeSignal === 'saved' ? 'all' : 'saved'" :class="activeSignal === 'saved' ? 'bg-slate-900 text-white shadow-lg shadow-slate-200' : 'bg-white text-slate-700'" class="rounded-full px-4 py-2 font-semibold shadow-sm ring-1 ring-slate-200 transition-all">__SIGNAL_ONE__</button>
                        <button @click="activeSignal = activeSignal === 'hot' ? 'all' : 'hot'" :class="activeSignal === 'hot' ? 'bg-slate-900 text-white shadow-lg shadow-slate-200' : 'bg-white text-slate-700'" class="rounded-full px-4 py-2 font-semibold shadow-sm ring-1 ring-slate-200 transition-all">__SIGNAL_TWO__</button>
                        <button @click="activeSignal = activeSignal === 'media' ? 'all' : 'media'" :class="activeSignal === 'media' ? 'bg-slate-900 text-white shadow-lg shadow-slate-200' : 'bg-white text-slate-700'" class="rounded-full px-4 py-2 font-semibold shadow-sm ring-1 ring-slate-200 transition-all">__SIGNAL_THREE__</button>
                      </div>
                    </div>
                  </section>
                  <section class="grid gap-6 xl:grid-cols-[minmax(0,1fr)_320px]">
                    <div class="space-y-4">
                      <div class="flex items-end justify-between gap-4">
                        <div><h2 class="text-2xl font-black text-slate-900">__RECOMMEND_TITLE__</h2><p class="mt-1 text-sm text-slate-500">__RECOMMEND_SUBTITLE__</p></div>
                      </div>
                      <div class="overflow-hidden rounded-[28px] border border-slate-200 bg-white shadow-sm">
                        <template x-for='(item, index) in getFilteredFeed(__SEEDED_FEED__).slice(0, __PRIMARY_CARDS__)' :key="(item.id || item.title || index) + '-' + index">
                          <article @click="selectedItem = item; hash = '#detail'" class="cursor-pointer border-b border-slate-100 p-5 transition hover:bg-slate-50 last:border-b-0">
                            <div class="flex gap-4">
                              <div class="min-w-0 flex-1">
                                <div class="flex flex-wrap items-center gap-2 text-xs text-slate-500">
                                  <span class="font-semibold text-slate-700" x-text="item.author || item.username || item.creator || '__AUTHOR_FALLBACK__'"></span>
                                  <span>·</span>
                                  <span x-text="item.time || item.publishTime || '__TIME_FALLBACK__'"></span>
                                  <span>·</span>
                                  <span x-text="item.category || '__CATEGORY_FALLBACK__'"></span>
                                </div>
                                <h3 class="mt-2 text-xl font-black leading-snug text-slate-900" x-text="item.title || item.name || '__CARD_TITLE_FALLBACK__'"></h3>
                                <p class="mt-3 line-clamp-3 text-sm leading-6 text-slate-600" x-text="item.description || item.content || item.summary || '__DESCRIPTION__'"></p>
                                <div class="mt-4 flex flex-wrap items-center gap-3 text-xs text-slate-500">
                                  <span class="rounded-full bg-slate-100 px-3 py-1 font-semibold text-slate-700" x-text="item.mediaType || item.contentType || '__SIGNAL_THREE__'"></span>
                                  <template x-for="(tag, tagIndex) in ((Array.isArray(item.tags) && item.tags.length ? item.tags.slice(0, 3) : [item.topic || '__TOPIC_FALLBACK__']))" :key="tag + '-' + tagIndex">
                                    <span class="rounded-full bg-slate-100 px-3 py-1" x-text="'#' + tag"></span>
                                  </template>
                                </div>
                                <div class="mt-4 flex items-center gap-5 text-xs text-slate-500">
                                  <span x-text="'__SIGNAL_ONE__: ' + (item.collects || item.saves || '1.2k')"></span>
                                  <span x-text="'__SIGNAL_TWO__: ' + (item.comments || item.commentCount || '320')"></span>
                                  <span x-text="'__SIGNAL_THREE__: ' + (item.likes || item.likeCount || '2.4k')"></span>
                                </div>
                              </div>
                              <div class="hidden w-40 shrink-0 overflow-hidden rounded-2xl bg-slate-100 lg:block" x-show="item.cover || item.image || item.thumbUrl">
                                <img :src="item.cover || item.image || item.thumbUrl" class="h-full w-full object-cover" />
                              </div>
                            </div>
                          </article>
                        </template>
                      </div>
                    </div>
                    <aside class="space-y-4">
                      <section data-aux-section="true" class="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm">
                        <h3 class="text-lg font-black text-slate-900">__HOT_TOPIC_TITLE__</h3>
                        <p class="mt-1 text-xs text-slate-500">__HOT_TOPIC_HINT__</p>
                        <div class="mt-4 space-y-3">
                          <template x-for="topic in __HOT_TOPICS__" :key="topic">
                            <button @click="searchQuery = topic; activeSignal = 'hot'" class="w-full rounded-2xl bg-slate-50 px-4 py-3 text-left text-sm font-semibold text-slate-800 transition hover:bg-slate-100" x-text="'#' + topic"></button>
                          </template>
                        </div>
                      </section>
                    </aside>
                  </section>
                </div>
                """;

        return html.replace("__ID__", route.id)
                .replace("__SURFACE_LABEL__", zh ? profile.surfaceLabelZh() : profile.surfaceLabelEn())
                .replace("__HERO_TITLE__", zh ? profile.heroTitleZh() : profile.heroTitleEn())
                .replace("__HERO_DESCRIPTION__", zh ? profile.heroDescriptionZh() : profile.heroDescriptionEn())
                .replace("__SIGNAL_ONE__", zh ? profile.signalOneZh() : profile.signalOneEn())
                .replace("__SIGNAL_TWO__", zh ? profile.signalTwoZh() : profile.signalTwoEn())
                .replace("__SIGNAL_THREE__", zh ? profile.signalThreeZh() : profile.signalThreeEn())
                .replace("__RECOMMEND_TITLE__", zh ? profile.recommendTitleZh() : profile.recommendTitleEn())
                .replace("__RECOMMEND_SUBTITLE__", zh ? profile.recommendSubtitleZh() : profile.recommendSubtitleEn())
                .replace("__PRIMARY_CARDS__", Integer.toString(primaryCards))
                .replace("__AUTHOR_FALLBACK__", zh ? profile.authorFallbackZh() : profile.authorFallbackEn())
                .replace("__CATEGORY_FALLBACK__", zh ? profile.categoryFallbackZh() : profile.categoryFallbackEn())
                .replace("__CARD_TITLE_FALLBACK__", zh ? profile.cardTitleFallbackZh() : profile.cardTitleFallbackEn())
                .replace("__TOPIC_FALLBACK__", zh ? profile.topicFallbackZh() : profile.topicFallbackEn())
                .replace("__TIME_FALLBACK__", zh ? "2小时前" : "2h ago")
                .replace("__DESCRIPTION__", description)
                .replace("__SEEDED_FEED__", seededFeed)
                .replace("__HOT_TOPIC_TITLE__", zh ? profile.hotTopicTitleZh() : profile.hotTopicTitleEn())
                .replace("__HOT_TOPIC_HINT__", zh ? profile.hotTopicHintZh() : profile.hotTopicHintEn())
                .replace("__HOT_TOPICS__", hotTopics);
    }

    private ShapeSurfaceProfile buildShapeSurfaceProfile(ProjectManifest manifest) {
        ProjectManifest.DesignContract contract = manifest.getDesignContract();
        ProjectManifest.LayoutRhythm layout = contract != null && contract.getLayoutRhythm() != null ? contract.getLayoutRhythm() : ProjectManifest.LayoutRhythm.COMPACT_CARD;
        ProjectManifest.PrimaryGoal primaryGoal = contract != null && contract.getPrimaryGoal() != null ? contract.getPrimaryGoal() : ProjectManifest.PrimaryGoal.READ;
        ProjectManifest.UiTone uiTone = contract != null && contract.getUiTone() != null ? contract.getUiTone() : ProjectManifest.UiTone.PROFESSIONAL;
        ProjectManifest.MediaWeight mediaWeight = contract != null && contract.getMediaWeight() != null ? contract.getMediaWeight() : ProjectManifest.MediaWeight.MIXED;
        boolean discoveryLikeSurface = primaryGoal == ProjectManifest.PrimaryGoal.DISCOVER
                && (uiTone == ProjectManifest.UiTone.LIVELY
                || uiTone == ProjectManifest.UiTone.PLAZA
                || mediaWeight == ProjectManifest.MediaWeight.VISUAL_HEAVY);

        if (layout == ProjectManifest.LayoutRhythm.WATERFALL || mediaWeight == ProjectManifest.MediaWeight.VISUAL_HEAVY || discoveryLikeSurface) {
            return new ShapeSurfaceProfile(
                    layout,
                    List.of("推荐", "风格", "体验", "旅行", "生活"),
                    List.of("For you", "Style", "Experiences", "Travel", "Life"),
                    List.of("城市漫游", "今日灵感", "热门清单", "周末去处"),
                    List.of("City walk", "Inspiration", "Hot lists", "Weekend ideas"),
                    "视觉内容社区", "Visual discovery community",
                    "今天值得收藏的灵感", "Inspiration worth saving today",
                    "先看内容质感，再决定收藏、关注和继续浏览。",
                    "Start from visual quality, then save, follow, and keep exploring.",
                    "发现内容", "Discover picks",
                    "围绕你的兴趣持续更新，优先呈现高质量的内容卡片。",
                    "Continuously updated around your interests with high-quality visual cards first.",
                    "高收藏", "Most saved",
                    "热度", "Trending",
                    "视频/图文", "Video/Photo",
                    "热门话题", "Hot topics",
                    "当前最活跃的内容线索", "What people are actively exploring now",
                    "LingNow 创作者", "LingNow creator",
                    "城市生活", "Local picks",
                    "生活方式", "Lifestyle",
                    "一条值得继续点开的内容", "A post worth opening",
                    "今日灵感", "Inspiration"
            );
        }

        if (layout == ProjectManifest.LayoutRhythm.THREAD || primaryGoal == ProjectManifest.PrimaryGoal.DISCUSS) {
            return new ShapeSurfaceProfile(
                    layout,
                    List.of("推荐", "最新", "精华", "版块", "问答"),
                    List.of("For you", "Latest", "Best", "Boards", "Q&A"),
                    List.of("热门帖子", "高质量回复", "最新更新", "技术问答"),
                    List.of("Hot threads", "Quality replies", "Latest updates", "Tech Q&A"),
                    "讨论型社区", "Discussion community",
                    "今天值得参与的讨论", "Discussions worth joining today",
                    "先看标题、回复热度和最后活跃，再决定进入讨论或继续追更。",
                    "Start from thread titles, reply heat, and recency before deciding where to join the discussion.",
                    "热门讨论", "Popular discussions",
                    "围绕当前话题、回帖活跃度和版块线索快速定位值得参与的帖子。",
                    "Navigate by thread activity, current topics, and board structure to find discussions worth joining.",
                    "高回复", "Most replies",
                    "最新更新", "Latest updates",
                    "深度主题", "Deep dives",
                    "当前热帖", "Current hot threads",
                    "按回帖活跃度和更新时间排序", "Ranked by reply activity and last update time",
                    "LingNow 楼主", "LingNow author",
                    "分区社区", "Community board",
                    "讨论主题", "Discussion",
                    "一个值得进入的帖子", "A thread worth opening",
                    "热议", "Discussion"
            );
        }

        return new ShapeSurfaceProfile(
                layout,
                List.of("推荐", "前端", "后端", "人工智能", "架构"),
                List.of("For you", "Frontend", "Backend", "AI", "Architecture"),
                List.of("AI 编程", "系统设计", "工程效率", "开源实践"),
                List.of("AI coding", "System design", "Engineering productivity", "Open source"),
                uiTone == ProjectManifest.UiTone.EDITORIAL ? "资讯内容社区" : "技术内容社区",
                uiTone == ProjectManifest.UiTone.EDITORIAL ? "Editorial knowledge community" : "Tech knowledge community",
                uiTone == ProjectManifest.UiTone.EDITORIAL ? "今天值得追踪的行业洞察" : "今天值得读的技术内容",
                uiTone == ProjectManifest.UiTone.EDITORIAL ? "Industry reads worth tracking today" : "Engineering reads worth your attention today",
                uiTone == ProjectManifest.UiTone.EDITORIAL
                        ? "聚焦新闻线索、栏目内容与关键判断，优先帮助用户快速抓住重要变化。"
                        : "聚焦工程实践、架构复盘与开发经验，优先帮助用户快速判断内容价值。",
                uiTone == ProjectManifest.UiTone.EDITORIAL
                        ? "Surface headline-worthy updates, editorial picks, and key shifts so users can grasp what matters quickly."
                        : "Focus on engineering practice, architecture reviews, and development insights so users can quickly judge value.",
                uiTone == ProjectManifest.UiTone.EDITORIAL ? "编辑精选" : "精选文章",
                uiTone == ProjectManifest.UiTone.EDITORIAL ? "Editor's picks" : "Featured reads",
                uiTone == ProjectManifest.UiTone.EDITORIAL
                        ? "围绕栏目、时间和洞察价值组织阅读流。"
                        : "围绕主题、作者与可收藏价值组织阅读流。",
                uiTone == ProjectManifest.UiTone.EDITORIAL
                        ? "Organize reading flow around channels, recency, and editorial significance."
                        : "Organize reading flow around topics, authors, and bookmark value.",
                "高收藏", "Most bookmarked",
                uiTone == ProjectManifest.UiTone.EDITORIAL ? "最新动态" : "热议讨论",
                uiTone == ProjectManifest.UiTone.EDITORIAL ? "Latest updates" : "Active discussions",
                uiTone == ProjectManifest.UiTone.EDITORIAL ? "栏目类型" : "内容类型",
                uiTone == ProjectManifest.UiTone.EDITORIAL ? "Channel type" : "Content type",
                uiTone == ProjectManifest.UiTone.EDITORIAL ? "重要栏目" : "趋势话题",
                uiTone == ProjectManifest.UiTone.EDITORIAL ? "Key channels" : "Trending topics",
                uiTone == ProjectManifest.UiTone.EDITORIAL ? "当前最值得继续追踪的栏目与主题" : "今天开发者都在追这些方向",
                uiTone == ProjectManifest.UiTone.EDITORIAL ? "The channels and themes worth following next" : "What developers are following right now",
                "LingNow 作者", "LingNow author",
                uiTone == ProjectManifest.UiTone.EDITORIAL ? "编辑栏目" : "技术社区",
                uiTone == ProjectManifest.UiTone.EDITORIAL ? "Editorial desk" : "Tech community",
                uiTone == ProjectManifest.UiTone.EDITORIAL ? "栏目" : "工程实践",
                uiTone == ProjectManifest.UiTone.EDITORIAL ? "Channel" : "Engineering",
                uiTone == ProjectManifest.UiTone.EDITORIAL ? "一条值得继续跟进的资讯" : "一篇值得收藏的技术文章",
                uiTone == ProjectManifest.UiTone.EDITORIAL ? "A report worth following" : "A technical article worth bookmarking",
                uiTone == ProjectManifest.UiTone.EDITORIAL ? "行业洞察" : "架构复盘",
                uiTone == ProjectManifest.UiTone.EDITORIAL ? "Industry insight" : "Architecture review"
        );
    }

    private String safeEnumName(Enum<?> value) {
        return value == null ? "" : value.name();
    }

    private boolean containsAny(String source, String... tokens) {
        if (source == null) {
            return false;
        }
        for (String token : tokens) {
            if (source.contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Phase 7.3.2: Generate a dedicated Detail Modal component for the OVERLAY slot.
     * This closes the interaction loop: card click → selectedItem set → modal renders.
     */
    private String generateDetailModal(ProjectManifest manifest, Route overlayRoute, String lang) {
        String handbook = loadHandbook();
        String langInstruction = "ZH".equalsIgnoreCase(lang) ? "Use Chinese labels." : "Use English labels.";

        String systemPrompt = String.format("""
                %s
                
                YOUR GOAL: Generate a beautiful FULL-SCREEN Detail Modal Panel for a community prototype.
                
                RULES:
                - The modal is ALREADY wrapped by `<template x-if="selectedItem">` in the shell. DO NOT add x-if.
                - Render fields from `selectedItem` using Alpine.js expressions: `x-text="selectedItem.fieldName"`.
                - MUST include: large hero image/cover, title, author info (avatar + name), stats (likes, comments, collects), full body content/description, tags, a comment input area, and a close button.
                - Close button MUST: @click="selectedItem = null; hash = '#pg1'".
                - Style: premium card, rounded-3xl, bg-white, shadow-2xl, overflow-y-auto max-h-[90vh].
                - %s
                OUTPUT: Return ONLY a raw HTML snippet (no ```html markers). Start with a <div class="relative ...">
                """, handbook, langInstruction);

        String userPrompt = String.format(
                "User Intent: %s\nMock Data sample: %s\nGenerate the detail modal inner content.",
                manifest.getUserIntent(), manifest.getMockData());

        try {
            String response = llmClient.chat(systemPrompt, userPrompt);
            // parseHtmlSnippet handles both raw HTML and ```html``` wrapped responses
            return parseHtmlSnippet(response);
        } catch (java.io.IOException e) {
            log.error("Failed to generate detail modal", e);
            return buildFallbackDetailModal(lang);
        }
    }

    private String buildFallbackDetailModal(String lang) {
        boolean zh = !"EN".equalsIgnoreCase(lang);
        return """
                <div class="relative bg-white rounded-3xl p-8 max-w-3xl mx-auto shadow-2xl">
                  <button @click="selectedItem = null; hash = '#pg1'" class="absolute top-4 right-4 text-slate-400 hover:text-slate-700 text-2xl">&times;</button>
                  <div class="overflow-hidden rounded-2xl bg-slate-100">
                    <img :src="selectedItem.cover || selectedItem.image || selectedItem.thumbUrl" class="h-72 w-full object-cover" />
                  </div>
                  <div class="mt-6 flex flex-wrap items-center gap-3 text-sm text-slate-500">
                    <span x-text="selectedItem.author || selectedItem.作者 || 'LingNow'"></span>
                    <span>·</span>
                    <span x-text="selectedItem.time || selectedItem.publishTime || '%s'"></span>
                  </div>
                  <h2 class="mt-3 text-3xl font-black text-slate-900" x-text="selectedItem.title || selectedItem.标题 || '%s'"></h2>
                  <p class="mt-4 text-slate-700 leading-relaxed" x-text="selectedItem.content || selectedItem.内容 || selectedItem.description || ''"></p>
                  <div class="mt-6 flex flex-wrap gap-2">
                    <template x-for="tag in (selectedItem.tags || [])" :key="tag">
                      <span class="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-600" x-text="'#' + tag"></span>
                    </template>
                  </div>
                  <div class="mt-6 grid grid-cols-3 gap-3 rounded-2xl bg-slate-50 p-4 text-sm text-slate-500">
                    <div><div class="font-semibold text-slate-900" x-text="selectedItem.likes || selectedItem.likeCount || '0'"></div><div>%s</div></div>
                    <div><div class="font-semibold text-slate-900" x-text="selectedItem.collects || selectedItem.saves || '0'"></div><div>%s</div></div>
                    <div><div class="font-semibold text-slate-900" x-text="selectedItem.comments || selectedItem.commentCount || '0'"></div><div>%s</div></div>
                  </div>
                </div>
                """.formatted(
                zh ? "刚刚" : "just now",
                zh ? "详情" : "Detail",
                zh ? "点赞" : "Likes",
                zh ? "收藏" : "Saves",
                zh ? "评论" : "Comments");
    }

    /**
     * Internal Route metadata
     */
    private record ShapeSurfaceProfile(
            ProjectManifest.LayoutRhythm layoutRhythm,
            List<String> categoriesZh,
            List<String> categoriesEn,
            List<String> hotTopicsZh,
            List<String> hotTopicsEn,
            String surfaceLabelZh,
            String surfaceLabelEn,
            String heroTitleZh,
            String heroTitleEn,
            String heroDescriptionZh,
            String heroDescriptionEn,
            String recommendTitleZh,
            String recommendTitleEn,
            String recommendSubtitleZh,
            String recommendSubtitleEn,
            String signalOneZh,
            String signalOneEn,
            String signalTwoZh,
            String signalTwoEn,
            String signalThreeZh,
            String signalThreeEn,
            String hotTopicTitleZh,
            String hotTopicTitleEn,
            String hotTopicHintZh,
            String hotTopicHintEn,
            String authorFallbackZh,
            String authorFallbackEn,
            String locationFallbackZh,
            String locationFallbackEn,
            String categoryFallbackZh,
            String categoryFallbackEn,
            String cardTitleFallbackZh,
            String cardTitleFallbackEn,
            String topicFallbackZh,
            String topicFallbackEn
    ) {
    }

    private record Route(String id, String name, String navType) {
    }

    /**
     * M6: Iterative Redesign - Refines the existing prototype based on user instructions.
     */
    public void redesign(ProjectManifest manifest, String instructions) {
        log.info("Designer is refining prototype based on instructions: {}", instructions);
        String existingHtml = manifest.getPrototypeHtml();

        String systemPrompt = "You are an expert UI/UX Refinement Agent. You will receive an existing SPA prototype (with Hash-based Alpine.js logic) and modification instructions.\n"
                + "YOUR GOAL: Evolve the existing design without breaking the 'mindmap' node coverage or 'mockData' consistency.\n"
                + "1. MAINTAIN FLOW: Ensure navigation (Hash changes) and state management still works perfectly.\n"
                + "2. CONSISTENT AESTHETICS: Keep the high-density list flow and technical blog aesthetics.\n"
                + "3. OUTPUT: Respond ONLY with the raw HTML string wrapped in ```html and ``` markers. DO NOT wrap it in JSON.";

        String lang = manifest.getMetaData() != null ? manifest.getMetaData().getOrDefault("lang", "EN") : "EN";
        if ("ZH".equalsIgnoreCase(lang)) {
            systemPrompt += "CRITICAL: If you add new UI elements or update texts, USE CHINESE.";
        }

        String userPrompt = String.format("Existing HTML: \n%s\n\nModification Instructions: %s", existingHtml, instructions);
                
        try {
            String response = llmClient.chat(systemPrompt, userPrompt);
            String html = parseHtmlSnippet(response);
            manifest.setPrototypeHtml(html);
            log.info("Prototype refinement complete ({} chars).", html.length());
        } catch (Exception e) {
            log.error("Prototype refinement failed", e);
            throw new RuntimeException("UI Redesign phase failed: " + e.getMessage());
        }
    }

    private String parseHtmlSnippet(String response) {
        if (response == null) return "";
        try {
            int startIndex = response.indexOf("```html");
            if (startIndex != -1) {
                startIndex += 7; // Length of ```html
                int endIndex = response.lastIndexOf("```");
                if (endIndex != -1 && endIndex > startIndex) {
                    return normalizeHtmlFragment(response.substring(startIndex, endIndex));
                }
            }
            // Fallback: If no markers found, but it might be pure HTML anyway
            if (response.trim().startsWith("<")) {
                return normalizeHtmlFragment(response);
            }
            log.warn("No valid HTML code block found in response.");
            return normalizeHtmlFragment(response);
        } catch (Exception e) {
            log.warn("Failed to extract HTML snippet from LLM output. Returning raw response.");
            return normalizeHtmlFragment(response);
        }
    }
}
