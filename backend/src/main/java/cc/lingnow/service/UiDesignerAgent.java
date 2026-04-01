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

            // STEP 1: Generate App Shell
            log.info("Step 1: Generating Application Layout Shell with {} primary routes...", primaryRoutes.size());
            String shellHtml = generateShell(manifest, primaryRoutes, lang);

            // STEP 2: Generate Page Components
            log.info("Step 2: Generating Feature Components (Context Bridge Active)...");
            StringBuilder contentSlots = new StringBuilder();
            int count = 0;
            for (Route route : primaryRoutes) {
                if (count >= 6) {
                    log.info("Reached maximum of 6 primary pages. Skipping the rest.");
                    break;
                }
                ProjectManifest.PageSpec pageSpec = findPageSpec(manifest, route);
                log.info("Generating component for: {} (#{}) using Context: {}", route.name, route.id, (pageSpec != null));
                String componentHtml = ensureRenderableComponent(
                        manifest,
                        route,
                        pageSpec,
                        generateComponent(manifest, route, pageSpec, lang)
                );
                contentSlots.append(componentHtml).append("\n");
                count++;
            }

            // STEP 2b: Generate Detail Modal Component (OVERLAY slot)
            log.info("Step 2b: Generating Detail Modal for OVERLAY routes...");
            String modalHtml = generateDetailModal(manifest, overlayRoutes.get(0), lang);

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

            if (contentFirst) {
                sidebarHtml = buildFallbackPrimaryNav(manifest, routes, true);
                utilityHtml = "";
                personalHtml = "";
            } else if (sidebarHtml.isBlank()) {
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
        String benchmarkInstruction = contentCommunity
                ? """
                BENCHMARK MODE (content community):
                - Align to Xiaohongshu / Pinterest-style discovery surfaces instead of a dashboard or portal.
                - The main column MUST be the primary attention sink: dense, scrollable, image-first feed cards.
                - Prefer masonry / waterfall rhythm or visibly varied card heights over a rigid equal-height grid.
                - Keep auxiliary content light: at most 1-2 small supporting modules, never a giant strategy/control panel.
                - Category tabs belong in the shell's top strip; DO NOT render a second full category tab bar inside the hero/body.
                - DO NOT create a persistent left sidebar or internal portal navigation inside the page body; shell navigation already exists.
                - DO NOT add a second sticky search toolbar or duplicate publish/search strip inside the page body.
                - Keep any hero/intro compact and content-leading, not a massive landing-page billboard.
                - NEVER mention benchmark names or internal strategy language in visible copy (for example: 小红书, content-first, 灵感发现流, 内容优先布局).
                - NEVER echo the raw user request as page copy, and never use generic H1 text like 首页 / Home / Overview as the main headline.
                - Add a top category strip near the feed (for example: 推荐 / 穿搭 / 美食 / 旅行) and wire it to Alpine state like `activeCategory`.
                - Search, category tabs, and social filters like `高收藏` / `实时热议` MUST visibly change the card list. Use Alpine state such as `searchQuery`, `activeCategory`, and `activeSignal`.
                - Include a lightweight auth entry pattern in the shell/header (登录 / 注册 or equivalent) instead of showing a fully-signed-in avatar by default.
                - Each card should feel social: creator avatar/name, topic tag, save/like/comment cues, and authentic photography.
                - When mockData has cover/image/thumbUrl/avatar/gallery fields, USE them directly so the prototype shows realistic media.
                """
                : "";

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
        if (isContentFirstRoute(route)) {
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
            return visibleText.length() >= 120
                    && contentSignals > 0
                    && hasFeedInteraction
                    && articleCount >= minCards
                    && hasWaterfallRhythm
                    && hasInteractiveFiltering
                    && !hasInBodyCategoryStrip
                    && !hasPortalBias
                    && !hasInternalLanguageLeak(manifest, lower, visibleText);
        }
        return visibleText.length() >= 120 && contentSignals > 0;
    }

    private String buildFallbackComponent(ProjectManifest manifest, Route route, ProjectManifest.PageSpec pageSpec) {
        ProjectManifest.DesignContract contract = manifest.getDesignContract();
        boolean contentFirst = isContentFirst(manifest);
        boolean zh = manifest.getMetaData() == null || !"EN".equalsIgnoreCase(manifest.getMetaData().getOrDefault("lang", "ZH"));
        int primaryCards = contract != null ? Math.max(contract.getMinPrimaryCards(), 4) : 4;
        String title = escapeHtml(route.name);
        String description = escapeHtml(pageSpec != null && pageSpec.getDescription() != null
                ? pageSpec.getDescription()
                : manifest.getUserIntent());
        boolean communityRoute = contentFirst && isContentFirstRoute(route);
        String layoutBadge = zh ? "今日精选" : "Curated picks";
        String surfaceLabel = communityRoute ? (zh ? "生活方式社区" : "Lifestyle community") : title;
        String heroTitle = communityRoute ? (zh ? "今天的灵感，值得慢慢收藏" : "Fresh inspiration worth saving today") : title;
        String heroDescription = communityRoute
                ? (zh ? "从穿搭、旅行、探店到家居与生活方式内容，先刷真实内容，再决定收藏、关注与互动。" : "From style and travel to cafés and home ideas, browse real content first, then save, follow, and react.")
                : description;
        String followLabel = zh ? "关注" : "Follow";
        String recommendTitle = zh ? "为你推荐" : "For you";
        String recommendSubtitle = zh ? "按你的兴趣持续更新，先看内容，再决定收藏、关注与互动。" : "Continuously updated by interest so you can browse, save, follow, and react.";
        String socialSignalOne = zh ? "高收藏" : "Most saved";
        String socialSignalTwo = zh ? "实时热议" : "Live now";
        String socialSignalThree = zh ? "视频优先" : "Video first";
        String hotTopicTitle = zh ? "正在热议" : "Hot topics";
        String hotTopicHint = zh ? "今天大家都在刷这些关键词" : "What people are saving right now";
        String creatorPromptTitle = zh ? "创作者入口" : "Creator actions";
        String creatorPromptBody = zh ? "发布新内容、查看收藏反馈、继续完善详情页闭环。" : "Publish, review saves, and keep the detail flow tight.";
        String authorFallback = zh ? "LingNow 创作者" : "LingNow creator";
        String locationFallback = zh ? "城市生活" : "Local picks";
        String categoryFallback = zh ? "生活方式" : "Lifestyle";
        String cardTitleFallback = zh ? "一条值得收藏的灵感笔记" : "A post worth saving";
        String topicFallback = zh ? "今日灵感" : "Inspiration";
        String timeFallback = zh ? "2小时前" : "2h ago";
        String emptyStateTitle = zh ? "暂时没有命中内容" : "No posts match yet";
        String emptyStateHint = zh ? "换个分类、关键词或筛选方式，马上继续刷。" : "Try a different category, keyword, or signal to keep browsing.";
        String coverPool = buildRealMediaArrayJson(true);
        String avatarPool = buildRealMediaArrayJson(false);
        String seededFeed = buildSeededFeedJson(zh, Math.max(primaryCards, 6));
        String hotTopics = buildHotTopicsJson(zh);

        String component = """
                <div x-show="hash === '#__ID__'" class="animate-fade-in pb-8 space-y-6">
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
                          <span class="rounded-full border border-slate-200 bg-white px-4 py-2 text-sm text-slate-600">先刷真实内容</span>
                          <span class="rounded-full border border-slate-200 bg-white px-4 py-2 text-sm text-slate-600">即时筛选高收藏与热议</span>
                          <span class="rounded-full border border-slate-200 bg-white px-4 py-2 text-sm text-slate-600">打开详情继续收藏和互动</span>
                        </div>
                      </div>
                      <div class="flex flex-wrap gap-3 text-sm">
                        <button
                          @click="activeSignal = activeSignal === 'saved' ? 'all' : 'saved'"
                          :class="activeSignal === 'saved' ? 'bg-slate-900 text-white shadow-lg shadow-slate-200' : 'bg-white text-slate-700'"
                          class="rounded-full px-4 py-2 font-semibold shadow-sm ring-1 ring-slate-200 transition-all">__SIGNAL_ONE__</button>
                        <button
                          @click="activeSignal = activeSignal === 'hot' ? 'all' : 'hot'"
                          :class="activeSignal === 'hot' ? 'bg-slate-900 text-white shadow-lg shadow-slate-200' : 'bg-white text-slate-700'"
                          class="rounded-full px-4 py-2 font-semibold shadow-sm ring-1 ring-slate-200 transition-all">__SIGNAL_TWO__</button>
                        <button
                          @click="activeSignal = activeSignal === 'media' ? 'all' : 'media'"
                          :class="activeSignal === 'media' ? 'bg-slate-900 text-white shadow-lg shadow-slate-200' : 'bg-white text-slate-700'"
                          class="rounded-full px-4 py-2 font-semibold shadow-sm ring-1 ring-slate-200 transition-all">__SIGNAL_THREE__</button>
                        <span class="rounded-full bg-slate-900 px-4 py-2 font-semibold text-white" x-text="getFilteredFeed(__SEEDED_FEED__).length + '+'"></span>
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
                        <div class="hidden items-center gap-2 rounded-full bg-white px-3 py-2 shadow-sm ring-1 ring-slate-200 md:flex">
                          <span class="text-xs font-semibold text-slate-500">筛选结果</span>
                          <span class="rounded-full bg-slate-900 px-3 py-1 text-xs font-semibold text-white" x-text="getFilteredFeed(__SEEDED_FEED__).length"></span>
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
                                  <div class="truncate text-xs text-slate-500">
                                    <span x-text="item.location || '__LOCATION_FALLBACK__'"></span>
                                    <span class="mx-1">·</span>
                                    <span x-text="item.time || item.publishTime || '__TIME_FALLBACK__'"></span>
                                  </div>
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
                                <div class="space-y-1">
                                  <div class="font-semibold text-slate-900" x-text="item.likes || item.likeCount || '2.9w'"></div>
                                  <div>点赞</div>
                                </div>
                                <div class="space-y-1">
                                  <div class="font-semibold text-slate-900" x-text="item.comments || item.commentCount || '1.6k'"></div>
                                  <div>评论</div>
                                </div>
                                <div class="space-y-1">
                                  <div class="font-semibold text-slate-900" x-text="item.collects || item.saves || '8.4k'"></div>
                                  <div>收藏</div>
                                </div>
                              </div>
                            </div>
                          </article>
                        </template>
                        <template x-if="!getFilteredFeed(__SEEDED_FEED__).length">
                          <div class="rounded-[28px] border border-dashed border-slate-300 bg-white/80 p-8 text-center text-slate-500">
                            <div class="text-lg font-bold text-slate-800">__EMPTY_STATE_TITLE__</div>
                            <p class="mt-2 text-sm">__EMPTY_STATE_HINT__</p>
                          </div>
                        </template>
                      </div>
                    </div>
                
                    <aside class="space-y-4 xl:sticky xl:top-24">
                      <section data-aux-section="true" class="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm">
                        <div class="flex items-center justify-between">
                          <div>
                            <h3 class="text-lg font-black text-slate-900">__HOT_TOPIC_TITLE__</h3>
                            <p class="mt-1 text-xs text-slate-500">__HOT_TOPIC_HINT__</p>
                          </div>
                          <span class="rounded-full bg-rose-50 px-3 py-1 text-xs font-semibold text-rose-500">Hot</span>
                        </div>
                        <div class="mt-4 space-y-3">
                          <template x-for="topic in __HOT_TOPICS__" :key="topic">
                            <button
                              @click="searchQuery = topic; activeSignal = 'hot'"
                              class="w-full rounded-2xl bg-slate-50 px-4 py-3 text-left text-sm font-semibold text-slate-800 transition hover:bg-slate-100"
                              x-text="'#' + topic"></button>
                          </template>
                        </div>
                      </section>
                
                      <section data-aux-section="true" class="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm">
                        <h3 class="text-lg font-black text-slate-900">__CREATOR_PROMPT_TITLE__</h3>
                        <p class="mt-2 text-sm leading-7 text-slate-500">__CREATOR_PROMPT_BODY__</p>
                        <div class="mt-4 flex flex-wrap gap-2">
                          <span class="rounded-full bg-white px-3 py-1 text-xs font-semibold text-slate-600 ring-1 ring-slate-200">发布</span>
                          <span class="rounded-full bg-white px-3 py-1 text-xs font-semibold text-slate-600 ring-1 ring-slate-200">收藏反馈</span>
                          <span class="rounded-full bg-white px-3 py-1 text-xs font-semibold text-slate-600 ring-1 ring-slate-200">详情闭环</span>
                        </div>
                      </section>
                    </aside>
                  </section>
                </div>
                """;

        return component
                .replace("__ID__", route.id)
                .replace("__BADGE__", contentFirst ? layoutBadge : (zh ? "稳定导航布局" : "Stable navigation layout"))
                .replace("__TITLE__", title)
                .replace("__DESCRIPTION__", description)
                .replace("__SURFACE_LABEL__", surfaceLabel)
                .replace("__HERO_TITLE__", heroTitle)
                .replace("__HERO_DESCRIPTION__", heroDescription)
                .replace("__SIGNAL_ONE__", socialSignalOne)
                .replace("__SIGNAL_TWO__", socialSignalTwo)
                .replace("__SIGNAL_THREE__", socialSignalThree)
                .replace("__PRIMARY_CARDS__", Integer.toString(primaryCards))
                .replace("__RECOMMEND_TITLE__", recommendTitle)
                .replace("__RECOMMEND_SUBTITLE__", recommendSubtitle)
                .replace("__HOT_TOPIC_TITLE__", hotTopicTitle)
                .replace("__HOT_TOPIC_HINT__", hotTopicHint)
                .replace("__CREATOR_PROMPT_TITLE__", creatorPromptTitle)
                .replace("__CREATOR_PROMPT_BODY__", creatorPromptBody)
                .replace("__FOLLOW_LABEL__", followLabel)
                .replace("__AUTHOR_FALLBACK__", authorFallback)
                .replace("__LOCATION_FALLBACK__", locationFallback)
                .replace("__CATEGORY_FALLBACK__", categoryFallback)
                .replace("__CARD_TITLE_FALLBACK__", cardTitleFallback)
                .replace("__TOPIC_FALLBACK__", topicFallback)
                .replace("__TIME_FALLBACK__", timeFallback)
                .replace("__EMPTY_STATE_TITLE__", emptyStateTitle)
                .replace("__EMPTY_STATE_HINT__", emptyStateHint)
                .replace("__COVER_POOL__", coverPool)
                .replace("__AVATAR_POOL__", avatarPool)
                .replace("__SEEDED_FEED__", seededFeed)
                .replace("__HOT_TOPICS__", hotTopics);
    }

    private String buildRealMediaArrayJson(boolean cover) {
        List<String> media = cover
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

    private String buildHotTopicsJson(boolean zh) {
        List<String> topics = zh
                ? List.of("城市漫游", "今日妆容", "咖啡探店", "周末去哪儿")
                : List.of("City walk", "Makeup today", "Coffee spots", "Weekend picks");
        try {
            return objectMapper.writeValueAsString(topics);
        } catch (Exception e) {
            log.warn("[Designer] Failed to serialize hot topics", e);
            return "[]";
        }
    }

    private String buildSeededFeedJson(boolean zh, int count) {
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
        String homeRouteId = routes.stream()
                .filter(this::isContentFirstRoute)
                .map(route -> route.id)
                .findFirst()
                .orElse(routes.isEmpty() ? "pg1" : routes.get(0).id);
        List<String> categories = zh
                ? List.of("推荐", "穿搭", "美食", "彩妆", "家居", "旅行", "健身", "摄影")
                : List.of("For you", "Style", "Food", "Beauty", "Home", "Travel", "Fitness", "Photo");
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
                "discover feed");
        return echoesPrompt || mentionsInternalLanguage;
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
            // Hardcoded safe fallback modal
            return """       
                    <div class="relative bg-white rounded-3xl p-8 max-w-2xl mx-auto shadow-2xl">
                      <button @click="selectedItem = null; hash = '#pg1'" class="absolute top-4 right-4 text-slate-400 hover:text-slate-700 text-2xl">&times;</button>
                      <h2 class="text-2xl font-bold mb-2" x-text="selectedItem.title || selectedItem.标题 || '详情'"></h2>
                      <p class="text-slate-500 text-sm mb-4" x-text="selectedItem.author || selectedItem.作者 || ''"></p>
                      <p class="text-slate-700 leading-relaxed" x-text="selectedItem.content || selectedItem.内容 || selectedItem.description || ''"></p>
                    </div>
                    """;
        }
    }

    /**
     * Internal Route metadata
     */
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
