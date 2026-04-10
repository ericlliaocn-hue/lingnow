package cc.lingnow.service;

import cc.lingnow.llm.LlmClient;
import cc.lingnow.model.ProjectManifest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${llm.timeout-seconds:600}")
    private int llmTimeoutSeconds;

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
            List<Route> componentRoutes = primaryRoutes.stream().limit(6).toList();
            int componentConcurrency = Math.min(2, Math.max(1, componentRoutes.size()));
            log.info("Step 2: Generating Feature Components (bounded concurrency: {})...", componentConcurrency);
            List<java.util.concurrent.CompletableFuture<String>> futures = new ArrayList<>();
            StringBuilder contentSlots = new StringBuilder();
            java.util.concurrent.ExecutorService componentExecutor = java.util.concurrent.Executors.newFixedThreadPool(componentConcurrency);
            try {
                for (Route route : componentRoutes) {
                    ProjectManifest.PageSpec pageSpec = findPageSpec(manifest, route);
                    futures.add(java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                        try {
                            log.info("[Designer] Starting component generation for: {}", route.id);
                            String rawHtml = generateComponent(manifest, route, pageSpec, lang);
                            return ensureRenderableComponent(manifest, route, pageSpec, rawHtml);
                        } catch (Exception e) {
                            log.error("[Designer] Component generation failed for {}: {}", route.id, e.getMessage());
                            return buildFallbackComponent(manifest, route, pageSpec);
                        }
                    }, componentExecutor));
                }

                int componentTimeoutSeconds = componentWaitTimeoutSeconds();
                for (java.util.concurrent.CompletableFuture<String> future : futures) {
                    try {
                        contentSlots.append(future.get(componentTimeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)).append("\n");
                    } catch (Exception e) {
                        log.error("[Designer] Feature component future timed out or failed", e);
                    }
                }
            } finally {
                componentExecutor.shutdownNow();
            }

            // STEP 2b: Generate Detail Modal Component (OVERLAY slot)
            log.info("Step 2b: Generating Detail Modal for OVERLAY routes...");
            String homeRouteId = firstPrimaryRouteId(primaryRoutes);
            String modalHtml = deterministicContentFirst
                    ? buildFallbackDetailModal(lang, homeRouteId)
                    : generateDetailModal(manifest, overlayRoutes.get(0), lang, homeRouteId);

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
        ShellCopy shellCopy = buildShellCopy(manifest, contentFirst);

        // Phase 1: Build the Shell (Instant Frame)
        String shell = applyShellCopy(template, shellCopy)
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

        String modalHtml = buildFallbackDetailModal(lang, firstPrimaryRouteId(primaryRoutes));
        String finalHtml = shell
                .replace("{{MOCK_DATA}}", "[]")
                .replace("{{MODAL_SLOT}}", modalHtml)
                .replace("{{CONTENT_SLOTS}}", contentSlots.toString())
                .replace("{{CONTENT_SLOT}}", contentSlots.toString());

        manifest.setPrototypeHtml(finalHtml);
    }

    private int componentWaitTimeoutSeconds() {
        return Math.max(60, llmTimeoutSeconds);
    }

    private String firstPrimaryRouteId(List<Route> primaryRoutes) {
        if (primaryRoutes == null || primaryRoutes.isEmpty() || primaryRoutes.get(0).id == null || primaryRoutes.get(0).id.isBlank()) {
            return "pg1";
        }
        return primaryRoutes.get(0).id;
    }

    private String applyShellCopy(String template, ShellCopy shellCopy) {
        return template
                .replace("{{SEARCH_PLACEHOLDER}}", escapeHtml(shellCopy.searchPlaceholder()))
                .replace("{{PUBLISH_LABEL}}", escapeHtml(shellCopy.publishLabel()))
                .replace("{{POST_TITLE}}", escapeHtml(shellCopy.postTitle()))
                .replace("{{POST_PLACEHOLDER}}", escapeHtml(shellCopy.postPlaceholder()))
                .replace("{{POST_SUBMIT_LABEL}}", escapeHtml(shellCopy.postSubmitLabel()));
    }

    private ShellCopy buildShellCopy(ProjectManifest manifest, boolean contentFirst) {
        boolean zh = manifest.getMetaData() == null || !"EN".equalsIgnoreCase(manifest.getMetaData().getOrDefault("lang", "ZH"));
        String intent = manifest.getUserIntent() != null ? manifest.getUserIntent().toLowerCase(Locale.ROOT) : "";
        if (isPhotographyIntent(intent)) {
            return new ShellCopy(
                    zh ? "搜索摄影师、作品风格、城市或可约档期..." : "Search photographers, styles, cities, or availability...",
                    zh ? "发布作品" : "Publish work",
                    zh ? "发布作品与可约档期" : "Publish work and availability",
                    zh ? "介绍拍摄风格、服务城市、可预约档期或套餐亮点..." : "Describe your shooting style, service city, availability, or package highlights...",
                    zh ? "更新作品" : "Publish"
            );
        }
        if (containsAny(intent, "预约", "预订", "booking", "appointment", "inquiry", "询价", "咨询", "服务", "客户")) {
            return new ShellCopy(
                    zh ? "搜索服务、案例、城市或可预约时间..." : "Search services, cases, cities, or available times...",
                    zh ? "发布服务" : "Publish service",
                    zh ? "发布服务与可预约时间" : "Publish service and availability",
                    zh ? "写下服务亮点、可服务范围、报价线索或预约说明..." : "Describe service highlights, scope, pricing hints, or booking notes...",
                    zh ? "更新服务" : "Publish"
            );
        }
        if (contentFirst) {
            return new ShellCopy(
                    zh ? "发现你感兴趣的话题..." : "Discover topics you care about...",
                    zh ? "发布" : "Post",
                    zh ? "发布新内容" : "Create a new post",
                    zh ? "写点什么分享你的灵感..." : "Share a thought, note, or inspiration...",
                    zh ? "立即发布" : "Post now"
            );
        }
        return new ShellCopy(
                zh ? "搜索功能、客户、订单或关键内容..." : "Search features, customers, orders, or key content...",
                zh ? "新建" : "Create",
                zh ? "新建内容" : "Create item",
                zh ? "补充标题、说明、状态或下一步动作..." : "Add a title, description, status, or next action...",
                zh ? "保存" : "Save"
        );
    }

    private boolean isPhotographyIntent(String intent) {
        return containsAny(intent, "摄影", "摄影师", "拍摄", "约拍", "photo", "photograph", "photographer", "portfolio", "档期", "作品展示");
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
        ShellCopy shellCopy = buildShellCopy(manifest, contentFirst);
        String handbook = loadHandbook();

        if (contentFirst) {
            String safeLogo = "<span class=\"text-xl font-bold text-rose-500\">" + (manifest.getOverview() != null ? manifest.getOverview() : "LingNow") + "</span>";
            return applyShellCopy(template, shellCopy)
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

            String shell = applyShellCopy(template, shellCopy)
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
            return applyShellCopy(template, shellCopy)
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
        if (isPhotographyIntent(manifest.getUserIntent() != null ? manifest.getUserIntent().toLowerCase(Locale.ROOT) : "")) {
            return buildPhotographyFallbackComponent(manifest, route, pageSpec);
        }
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

    private String buildPhotographyFallbackComponent(ProjectManifest manifest, Route route, ProjectManifest.PageSpec pageSpec) {
        boolean zh = manifest.getMetaData() == null || !"EN".equalsIgnoreCase(manifest.getMetaData().getOrDefault("lang", "ZH"));
        String routeKey = ((route.id == null ? "" : route.id) + " " + (route.name == null ? "" : route.name)).toLowerCase(Locale.ROOT);
        String title = photographyRouteLabel(route, zh);
        if (containsAny(routeKey, "availability", "档期", "calendar")) {
            return buildPhotographyAvailabilityComponent(route.id, title, zh);
        }
        if (containsAny(routeKey, "inquiries", "询价", "leads", "客户")) {
            return buildPhotographyInquiryComponent(route.id, title, zh);
        }
        if (containsAny(routeKey, "orders", "订单", "booking")) {
            return buildPhotographyOrdersComponent(route.id, title, zh);
        }
        if (containsAny(routeKey, "photographers", "摄影师")) {
            return buildPhotographyDirectoryComponent(route.id, title, zh);
        }
        return buildPhotographyDiscoverComponent(manifest, route.id, title, zh);
    }

    private String buildPhotographyDiscoverComponent(ProjectManifest manifest, String routeId, String title, boolean zh) {
        ShapeSurfaceProfile profile = buildShapeSurfaceProfile(manifest);
        String feed = buildPhotographySeededFeedJson(zh, 6);
        return """
                <div x-show="hash === '#__ID__'" class="min-h-screen animate-fade-in bg-slate-50 pb-16">
                  <section class="rounded-[36px] bg-slate-950 p-8 text-white shadow-2xl">
                    <div class="grid gap-8 xl:grid-cols-[minmax(0,1.2fr)_360px] xl:items-end">
                      <div>
                        <span class="inline-flex rounded-full bg-white/10 px-3 py-1 text-xs font-bold text-rose-200">__BADGE__</span>
                        <h1 class="mt-5 max-w-3xl text-4xl font-black leading-tight tracking-tight">__HERO__</h1>
                        <p class="mt-4 max-w-2xl text-sm leading-7 text-slate-300">__DESC__</p>
                        <div class="mt-6 flex flex-wrap gap-3">
                          <button class="rounded-full bg-rose-500 px-5 py-3 text-sm font-black text-white shadow-lg shadow-rose-500/30">__CTA_PRIMARY__</button>
                          <button class="rounded-full border border-white/15 px-5 py-3 text-sm font-bold text-white/85">__CTA_SECONDARY__</button>
                        </div>
                      </div>
                      <div class="grid grid-cols-3 gap-3 rounded-[28px] bg-white/10 p-4 backdrop-blur">
                        <div class="rounded-2xl bg-white/10 p-4"><div class="text-2xl font-black">286</div><div class="mt-1 text-xs text-slate-300">__STAT_A__</div></div>
                        <div class="rounded-2xl bg-white/10 p-4"><div class="text-2xl font-black">1.8k</div><div class="mt-1 text-xs text-slate-300">__STAT_B__</div></div>
                        <div class="rounded-2xl bg-white/10 p-4"><div class="text-2xl font-black">92%</div><div class="mt-1 text-xs text-slate-300">__STAT_C__</div></div>
                      </div>
                    </div>
                  </section>
                  <section class="mt-8 grid gap-6 xl:grid-cols-[minmax(0,1fr)_320px]">
                    <div>
                      <div class="mb-4 flex items-end justify-between">
                        <div><h2 class="text-2xl font-black text-slate-900">__TITLE__</h2><p class="mt-1 text-sm text-slate-500">__SUBTITLE__</p></div>
                      </div>
                      <div class="grid gap-5 md:grid-cols-2 2xl:grid-cols-3">
                        <template x-for='item in __FEED__' :key="item.id">
                          <article @click="selectedItem = item; hash = '#detail'" class="group cursor-pointer overflow-hidden rounded-[30px] border border-slate-200 bg-white shadow-sm transition hover:-translate-y-1 hover:shadow-2xl">
                            <div class="aspect-[4/3.2] overflow-hidden bg-slate-100"><img :src="item.cover" class="h-full w-full object-cover transition duration-500 group-hover:scale-105"/></div>
                            <div class="space-y-4 p-5">
                              <div class="flex items-center gap-3">
                                <img :src="item.avatar" class="h-10 w-10 rounded-full object-cover"/>
                                <div class="min-w-0 flex-1"><div class="truncate text-sm font-black text-slate-900" x-text="item.author"></div><div class="truncate text-xs text-slate-500" x-text="item.location"></div></div>
                                <span class="rounded-full bg-emerald-50 px-3 py-1 text-[10px] font-black text-emerald-600">__OPEN__</span>
                              </div>
                              <div><h3 class="line-clamp-2 text-lg font-black text-slate-900" x-text="item.title"></h3><p class="mt-2 line-clamp-2 text-sm leading-6 text-slate-500" x-text="item.description"></p></div>
                              <div class="flex flex-wrap gap-2"><template x-for="tag in item.tags.slice(0,3)" :key="tag"><span class="rounded-full bg-rose-50 px-3 py-1 text-xs font-bold text-rose-600" x-text="'#' + tag"></span></template></div>
                              <div class="flex items-center justify-between border-t border-slate-100 pt-4 text-xs text-slate-500"><span x-text="item.category"></span><button class="rounded-full bg-slate-950 px-4 py-2 font-black text-white">__INQUIRE__</button></div>
                            </div>
                          </article>
                        </template>
                      </div>
                    </div>
                    <aside class="space-y-4">
                      <section class="rounded-[30px] border border-slate-200 bg-white p-5 shadow-sm">
                        <h3 class="text-lg font-black text-slate-900">__SIDE_TITLE__</h3>
                        <div class="mt-4 space-y-3">
                          <div class="rounded-2xl bg-slate-50 p-4"><div class="text-sm font-black text-slate-900">__SIDE_A__</div><div class="mt-1 text-xs text-slate-500">__SIDE_A_DESC__</div></div>
                          <div class="rounded-2xl bg-slate-50 p-4"><div class="text-sm font-black text-slate-900">__SIDE_B__</div><div class="mt-1 text-xs text-slate-500">__SIDE_B_DESC__</div></div>
                          <div class="rounded-2xl bg-slate-50 p-4"><div class="text-sm font-black text-slate-900">__SIDE_C__</div><div class="mt-1 text-xs text-slate-500">__SIDE_C_DESC__</div></div>
                        </div>
                      </section>
                    </aside>
                  </section>
                </div>
                """
                .replace("__ID__", routeId)
                .replace("__BADGE__", zh ? profile.surfaceLabelZh() : profile.surfaceLabelEn())
                .replace("__HERO__", zh ? profile.heroTitleZh() : profile.heroTitleEn())
                .replace("__DESC__", zh ? profile.heroDescriptionZh() : profile.heroDescriptionEn())
                .replace("__CTA_PRIMARY__", zh ? "发起询价" : "Start inquiry")
                .replace("__CTA_SECONDARY__", zh ? "查看本周档期" : "View availability")
                .replace("__STAT_A__", zh ? "认证摄影师" : "Verified photographers")
                .replace("__STAT_B__", zh ? "客户询价" : "Client inquiries")
                .replace("__STAT_C__", zh ? "按期交付" : "On-time delivery")
                .replace("__TITLE__", title)
                .replace("__SUBTITLE__", zh ? "用作品质感、城市、档期和询价热度帮助客户快速筛选。" : "Filter by portfolio quality, city, availability, and inquiry heat.")
                .replace("__FEED__", feed)
                .replace("__OPEN__", zh ? "可约" : "Open")
                .replace("__INQUIRE__", zh ? "询价" : "Inquire")
                .replace("__SIDE_TITLE__", zh ? "预约转化路径" : "Booking conversion path")
                .replace("__SIDE_A__", zh ? "作品展示" : "Portfolio")
                .replace("__SIDE_A_DESC__", zh ? "先建立风格信任，再进入详情。" : "Build style trust before detail view.")
                .replace("__SIDE_B__", zh ? "档期预约" : "Availability")
                .replace("__SIDE_B_DESC__", zh ? "突出本周/本月可约时间。" : "Surface this week/month slots.")
                .replace("__SIDE_C__", zh ? "客户询价" : "Inquiry")
                .replace("__SIDE_C_DESC__", zh ? "把预算、城市、拍摄类型收进线索池。" : "Capture budget, city, and shoot type.");
    }

    private String buildPhotographyDirectoryComponent(String routeId, String title, boolean zh) {
        return """
                <div x-show="hash === '#__ID__'" class="min-h-screen animate-fade-in bg-slate-50 pb-16">
                  <section class="rounded-[32px] border border-slate-200 bg-white p-7 shadow-sm">
                    <h1 class="text-3xl font-black text-slate-900">__TITLE__</h1>
                    <p class="mt-3 max-w-3xl text-sm leading-7 text-slate-500">__DESC__</p>
                  </section>
                  <section class="mt-6 grid gap-4">
                    <template x-for="person in [
                      {name:'林屿影像工作室', city:'上海 / 杭州', style:'婚礼纪实 · 自然光', price:'¥4800 起', status:'本周可约', score:'4.9'},
                      {name:'北辰商业摄影', city:'北京', style:'品牌商业 · 产品棚拍', price:'¥6800 起', status:'3 天内可排期', score:'4.8'},
                      {name:'小满家庭影像', city:'广州', style:'亲子写真 · 外景抓拍', price:'¥2600 起', status:'周日可约', score:'4.7'},
                      {name:'阿澈人像计划', city:'成都', style:'人像约拍 · 胶片质感', price:'¥1600 起', status:'明晚可约', score:'4.9'}
                    ]" :key="person.name">
                      <article class="grid gap-4 rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm md:grid-cols-[1fr_auto] md:items-center">
                        <div><div class="text-xl font-black text-slate-900" x-text="person.name"></div><div class="mt-2 flex flex-wrap gap-2 text-xs text-slate-500"><span x-text="person.city"></span><span>·</span><span x-text="person.style"></span><span>·</span><span x-text="person.price"></span></div></div>
                        <div class="flex items-center gap-3"><span class="rounded-full bg-emerald-50 px-3 py-1 text-xs font-black text-emerald-600" x-text="person.status"></span><button class="rounded-full bg-slate-950 px-5 py-2 text-sm font-black text-white">__CTA__</button></div>
                      </article>
                    </template>
                  </section>
                </div>
                """
                .replace("__ID__", routeId)
                .replace("__TITLE__", title)
                .replace("__DESC__", zh ? "按城市、风格、价格与可约状态管理摄影师供给，帮助客户从作品浏览自然走向询价。" : "Manage photographer supply by city, style, price, and availability.")
                .replace("__CTA__", zh ? "发起询价" : "Inquire");
    }

    private String buildPhotographyAvailabilityComponent(String routeId, String title, boolean zh) {
        return """
                <div x-show="hash === '#__ID__'" class="min-h-screen animate-fade-in bg-slate-50 pb-16">
                  <section class="rounded-[32px] border border-slate-200 bg-white p-7 shadow-sm">
                    <h1 class="text-3xl font-black text-slate-900">__TITLE__</h1>
                    <p class="mt-3 text-sm leading-7 text-slate-500">__DESC__</p>
                  </section>
                  <section class="mt-6 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
                    <template x-for="slot in [
                      {day:'周五', time:'10:00 - 14:00', type:'商业棚拍', owner:'北辰商业摄影'},
                      {day:'周六', time:'15:00 - 18:00', type:'人像约拍', owner:'阿澈人像计划'},
                      {day:'周日', time:'09:30 - 12:30', type:'亲子写真', owner:'小满家庭影像'},
                      {day:'下周三', time:'全天', type:'婚礼纪实', owner:'林屿影像工作室'},
                      {day:'下周五', time:'19:00 - 21:00', type:'活动跟拍', owner:'峰会现场影像'},
                      {day:'本月余量', time:'18 个半天档', type:'多城市可约', owner:'平台摄影师池'}
                    ]" :key="slot.day + slot.time">
                      <article class="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm">
                        <div class="flex items-center justify-between"><span class="text-sm font-black text-rose-600" x-text="slot.day"></span><span class="rounded-full bg-emerald-50 px-3 py-1 text-xs font-black text-emerald-600">__OPEN__</span></div>
                        <div class="mt-4 text-2xl font-black text-slate-900" x-text="slot.time"></div>
                        <div class="mt-2 text-sm text-slate-500" x-text="slot.type"></div>
                        <div class="mt-5 rounded-2xl bg-slate-50 p-4 text-sm font-bold text-slate-700" x-text="slot.owner"></div>
                      </article>
                    </template>
                  </section>
                </div>
                """
                .replace("__ID__", routeId)
                .replace("__TITLE__", title)
                .replace("__DESC__", zh ? "把可预约档期作为转化核心，让客户不用来回沟通就能判断是否可约。" : "Make availability the conversion core so clients can decide before back-and-forth.")
                .replace("__OPEN__", zh ? "可预约" : "Open");
    }

    private String buildPhotographyInquiryComponent(String routeId, String title, boolean zh) {
        return """
                <div x-show="hash === '#__ID__'" class="min-h-screen animate-fade-in bg-slate-50 pb-16">
                  <section class="rounded-[32px] border border-slate-200 bg-white p-7 shadow-sm">
                    <h1 class="text-3xl font-black text-slate-900">__TITLE__</h1>
                    <p class="mt-3 text-sm leading-7 text-slate-500">__DESC__</p>
                  </section>
                  <section class="mt-6 grid gap-5 xl:grid-cols-3">
                    <template x-for="column in [
                      {name:'新询价', count:12, leads:['婚礼跟拍 · 上海 · ¥8000','亲子写真 · 广州 · ¥3000','商业主图 · 北京 · 待报价']},
                      {name:'方案沟通', count:8, leads:['旅拍婚纱 · 三亚 · 需路线','活动跟拍 · 深圳 · 双机位','人像约拍 · 成都 · 明晚']},
                      {name:'待确认', count:5, leads:['婚礼纪实 · 杭州 · 已发报价','品牌棚拍 · 北京 · 等合同','家庭影像 · 广州 · 等定金']}
                    ]" :key="column.name">
                      <div class="rounded-[30px] border border-slate-200 bg-white p-5 shadow-sm">
                        <div class="flex items-center justify-between"><h2 class="text-xl font-black text-slate-900" x-text="column.name"></h2><span class="rounded-full bg-slate-100 px-3 py-1 text-xs font-black text-slate-600" x-text="column.count"></span></div>
                        <div class="mt-4 space-y-3"><template x-for="lead in column.leads" :key="lead"><div class="rounded-2xl bg-slate-50 p-4 text-sm font-bold text-slate-700" x-text="lead"></div></template></div>
                      </div>
                    </template>
                  </section>
                </div>
                """
                .replace("__ID__", routeId)
                .replace("__TITLE__", title)
                .replace("__DESC__", zh ? "用看板承接客户询价，从预算、城市、类型到报价状态都能继续推进。" : "Track client inquiries from budget, city, shoot type, and quote status.");
    }

    private String buildPhotographyOrdersComponent(String routeId, String title, boolean zh) {
        return """
                <div x-show="hash === '#__ID__'" class="min-h-screen animate-fade-in bg-slate-50 pb-16">
                  <section class="rounded-[32px] border border-slate-200 bg-white p-7 shadow-sm">
                    <h1 class="text-3xl font-black text-slate-900">__TITLE__</h1>
                    <p class="mt-3 text-sm leading-7 text-slate-500">__DESC__</p>
                  </section>
                  <section class="mt-6 grid gap-4">
                    <template x-for="order in [
                      {id:'LN-2401', title:'上海婚礼纪实全天档', stage:'已付定金', date:'4月18日', amount:'¥12,800'},
                      {id:'LN-2402', title:'北京品牌产品棚拍', stage:'待确认脚本', date:'4月21日', amount:'¥18,600'},
                      {id:'LN-2403', title:'广州亲子外景写真', stage:'精修交付中', date:'4月25日', amount:'¥3,200'}
                    ]" :key="order.id">
                      <article class="grid gap-4 rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm md:grid-cols-[auto_1fr_auto] md:items-center">
                        <div class="rounded-2xl bg-slate-950 px-4 py-3 text-sm font-black text-white" x-text="order.id"></div>
                        <div><div class="text-lg font-black text-slate-900" x-text="order.title"></div><div class="mt-1 text-sm text-slate-500"><span x-text="order.date"></span><span class="mx-2">·</span><span x-text="order.stage"></span></div></div>
                        <div class="text-right"><div class="text-xl font-black text-slate-900" x-text="order.amount"></div><button class="mt-2 rounded-full bg-rose-500 px-4 py-2 text-xs font-black text-white">__CTA__</button></div>
                      </article>
                    </template>
                  </section>
                </div>
                """
                .replace("__ID__", routeId)
                .replace("__TITLE__", title)
                .replace("__DESC__", zh ? "把已成交预约继续推进到合同、定金、拍摄、选片和交付。" : "Move booked shoots through contract, deposit, shoot, selection, and delivery.")
                .replace("__CTA__", zh ? "查看进度" : "View");
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
        if (isPhotographySurface(profile)) {
            List<String> media = cover
                    ? List.of(
                    "https://images.unsplash.com/photo-1519741497674-611481863552?q=80&w=1200",
                    "https://images.unsplash.com/photo-1523438885200-e635ba2c371e?q=80&w=1200",
                    "https://images.unsplash.com/photo-1516035069371-29a1b244cc32?q=80&w=1200",
                    "https://images.unsplash.com/photo-1508214751196-bcfd4ca60f91?q=80&w=1200",
                    "https://images.unsplash.com/photo-1511285560929-80b456fea0bc?q=80&w=1200",
                    "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?q=80&w=1200",
                    "https://images.unsplash.com/photo-1519225421980-715cb0215aed?q=80&w=1200",
                    "https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?q=80&w=1200"
            )
                    : List.of(
                    "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?q=80&w=256",
                    "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?q=80&w=256",
                    "https://images.unsplash.com/photo-1494790108377-be9c29b29330?q=80&w=256",
                    "https://images.unsplash.com/photo-1504257432389-52343af06ae3?q=80&w=256",
                    "https://images.unsplash.com/photo-1534528741775-53994a69daeb?q=80&w=256",
                    "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?q=80&w=256",
                    "https://images.unsplash.com/photo-1531746020798-e6953c6e8e04?q=80&w=256",
                    "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?q=80&w=256"
            );
            try {
                return objectMapper.writeValueAsString(media);
            } catch (Exception e) {
                log.warn("[Designer] Failed to serialize photography media array", e);
                return "[]";
            }
        }
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
        if (isPhotographySurface(profile)) {
            return buildPhotographySeededFeedJson(zh, count);
        }
        if (profile.layoutRhythm() == ProjectManifest.LayoutRhythm.WATERFALL) {
            return buildLifestyleSeededFeedJson(zh, count);
        }
        return buildKnowledgeSeededFeedJson(zh, count);
    }

    private boolean isPhotographySurface(ShapeSurfaceProfile profile) {
        return profile != null && containsAny(
                (profile.surfaceLabelZh() + " " + profile.surfaceLabelEn() + " " + profile.categoryFallbackZh()).toLowerCase(Locale.ROOT),
                "摄影", "photography", "photographer");
    }

    private String buildPhotographySeededFeedJson(boolean zh, int count) {
        List<Map<String, Object>> cards = new ArrayList<>();
        cards.add(seedCard(
                "id", "photographer-1",
                "title", zh ? "城市纪实婚礼摄影：自然光、低打扰、48 小时预告片" : "Documentary wedding photography with natural light and a 48-hour preview",
                "author", zh ? "林屿影像工作室" : "Linyu Studio",
                "avatar", "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?q=80&w=256",
                "description", zh ? "主打真实情绪和现场叙事，上海/杭州本周末仍有半天档期，可先询价确认套系。" : "Natural documentary coverage for Shanghai and Hangzhou, with half-day slots still open this weekend.",
                "cover", "https://images.unsplash.com/photo-1519741497674-611481863552?q=80&w=1200",
                "location", zh ? "上海 · 本周可约" : "Shanghai · Available this week",
                "time", zh ? "刚更新档期" : "Availability updated",
                "category", zh ? "婚礼纪实" : "Wedding documentary",
                "mediaType", zh ? "作品集" : "Portfolio",
                "likes", "4.8w",
                "comments", "126",
                "collects", "2380",
                "tags", List.of(zh ? "婚礼" : "Wedding", zh ? "自然光" : "Natural light", zh ? "可约档期" : "Available")
        ));
        cards.add(seedCard(
                "id", "photographer-2",
                "title", zh ? "品牌商业棚拍：从视觉提案到成片交付一站式" : "Commercial studio shoots from visual proposal to final delivery",
                "author", zh ? "北辰商业摄影" : "Northstar Commercial",
                "avatar", "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?q=80&w=256",
                "description", zh ? "适合新品主图、品牌官网和社媒发布，支持询价后自动生成拍摄清单与报价范围。" : "Built for product launches, brand websites, and social campaigns with quote-ready shoot scopes.",
                "cover", "https://images.unsplash.com/photo-1516035069371-29a1b244cc32?q=80&w=1200",
                "location", zh ? "北京 · 商业棚" : "Beijing · Studio",
                "time", zh ? "1小时前" : "1h ago",
                "category", zh ? "商业拍摄" : "Commercial",
                "mediaType", zh ? "案例" : "Case study",
                "likes", "2.6w",
                "comments", "88",
                "collects", "1420",
                "tags", List.of(zh ? "产品摄影" : "Product", zh ? "品牌视觉" : "Brand", zh ? "报价快" : "Fast quote")
        ));
        cards.add(seedCard(
                "id", "photographer-3",
                "title", zh ? "亲子写真预约：外景抓拍 + 当日精修预览" : "Family portrait booking with outdoor candids and same-day previews",
                "author", zh ? "小满家庭影像" : "Mellow Family Photo",
                "avatar", "https://images.unsplash.com/photo-1494790108377-be9c29b29330?q=80&w=256",
                "description", zh ? "公园、社区和家中都能拍，按宝宝作息安排时段，询价时直接带出推荐套餐。" : "Outdoor, home, or neighborhood sessions scheduled around children's routines with package suggestions.",
                "cover", "https://images.unsplash.com/photo-1508214751196-bcfd4ca60f91?q=80&w=1200",
                "location", zh ? "广州 · 周日可约" : "Guangzhou · Sunday open",
                "time", zh ? "2小时前" : "2h ago",
                "category", zh ? "亲子写真" : "Family portraits",
                "mediaType", zh ? "档期" : "Availability",
                "likes", "1.9w",
                "comments", "73",
                "collects", "980",
                "tags", List.of(zh ? "亲子" : "Family", zh ? "外景" : "Outdoor", zh ? "当日预览" : "Same-day preview")
        ));
        cards.add(seedCard(
                "id", "photographer-4",
                "title", zh ? "人像约拍：复古街区、胶片质感、两小时轻写真" : "Portrait sessions with vintage streets, film tones, and two-hour shoots",
                "author", zh ? "阿澈人像计划" : "Ache Portrait Lab",
                "avatar", "https://images.unsplash.com/photo-1534528741775-53994a69daeb?q=80&w=256",
                "description", zh ? "适合头像、生日纪念和情侣轻写真，可查看完整客片后再发起询价。" : "Great for headshots, birthdays, and couple portraits with full galleries available before inquiry.",
                "cover", "https://images.unsplash.com/photo-1523438885200-e635ba2c371e?q=80&w=1200",
                "location", zh ? "成都 · 明晚可约" : "Chengdu · Tomorrow evening",
                "time", zh ? "3小时前" : "3h ago",
                "category", zh ? "人像约拍" : "Portrait",
                "mediaType", zh ? "客片" : "Gallery",
                "likes", "3.1w",
                "comments", "154",
                "collects", "1760",
                "tags", List.of(zh ? "人像" : "Portrait", zh ? "胶片感" : "Film tone", zh ? "轻写真" : "Casual shoot")
        ));
        cards.add(seedCard(
                "id", "photographer-5",
                "title", zh ? "活动跟拍团队：论坛、发布会、年会即时出图" : "Event coverage team for forums, launches, and annual meetings",
                "author", zh ? "峰会现场影像" : "LiveFrame Events",
                "avatar", "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?q=80&w=256",
                "description", zh ? "双机位覆盖签到、嘉宾、舞台和媒体图，支持半日/全天快速询价。" : "Two-camera coverage for check-in, speakers, stage, and press images with half-day or full-day quotes.",
                "cover", "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?q=80&w=1200",
                "location", zh ? "深圳 · 企业服务" : "Shenzhen · Corporate",
                "time", zh ? "5小时前" : "5h ago",
                "category", zh ? "活动跟拍" : "Event coverage",
                "mediaType", zh ? "服务" : "Service",
                "likes", "1.4w",
                "comments", "61",
                "collects", "840",
                "tags", List.of(zh ? "发布会" : "Launch", zh ? "即时出图" : "Fast delivery", zh ? "双机位" : "Two cameras")
        ));
        cards.add(seedCard(
                "id", "photographer-6",
                "title", zh ? "旅拍摄影师：海边婚纱与目的地轻婚礼" : "Destination photographer for seaside bridal and intimate weddings",
                "author", zh ? "南岛旅拍" : "South Island Photo",
                "avatar", "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?q=80&w=256",
                "description", zh ? "三亚、厦门、大理多地可约，作品页直接展示路线、天气备选和套餐差异。" : "Available in Sanya, Xiamen, and Dali with route, weather backup, and package comparisons.",
                "cover", "https://images.unsplash.com/photo-1519225421980-715cb0215aed?q=80&w=1200",
                "location", zh ? "三亚 · 旅拍档期" : "Sanya · Destination slots",
                "time", zh ? "昨天" : "Yesterday",
                "category", zh ? "旅拍婚纱" : "Destination bridal",
                "mediaType", zh ? "路线" : "Route",
                "likes", "5.2w",
                "comments", "241",
                "collects", "3160",
                "tags", List.of(zh ? "旅拍" : "Travel shoot", zh ? "婚纱" : "Bridal", zh ? "目的地" : "Destination")
        ));
        try {
            return objectMapper.writeValueAsString(cards.subList(0, Math.min(cards.size(), Math.max(count, 4))));
        } catch (Exception e) {
            log.warn("[Designer] Failed to serialize photography feed cards", e);
            return "[]";
        }
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
        boolean zh = manifest.getMetaData() == null || !"EN".equalsIgnoreCase(manifest.getMetaData().getOrDefault("lang", "ZH"));
        boolean photographyIntent = isPhotographyIntent(manifest.getUserIntent() != null ? manifest.getUserIntent().toLowerCase(Locale.ROOT) : "");
        for (Route route : routes) {
            String label = escapeHtml(photographyIntent ? photographyRouteLabel(route, zh) : route.name);
            fallbackNav.append(String.format(
                    "<a @click=\"hash='#%s'\" :class=\"hash==='#%s'?'bg-rose-50 text-rose-600 font-semibold':''\" class=\"flex items-center gap-3 px-4 py-2.5 rounded-xl text-slate-700 hover:bg-slate-100 transition-all text-sm\">%s</a>\n",
                    route.id, route.id, label));
        }
        return fallbackNav.toString();
    }

    private String photographyRouteLabel(Route route, boolean zh) {
        String routeKey = ((route.id == null ? "" : route.id) + " " + (route.name == null ? "" : route.name)).toLowerCase(Locale.ROOT);
        if (containsAny(routeKey, "discover", "home", "作品", "发现")) return zh ? "发现作品" : "Discover";
        if (containsAny(routeKey, "photographers", "摄影师")) return zh ? "摄影师" : "Photographers";
        if (containsAny(routeKey, "availability", "档期", "calendar")) return zh ? "档期预约" : "Availability";
        if (containsAny(routeKey, "inquiries", "询价", "客户", "leads")) return zh ? "客户询价" : "Inquiries";
        if (containsAny(routeKey, "orders", "订单", "booking")) return zh ? "订单交付" : "Orders";
        return route.name == null || route.name.isBlank() ? (zh ? "业务页面" : "Page") : route.name;
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
        String color = profile.vibeColor();
        String accentColor = color.replace("-500", "").replace("-400", "");

        String html = """
                <div x-show="hash === '#__ID__'" class="animate-fade-in pb-8 space-y-6 relative">
                  <!-- AI Hydration Indicator -->
                  <div class="pointer-events-none sticky top-4 z-30 mb-6 flex items-center justify-between">
                    <div class="flex items-center gap-2 px-3 py-1.5 bg-__ACCENT__/90 backdrop-blur shadow-lg shadow-__ACCENT__/20 border border-__ACCENT__/30 rounded-full">
                      <span class="flex h-2 w-2 relative">
                        <span class="animate-ping absolute inline-flex h-full w-full rounded-full bg-white opacity-75"></span>
                        <span class="relative inline-flex rounded-full h-2 w-2 bg-white"></span>
                      </span>
                      <span class="text-[10px] font-black text-white uppercase tracking-widest">AI Polishing in Progress</span>
                    </div>
                  </div>
                  <section class="rounded-[32px] border border-slate-200 bg-white/95 p-6 shadow-sm">
                    <div class="flex flex-col gap-5 xl:flex-row xl:items-end xl:justify-between">
                      <div class="space-y-3">
                        <div class="flex flex-wrap items-center gap-3">
                          <span class="inline-flex items-center rounded-full bg-__ACCENT__/10 px-3 py-1 text-xs font-semibold text-__ACCENT__">__BADGE__</span>
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
                    <div class="space-y-4">
                      <div class="flex items-end justify-between gap-4">
                        <div><h2 class="text-2xl font-black text-slate-900">__RECOMMEND_TITLE__</h2><p class="mt-1 text-sm text-slate-500">__RECOMMEND_SUBTITLE__</p></div>
                      </div>
                      <div class="lingnow-waterfall columns-1 gap-5 md:columns-2 2xl:columns-3">
                        <template x-for='(item, index) in getFilteredFeed(__SEEDED_FEED__).slice(0, __PRIMARY_CARDS__)' :key="(item.id || item.title || index) + '-' + index">
                          <article @click="selectedItem = item; hash = '#detail'" class="lingnow-waterfall-card group mb-5 cursor-pointer break-inside-avoid overflow-hidden rounded-[32px] border border-slate-200 bg-white shadow-sm transition duration-300 hover:-translate-y-1 hover:shadow-2xl">
                            <div class="overflow-hidden bg-slate-100" :class="index % 5 === 0 ? 'aspect-[4/6]' : (index % 5 === 1 ? 'aspect-[4/5]' : (index % 5 === 2 ? 'aspect-[4/4.8]' : (index % 5 === 3 ? 'aspect-[4/5.4]' : 'aspect-[4/6.2]')))">
                              <img :src='item.cover || item.image || item.thumbUrl || __COVER_POOL__[index % __COVER_POOL__.length]' class="h-full w-full object-cover transition duration-500 group-hover:scale-105" />
                            </div>
                            <div class="space-y-3 p-4">
                              <div class="flex items-center gap-3">
                                <img :src='item.avatar || item.authorAvatar || __AVATAR_POOL__[index % __AVATAR_POOL__.length]' class="h-10 w-10 rounded-full border border-white object-cover shadow-sm" />
                                <div class="min-w-0 flex-1">
                                  <div class="truncate text-sm font-semibold text-slate-900" x-text="item.author || item.username || item.creator || '__AUTHOR_FALLBACK__'"></div>
                                  <div class="truncate text-[10px] text-slate-500"><span x-text="item.location || '__LOCATION_FALLBACK__'"></span><span class="mx-1">·</span><span x-text="item.time || item.publishTime || '__TIME_FALLBACK__'"></span></div>
                                </div>
                                <button class="ml-auto rounded-full bg-__ACCENT__/10 px-3 py-1 text-[10px] font-bold text-__ACCENT__">__FOLLOW_LABEL__</button>
                              </div>
                              <div>
                                <h3 class="line-clamp-2 text-base font-black text-slate-900" x-text="item.title || item.name || '__CARD_TITLE_FALLBACK__'"></h3>
                                <p class="mt-1 line-clamp-2 text-xs leading-5 text-slate-500" x-text="item.description || item.content || item.summary || '__DESCRIPTION__'"></p>
                              </div>
                              <div class="flex flex-wrap gap-2">
                                <template x-for="(tag, tagIndex) in ((Array.isArray(item.tags) && item.tags.length ? item.tags.slice(0, 2) : [item.topic || '__TOPIC_FALLBACK__']))" :key="tag + '-' + tagIndex">
                                  <span class="rounded-full bg-__ACCENT__/5 px-2 py-0.5 text-[10px] font-semibold text-__ACCENT__" x-text="'#' + tag"></span>
                                </template>
                              </div>
                              <div class="flex items-center justify-between pt-2 border-t border-slate-50 text-[10px] text-slate-400">
                                <div class="flex items-center gap-3">
                                  <span class="flex items-center gap-1"><span x-text="item.likes || '2.9w'"></span><span class="scale-75">❤️</span></span>
                                  <span class="flex items-center gap-1"><span x-text="item.comments || '1.6k'"></span><span class="scale-75">💬</span></span>
                                </div>
                                <span class="rounded-full bg-slate-900 text-white px-2 py-0.5 scale-90" x-text="item.mediaType || 'Video'"></span>
                              </div>
                            </div>
                          </article>
                        </template>
                      </div>
                    </div>
                    <aside class="space-y-4 xl:sticky xl:top-24">
                      <section data-aux-section="true" class="rounded-[32px] border border-slate-200 bg-white p-5 shadow-sm">
                        <div class="flex items-center justify-between">
                          <div><h3 class="text-lg font-black text-slate-900">__HOT_TOPIC_TITLE__</h3><p class="mt-1 text-xs text-slate-500">__HOT_TOPIC_HINT__</p></div>
                          <span class="rounded-full bg-__ACCENT__/10 px-3 py-1 text-xs font-semibold text-__ACCENT__">Hot</span>
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
                .replace("__ACCENT__", accentColor);

        return html.replace("__ID__", route.id)
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
        String color = profile.vibeColor();
        String accentColor = color.replace("-500", "").replace("-400", "");

        String html = """
                <div x-show="hash === '#__ID__'" class="animate-fade-in pb-8 space-y-6 relative">
                  <!-- AI Hydration Indicator -->
                  <div class="pointer-events-none sticky top-4 z-30 mb-6 flex items-center gap-2 px-3 py-1.5 w-fit bg-__ACCENT__/90 backdrop-blur shadow-lg border border-__ACCENT__/30 rounded-full">
                    <span class="flex h-2 w-2 relative">
                      <span class="animate-ping absolute inline-flex h-full w-full rounded-full bg-white opacity-75"></span>
                      <span class="relative inline-flex rounded-full h-2 w-2 bg-white"></span>
                    </span>
                    <span class="text-[10px] font-black text-white uppercase tracking-widest">AI Hydrating Details</span>
                  </div>
                  <section class="rounded-[32px] border border-slate-200 bg-white/95 p-6 shadow-sm">
                    <div class="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
                      <div class="space-y-3">
                        <span class="inline-flex items-center rounded-full bg-__ACCENT__/10 px-3 py-1 text-xs font-semibold text-__ACCENT__">__SURFACE_LABEL__</span>
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
                      <div class="overflow-hidden rounded-[32px] border border-slate-200 bg-white shadow-sm">
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
                                    <span class="rounded-full bg-__ACCENT__/5 px-3 py-1 text-__ACCENT__ font-semibold" x-text="'#' + tag"></span>
                                  </template>
                                </div>
                                <div class="mt-4 flex items-center gap-5 text-[11px] text-slate-400 font-medium">
                                  <span class="flex items-center gap-1">__SIGNAL_ONE__: <span class="text-slate-900" x-text="(item.collects || item.saves || '1.2k')"></span></span>
                                  <span class="flex items-center gap-1">__SIGNAL_TWO__: <span class="text-slate-900" x-text="(item.comments || item.commentCount || '320')"></span></span>
                                  <span class="flex items-center gap-1">__SIGNAL_THREE__: <span class="text-slate-900" x-text="(item.likes || item.likeCount || '2.4k')"></span></span>
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
                      <section data-aux-section="true" class="rounded-[32px] border border-slate-200 bg-white p-5 shadow-sm">
                        <div class="flex items-center justify-between mb-4">
                           <h3 class="text-lg font-black text-slate-900">__HOT_TOPIC_TITLE__</h3>
                           <span class="rounded-full bg-__ACCENT__/10 px-2 py-0.5 text-[10px] font-bold text-__ACCENT__">Trends</span>
                        </div>
                        <p class="mb-4 text-xs text-slate-500">__HOT_TOPIC_HINT__</p>
                        <div class="space-y-3">
                          <template x-for="topic in __HOT_TOPICS__" :key="topic">
                            <button @click="searchQuery = topic; activeSignal = 'hot'" class="w-full rounded-2xl bg-slate-50 px-4 py-3 text-left text-sm font-semibold text-slate-800 transition hover:bg-slate-100" x-text="'#' + topic"></button>
                          </template>
                        </div>
                      </section>
                    </aside>
                  </section>
                </div>
                """
                .replace("__ACCENT__", accentColor);

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
        String intent = manifest.getUserIntent() != null ? manifest.getUserIntent().toLowerCase(Locale.ROOT) : "";
        
        ProjectManifest.LayoutRhythm layout = contract != null && contract.getLayoutRhythm() != null ? contract.getLayoutRhythm() : ProjectManifest.LayoutRhythm.COMPACT_CARD;
        ProjectManifest.PrimaryGoal primaryGoal = contract != null && contract.getPrimaryGoal() != null ? contract.getPrimaryGoal() : ProjectManifest.PrimaryGoal.READ;
        ProjectManifest.UiTone uiTone = contract != null && contract.getUiTone() != null ? contract.getUiTone() : ProjectManifest.UiTone.PROFESSIONAL;
        ProjectManifest.MediaWeight mediaWeight = contract != null && contract.getMediaWeight() != null ? contract.getMediaWeight() : ProjectManifest.MediaWeight.MIXED;

        // Force Waterfall for known visual discovery intents
        boolean forceWaterfall = containsAny(intent, "小红书", "xiaohongshu", "瀑布流", "waterfall", "pinterest", "instagram", "发现", "discovery");
        if (forceWaterfall) {
            layout = ProjectManifest.LayoutRhythm.WATERFALL;
            mediaWeight = ProjectManifest.MediaWeight.VISUAL_HEAVY;
        }

        boolean discoveryLikeSurface = forceWaterfall || (primaryGoal == ProjectManifest.PrimaryGoal.DISCOVER
                && (uiTone == ProjectManifest.UiTone.LIVELY
                || uiTone == ProjectManifest.UiTone.PLAZA
                || mediaWeight == ProjectManifest.MediaWeight.VISUAL_HEAVY));

        String vibeColor = detectVibeColor(manifest);

        if (isPhotographyIntent(intent)) {
            layout = ProjectManifest.LayoutRhythm.WATERFALL;
            return new ShapeSurfaceProfile(
                    layout,
                    vibeColor,
                    List.of("推荐", "婚礼", "人像", "商业", "本地档期"),
                    List.of("For you", "Wedding", "Portrait", "Commercial", "Local slots"),
                    List.of("本周可约", "婚礼纪实", "商业棚拍", "亲子写真"),
                    List.of("Available this week", "Wedding documentary", "Commercial studio", "Family portraits"),
                    "摄影师接单平台", "Photography booking marketplace",
                    "找到风格合拍且档期可约的摄影师", "Find photographers whose style and availability fit",
                    "先看作品质感、服务城市和可约档期，再发起询价或收藏备选。",
                    "Start from portfolio quality, service city, and availability before sending an inquiry or saving options.",
                    "摄影师推荐", "Recommended photographers",
                    "围绕作品集、档期、城市和询价热度组织发现流，帮助客户快速做出预约判断。",
                    "Organize discovery around portfolios, availability, cities, and inquiry heat so clients can book faster.",
                    "可约档期", "Available slots",
                    "询价热度", "Inquiry heat",
                    "作品集", "Portfolio",
                    "热门服务", "Popular services",
                    "客户最常比较的拍摄类型与档期线索", "The shoot types and availability signals clients compare most",
                    "光影工作室", "Photo studio",
                    "本地可约", "Local availability",
                    "摄影服务", "Photography service",
                    "一位值得预约的摄影师", "A photographer worth booking",
                    "人像约拍", "Portrait booking"
            );
        }

        if (layout == ProjectManifest.LayoutRhythm.WATERFALL || mediaWeight == ProjectManifest.MediaWeight.VISUAL_HEAVY || discoveryLikeSurface) {
            return new ShapeSurfaceProfile(
                    layout,
                    vibeColor,
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
                    vibeColor,
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
                vibeColor,
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
    private String generateDetailModal(ProjectManifest manifest, Route overlayRoute, String lang, String homeRouteId) {
        String handbook = loadHandbook();
        String langInstruction = "ZH".equalsIgnoreCase(lang) ? "Use Chinese labels." : "Use English labels.";

        String systemPrompt = String.format("""
                %s
                
                YOUR GOAL: Generate a beautiful FULL-SCREEN Detail Modal Panel for a community prototype.
                
                RULES:
                - The modal is ALREADY wrapped by `<template x-if="selectedItem">` in the shell. DO NOT add x-if.
                - Render fields from `selectedItem` using Alpine.js expressions: `x-text="selectedItem.fieldName"`.
                - MUST include: large hero image/cover, title, author info (avatar + name), stats (likes, comments, collects), full body content/description, tags, a comment input area, and a close button.
                - Close button MUST: @click="selectedItem = null; hash = '#%s'".
                - Style: premium card, rounded-3xl, bg-white, shadow-2xl, overflow-y-auto max-h-[90vh].
                - %s
                OUTPUT: Return ONLY a raw HTML snippet (no ```html markers). Start with a <div class="relative ...">
                """, handbook, homeRouteId, langInstruction);

        String userPrompt = String.format(
                "User Intent: %s\nMock Data sample: %s\nGenerate the detail modal inner content.",
                manifest.getUserIntent(), manifest.getMockData());

        try {
            String response = llmClient.chat(systemPrompt, userPrompt);
            // parseHtmlSnippet handles both raw HTML and ```html``` wrapped responses
            return parseHtmlSnippet(response);
        } catch (java.io.IOException e) {
            log.error("Failed to generate detail modal", e);
            return buildFallbackDetailModal(lang, homeRouteId);
        }
    }

    private String buildFallbackDetailModal(String lang, String homeRouteId) {
        boolean zh = !"EN".equalsIgnoreCase(lang);
        return """
                <div class="relative bg-white rounded-3xl p-8 max-w-3xl mx-auto shadow-2xl">
                  <button @click="selectedItem = null; hash = '#%s'" class="absolute top-4 right-4 text-slate-400 hover:text-slate-700 text-2xl">&times;</button>
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
                homeRouteId,
                zh ? "刚刚" : "just now",
                zh ? "详情" : "Detail",
                zh ? "点赞" : "Likes",
                zh ? "收藏" : "Saves",
                zh ? "评论" : "Comments");
    }

    private String detectVibeColor(ProjectManifest manifest) {
        String intent = manifest.getUserIntent() != null ? manifest.getUserIntent().toLowerCase(Locale.ROOT) : "";
        if (containsAny(intent, "粉", "pink", "rose", "lively", "活泼", "小红书", "xhs", "xiaohongshu"))
            return "rose-500";
        if (containsAny(intent, "蓝", "blue", "ocean", "sky", "专业", "professional")) return "indigo-500";
        if (containsAny(intent, "绿", "green", "nature", "forest", "fresh", "清新")) return "emerald-500";
        if (containsAny(intent, "黄", "yellow", "gold", "warm", "温馨")) return "amber-500";
        if (containsAny(intent, "黑", "dark", "night", "luxury", "高级")) return "slate-900";
        return "indigo-500"; // Default
    }

    /**
     * Internal Route metadata
     */
    private record ShapeSurfaceProfile(
            ProjectManifest.LayoutRhythm layoutRhythm,
            String vibeColor,
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

    private record ShellCopy(
            String searchPlaceholder,
            String publishLabel,
            String postTitle,
            String postPlaceholder,
            String postSubmitLabel
    ) {
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
