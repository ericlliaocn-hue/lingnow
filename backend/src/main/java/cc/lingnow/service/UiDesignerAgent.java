package cc.lingnow.service;

import cc.lingnow.llm.LlmClient;
import cc.lingnow.model.ProjectManifest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


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
                sidebarHtml = buildFallbackPrimaryNav(routes, true);
            } else if (sidebarHtml.isBlank()) {
                log.warn("[Designer] Shell JSON parse returned empty sidebar. Using safe fallback nav.");
                sidebarHtml = buildFallbackPrimaryNav(routes, contentFirst);
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
                    .replace("{{SIDEBAR_NAV}}", buildFallbackPrimaryNav(routes, contentFirst))
                    .replace("{{UTILITY_BUTTONS}}", "")
                    .replace("{{PERSONAL_LINKS}}", "");
        }
    }

    private String generateComponent(ProjectManifest manifest, Route route, ProjectManifest.PageSpec pageSpec, String lang) {
        String handbook = loadHandbook();
        String contextDescription = pageSpec != null ?
                "ARCHITECT'S PLAN: " + pageSpec.getDescription() + "\nEXPECTED COMPONENTS: " + String.join(", ", pageSpec.getComponents()) :
                "Generate a standard view for this feature.";

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
                
                REQUIRED WRAPPER (start your output with exactly this):
                <div x-show="hash === '#%s'" class="animate-fade-in pb-8">
                """, handbook, route.id, route.id, route.id);

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
        if (isRenderableComponent(route, componentHtml)) {
            return componentHtml;
        }
        log.warn("[Designer] Component for {} returned sparse or invalid markup. Using deterministic fallback.", route.id);
        return buildFallbackComponent(manifest, route, pageSpec);
    }

    private boolean isRenderableComponent(Route route, String componentHtml) {
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
            return visibleText.length() >= 120 && contentSignals > 0 && hasFeedInteraction && countOccurrences(lower, "<article") > 0;
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
        String chips = buildFallbackChips(pageSpec);
        String layoutClass = contentFirst
                ? "grid gap-6 xl:grid-cols-[minmax(0,1.5fr)_minmax(300px,0.9fr)]"
                : "grid gap-6 xl:grid-cols-[minmax(0,1.6fr)_minmax(280px,0.8fr)]";
        String layoutBadge = zh ? "内容优先布局" : "Content-first layout";
        String heroStatLabel = zh ? "今日浏览" : "Today views";
        String featuredLabel = zh ? "精选内容" : "Featured items";
        String actionLabel = zh ? "互动动作" : "Actions";
        String recommendTitle = zh ? "为你推荐" : "Recommended for you";
        String recommendSubtitle = zh ? "基于浏览、收藏与停留行为智能排序" : "Ranked by browse, save, and dwell signals.";
        String heatNowLabel = zh ? "实时热度" : "Live heat";
        String personalizedLabel = zh ? "个性化推荐" : "Personalized";
        String searchTitle = zh ? "搜索" : "Search";
        String searchHint = zh ? "热门关键词" : "Trending keywords";
        String searchPlaceholder = zh ? "搜索笔记 / 作者 / 话题" : "Search posts / authors / topics";
        String rankingTitle = zh ? "社区热榜" : "Trending";
        String rankingLive = zh ? "实时" : "Live";
        String rankingItemOne = zh ? "#本周精选" : "#Weekly picks";
        String rankingItemOneDesc = zh ? "社区讨论上升中" : "Community discussion rising";
        String rankingItemTwo = zh ? "#高赞笔记" : "#Most saved";
        String rankingItemTwoDesc = zh ? "值得收藏的灵感合集" : "Collections worth saving";
        String fallbackDescription = zh
                ? "当前还没有结构化 mockData，这里先保留一个可工作的内容位。后续数据工程阶段会把信息流卡片、标签、作者、互动数据补齐。"
                : "Structured mockData is not ready yet, so this stays as a working content slot until the data stage fills in feed cards, tags, authors, and engagement stats.";
        String authorFallback = zh ? "LingNow 用户" : "LingNow user";
        String categoryFallback = zh ? "精选推荐" : "Featured";
        String cardTitleFallback = zh ? "精选内容卡片" : "Featured story";
        String topicFallback = zh ? "#灵感推荐" : "#Recommended";

        return String.format("""
                        <div x-show="hash === '#%s'" class="animate-fade-in pb-8 space-y-6">
                          <section class="rounded-[32px] border border-slate-200 bg-gradient-to-br from-white via-white to-rose-50/60 p-8 shadow-sm">
                            <div class="flex flex-wrap items-center gap-3">
                              <span class="inline-flex items-center rounded-full bg-emerald-50 px-3 py-1 text-xs font-semibold text-emerald-700">%s</span>
                              <span class="inline-flex items-center rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-500">%s</span>
                            </div>
                            <div class="%s mt-6">
                              <div class="space-y-5">
                                <div class="space-y-3">
                                  <h1 class="text-4xl font-black tracking-tight text-slate-900">%s</h1>
                                  <p class="max-w-2xl text-base leading-8 text-slate-600">%s</p>
                                </div>
                                <div class="flex flex-wrap gap-3">%s</div>
                              </div>
                              <div class="grid grid-cols-3 gap-3">
                                <div class="rounded-3xl border border-slate-200 bg-white p-4 shadow-sm">
                                  <div class="text-xs text-slate-500">%s</div>
                                  <div class="mt-2 text-3xl font-black text-slate-900">1.2k</div>
                                </div>
                                <div class="rounded-3xl border border-slate-200 bg-white p-4 shadow-sm">
                                  <div class="text-xs text-slate-500">%s</div>
                                  <div class="mt-2 text-3xl font-black text-slate-900">%d</div>
                                </div>
                                <div class="rounded-3xl border border-slate-200 bg-white p-4 shadow-sm">
                                  <div class="text-xs text-slate-500">%s</div>
                                  <div class="mt-2 text-3xl font-black text-slate-900">3</div>
                                </div>
                              </div>
                            </div>
                          </section>
                        
                          <section class="grid gap-6 lg:grid-cols-[minmax(0,1.6fr)_minmax(300px,0.9fr)]">
                            <div class="space-y-5">
                              <div class="flex items-center justify-between">
                                <div>
                                  <h2 class="text-2xl font-black text-slate-900">%s</h2>
                                  <p class="mt-1 text-sm text-slate-500">%s</p>
                                </div>
                                <div class="hidden items-center gap-2 rounded-full bg-white px-2 py-1 shadow-sm ring-1 ring-slate-200 md:flex">
                                  <span class="rounded-full bg-slate-900 px-3 py-1 text-xs font-semibold text-white">%s</span>
                                  <span class="rounded-full px-3 py-1 text-xs font-semibold text-slate-500">%s</span>
                                </div>
                              </div>
                              <div class="grid gap-5 md:grid-cols-2">
                                <template x-for="(item, index) in (Array.isArray(mockData) ? mockData.slice(0, %d) : [])" :key="index">
                                  <article @click="selectedItem = item; hash = '#detail'" class="group cursor-pointer overflow-hidden rounded-[28px] border border-slate-200 bg-white shadow-sm transition hover:-translate-y-1 hover:shadow-xl">
                                    <div class="aspect-[4/5] overflow-hidden bg-slate-100">
                                      <img :src="item.cover || item.image || item.thumbUrl || 'https://placehold.co/900x1200/F8FAFC/0F172A?text=LingNow'" class="h-full w-full object-cover transition duration-500 group-hover:scale-105" />
                                    </div>
                                    <div class="space-y-3 p-5">
                                      <div class="flex items-center gap-3">
                                        <img :src="item.avatar || 'https://placehold.co/64x64/FDE68A/111827?text=U'" class="h-9 w-9 rounded-full border border-slate-200 object-cover" />
                                        <div class="min-w-0">
                                          <div class="truncate text-sm font-semibold text-slate-900" x-text="item.author || item.username || item.creator || '%s'"></div>
                                          <div class="truncate text-xs text-slate-500" x-text="item.category || item.tag || '%s'"></div>
                                        </div>
                                      </div>
                                      <div>
                                        <h3 class="line-clamp-2 text-lg font-bold text-slate-900" x-text="item.title || item.name || '%s'"></h3>
                                        <p class="mt-2 line-clamp-3 text-sm leading-6 text-slate-600" x-text="item.description || item.content || item.summary || '%s'"></p>
                                      </div>
                                      <div class="flex items-center justify-between text-xs text-slate-500">
                                        <span x-text="item.location || item.topic || '%s'"></span>
                                        <div class="flex items-center gap-3">
                                          <span x-text="item.likes || item.likeCount || '2.9w'"></span>
                                          <span x-text="item.comments || item.commentCount || '1.6k'"></span>
                                        </div>
                                      </div>
                                    </div>
                                  </article>
                                </template>
                                <div x-show="!Array.isArray(mockData) || mockData.length === 0" class="rounded-[28px] border border-dashed border-slate-300 bg-white/90 p-8 text-sm leading-7 text-slate-500">
                                  %s
                                </div>
                              </div>
                            </div>
                        
                            <aside class="space-y-5">
                              <section class="rounded-[28px] border border-slate-200 bg-white p-6 shadow-sm">
                                <div class="flex items-center justify-between">
                                  <h3 class="text-xl font-black text-slate-900">%s</h3>
                                  <span class="text-xs font-semibold text-slate-400">%s</span>
                                </div>
                                <div class="mt-4 rounded-2xl bg-slate-50 p-3">
                                  <div class="flex items-center gap-2 text-slate-400">
                                    <i class="fa-solid fa-magnifying-glass"></i>
                                    <span class="text-sm">%s</span>
                                  </div>
                                </div>
                                <div class="mt-4 flex flex-wrap gap-2">%s</div>
                              </section>
                              <section class="rounded-[28px] border border-slate-200 bg-white p-6 shadow-sm">
                                <div class="flex items-center justify-between">
                                  <h3 class="text-xl font-black text-slate-900">%s</h3>
                                  <span class="text-xs font-semibold text-emerald-600">%s</span>
                                </div>
                                <div class="mt-4 space-y-3">
                                  <div class="flex items-center justify-between rounded-2xl bg-slate-50 px-4 py-3">
                                    <div>
                                      <div class="text-sm font-semibold text-slate-900">%s</div>
                                      <div class="mt-1 text-xs text-slate-500">%s</div>
                                    </div>
                                    <div class="text-sm font-black text-slate-900">12.8w</div>
                                  </div>
                                  <div class="flex items-center justify-between rounded-2xl bg-slate-50 px-4 py-3">
                                    <div>
                                      <div class="text-sm font-semibold text-slate-900">%s</div>
                                      <div class="mt-1 text-xs text-slate-500">%s</div>
                                    </div>
                                    <div class="text-sm font-black text-slate-900">9.6w</div>
                                  </div>
                                </div>
                              </section>
                            </aside>
                          </section>
                        </div>
                        """,
                route.id,
                title,
                contentFirst ? layoutBadge : (zh ? "稳定导航布局" : "Stable navigation layout"),
                layoutClass,
                title,
                description,
                chips,
                heroStatLabel,
                featuredLabel,
                primaryCards,
                actionLabel,
                recommendTitle,
                recommendSubtitle,
                heatNowLabel,
                personalizedLabel,
                primaryCards,
                authorFallback,
                categoryFallback,
                cardTitleFallback,
                description,
                topicFallback,
                fallbackDescription,
                searchTitle,
                searchHint,
                searchPlaceholder,
                chips,
                rankingTitle,
                rankingLive,
                rankingItemOne,
                rankingItemOneDesc,
                rankingItemTwo,
                rankingItemTwoDesc);
    }

    private String buildFallbackChips(ProjectManifest.PageSpec pageSpec) {
        List<String> tags = pageSpec != null && pageSpec.getComponents() != null && !pageSpec.getComponents().isEmpty()
                ? pageSpec.getComponents().stream().limit(6).toList()
                : List.of("发现灵感", "内容精选", "高赞互动", "趋势热点");
        StringBuilder chips = new StringBuilder();
        for (String tag : tags) {
            chips.append("<span class=\"rounded-full border border-slate-200 bg-white px-4 py-2 text-sm text-slate-600\">#")
                    .append(escapeHtml(tag))
                    .append("</span>");
        }
        return chips.toString();
    }

    private String buildFallbackPrimaryNav(List<Route> routes, boolean contentFirst) {
        StringBuilder fallbackNav = new StringBuilder();
        for (Route route : routes) {
            fallbackNav.append(contentFirst
                    ? String.format(
                    "<a @click=\"hash='#%s'\" :class=\"hash==='#%s'?'bg-rose-500 text-white shadow-lg shadow-rose-100':'bg-white text-slate-600'\" class=\"inline-flex items-center gap-2 rounded-full border border-slate-200 px-4 py-2 text-sm font-semibold transition-all\">%s</a>\n",
                    route.id, route.id, route.name)
                    : String.format(
                    "<a @click=\"hash='#%s'\" :class=\"hash==='#%s'?'bg-rose-50 text-rose-600 font-semibold':''\" class=\"flex items-center gap-3 px-4 py-2.5 rounded-xl text-slate-700 hover:bg-slate-100 transition-all text-sm\">%s</a>\n",
                    route.id, route.id, route.name));
        }
        return fallbackNav.toString();
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
