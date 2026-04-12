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
import java.util.regex.Matcher;
import java.util.regex.Pattern;


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
        boolean contentFirst = isContentFirst(manifest);
        ShellTheme shellTheme = buildShellTheme(manifest, contentFirst);
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
                meta.getOrDefault("visual_primaryColor", shellTheme.accentToken()),
                meta.getOrDefault("visual_accentColor", shellTheme.accentToken()),
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
            if (isPhotographyManifest(manifest)) {
                log.info("[Designer] Photography workflow detected. Using deterministic clickable prototype path before any LLM polish.");
                rebuildShapeAlignedPrototype(manifest);
                return;
            }

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
            String modalHtml = isPhotographyManifest(manifest)
                    ? buildPhotographyDetailModal(lang, homeRouteId)
                    : shouldUseWorkflowDetailModal(manifest)
                    ? buildWorkflowDetailModal(manifest, lang, homeRouteId)
                    : deterministicContentFirst
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
        ShellTheme shellTheme = buildShellTheme(manifest, contentFirst);

        // Phase 1: Build the Shell (Instant Frame)
        String shell = applyShellCopy(applyShellTheme(template, shellTheme), shellCopy)
                .replace("{{TITLE}}", manifest.getOverview() != null ? manifest.getOverview() : "LingNow")
                .replace("{{LOGO_AREA}}", buildShellLogo(manifest))
                .replace("{{SIDEBAR_NAV}}", buildFallbackPrimaryNav(manifest, primaryRoutes, contentFirst))
                .replace("{{PUBLISH_ACTION}}", buildContentPublishAction(manifest, routes))
                .replace("{{UTILITY_BUTTONS}}", "")
                .replace("{{PERSONAL_LINKS}}", buildDeterministicPersonalLinks(manifest, routes));

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

        String modalHtml = isPhotographyManifest(manifest)
                ? buildPhotographyDetailModal(lang, firstPrimaryRouteId(primaryRoutes))
                : shouldUseWorkflowDetailModal(manifest)
                ? buildWorkflowDetailModal(manifest, lang, firstPrimaryRouteId(primaryRoutes))
                : buildFallbackDetailModal(lang, firstPrimaryRouteId(primaryRoutes));
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
                .replace("{{PUBLISH_KIND}}", escapeHtml(shellCopy.publishKind()))
                .replace("{{SEARCH_PLACEHOLDER}}", escapeHtml(shellCopy.searchPlaceholder()))
                .replace("{{PUBLISH_LABEL}}", escapeHtml(shellCopy.publishLabel()))
                .replace("{{POST_TITLE}}", escapeHtml(shellCopy.postTitle()))
                .replace("{{POST_PLACEHOLDER}}", escapeHtml(shellCopy.postPlaceholder()))
                .replace("{{POST_SUBMIT_LABEL}}", escapeHtml(shellCopy.postSubmitLabel()));
    }

    private String applyShellTheme(String template, ShellTheme shellTheme) {
        return template.replace("{{THEME_CSS}}", buildShellThemeCss(shellTheme));
    }

    private ShellCopy buildShellCopy(ProjectManifest manifest, boolean contentFirst) {
        boolean zh = manifest.getMetaData() == null || !"EN".equalsIgnoreCase(manifest.getMetaData().getOrDefault("lang", "ZH"));
        return switch (resolveShellFlavor(manifest, contentFirst)) {
            case PHOTOGRAPHY -> new ShellCopy(
                    zh ? "搜索摄影师、作品风格、城市或可约档期..." : "Search photographers, styles, cities, or availability...",
                    zh ? "发布作品" : "Publish work",
                    zh ? "发布作品与可约档期" : "Publish work and availability",
                    zh ? "介绍拍摄风格、服务城市、可预约档期或套餐亮点..." : "Describe your shooting style, service city, availability, or package highlights...",
                    zh ? "更新作品" : "Publish",
                    "portfolio"
            );
            case CONTENT -> new ShellCopy(
                    zh ? "发现你感兴趣的话题..." : "Discover topics you care about...",
                    isFashionIntent(manifest) ? (zh ? "发布笔记" : "Post look") : (zh ? "发布" : "Post"),
                    isFashionIntent(manifest) ? (zh ? "发布穿搭笔记" : "Create a style post") : (zh ? "发布新内容" : "Create a new post"),
                    isFashionIntent(manifest)
                            ? (zh ? "写下今天的穿搭灵感、单品、尺码和搭配场景..." : "Share today's outfit, pieces, sizing, and styling context...")
                            : (zh ? "写点什么分享你的灵感..." : "Share a thought, note, or inspiration..."),
                    isFashionIntent(manifest) ? (zh ? "发布笔记" : "Publish look") : (zh ? "立即发布" : "Post now"),
                    "post"
            );
            case PIPELINE -> new ShellCopy(
                    zh ? "搜索线索、商机、客户或负责人..." : "Search leads, opportunities, customers, or owners...",
                    zh ? "新建线索" : "New lead",
                    zh ? "创建线索与推进记录" : "Create lead and progression note",
                    zh ? "录入公司、联系人、金额、阶段和下一步动作..." : "Add account, contact, amount, stage, and next action...",
                    zh ? "创建线索" : "Create lead",
                    "lead"
            );
            case OPS -> new ShellCopy(
                    zh ? "搜索告警、工单、站点或负责人..." : "Search alerts, tickets, sites, or operators...",
                    zh ? "新建工单" : "New ticket",
                    zh ? "创建工单与派发记录" : "Create ticket and dispatch note",
                    zh ? "补充告警来源、站点、负责人、SLA 和处理备注..." : "Add alert source, site, owner, SLA, and handling notes...",
                    zh ? "保存工单" : "Save ticket",
                    "workorder"
            );
            case CLINIC -> new ShellCopy(
                    zh ? "搜索项目、医生、档期或就诊单..." : "Search services, doctors, slots, or visit orders...",
                    zh ? "新建预约" : "New appointment",
                    zh ? "创建预约与就诊单" : "Create appointment and visit order",
                    zh ? "填写项目、医生、预约时间、到诊提醒或支付说明..." : "Add service, doctor, appointment time, visit reminders, or payment notes...",
                    zh ? "创建预约" : "Create appointment",
                    "appointment"
            );
            case FITNESS -> new ShellCopy(
                    zh ? "搜索课程、教练、会员或课包..." : "Search classes, coaches, members, or packages...",
                    zh ? "新建排课" : "New class slot",
                    zh ? "创建排课与课包记录" : "Create schedule and package record",
                    zh ? "填写课程、教练、会员、课包消耗和签到说明..." : "Add class, coach, member, package use, and attendance notes...",
                    zh ? "保存排课" : "Save schedule",
                    "schedule"
            );
            case VEHICLE -> new ShellCopy(
                    zh ? "搜索车源、品牌、城市或试驾时间..." : "Search vehicles, brands, cities, or test-drive slots...",
                    zh ? "发布车源" : "Publish listing",
                    zh ? "发布车源与试驾档期" : "Publish vehicle and test-drive availability",
                    zh ? "填写车况、价格、地点、试驾时间和交付说明..." : "Add condition, price, location, test-drive time, and delivery notes...",
                    zh ? "发布车源" : "Publish listing",
                    "vehicle"
            );
            case PROPERTY -> new ShellCopy(
                    zh ? "搜索房源、区域、租期或看房时间..." : "Search properties, areas, lease terms, or viewing times...",
                    zh ? "发布房源" : "Publish property",
                    zh ? "发布房源与看房档期" : "Publish property and viewing slots",
                    zh ? "填写户型、租金、区域、看房时间和签约说明..." : "Add layout, rent, area, viewing schedule, and contract notes...",
                    zh ? "发布房源" : "Publish property",
                    "property"
            );
            case EVENT -> new ShellCopy(
                    zh ? "搜索场次、城市、日期或票档..." : "Search sessions, cities, dates, or ticket tiers...",
                    zh ? "发布场次" : "Publish session",
                    zh ? "创建场次与票务信息" : "Create session and ticket details",
                    zh ? "填写活动简介、时间、票档、核销说明和现场信息..." : "Add event summary, time, tiers, check-in notes, and venue details...",
                    zh ? "发布场次" : "Publish session",
                    "ticket"
            );
            case LEARNING -> new ShellCopy(
                    zh ? "搜索课程、任务、学员或直播答疑..." : "Search courses, tasks, students, or office hours...",
                    zh ? "新建课程" : "New course",
                    zh ? "创建课程与任务安排" : "Create course and task schedule",
                    zh ? "填写课程主题、任务、直播时间、作业说明和进度要求..." : "Add course topic, tasks, live session time, homework notes, and progress goals...",
                    zh ? "保存课程" : "Save course",
                    "course"
            );
            case COMMERCE -> new ShellCopy(
                    zh ? "搜索商品、类目、订单或履约状态..." : "Search products, categories, orders, or fulfillment status...",
                    zh ? "发布商品" : "Publish product",
                    zh ? "创建商品与订单说明" : "Create product and order notes",
                    zh ? "填写卖点、库存、价格、物流和售后说明..." : "Add highlights, stock, pricing, logistics, and after-sales notes...",
                    zh ? "发布商品" : "Publish product",
                    "product"
            );
            case SERVICE -> new ShellCopy(
                    zh ? "搜索服务、案例、城市或可预约时间..." : "Search services, cases, cities, or available times...",
                    zh ? "发布服务" : "Publish service",
                    zh ? "发布服务与可预约时间" : "Publish service and availability",
                    zh ? "写下服务亮点、可服务范围、报价线索或预约说明..." : "Describe service highlights, scope, pricing hints, or booking notes...",
                    zh ? "更新服务" : "Publish",
                    "service"
            );
            default -> new ShellCopy(
                    zh ? "搜索功能、客户、订单或关键内容..." : "Search features, customers, orders, or key content...",
                    zh ? "新建" : "Create",
                    zh ? "新建内容" : "Create item",
                    zh ? "补充标题、说明、状态或下一步动作..." : "Add a title, description, status, or next action...",
                    zh ? "保存" : "Save",
                    "generic"
            );
        };
    }

    private ShellTheme buildShellTheme(ProjectManifest manifest, boolean contentFirst) {
        return switch (resolveShellFlavor(manifest, contentFirst)) {
            case PHOTOGRAPHY, CONTENT ->
                    new ShellTheme("rose-500", "#f43f5e", "#e11d48", "#fff1f2", "#be123c", "#fecdd3", "rgba(244,63,94,0.24)");
            case PIPELINE ->
                    new ShellTheme("blue-600", "#2563eb", "#1d4ed8", "#eff6ff", "#1d4ed8", "#bfdbfe", "rgba(37,99,235,0.24)");
            case OPS ->
                    new ShellTheme("orange-600", "#ea580c", "#c2410c", "#fff7ed", "#c2410c", "#fdba74", "rgba(234,88,12,0.24)");
            case CLINIC ->
                    new ShellTheme("teal-600", "#0f766e", "#115e59", "#f0fdfa", "#0f766e", "#99f6e4", "rgba(15,118,110,0.22)");
            case FITNESS ->
                    new ShellTheme("emerald-600", "#16a34a", "#15803d", "#f0fdf4", "#15803d", "#86efac", "rgba(22,163,74,0.22)");
            case VEHICLE ->
                    new ShellTheme("amber-600", "#d97706", "#b45309", "#fffbeb", "#b45309", "#fcd34d", "rgba(217,119,6,0.22)");
            case PROPERTY ->
                    new ShellTheme("emerald-700", "#047857", "#065f46", "#ecfdf5", "#065f46", "#a7f3d0", "rgba(4,120,87,0.22)");
            case EVENT ->
                    new ShellTheme("violet-600", "#7c3aed", "#6d28d9", "#f5f3ff", "#6d28d9", "#c4b5fd", "rgba(124,58,237,0.22)");
            case LEARNING ->
                    new ShellTheme("indigo-600", "#4f46e5", "#4338ca", "#eef2ff", "#4338ca", "#c7d2fe", "rgba(79,70,229,0.22)");
            case COMMERCE ->
                    new ShellTheme("sky-600", "#0284c7", "#0369a1", "#f0f9ff", "#0369a1", "#bae6fd", "rgba(2,132,199,0.22)");
            case SERVICE, DEFAULT ->
                    new ShellTheme("indigo-600", "#4f46e5", "#4338ca", "#eef2ff", "#4338ca", "#c7d2fe", "rgba(79,70,229,0.22)");
        };
    }

    private ShellFlavor resolveShellFlavor(ProjectManifest manifest, boolean contentFirst) {
        String source = buildIntentSource(manifest);
        ProjectManifest.DesignContract contract = manifest.getDesignContract();
        boolean pipeline = containsAny(source, "crm", "ats", "招聘", "候选人", "面试", "offer", "销售线索", "商机", "客户成功", "pipeline");
        boolean ops = containsAny(source, "看板", "运维", "设备", "监控", "工单", "dashboard", "ops", "maintenance", "履约", "物流", "预警", "站点");
        boolean learning = containsAny(source, "课程", "学习", "共读", "笔记", "进度", "阅读", "learn", "study", "bootcamp", "作业", "直播");
        boolean commerce = containsAny(source, "商城", "商品", "预售", "尺码", "物流", "支付", "ecommerce", "sku", "cart");
        boolean service = containsAny(source, "预约", "预订", "booking", "appointment", "inquiry", "询价", "咨询", "服务", "客户");
        if (isPhotographyIntent(source)) {
            return ShellFlavor.PHOTOGRAPHY;
        }
        if (contentFirst) {
            return ShellFlavor.CONTENT;
        }
        if (pipeline || (contract != null && contract.getUiTone() == ProjectManifest.UiTone.ENTERPRISE && containsAny(source, "跟进", "转化", "负责人"))) {
            return ShellFlavor.PIPELINE;
        }
        if (ops || (contract != null && contract.getLayoutRhythm() == ProjectManifest.LayoutRhythm.DASHBOARD && contract.getPrimaryGoal() == ProjectManifest.PrimaryGoal.MONITOR)) {
            return ShellFlavor.OPS;
        }
        if (containsAny(source, "医美", "诊所", "门诊", "医生", "体检", "康复", "clinic", "medical", "healthcare")) {
            return ShellFlavor.CLINIC;
        }
        if (containsAny(source, "健身", "瑜伽", "私教", "排课", "课包", "教练", "fitness", "gym", "coach")) {
            return ShellFlavor.FITNESS;
        }
        if (containsAny(source, "二手车", "车辆", "汽车", "看车", "试驾", "车源", "vehicle", "car")) {
            return ShellFlavor.VEHICLE;
        }
        if (containsAny(source, "房源", "租房", "租赁", "看房", "公寓", "长租", "property", "rental", "housing")) {
            return ShellFlavor.PROPERTY;
        }
        if (containsAny(source, "票务", "门票", "活动", "演出", "展会", "ticket", "event")) {
            return ShellFlavor.EVENT;
        }
        if (learning) {
            return ShellFlavor.LEARNING;
        }
        if (commerce) {
            return ShellFlavor.COMMERCE;
        }
        if (service || (contract != null && contract.getPrimaryGoal() == ProjectManifest.PrimaryGoal.TRANSACT)) {
            return ShellFlavor.SERVICE;
        }
        return ShellFlavor.DEFAULT;
    }

    private String buildIntentSource(ProjectManifest manifest) {
        if (manifest == null) {
            return "";
        }
        return ((manifest.getUserIntent() == null ? "" : manifest.getUserIntent()) + " "
                + (manifest.getArchetype() == null ? "" : manifest.getArchetype()) + " "
                + (manifest.getOverview() == null ? "" : manifest.getOverview())).toLowerCase(Locale.ROOT);
    }

    private boolean isFashionIntent(ProjectManifest manifest) {
        String source = buildIntentSource(manifest);
        return containsAny(source, "小红书", "穿搭", "ootd", "搭配", "lookbook", "种草", "时尚", "单品");
    }

    private String buildShellThemeCss(ShellTheme shellTheme) {
        return """
                :root {
                    --shell-accent: %s;
                    --shell-accent-strong: %s;
                    --shell-accent-soft: %s;
                    --shell-accent-soft-text: %s;
                    --shell-accent-ring: %s;
                    --shell-accent-shadow: %s;
                }
                
                .shell-logo {
                    color: var(--shell-accent);
                }
                
                .shell-search-focus:focus {
                    outline: none;
                    box-shadow: 0 0 0 2px var(--shell-accent-ring);
                }
                
                .shell-primary-button {
                    background: var(--shell-accent);
                    color: #fff;
                    box-shadow: 0 16px 28px var(--shell-accent-shadow);
                }
                
                .shell-primary-button:hover {
                    background: var(--shell-accent-strong);
                }
                
                .shell-submit-button {
                    background: var(--shell-accent);
                    color: #fff;
                }
                
                .shell-submit-button:hover {
                    background: var(--shell-accent-strong);
                }
                
                .shell-field-focus:focus {
                    outline: none;
                    border-color: var(--shell-accent-ring);
                    background: #fff;
                    box-shadow: 0 0 0 2px var(--shell-accent-ring);
                }
                
                .shell-nav-active {
                    background: var(--shell-accent-soft);
                    color: var(--shell-accent-soft-text);
                    font-weight: 600;
                }
                
                .shell-pill-active {
                    background: var(--shell-accent);
                    color: #fff;
                    box-shadow: 0 14px 24px var(--shell-accent-shadow);
                }
                
                .shell-soft-pill {
                    background: var(--shell-accent-soft);
                    color: var(--shell-accent-soft-text);
                }
                
                .shell-soft-button {
                    background: var(--shell-accent-soft);
                    color: var(--shell-accent-soft-text);
                }
                
                .shell-soft-button:hover,
                .shell-flow-hover:hover {
                    background: var(--shell-accent-soft);
                    color: var(--shell-accent-soft-text);
                }
                
                .shell-flow-active {
                    border-color: var(--shell-accent-ring);
                    background: var(--shell-accent-soft);
                    color: var(--shell-accent-soft-text);
                }
                """.formatted(
                shellTheme.accentHex(),
                shellTheme.accentStrongHex(),
                shellTheme.accentSoftHex(),
                shellTheme.accentSoftTextHex(),
                shellTheme.accentRingHex(),
                shellTheme.accentShadow()
        );
    }

    private String buildShellLogo(ProjectManifest manifest) {
        return "<span class=\"shell-logo text-xl font-bold\">" + escapeHtml(manifest.getOverview() != null ? manifest.getOverview() : "LingNow") + "</span>";
    }

    private boolean isPhotographyIntent(String intent) {
        return containsAny(intent, "摄影", "摄影师", "拍摄", "约拍", "photo", "photograph", "photographer", "photo studio");
    }

    private boolean isPhotographyManifest(ProjectManifest manifest) {
        if (manifest == null) {
            return false;
        }
        return isPhotographyIntent(manifest.getUserIntent()) || isPhotographySurface(buildShapeSurfaceProfile(manifest));
    }

    private boolean shouldUseWorkflowDetailModal(ProjectManifest manifest) {
        if (manifest == null || isPhotographyManifest(manifest) || isContentFirst(manifest)) {
            return false;
        }
        ProjectManifest.DesignContract contract = manifest.getDesignContract();
        String source = ((manifest.getUserIntent() == null ? "" : manifest.getUserIntent()) + " "
                + (manifest.getArchetype() == null ? "" : manifest.getArchetype()) + " "
                + (manifest.getOverview() == null ? "" : manifest.getOverview())).toLowerCase(Locale.ROOT);
        boolean keywordMatch = containsAny(source,
                "crm", "pipeline", "线索", "商机", "客户成功", "排课", "课包", "学习", "预约", "booking",
                "order", "交易", "commerce", "dashboard", "ops", "运维", "告警", "工单", "监控",
                "医美", "诊所", "健身", "票务", "活动", "房源", "租赁", "车源", "看车", "履约", "物流");
        if (keywordMatch) {
            return true;
        }
        if (contract == null) {
            return false;
        }
        return contract.getLayoutRhythm() == ProjectManifest.LayoutRhythm.DASHBOARD
                || contract.getUiTone() == ProjectManifest.UiTone.ENTERPRISE
                || contract.getPrimaryGoal() == ProjectManifest.PrimaryGoal.MONITOR
                || contract.getPrimaryGoal() == ProjectManifest.PrimaryGoal.TRANSACT
                || contract.getPrimaryGoal() == ProjectManifest.PrimaryGoal.COMPARE
                || contract.getPrimaryGoal() == ProjectManifest.PrimaryGoal.LEARN;
    }

    private List<Route> extractRoutes(ProjectManifest manifest) {
        if (manifest != null && isContentFirst(manifest) && manifest.getPages() != null && !manifest.getPages().isEmpty()) {
            List<Route> routesFromPages = new ArrayList<>();
            int index = 1;
            for (ProjectManifest.PageSpec page : manifest.getPages()) {
                String role = page.getNavRole() == null ? "PRIMARY" : page.getNavRole();
                if (!List.of("PRIMARY", "UTILITY", "PERSONAL").contains(role)) {
                    continue;
                }
                String name = primaryRouteLabel(page);
                String navType = page.getNavType() != null ? page.getNavType() : "NAV_ANCHOR";
                routesFromPages.add(new Route("pg" + index++, name, navType));
            }
            if (!routesFromPages.isEmpty()) {
                return routesFromPages;
            }
        }

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
            name = sanitizeRouteName(name);
            if (name.isEmpty()) continue;

            String id = "pg" + (i + 1);

            // Match with PageSpec to get navType
            ProjectManifest.PageSpec spec = findPageSpec(manifest, new Route(id, name, "NAV_ANCHOR"));
            String navType = spec != null ? spec.getNavType() : "NAV_ANCHOR";

            routes.add(new Route(id, name, navType));
        }
        return routes;
    }

    private String primaryRouteLabel(ProjectManifest.PageSpec page) {
        String route = safe(page.getRoute()).toLowerCase(Locale.ROOT);
        String description = safe(page.getDescription()).trim();
        if (route.contains("home")) {
            return "社区首页";
        }
        if (route.contains("discover")) {
            return "发现页";
        }
        if (route.contains("following")) {
            return "关注流页";
        }
        if (route.contains("profile") || route.contains("user")) {
            return "创作者主页";
        }
        if (route.contains("publish")) {
            return "发布笔记页";
        }
        if (route.contains("post") || route.contains("detail")) {
            return "帖子详情页";
        }
        if (description.isBlank()) {
            return safe(page.getRoute()).replace("/", "");
        }
        String[] parts = description.split("[，。:：]", 2);
        return sanitizeRouteName(parts[0].trim());
    }

    private String sanitizeRouteName(String name) {
        if (name == null) {
            return "";
        }
        String cleaned = name.replaceAll("\\s+", " ").trim();
        if (cleaned.length() <= 18) {
            return cleaned;
        }
        for (String delimiter : List.of("，", "。", "：", ":", ",", " - ", " — ")) {
            int index = cleaned.indexOf(delimiter);
            if (index > 0 && index <= 18) {
                return cleaned.substring(0, index).trim();
            }
        }
        return cleaned.substring(0, Math.min(cleaned.length(), 18)).trim();
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
            ShellTheme shellTheme = buildShellTheme(manifest, true);
            return applyShellCopy(applyShellTheme(template, shellTheme), shellCopy)
                    .replace("{{TITLE}}", manifest.getOverview() != null ? manifest.getOverview() : "LingNow App")
                    .replace("{{LOGO_AREA}}", buildShellLogo(manifest))
                    .replace("{{SIDEBAR_NAV}}", buildFallbackPrimaryNav(manifest, routes, true))
                    .replace("{{PUBLISH_ACTION}}", buildContentPublishAction(manifest, routes))
                    .replace("{{UTILITY_BUTTONS}}", buildDeterministicUtilityButtons(manifest, routes))
                    .replace("{{PERSONAL_LINKS}}", buildDeterministicPersonalLinks(manifest, routes));
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
                - Each PRIMARY link: <a @click="hash='#ID'" :class="hash==='#ID'?'shell-pill-active':'bg-white text-slate-600'" class="inline-flex items-center gap-2 rounded-full px-4 py-2 text-sm font-semibold border border-slate-200 transition-all">
                - DO NOT generate extra publish, search, login, register, or profile buttons in utility/personal fragments; the shell already owns those actions.
                """
                : """
                SIDEBAR PURITY RULE:
                - ONLY generate <a> tags for routes with 'Role: PRIMARY'.
                - UTILITY and PERSONAL roles MUST NOT appear in the sidebar.
                - Each sidebar link: <a @click="hash='#ID'" :class="hash==='#ID'?'shell-nav-active':''" class="flex items-center gap-3 px-4 py-2.5 rounded-xl text-slate-700 hover:bg-slate-100 transition-all text-sm">
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
            utilityHtml = autoWireInteractiveButtons(manifest, null, normalizeHtmlFragment(utilityHtml));
            personalHtml = autoWireInteractiveButtons(manifest, null, normalizeHtmlFragment(personalHtml));
            logoHtml = buildShellLogo(manifest);

            if (sidebarHtml.isBlank()) {
                log.warn("[Designer] Shell JSON parse returned empty sidebar. Using safe fallback nav.");
                sidebarHtml = buildFallbackPrimaryNav(manifest, routes, contentFirst);
            }
            ShellTheme shellTheme = buildShellTheme(manifest, contentFirst);

            String shell = applyShellCopy(applyShellTheme(template, shellTheme), shellCopy)
                    .replace("{{TITLE}}", manifest.getOverview() != null ? manifest.getOverview() : "LingNow App")
                    .replace("{{LOGO_AREA}}", logoHtml)
                    .replace("{{SIDEBAR_NAV}}", sidebarHtml)
                    .replace("{{PUBLISH_ACTION}}", buildContentPublishAction(manifest, routes))
                    .replace("{{UTILITY_BUTTONS}}", buildDeterministicUtilityButtons(manifest, routes))
                    .replace("{{PERSONAL_LINKS}}", buildDeterministicPersonalLinks(manifest, routes));

            return shell;
        } catch (Exception e) {
            log.error("Shell fragment generation failed, using minimal safe fallback shell", e);
            // Safe fallback: build sidebar from routes list directly
            ShellTheme shellTheme = buildShellTheme(manifest, contentFirst);
            return applyShellCopy(applyShellTheme(template, shellTheme), shellCopy)
                    .replace("{{TITLE}}", manifest.getOverview() != null ? manifest.getOverview() : "LingNow")
                    .replace("{{LOGO_AREA}}", buildShellLogo(manifest))
                    .replace("{{SIDEBAR_NAV}}", buildFallbackPrimaryNav(manifest, routes, contentFirst))
                    .replace("{{PUBLISH_ACTION}}", buildContentPublishAction(manifest, routes))
                    .replace("{{UTILITY_BUTTONS}}", buildDeterministicUtilityButtons(manifest, routes))
                    .replace("{{PERSONAL_LINKS}}", buildDeterministicPersonalLinks(manifest, routes));
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
                
                MANDATORY INTERACTION LOGIC (PROTOTYPE BINDING):
                - If this is a Feed/Grid/List view, every item/card MUST have:
                  @click="openDetail(item)" class="cursor-pointer"
                - Use Alpine.js x-for loop to render from mockData array.
                - Every visible CTA button must mutate prototype state with @click, such as go('#routeId'), openDetail(item), startInquiry(item), pickSlot(slot), submitInquiry(), or advanceOrder(order).
                - Treat this as a clickable workflow prototype, never a static marketing page.
                
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
        String repairedHtml = autoWireInteractiveButtons(manifest, route, componentHtml);
        if (shouldUseDeterministicContentFallback(manifest, route)) {
            log.info("[Designer] Route {} is using deterministic shape fallback to preserve layout rhythm.", route.id);
            return buildFallbackComponent(manifest, route, pageSpec);
        }
        if (isRenderableComponent(manifest, route, repairedHtml)) {
            return repairedHtml;
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
        if (hasDeadButtons(trimmed)) {
            return false;
        }
        String visibleText = trimmed.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        int contentSignals = countOccurrences(lower, "<section")
                + countOccurrences(lower, "<article")
                + countOccurrences(lower, "x-for=");
        boolean hasFeedInteraction = lower.contains("opendetail(item)")
                || (lower.contains("selecteditem = item")
                && (lower.contains("hash = '#detail'") || lower.contains("hash='#detail'")));
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
        if (isContentFirst(manifest) && pageSpec != null && "PERSONAL".equalsIgnoreCase(pageSpec.getNavRole())) {
            return buildContentProfileFallbackComponent(manifest, route, pageSpec);
        }
        if (isContentFirst(manifest) && pageSpec != null && "UTILITY".equalsIgnoreCase(pageSpec.getNavRole())) {
            return buildContentPublishFallbackComponent(manifest, route, pageSpec);
        }
        if (manifest.getDesignContract() != null && isContentFirst(manifest) && pageSpec != null && pageSpec.getRoute() != null
                && pageSpec.getRoute().toLowerCase(Locale.ROOT).contains("discover")) {
            ShapeSurfaceProfile profile = buildShapeSurfaceProfile(manifest);
            return buildContentDiscoverFallbackComponent(manifest, route, pageSpec, profile);
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

    private String buildContentDiscoverFallbackComponent(ProjectManifest manifest, Route route, ProjectManifest.PageSpec pageSpec, ShapeSurfaceProfile profile) {
        boolean zh = manifest.getMetaData() == null || !"EN".equalsIgnoreCase(manifest.getMetaData().getOrDefault("lang", "ZH"));
        String hotTopics = buildHotTopicsJson(zh, profile);
        String color = profile.vibeColor();
        String accentColor = color.replace("-500", "").replace("-400", "");
        String seededFeed = buildSeededFeedJson(zh, 6, profile);

        String html = """
                <div x-show="hash === '#__ID__'" class="animate-fade-in pb-8 space-y-6">
                  <section class="rounded-[32px] border border-slate-200 bg-white p-6 shadow-sm">
                    <div class="flex items-end justify-between gap-4">
                      <div>
                        <h2 class="text-2xl font-black text-slate-900">__TITLE__</h2>
                        <p class="mt-2 text-sm leading-7 text-slate-500">__DESC__</p>
                      </div>
                      <div class="flex flex-wrap gap-3 text-sm">
                        <button @click="activeSignal = 'hot'" class="rounded-full border border-slate-200 bg-white px-4 py-2 font-semibold text-slate-700">__HOT__</button>
                        <button @click="activeSignal = 'saved'" class="rounded-full border border-slate-200 bg-white px-4 py-2 font-semibold text-slate-700">__SAVED__</button>
                      </div>
                    </div>
                  </section>
                  <section class="grid gap-6 xl:grid-cols-[320px_minmax(0,1fr)]">
                    <aside class="space-y-4">
                      <section class="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm">
                        <h3 class="text-lg font-black text-slate-900">__TOPIC_TITLE__</h3>
                        <p class="mt-2 text-xs leading-6 text-slate-500">__TOPIC_DESC__</p>
                        <div class="mt-4 space-y-3">
                          <template x-for="topic in __HOT_TOPICS__" :key="topic">
                            <button @click="searchQuery = topic; activeSignal = 'hot'" class="w-full rounded-2xl bg-slate-50 px-4 py-3 text-left text-sm font-semibold text-slate-800 transition hover:bg-slate-100" x-text="'#' + topic"></button>
                          </template>
                        </div>
                      </section>
                      <section class="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm">
                        <h3 class="text-lg font-black text-slate-900">__CREATOR_TITLE__</h3>
                        <div class="mt-4 space-y-3">
                          <div class="rounded-2xl bg-slate-50 p-4 text-sm text-slate-700">@ootd.daily · __CREATOR_A__</div>
                          <div class="rounded-2xl bg-slate-50 p-4 text-sm text-slate-700">@studio.lane · __CREATOR_B__</div>
                          <div class="rounded-2xl bg-slate-50 p-4 text-sm text-slate-700">@fit.notes · __CREATOR_C__</div>
                        </div>
                      </section>
                    </aside>
                    <section class="rounded-[32px] border border-slate-200 bg-white p-6 shadow-sm">
                      <div class="mb-5 flex items-end justify-between gap-4">
                        <div>
                          <h3 class="text-2xl font-black text-slate-900">__TREND_TITLE__</h3>
                          <p class="mt-1 text-sm text-slate-500">__TREND_DESC__</p>
                        </div>
                      </div>
                      <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
                        <template x-for='(item, index) in getFilteredFeed(__SEEDED_FEED__).slice(0, 6)' :key="(item.id || item.title || index) + '-' + index">
                          <article @click="selectedItem = item; hash = '#detail'" class="group cursor-pointer overflow-hidden rounded-[28px] border border-slate-200 bg-slate-50 transition hover:-translate-y-1 hover:bg-white hover:shadow-lg">
                            <div class="aspect-[4/5] overflow-hidden bg-slate-100">
                              <img :src="item.cover" class="h-full w-full object-cover transition duration-500 group-hover:scale-105" />
                            </div>
                            <div class="space-y-3 p-4">
                              <div class="flex items-center justify-between gap-3 text-[11px] text-slate-500">
                                <span x-text="item.author"></span>
                                <span x-text="item.time"></span>
                              </div>
                              <h4 class="line-clamp-2 text-base font-black text-slate-900" x-text="item.title"></h4>
                              <div class="flex flex-wrap gap-2">
                                <template x-for="tag in item.tags.slice(0, 2)" :key="tag">
                                  <span class="rounded-full bg-__ACCENT__/5 px-2.5 py-1 text-[10px] font-semibold text-__ACCENT__" x-text="'#' + tag"></span>
                                </template>
                              </div>
                            </div>
                          </article>
                        </template>
                      </div>
                    </section>
                  </section>
                </div>
                """
                .replace("__ACCENT__", accentColor);

        return html.replace("__ID__", route.id)
                .replace("__TITLE__", zh ? "发现页" : "Discover")
                .replace("__DESC__", zh ? "按风格、场景、品牌和热度聚合内容，帮助用户探索新的穿搭方向。" : "Browse style directions by occasion, brand, and momentum.")
                .replace("__HOT__", zh ? "热榜" : "Trending")
                .replace("__SAVED__", zh ? "高收藏" : "Most saved")
                .replace("__TOPIC_TITLE__", zh ? "穿搭专题" : "Style topics")
                .replace("__TOPIC_DESC__", zh ? "从热榜、专题和趋势里快速进入感兴趣的穿搭方向。" : "Jump into style directions through trends and topics.")
                .replace("__HOT_TOPICS__", hotTopics)
                .replace("__CREATOR_TITLE__", zh ? "推荐创作者" : "Featured creators")
                .replace("__CREATOR_A__", zh ? "通勤和小个子穿搭" : "Workwear and petite looks")
                .replace("__CREATOR_B__", zh ? "周末约会与旅行搭配" : "Weekend and travel styling")
                .replace("__CREATOR_C__", zh ? "身材参考与单品拆解" : "Fit notes and styling breakdowns")
                .replace("__TREND_TITLE__", zh ? "趋势内容" : "Trending content")
                .replace("__TREND_DESC__", zh ? "围绕热度、收藏和近期讨论，继续发现值得点开的穿搭笔记。" : "Keep exploring looks driven by trend, saves, and recent discussion.")
                .replace("__SEEDED_FEED__", seededFeed);
    }

    private String buildContentProfileFallbackComponent(ProjectManifest manifest, Route route, ProjectManifest.PageSpec pageSpec) {
        boolean zh = manifest.getMetaData() == null || !"EN".equalsIgnoreCase(manifest.getMetaData().getOrDefault("lang", "ZH"));
        return """
                <div x-show="hash === '#__ID__'" class="animate-fade-in pb-10 space-y-6">
                  <section class="rounded-[32px] border border-slate-200 bg-white p-7 shadow-sm">
                    <div class="flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
                      <div class="flex items-center gap-4">
                        <img src="https://images.unsplash.com/photo-1494790108377-be9c29b29330?q=80&w=256" class="h-20 w-20 rounded-full object-cover"/>
                        <div>
                          <div class="text-2xl font-black text-slate-900">__TITLE__</div>
                          <div class="mt-2 text-sm text-slate-500">__DESC__</div>
                          <div class="mt-3 flex flex-wrap gap-2 text-xs text-slate-500">
                            <span class="rounded-full bg-slate-100 px-3 py-1">168cm / 52kg</span>
                            <span class="rounded-full bg-slate-100 px-3 py-1">__TAG_A__</span>
                            <span class="rounded-full bg-slate-100 px-3 py-1">__TAG_B__</span>
                          </div>
                        </div>
                      </div>
                      <div class="flex flex-wrap gap-3">
                        <button class="shell-primary-button rounded-full px-5 py-3 text-sm font-black text-white">__FOLLOW__</button>
                        <button class="rounded-full border border-slate-200 bg-white px-5 py-3 text-sm font-black text-slate-700">__MESSAGE__</button>
                      </div>
                    </div>
                  </section>
                  <section class="grid gap-6 xl:grid-cols-[minmax(0,1fr)_320px]">
                    <div class="rounded-[32px] border border-slate-200 bg-white p-6 shadow-sm">
                      <h2 class="text-2xl font-black text-slate-900">__WORKS__</h2>
                      <div class="mt-5 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
                        <template x-for="item in [
                          {title:'通勤西装 + 直筒裤', cover:'https://images.unsplash.com/photo-1529139574466-a303027c1d8b?q=80&w=1200'},
                          {title:'针织开衫 + 半裙', cover:'https://images.unsplash.com/photo-1483985988355-763728e1935b?q=80&w=1200'},
                          {title:'风衣旅行穿搭', cover:'https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?q=80&w=1200'}
                        ]" :key="item.title">
                          <article class="overflow-hidden rounded-[24px] border border-slate-200 bg-slate-50">
                            <img :src="item.cover" class="aspect-[4/5] w-full object-cover"/>
                            <div class="p-4 text-sm font-semibold text-slate-800" x-text="item.title"></div>
                          </article>
                        </template>
                      </div>
                    </div>
                    <aside class="space-y-4">
                      <section class="rounded-[32px] border border-slate-200 bg-white p-5 shadow-sm">
                        <div class="text-sm font-black text-slate-900">__STATS__</div>
                        <div class="mt-4 grid grid-cols-3 gap-3 text-center">
                          <div class="rounded-2xl bg-slate-50 p-3"><div class="text-lg font-black text-slate-900">12.8k</div><div class="mt-1 text-[11px] text-slate-500">__FOLLOWERS__</div></div>
                          <div class="rounded-2xl bg-slate-50 p-3"><div class="text-lg font-black text-slate-900">846</div><div class="mt-1 text-[11px] text-slate-500">__FOLLOWS__</div></div>
                          <div class="rounded-2xl bg-slate-50 p-3"><div class="text-lg font-black text-slate-900">92</div><div class="mt-1 text-[11px] text-slate-500">__NOTES__</div></div>
                        </div>
                      </section>
                    </aside>
                  </section>
                </div>
                """
                .replace("__ID__", route.id)
                .replace("__TITLE__", escapeHtml(route.name))
                .replace("__DESC__", escapeHtml(pageSpec.getDescription()))
                .replace("__TAG_A__", zh ? "通勤穿搭" : "Workwear")
                .replace("__TAG_B__", zh ? "小个子" : "Petite")
                .replace("__FOLLOW__", zh ? "关注作者" : "Follow")
                .replace("__MESSAGE__", zh ? "发消息" : "Message")
                .replace("__WORKS__", zh ? "作品集" : "Looks")
                .replace("__STATS__", zh ? "账号数据" : "Stats")
                .replace("__FOLLOWERS__", zh ? "粉丝" : "Followers")
                .replace("__FOLLOWS__", zh ? "关注" : "Following")
                .replace("__NOTES__", zh ? "笔记" : "Posts");
    }

    private String buildContentPublishFallbackComponent(ProjectManifest manifest, Route route, ProjectManifest.PageSpec pageSpec) {
        boolean zh = manifest.getMetaData() == null || !"EN".equalsIgnoreCase(manifest.getMetaData().getOrDefault("lang", "ZH"));
        return """
                <div x-show="hash === '#__ID__'" class="animate-fade-in pb-10 space-y-6">
                  <section class="rounded-[32px] border border-slate-200 bg-white p-7 shadow-sm">
                    <h1 class="text-3xl font-black text-slate-900">__TITLE__</h1>
                    <p class="mt-3 text-sm leading-7 text-slate-500">__DESC__</p>
                  </section>
                  <section class="grid gap-6 xl:grid-cols-[minmax(0,1fr)_320px]">
                    <div class="rounded-[32px] border border-slate-200 bg-white p-6 shadow-sm space-y-5">
                      <div class="rounded-2xl border border-dashed border-slate-200 bg-slate-50 p-6 text-sm text-slate-500">__UPLOAD__</div>
                      <input class="w-full rounded-2xl border border-slate-200 px-4 py-3 text-sm" placeholder="__TITLE_PLACEHOLDER__"/>
                      <textarea class="h-40 w-full rounded-2xl border border-slate-200 px-4 py-3 text-sm" placeholder="__BODY_PLACEHOLDER__"></textarea>
                      <div class="grid gap-4 md:grid-cols-2">
                        <input class="w-full rounded-2xl border border-slate-200 px-4 py-3 text-sm" placeholder="__TAG_PLACEHOLDER__"/>
                        <input class="w-full rounded-2xl border border-slate-200 px-4 py-3 text-sm" placeholder="__SCENE_PLACEHOLDER__"/>
                      </div>
                      <div class="flex gap-3">
                        <button class="rounded-full border border-slate-200 bg-white px-5 py-3 text-sm font-black text-slate-700">__DRAFT__</button>
                        <button class="shell-primary-button rounded-full px-5 py-3 text-sm font-black text-white">__SUBMIT__</button>
                      </div>
                    </div>
                    <aside class="rounded-[32px] border border-slate-200 bg-white p-5 shadow-sm">
                      <div class="text-sm font-black text-slate-900">__CHECKLIST__</div>
                      <div class="mt-4 space-y-3 text-sm text-slate-600">
                        <div class="rounded-2xl bg-slate-50 p-4">1. __CHECK_A__</div>
                        <div class="rounded-2xl bg-slate-50 p-4">2. __CHECK_B__</div>
                        <div class="rounded-2xl bg-slate-50 p-4">3. __CHECK_C__</div>
                      </div>
                    </aside>
                  </section>
                </div>
                """
                .replace("__ID__", route.id)
                .replace("__TITLE__", escapeHtml(route.name))
                .replace("__DESC__", escapeHtml(pageSpec.getDescription()))
                .replace("__UPLOAD__", zh ? "上传图片 / 视频并选择封面" : "Upload media and pick a cover")
                .replace("__TITLE_PLACEHOLDER__", zh ? "写一个让人想点开的标题..." : "Write a title...")
                .replace("__BODY_PLACEHOLDER__", zh ? "记录今天的穿搭灵感、单品信息和搭配场景..." : "Describe the look, pieces, and context...")
                .replace("__TAG_PLACEHOLDER__", zh ? "话题 / 品牌 / 单品" : "Tags / brand / item")
                .replace("__SCENE_PLACEHOLDER__", zh ? "场景 / 尺码 / 身材参考" : "Scene / size / fit note")
                .replace("__DRAFT__", zh ? "保存草稿" : "Save draft")
                .replace("__SUBMIT__", zh ? "发布笔记" : "Publish")
                .replace("__CHECKLIST__", zh ? "发布前检查" : "Checklist")
                .replace("__CHECK_A__", zh ? "封面和标题是否清楚" : "Cover and title ready")
                .replace("__CHECK_B__", zh ? "是否补充单品与场景信息" : "Piece and scene details added")
                .replace("__CHECK_C__", zh ? "是否添加标签与品牌" : "Tags and brands added");
    }

    private String buildGenericFallbackComponent(ProjectManifest manifest, Route route, ProjectManifest.PageSpec pageSpec) {
        boolean zh = manifest.getMetaData() == null || !"EN".equalsIgnoreCase(manifest.getMetaData().getOrDefault("lang", "ZH"));
        GenericWorkflowProfile profile = buildGenericWorkflowProfile(manifest, zh);
        return switch (resolveShellFlavor(manifest, false)) {
            case PIPELINE -> buildPipelineBoardFallbackComponent(manifest, route, pageSpec, profile, zh);
            case CLINIC, FITNESS, SERVICE ->
                    buildBookingStudioFallbackComponent(manifest, route, pageSpec, profile, zh);
            case VEHICLE, PROPERTY, EVENT, COMMERCE ->
                    buildMarketplaceHubFallbackComponent(manifest, route, pageSpec, profile, zh);
            case OPS -> buildOpsCommandFallbackComponent(manifest, route, pageSpec, profile, zh);
            case LEARNING -> buildLearningCampusFallbackComponent(manifest, route, pageSpec, profile, zh);
            default -> buildDefaultWorkflowFallbackComponent(manifest, route, pageSpec, profile, zh);
        };
    }

    private String buildDefaultWorkflowFallbackComponent(ProjectManifest manifest, Route route, ProjectManifest.PageSpec pageSpec, GenericWorkflowProfile profile, boolean zh) {
        String title = escapeHtml(route.name);
        String description = escapeHtml(pageSpec != null && pageSpec.getDescription() != null
                ? pageSpec.getDescription()
                : manifest.getUserIntent());
        String itemJson = serializeSeedItems(profile.items());
        String nextRoute = nextRouteId(route.id);
        return """
                <div x-show="hash === '#__ID__'" data-lingnow-flow="__FLOW__" data-lingnow-layout="generic-workflow" class="animate-fade-in pb-10 space-y-6">
                  <section class="overflow-hidden rounded-[32px] border border-slate-200 bg-white shadow-sm">
                    <div class="grid gap-0 lg:grid-cols-[minmax(0,1fr)_360px]">
                      <div class="p-7">
                        <span class="shell-soft-pill inline-flex items-center rounded-full px-3 py-1 text-xs font-black">__SURFACE__</span>
                        <h1 class="mt-4 max-w-3xl text-3xl font-black tracking-tight text-slate-900">__TITLE__</h1>
                        <p class="mt-3 max-w-3xl text-sm leading-7 text-slate-600">__DESCRIPTION__</p>
                        <div class="mt-6 flex flex-wrap gap-3">
                          <button @click="pickSlot({day:'__SLOT_DAY__', time:'__SLOT_TIME__', type:'__TITLE__', owner:'LingNow'}); toast='__TOAST_SELECT__'" data-lingnow-action="select-workflow-item" class="shell-primary-button rounded-full px-5 py-3 text-sm font-black transition">__PRIMARY_ACTION__</button>
                          <button @click="confirmBooking()" data-lingnow-action="confirm-workflow" class="rounded-full border border-slate-200 bg-white px-5 py-3 text-sm font-black text-slate-700 hover:bg-slate-50">__SECONDARY_ACTION__</button>
                          <button @click="advanceOrder({id:'__CODE__-01', title:'__TITLE__', stage:'__ADVANCED_STAGE__'}); go('__NEXT_ROUTE__')" data-lingnow-action="advance-workflow" class="shell-soft-button rounded-full border border-transparent px-5 py-3 text-sm font-black transition">__TERTIARY_ACTION__</button>
                        </div>
                      </div>
                      <aside class="border-t border-slate-200 bg-slate-950 p-7 text-white lg:border-l lg:border-t-0">
                        <div class="text-xs font-black uppercase tracking-[0.25em] text-indigo-200">__STATUS_TITLE__</div>
                        <div class="mt-5 grid grid-cols-3 gap-3">
                          <div class="rounded-2xl bg-white/10 p-4"><div class="text-2xl font-black">__METRIC_A__</div><div class="mt-1 text-[11px] text-slate-300">__SIGNAL_A__</div></div>
                          <div class="rounded-2xl bg-white/10 p-4"><div class="text-2xl font-black">__METRIC_B__</div><div class="mt-1 text-[11px] text-slate-300">__SIGNAL_B__</div></div>
                          <div class="rounded-2xl bg-white/10 p-4"><div class="text-2xl font-black">__METRIC_C__</div><div class="mt-1 text-[11px] text-slate-300">__SIGNAL_C__</div></div>
                        </div>
                        <div class="mt-5 rounded-3xl bg-white/10 p-5 text-sm leading-7 text-slate-200">__STATUS_DESC__</div>
                      </aside>
                    </div>
                  </section>
                
                  <section class="rounded-[32px] border border-slate-200 bg-white p-6 shadow-sm">
                    <div class="mb-5 flex flex-col gap-2 md:flex-row md:items-end md:justify-between">
                      <div>
                        <h2 class="text-2xl font-black text-slate-900">__SECTION_TITLE__</h2>
                        <p class="mt-1 text-sm text-slate-500">__SECTION_DESC__</p>
                      </div>
                      <span class="rounded-full bg-slate-100 px-3 py-1 text-xs font-black text-slate-500">__CODE__</span>
                    </div>
                    <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                      <template x-for='item in __ITEMS__' :key="item.id">
                        <article @click="openDetail(item)" data-lingnow-action="open-detail" class="group cursor-pointer overflow-hidden rounded-[26px] border border-slate-200 bg-slate-50 transition hover:-translate-y-1 hover:bg-white hover:shadow-xl">
                          <div class="aspect-[4/2.7] overflow-hidden bg-slate-200">
                            <img :src="item.cover" class="h-full w-full object-cover transition duration-500 group-hover:scale-105">
                          </div>
                          <div class="space-y-3 p-4">
                            <div class="flex items-center justify-between gap-3">
                              <span class="rounded-full bg-white px-3 py-1 text-[11px] font-black text-slate-600 shadow-sm" x-text="item.category"></span>
                              <span class="text-xs font-bold text-indigo-600" x-text="item.status"></span>
                            </div>
                            <h3 class="line-clamp-2 text-lg font-black leading-tight text-slate-900" x-text="item.title"></h3>
                            <p class="line-clamp-3 text-sm leading-6 text-slate-600" x-text="item.description"></p>
                            <div class="flex items-center justify-between border-t border-slate-200 pt-3">
                              <span class="text-xs font-bold text-slate-500" x-text="item.metric"></span>
                              <button @click.stop="openDetail(item)" data-lingnow-action="inspect-item" class="rounded-full bg-slate-950 px-3 py-1.5 text-xs font-black text-white">__INSPECT__</button>
                            </div>
                          </div>
                        </article>
                      </template>
                    </div>
                  </section>
                
                  <section class="grid gap-5 xl:grid-cols-[minmax(0,1fr)_340px]">
                    <div class="rounded-[32px] border border-slate-200 bg-white p-6 shadow-sm">
                      <h2 class="text-xl font-black text-slate-900">__FLOW_TITLE__</h2>
                      <div class="mt-5 grid gap-3 md:grid-cols-3">
                        <button @click="pickSlot({day:'__SLOT_DAY__', time:'__SLOT_TIME__', type:'__TITLE__', owner:'LingNow'}); toast='__TOAST_SELECT__'" data-lingnow-action="pick-generic-slot" :class="selectedSlot ? 'shell-flow-active' : 'border-slate-200 bg-slate-50 text-slate-700'" class="shell-flow-hover rounded-2xl border p-4 text-left text-sm font-black transition">1. __STEP_A__</button>
                        <button @click="confirmBooking()" data-lingnow-action="confirm-generic-flow" :class="bookingConfirmed ? 'border-emerald-300 bg-emerald-50 text-emerald-700' : 'border-slate-200 bg-slate-50 text-slate-700'" class="rounded-2xl border p-4 text-left text-sm font-black transition hover:bg-emerald-50">2. __STEP_B__</button>
                        <button @click="submitInquiry()" data-lingnow-action="submit-generic-flow" :class="inquirySubmitted ? 'shell-flow-active' : 'border-slate-200 bg-slate-50 text-slate-700'" class="shell-flow-hover rounded-2xl border p-4 text-left text-sm font-black transition">3. __STEP_C__</button>
                      </div>
                    </div>
                    <aside class="rounded-[32px] border border-emerald-100 bg-emerald-50 p-6 text-emerald-900 shadow-sm">
                      <div class="text-xs font-black uppercase tracking-[0.25em] text-emerald-600">__LIVE_STATE__</div>
                      <div class="mt-4 space-y-3 text-sm">
                        <div class="rounded-2xl bg-white/70 p-4"><span class="font-black">__SELECTED_LABEL__：</span><span x-text="selectedSlot ? `${selectedSlot.day} ${selectedSlot.time}` : '__EMPTY__'"></span></div>
                        <div class="rounded-2xl bg-white/70 p-4"><span class="font-black">__ORDER_LABEL__：</span><span x-text="activeOrder ? `${activeOrder.id} · ${activeOrder.stage}` : '__EMPTY__'"></span></div>
                        <div class="rounded-2xl bg-white/70 p-4"><span class="font-black">Toast：</span><span x-text="toast || '__EMPTY__'"></span></div>
                      </div>
                    </aside>
                  </section>
                </div>
                """
                .replace("__ID__", route.id)
                .replace("__FLOW__", profile.flowKey())
                .replace("__SURFACE__", escapeHtml(profile.surfaceLabel()))
                .replace("__TITLE__", title)
                .replace("__DESCRIPTION__", description)
                .replace("__SLOT_DAY__", escapeHtml(profile.slotDay()))
                .replace("__SLOT_TIME__", escapeHtml(profile.slotTime()))
                .replace("__TOAST_SELECT__", escapeHtml(profile.toastSelect()))
                .replace("__PRIMARY_ACTION__", escapeHtml(profile.primaryAction()))
                .replace("__SECONDARY_ACTION__", escapeHtml(profile.secondaryAction()))
                .replace("__TERTIARY_ACTION__", escapeHtml(profile.tertiaryAction()))
                .replace("__CODE__", escapeHtml(profile.codePrefix()))
                .replace("__ADVANCED_STAGE__", escapeHtml(profile.advancedStage()))
                .replace("__NEXT_ROUTE__", nextRoute)
                .replace("__STATUS_TITLE__", escapeHtml(profile.statusTitle()))
                .replace("__METRIC_A__", escapeHtml(profile.metricA()))
                .replace("__METRIC_B__", escapeHtml(profile.metricB()))
                .replace("__METRIC_C__", escapeHtml(profile.metricC()))
                .replace("__SIGNAL_A__", escapeHtml(profile.signalA()))
                .replace("__SIGNAL_B__", escapeHtml(profile.signalB()))
                .replace("__SIGNAL_C__", escapeHtml(profile.signalC()))
                .replace("__STATUS_DESC__", escapeHtml(profile.statusDescription()))
                .replace("__SECTION_TITLE__", escapeHtml(profile.sectionTitle()))
                .replace("__SECTION_DESC__", escapeHtml(profile.sectionDescription()))
                .replace("__ITEMS__", itemJson)
                .replace("__INSPECT__", zh ? "查看" : "Inspect")
                .replace("__FLOW_TITLE__", escapeHtml(profile.flowTitle()))
                .replace("__STEP_A__", escapeHtml(profile.stepA()))
                .replace("__STEP_B__", escapeHtml(profile.stepB()))
                .replace("__STEP_C__", escapeHtml(profile.stepC()))
                .replace("__LIVE_STATE__", zh ? "原型状态" : "Prototype state")
                .replace("__SELECTED_LABEL__", escapeHtml(profile.selectedLabel()))
                .replace("__ORDER_LABEL__", escapeHtml(profile.orderLabel()))
                .replace("__EMPTY__", zh ? "待操作" : "Waiting");
    }

    private String buildPipelineBoardFallbackComponent(ProjectManifest manifest, Route route, ProjectManifest.PageSpec pageSpec, GenericWorkflowProfile profile, boolean zh) {
        String title = escapeHtml(route.name);
        String description = escapeHtml(pageSpec != null && pageSpec.getDescription() != null ? pageSpec.getDescription() : manifest.getUserIntent());
        String itemJson = serializeSeedItems(profile.items());
        String nextRoute = nextRouteId(route.id);
        return """
                <div x-show="hash === '#__ID__'" data-lingnow-flow="__FLOW__" data-lingnow-layout="pipeline-board" class="animate-fade-in pb-10 space-y-6">
                  <section class="grid gap-6 xl:grid-cols-[minmax(0,1fr)_360px]">
                    <div class="rounded-[32px] border border-slate-200 bg-white p-7 shadow-sm">
                      <div class="flex flex-wrap items-center gap-3">
                        <span class="shell-soft-pill inline-flex items-center rounded-full px-3 py-1 text-xs font-black">__SURFACE__</span>
                        <span class="rounded-full bg-slate-100 px-3 py-1 text-xs font-black text-slate-500">__CODE__</span>
                      </div>
                      <h1 class="mt-4 text-3xl font-black tracking-tight text-slate-900">__TITLE__</h1>
                      <p class="mt-3 max-w-3xl text-sm leading-7 text-slate-600">__DESCRIPTION__</p>
                      <div class="mt-6 grid gap-3 md:grid-cols-3">
                        <div class="rounded-[26px] border border-slate-200 bg-slate-50 p-4"><div class="text-xs font-bold uppercase tracking-[0.2em] text-slate-400">__SIGNAL_A__</div><div class="mt-3 text-3xl font-black text-slate-900">__METRIC_A__</div></div>
                        <div class="rounded-[26px] border border-slate-200 bg-slate-50 p-4"><div class="text-xs font-bold uppercase tracking-[0.2em] text-slate-400">__SIGNAL_B__</div><div class="mt-3 text-3xl font-black text-slate-900">__METRIC_B__</div></div>
                        <div class="rounded-[26px] border border-slate-200 bg-slate-50 p-4"><div class="text-xs font-bold uppercase tracking-[0.2em] text-slate-400">__SIGNAL_C__</div><div class="mt-3 text-3xl font-black text-slate-900">__METRIC_C__</div></div>
                      </div>
                      <div class="mt-6 flex flex-wrap gap-3">
                        <button @click="selectedLead = '__TITLE__'; showToast('__TOAST_SELECT__')" data-lingnow-action="select-workflow-item" class="shell-primary-button rounded-full px-5 py-3 text-sm font-black transition">__PRIMARY_ACTION__</button>
                        <button @click="confirmBooking(); showToast('__SECONDARY_ACTION__ 已记录')" data-lingnow-action="confirm-workflow" class="rounded-full border border-slate-200 bg-white px-5 py-3 text-sm font-black text-slate-700 hover:bg-slate-50">__SECONDARY_ACTION__</button>
                        <button @click="advanceOrder({id:'__CODE__-HQ', title:'__TITLE__', stage:'__ADVANCED_STAGE__'}); go('__NEXT_ROUTE__')" data-lingnow-action="advance-workflow" class="shell-soft-button rounded-full px-5 py-3 text-sm font-black transition">__TERTIARY_ACTION__</button>
                      </div>
                    </div>
                    <aside class="rounded-[32px] bg-slate-950 p-7 text-white shadow-sm">
                      <div class="text-xs font-black uppercase tracking-[0.25em] text-white/60">__STATUS_TITLE__</div>
                      <p class="mt-4 text-sm leading-7 text-slate-300">__STATUS_DESC__</p>
                      <div class="mt-5 space-y-3">
                        <div class="rounded-[24px] bg-white/10 p-4">
                          <div class="text-[11px] font-bold uppercase tracking-[0.2em] text-slate-400">__SELECTED_LABEL__</div>
                          <div class="mt-2 text-lg font-black text-white" x-text="selectedLead || '__EMPTY__'"></div>
                        </div>
                        <div class="rounded-[24px] bg-white/10 p-4">
                          <div class="text-[11px] font-bold uppercase tracking-[0.2em] text-slate-400">__ORDER_LABEL__</div>
                          <div class="mt-2 text-lg font-black text-white" x-text="activeOrder ? `${activeOrder.id} · ${activeOrder.stage}` : '__EMPTY__'"></div>
                        </div>
                        <div class="rounded-[24px] bg-white/10 p-4">
                          <div class="text-[11px] font-bold uppercase tracking-[0.2em] text-slate-400">Toast</div>
                          <div class="mt-2 text-sm text-slate-200" x-text="toast || '__EMPTY__'"></div>
                        </div>
                      </div>
                    </aside>
                  </section>
                
                  <section class="grid gap-6 xl:grid-cols-[minmax(0,1fr)_320px]">
                    <div class="rounded-[32px] border border-slate-200 bg-white p-6 shadow-sm">
                      <div class="mb-5 flex items-end justify-between gap-4">
                        <div>
                          <h2 class="text-2xl font-black text-slate-900">__SECTION_TITLE__</h2>
                          <p class="mt-1 text-sm text-slate-500">__SECTION_DESC__</p>
                        </div>
                        <span class="rounded-full bg-slate-100 px-3 py-1 text-xs font-black text-slate-500">__FLOW_TITLE__</span>
                      </div>
                      <div class="space-y-4">
                        <template x-for='item in __ITEMS__' :key="item.id">
                          <article class="rounded-[28px] border border-slate-200 bg-slate-50 p-5 transition hover:border-slate-300 hover:bg-white">
                            <div class="flex flex-col gap-4 lg:flex-row lg:items-center">
                              <button @click="selectedLead = item.title; openDetail(item)" data-lingnow-action="open-detail" class="flex-1 text-left">
                                <div class="flex flex-wrap items-center gap-2 text-[11px] font-bold uppercase tracking-[0.2em] text-slate-400">
                                  <span x-text="item.category"></span>
                                  <span>·</span>
                                  <span x-text="item.status"></span>
                                </div>
                                <h3 class="mt-3 text-2xl font-black text-slate-900" x-text="item.title"></h3>
                                <p class="mt-2 text-sm leading-7 text-slate-600" x-text="item.description"></p>
                                <div class="mt-4 flex flex-wrap items-center gap-3 text-sm text-slate-500">
                                  <span class="rounded-full bg-white px-3 py-1 font-bold text-slate-700" x-text="item.author"></span>
                                  <span x-text="item.metric"></span>
                                </div>
                              </button>
                              <div class="flex shrink-0 flex-wrap gap-2 lg:w-[250px] lg:justify-end">
                                <button @click.stop="selectedLead = item.title; showToast('__TOAST_SELECT__')" data-lingnow-action="select-workflow-item" class="shell-soft-button rounded-full px-4 py-2 text-sm font-black transition">__STEP_A__</button>
                                <button @click.stop="confirmBooking(); selectedLead = item.title; showToast('__SECONDARY_ACTION__ 已记录')" data-lingnow-action="confirm-workflow" class="rounded-full border border-slate-200 bg-white px-4 py-2 text-sm font-black text-slate-700 hover:bg-slate-50">__STEP_B__</button>
                                <button @click.stop="advanceOrder({id:item.id, title:item.title, stage:'__ADVANCED_STAGE__'}); selectedLead = item.title" data-lingnow-action="advance-workflow" class="shell-primary-button rounded-full px-4 py-2 text-sm font-black transition">__STEP_C__</button>
                              </div>
                            </div>
                          </article>
                        </template>
                      </div>
                    </div>
                    <aside class="space-y-4">
                      <section class="rounded-[32px] border border-slate-200 bg-white p-5 shadow-sm">
                        <div class="text-sm font-black text-slate-900">__FLOW_TITLE__</div>
                        <div class="mt-4 space-y-3">
                          <div class="rounded-2xl bg-slate-50 p-4 text-sm text-slate-700"><span class="font-black">1.</span> __STEP_A__</div>
                          <div class="rounded-2xl bg-slate-50 p-4 text-sm text-slate-700"><span class="font-black">2.</span> __STEP_B__</div>
                          <div class="rounded-2xl bg-slate-50 p-4 text-sm text-slate-700"><span class="font-black">3.</span> __STEP_C__</div>
                        </div>
                      </section>
                      <section class="rounded-[32px] border border-slate-200 bg-white p-5 shadow-sm">
                        <div class="text-sm font-black text-slate-900">__LIVE_STATE__</div>
                        <div class="mt-4 space-y-3 text-sm">
                          <div class="rounded-2xl bg-slate-50 p-4"><span class="font-black">__SELECTED_LABEL__：</span><span x-text="selectedLead || '__EMPTY__'"></span></div>
                          <div class="rounded-2xl bg-slate-50 p-4"><span class="font-black">__ORDER_LABEL__：</span><span x-text="activeOrder ? `${activeOrder.id} · ${activeOrder.stage}` : '__EMPTY__'"></span></div>
                        </div>
                      </section>
                    </aside>
                  </section>
                </div>
                """
                .replace("__ID__", route.id)
                .replace("__FLOW__", profile.flowKey())
                .replace("__SURFACE__", escapeHtml(profile.surfaceLabel()))
                .replace("__TITLE__", title)
                .replace("__DESCRIPTION__", description)
                .replace("__TOAST_SELECT__", escapeHtml(profile.toastSelect()))
                .replace("__SECONDARY_ACTION__", escapeHtml(profile.secondaryAction()))
                .replace("__TERTIARY_ACTION__", escapeHtml(profile.tertiaryAction()))
                .replace("__CODE__", escapeHtml(profile.codePrefix()))
                .replace("__ADVANCED_STAGE__", escapeHtml(profile.advancedStage()))
                .replace("__NEXT_ROUTE__", nextRoute)
                .replace("__STATUS_TITLE__", escapeHtml(profile.statusTitle()))
                .replace("__METRIC_A__", escapeHtml(profile.metricA()))
                .replace("__METRIC_B__", escapeHtml(profile.metricB()))
                .replace("__METRIC_C__", escapeHtml(profile.metricC()))
                .replace("__SIGNAL_A__", escapeHtml(profile.signalA()))
                .replace("__SIGNAL_B__", escapeHtml(profile.signalB()))
                .replace("__SIGNAL_C__", escapeHtml(profile.signalC()))
                .replace("__STATUS_DESC__", escapeHtml(profile.statusDescription()))
                .replace("__SECTION_TITLE__", escapeHtml(profile.sectionTitle()))
                .replace("__SECTION_DESC__", escapeHtml(profile.sectionDescription()))
                .replace("__ITEMS__", itemJson)
                .replace("__FLOW_TITLE__", escapeHtml(profile.flowTitle()))
                .replace("__STEP_A__", escapeHtml(profile.stepA()))
                .replace("__STEP_B__", escapeHtml(profile.stepB()))
                .replace("__STEP_C__", escapeHtml(profile.stepC()))
                .replace("__LIVE_STATE__", zh ? "当前推进" : "Current progress")
                .replace("__SELECTED_LABEL__", escapeHtml(profile.selectedLabel()))
                .replace("__ORDER_LABEL__", escapeHtml(profile.orderLabel()))
                .replace("__EMPTY__", zh ? "待选择" : "Waiting");
    }

    private String buildBookingStudioFallbackComponent(ProjectManifest manifest, Route route, ProjectManifest.PageSpec pageSpec, GenericWorkflowProfile profile, boolean zh) {
        String title = escapeHtml(route.name);
        String description = escapeHtml(pageSpec != null && pageSpec.getDescription() != null ? pageSpec.getDescription() : manifest.getUserIntent());
        String itemJson = serializeSeedItems(profile.items());
        String nextRoute = nextRouteId(route.id);
        return """
                <div x-show="hash === '#__ID__'" data-lingnow-flow="__FLOW__" data-lingnow-layout="booking-studio" class="animate-fade-in pb-10 space-y-6">
                  <section class="grid gap-6 xl:grid-cols-[minmax(0,1fr)_360px]">
                    <div class="rounded-[32px] border border-slate-200 bg-white p-7 shadow-sm">
                      <div class="flex flex-wrap items-center gap-3">
                        <span class="shell-soft-pill inline-flex items-center rounded-full px-3 py-1 text-xs font-black">__SURFACE__</span>
                        <span class="rounded-full bg-slate-100 px-3 py-1 text-xs font-black text-slate-500">__SIGNAL_A__ / __SIGNAL_B__ / __SIGNAL_C__</span>
                      </div>
                      <h1 class="mt-4 text-3xl font-black tracking-tight text-slate-900">__TITLE__</h1>
                      <p class="mt-3 max-w-3xl text-sm leading-7 text-slate-600">__DESCRIPTION__</p>
                      <div class="mt-6 grid gap-3 md:grid-cols-3">
                        <div class="rounded-[26px] bg-slate-50 p-4"><div class="text-xs font-bold uppercase tracking-[0.2em] text-slate-400">__SIGNAL_A__</div><div class="mt-3 text-3xl font-black text-slate-900">__METRIC_A__</div></div>
                        <div class="rounded-[26px] bg-slate-50 p-4"><div class="text-xs font-bold uppercase tracking-[0.2em] text-slate-400">__SIGNAL_B__</div><div class="mt-3 text-3xl font-black text-slate-900">__METRIC_B__</div></div>
                        <div class="rounded-[26px] bg-slate-50 p-4"><div class="text-xs font-bold uppercase tracking-[0.2em] text-slate-400">__SIGNAL_C__</div><div class="mt-3 text-3xl font-black text-slate-900">__METRIC_C__</div></div>
                      </div>
                    </div>
                    <aside class="rounded-[32px] border border-slate-200 bg-white p-6 shadow-sm">
                      <div class="text-xs font-black uppercase tracking-[0.25em] text-slate-400">__STATUS_TITLE__</div>
                      <div class="mt-4 rounded-[28px] bg-slate-950 p-5 text-white">
                        <div class="text-sm text-slate-300">__SLOT_DAY__</div>
                        <div class="mt-2 text-3xl font-black">__SLOT_TIME__</div>
                        <div class="mt-2 text-sm leading-6 text-slate-300">__STATUS_DESC__</div>
                        <div class="mt-5 flex flex-wrap gap-3">
                          <button @click="pickSlot({day:'__SLOT_DAY__', time:'__SLOT_TIME__', type:'__TITLE__', owner:'LingNow'}); toast='__TOAST_SELECT__'" data-lingnow-action="select-workflow-item" class="shell-primary-button rounded-full px-4 py-2 text-sm font-black transition">__PRIMARY_ACTION__</button>
                          <button @click="confirmBooking()" data-lingnow-action="confirm-workflow" class="rounded-full border border-white/20 bg-white/10 px-4 py-2 text-sm font-black text-white">__SECONDARY_ACTION__</button>
                        </div>
                      </div>
                      <div class="mt-4 rounded-[24px] bg-emerald-50 p-4 text-sm text-emerald-900">
                        <div class="text-xs font-black uppercase tracking-[0.2em] text-emerald-600">__LIVE_STATE__</div>
                        <div class="mt-3 space-y-2">
                          <div><span class="font-black">__SELECTED_LABEL__：</span><span x-text="selectedSlot ? `${selectedSlot.day} ${selectedSlot.time}` : '__EMPTY__'"></span></div>
                          <div><span class="font-black">__ORDER_LABEL__：</span><span x-text="activeOrder ? `${activeOrder.id} · ${activeOrder.stage}` : '__EMPTY__'"></span></div>
                        </div>
                      </div>
                    </aside>
                  </section>
                
                  <section class="grid gap-6 xl:grid-cols-[minmax(0,1fr)_320px]">
                    <div class="rounded-[32px] border border-slate-200 bg-white p-6 shadow-sm">
                      <div class="mb-5 flex items-end justify-between gap-4">
                        <div>
                          <h2 class="text-2xl font-black text-slate-900">__SECTION_TITLE__</h2>
                          <p class="mt-1 text-sm text-slate-500">__SECTION_DESC__</p>
                        </div>
                        <span class="rounded-full bg-slate-100 px-3 py-1 text-xs font-black text-slate-500">__CODE__</span>
                      </div>
                      <div class="grid gap-4 md:grid-cols-2">
                        <template x-for='item in __ITEMS__' :key="item.id">
                          <article @click="openDetail(item)" data-lingnow-action="open-detail" class="overflow-hidden rounded-[28px] border border-slate-200 bg-slate-50 transition hover:-translate-y-1 hover:bg-white hover:shadow-lg">
                            <div class="aspect-[4/2.6] overflow-hidden bg-slate-100">
                              <img :src="item.cover" class="h-full w-full object-cover">
                            </div>
                            <div class="space-y-3 p-5">
                              <div class="flex items-center justify-between gap-3">
                                <span class="rounded-full bg-white px-3 py-1 text-[11px] font-black text-slate-600" x-text="item.category"></span>
                                <span class="text-xs font-black text-slate-500" x-text="item.status"></span>
                              </div>
                              <h3 class="text-xl font-black text-slate-900" x-text="item.title"></h3>
                              <p class="text-sm leading-7 text-slate-600" x-text="item.description"></p>
                              <div class="flex flex-wrap items-center justify-between gap-3 border-t border-slate-200 pt-3">
                                <div class="text-sm font-bold text-slate-500" x-text="item.metric"></div>
                                <div class="flex flex-wrap gap-2">
                                  <button @click.stop="pickSlot({day:'__SLOT_DAY__', time:'__SLOT_TIME__', type:item.title, owner:item.author}); toast='__TOAST_SELECT__'" data-lingnow-action="select-workflow-item" class="shell-soft-button rounded-full px-3 py-1.5 text-xs font-black transition">__STEP_A__</button>
                                  <button @click.stop="openDetail(item)" data-lingnow-action="inspect-item" class="rounded-full bg-slate-950 px-3 py-1.5 text-xs font-black text-white">__INSPECT__</button>
                                </div>
                              </div>
                            </div>
                          </article>
                        </template>
                      </div>
                    </div>
                    <aside class="space-y-4">
                      <section class="rounded-[32px] border border-slate-200 bg-white p-5 shadow-sm">
                        <div class="text-sm font-black text-slate-900">__FLOW_TITLE__</div>
                        <div class="mt-4 space-y-3">
                          <button @click="pickSlot({day:'__SLOT_DAY__', time:'__SLOT_TIME__', type:'__TITLE__', owner:'LingNow'}); toast='__TOAST_SELECT__'" data-lingnow-action="pick-generic-slot" :class="selectedSlot ? 'shell-flow-active' : 'border-slate-200 bg-slate-50 text-slate-700'" class="shell-flow-hover block w-full rounded-2xl border px-4 py-4 text-left text-sm font-black transition">1. __STEP_A__</button>
                          <button @click="confirmBooking()" data-lingnow-action="confirm-generic-flow" :class="bookingConfirmed ? 'border-emerald-300 bg-emerald-50 text-emerald-700' : 'border-slate-200 bg-slate-50 text-slate-700'" class="block w-full rounded-2xl border px-4 py-4 text-left text-sm font-black transition hover:bg-emerald-50">2. __STEP_B__</button>
                          <button @click="advanceOrder({id:'__CODE__-01', title:'__TITLE__', stage:'__ADVANCED_STAGE__'}); go('__NEXT_ROUTE__')" data-lingnow-action="advance-workflow" class="shell-primary-button block w-full rounded-2xl px-4 py-4 text-left text-sm font-black transition">3. __STEP_C__</button>
                        </div>
                      </section>
                      <section class="rounded-[32px] border border-slate-200 bg-white p-5 shadow-sm">
                        <div class="text-sm font-black text-slate-900">__STATUS_TITLE__</div>
                        <p class="mt-3 text-sm leading-7 text-slate-600">__STATUS_DESC__</p>
                      </section>
                    </aside>
                  </section>
                </div>
                """
                .replace("__ID__", route.id)
                .replace("__FLOW__", profile.flowKey())
                .replace("__SURFACE__", escapeHtml(profile.surfaceLabel()))
                .replace("__TITLE__", title)
                .replace("__DESCRIPTION__", description)
                .replace("__SIGNAL_A__", escapeHtml(profile.signalA()))
                .replace("__SIGNAL_B__", escapeHtml(profile.signalB()))
                .replace("__SIGNAL_C__", escapeHtml(profile.signalC()))
                .replace("__METRIC_A__", escapeHtml(profile.metricA()))
                .replace("__METRIC_B__", escapeHtml(profile.metricB()))
                .replace("__METRIC_C__", escapeHtml(profile.metricC()))
                .replace("__SLOT_DAY__", escapeHtml(profile.slotDay()))
                .replace("__SLOT_TIME__", escapeHtml(profile.slotTime()))
                .replace("__TOAST_SELECT__", escapeHtml(profile.toastSelect()))
                .replace("__PRIMARY_ACTION__", escapeHtml(profile.primaryAction()))
                .replace("__SECONDARY_ACTION__", escapeHtml(profile.secondaryAction()))
                .replace("__ADVANCED_STAGE__", escapeHtml(profile.advancedStage()))
                .replace("__NEXT_ROUTE__", nextRoute)
                .replace("__STATUS_TITLE__", escapeHtml(profile.statusTitle()))
                .replace("__STATUS_DESC__", escapeHtml(profile.statusDescription()))
                .replace("__SECTION_TITLE__", escapeHtml(profile.sectionTitle()))
                .replace("__SECTION_DESC__", escapeHtml(profile.sectionDescription()))
                .replace("__CODE__", escapeHtml(profile.codePrefix()))
                .replace("__ITEMS__", itemJson)
                .replace("__INSPECT__", zh ? "查看" : "Inspect")
                .replace("__FLOW_TITLE__", escapeHtml(profile.flowTitle()))
                .replace("__STEP_A__", escapeHtml(profile.stepA()))
                .replace("__STEP_B__", escapeHtml(profile.stepB()))
                .replace("__STEP_C__", escapeHtml(profile.stepC()))
                .replace("__LIVE_STATE__", zh ? "预约状态" : "Booking state")
                .replace("__SELECTED_LABEL__", escapeHtml(profile.selectedLabel()))
                .replace("__ORDER_LABEL__", escapeHtml(profile.orderLabel()))
                .replace("__EMPTY__", zh ? "待操作" : "Waiting");
    }

    private String buildMarketplaceHubFallbackComponent(ProjectManifest manifest, Route route, ProjectManifest.PageSpec pageSpec, GenericWorkflowProfile profile, boolean zh) {
        String title = escapeHtml(route.name);
        String description = escapeHtml(pageSpec != null && pageSpec.getDescription() != null ? pageSpec.getDescription() : manifest.getUserIntent());
        String itemJson = serializeSeedItems(profile.items());
        String nextRoute = nextRouteId(route.id);
        return """
                <div x-show="hash === '#__ID__'" data-lingnow-flow="__FLOW__" data-lingnow-layout="marketplace-hub" class="animate-fade-in pb-10 space-y-6">
                  <section class="rounded-[32px] border border-slate-200 bg-white p-7 shadow-sm">
                    <div class="flex flex-col gap-5 xl:flex-row xl:items-end xl:justify-between">
                      <div>
                        <div class="flex flex-wrap items-center gap-3">
                          <span class="shell-soft-pill inline-flex items-center rounded-full px-3 py-1 text-xs font-black">__SURFACE__</span>
                          <span class="rounded-full bg-slate-100 px-3 py-1 text-xs font-black text-slate-500">__STATUS_TITLE__</span>
                        </div>
                        <h1 class="mt-4 text-3xl font-black tracking-tight text-slate-900">__TITLE__</h1>
                        <p class="mt-3 max-w-4xl text-sm leading-7 text-slate-600">__DESCRIPTION__</p>
                      </div>
                      <div class="flex flex-wrap gap-3">
                        <button @click="pickSlot({day:'__SLOT_DAY__', time:'__SLOT_TIME__', type:'__TITLE__', owner:'LingNow'}); toast='__TOAST_SELECT__'" data-lingnow-action="select-workflow-item" class="shell-primary-button rounded-full px-5 py-3 text-sm font-black transition">__PRIMARY_ACTION__</button>
                        <button @click="confirmBooking()" data-lingnow-action="confirm-workflow" class="rounded-full border border-slate-200 bg-white px-5 py-3 text-sm font-black text-slate-700 hover:bg-slate-50">__SECONDARY_ACTION__</button>
                        <button @click="advanceOrder({id:'__CODE__-01', title:'__TITLE__', stage:'__ADVANCED_STAGE__'}); go('__NEXT_ROUTE__')" data-lingnow-action="advance-workflow" class="shell-soft-button rounded-full px-5 py-3 text-sm font-black transition">__TERTIARY_ACTION__</button>
                      </div>
                    </div>
                    <div class="mt-6 flex flex-wrap gap-2 text-sm">
                      <span class="rounded-full bg-slate-100 px-4 py-2 font-semibold text-slate-700">__SIGNAL_A__ · __METRIC_A__</span>
                      <span class="rounded-full bg-slate-100 px-4 py-2 font-semibold text-slate-700">__SIGNAL_B__ · __METRIC_B__</span>
                      <span class="rounded-full bg-slate-100 px-4 py-2 font-semibold text-slate-700">__SIGNAL_C__ · __METRIC_C__</span>
                    </div>
                  </section>
                
                  <section class="grid gap-6 xl:grid-cols-[minmax(0,1fr)_320px]">
                    <div class="space-y-4">
                      <div class="rounded-[32px] border border-slate-200 bg-white p-6 shadow-sm">
                        <div class="mb-4 flex items-end justify-between gap-4">
                          <div>
                            <h2 class="text-2xl font-black text-slate-900">__SECTION_TITLE__</h2>
                            <p class="mt-1 text-sm text-slate-500">__SECTION_DESC__</p>
                          </div>
                          <span class="rounded-full bg-slate-100 px-3 py-1 text-xs font-black text-slate-500">__CODE__</span>
                        </div>
                        <div class="space-y-4">
                          <template x-for='item in __ITEMS__' :key="item.id">
                            <article class="rounded-[28px] border border-slate-200 bg-slate-50 p-4 transition hover:border-slate-300 hover:bg-white hover:shadow-lg">
                              <div class="grid gap-4 lg:grid-cols-[220px_minmax(0,1fr)_160px] lg:items-center">
                                <button @click="openDetail(item)" data-lingnow-action="open-detail" class="overflow-hidden rounded-[22px] bg-slate-100">
                                  <img :src="item.cover" class="h-full w-full object-cover">
                                </button>
                                <button @click="openDetail(item)" class="text-left">
                                  <div class="flex flex-wrap items-center gap-2">
                                    <span class="rounded-full bg-white px-3 py-1 text-[11px] font-black text-slate-600" x-text="item.category"></span>
                                    <span class="text-xs font-black text-slate-500" x-text="item.status"></span>
                                  </div>
                                  <h3 class="mt-3 text-2xl font-black text-slate-900" x-text="item.title"></h3>
                                  <p class="mt-2 text-sm leading-7 text-slate-600" x-text="item.description"></p>
                                  <div class="mt-3 flex flex-wrap items-center gap-3 text-sm text-slate-500">
                                    <span class="rounded-full bg-white px-3 py-1 font-bold text-slate-700" x-text="item.author"></span>
                                    <span x-text="item.metric"></span>
                                  </div>
                                </button>
                                <div class="flex flex-wrap justify-end gap-2 lg:flex-col">
                                  <button @click.stop="pickSlot({day:'__SLOT_DAY__', time:'__SLOT_TIME__', type:item.title, owner:item.author}); toast='__TOAST_SELECT__'" data-lingnow-action="select-workflow-item" class="shell-soft-button rounded-full px-4 py-2 text-sm font-black transition">__STEP_A__</button>
                                  <button @click.stop="confirmBooking(); showToast('__SECONDARY_ACTION__ 已记录')" data-lingnow-action="confirm-workflow" class="rounded-full border border-slate-200 bg-white px-4 py-2 text-sm font-black text-slate-700 hover:bg-slate-50">__STEP_B__</button>
                                  <button @click.stop="advanceOrder({id:item.id, title:item.title, stage:'__ADVANCED_STAGE__'})" data-lingnow-action="advance-workflow" class="shell-primary-button rounded-full px-4 py-2 text-sm font-black transition">__STEP_C__</button>
                                </div>
                              </div>
                            </article>
                          </template>
                        </div>
                      </div>
                    </div>
                    <aside class="space-y-4">
                      <section class="rounded-[32px] border border-slate-200 bg-white p-5 shadow-sm">
                        <div class="text-sm font-black text-slate-900">__FLOW_TITLE__</div>
                        <p class="mt-3 text-sm leading-7 text-slate-600">__STATUS_DESC__</p>
                        <div class="mt-4 space-y-3 text-sm">
                          <div class="rounded-2xl bg-slate-50 p-4"><span class="font-black">__SELECTED_LABEL__：</span><span x-text="selectedSlot ? `${selectedSlot.day} ${selectedSlot.time}` : '__EMPTY__'"></span></div>
                          <div class="rounded-2xl bg-slate-50 p-4"><span class="font-black">__ORDER_LABEL__：</span><span x-text="activeOrder ? `${activeOrder.id} · ${activeOrder.stage}` : '__EMPTY__'"></span></div>
                          <div class="rounded-2xl bg-slate-50 p-4"><span class="font-black">Toast：</span><span x-text="toast || '__EMPTY__'"></span></div>
                        </div>
                      </section>
                      <section class="rounded-[32px] border border-slate-200 bg-white p-5 shadow-sm">
                        <div class="text-sm font-black text-slate-900">__STATUS_TITLE__</div>
                        <div class="mt-4 grid grid-cols-3 gap-3 text-center">
                          <div class="rounded-2xl bg-slate-50 p-3"><div class="text-xl font-black text-slate-900">__METRIC_A__</div><div class="mt-1 text-[11px] text-slate-500">__SIGNAL_A__</div></div>
                          <div class="rounded-2xl bg-slate-50 p-3"><div class="text-xl font-black text-slate-900">__METRIC_B__</div><div class="mt-1 text-[11px] text-slate-500">__SIGNAL_B__</div></div>
                          <div class="rounded-2xl bg-slate-50 p-3"><div class="text-xl font-black text-slate-900">__METRIC_C__</div><div class="mt-1 text-[11px] text-slate-500">__SIGNAL_C__</div></div>
                        </div>
                      </section>
                    </aside>
                  </section>
                </div>
                """
                .replace("__ID__", route.id)
                .replace("__FLOW__", profile.flowKey())
                .replace("__SURFACE__", escapeHtml(profile.surfaceLabel()))
                .replace("__TITLE__", title)
                .replace("__DESCRIPTION__", description)
                .replace("__SLOT_DAY__", escapeHtml(profile.slotDay()))
                .replace("__SLOT_TIME__", escapeHtml(profile.slotTime()))
                .replace("__TOAST_SELECT__", escapeHtml(profile.toastSelect()))
                .replace("__PRIMARY_ACTION__", escapeHtml(profile.primaryAction()))
                .replace("__SECONDARY_ACTION__", escapeHtml(profile.secondaryAction()))
                .replace("__TERTIARY_ACTION__", escapeHtml(profile.tertiaryAction()))
                .replace("__CODE__", escapeHtml(profile.codePrefix()))
                .replace("__ADVANCED_STAGE__", escapeHtml(profile.advancedStage()))
                .replace("__NEXT_ROUTE__", nextRoute)
                .replace("__STATUS_TITLE__", escapeHtml(profile.statusTitle()))
                .replace("__METRIC_A__", escapeHtml(profile.metricA()))
                .replace("__METRIC_B__", escapeHtml(profile.metricB()))
                .replace("__METRIC_C__", escapeHtml(profile.metricC()))
                .replace("__SIGNAL_A__", escapeHtml(profile.signalA()))
                .replace("__SIGNAL_B__", escapeHtml(profile.signalB()))
                .replace("__SIGNAL_C__", escapeHtml(profile.signalC()))
                .replace("__STATUS_DESC__", escapeHtml(profile.statusDescription()))
                .replace("__SECTION_TITLE__", escapeHtml(profile.sectionTitle()))
                .replace("__SECTION_DESC__", escapeHtml(profile.sectionDescription()))
                .replace("__ITEMS__", itemJson)
                .replace("__FLOW_TITLE__", escapeHtml(profile.flowTitle()))
                .replace("__STEP_A__", escapeHtml(profile.stepA()))
                .replace("__STEP_B__", escapeHtml(profile.stepB()))
                .replace("__STEP_C__", escapeHtml(profile.stepC()))
                .replace("__SELECTED_LABEL__", escapeHtml(profile.selectedLabel()))
                .replace("__ORDER_LABEL__", escapeHtml(profile.orderLabel()))
                .replace("__EMPTY__", zh ? "待操作" : "Waiting");
    }

    private String buildOpsCommandFallbackComponent(ProjectManifest manifest, Route route, ProjectManifest.PageSpec pageSpec, GenericWorkflowProfile profile, boolean zh) {
        String title = escapeHtml(route.name);
        String description = escapeHtml(pageSpec != null && pageSpec.getDescription() != null ? pageSpec.getDescription() : manifest.getUserIntent());
        String itemJson = serializeSeedItems(profile.items());
        return """
                <div x-show="hash === '#__ID__'" data-lingnow-flow="__FLOW__" data-lingnow-layout="ops-command" class="animate-fade-in pb-10 space-y-6">
                  <section class="rounded-[32px] bg-slate-950 p-7 text-white shadow-sm">
                    <div class="flex flex-wrap items-center gap-3">
                      <span class="rounded-full bg-white/10 px-3 py-1 text-xs font-black text-white/80">__SURFACE__</span>
                      <span class="rounded-full bg-white/10 px-3 py-1 text-xs font-black text-white/80">__STATUS_TITLE__</span>
                    </div>
                    <div class="mt-4 grid gap-6 xl:grid-cols-[minmax(0,1fr)_360px]">
                      <div>
                        <h1 class="text-3xl font-black tracking-tight">__TITLE__</h1>
                        <p class="mt-3 max-w-3xl text-sm leading-7 text-slate-300">__DESCRIPTION__</p>
                        <div class="mt-6 flex flex-wrap gap-3">
                          <button @click="selectedLead = '__TITLE__'; showToast('__TOAST_SELECT__')" data-lingnow-action="select-workflow-item" class="shell-primary-button rounded-full px-5 py-3 text-sm font-black transition">__PRIMARY_ACTION__</button>
                          <button @click="confirmBooking(); showToast('__SECONDARY_ACTION__ 已记录')" data-lingnow-action="confirm-workflow" class="rounded-full border border-white/15 bg-white/10 px-5 py-3 text-sm font-black text-white">__SECONDARY_ACTION__</button>
                          <button @click="advanceOrder({id:'__CODE__-OPS', title:'__TITLE__', stage:'__ADVANCED_STAGE__'})" data-lingnow-action="advance-workflow" class="rounded-full border border-white/15 bg-white/10 px-5 py-3 text-sm font-black text-white">__TERTIARY_ACTION__</button>
                        </div>
                      </div>
                      <div class="grid gap-3 grid-cols-3">
                        <div class="rounded-2xl bg-white/10 p-4"><div class="text-2xl font-black">__METRIC_A__</div><div class="mt-1 text-[11px] text-slate-300">__SIGNAL_A__</div></div>
                        <div class="rounded-2xl bg-white/10 p-4"><div class="text-2xl font-black">__METRIC_B__</div><div class="mt-1 text-[11px] text-slate-300">__SIGNAL_B__</div></div>
                        <div class="rounded-2xl bg-white/10 p-4"><div class="text-2xl font-black">__METRIC_C__</div><div class="mt-1 text-[11px] text-slate-300">__SIGNAL_C__</div></div>
                      </div>
                    </div>
                  </section>
                  <section class="grid gap-6 xl:grid-cols-[minmax(0,1fr)_320px]">
                    <div class="rounded-[32px] border border-slate-200 bg-white p-6 shadow-sm">
                      <div class="mb-5 flex items-end justify-between gap-4">
                        <div>
                          <h2 class="text-2xl font-black text-slate-900">__SECTION_TITLE__</h2>
                          <p class="mt-1 text-sm text-slate-500">__SECTION_DESC__</p>
                        </div>
                        <span class="rounded-full bg-slate-100 px-3 py-1 text-xs font-black text-slate-500">__CODE__</span>
                      </div>
                      <div class="space-y-4">
                        <template x-for='item in __ITEMS__' :key="item.id">
                          <article class="rounded-[28px] border border-slate-200 bg-slate-50 p-5 transition hover:bg-white hover:shadow-lg">
                            <div class="flex flex-col gap-4 lg:flex-row lg:items-center">
                              <button @click="selectedLead = item.title; openDetail(item)" data-lingnow-action="open-detail" class="flex-1 text-left">
                                <div class="flex flex-wrap items-center gap-2 text-[11px] font-bold uppercase tracking-[0.2em] text-slate-400">
                                  <span x-text="item.category"></span><span>·</span><span x-text="item.status"></span>
                                </div>
                                <h3 class="mt-3 text-2xl font-black text-slate-900" x-text="item.title"></h3>
                                <p class="mt-2 text-sm leading-7 text-slate-600" x-text="item.description"></p>
                              </button>
                              <div class="flex flex-wrap gap-2 lg:w-[240px] lg:justify-end">
                                <button @click.stop="selectedLead = item.title; showToast('__TOAST_SELECT__')" data-lingnow-action="select-workflow-item" class="shell-soft-button rounded-full px-4 py-2 text-sm font-black transition">__STEP_A__</button>
                                <button @click.stop="confirmBooking(); selectedLead = item.title" data-lingnow-action="confirm-workflow" class="rounded-full border border-slate-200 bg-white px-4 py-2 text-sm font-black text-slate-700 hover:bg-slate-50">__STEP_B__</button>
                                <button @click.stop="advanceOrder({id:item.id, title:item.title, stage:'__ADVANCED_STAGE__'})" data-lingnow-action="advance-workflow" class="shell-primary-button rounded-full px-4 py-2 text-sm font-black transition">__STEP_C__</button>
                              </div>
                            </div>
                          </article>
                        </template>
                      </div>
                    </div>
                    <aside class="space-y-4">
                      <section class="rounded-[32px] border border-slate-200 bg-white p-5 shadow-sm">
                        <div class="text-sm font-black text-slate-900">__FLOW_TITLE__</div>
                        <p class="mt-3 text-sm leading-7 text-slate-600">__STATUS_DESC__</p>
                      </section>
                      <section class="rounded-[32px] border border-slate-200 bg-white p-5 shadow-sm">
                        <div class="text-sm font-black text-slate-900">__LIVE_STATE__</div>
                        <div class="mt-4 space-y-3 text-sm">
                          <div class="rounded-2xl bg-slate-50 p-4"><span class="font-black">__SELECTED_LABEL__：</span><span x-text="selectedLead || '__EMPTY__'"></span></div>
                          <div class="rounded-2xl bg-slate-50 p-4"><span class="font-black">__ORDER_LABEL__：</span><span x-text="activeOrder ? `${activeOrder.id} · ${activeOrder.stage}` : '__EMPTY__'"></span></div>
                        </div>
                      </section>
                    </aside>
                  </section>
                </div>
                """
                .replace("__ID__", route.id)
                .replace("__FLOW__", profile.flowKey())
                .replace("__SURFACE__", escapeHtml(profile.surfaceLabel()))
                .replace("__TITLE__", title)
                .replace("__DESCRIPTION__", description)
                .replace("__TOAST_SELECT__", escapeHtml(profile.toastSelect()))
                .replace("__SECONDARY_ACTION__", escapeHtml(profile.secondaryAction()))
                .replace("__TERTIARY_ACTION__", escapeHtml(profile.tertiaryAction()))
                .replace("__CODE__", escapeHtml(profile.codePrefix()))
                .replace("__ADVANCED_STAGE__", escapeHtml(profile.advancedStage()))
                .replace("__STATUS_TITLE__", escapeHtml(profile.statusTitle()))
                .replace("__METRIC_A__", escapeHtml(profile.metricA()))
                .replace("__METRIC_B__", escapeHtml(profile.metricB()))
                .replace("__METRIC_C__", escapeHtml(profile.metricC()))
                .replace("__SIGNAL_A__", escapeHtml(profile.signalA()))
                .replace("__SIGNAL_B__", escapeHtml(profile.signalB()))
                .replace("__SIGNAL_C__", escapeHtml(profile.signalC()))
                .replace("__STATUS_DESC__", escapeHtml(profile.statusDescription()))
                .replace("__SECTION_TITLE__", escapeHtml(profile.sectionTitle()))
                .replace("__SECTION_DESC__", escapeHtml(profile.sectionDescription()))
                .replace("__ITEMS__", itemJson)
                .replace("__FLOW_TITLE__", escapeHtml(profile.flowTitle()))
                .replace("__STEP_A__", escapeHtml(profile.stepA()))
                .replace("__STEP_B__", escapeHtml(profile.stepB()))
                .replace("__STEP_C__", escapeHtml(profile.stepC()))
                .replace("__LIVE_STATE__", zh ? "处理状态" : "Handling state")
                .replace("__SELECTED_LABEL__", escapeHtml(profile.selectedLabel()))
                .replace("__ORDER_LABEL__", escapeHtml(profile.orderLabel()))
                .replace("__EMPTY__", zh ? "待处理" : "Waiting");
    }

    private String buildLearningCampusFallbackComponent(ProjectManifest manifest, Route route, ProjectManifest.PageSpec pageSpec, GenericWorkflowProfile profile, boolean zh) {
        String title = escapeHtml(route.name);
        String description = escapeHtml(pageSpec != null && pageSpec.getDescription() != null ? pageSpec.getDescription() : manifest.getUserIntent());
        String itemJson = serializeSeedItems(profile.items());
        String nextRoute = nextRouteId(route.id);
        return """
                <div x-show="hash === '#__ID__'" data-lingnow-flow="__FLOW__" data-lingnow-layout="learning-campus" class="animate-fade-in pb-10 space-y-6">
                  <section class="grid gap-6 xl:grid-cols-[minmax(0,1fr)_340px]">
                    <div class="rounded-[32px] border border-slate-200 bg-white p-7 shadow-sm">
                      <span class="shell-soft-pill inline-flex items-center rounded-full px-3 py-1 text-xs font-black">__SURFACE__</span>
                      <h1 class="mt-4 text-3xl font-black tracking-tight text-slate-900">__TITLE__</h1>
                      <p class="mt-3 max-w-3xl text-sm leading-7 text-slate-600">__DESCRIPTION__</p>
                      <div class="mt-6 grid gap-3 md:grid-cols-3">
                        <div class="rounded-[26px] bg-slate-50 p-4"><div class="text-xs font-bold uppercase tracking-[0.2em] text-slate-400">__SIGNAL_A__</div><div class="mt-3 text-3xl font-black text-slate-900">__METRIC_A__</div></div>
                        <div class="rounded-[26px] bg-slate-50 p-4"><div class="text-xs font-bold uppercase tracking-[0.2em] text-slate-400">__SIGNAL_B__</div><div class="mt-3 text-3xl font-black text-slate-900">__METRIC_B__</div></div>
                        <div class="rounded-[26px] bg-slate-50 p-4"><div class="text-xs font-bold uppercase tracking-[0.2em] text-slate-400">__SIGNAL_C__</div><div class="mt-3 text-3xl font-black text-slate-900">__METRIC_C__</div></div>
                      </div>
                    </div>
                    <aside class="rounded-[32px] border border-slate-200 bg-white p-6 shadow-sm">
                      <div class="text-xs font-black uppercase tracking-[0.25em] text-slate-400">__STATUS_TITLE__</div>
                      <p class="mt-4 text-sm leading-7 text-slate-600">__STATUS_DESC__</p>
                      <div class="mt-5 space-y-3">
                        <button @click="pickSlot({day:'__SLOT_DAY__', time:'__SLOT_TIME__', type:'__TITLE__', owner:'LingNow'}); toast='__TOAST_SELECT__'" data-lingnow-action="select-workflow-item" class="shell-primary-button block w-full rounded-2xl px-4 py-3 text-left text-sm font-black transition">__PRIMARY_ACTION__</button>
                        <button @click="confirmBooking()" data-lingnow-action="confirm-workflow" class="block w-full rounded-2xl border border-slate-200 bg-white px-4 py-3 text-left text-sm font-black text-slate-700 hover:bg-slate-50">__SECONDARY_ACTION__</button>
                        <button @click="advanceOrder({id:'__CODE__-01', title:'__TITLE__', stage:'__ADVANCED_STAGE__'}); go('__NEXT_ROUTE__')" data-lingnow-action="advance-workflow" class="shell-soft-button block w-full rounded-2xl px-4 py-3 text-left text-sm font-black transition">__TERTIARY_ACTION__</button>
                      </div>
                    </aside>
                  </section>
                  <section class="grid gap-6 xl:grid-cols-[minmax(0,1fr)_320px]">
                    <div class="rounded-[32px] border border-slate-200 bg-white p-6 shadow-sm">
                      <div class="mb-5 flex items-end justify-between gap-4">
                        <div>
                          <h2 class="text-2xl font-black text-slate-900">__SECTION_TITLE__</h2>
                          <p class="mt-1 text-sm text-slate-500">__SECTION_DESC__</p>
                        </div>
                        <span class="rounded-full bg-slate-100 px-3 py-1 text-xs font-black text-slate-500">__CODE__</span>
                      </div>
                      <div class="space-y-4">
                        <template x-for='item in __ITEMS__' :key="item.id">
                          <article @click="openDetail(item)" data-lingnow-action="open-detail" class="rounded-[28px] border border-slate-200 bg-slate-50 p-5 transition hover:-translate-y-1 hover:bg-white hover:shadow-lg">
                            <div class="flex flex-col gap-4 lg:flex-row lg:items-center">
                              <div class="flex-1">
                                <div class="flex flex-wrap items-center gap-2">
                                  <span class="rounded-full bg-white px-3 py-1 text-[11px] font-black text-slate-600" x-text="item.category"></span>
                                  <span class="text-xs font-black text-slate-500" x-text="item.status"></span>
                                </div>
                                <h3 class="mt-3 text-2xl font-black text-slate-900" x-text="item.title"></h3>
                                <p class="mt-2 text-sm leading-7 text-slate-600" x-text="item.description"></p>
                                <div class="mt-3 text-sm font-bold text-slate-500" x-text="item.metric"></div>
                              </div>
                              <div class="flex flex-wrap gap-2 lg:w-[240px] lg:justify-end">
                                <button @click.stop="pickSlot({day:'__SLOT_DAY__', time:'__SLOT_TIME__', type:item.title, owner:item.author}); toast='__TOAST_SELECT__'" data-lingnow-action="select-workflow-item" class="shell-soft-button rounded-full px-4 py-2 text-sm font-black transition">__STEP_A__</button>
                                <button @click.stop="confirmBooking(); showToast('__SECONDARY_ACTION__ 已记录')" data-lingnow-action="confirm-workflow" class="rounded-full border border-slate-200 bg-white px-4 py-2 text-sm font-black text-slate-700 hover:bg-slate-50">__STEP_B__</button>
                                <button @click.stop="advanceOrder({id:item.id, title:item.title, stage:'__ADVANCED_STAGE__'})" data-lingnow-action="advance-workflow" class="shell-primary-button rounded-full px-4 py-2 text-sm font-black transition">__STEP_C__</button>
                              </div>
                            </div>
                          </article>
                        </template>
                      </div>
                    </div>
                    <aside class="space-y-4">
                      <section class="rounded-[32px] border border-slate-200 bg-white p-5 shadow-sm">
                        <div class="text-sm font-black text-slate-900">__LIVE_STATE__</div>
                        <div class="mt-4 space-y-3 text-sm">
                          <div class="rounded-2xl bg-slate-50 p-4"><span class="font-black">__SELECTED_LABEL__：</span><span x-text="selectedSlot ? `${selectedSlot.day} ${selectedSlot.time}` : '__EMPTY__'"></span></div>
                          <div class="rounded-2xl bg-slate-50 p-4"><span class="font-black">__ORDER_LABEL__：</span><span x-text="activeOrder ? `${activeOrder.id} · ${activeOrder.stage}` : '__EMPTY__'"></span></div>
                        </div>
                      </section>
                    </aside>
                  </section>
                </div>
                """
                .replace("__ID__", route.id)
                .replace("__FLOW__", profile.flowKey())
                .replace("__SURFACE__", escapeHtml(profile.surfaceLabel()))
                .replace("__TITLE__", title)
                .replace("__DESCRIPTION__", description)
                .replace("__SIGNAL_A__", escapeHtml(profile.signalA()))
                .replace("__SIGNAL_B__", escapeHtml(profile.signalB()))
                .replace("__SIGNAL_C__", escapeHtml(profile.signalC()))
                .replace("__METRIC_A__", escapeHtml(profile.metricA()))
                .replace("__METRIC_B__", escapeHtml(profile.metricB()))
                .replace("__METRIC_C__", escapeHtml(profile.metricC()))
                .replace("__SLOT_DAY__", escapeHtml(profile.slotDay()))
                .replace("__SLOT_TIME__", escapeHtml(profile.slotTime()))
                .replace("__TOAST_SELECT__", escapeHtml(profile.toastSelect()))
                .replace("__PRIMARY_ACTION__", escapeHtml(profile.primaryAction()))
                .replace("__SECONDARY_ACTION__", escapeHtml(profile.secondaryAction()))
                .replace("__TERTIARY_ACTION__", escapeHtml(profile.tertiaryAction()))
                .replace("__CODE__", escapeHtml(profile.codePrefix()))
                .replace("__ADVANCED_STAGE__", escapeHtml(profile.advancedStage()))
                .replace("__NEXT_ROUTE__", nextRoute)
                .replace("__STATUS_TITLE__", escapeHtml(profile.statusTitle()))
                .replace("__STATUS_DESC__", escapeHtml(profile.statusDescription()))
                .replace("__SECTION_TITLE__", escapeHtml(profile.sectionTitle()))
                .replace("__SECTION_DESC__", escapeHtml(profile.sectionDescription()))
                .replace("__ITEMS__", itemJson)
                .replace("__STEP_A__", escapeHtml(profile.stepA()))
                .replace("__STEP_B__", escapeHtml(profile.stepB()))
                .replace("__STEP_C__", escapeHtml(profile.stepC()))
                .replace("__LIVE_STATE__", zh ? "学习状态" : "Learning state")
                .replace("__SELECTED_LABEL__", escapeHtml(profile.selectedLabel()))
                .replace("__ORDER_LABEL__", escapeHtml(profile.orderLabel()))
                .replace("__EMPTY__", zh ? "待推进" : "Waiting");
    }

    private GenericWorkflowProfile buildGenericWorkflowProfile(ProjectManifest manifest, boolean zh) {
        String source = ((manifest.getUserIntent() == null ? "" : manifest.getUserIntent()) + " "
                + (manifest.getArchetype() == null ? "" : manifest.getArchetype()) + " "
                + (manifest.getOverview() == null ? "" : manifest.getOverview())).toLowerCase(Locale.ROOT);
        ProjectManifest.DesignContract contract = manifest.getDesignContract();
        boolean booking = containsAny(source, "预约", "预订", "排队", "点单", "取号", "座位", "排课", "报名", "看车", "看房", "挂号", "到店", "booking", "appointment", "reservation");
        boolean learning = containsAny(source, "课程", "学习", "共读", "笔记", "进度", "阅读", "learn", "study", "notes");
        boolean pipeline = containsAny(source, "crm", "ats", "招聘", "候选人", "面试", "offer", "销售线索", "商机", "客户成功", "pipeline");
        boolean explicitCommerce = containsAny(source, "商城", "商品", "预售", "尺码", "物流", "支付", "ecommerce", "sku", "cart");
        boolean commerce = explicitCommerce
                || (contract != null
                && contract.getPrimaryGoal() == ProjectManifest.PrimaryGoal.TRANSACT
                && !booking
                && !learning
                && !pipeline);
        boolean explicitDashboard = containsAny(source, "看板", "运维", "设备", "监控", "工单", "dashboard", "ops", "maintenance");
        boolean dashboard = explicitDashboard
                || (contract != null
                && contract.getLayoutRhythm() == ProjectManifest.LayoutRhythm.DASHBOARD
                && !commerce
                && !booking
                && !learning
                && !pipeline);

        if (pipeline) {
            return buildPipelineWorkflowProfile(zh);
        }

        if (dashboard && !containsAny(source, "咖啡", "coffee")) {
            return buildGenericOpsWorkflowProfile(zh);
        }

        if (dashboard) {
            return new GenericWorkflowProfile(
                    "ops-dashboard",
                    zh ? "运维工作台" : "Ops workspace",
                    zh ? "选择异常设备" : "Select device",
                    zh ? "确认派单" : "Confirm dispatch",
                    zh ? "推进工单" : "Advance ticket",
                    zh ? "今晚" : "Tonight",
                    "22:30",
                    zh ? "已选中异常设备，等待确认处理" : "Device selected for handling",
                    zh ? "处理中" : "In progress",
                    zh ? "设备态势" : "Fleet status",
                    "86%",
                    "12",
                    "4",
                    zh ? "在线率" : "Online",
                    zh ? "预警" : "Alerts",
                    zh ? "待派单" : "Tickets",
                    zh ? "从设备状态进入补货预警、故障工单和远程控制，点击后状态会直接写入原型。" : "Move from device status to replenishment, tickets, and remote controls with visible state updates.",
                    zh ? "可操作对象" : "Actionable objects",
                    zh ? "每张卡都能打开详情，底部流程按钮能改变选中、确认和工单状态。" : "Every card opens detail; flow buttons mutate selected, confirmed, and ticket state.",
                    zh ? "运维闭环" : "Ops loop",
                    zh ? "锁定设备" : "Lock device",
                    zh ? "确认处理" : "Confirm handling",
                    zh ? "提交记录" : "Submit record",
                    zh ? "选中设备" : "Selected device",
                    zh ? "工单状态" : "Ticket status",
                    "OPS",
                    List.of(
                            seedCard("id", "ops-1", "title", zh ? "虹桥门店 A12 咖啡机" : "Hongqiao A12 coffee machine", "description", zh ? "奶仓余量低于 18%，萃取压力波动，建议合并补货和维护派单。" : "Milk tank below 18% with pressure variance; combine replenishment and maintenance.", "author", zh ? "华东一区" : "East region", "category", zh ? "补货预警" : "Replenishment", "status", zh ? "需处理" : "Action needed", "metric", zh ? "18% 奶仓 · 2 次告警" : "18% tank · 2 alerts", "cover", "https://images.unsplash.com/photo-1517701604599-bb29b565090c?q=80&w=1200", "tags", List.of(zh ? "咖啡机" : "Coffee", zh ? "补货" : "Stock")),
                            seedCard("id", "ops-2", "title", zh ? "陆家嘴 B07 自动清洁失败" : "Lujiazui B07 cleaning failed", "description", zh ? "夜间清洁任务失败，远程重试一次后仍需现场检查废水盒。" : "Nightly cleaning failed; remote retry suggests waste tray inspection.", "author", zh ? "上海核心区" : "Shanghai core", "category", zh ? "故障工单" : "Fault ticket", "status", zh ? "待派单" : "Dispatch", "metric", zh ? "SLA 2h" : "SLA 2h", "cover", "https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?q=80&w=1200", "tags", List.of(zh ? "清洁" : "Cleaning", zh ? "SLA" : "SLA")),
                            seedCard("id", "ops-3", "title", zh ? "静安 C03 豆仓预测补货" : "Jing'an C03 bean refill forecast", "description", zh ? "按近 7 天销量预测明早 9 点前需要补豆，可提前合并配送。" : "Forecast says beans run out before 9am; merge delivery ahead.", "author", zh ? "预测模型" : "Forecast model", "category", zh ? "预测" : "Forecast", "status", zh ? "可合并" : "Mergeable", "metric", zh ? "93% 置信度" : "93% confidence", "cover", "https://images.unsplash.com/photo-1442512595331-e89e73853f31?q=80&w=1200", "tags", List.of(zh ? "豆仓" : "Beans", zh ? "预测" : "Forecast")),
                            seedCard("id", "ops-4", "title", zh ? "徐汇 D19 远程重启窗口" : "Xuhui D19 remote restart window", "description", zh ? "当前无排队订单，可在 5 分钟维护窗口内远程重启控制模块。" : "No queued orders; restart control module within a five-minute window.", "author", zh ? "远程控制" : "Remote control", "category", zh ? "控制" : "Control", "status", zh ? "可执行" : "Ready", "metric", zh ? "5 分钟窗口" : "5m window", "cover", "https://images.unsplash.com/photo-1509042239860-f550ce710b93?q=80&w=1200", "tags", List.of(zh ? "重启" : "Restart", zh ? "远程" : "Remote"))
                    )
            );
        }

        if (booking) {
            if (containsAny(source, "二手车", "车辆", "汽车", "看车", "试驾", "车源", "vehicle", "car")) {
                return buildVehicleCommerceProfile(zh);
            }
            if (containsAny(source, "房源", "租房", "租赁", "看房", "公寓", "长租", "property", "rental", "housing")) {
                return buildPropertyCommerceProfile(zh);
            }
            if (containsAny(source, "票务", "门票", "活动", "演出", "展会", "ticket", "event")) {
                return buildEventTicketCommerceProfile(zh);
            }
            if (containsAny(source, "医美", "诊所", "门诊", "医生", "体检", "康复", "clinic", "medical", "healthcare")) {
                return buildClinicBookingProfile(zh);
            }
            if (containsAny(source, "健身", "瑜伽", "私教", "排课", "课包", "教练", "训练营", "fitness", "gym", "coach")) {
                return buildFitnessBookingProfile(zh);
            }
            if (!containsAny(source, "餐厅", "食堂", "点单", "菜单", "堂食", "夜宵", "restaurant", "diner", "menu")) {
                return buildGenericBookingProfile(zh);
            }
            return new GenericWorkflowProfile(
                    "booking-order",
                    zh ? "预约点单原型" : "Booking prototype",
                    zh ? "选择时间/桌型" : "Pick time / table",
                    zh ? "确认预约" : "Confirm booking",
                    zh ? "进入订单" : "Go to order",
                    zh ? "今晚" : "Tonight",
                    "21:30",
                    zh ? "已选择预约时段，可继续确认" : "Slot selected for confirmation",
                    zh ? "预约已确认" : "Booking confirmed",
                    zh ? "到店闭环" : "Visit loop",
                    "38",
                    "18min",
                    "¥268",
                    zh ? "前方桌数" : "Queue",
                    zh ? "预计等待" : "Wait",
                    zh ? "客单价" : "Ticket",
                    zh ? "从排队取号到预约、点单、支付和订单状态，点击按钮能模拟完整到店流程。" : "Simulate queue ticketing, reservation, ordering, payment, and order status with working buttons.",
                    zh ? "当前可操作项" : "Current actions",
                    zh ? "卡片可打开详情，流程按钮可写入选中时段、确认状态和订单状态。" : "Cards open detail; flow buttons write selected slot, confirmation, and order state.",
                    zh ? "预约点单流程" : "Booking flow",
                    zh ? "取号/选座" : "Ticket / seat",
                    zh ? "确认预约" : "Confirm booking",
                    zh ? "提交订单" : "Submit order",
                    zh ? "选中时段" : "Selected slot",
                    zh ? "订单状态" : "Order status",
                    "DIN",
                    List.of(
                            seedCard("id", "book-1", "title", zh ? "2 人吧台位 · 21:30" : "Counter seats for 2 · 21:30", "description", zh ? "适合快速到店，系统会保留 10 分钟并推荐提前点单。" : "Fast visit slot held for 10 minutes with preorder suggestion.", "author", zh ? "深夜食堂·静安店" : "Late Diner Jing'an", "category", zh ? "预约座位" : "Reservation", "status", zh ? "可约" : "Open", "metric", zh ? "保留 10 分钟" : "10m hold", "cover", "https://images.unsplash.com/photo-1555396273-367ea4eb4db5?q=80&w=1200", "tags", List.of(zh ? "吧台" : "Counter", zh ? "两人" : "Two")),
                            seedCard("id", "book-2", "title", zh ? "招牌夜宵套餐" : "Signature late-night set", "description", zh ? "拉面、小食和饮品组合，可在排队时先加入订单。" : "Ramen, side, and drink bundle ready to add while queueing.", "author", zh ? "菜单推荐" : "Menu picks", "category", zh ? "菜单" : "Menu", "status", zh ? "热卖" : "Hot", "metric", "¥88", "cover", "https://images.unsplash.com/photo-1569718212165-3a8278d5f624?q=80&w=1200", "tags", List.of(zh ? "拉面" : "Ramen", zh ? "套餐" : "Set")),
                            seedCard("id", "book-3", "title", zh ? "当前排队号码 A038" : "Queue ticket A038", "description", zh ? "前方 12 桌，预计 18 分钟，可开启到号提醒。" : "12 tables ahead, 18-minute estimate, reminders available.", "author", zh ? "排队系统" : "Queue system", "category", zh ? "取号" : "Ticket", "status", zh ? "等待中" : "Waiting", "metric", zh ? "12 桌" : "12 tables", "cover", "https://images.unsplash.com/photo-1414235077428-338989a2e8c0?q=80&w=1200", "tags", List.of(zh ? "排队" : "Queue", zh ? "提醒" : "Reminder")),
                            seedCard("id", "book-4", "title", zh ? "待支付订单 LN-2408" : "Pending order LN-2408", "description", zh ? "已选择堂食和预约时段，支付后进入备餐与到店提醒。" : "Dine-in and slot selected; pay to enter prep and visit reminder.", "author", zh ? "订单中心" : "Orders", "category", zh ? "订单" : "Order", "status", zh ? "待支付" : "Pay", "metric", "¥268", "cover", "https://images.unsplash.com/photo-1552566626-52f8b828add9?q=80&w=1200", "tags", List.of(zh ? "支付" : "Payment", zh ? "堂食" : "Dine-in"))
                    )
            );
        }

        if (commerce) {
            if (containsAny(source, "二手车", "车辆", "汽车", "看车", "试驾", "车源", "vehicle", "car")) {
                return buildVehicleCommerceProfile(zh);
            }
            if (containsAny(source, "房源", "租房", "租赁", "看房", "公寓", "长租", "property", "rental", "housing")) {
                return buildPropertyCommerceProfile(zh);
            }
            if (containsAny(source, "票务", "门票", "活动", "演出", "展会", "报名", "ticket", "event")) {
                return buildEventTicketCommerceProfile(zh);
            }
            if (!containsAny(source, "服装", "时装", "穿搭", "尺码", "外套", "半裙", "礼服", "fashion", "apparel")) {
                return buildGenericCommerceProfile(zh);
            }
            return new GenericWorkflowProfile(
                    "commerce-order",
                    zh ? "交易原型" : "Commerce prototype",
                    zh ? "选择商品/尺码" : "Select product / size",
                    zh ? "确认预售" : "Confirm preorder",
                    zh ? "推进订单" : "Advance order",
                    zh ? "本周" : "This week",
                    zh ? "预售批次 A" : "Drop A",
                    zh ? "已选择预售商品，等待锁单" : "Product selected for preorder",
                    zh ? "预售已锁单" : "Preorder locked",
                    zh ? "预售转化" : "Preorder conversion",
                    "42",
                    "¥18.6k",
                    "96%",
                    zh ? "待付款" : "Pending",
                    zh ? "预售额" : "GMV",
                    zh ? "尺码覆盖" : "Sizing",
                    zh ? "从商品展示进入详情、尺码选择、预售订单和物流进度，按钮会推进订单状态。" : "Move from product display to details, size choice, preorder, and shipment state.",
                    zh ? "预售商品" : "Preorder items",
                    zh ? "点击商品看详情，点击流程按钮模拟锁单、支付和物流推进。" : "Open product details and use flow buttons to simulate order, payment, and logistics.",
                    zh ? "交易闭环" : "Transaction loop",
                    zh ? "选定尺码" : "Choose size",
                    zh ? "锁定预售" : "Lock preorder",
                    zh ? "提交支付" : "Submit payment",
                    zh ? "选中商品" : "Selected item",
                    zh ? "订单状态" : "Order status",
                    "PRE",
                    List.of(
                            seedCard("id", "sku-1", "title", zh ? "羊毛廓形短外套 · 黑" : "Wool cropped jacket · black", "description", zh ? "首批预售 42 件，支持 XS-L 尺码选择，预计 21 天发货。" : "First drop of 42 units, XS-L sizing, ships in 21 days.", "author", zh ? "独立设计师 Aora" : "Designer Aora", "category", zh ? "外套" : "Jacket", "status", zh ? "预售中" : "Preorder", "metric", "¥1,680", "cover", "https://images.unsplash.com/photo-1496747611176-843222e1e57c?q=80&w=1200", "tags", List.of(zh ? "羊毛" : "Wool", zh ? "短外套" : "Cropped")),
                            seedCard("id", "sku-2", "title", zh ? "不对称褶裥半裙 · 灰" : "Asymmetric pleated skirt · gray", "description", zh ? "小批量定制面料，M 码库存紧张，加入预售后进入待付款。" : "Small-batch fabric with tight M availability; preorder enters pending payment.", "author", zh ? "Studio K" : "Studio K", "category", zh ? "半裙" : "Skirt", "status", zh ? "尺码紧张" : "Low size", "metric", "¥860", "cover", "https://images.unsplash.com/photo-1483985988355-763728e1935b?q=80&w=1200", "tags", List.of(zh ? "褶裥" : "Pleated", zh ? "灰色" : "Gray")),
                            seedCard("id", "sku-3", "title", zh ? "手工珠片吊带裙 · 香槟" : "Hand-beaded slip dress · champagne", "description", zh ? "需要确认胸围和裙长，支付定金后进入手工排产。" : "Requires bust and length confirmation before handcrafted production.", "author", zh ? "Maison Eleven" : "Maison Eleven", "category", zh ? "礼服" : "Dress", "status", zh ? "定金预售" : "Deposit", "metric", "¥3,280", "cover", "https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?q=80&w=1200", "tags", List.of(zh ? "礼服" : "Evening", zh ? "手工" : "Handmade")),
                            seedCard("id", "sku-4", "title", zh ? "通勤西裤 · 深海蓝" : "Tailored trousers · navy", "description", zh ? "热卖尺码可合并发货，物流节点会在订单页持续更新。" : "Popular sizes can ship together with logistics updates on order page.", "author", zh ? "Urban Line" : "Urban Line", "category", zh ? "裤装" : "Trousers", "status", zh ? "可发货" : "Ready", "metric", "¥720", "cover", "https://images.unsplash.com/photo-1529139574466-a303027c1d8b?q=80&w=1200", "tags", List.of(zh ? "通勤" : "Workwear", zh ? "西裤" : "Tailored"))
                    )
            );
        }

        if (learning) {
            if (!containsAny(source, "共读", "读书", "阅读", "笔记", "划线", "reading", "notes")) {
                return buildCourseLearningProfile(zh);
            }
            return new GenericWorkflowProfile(
                    "learning-progress",
                    zh ? "学习进度原型" : "Learning prototype",
                    zh ? "选择章节" : "Choose chapter",
                    zh ? "确认学习" : "Confirm progress",
                    zh ? "提交笔记" : "Submit note",
                    zh ? "本周" : "This week",
                    zh ? "共读会" : "Reading club",
                    zh ? "已选择共读任务" : "Reading task selected",
                    zh ? "笔记已提交" : "Note submitted",
                    zh ? "学习闭环" : "Learning loop",
                    "64%",
                    "18",
                    "7",
                    zh ? "完成度" : "Progress",
                    zh ? "笔记" : "Notes",
                    zh ? "讨论" : "Replies",
                    zh ? "从阅读任务进入笔记、收藏、讨论和进度更新，适合作为可点击学习原型。" : "Move from reading task to notes, saves, discussion, and progress updates.",
                    zh ? "共读任务" : "Reading tasks",
                    zh ? "打开详情可看笔记上下文，流程按钮可推进学习状态。" : "Open detail for note context and use buttons to advance learning state.",
                    zh ? "共读流程" : "Reading loop",
                    zh ? "选章节" : "Pick chapter",
                    zh ? "标记完成" : "Mark done",
                    zh ? "发讨论" : "Discuss",
                    zh ? "选中章节" : "Selected chapter",
                    zh ? "学习状态" : "Learning state",
                    "RD",
                    List.of(
                            seedCard("id", "learn-1", "title", zh ? "第一章：慢读与标注" : "Chapter 1: Slow reading", "description", zh ? "本周共读章节，已沉淀 18 条高亮笔记和 7 条讨论。" : "This week's chapter with 18 highlights and 7 replies.", "author", zh ? "精读会" : "Reading club", "category", zh ? "章节" : "Chapter", "status", zh ? "进行中" : "Active", "metric", zh ? "64% 完成" : "64% done", "cover", "https://images.unsplash.com/photo-1519682337058-a94d519337bc?q=80&w=1200", "tags", List.of(zh ? "高亮" : "Highlights", zh ? "共读" : "Reading")),
                            seedCard("id", "learn-2", "title", zh ? "关于“注意力”的讨论串" : "Discussion on attention", "description", zh ? "成员围绕关键段落回复观点，可从详情继续评论。" : "Members discuss a key passage and can keep replying in detail.", "author", zh ? "讨论区" : "Discussion", "category", zh ? "讨论" : "Thread", "status", zh ? "热议" : "Hot", "metric", zh ? "32 回复" : "32 replies", "cover", "https://images.unsplash.com/photo-1456513080510-7bf3a84b82f8?q=80&w=1200", "tags", List.of(zh ? "注意力" : "Attention", zh ? "回复" : "Replies")),
                            seedCard("id", "learn-3", "title", zh ? "已收藏：三条核心论点" : "Saved: three core arguments", "description", zh ? "收藏夹把高价值笔记聚合成复盘材料。" : "Bookmarks collect high-value notes for review.", "author", zh ? "个人收藏" : "Bookmarks", "category", zh ? "收藏" : "Saved", "status", zh ? "已收藏" : "Saved", "metric", zh ? "3 条论点" : "3 points", "cover", "https://images.unsplash.com/photo-1524995997946-a1c2e315a42f?q=80&w=1200", "tags", List.of(zh ? "复盘" : "Review", zh ? "论点" : "Arguments")),
                            seedCard("id", "learn-4", "title", zh ? "下次共读直播提醒" : "Next live reading reminder", "description", zh ? "周四 20:30 开始，可提前提交问题和摘要。" : "Starts Thursday 20:30; submit questions and summaries ahead.", "author", zh ? "学习计划" : "Plan", "category", zh ? "进度" : "Progress", "status", zh ? "待开始" : "Upcoming", "metric", zh ? "周四 20:30" : "Thu 20:30", "cover", "https://images.unsplash.com/photo-1497633762265-9d179a990aa6?q=80&w=1200", "tags", List.of(zh ? "直播" : "Live", zh ? "问题" : "Questions"))
                    )
            );
        }

        return new GenericWorkflowProfile(
                "generic-workflow",
                zh ? "业务原型" : "Workflow prototype",
                zh ? "选择对象" : "Select item",
                zh ? "确认处理" : "Confirm",
                zh ? "推进流程" : "Advance",
                zh ? "今天" : "Today",
                "14:00",
                zh ? "已选择对象，等待确认" : "Item selected",
                zh ? "已推进" : "Advanced",
                zh ? "核心流程" : "Core flow",
                "24",
                "8",
                "92%",
                zh ? "对象" : "Items",
                zh ? "待办" : "Tasks",
                zh ? "完成率" : "Done",
                zh ? "这是一个可交互的业务原型，卡片、确认、提交和推进都能改变页面状态。" : "Clickable workflow prototype with cards, confirmation, submission, and state changes.",
                zh ? "业务对象" : "Business objects",
                zh ? "所有卡片都能进入详情，流程按钮证明原型不是静态页面。" : "Cards open detail and flow buttons prove the prototype is not static.",
                zh ? "操作流程" : "Operation flow",
                zh ? "选择" : "Select",
                zh ? "确认" : "Confirm",
                zh ? "提交" : "Submit",
                zh ? "选中对象" : "Selected item",
                zh ? "流程状态" : "Flow status",
                "LN",
                List.of(
                        seedCard("id", "gen-1", "title", zh ? "核心对象 A" : "Core object A", "description", zh ? "用于承接当前页面的主要操作。" : "Handles the primary operation on this page.", "author", "LingNow", "category", zh ? "主流程" : "Primary", "status", zh ? "可处理" : "Ready", "metric", "A1", "cover", "https://images.unsplash.com/photo-1516321318423-f06f85e504b3?q=80&w=1200", "tags", List.of(zh ? "流程" : "Flow")),
                        seedCard("id", "gen-2", "title", zh ? "待确认对象 B" : "Pending object B", "description", zh ? "需要用户确认后进入下一步。" : "Needs confirmation before moving forward.", "author", "LingNow", "category", zh ? "确认" : "Confirm", "status", zh ? "待确认" : "Pending", "metric", "B2", "cover", "https://images.unsplash.com/photo-1517245386807-bb43f82c33c4?q=80&w=1200", "tags", List.of(zh ? "确认" : "Confirm")),
                        seedCard("id", "gen-3", "title", zh ? "已推进对象 C" : "Advanced object C", "description", zh ? "展示状态推进后的反馈。" : "Shows feedback after status advance.", "author", "LingNow", "category", zh ? "状态" : "Status", "status", zh ? "进行中" : "In progress", "metric", "C3", "cover", "https://images.unsplash.com/photo-1497366754035-f200968a6e72?q=80&w=1200", "tags", List.of(zh ? "状态" : "Status")),
                        seedCard("id", "gen-4", "title", zh ? "完成对象 D" : "Completed object D", "description", zh ? "用于验证完整闭环。" : "Validates the full loop.", "author", "LingNow", "category", zh ? "完成" : "Done", "status", zh ? "已完成" : "Done", "metric", "D4", "cover", "https://images.unsplash.com/photo-1454165804606-c3d57bc86b40?q=80&w=1200", "tags", List.of(zh ? "闭环" : "Loop"))
                )
        );
    }

    private GenericWorkflowProfile buildPipelineWorkflowProfile(boolean zh) {
        return new GenericWorkflowProfile(
                "pipeline-workflow",
                zh ? "管道工作台" : "Pipeline workspace",
                zh ? "选择对象" : "Select record",
                zh ? "安排跟进" : "Schedule follow-up",
                zh ? "推进阶段" : "Advance stage",
                zh ? "本周" : "This week",
                "16:00",
                zh ? "已选中管道对象，等待安排下一步" : "Pipeline record selected",
                zh ? "阶段已推进" : "Stage advanced",
                zh ? "转化管道" : "Conversion pipeline",
                "36",
                "12",
                "71%",
                zh ? "活跃对象" : "Active",
                zh ? "待跟进" : "Follow-ups",
                zh ? "推进率" : "Progress",
                zh ? "从线索/候选对象进入详情、安排下一步、更新状态和交接记录。" : "Open pipeline records, schedule the next step, update stage, and hand off notes.",
                zh ? "管道对象" : "Pipeline records",
                zh ? "每张卡都能进入详情，流程按钮会写入跟进时间、确认状态和阶段推进。" : "Every card opens detail; buttons update follow-up time, confirmation, and stage.",
                zh ? "管道推进流程" : "Pipeline flow",
                zh ? "看详情" : "Inspect",
                zh ? "排跟进" : "Schedule",
                zh ? "推阶段" : "Advance",
                zh ? "选中对象" : "Selected record",
                zh ? "管道状态" : "Pipeline status",
                "PIPE",
                List.of(
                        seedCard("id", "pipe-1", "title", zh ? "候选人 Ava · 产品设计师" : "Candidate Ava · Product Designer", "description", zh ? "二面反馈良好，待安排作品集复盘和薪资沟通。" : "Strong second-round feedback; portfolio review and offer chat pending.", "author", zh ? "招聘小组" : "Hiring team", "category", zh ? "面试管道" : "Interview", "status", zh ? "待跟进" : "Follow-up", "metric", zh ? "匹配度 92%" : "92% match", "cover", "https://images.unsplash.com/photo-1551836022-d5d88e9218df?q=80&w=1200", "tags", List.of(zh ? "候选人" : "Candidate", zh ? "二面" : "Round 2")),
                        seedCard("id", "pipe-2", "title", zh ? "商机 LN-2409 · 连锁门店" : "Opportunity LN-2409 · Retail chain", "description", zh ? "客户已确认试点门店范围，需要下发报价和实施排期。" : "Pilot scope confirmed; quote and rollout schedule needed.", "author", zh ? "销售团队" : "Sales team", "category", zh ? "销售商机" : "Sales", "status", zh ? "报价中" : "Quote", "metric", "¥128k", "cover", "https://images.unsplash.com/photo-1556761175-b413da4baf72?q=80&w=1200", "tags", List.of(zh ? "报价" : "Quote", zh ? "试点" : "Pilot")),
                        seedCard("id", "pipe-3", "title", zh ? "客户成功 · 高风险续费" : "Customer success · Renewal risk", "description", zh ? "近 14 天活跃下降，需要安排成功经理做价值复盘。" : "Usage dipped over 14 days; success review should be scheduled.", "author", zh ? "CSM Lily" : "CSM Lily", "category", zh ? "续费" : "Renewal", "status", zh ? "高风险" : "At risk", "metric", zh ? "健康度 61" : "Health 61", "cover", "https://images.unsplash.com/photo-1553877522-43269d4ea984?q=80&w=1200", "tags", List.of(zh ? "续费" : "Renewal", zh ? "健康度" : "Health")),
                        seedCard("id", "pipe-4", "title", zh ? "Offer 审批 · 后端工程师" : "Offer approval · Backend engineer", "description", zh ? "薪资区间已锁定，等待 HRBP 和负责人双确认。" : "Comp range locked; HRBP and hiring manager approval pending.", "author", zh ? "HRBP" : "HRBP", "category", "Offer", "status", zh ? "审批中" : "Approval", "metric", zh ? "2 人待批" : "2 approvers", "cover", "https://images.unsplash.com/photo-1497366811353-6870744d04b2?q=80&w=1200", "tags", List.of("Offer", zh ? "审批" : "Approval"))
                )
        );
    }

    private GenericWorkflowProfile buildGenericOpsWorkflowProfile(boolean zh) {
        return new GenericWorkflowProfile(
                "ops-dashboard",
                zh ? "运营看板" : "Operations dashboard",
                zh ? "选择异常项" : "Select exception",
                zh ? "确认派单" : "Confirm dispatch",
                zh ? "推进处理" : "Advance handling",
                zh ? "今天" : "Today",
                "18:00",
                zh ? "已选中异常项，等待确认处理" : "Exception selected for handling",
                zh ? "处理中" : "In progress",
                zh ? "运营态势" : "Operations status",
                "91%",
                "17",
                "5",
                zh ? "达成率" : "Target",
                zh ? "预警" : "Alerts",
                zh ? "待处理" : "Queue",
                zh ? "从运营概览进入预警、工单、派单和处理记录，点击后状态会直接写入页面。" : "Move from overview to alerts, tickets, dispatch, and handling records with visible state updates.",
                zh ? "待处理事项" : "Actionable items",
                zh ? "卡片可打开详情，流程按钮可改变选中、确认和处理状态。" : "Cards open detail; buttons mutate selected, confirmed, and handling state.",
                zh ? "运营处理闭环" : "Ops handling loop",
                zh ? "定位异常" : "Locate issue",
                zh ? "确认处理" : "Confirm",
                zh ? "提交记录" : "Submit record",
                zh ? "选中事项" : "Selected item",
                zh ? "处理状态" : "Handling status",
                "OPS",
                List.of(
                        seedCard("id", "ops-g-1", "title", zh ? "华东仓配送延迟预警" : "East warehouse delivery delay", "description", zh ? "上海片区 6 单超过预计到达时间，需要合并通知和改派。" : "Six Shanghai orders exceeded ETA; notify and reroute together.", "author", zh ? "履约中心" : "Fulfillment", "category", zh ? "配送" : "Delivery", "status", zh ? "需处理" : "Action needed", "metric", zh ? "6 单延迟" : "6 delayed", "cover", "https://images.unsplash.com/photo-1586528116311-ad8dd3c8310d?q=80&w=1200", "tags", List.of(zh ? "履约" : "Fulfillment", zh ? "改派" : "Reroute")),
                        seedCard("id", "ops-g-2", "title", zh ? "客服队列高峰" : "Support queue spike", "description", zh ? "咨询量高于均值 42%，建议开启快捷回复和分流策略。" : "Queue is 42% above baseline; enable shortcuts and routing.", "author", zh ? "客服中台" : "Support ops", "category", zh ? "客服" : "Support", "status", zh ? "排队中" : "Queued", "metric", zh ? "42% 超均值" : "42% above", "cover", "https://images.unsplash.com/photo-1525182008055-f88b95ff7980?q=80&w=1200", "tags", List.of(zh ? "分流" : "Routing", zh ? "高峰" : "Spike")),
                        seedCard("id", "ops-g-3", "title", zh ? "门店库存安全线" : "Store stock threshold", "description", zh ? "三家门店低于安全库存，可从详情合并补货任务。" : "Three stores are below safety stock; merge restock tasks from detail.", "author", zh ? "库存系统" : "Inventory", "category", zh ? "库存" : "Inventory", "status", zh ? "预警" : "Alert", "metric", zh ? "3 家门店" : "3 stores", "cover", "https://images.unsplash.com/photo-1553413077-190dd305871c?q=80&w=1200", "tags", List.of(zh ? "库存" : "Stock", zh ? "补货" : "Restock")),
                        seedCard("id", "ops-g-4", "title", zh ? "现场工单待派发" : "Field tickets pending", "description", zh ? "两张现场处理单缺少负责人，确认后会进入进行中。" : "Two field tickets need owners; confirmation moves them in progress.", "author", zh ? "调度台" : "Dispatch desk", "category", zh ? "工单" : "Ticket", "status", zh ? "待派单" : "Dispatch", "metric", zh ? "SLA 4h" : "SLA 4h", "cover", "https://images.unsplash.com/photo-1504384308090-c894fdcc538d?q=80&w=1200", "tags", List.of(zh ? "派单" : "Dispatch", "SLA"))
                )
        );
    }

    private GenericWorkflowProfile buildClinicBookingProfile(boolean zh) {
        return new GenericWorkflowProfile(
                "booking-order",
                zh ? "诊疗预约原型" : "Clinic booking prototype",
                zh ? "选择项目/医生" : "Pick service / doctor",
                zh ? "确认预约" : "Confirm appointment",
                zh ? "进入就诊单" : "Open visit order",
                zh ? "明天" : "Tomorrow",
                "10:30",
                zh ? "已选择预约时段，可继续确认" : "Appointment slot selected",
                zh ? "预约已确认" : "Appointment confirmed",
                zh ? "到诊闭环" : "Visit loop",
                "24",
                "6",
                "98%",
                zh ? "可约时段" : "Slots",
                zh ? "待确认" : "Pending",
                zh ? "准点率" : "On time",
                zh ? "从项目咨询进入医生档期、预约确认、到诊提醒和就诊单状态。" : "Move from consultation to doctor availability, confirmation, visit reminder, and order state.",
                zh ? "可预约项目" : "Bookable services",
                zh ? "卡片可打开详情，按钮可写入选中时段、预约确认和到诊状态。" : "Cards open detail; buttons write selected slot, confirmation, and visit state.",
                zh ? "预约就诊流程" : "Clinic booking flow",
                zh ? "选项目" : "Pick service",
                zh ? "确认预约" : "Confirm",
                zh ? "提交就诊单" : "Submit order",
                zh ? "选中时段" : "Selected slot",
                zh ? "就诊状态" : "Visit status",
                "CLN",
                List.of(
                        seedCard("id", "clinic-1", "title", zh ? "皮肤管理初诊 · 周三 10:30" : "Skin consult · Wed 10:30", "description", zh ? "主任医生可约，系统会预留 15 分钟问诊和方案沟通。" : "Senior doctor available with 15 minutes reserved for consultation.", "author", zh ? "静安诊所" : "Jing'an Clinic", "category", zh ? "初诊" : "Consult", "status", zh ? "可约" : "Open", "metric", zh ? "预留 15 分钟" : "15m hold", "cover", "https://images.unsplash.com/photo-1579684385127-1ef15d508118?q=80&w=1200", "tags", List.of(zh ? "医生" : "Doctor", zh ? "初诊" : "Consult")),
                        seedCard("id", "clinic-2", "title", zh ? "光电项目复诊提醒" : "Aesthetic follow-up reminder", "description", zh ? "上次治疗后第 28 天，适合安排复诊和护理记录。" : "Day 28 after prior treatment; schedule follow-up and care notes.", "author", zh ? "护理团队" : "Care team", "category", zh ? "复诊" : "Follow-up", "status", zh ? "待确认" : "Pending", "metric", zh ? "第 28 天" : "Day 28", "cover", "https://images.unsplash.com/photo-1551076805-e1869033e561?q=80&w=1200", "tags", List.of(zh ? "复诊" : "Follow-up", zh ? "护理" : "Care")),
                        seedCard("id", "clinic-3", "title", zh ? "体检报告解读 · 线上" : "Health report review · Online", "description", zh ? "支持上传报告并锁定 20 分钟线上解读时段。" : "Upload report and reserve a 20-minute online review slot.", "author", zh ? "远程问诊" : "Telehealth", "category", zh ? "线上" : "Online", "status", zh ? "可预约" : "Bookable", "metric", zh ? "20 分钟" : "20m", "cover", "https://images.unsplash.com/photo-1584982751601-97dcc096659c?q=80&w=1200", "tags", List.of(zh ? "报告" : "Report", zh ? "线上" : "Online")),
                        seedCard("id", "clinic-4", "title", zh ? "待支付就诊单 CL-2401" : "Pending visit order CL-2401", "description", zh ? "项目和时段已选，支付后进入到诊提醒和资料准备。" : "Service and slot selected; pay to enter visit reminder and prep.", "author", zh ? "就诊单" : "Visit order", "category", zh ? "支付" : "Payment", "status", zh ? "待支付" : "Pay", "metric", "¥398", "cover", "https://images.unsplash.com/photo-1584515933487-779824d29309?q=80&w=1200", "tags", List.of(zh ? "支付" : "Payment", zh ? "提醒" : "Reminder"))
                )
        );
    }

    private GenericWorkflowProfile buildFitnessBookingProfile(boolean zh) {
        return new GenericWorkflowProfile(
                "booking-order",
                zh ? "训练预约原型" : "Training booking prototype",
                zh ? "选择课程/教练" : "Pick class / coach",
                zh ? "确认排课" : "Confirm schedule",
                zh ? "扣减课包" : "Deduct package",
                zh ? "今晚" : "Tonight",
                "19:30",
                zh ? "已选择训练时段，可继续确认排课" : "Training slot selected",
                zh ? "课程已确认" : "Class confirmed",
                zh ? "训练履约" : "Training loop",
                "18",
                "7",
                "84%",
                zh ? "可约课" : "Slots",
                zh ? "待消课" : "Sessions",
                zh ? "出勤率" : "Attendance",
                zh ? "从课程选择进入教练档期、排课确认、签到和课包消耗。" : "Move from class selection to coach availability, schedule confirmation, check-in, and package deduction.",
                zh ? "可排课程" : "Schedulable classes",
                zh ? "卡片可打开详情，按钮可改变选课、确认和课包状态。" : "Cards open detail; buttons update class, confirmation, and package state.",
                zh ? "训练排课流程" : "Training schedule flow",
                zh ? "选课" : "Pick class",
                zh ? "确认排课" : "Confirm",
                zh ? "提交签到" : "Check in",
                zh ? "选中课程" : "Selected class",
                zh ? "训练状态" : "Training status",
                "FIT",
                List.of(
                        seedCard("id", "fit-1", "title", zh ? "私教力量课 · 19:30" : "Personal strength · 19:30", "description", zh ? "教练 Leo 可约，适合完成本周下肢训练计划。" : "Coach Leo is open for this week's lower-body plan.", "author", zh ? "核心门店" : "Main studio", "category", zh ? "私教" : "PT", "status", zh ? "可约" : "Open", "metric", zh ? "消耗 1 课时" : "1 credit", "cover", "https://images.unsplash.com/photo-1517838277536-f5f99be501cd?q=80&w=1200", "tags", List.of(zh ? "私教" : "Coach", zh ? "力量" : "Strength")),
                        seedCard("id", "fit-2", "title", zh ? "团课瑜伽 · 12 人小班" : "Yoga group class · 12 seats", "description", zh ? "剩余 3 个名额，确认后进入签到提醒。" : "Three seats left; confirmation enables check-in reminder.", "author", zh ? "瑜伽教室" : "Yoga room", "category", zh ? "团课" : "Group", "status", zh ? "少量名额" : "Few seats", "metric", zh ? "3 席" : "3 seats", "cover", "https://images.unsplash.com/photo-1575052814086-f385e2e2ad1b?q=80&w=1200", "tags", List.of(zh ? "瑜伽" : "Yoga", zh ? "团课" : "Group")),
                        seedCard("id", "fit-3", "title", zh ? "会员 Mina · 课包剩余 6 次" : "Member Mina · 6 credits left", "description", zh ? "连续两周未到店，适合安排一次恢复训练。" : "No visits for two weeks; schedule a recovery session.", "author", zh ? "会员运营" : "Member ops", "category", zh ? "跟进" : "Follow-up", "status", zh ? "待唤醒" : "Nudge", "metric", zh ? "6 次" : "6 credits", "cover", "https://images.unsplash.com/photo-1594737625785-a6cbdabd333c?q=80&w=1200", "tags", List.of(zh ? "课包" : "Package", zh ? "唤醒" : "Nudge")),
                        seedCard("id", "fit-4", "title", zh ? "待确认排课 FIT-2407" : "Pending schedule FIT-2407", "description", zh ? "课程和教练已选，确认后扣减课包并生成提醒。" : "Class and coach selected; confirm to deduct credit and notify.", "author", zh ? "排课系统" : "Scheduler", "category", zh ? "排课" : "Schedule", "status", zh ? "待确认" : "Pending", "metric", zh ? "1 课时" : "1 credit", "cover", "https://images.unsplash.com/photo-1534438327276-14e5300c3a48?q=80&w=1200", "tags", List.of(zh ? "确认" : "Confirm", zh ? "签到" : "Check-in"))
                )
        );
    }

    private GenericWorkflowProfile buildGenericBookingProfile(boolean zh) {
        return new GenericWorkflowProfile(
                "booking-order",
                zh ? "预约服务原型" : "Service booking prototype",
                zh ? "选择服务/时间" : "Pick service / time",
                zh ? "确认预约" : "Confirm booking",
                zh ? "提交订单" : "Submit order",
                zh ? "明天" : "Tomorrow",
                "14:00",
                zh ? "已选择服务时段，可继续确认" : "Service slot selected",
                zh ? "预约已确认" : "Booking confirmed",
                zh ? "服务预约闭环" : "Service booking loop",
                "32",
                "9",
                "94%",
                zh ? "可约时段" : "Slots",
                zh ? "待确认" : "Pending",
                zh ? "履约率" : "Fulfillment",
                zh ? "从服务列表进入详情、时段选择、预约确认和订单状态。" : "Move from service list to detail, time selection, confirmation, and order state.",
                zh ? "可预约服务" : "Bookable services",
                zh ? "卡片可打开详情，流程按钮可写入选中时段、确认状态和订单状态。" : "Cards open detail; buttons write selected slot, confirmation, and order state.",
                zh ? "服务预约流程" : "Service booking flow",
                zh ? "选服务" : "Pick service",
                zh ? "确认预约" : "Confirm",
                zh ? "提交订单" : "Submit",
                zh ? "选中时段" : "Selected slot",
                zh ? "预约状态" : "Booking status",
                "BKG",
                List.of(
                        seedCard("id", "book-g-1", "title", zh ? "标准服务 · 明天 14:00" : "Standard service · Tomorrow 14:00", "description", zh ? "适合普通预约场景，系统会保留 10 分钟确认窗口。" : "Designed for regular appointments with a 10-minute confirmation hold.", "author", zh ? "服务中心" : "Service center", "category", zh ? "预约" : "Booking", "status", zh ? "可约" : "Open", "metric", zh ? "保留 10 分钟" : "10m hold", "cover", "https://images.unsplash.com/photo-1556745757-8d76bdb6984b?q=80&w=1200", "tags", List.of(zh ? "预约" : "Booking", zh ? "服务" : "Service")),
                        seedCard("id", "book-g-2", "title", zh ? "专家时段 · 下午可选" : "Expert slot · Afternoon", "description", zh ? "可选择负责人和服务地点，确认后进入提醒。" : "Choose owner and location before confirmation and reminders.", "author", zh ? "专家团队" : "Expert team", "category", zh ? "专家" : "Expert", "status", zh ? "可选" : "Selectable", "metric", zh ? "3 个时段" : "3 slots", "cover", "https://images.unsplash.com/photo-1521737604893-d14cc237f11d?q=80&w=1200", "tags", List.of(zh ? "专家" : "Expert", zh ? "提醒" : "Reminder")),
                        seedCard("id", "book-g-3", "title", zh ? "待确认预约 BKG-2406" : "Pending booking BKG-2406", "description", zh ? "服务和时段已选择，点击确认后进入履约状态。" : "Service and time selected; confirm to enter fulfillment state.", "author", zh ? "预约中心" : "Booking center", "category", zh ? "订单" : "Order", "status", zh ? "待确认" : "Pending", "metric", "BKG-2406", "cover", "https://images.unsplash.com/photo-1450101499163-c8848c66ca85?q=80&w=1200", "tags", List.of(zh ? "确认" : "Confirm", zh ? "履约" : "Fulfill")),
                        seedCard("id", "book-g-4", "title", zh ? "已完成服务回访" : "Completed service follow-up", "description", zh ? "用于验证预约完成后的评价和回访入口。" : "Validates review and follow-up after appointment completion.", "author", zh ? "客户运营" : "Customer ops", "category", zh ? "回访" : "Follow-up", "status", zh ? "已完成" : "Done", "metric", zh ? "4.9 分" : "4.9 score", "cover", "https://images.unsplash.com/photo-1556761175-5973dc0f32e7?q=80&w=1200", "tags", List.of(zh ? "评价" : "Review", zh ? "回访" : "Follow-up"))
                )
        );
    }

    private GenericWorkflowProfile buildVehicleCommerceProfile(boolean zh) {
        return new GenericWorkflowProfile(
                "commerce-order",
                zh ? "车源交易原型" : "Vehicle marketplace prototype",
                zh ? "选择车源/配置" : "Pick vehicle / trim",
                zh ? "预约看车" : "Book viewing",
                zh ? "推进交付" : "Advance delivery",
                zh ? "本周" : "This week",
                zh ? "看车批次 A" : "Viewing batch A",
                zh ? "已选择车源，等待锁定看车" : "Vehicle selected for viewing",
                zh ? "看车已锁定" : "Viewing locked",
                zh ? "车源转化" : "Vehicle conversion",
                "28",
                "¥86.4w",
                "93%",
                zh ? "待看车" : "Viewings",
                zh ? "交易额" : "GMV",
                zh ? "资料完整" : "Docs",
                zh ? "从车源展示进入详情、预约看车、定金订单和交付节点。" : "Move from listings to detail, viewing appointment, deposit order, and delivery milestones.",
                zh ? "精选车源" : "Vehicle listings",
                zh ? "点击车源看详情，流程按钮模拟看车锁定、付款和交付推进。" : "Open vehicle detail and use buttons to simulate viewing, payment, and delivery.",
                zh ? "车源交易闭环" : "Vehicle transaction loop",
                zh ? "选车源" : "Pick vehicle",
                zh ? "锁看车" : "Lock viewing",
                zh ? "推交付" : "Deliver",
                zh ? "选中车源" : "Selected vehicle",
                zh ? "交易状态" : "Deal status",
                "CAR",
                List.of(
                        seedCard("id", "car-1", "title", zh ? "2022 Model 3 长续航 · 上海牌" : "2022 Model 3 Long Range", "description", zh ? "一手车源，里程 3.2 万公里，支持周末预约看车和第三方检测。" : "Single-owner listing, 32k km, weekend viewing and inspection available.", "author", zh ? "浦东认证车商" : "Pudong dealer", "category", zh ? "新能源" : "EV", "status", zh ? "可预约" : "Bookable", "metric", "¥21.8w", "cover", "https://images.unsplash.com/photo-1560958089-b8a1929cea89?q=80&w=1200", "tags", List.of(zh ? "一手" : "Single-owner", zh ? "检测" : "Inspection")),
                        seedCard("id", "car-2", "title", zh ? "宝马 3 系 · 运动套装" : "BMW 3 Series · Sport", "description", zh ? "保养记录完整，议价后可进入定金锁车流程。" : "Full service history; negotiation can move to deposit lock.", "author", zh ? "虹桥展厅" : "Hongqiao showroom", "category", zh ? "燃油车" : "ICE", "status", zh ? "可议价" : "Negotiable", "metric", "¥18.6w", "cover", "https://images.unsplash.com/photo-1555215695-3004980ad54e?q=80&w=1200", "tags", List.of(zh ? "保养" : "Service", zh ? "议价" : "Negotiate")),
                        seedCard("id", "car-3", "title", zh ? "待确认看车 CAR-2402" : "Pending viewing CAR-2402", "description", zh ? "用户已选择车源和门店，确认后生成看车提醒。" : "Vehicle and showroom selected; confirm to create viewing reminder.", "author", zh ? "交易顾问" : "Advisor", "category", zh ? "看车" : "Viewing", "status", zh ? "待确认" : "Pending", "metric", zh ? "周六 15:00" : "Sat 15:00", "cover", "https://images.unsplash.com/photo-1493238792000-8113da705763?q=80&w=1200", "tags", List.of(zh ? "看车" : "Viewing", zh ? "提醒" : "Reminder")),
                        seedCard("id", "car-4", "title", zh ? "交付资料待补齐" : "Delivery docs pending", "description", zh ? "身份证和保险资料待上传，补齐后进入交付排期。" : "ID and insurance docs pending; upload to schedule delivery.", "author", zh ? "交付中心" : "Delivery center", "category", zh ? "交付" : "Delivery", "status", zh ? "待补资料" : "Docs needed", "metric", zh ? "2 项资料" : "2 docs", "cover", "https://images.unsplash.com/photo-1503376780353-7e6692767b70?q=80&w=1200", "tags", List.of(zh ? "资料" : "Docs", zh ? "交付" : "Delivery"))
                )
        );
    }

    private GenericWorkflowProfile buildPropertyCommerceProfile(boolean zh) {
        return new GenericWorkflowProfile(
                "commerce-order",
                zh ? "房源租赁原型" : "Rental marketplace prototype",
                zh ? "选择房源/户型" : "Pick listing / layout",
                zh ? "预约看房" : "Book viewing",
                zh ? "推进签约" : "Advance lease",
                zh ? "本周" : "This week",
                zh ? "看房批次 B" : "Viewing batch B",
                zh ? "已选择房源，等待确认看房" : "Listing selected for viewing",
                zh ? "看房已确认" : "Viewing confirmed",
                zh ? "租赁转化" : "Rental conversion",
                "42",
                "16",
                "88%",
                zh ? "待看房" : "Viewings",
                zh ? "可签约" : "Lease-ready",
                zh ? "资料完整" : "Docs",
                zh ? "从房源展示进入详情、预约看房、合同资料和签约状态。" : "Move from listings to detail, viewing appointment, lease docs, and contract state.",
                zh ? "精选房源" : "Rental listings",
                zh ? "点击房源看详情，流程按钮模拟看房确认、定金和签约推进。" : "Open listing detail and use buttons to simulate viewing, deposit, and lease progress.",
                zh ? "租赁签约闭环" : "Rental lease loop",
                zh ? "选房源" : "Pick listing",
                zh ? "约看房" : "Book viewing",
                zh ? "推签约" : "Lease",
                zh ? "选中房源" : "Selected listing",
                zh ? "租赁状态" : "Rental status",
                "REN",
                List.of(
                        seedCard("id", "rent-1", "title", zh ? "徐汇滨江一居 · 近地铁" : "Xuhui riverside 1BR · Metro", "description", zh ? "整租一居，家具齐全，支持今晚视频看房和周末线下看房。" : "Furnished 1BR with video viewing tonight and weekend offline tour.", "author", zh ? "徐汇管家" : "Xuhui agent", "category", zh ? "整租" : "Entire unit", "status", zh ? "可看房" : "Viewable", "metric", "¥7,800/月", "cover", "https://images.unsplash.com/photo-1560448204-e02f11c3d0e2?q=80&w=1200", "tags", List.of(zh ? "近地铁" : "Metro", zh ? "整租" : "Entire")),
                        seedCard("id", "rent-2", "title", zh ? "静安合租主卧 · 独卫" : "Jing'an master room · Private bath", "description", zh ? "室友资料已验证，确认后可锁定看房和合同模板。" : "Roommate profiles verified; confirm to lock viewing and contract template.", "author", zh ? "静安管家" : "Jing'an agent", "category", zh ? "合租" : "Shared", "status", zh ? "可签约" : "Lease-ready", "metric", "¥4,600/月", "cover", "https://images.unsplash.com/photo-1505693416388-ac5ce068fe85?q=80&w=1200", "tags", List.of(zh ? "独卫" : "Private bath", zh ? "合租" : "Shared")),
                        seedCard("id", "rent-3", "title", zh ? "待确认看房 REN-2411" : "Pending viewing REN-2411", "description", zh ? "用户已选择房源和时间，确认后进入看房提醒。" : "Listing and time selected; confirm to create viewing reminder.", "author", zh ? "看房日程" : "Viewing schedule", "category", zh ? "看房" : "Viewing", "status", zh ? "待确认" : "Pending", "metric", zh ? "周日 11:00" : "Sun 11:00", "cover", "https://images.unsplash.com/photo-1494526585095-c41746248156?q=80&w=1200", "tags", List.of(zh ? "看房" : "Viewing", zh ? "提醒" : "Reminder")),
                        seedCard("id", "rent-4", "title", zh ? "合同资料待补齐" : "Lease docs pending", "description", zh ? "租客证件和押金规则待确认，补齐后推进签约。" : "Tenant ID and deposit rules pending; complete to advance lease.", "author", zh ? "签约中心" : "Lease center", "category", zh ? "签约" : "Lease", "status", zh ? "待补资料" : "Docs needed", "metric", zh ? "2 项资料" : "2 docs", "cover", "https://images.unsplash.com/photo-1512917774080-9991f1c4c750?q=80&w=1200", "tags", List.of(zh ? "合同" : "Contract", zh ? "押金" : "Deposit"))
                )
        );
    }

    private GenericWorkflowProfile buildEventTicketCommerceProfile(boolean zh) {
        return new GenericWorkflowProfile(
                "commerce-order",
                zh ? "票务报名原型" : "Ticketing prototype",
                zh ? "选择场次/票档" : "Pick session / ticket",
                zh ? "确认报名" : "Confirm registration",
                zh ? "推进核销" : "Advance check-in",
                zh ? "本周" : "This week",
                zh ? "早鸟批次" : "Early-bird batch",
                zh ? "已选择票档，等待锁票" : "Ticket tier selected",
                zh ? "报名已确认" : "Registration confirmed",
                zh ? "票务转化" : "Ticket conversion",
                "320",
                "86%",
                "42",
                zh ? "余票" : "Tickets",
                zh ? "上座率" : "Occupancy",
                zh ? "待核销" : "Check-ins",
                zh ? "从活动页进入场次、票档、报名支付和到场核销状态。" : "Move from event page to session, ticket tier, payment, and check-in state.",
                zh ? "可报名场次" : "Bookable sessions",
                zh ? "点击场次看详情，流程按钮模拟锁票、报名和核销推进。" : "Open session detail and use buttons to simulate ticket lock, registration, and check-in.",
                zh ? "票务报名闭环" : "Ticketing loop",
                zh ? "选场次" : "Pick session",
                zh ? "锁票" : "Lock ticket",
                zh ? "核销" : "Check in",
                zh ? "选中场次" : "Selected session",
                zh ? "报名状态" : "Registration status",
                "TIX",
                List.of(
                        seedCard("id", "ticket-1", "title", zh ? "AI 创作者夜谈 · 早鸟票" : "AI Creator Night · Early bird", "description", zh ? "限量 120 张，报名后生成电子票和入场二维码。" : "Limited to 120 tickets; registration creates e-ticket and QR code.", "author", zh ? "活动运营" : "Event ops", "category", zh ? "早鸟" : "Early bird", "status", zh ? "售卖中" : "On sale", "metric", "¥99", "cover", "https://images.unsplash.com/photo-1505373877841-8d25f7d46678?q=80&w=1200", "tags", List.of(zh ? "早鸟" : "Early bird", zh ? "二维码" : "QR")),
                        seedCard("id", "ticket-2", "title", zh ? "品牌快闪展 · 周末场" : "Brand pop-up · Weekend", "description", zh ? "分时段预约入场，系统会控制每小时入场容量。" : "Timed entry controls hourly capacity.", "author", zh ? "展览团队" : "Exhibition team", "category", zh ? "展览" : "Exhibition", "status", zh ? "可报名" : "Open", "metric", zh ? "余 86 张" : "86 left", "cover", "https://images.unsplash.com/photo-1503428593586-e225b39bddfe?q=80&w=1200", "tags", List.of(zh ? "快闪" : "Pop-up", zh ? "分时" : "Timed")),
                        seedCard("id", "ticket-3", "title", zh ? "待支付报名 TIX-2403" : "Pending registration TIX-2403", "description", zh ? "票档和场次已选，支付后进入电子票状态。" : "Tier and session selected; payment creates e-ticket state.", "author", zh ? "票务中心" : "Ticket center", "category", zh ? "订单" : "Order", "status", zh ? "待支付" : "Pay", "metric", "TIX-2403", "cover", "https://images.unsplash.com/photo-1540575467063-178a50c2df87?q=80&w=1200", "tags", List.of(zh ? "支付" : "Payment", zh ? "电子票" : "E-ticket")),
                        seedCard("id", "ticket-4", "title", zh ? "现场核销队列" : "Check-in queue", "description", zh ? "42 位观众待核销，可从详情查看异常票据。" : "42 attendees pending check-in; detail exposes exception tickets.", "author", zh ? "现场组" : "Onsite team", "category", zh ? "核销" : "Check-in", "status", zh ? "进行中" : "Live", "metric", zh ? "42 人" : "42 people", "cover", "https://images.unsplash.com/photo-1515169067865-5387ec356754?q=80&w=1200", "tags", List.of(zh ? "核销" : "Check-in", zh ? "现场" : "Onsite"))
                )
        );
    }

    private GenericWorkflowProfile buildGenericCommerceProfile(boolean zh) {
        return new GenericWorkflowProfile(
                "commerce-order",
                zh ? "交易原型" : "Commerce prototype",
                zh ? "选择商品/服务" : "Pick item / service",
                zh ? "确认下单" : "Confirm order",
                zh ? "推进交付" : "Advance delivery",
                zh ? "本周" : "This week",
                zh ? "交易批次 A" : "Batch A",
                zh ? "已选择交易对象，等待锁单" : "Item selected for order lock",
                zh ? "订单已锁定" : "Order locked",
                zh ? "交易转化" : "Transaction conversion",
                "46",
                "¥32.8k",
                "95%",
                zh ? "待付款" : "Pending",
                zh ? "交易额" : "GMV",
                zh ? "履约率" : "Fulfillment",
                zh ? "从商品/服务展示进入详情、下单、支付和交付节点。" : "Move from item display to detail, order, payment, and delivery milestones.",
                zh ? "交易对象" : "Trade items",
                zh ? "点击卡片看详情，流程按钮模拟锁单、支付和交付推进。" : "Open card detail and use buttons to simulate order, payment, and delivery.",
                zh ? "交易闭环" : "Transaction loop",
                zh ? "选对象" : "Pick item",
                zh ? "锁订单" : "Lock order",
                zh ? "推交付" : "Deliver",
                zh ? "选中对象" : "Selected item",
                zh ? "订单状态" : "Order status",
                "ORD",
                List.of(
                        seedCard("id", "trade-1", "title", zh ? "标准套餐 · 企业版" : "Standard package · Business", "description", zh ? "适合可配置商品或服务交易，支持确认规格后下单。" : "Fits configurable goods or services; order after confirming specs.", "author", zh ? "交易中心" : "Commerce center", "category", zh ? "套餐" : "Package", "status", zh ? "可下单" : "Orderable", "metric", "¥2,980", "cover", "https://images.unsplash.com/photo-1556742049-0cfed4f6a45d?q=80&w=1200", "tags", List.of(zh ? "套餐" : "Package", zh ? "规格" : "Specs")),
                        seedCard("id", "trade-2", "title", zh ? "定制服务 · 待报价" : "Custom service · Quote pending", "description", zh ? "需要补充需求说明，确认后进入报价和付款流程。" : "Needs requirement details before quote and payment flow.", "author", zh ? "服务顾问" : "Service advisor", "category", zh ? "定制" : "Custom", "status", zh ? "待报价" : "Quote", "metric", zh ? "2 项待补" : "2 missing", "cover", "https://images.unsplash.com/photo-1556761175-b413da4baf72?q=80&w=1200", "tags", List.of(zh ? "报价" : "Quote", zh ? "定制" : "Custom")),
                        seedCard("id", "trade-3", "title", zh ? "待支付订单 ORD-2408" : "Pending order ORD-2408", "description", zh ? "交易对象已锁定，支付后进入履约看板。" : "Item locked; payment moves it into fulfillment board.", "author", zh ? "订单中心" : "Orders", "category", zh ? "订单" : "Order", "status", zh ? "待支付" : "Pay", "metric", "ORD-2408", "cover", "https://images.unsplash.com/photo-1450101499163-c8848c66ca85?q=80&w=1200", "tags", List.of(zh ? "支付" : "Payment", zh ? "履约" : "Fulfillment")),
                        seedCard("id", "trade-4", "title", zh ? "交付节点更新" : "Delivery milestone update", "description", zh ? "当前节点已完成，可推进到验收或复购提醒。" : "Current milestone complete; advance to acceptance or reorder reminder.", "author", zh ? "履约团队" : "Fulfillment team", "category", zh ? "交付" : "Delivery", "status", zh ? "可推进" : "Ready", "metric", zh ? "节点 3/5" : "Step 3/5", "cover", "https://images.unsplash.com/photo-1516321318423-f06f85e504b3?q=80&w=1200", "tags", List.of(zh ? "交付" : "Delivery", zh ? "验收" : "Acceptance"))
                )
        );
    }

    private GenericWorkflowProfile buildCourseLearningProfile(boolean zh) {
        return new GenericWorkflowProfile(
                "learning-progress",
                zh ? "课程学习原型" : "Course learning prototype",
                zh ? "选择课程" : "Choose course",
                zh ? "确认学习" : "Confirm progress",
                zh ? "提交作业" : "Submit assignment",
                zh ? "本周" : "This week",
                zh ? "训练营" : "Bootcamp",
                zh ? "已选择课程任务" : "Course task selected",
                zh ? "作业已提交" : "Assignment submitted",
                zh ? "学习闭环" : "Learning loop",
                "72%",
                "24",
                "9",
                zh ? "完成度" : "Progress",
                zh ? "作业" : "Assignments",
                zh ? "答疑" : "Q&A",
                zh ? "从课程任务进入视频、作业、答疑和学习进度更新。" : "Move from course task to lessons, assignments, Q&A, and progress updates.",
                zh ? "课程任务" : "Course tasks",
                zh ? "打开详情可看学习上下文，流程按钮可推进课程状态。" : "Open detail for learning context and use buttons to advance course state.",
                zh ? "课程学习流程" : "Course learning flow",
                zh ? "选课程" : "Pick course",
                zh ? "标完成" : "Mark done",
                zh ? "交作业" : "Submit",
                zh ? "选中课程" : "Selected course",
                zh ? "学习状态" : "Learning status",
                "CRS",
                List.of(
                        seedCard("id", "course-1", "title", zh ? "增长训练营 · 第 3 课" : "Growth bootcamp · Lesson 3", "description", zh ? "本周核心课程，包含视频、课件和一次课后作业。" : "Core lesson this week with video, slides, and one assignment.", "author", zh ? "训练营导师" : "Bootcamp mentor", "category", zh ? "课程" : "Lesson", "status", zh ? "进行中" : "Active", "metric", zh ? "72% 完成" : "72% done", "cover", "https://images.unsplash.com/photo-1516321318423-f06f85e504b3?q=80&w=1200", "tags", List.of(zh ? "课程" : "Course", zh ? "作业" : "Assignment")),
                        seedCard("id", "course-2", "title", zh ? "待提交作业 · 用户访谈" : "Assignment due · User interview", "description", zh ? "需要上传访谈摘要和三条洞察，提交后进入导师点评。" : "Upload interview summary and three insights for mentor review.", "author", zh ? "作业系统" : "Assignments", "category", zh ? "作业" : "Assignment", "status", zh ? "待提交" : "Due", "metric", zh ? "今晚截止" : "Due tonight", "cover", "https://images.unsplash.com/photo-1517245386807-bb43f82c33c4?q=80&w=1200", "tags", List.of(zh ? "访谈" : "Interview", zh ? "点评" : "Review")),
                        seedCard("id", "course-3", "title", zh ? "直播答疑 · 周四 20:30" : "Live Q&A · Thu 20:30", "description", zh ? "可提前提交问题，系统会把问题加入答疑队列。" : "Submit questions ahead and add them into Q&A queue.", "author", zh ? "助教团队" : "TA team", "category", zh ? "答疑" : "Q&A", "status", zh ? "待开始" : "Upcoming", "metric", zh ? "周四 20:30" : "Thu 20:30", "cover", "https://images.unsplash.com/photo-1497633762265-9d179a990aa6?q=80&w=1200", "tags", List.of(zh ? "直播" : "Live", zh ? "问题" : "Questions")),
                        seedCard("id", "course-4", "title", zh ? "学习计划 · 第 2 周" : "Study plan · Week 2", "description", zh ? "已完成 5/7 个任务，可推进到复盘和证书节点。" : "Completed 5/7 tasks; advance to review and certificate milestone.", "author", zh ? "学习计划" : "Learning plan", "category", zh ? "进度" : "Progress", "status", zh ? "可推进" : "Ready", "metric", "5/7", "cover", "https://images.unsplash.com/photo-1524995997946-a1c2e315a42f?q=80&w=1200", "tags", List.of(zh ? "复盘" : "Review", zh ? "证书" : "Certificate"))
                )
        );
    }

    private String serializeSeedItems(List<Map<String, Object>> items) {
        try {
            return objectMapper.writeValueAsString(items).replace("'", "&#39;");
        } catch (Exception e) {
            log.warn("[Designer] Failed to serialize generic workflow items", e);
            return "[]";
        }
    }

    private String nextRouteId(String routeId) {
        if (routeId != null && routeId.matches("pg\\d+")) {
            int current = Integer.parseInt(routeId.substring(2));
            return "#pg" + (current >= 5 ? 1 : current + 1);
        }
        return "#pg1";
    }

    private String buildSeededFeedJson(boolean zh, int count, ShapeSurfaceProfile profile) {
        if (isPhotographySurface(profile)) {
            return buildPhotographySeededFeedJson(zh, count);
        }
        if (isFashionSurface(profile)) {
            return buildFashionSeededFeedJson(zh, count);
        }
        if (isPetSurface(profile)) {
            return buildPetSeededFeedJson(zh, count);
        }
        if (isStudySurface(profile)) {
            return buildStudySeededFeedJson(zh, count);
        }
        if (profile.layoutRhythm() == ProjectManifest.LayoutRhythm.WATERFALL) {
            return buildLifestyleSeededFeedJson(zh, count);
        }
        return buildKnowledgeSeededFeedJson(zh, count);
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
                <div x-show="hash === '#__ID__'" data-lingnow-flow="photography-discover" class="min-h-screen animate-fade-in bg-slate-50 pb-16">
                  <section class="rounded-[36px] bg-slate-950 p-8 text-white shadow-2xl">
                    <div class="grid gap-8 xl:grid-cols-[minmax(0,1.2fr)_360px] xl:items-end">
                      <div>
                        <span class="inline-flex rounded-full bg-white/10 px-3 py-1 text-xs font-bold text-rose-200">__BADGE__</span>
                        <h1 class="mt-5 max-w-3xl text-4xl font-black leading-tight tracking-tight">__HERO__</h1>
                        <p class="mt-4 max-w-2xl text-sm leading-7 text-slate-300">__DESC__</p>
                        <div class="mt-6 flex flex-wrap gap-3">
                          <button @click="startInquiry()" data-lingnow-action="start-inquiry" class="rounded-full bg-rose-500 px-5 py-3 text-sm font-black text-white shadow-lg shadow-rose-500/30">__CTA_PRIMARY__</button>
                          <button @click="go('#pg3')" data-lingnow-action="view-availability" class="rounded-full border border-white/15 px-5 py-3 text-sm font-bold text-white/85">__CTA_SECONDARY__</button>
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
                          <article @click="openDetail(item)" data-lingnow-action="open-detail" class="group cursor-pointer overflow-hidden rounded-[30px] border border-slate-200 bg-white shadow-sm transition hover:-translate-y-1 hover:shadow-2xl">
                            <div class="aspect-[4/3.2] overflow-hidden bg-slate-100"><img :src="item.cover" class="h-full w-full object-cover transition duration-500 group-hover:scale-105"/></div>
                            <div class="space-y-4 p-5">
                              <div class="flex items-center gap-3">
                                <img :src="item.avatar" class="h-10 w-10 rounded-full object-cover"/>
                                <div class="min-w-0 flex-1"><div class="truncate text-sm font-black text-slate-900" x-text="item.author"></div><div class="truncate text-xs text-slate-500" x-text="item.location"></div></div>
                                <span class="rounded-full bg-emerald-50 px-3 py-1 text-[10px] font-black text-emerald-600">__OPEN__</span>
                              </div>
                              <div><h3 class="line-clamp-2 text-lg font-black text-slate-900" x-text="item.title"></h3><p class="mt-2 line-clamp-2 text-sm leading-6 text-slate-500" x-text="item.description"></p></div>
                              <div class="flex flex-wrap gap-2"><template x-for="tag in item.tags.slice(0,3)" :key="tag"><span class="rounded-full bg-rose-50 px-3 py-1 text-xs font-bold text-rose-600" x-text="'#' + tag"></span></template></div>
                              <div class="flex items-center justify-between border-t border-slate-100 pt-4 text-xs text-slate-500"><span x-text="item.category"></span><button @click.stop="startInquiry(item)" data-lingnow-action="card-inquiry" class="rounded-full bg-slate-950 px-4 py-2 font-black text-white">__INQUIRE__</button></div>
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
                <div x-show="hash === '#__ID__'" data-lingnow-flow="photography-directory" class="min-h-screen animate-fade-in bg-slate-50 pb-16">
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
                        <div class="flex items-center gap-3"><span class="rounded-full bg-emerald-50 px-3 py-1 text-xs font-black text-emerald-600" x-text="person.status"></span><button @click="startInquiry(person)" data-lingnow-action="directory-inquiry" class="rounded-full bg-slate-950 px-5 py-2 text-sm font-black text-white">__CTA__</button></div>
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
                <div x-show="hash === '#__ID__'" data-lingnow-flow="photography-availability" class="min-h-screen animate-fade-in bg-slate-50 pb-16">
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
                      <article @click="pickSlot(slot)" data-lingnow-action="pick-slot" :class="selectedSlot && selectedSlot.day === slot.day && selectedSlot.time === slot.time ? 'border-rose-300 ring-4 ring-rose-100' : 'border-slate-200'" class="cursor-pointer rounded-[28px] border bg-white p-5 shadow-sm transition hover:-translate-y-1 hover:shadow-xl">
                        <div class="flex items-center justify-between"><span class="text-sm font-black text-rose-600" x-text="slot.day"></span><span class="rounded-full bg-emerald-50 px-3 py-1 text-xs font-black text-emerald-600">__OPEN__</span></div>
                        <div class="mt-4 text-2xl font-black text-slate-900" x-text="slot.time"></div>
                        <div class="mt-2 text-sm text-slate-500" x-text="slot.type"></div>
                        <div class="mt-5 rounded-2xl bg-slate-50 p-4 text-sm font-bold text-slate-700" x-text="slot.owner"></div>
                      </article>
                    </template>
                  </section>
                  <section class="mt-6 rounded-[30px] border border-slate-200 bg-white p-6 shadow-sm" x-show="selectedSlot" x-cloak>
                    <div class="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
                      <div>
                        <div class="text-xs font-black uppercase tracking-widest text-rose-500">__SELECTED_LABEL__</div>
                        <div class="mt-2 text-xl font-black text-slate-900"><span x-text="selectedSlot?.day"></span><span class="mx-2">·</span><span x-text="selectedSlot?.time"></span></div>
                        <p class="mt-1 text-sm text-slate-500"><span x-text="selectedSlot?.owner"></span><span class="mx-2">/</span><span x-text="selectedSlot?.type"></span></p>
                      </div>
                      <button @click="confirmBooking()" data-lingnow-action="confirm-booking" class="rounded-full bg-rose-500 px-6 py-3 text-sm font-black text-white shadow-lg shadow-rose-100">__CONFIRM__</button>
                    </div>
                  </section>
                </div>
                """
                .replace("__ID__", routeId)
                .replace("__TITLE__", title)
                .replace("__DESC__", zh ? "把可预约档期作为转化核心，让客户不用来回沟通就能判断是否可约。" : "Make availability the conversion core so clients can decide before back-and-forth.")
                .replace("__OPEN__", zh ? "可预约" : "Open")
                .replace("__SELECTED_LABEL__", zh ? "已选择档期" : "Selected slot")
                .replace("__CONFIRM__", zh ? "锁定档期并询价" : "Hold slot and inquire");
    }

    private String buildPhotographyInquiryComponent(String routeId, String title, boolean zh) {
        return """
                <div x-show="hash === '#__ID__'" data-lingnow-flow="photography-inquiry" class="min-h-screen animate-fade-in bg-slate-50 pb-16">
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
                        <div class="mt-4 space-y-3"><template x-for="lead in column.leads" :key="lead"><button @click="selectedLead = lead" data-lingnow-action="select-lead" :class="selectedLead === lead ? 'bg-rose-50 text-rose-700 ring-2 ring-rose-100' : 'bg-slate-50 text-slate-700'" class="block w-full rounded-2xl p-4 text-left text-sm font-bold transition hover:bg-rose-50" x-text="lead"></button></template></div>
                      </div>
                    </template>
                  </section>
                  <section class="mt-6 grid gap-5 xl:grid-cols-[minmax(0,1fr)_360px]">
                    <div class="rounded-[30px] border border-slate-200 bg-white p-6 shadow-sm">
                      <div class="flex items-center justify-between">
                        <div><h2 class="text-xl font-black text-slate-900">__FORM_TITLE__</h2><p class="mt-1 text-sm text-slate-500">__FORM_DESC__</p></div>
                        <span class="rounded-full bg-emerald-50 px-3 py-1 text-xs font-black text-emerald-600" x-show="bookingConfirmed">__SLOT_HELD__</span>
                      </div>
                      <div class="mt-5 grid gap-3 md:grid-cols-2">
                        <input x-model="draftInquiry.photographer" class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm outline-none focus:border-rose-300 focus:bg-white" placeholder="__PHOTOGRAPHER__">
                        <input x-model="draftInquiry.city" class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm outline-none focus:border-rose-300 focus:bg-white" placeholder="__CITY__">
                        <input x-model="draftInquiry.service" class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm outline-none focus:border-rose-300 focus:bg-white" placeholder="__SERVICE__">
                        <input x-model="draftInquiry.budget" class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm outline-none focus:border-rose-300 focus:bg-white" placeholder="__BUDGET__">
                      </div>
                      <textarea x-model="draftInquiry.note" class="mt-3 h-24 w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm outline-none focus:border-rose-300 focus:bg-white" placeholder="__NOTE__"></textarea>
                      <button @click="submitInquiry()" data-lingnow-action="submit-inquiry" class="mt-4 w-full rounded-2xl bg-rose-500 px-5 py-3 text-sm font-black text-white shadow-lg shadow-rose-100">__SUBMIT__</button>
                    </div>
                    <aside class="rounded-[30px] border border-slate-200 bg-white p-6 shadow-sm">
                      <h3 class="text-lg font-black text-slate-900">__NEXT_TITLE__</h3>
                      <div class="mt-4 space-y-3 text-sm text-slate-600">
                        <div class="rounded-2xl bg-slate-50 p-4">1. __NEXT_A__</div>
                        <div class="rounded-2xl bg-slate-50 p-4">2. __NEXT_B__</div>
                        <div class="rounded-2xl bg-slate-50 p-4">3. __NEXT_C__</div>
                      </div>
                      <div x-show="inquirySubmitted" x-cloak class="mt-4 rounded-2xl bg-emerald-50 p-4 text-sm font-bold text-emerald-700">__SUBMITTED__</div>
                    </aside>
                  </section>
                </div>
                """
                .replace("__ID__", routeId)
                .replace("__TITLE__", title)
                .replace("__DESC__", zh ? "用看板承接客户询价，从预算、城市、类型到报价状态都能继续推进。" : "Track client inquiries from budget, city, shoot type, and quote status.")
                .replace("__FORM_TITLE__", zh ? "新建询价单" : "Create inquiry")
                .replace("__FORM_DESC__", zh ? "从作品、摄影师或档期页带入上下文，提交后进入订单跟进。" : "Context flows in from portfolio, photographer, or availability pages before order follow-up.")
                .replace("__SLOT_HELD__", zh ? "已锁定档期" : "Slot held")
                .replace("__PHOTOGRAPHER__", zh ? "摄影师 / 工作室" : "Photographer / studio")
                .replace("__CITY__", zh ? "城市 / 拍摄地" : "City / location")
                .replace("__SERVICE__", zh ? "拍摄类型" : "Shoot type")
                .replace("__BUDGET__", zh ? "预算范围" : "Budget")
                .replace("__NOTE__", zh ? "补充拍摄时间、人数、风格参考或交付要求..." : "Add timing, people, style reference, or delivery needs...")
                .replace("__SUBMIT__", zh ? "提交询价并生成订单" : "Submit inquiry and create order")
                .replace("__NEXT_TITLE__", zh ? "提交后流程" : "After submit")
                .replace("__NEXT_A__", zh ? "摄影师确认档期与报价" : "Photographer confirms slot and quote")
                .replace("__NEXT_B__", zh ? "客户确认方案并支付定金" : "Client confirms plan and deposit")
                .replace("__NEXT_C__", zh ? "订单进入拍摄与交付看板" : "Order moves into shoot and delivery board")
                .replace("__SUBMITTED__", zh ? "询价已提交，正在跳转到订单交付。" : "Inquiry submitted and moved to orders.");
    }

    private String buildPhotographyOrdersComponent(String routeId, String title, boolean zh) {
        return """
                <div x-show="hash === '#__ID__'" data-lingnow-flow="photography-orders" class="min-h-screen animate-fade-in bg-slate-50 pb-16">
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
                        <div class="text-right"><div class="text-xl font-black text-slate-900" x-text="order.amount"></div><button @click="advanceOrder(order)" data-lingnow-action="advance-order" class="mt-2 rounded-full bg-rose-500 px-4 py-2 text-xs font-black text-white">__CTA__</button></div>
                      </article>
                    </template>
                  </section>
                  <section x-show="activeOrder" x-cloak class="mt-6 rounded-[30px] border border-emerald-100 bg-emerald-50 p-6 text-emerald-800">
                    <div class="text-xs font-black uppercase tracking-widest">__UPDATED__</div>
                    <div class="mt-2 text-lg font-black"><span x-text="activeOrder?.id"></span><span class="mx-2">·</span><span x-text="activeOrder?.stage"></span></div>
                    <p class="mt-1 text-sm">__UPDATED_DESC__</p>
                  </section>
                </div>
                """
                .replace("__ID__", routeId)
                .replace("__TITLE__", title)
                .replace("__DESC__", zh ? "把已成交预约继续推进到合同、定金、拍摄、选片和交付。" : "Move booked shoots through contract, deposit, shoot, selection, and delivery.")
                .replace("__CTA__", zh ? "推进进度" : "Advance")
                .replace("__UPDATED__", zh ? "状态已更新" : "Status updated")
                .replace("__UPDATED_DESC__", zh ? "这是原型态的即时状态变更，用于证明流程不是静态页面。" : "This instant prototype state change proves the flow is not a static page.");
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

    private boolean isPetSurface(ShapeSurfaceProfile profile) {
        return profile != null && containsAny(
                (profile.surfaceLabelZh() + " " + profile.surfaceLabelEn() + " " + profile.categoryFallbackZh()).toLowerCase(Locale.ROOT),
                "宠物", "萌宠", "pet");
    }

    private boolean isPhotographySurface(ShapeSurfaceProfile profile) {
        return profile != null && containsAny(
                (profile.surfaceLabelZh() + " " + profile.surfaceLabelEn() + " " + profile.categoryFallbackZh()).toLowerCase(Locale.ROOT),
                "摄影", "photography", "photographer");
    }

    private boolean isFashionSurface(ShapeSurfaceProfile profile) {
        return profile != null && containsAny(
                (profile.surfaceLabelZh() + " " + profile.surfaceLabelEn() + " " + profile.categoryFallbackZh()).toLowerCase(Locale.ROOT),
                "穿搭", "时尚", "look", "outfit", "ootd");
    }

    private boolean isStudySurface(ShapeSurfaceProfile profile) {
        return profile != null && containsAny(
                (profile.surfaceLabelZh() + " " + profile.surfaceLabelEn() + " " + profile.categoryFallbackZh()).toLowerCase(Locale.ROOT),
                "共读", "读书", "阅读", "笔记", "study", "reading", "note");
    }

    private String buildPetSeededFeedJson(boolean zh, int count) {
        List<Map<String, Object>> cards = new ArrayList<>();
        cards.add(seedCard(
                "id", "pet-1",
                "title", zh ? "布偶猫第一次坐地铁，评论区都在云吸猫" : "A ragdoll cat's first metro ride has everyone melting",
                "author", zh ? "团子和铲屎官" : "Tuanzi & human",
                "avatar", "https://images.unsplash.com/photo-1494790108377-be9c29b29330?q=80&w=256",
                "description", zh ? "从出门包到安抚零食，记录一次低压力带猫出行，适合新手收藏。" : "A low-stress cat travel log with carrier setup and calming snacks.",
                "cover", "https://images.unsplash.com/photo-1518791841217-8f162f1e1131?q=80&w=1200",
                "location", zh ? "上海 · 猫咪日常" : "Shanghai · Cat life",
                "time", zh ? "12分钟前" : "12m ago",
                "category", zh ? "猫咪" : "Cats",
                "mediaType", zh ? "图文" : "Photo",
                "likes", "4.2w",
                "comments", "836",
                "collects", "2.1k",
                "tags", List.of(zh ? "布偶猫" : "Ragdoll", zh ? "出行" : "Travel", zh ? "新手养猫" : "Cat care")
        ));
        cards.add(seedCard(
                "id", "pet-2",
                "title", zh ? "柴犬雨天散步装备：脚套到底有没有必要？" : "Rainy-day dog walk gear: are paw covers worth it?",
                "author", zh ? "豆柴小满" : "Mame Shiba Momo",
                "avatar", "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?q=80&w=256",
                "description", zh ? "把雨衣、牵引绳、擦脚流程和踩坑点整理成清单。" : "Raincoat, leash, paw cleaning, and what not to buy in one checklist.",
                "cover", "https://images.unsplash.com/photo-1517849845537-4d257902454a?q=80&w=1200",
                "location", zh ? "杭州 · 狗狗护理" : "Hangzhou · Dog care",
                "time", zh ? "34分钟前" : "34m ago",
                "category", zh ? "狗狗" : "Dogs",
                "mediaType", zh ? "清单" : "Checklist",
                "likes", "2.8w",
                "comments", "512",
                "collects", "1.7k",
                "tags", List.of(zh ? "柴犬" : "Shiba", zh ? "雨天散步" : "Rain walk", zh ? "护理" : "Care")
        ));
        cards.add(seedCard(
                "id", "pet-3",
                "title", zh ? "领养第 30 天：小橘终于敢在沙发上睡觉了" : "Day 30 after adoption: the orange cat finally sleeps on the sofa",
                "author", zh ? "橘子救助日记" : "Orange rescue diary",
                "avatar", "https://images.unsplash.com/photo-1438761681033-6461ffad8d80?q=80&w=256",
                "description", zh ? "记录从躲床底到主动蹭手的变化，也写了适应期要避开的事。" : "From hiding under the bed to asking for pets, plus adoption adjustment tips.",
                "cover", "https://images.unsplash.com/photo-1574158622682-e40e69881006?q=80&w=1200",
                "location", zh ? "广州 · 领养故事" : "Guangzhou · Adoption",
                "time", zh ? "1小时前" : "1h ago",
                "category", zh ? "领养" : "Adoption",
                "mediaType", zh ? "故事" : "Story",
                "likes", "5.6w",
                "comments", "1.3k",
                "collects", "3.4k",
                "tags", List.of(zh ? "领养" : "Adoption", zh ? "橘猫" : "Orange cat", zh ? "适应期" : "Adjustment")
        ));
        cards.add(seedCard(
                "id", "pet-4",
                "title", zh ? "宠物摄影小技巧：在家也能拍出毛孩子证件照" : "Pet photo tips for clean portrait shots at home",
                "author", zh ? "毛球影像" : "Furball Studio",
                "avatar", "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?q=80&w=256",
                "description", zh ? "窗边自然光、零食引导和背景布选择，3 分钟布置一个小拍摄角。" : "Window light, treat cues, and backdrop picks for a three-minute mini studio.",
                "cover", "https://images.unsplash.com/photo-1548199973-03cce0bbc87b?q=80&w=1200",
                "location", zh ? "北京 · 拍照教程" : "Beijing · Photo tips",
                "time", zh ? "2小时前" : "2h ago",
                "category", zh ? "摄影" : "Photo",
                "mediaType", zh ? "教程" : "Guide",
                "likes", "1.9w",
                "comments", "274",
                "collects", "1.5k",
                "tags", List.of(zh ? "宠物摄影" : "Pet photo", zh ? "教程" : "Guide", zh ? "在家拍" : "Home shoot")
        ));
        try {
            return objectMapper.writeValueAsString(cards.subList(0, Math.min(cards.size(), Math.max(count, 4))));
        } catch (Exception e) {
            log.warn("[Designer] Failed to serialize pet feed cards", e);
            return "[]";
        }
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

    private String buildStudySeededFeedJson(boolean zh, int count) {
        List<Map<String, Object>> cards = new ArrayList<>();
        cards.add(seedCard(
                "id", "study-1",
                "title", zh ? "本周共读：第一章慢读标注，18 条高亮已同步" : "This week's reading: chapter one highlights synced",
                "author", zh ? "精读会主持人" : "Reading host",
                "avatar", "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?q=80&w=256",
                "description", zh ? "围绕关键段落做慢读，高亮、摘要和疑问会进入小组讨论流。" : "Slow-read key passages; highlights, summaries, and questions flow into group discussion.",
                "cover", "https://images.unsplash.com/photo-1519682337058-a94d519337bc?q=80&w=1200",
                "location", zh ? "第一章 · 进行中" : "Chapter 1 · Active",
                "time", zh ? "刚刚更新" : "Updated now",
                "category", zh ? "共读任务" : "Reading task",
                "mediaType", zh ? "章节" : "Chapter",
                "likes", "836",
                "comments", "64",
                "collects", "218",
                "tags", List.of(zh ? "慢读" : "Slow reading", zh ? "高亮" : "Highlights", zh ? "本周任务" : "Weekly task")
        ));
        cards.add(seedCard(
                "id", "study-2",
                "title", zh ? "笔记精选：关于注意力的三条核心论点" : "Note picks: three core arguments about attention",
                "author", zh ? "清单同学" : "List Maker",
                "avatar", "https://images.unsplash.com/photo-1502685104226-ee32379fefbe?q=80&w=256",
                "description", zh ? "把分散高亮整理成可复盘的论点卡，适合收藏到个人笔记库。" : "Turns scattered highlights into argument cards ready for review.",
                "cover", "https://images.unsplash.com/photo-1524995997946-a1c2e315a42f?q=80&w=1200",
                "location", zh ? "笔记库" : "Note library",
                "time", zh ? "25分钟前" : "25m ago",
                "category", zh ? "精选笔记" : "Featured note",
                "mediaType", zh ? "笔记" : "Note",
                "likes", "1.2k",
                "comments", "93",
                "collects", "640",
                "tags", List.of(zh ? "注意力" : "Attention", zh ? "论点" : "Argument", zh ? "收藏" : "Save")
        ));
        cards.add(seedCard(
                "id", "study-3",
                "title", zh ? "讨论串：读到这里你会怎么理解“主动选择”？" : "Thread: how do you read 'active choice' here?",
                "author", zh ? "讨论区" : "Discussion board",
                "avatar", "https://images.unsplash.com/photo-1494790108377-be9c29b29330?q=80&w=256",
                "description", zh ? "高赞回复已经把原文、生活例子和反对意见并排展开。" : "Top replies compare original text, life examples, and counterarguments.",
                "cover", "https://images.unsplash.com/photo-1456513080510-7bf3a84b82f8?q=80&w=1200",
                "location", zh ? "讨论 · 热门" : "Discussion · Hot",
                "time", zh ? "1小时前" : "1h ago",
                "category", zh ? "讨论" : "Discussion",
                "mediaType", zh ? "回复" : "Replies",
                "likes", "960",
                "comments", "132",
                "collects", "310",
                "tags", List.of(zh ? "讨论" : "Discussion", zh ? "回复" : "Replies", zh ? "主动选择" : "Choice")
        ));
        cards.add(seedCard(
                "id", "study-4",
                "title", zh ? "学习进度：第 2 次直播前需要完成的 4 件事" : "Progress: four things before the second live session",
                "author", zh ? "学习计划" : "Study plan",
                "avatar", "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?q=80&w=256",
                "description", zh ? "阅读、标注、提交问题、回复一条讨论，完成后会更新小组进度。" : "Read, highlight, submit a question, and reply once to update group progress.",
                "cover", "https://images.unsplash.com/photo-1497633762265-9d179a990aa6?q=80&w=1200",
                "location", zh ? "进度 · 64%" : "Progress · 64%",
                "time", zh ? "今晚 20:30" : "Tonight 20:30",
                "category", zh ? "学习进度" : "Progress",
                "mediaType", zh ? "清单" : "Checklist",
                "likes", "512",
                "comments", "48",
                "collects", "188",
                "tags", List.of(zh ? "进度" : "Progress", zh ? "直播" : "Live", zh ? "清单" : "Checklist")
        ));
        try {
            return objectMapper.writeValueAsString(cards.subList(0, Math.min(cards.size(), Math.max(count, 4))));
        } catch (Exception e) {
            log.warn("[Designer] Failed to serialize study feed cards", e);
            return "[]";
        }
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
                    "<a @click=\"hash='#%s'\" :class=\"hash==='#%s'?'shell-nav-active':''\" class=\"flex items-center gap-3 px-4 py-2.5 rounded-xl text-slate-700 hover:bg-slate-100 transition-all text-sm\">%s</a>\n",
                    route.id, route.id, label));
        }
        return fallbackNav.toString();
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

    private String buildFashionSeededFeedJson(boolean zh, int count) {
        List<Map<String, Object>> cards = new ArrayList<>();
        cards.add(seedCard(
                "id", "fashion-1",
                "title", zh ? "158 小个子通勤穿搭：西装短外套 + 直筒裤真的很显高" : "A petite workwear look that actually elongates the frame",
                "author", zh ? "小鹿今日穿什么" : "Lulu OOTD",
                "avatar", "https://images.unsplash.com/photo-1494790108377-be9c29b29330?q=80&w=256",
                "description", zh ? "今天这套把比例重点放在短外套和高腰裤，通勤稳妥又不会太板正。" : "Cropped blazer plus high-waist trousers keeps the commute look sharp without feeling stiff.",
                "cover", "https://images.unsplash.com/photo-1529139574466-a303027c1d8b?q=80&w=1200",
                "location", zh ? "上海 · 通勤穿搭" : "Shanghai · Workwear",
                "time", zh ? "12分钟前" : "12m ago",
                "category", zh ? "通勤" : "Workwear",
                "mediaType", zh ? "图文" : "Photo",
                "likes", "2.4w",
                "comments", "982",
                "collects", "4.1k",
                "tags", List.of(zh ? "小个子" : "Petite", zh ? "通勤穿搭" : "Workwear", zh ? "显高" : "Elongating")
        ));
        cards.add(seedCard(
                "id", "fashion-2",
                "title", zh ? "周末约会穿搭：针织开衫 + 缎面半裙，温柔但不无聊" : "A soft date-night look with knitwear and a satin skirt",
                "author", zh ? "周末衣橱计划" : "Weekend Closet",
                "avatar", "https://images.unsplash.com/photo-1544005313-94ddf0286df2?q=80&w=256",
                "description", zh ? "这套比较适合春天约会，颜色低饱和但镜头里很有层次。" : "Muted tones, better texture, and enough shape to feel dressed without overdoing it.",
                "cover", "https://images.unsplash.com/photo-1483985988355-763728e1935b?q=80&w=1200",
                "location", zh ? "杭州 · 周末穿搭" : "Hangzhou · Weekend",
                "time", zh ? "36分钟前" : "36m ago",
                "category", zh ? "约会" : "Date night",
                "mediaType", zh ? "视频" : "Video",
                "likes", "1.9w",
                "comments", "764",
                "collects", "3.2k",
                "tags", List.of(zh ? "约会穿搭" : "Date look", zh ? "半裙" : "Skirt", zh ? "温柔感" : "Soft tone")
        ));
        cards.add(seedCard(
                "id", "fashion-3",
                "title", zh ? "旅行穿搭清单：一件风衣搞定机场、拍照和夜晚降温" : "One trench coat that covers airport, photo spots, and cooler nights",
                "author", zh ? "旅行也要会搭配" : "Travel Fits",
                "avatar", "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?q=80&w=256",
                "description", zh ? "旅行箱里留一件能叠穿的外套，真的能省很多搭配成本。" : "One layerable trench solves airport comfort, city photos, and late-night temperature drops.",
                "cover", "https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?q=80&w=1200",
                "location", zh ? "北京 · 旅行穿搭" : "Beijing · Travel",
                "time", zh ? "1小时前" : "1h ago",
                "category", zh ? "旅行" : "Travel",
                "mediaType", zh ? "图文" : "Photo",
                "likes", "1.6w",
                "comments", "618",
                "collects", "2.8k",
                "tags", List.of(zh ? "旅行穿搭" : "Travel look", zh ? "风衣" : "Trench", zh ? "叠穿" : "Layering")
        ));
        cards.add(seedCard(
                "id", "fashion-4",
                "title", zh ? "梨形身材怎么挑牛仔裤？这 3 条版型上身差别很大" : "Three denim fits that sit very differently on a pear-shaped frame",
                "author", zh ? "梨形穿搭实验室" : "Fit Notes",
                "avatar", "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?q=80&w=256",
                "description", zh ? "把直筒、微喇和阔腿放在一起看，版型差别会比想象里更明显。" : "Seeing straight, flare, and wide-leg side by side makes the silhouette difference obvious.",
                "cover", "https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?q=80&w=1200",
                "location", zh ? "成都 · 身材参考" : "Chengdu · Body type",
                "time", zh ? "2小时前" : "2h ago",
                "category", zh ? "身材参考" : "Fit guide",
                "mediaType", zh ? "图文" : "Photo",
                "likes", "2.1w",
                "comments", "1.1k",
                "collects", "4.8k",
                "tags", List.of(zh ? "梨形身材" : "Pear shape", zh ? "牛仔裤" : "Denim", zh ? "版型" : "Fit")
        ));
        cards.add(seedCard(
                "id", "fashion-5",
                "title", zh ? "早八穿搭救急：白衬衫不无聊的 4 种搭配法" : "Four ways to make a white shirt feel less boring at 8AM",
                "author", zh ? "上班族衣橱" : "Office Fits",
                "avatar", "https://images.unsplash.com/photo-1438761681033-6461ffad8d80?q=80&w=256",
                "description", zh ? "加马甲、叠背心、换腰带，白衬衫真的不只有一种穿法。" : "A vest, an inner tank, or a new belt can completely change the same white shirt.",
                "cover", "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?q=80&w=1200",
                "location", zh ? "深圳 · 早八通勤" : "Shenzhen · 8AM",
                "time", zh ? "3小时前" : "3h ago",
                "category", zh ? "基础款" : "Basics",
                "mediaType", zh ? "图文" : "Photo",
                "likes", "1.3w",
                "comments", "432",
                "collects", "2.1k",
                "tags", List.of(zh ? "白衬衫" : "White shirt", zh ? "早八" : "8AM", zh ? "通勤" : "Commute")
        ));
        cards.add(seedCard(
                "id", "fashion-6",
                "title", zh ? "本周高收藏单品：一条深蓝西裤为什么能搭出 5 套风格" : "Why one navy trouser keeps showing up in saved looks this week",
                "author", zh ? "单品研究所" : "Piece Lab",
                "avatar", "https://images.unsplash.com/photo-1503023345310-bd7c1de61c7d?q=80&w=256",
                "description", zh ? "同一条西裤换上鞋、外套和包，风格从通勤到休闲切得很自然。" : "Changing the shoes, layer, and bag turns the same trouser from workwear to casual without effort.",
                "cover", "https://images.unsplash.com/photo-1506629905607-d9c75e4a2d53?q=80&w=1200",
                "location", zh ? "广州 · 单品拆解" : "Guangzhou · Styling",
                "time", zh ? "5小时前" : "5h ago",
                "category", zh ? "单品拆解" : "Styling",
                "mediaType", zh ? "图文" : "Photo",
                "likes", "1.7w",
                "comments", "520",
                "collects", "3.7k",
                "tags", List.of(zh ? "西裤" : "Trousers", zh ? "单品拆解" : "Styling", zh ? "高收藏" : "Most saved")
        ));

        try {
            return objectMapper.writeValueAsString(cards.subList(0, Math.min(cards.size(), Math.max(count, 4))));
        } catch (Exception e) {
            log.warn("[Designer] Failed to serialize fashion feed cards", e);
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

    private String buildFallbackCategoryNav(ProjectManifest manifest, List<Route> routes) {
        boolean zh = manifest.getMetaData() == null || !"EN".equalsIgnoreCase(manifest.getMetaData().getOrDefault("lang", "ZH"));
        ShapeSurfaceProfile profile = buildShapeSurfaceProfile(manifest);
        List<Route> contentRoutes = routes.stream().filter(this::isContentFirstRoute).toList();
        if (contentRoutes.size() >= 2) {
            StringBuilder nav = new StringBuilder();
            for (Route route : contentRoutes) {
                String label = escapeHtml(route.name == null || route.name.isBlank() ? route.id : route.name);
                nav.append(String.format(
                        "<button @click=\"go('#%s')\" :class=\"hash==='#%s'?'shell-pill-active':'bg-white text-slate-600'\" class=\"inline-flex items-center gap-2 rounded-full border border-slate-200 px-4 py-2 text-sm font-semibold transition-all hover:border-slate-300\">%s</button>\n",
                        route.id,
                        route.id,
                        label
                ));
            }
            return nav.toString();
        }

        String homeRouteId = contentRoutes.stream()
                .map(route -> route.id)
                .findFirst()
                .orElse(routes.isEmpty() ? "pg1" : routes.get(0).id);
        List<String> categories = zh ? profile.categoriesZh() : profile.categoriesEn();
        StringBuilder nav = new StringBuilder();
        for (String category : categories) {
            String normalizedCategory = escapeHtml(category);
            String activeValue = ("推荐".equals(category) || "For you".equals(category)) ? "" : normalizedCategory;
            nav.append(String.format(
                    "<button @click=\"activeCategory='%s'; go('#%s')\" :class=\"((!activeCategory && '%s'==='') || activeCategory === '%s')?'shell-pill-active':'bg-white text-slate-600'\" class=\"inline-flex items-center gap-2 rounded-full border border-slate-200 px-4 py-2 text-sm font-semibold transition-all hover:border-slate-300\">%s</button>\n",
                    activeValue,
                    homeRouteId,
                    activeValue,
                    normalizedCategory,
                    normalizedCategory
            ));
        }
        return nav.toString();
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

    private boolean hasClickBindingAttributes(String attrs) {
        if (attrs == null || attrs.isBlank()) {
            return false;
        }
        String lower = attrs.toLowerCase(Locale.ROOT);
        return lower.contains("@click") || lower.contains("x-on:click");
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

    private String safe(String value) {
        return value == null ? "" : value;
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

    private boolean hasDeadButtons(String html) {
        if (html == null || html.isBlank()) {
            return false;
        }
        Matcher matcher = Pattern.compile("(?is)<button\\b([^>]*)>").matcher(html);
        while (matcher.find()) {
            String attrs = matcher.group(1);
            if (!hasClickBindingAttributes(attrs)) {
                return true;
            }
        }
        return false;
    }

    private String autoWireInteractiveButtons(ProjectManifest manifest, Route route, String html) {
        if (html == null || html.isBlank()) {
            return html;
        }
        Matcher matcher = Pattern.compile("(?is)<button\\b((?:(?!>).)*)>(.*?)</button>").matcher(html);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String attrs = matcher.group(1);
            String lowerAttrs = attrs == null ? "" : attrs.toLowerCase(Locale.ROOT);
            if (hasClickBindingAttributes(lowerAttrs)) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }
            String innerHtml = matcher.group(2);
            String label = innerHtml == null ? "" : innerHtml.replaceAll("(?is)<[^>]+>", " ").replaceAll("\\s+", " ").trim();
            if (label.isBlank()) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }
            String target = inferLooseActionTarget(manifest, route, label);
            String clickExpr = target == null || target.isBlank()
                    ? "handleLooseAction('" + escapeJsSingleQuoted(label) + "')"
                    : "handleLooseAction('" + escapeJsSingleQuoted(label) + "', '" + escapeJsSingleQuoted(target) + "')";
            String actionAttr = lowerAttrs.contains("data-lingnow-action=")
                    ? ""
                    : " data-lingnow-action=\"auto-" + slugifyActionLabel(label) + "\"";
            String wiredButton = "<button" + actionAttr + " @click=\"" + clickExpr + "\"" + attrs + ">" + innerHtml + "</button>";
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(wiredButton));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String inferLooseActionTarget(ProjectManifest manifest, Route route, String label) {
        String normalized = label == null ? "" : label.toLowerCase(Locale.ROOT);
        List<Route> routes = extractRoutes(manifest);
        if (containsAny(normalized, "详情")) {
            return "#detail";
        }
        if (containsAny(normalized, "线索")) {
            return routeIdByKeywords(routes, route, "lead", "线索");
        }
        if (containsAny(normalized, "商机")) {
            return routeIdByKeywords(routes, route, "opportunity", "商机");
        }
        if (containsAny(normalized, "客户成功", "续费")) {
            return routeIdByKeywords(routes, route, "customer", "success", "续费");
        }
        if (containsAny(normalized, "预警", "风险")) {
            return routeIdByKeywords(routes, route, "alert", "预警", "风险");
        }
        if (containsAny(normalized, "分析", "报表", "看板")) {
            return routeIdByKeywords(routes, route, "analytics", "dashboard", "分析", "看板");
        }
        if (containsAny(normalized, "订单", "交付", "履约", "支付")) {
            return routeIdByKeywords(routes, route, "order", "fulfillment", "delivery", "订单", "交付", "履约");
        }
        if (containsAny(normalized, "预约", "档期", "看车", "看房", "报名")) {
            return routeIdByKeywords(routes, route, "reservation", "appointment", "availability", "看车", "看房", "报名", "档期");
        }
        if (route != null && route.id != null && !route.id.isBlank()) {
            return "#" + route.id;
        }
        return firstPrimaryRouteId(routes).isBlank() ? "#pg1" : "#" + firstPrimaryRouteId(routes);
    }

    private String routeIdByKeywords(List<Route> routes, Route fallbackRoute, String... keywords) {
        if (routes != null) {
            for (Route candidate : routes) {
                String haystack = ((candidate.id == null ? "" : candidate.id) + " " + (candidate.name == null ? "" : candidate.name)).toLowerCase(Locale.ROOT);
                if (containsAny(haystack, keywords)) {
                    return "#" + candidate.id;
                }
            }
        }
        if (fallbackRoute != null && fallbackRoute.id != null && !fallbackRoute.id.isBlank()) {
            return "#" + fallbackRoute.id;
        }
        return "#pg1";
    }

    private String slugifyActionLabel(String label) {
        String normalized = label == null ? "action" : label.toLowerCase(Locale.ROOT);
        String slug = normalized.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", "-").replaceAll("(^-|-$)", "");
        return slug.isBlank() ? "action" : slug;
    }

    private String escapeJsSingleQuoted(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private String buildDeterministicUtilityButtons(ProjectManifest manifest, List<Route> routes) {
        boolean zh = manifest.getMetaData() == null || !"EN".equalsIgnoreCase(manifest.getMetaData().getOrDefault("lang", "ZH"));
        String source = ((manifest.getUserIntent() == null ? "" : manifest.getUserIntent()) + " "
                + (manifest.getArchetype() == null ? "" : manifest.getArchetype()) + " "
                + (manifest.getOverview() == null ? "" : manifest.getOverview())).toLowerCase(Locale.ROOT);
        String primaryTarget = "#" + firstPrimaryRouteId(routes);
        String secondaryTarget = routeIdByKeywords(routes, null, "opportunity", "order", "dashboard", "analytics", "商机", "订单", "看板", "分析");
        if (containsAny(source, "crm", "线索", "商机", "客户成功", "pipeline")) {
            return String.join("\n",
                    shellUtilityButton(zh ? "新建线索" : "New lead", "openComposer('lead', '" + primaryTarget + "')", "utility-create-lead"),
                    shellUtilityButton(zh ? "分配负责人" : "Assign owner", "handleLooseAction('" + (zh ? "分配负责人" : "Assign owner") + "', '" + primaryTarget + "')", "utility-assign-owner"));
        }
        if (containsAny(source, "预约", "预订", "档期", "appointment", "reservation", "service")) {
            return String.join("\n",
                    shellUtilityButton(zh ? "新建预约" : "New booking", "openComposer('service', '" + primaryTarget + "')", "utility-create-booking"),
                    shellUtilityButton(zh ? "查看档期" : "View schedule", "handleLooseAction('" + (zh ? "查看档期" : "View schedule") + "', '" + secondaryTarget + "')", "utility-view-schedule"));
        }
        if (containsAny(source, "看板", "运维", "监控", "dashboard", "ops", "alert")) {
            return String.join("\n",
                    shellUtilityButton(zh ? "处理告警" : "Handle alert", "handleLooseAction('" + (zh ? "处理告警" : "Handle alert") + "', '" + primaryTarget + "')", "utility-handle-alert"),
                    shellUtilityButton(zh ? "查看工单" : "View tickets", "handleLooseAction('" + (zh ? "查看工单" : "View tickets") + "', '" + secondaryTarget + "')", "utility-view-tickets"));
        }
        if (containsAny(source, "商城", "交易", "订单", "预售", "commerce", "order")) {
            return String.join("\n",
                    shellUtilityButton(zh ? "新建订单" : "New order", "openComposer('order', '" + primaryTarget + "')", "utility-create-order"),
                    shellUtilityButton(zh ? "查看履约" : "View fulfillment", "handleLooseAction('" + (zh ? "查看履约" : "View fulfillment") + "', '" + secondaryTarget + "')", "utility-view-fulfillment"));
        }
        if (containsAny(source, "课程", "学习", "共读", "训练营", "learn", "course")) {
            return String.join("\n",
                    shellUtilityButton(zh ? "开始任务" : "Start task", "handleLooseAction('" + (zh ? "开始任务" : "Start task") + "', '" + primaryTarget + "')", "utility-start-task"),
                    shellUtilityButton(zh ? "查看进度" : "View progress", "handleLooseAction('" + (zh ? "查看进度" : "View progress") + "', '" + secondaryTarget + "')", "utility-view-progress"));
        }
        if (containsAny(source, "小红书", "穿搭", "ootd", "搭配", "种草", "时尚")) {
            return "";
        }
        return shellUtilityButton(zh ? "新建记录" : "New item", "openComposer('generic', '" + primaryTarget + "')", "utility-create-item");
    }

    private String buildDeterministicPersonalLinks(ProjectManifest manifest, List<Route> routes) {
        boolean zh = manifest.getMetaData() == null || !"EN".equalsIgnoreCase(manifest.getMetaData().getOrDefault("lang", "ZH"));
        String profileTarget = routeIdByKeywords(routes, null, "profile", "user", "主页", "个人");
        String primaryTarget = profileTarget == null || profileTarget.isBlank() ? "#" + firstPrimaryRouteId(routes) : profileTarget;
        return String.join("\n",
                "<button @click=\"handleLooseAction('" + (zh ? "个人中心" : "Profile") + "', '" + primaryTarget + "')\" class=\"flex w-full items-center gap-3 px-4 py-3 text-left text-sm text-slate-600 hover:bg-slate-50\">" + (zh ? "个人中心" : "Profile") + "</button>",
                "<button @click=\"showToast('" + (zh ? "通知设置已打开" : "Notifications opened") + "')\" class=\"flex w-full items-center gap-3 px-4 py-3 text-left text-sm text-slate-600 hover:bg-slate-50\">" + (zh ? "通知设置" : "Notifications") + "</button>");
    }

    private String buildContentPublishAction(ProjectManifest manifest, List<Route> routes) {
        String publishTarget = routeIdByKeywords(routes, null, "publish", "发布");
        if (publishTarget != null && !publishTarget.isBlank()) {
            return "viewer ? go('" + publishTarget + "') : (authMode = 'login', authOpen = true)";
        }
        return "viewer ? openComposer('post', hash) : (authMode = 'login', authOpen = true)";
    }

    private String shellUtilityButton(String label, String clickExpr, String actionName) {
        return "<button data-lingnow-action=\"" + actionName + "\" @click=\"" + clickExpr + "\" class=\"rounded-full border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-700 transition hover:border-slate-300 hover:bg-slate-50\">" + escapeHtml(label) + "</button>";
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
        boolean discoverRoute = pageSpec != null && pageSpec.getRoute() != null && pageSpec.getRoute().toLowerCase(Locale.ROOT).contains("discover");

        String recommendTitle = discoverRoute ? (zh ? "发现内容" : "Discover looks") : (zh ? "推荐内容" : "Recommended looks");
        String recommendSubtitle = discoverRoute
                ? (zh ? "按风格、场景、品牌和热度聚合当前值得继续浏览的穿搭内容。" : "Browse looks by style, occasion, brand, and momentum.")
                : (zh ? "围绕 OOTD、单品拆解、场景穿搭和身材参考持续更新内容流。" : "Continuously updated around OOTD, pieces, occasions, and fit references.");
        String hotTopicTitle = discoverRoute ? (zh ? "发现专题" : "Discover topics") : (zh ? "穿搭话题" : "Style topics");
        String hotTopicHint = discoverRoute
                ? (zh ? "从热榜、专题和趋势里快速进入感兴趣的穿搭方向。" : "Jump into style directions through trends, topics, and editorial picks.")
                : (zh ? "当前最值得继续逛和收藏的穿搭方向。" : "The style directions worth browsing and saving now.");

        String html = """
                <div x-show="hash === '#__ID__'" class="animate-fade-in pb-8 space-y-6">
                  <section class="grid gap-6 xl:grid-cols-[minmax(0,1fr)_300px]">
                    <div class="space-y-4">
                      <div class="flex items-end justify-between gap-4">
                        <div><h2 class="text-2xl font-black text-slate-900">__RECOMMEND_TITLE__</h2><p class="mt-1 text-sm text-slate-500">__RECOMMEND_SUBTITLE__</p></div>
                        <div class="flex flex-wrap gap-3 text-sm">
                          <button @click="activeSignal = activeSignal === 'saved' ? 'all' : 'saved'" :class="activeSignal === 'saved' ? 'bg-slate-900 text-white shadow-lg shadow-slate-200' : 'bg-white text-slate-700'" class="rounded-full px-4 py-2 font-semibold shadow-sm ring-1 ring-slate-200 transition-all">__SIGNAL_ONE__</button>
                          <button @click="activeSignal = activeSignal === 'hot' ? 'all' : 'hot'" :class="activeSignal === 'hot' ? 'bg-slate-900 text-white shadow-lg shadow-slate-200' : 'bg-white text-slate-700'" class="rounded-full px-4 py-2 font-semibold shadow-sm ring-1 ring-slate-200 transition-all">__SIGNAL_TWO__</button>
                          <button @click="activeSignal = activeSignal === 'media' ? 'all' : 'media'" :class="activeSignal === 'media' ? 'bg-slate-900 text-white shadow-lg shadow-slate-200' : 'bg-white text-slate-700'" class="rounded-full px-4 py-2 font-semibold shadow-sm ring-1 ring-slate-200 transition-all">__SIGNAL_THREE__</button>
                        </div>
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
                                <button @click="handleLooseAction('__FOLLOW_LABEL__')" data-lingnow-action="feed-follow" class="ml-auto rounded-full bg-__ACCENT__/10 px-3 py-1 text-[10px] font-bold text-__ACCENT__">__FOLLOW_LABEL__</button>
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
                    <aside class="space-y-4">
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
                .replace("__SIGNAL_ONE__", zh ? profile.signalOneZh() : profile.signalOneEn())
                .replace("__SIGNAL_TWO__", zh ? profile.signalTwoZh() : profile.signalTwoEn())
                .replace("__SIGNAL_THREE__", zh ? profile.signalThreeZh() : profile.signalThreeEn())
                .replace("__RECOMMEND_TITLE__", recommendTitle)
                .replace("__RECOMMEND_SUBTITLE__", recommendSubtitle)
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
                .replace("__HOT_TOPIC_TITLE__", hotTopicTitle)
                .replace("__HOT_TOPIC_HINT__", hotTopicHint)
                .replace("__HOT_TOPICS__", hotTopics);
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

        if (containsAny(intent, "宠物", "萌宠", "猫", "狗", "pet", "animal")) {
            layout = ProjectManifest.LayoutRhythm.WATERFALL;
            return new ShapeSurfaceProfile(
                    layout,
                    vibeColor,
                    List.of("推荐", "猫咪", "狗狗", "领养", "护理"),
                    List.of("For you", "Cats", "Dogs", "Adoption", "Care"),
                    List.of("萌宠日常", "领养故事", "护理经验", "宠物摄影"),
                    List.of("Pet life", "Adoption stories", "Care tips", "Pet photos"),
                    "宠物社交社区", "Pet social community",
                    "今天想云吸的毛孩子", "Pets worth melting over today",
                    "先看宠物照片、互动热度和养宠经验，再进入详情评论或收藏。",
                    "Start from pet photos, interaction heat, and care tips before opening details, commenting, or saving.",
                    "萌宠发现", "Pet discoveries",
                    "围绕宠物照片、主人故事、评论互动和收藏沉淀组织内容流。",
                    "Organize the feed around pet photos, owner stories, comments, and saved memories.",
                    "高收藏", "Most saved",
                    "评论热度", "Comment heat",
                    "照片/故事", "Photo/story",
                    "热门话题", "Hot topics",
                    "当前最活跃的宠物话题和养宠线索", "The most active pet topics and care signals",
                    "宠物主人", "Pet owner",
                    "萌宠社区", "Pet community",
                    "宠物内容", "Pet post",
                    "一条值得继续互动的萌宠动态", "A pet post worth opening",
                    "萌宠日常", "Pet life"
            );
        }

        if (forceWaterfall && containsAny(intent, "穿搭", "搭配", "ootd", "lookbook", "时尚", "单品", "种草")) {
            return new ShapeSurfaceProfile(
                    ProjectManifest.LayoutRhythm.WATERFALL,
                    vibeColor,
                    List.of("推荐", "通勤", "约会", "旅行", "身材参考"),
                    List.of("For you", "Workwear", "Date", "Travel", "Fit guide"),
                    List.of("今日穿搭", "高收藏单品", "通勤公式", "约会灵感"),
                    List.of("OOTD", "Most saved", "Workwear formula", "Date ideas"),
                    "穿搭分享社区", "Style-sharing community",
                    "今天值得继续逛的穿搭内容", "Looks worth opening today",
                    "先看单品搭配、场景标签和收藏热度，再决定进入详情、收藏或关注作者。",
                    "Start from outfit pairing, occasion tags, and save heat before opening details, saving, or following creators.",
                    "推荐瀑布流", "Recommended looks",
                    "围绕 OOTD、单品拆解、场景穿搭和身材参考持续更新穿搭内容流。",
                    "Continuously update around OOTD, single-item styling, occasion looks, and body-type references.",
                    "高收藏", "Most saved",
                    "热议", "Trending",
                    "图文/视频", "Photo/Video",
                    "穿搭话题", "Style topics",
                    "当前最值得继续逛和收藏的穿搭方向", "The style directions worth browsing and saving now",
                    "穿搭作者", "Style creator",
                    "穿搭社区", "Style community",
                    "穿搭内容", "Outfit post",
                    "一条值得继续点开的穿搭笔记", "A style post worth opening",
                    "今日穿搭", "OOTD"
            );
        }

        if (!forceWaterfall && containsAny(intent, "共读", "精读", "读书", "阅读", "笔记", "划线", "学习进度", "book club", "reading", "note")) {
            layout = ProjectManifest.LayoutRhythm.LIST;
            return new ShapeSurfaceProfile(
                    layout,
                    vibeColor,
                    List.of("推荐", "本周共读", "笔记", "讨论", "进度"),
                    List.of("For you", "This week", "Notes", "Discussion", "Progress"),
                    List.of("本周章节", "高亮笔记", "热门讨论", "学习进度"),
                    List.of("Weekly chapters", "Highlights", "Hot discussions", "Progress"),
                    "共读笔记社区", "Reading notes community",
                    "今天值得继续精读的内容", "Reading worth continuing today",
                    "先看章节、笔记质量、讨论热度和学习进度，再收藏或回复。",
                    "Start from chapters, note quality, discussion heat, and progress before saving or replying.",
                    "共读精选", "Reading picks",
                    "围绕文章阅读、划线笔记、收藏、讨论回复和学习进度组织阅读流。",
                    "Organize the flow around reading, highlights, saves, replies, and learning progress.",
                    "收藏笔记", "Saved notes",
                    "讨论回复", "Replies",
                    "学习进度", "Progress",
                    "共读主题", "Reading themes",
                    "当前小组最值得继续阅读和讨论的内容", "What the group should keep reading and discussing",
                    "共读成员", "Reader",
                    "精读会", "Reading club",
                    "阅读笔记", "Reading note",
                    "一条值得继续标注的笔记", "A note worth annotating",
                    "高亮笔记", "Highlighted note"
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
                  <div class="pointer-events-none mb-6 flex items-center gap-2 px-3 py-1.5 w-fit bg-__ACCENT__/90 backdrop-blur shadow-lg border border-__ACCENT__/30 rounded-full">
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
                - Close button MUST: @click="closeDetail()".
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
            return buildFallbackDetailModal(lang, homeRouteId);
        }
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

    private String buildFallbackDetailModal(String lang, String homeRouteId) {
        boolean zh = !"EN".equalsIgnoreCase(lang);
        return """
                <div class="relative bg-white rounded-3xl p-8 max-w-4xl mx-auto shadow-2xl">
                  <button @click="closeDetail()" data-lingnow-action="close-detail" class="absolute top-4 right-4 text-slate-400 hover:text-slate-700 text-2xl">&times;</button>
                  <div class="overflow-hidden rounded-2xl bg-slate-100">
                    <img :src="selectedItem.cover || selectedItem.image || selectedItem.thumbUrl || 'https://images.unsplash.com/photo-1529139574466-a303027c1d8b?q=80&w=1200'" class="h-80 w-full object-cover" />
                  </div>
                  <div class="mt-6 flex items-center gap-4">
                    <img :src="selectedItem.avatar || selectedItem.authorAvatar || 'https://ui-avatars.com/api/?name=LingNow&background=fef08a&color=111827'" class="h-12 w-12 rounded-full object-cover" />
                    <div class="min-w-0">
                      <div class="truncate text-sm font-black text-slate-900" x-text="selectedItem.author || selectedItem.creator || selectedItem.作者 || 'LingNow'"></div>
                      <div class="mt-1 text-xs text-slate-500"><span x-text="selectedItem.time || selectedItem.publishTime || '%s'"></span><span class="mx-1">·</span><span x-text="selectedItem.location || selectedItem.category || '%s'"></span></div>
                    </div>
                    <button class="ml-auto rounded-full bg-rose-50 px-4 py-2 text-xs font-black text-rose-600">__FOLLOW__</button>
                  </div>
                  <h2 class="mt-3 text-3xl font-black text-slate-900" x-text="selectedItem.title || selectedItem.标题 || '%s'"></h2>
                  <p class="mt-4 text-slate-700 leading-relaxed" x-text="selectedItem.content || selectedItem.内容 || selectedItem.description || ''"></p>
                  <div class="mt-6 flex flex-wrap gap-2">
                    <template x-for="tag in ((selectedItem.tags && selectedItem.tags.length) ? selectedItem.tags : ['穿搭', 'OOTD'])" :key="tag">
                      <span class="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-600" x-text="'#' + tag"></span>
                    </template>
                  </div>
                  <div class="mt-6 grid grid-cols-3 gap-3 rounded-2xl bg-slate-50 p-4 text-sm text-slate-500">
                    <div><div class="font-semibold text-slate-900" x-text="selectedItem.likes || selectedItem.likeCount || '0'"></div><div>%s</div></div>
                    <div><div class="font-semibold text-slate-900" x-text="selectedItem.collects || selectedItem.saves || '0'"></div><div>%s</div></div>
                    <div><div class="font-semibold text-slate-900" x-text="selectedItem.comments || selectedItem.commentCount || '0'"></div><div>%s</div></div>
                  </div>
                  <section class="mt-8 rounded-2xl border border-slate-200 p-5">
                    <div class="flex items-center justify-between">
                      <h3 class="text-lg font-black text-slate-900">__COMMENTS__</h3>
                      <span class="text-xs text-slate-500" x-text="detailComments.length + ' __ITEMS__'"></span>
                    </div>
                    <div class="mt-4 space-y-3">
                      <template x-for="comment in detailComments" :key="comment.id">
                        <article class="rounded-2xl bg-slate-50 p-4">
                          <div class="flex items-center justify-between gap-3">
                            <div class="text-sm font-black text-slate-900" x-text="comment.author"></div>
                            <button @click="startReply(comment)" class="text-xs font-semibold text-rose-500">__REPLY__</button>
                          </div>
                          <p class="mt-2 text-sm leading-6 text-slate-600" x-text="comment.content"></p>
                        </article>
                      </template>
                    </div>
                    <div class="mt-4 rounded-2xl border border-slate-200 bg-white p-4">
                      <textarea x-model="draftComment" class="h-24 w-full resize-none bg-transparent text-sm outline-none" placeholder="__COMMENT_PLACEHOLDER__"></textarea>
                      <div class="mt-3 flex justify-end">
                        <button @click="submitComment()" data-lingnow-action="submit-comment" class="shell-primary-button rounded-full px-4 py-2 text-sm font-black text-white">__SEND__</button>
                      </div>
                    </div>
                  </section>
                </div>
                """.formatted(
                zh ? "刚刚" : "just now",
                        zh ? "穿搭社区" : "Style community",
                zh ? "详情" : "Detail",
                zh ? "点赞" : "Likes",
                zh ? "收藏" : "Saves",
                        zh ? "评论" : "Comments")
                .replace("__FOLLOW__", zh ? "关注作者" : "Follow")
                .replace("__COMMENTS__", zh ? "评论区" : "Comments")
                .replace("__ITEMS__", zh ? "条评论" : "comments")
                .replace("__REPLY__", zh ? "回复" : "Reply")
                .replace("__COMMENT_PLACEHOLDER__", zh ? "写下你的评论或回复..." : "Write a comment...")
                .replace("__SEND__", zh ? "发送评论" : "Send");
    }

    private String buildWorkflowDetailModal(ProjectManifest manifest, String lang, String homeRouteId) {
        boolean zh = !"EN".equalsIgnoreCase(lang);
        GenericWorkflowProfile profile = buildGenericWorkflowProfile(manifest, zh);
        String sectionTitle = escapeJsSingleQuoted(profile.sectionTitle());
        String sectionDescription = escapeJsSingleQuoted(profile.sectionDescription());
        String advancedStage = escapeJsSingleQuoted(profile.advancedStage());
        String selectedEmpty = escapeJsSingleQuoted(zh ? "当前对象" : "Current record");
        String orderEmpty = escapeJsSingleQuoted(zh ? "待推进" : "Waiting");
        String toastEmpty = escapeJsSingleQuoted(zh ? "等待反馈" : "Waiting");
        String slotFallback = escapeJsSingleQuoted(profile.slotDay() + " " + profile.slotTime());
        String slotDay = escapeJsSingleQuoted(profile.slotDay());
        String slotTime = escapeJsSingleQuoted(profile.slotTime());
        String tertiaryId = escapeJsSingleQuoted(profile.codePrefix() + "-01");
        return """
                <div data-lingnow-flow="__FLOW__-detail" class="relative overflow-hidden rounded-3xl bg-white shadow-2xl">
                  <button @click="closeDetail()" data-lingnow-action="close-detail" class="absolute right-5 top-5 z-10 grid h-10 w-10 place-items-center rounded-full bg-white/90 text-2xl leading-none text-slate-400 shadow-lg hover:text-slate-700">&times;</button>
                  <div class="grid lg:grid-cols-[minmax(0,1.05fr)_380px]">
                    <div class="bg-slate-100">
                      <img :src="selectedItem.cover || selectedItem.image || selectedItem.thumbUrl || 'https://images.unsplash.com/photo-1552664730-d307ca884978?q=80&w=1200'" class="h-full min-h-[420px] w-full object-cover" />
                    </div>
                    <aside class="flex flex-col p-7">
                      <div class="flex items-center gap-3">
                        <img :src="selectedItem.avatar || selectedItem.authorAvatar || 'https://ui-avatars.com/api/?name=LingNow&background=eff6ff&color=1e293b'" class="h-12 w-12 rounded-full object-cover" />
                        <div class="min-w-0">
                          <div class="truncate text-sm font-black text-slate-900" x-text="selectedItem.author || selectedItem.owner || 'LingNow'"></div>
                          <div class="mt-1 truncate text-xs text-slate-500" x-text="selectedItem.category || '__SURFACE__'"></div>
                        </div>
                        <span class="ml-auto rounded-full bg-indigo-50 px-3 py-1 text-xs font-bold text-indigo-600" x-text="selectedItem.status || '__STATUS__'"></span>
                      </div>
                      <h2 class="mt-6 text-3xl font-black leading-tight text-slate-900" x-text="selectedItem.title || '__TITLE__'"></h2>
                      <p class="mt-4 text-sm leading-7 text-slate-600" x-text="selectedItem.description || selectedItem.content || '__DESC__'"></p>
                      <div class="mt-5 flex flex-wrap gap-2">
                        <template x-for="tag in ((Array.isArray(selectedItem.tags) && selectedItem.tags.length ? selectedItem.tags : [selectedItem.category || '__TAG__']))" :key="tag">
                          <span class="rounded-full bg-indigo-50 px-3 py-1 text-xs font-bold text-indigo-600" x-text="'#' + tag"></span>
                        </template>
                      </div>
                      <div class="mt-6 grid grid-cols-3 gap-3 rounded-3xl bg-slate-50 p-4 text-center text-sm">
                        <div><div class="text-lg font-black text-slate-900" x-text="selectedItem.metric || '__METRIC__'"></div><div class="mt-1 text-xs text-slate-500">__SIGNAL_A__</div></div>
                        <div><div class="text-lg font-black text-slate-900" x-text="activeOrder ? activeOrder.stage : (selectedItem.status || '__STATUS__')"></div><div class="mt-1 text-xs text-slate-500">__SIGNAL_B__</div></div>
                        <div><div class="text-lg font-black text-slate-900" x-text="selectedSlot ? `${selectedSlot.day} ${selectedSlot.time}` : '__SLOT__'"></div><div class="mt-1 text-xs text-slate-500">__SIGNAL_C__</div></div>
                      </div>
                      <div class="mt-6 rounded-3xl border border-slate-200 p-4">
                        <div class="text-sm font-black text-slate-900">__FLOW_TITLE__</div>
                        <div class="mt-3 grid grid-cols-3 gap-2 text-center text-[11px] font-bold text-slate-500">
                          <div class="rounded-2xl bg-slate-50 p-3">__STEP_A__</div>
                          <div class="rounded-2xl bg-slate-50 p-3">__STEP_B__</div>
                          <div class="rounded-2xl bg-slate-50 p-3">__STEP_C__</div>
                        </div>
                      </div>
                      <div class="mt-4 rounded-3xl border border-emerald-100 bg-emerald-50 p-4 text-emerald-900">
                        <div class="text-xs font-black uppercase tracking-[0.25em] text-emerald-600">__STATE_TITLE__</div>
                        <div class="mt-3 space-y-2 text-sm">
                          <div class="rounded-2xl bg-white/70 p-3"><span class="font-black">__SELECTED_LABEL__：</span><span x-text="selectedItem.title || '__SELECTED_EMPTY__'"></span></div>
                          <div class="rounded-2xl bg-white/70 p-3"><span class="font-black">__ORDER_LABEL__：</span><span x-text="activeOrder ? `${activeOrder.id || selectedItem.id} · ${activeOrder.stage}` : '__ORDER_EMPTY__'"></span></div>
                          <div class="rounded-2xl bg-white/70 p-3"><span class="font-black">Toast：</span><span x-text="toast || '__TOAST_EMPTY__'"></span></div>
                        </div>
                      </div>
                      <div class="mt-auto grid gap-3 pt-6 sm:grid-cols-2">
                        <button @click="pickSlot({day:'__SLOT_DAY__', time:'__SLOT_TIME__', type:(selectedItem.title || '__TITLE__'), owner:(selectedItem.author || selectedItem.owner || 'LingNow')}); showToast('__TOAST_SELECT__')" data-lingnow-action="detail-primary" class="rounded-2xl bg-slate-950 px-5 py-3 text-sm font-black text-white shadow-lg shadow-slate-200">__PRIMARY_ACTION__</button>
                        <button @click="confirmBooking()" data-lingnow-action="detail-secondary" class="rounded-2xl border border-slate-200 px-5 py-3 text-sm font-black text-slate-700 hover:bg-slate-50">__SECONDARY_ACTION__</button>
                        <button @click="advanceOrder({id:(selectedItem.id || '__TERTIARY_ID__'), title:(selectedItem.title || '__TITLE__'), stage:'__ADVANCED_STAGE__'})" data-lingnow-action="detail-tertiary" class="rounded-2xl border border-indigo-100 bg-indigo-50 px-5 py-3 text-sm font-black text-indigo-700 sm:col-span-2">__TERTIARY_ACTION__</button>
                      </div>
                    </aside>
                  </div>
                </div>
                """
                .replace("__FLOW__", escapeHtml(profile.flowKey()))
                .replace("__SURFACE__", escapeJsSingleQuoted(profile.surfaceLabel()))
                .replace("__STATUS__", advancedStage)
                .replace("__TITLE__", sectionTitle)
                .replace("__DESC__", sectionDescription)
                .replace("__TAG__", escapeJsSingleQuoted(profile.sectionTitle()))
                .replace("__METRIC__", escapeJsSingleQuoted(profile.metricA()))
                .replace("__SIGNAL_A__", escapeHtml(profile.signalA()))
                .replace("__SIGNAL_B__", escapeHtml(profile.orderLabel()))
                .replace("__SIGNAL_C__", escapeHtml(profile.selectedLabel()))
                .replace("__SLOT__", slotFallback)
                .replace("__FLOW_TITLE__", escapeHtml(profile.flowTitle()))
                .replace("__STEP_A__", escapeHtml(profile.stepA()))
                .replace("__STEP_B__", escapeHtml(profile.stepB()))
                .replace("__STEP_C__", escapeHtml(profile.stepC()))
                .replace("__STATE_TITLE__", zh ? "原型状态" : "Prototype state")
                .replace("__SELECTED_LABEL__", escapeHtml(profile.selectedLabel()))
                .replace("__SELECTED_EMPTY__", selectedEmpty)
                .replace("__ORDER_LABEL__", escapeHtml(profile.orderLabel()))
                .replace("__ORDER_EMPTY__", orderEmpty)
                .replace("__TOAST_EMPTY__", toastEmpty)
                .replace("__SLOT_DAY__", slotDay)
                .replace("__SLOT_TIME__", slotTime)
                .replace("__TOAST_SELECT__", escapeJsSingleQuoted(profile.toastSelect()))
                .replace("__PRIMARY_ACTION__", escapeHtml(profile.primaryAction()))
                .replace("__SECONDARY_ACTION__", escapeHtml(profile.secondaryAction()))
                .replace("__TERTIARY_ID__", tertiaryId)
                .replace("__ADVANCED_STAGE__", advancedStage)
                .replace("__TERTIARY_ACTION__", escapeHtml(profile.tertiaryAction()));
    }

    private String buildPhotographyDetailModal(String lang, String homeRouteId) {
        boolean zh = !"EN".equalsIgnoreCase(lang);
        return """
                <div data-lingnow-flow="photography-detail" class="relative overflow-hidden rounded-3xl bg-white shadow-2xl">
                  <button @click="closeDetail()" data-lingnow-action="close-detail" class="absolute right-5 top-5 z-10 grid h-10 w-10 place-items-center rounded-full bg-white/90 text-2xl leading-none text-slate-400 shadow-lg hover:text-slate-700">&times;</button>
                  <div class="grid lg:grid-cols-[minmax(0,1.05fr)_360px]">
                    <div class="bg-slate-100">
                      <img :src="selectedItem.cover || selectedItem.image || selectedItem.thumbUrl" class="h-full min-h-[420px] w-full object-cover" />
                    </div>
                    <aside class="flex flex-col p-7">
                      <div class="flex items-center gap-3">
                        <img :src="selectedItem.avatar || 'https://ui-avatars.com/api/?name=LN&background=fef08a&color=111827'" class="h-12 w-12 rounded-full object-cover" />
                        <div class="min-w-0">
                          <div class="truncate text-sm font-black text-slate-900" x-text="selectedItem.author || selectedItem.name || 'LingNow 摄影师'"></div>
                          <div class="mt-1 truncate text-xs text-slate-500" x-text="selectedItem.location || selectedItem.city || '__LOCATION__'"></div>
                        </div>
                      </div>
                      <h2 class="mt-6 text-3xl font-black leading-tight text-slate-900" x-text="selectedItem.title || selectedItem.style || '__TITLE__'"></h2>
                      <p class="mt-4 text-sm leading-7 text-slate-600" x-text="selectedItem.content || selectedItem.description || '__DESC__'"></p>
                      <div class="mt-5 flex flex-wrap gap-2">
                        <template x-for="tag in (selectedItem.tags || [selectedItem.category || '__TAG__'])" :key="tag">
                          <span class="rounded-full bg-rose-50 px-3 py-1 text-xs font-bold text-rose-600" x-text="'#' + tag"></span>
                        </template>
                      </div>
                      <div class="mt-6 grid grid-cols-3 gap-3 rounded-3xl bg-slate-50 p-4 text-center text-sm">
                        <div><div class="text-lg font-black text-slate-900" x-text="selectedItem.likes || '4.8w'"></div><div class="mt-1 text-xs text-slate-500">__LIKES__</div></div>
                        <div><div class="text-lg font-black text-slate-900" x-text="selectedItem.collects || selectedItem.saves || '2380'"></div><div class="mt-1 text-xs text-slate-500">__SAVES__</div></div>
                        <div><div class="text-lg font-black text-slate-900" x-text="selectedItem.comments || '126'"></div><div class="mt-1 text-xs text-slate-500">__COMMENTS__</div></div>
                      </div>
                      <div class="mt-6 rounded-3xl border border-slate-200 p-4">
                        <div class="text-sm font-black text-slate-900">__FLOW_TITLE__</div>
                        <div class="mt-3 grid grid-cols-3 gap-2 text-center text-[11px] font-bold text-slate-500">
                          <div class="rounded-2xl bg-slate-50 p-3">__FLOW_A__</div>
                          <div class="rounded-2xl bg-slate-50 p-3">__FLOW_B__</div>
                          <div class="rounded-2xl bg-slate-50 p-3">__FLOW_C__</div>
                        </div>
                      </div>
                      <div class="mt-auto grid gap-3 pt-6 sm:grid-cols-2">
                        <button @click="startInquiry(selectedItem)" data-lingnow-action="detail-inquiry" class="rounded-2xl bg-rose-500 px-5 py-3 text-sm font-black text-white shadow-lg shadow-rose-100">__INQUIRE__</button>
                        <button @click="closeDetail(); go('#pg3')" data-lingnow-action="detail-booking" class="rounded-2xl border border-slate-200 px-5 py-3 text-sm font-black text-slate-700 hover:bg-slate-50">__BOOK__</button>
                      </div>
                    </aside>
                  </div>
                </div>
                """
                .replace("__LOCATION__", zh ? "城市与档期待确认" : "City and slot to confirm")
                .replace("__TITLE__", zh ? "摄影服务详情" : "Photography service detail")
                .replace("__DESC__", zh ? "查看作品风格、可服务城市、档期与报价线索，再进入询价或预约。" : "Review style, city, availability, and quote signals before inquiry or booking.")
                .replace("__TAG__", zh ? "摄影" : "Photography")
                .replace("__LIKES__", zh ? "喜欢" : "Likes")
                .replace("__SAVES__", zh ? "收藏" : "Saves")
                .replace("__COMMENTS__", zh ? "咨询" : "Inquiries")
                .replace("__FLOW_TITLE__", zh ? "下一步转化路径" : "Next conversion path")
                .replace("__FLOW_A__", zh ? "看作品" : "Portfolio")
                .replace("__FLOW_B__", zh ? "选档期" : "Slot")
                .replace("__FLOW_C__", zh ? "提交询价" : "Inquiry")
                .replace("__INQUIRE__", zh ? "发起询价" : "Start inquiry")
                .replace("__BOOK__", zh ? "查看档期" : "View slots");
    }

    private enum ShellFlavor {
        DEFAULT,
        CONTENT,
        PHOTOGRAPHY,
        PIPELINE,
        OPS,
        CLINIC,
        FITNESS,
        VEHICLE,
        PROPERTY,
        EVENT,
        LEARNING,
        COMMERCE,
        SERVICE
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

    private record GenericWorkflowProfile(
            String flowKey,
            String surfaceLabel,
            String primaryAction,
            String secondaryAction,
            String tertiaryAction,
            String slotDay,
            String slotTime,
            String toastSelect,
            String advancedStage,
            String statusTitle,
            String metricA,
            String metricB,
            String metricC,
            String signalA,
            String signalB,
            String signalC,
            String statusDescription,
            String sectionTitle,
            String sectionDescription,
            String flowTitle,
            String stepA,
            String stepB,
            String stepC,
            String selectedLabel,
            String orderLabel,
            String codePrefix,
            List<Map<String, Object>> items
    ) {
    }

    private record ShellCopy(
            String searchPlaceholder,
            String publishLabel,
            String postTitle,
            String postPlaceholder,
            String postSubmitLabel,
            String publishKind
    ) {
    }

    private record ShellTheme(
            String accentToken,
            String accentHex,
            String accentStrongHex,
            String accentSoftHex,
            String accentSoftTextHex,
            String accentRingHex,
            String accentShadow
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
