package cc.lingnow.service;

import cc.lingnow.model.ProjectManifest;
import cc.lingnow.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Normalizes cross-agent contracts so downstream stages consume a stable schema
 * instead of inferring semantics from loosely structured strings.
 */
@Slf4j
@Service
public class ManifestContractValidator {

    public void normalize(ProjectManifest manifest) {
        if (manifest == null) {
            return;
        }

        if (manifest.getMetaData() == null) {
            manifest.setMetaData(new HashMap<>());
        }

        List<ProjectManifest.PageSpec> pages = normalizePages(manifest.getPages());
        manifest.setPages(pages);

        List<ProjectManifest.TaskFlow> taskFlows = normalizeTaskFlows(manifest.getTaskFlows(), manifest.getMetaData());
        manifest.setTaskFlows(taskFlows);

        String normalizedMindMap = normalizeMindMap(manifest.getMindMap(), pages);
        manifest.setMindMap(normalizedMindMap);

        ProjectManifest.DesignContract designContract = buildDesignContract(manifest, pages);
        manifest.setDesignContract(designContract);

        manifest.getMetaData().put("shell_pattern", designContract.getShellPattern());
        manifest.getMetaData().put("content_mode", designContract.getContentMode());
        manifest.getMetaData().put("shape_primary_goal", safeName(designContract.getPrimaryGoal()));
        manifest.getMetaData().put("shape_content_unit", safeName(designContract.getContentUnit()));
        manifest.getMetaData().put("shape_consumption_mode", safeName(designContract.getConsumptionMode()));
        manifest.getMetaData().put("shape_media_weight", safeName(designContract.getMediaWeight()));
        manifest.getMetaData().put("shape_layout_rhythm", safeName(designContract.getLayoutRhythm()));
        manifest.getMetaData().put("shape_content_density", safeName(designContract.getContentDensity()));
        manifest.getMetaData().put("shape_navigation_mode", safeName(designContract.getNavigationMode()));
        manifest.getMetaData().put("shape_main_loop", safeName(designContract.getMainLoop()));
        manifest.getMetaData().put("shape_ui_tone", safeName(designContract.getUiTone()));
        manifest.getMetaData().put("shape_signal_priority", designContract.getSignalPriority() == null ? "[]" : designContract.getSignalPriority().toString());
        manifest.getMetaData().put("taskFlows", JsonUtils.toJson(taskFlows));
        manifest.getMetaData().putIfAbsent("design_ready", "false");

        log.info("[Contract] Normalized pages={}, taskFlows={}, shellPattern={}, contentMode={}",
                pages.size(),
                taskFlows.size(),
                designContract.getShellPattern(),
                designContract.getContentMode());
    }

    private List<ProjectManifest.PageSpec> normalizePages(List<ProjectManifest.PageSpec> pages) {
        List<ProjectManifest.PageSpec> safePages = pages == null ? new ArrayList<>() : new ArrayList<>(pages);
        List<ProjectManifest.PageSpec> normalized = safePages.stream()
                .filter(page -> page != null && isNotBlank(page.getRoute()))
                .map(page -> ProjectManifest.PageSpec.builder()
                        .route(page.getRoute())
                        .description(isNotBlank(page.getDescription()) ? page.getDescription() : page.getRoute())
                        .navType(normalizeNavType(page.getNavType()))
                        .navRole(normalizeNavRole(page.getNavRole()))
                        .components(page.getComponents() == null ? List.of() : page.getComponents().stream()
                                .filter(this::isNotBlank)
                                .distinct()
                                .toList())
                        .build())
                .collect(Collectors.toCollection(ArrayList::new));

        if (normalized.isEmpty()) {
            return normalized;
        }

        boolean hasPrimary = normalized.stream().anyMatch(page -> "PRIMARY".equals(page.getNavRole()));
        if (!hasPrimary) {
            ProjectManifest.PageSpec first = normalized.get(0);
            normalized.set(0, ProjectManifest.PageSpec.builder()
                    .route(first.getRoute())
                    .description(first.getDescription())
                    .navType(first.getNavType())
                    .navRole("PRIMARY")
                    .components(first.getComponents())
                    .build());
        }

        boolean hasOverlay = normalized.stream().anyMatch(page -> "OVERLAY".equals(page.getNavRole()));
        if (!hasOverlay) {
            normalized.add(ProjectManifest.PageSpec.builder()
                    .route("/detail")
                    .description("Detail overlay view")
                    .navType("LEAF_DETAIL")
                    .navRole("OVERLAY")
                    .components(List.of("Hero Media", "Meta Panel", "Comments", "Composer"))
                    .build());
        }

        return normalized;
    }

    private List<ProjectManifest.TaskFlow> normalizeTaskFlows(List<ProjectManifest.TaskFlow> taskFlows, Map<String, String> metaData) {
        List<ProjectManifest.TaskFlow> source = taskFlows;
        if ((source == null || source.isEmpty()) && metaData != null) {
            source = JsonUtils.fromJson(metaData.get("taskFlows"), new TypeReference<>() {
            });
        }
        if (source == null) {
            return new ArrayList<>();
        }

        return source.stream()
                .filter(flow -> flow != null && isNotBlank(flow.getDescription()))
                .map(flow -> ProjectManifest.TaskFlow.builder()
                        .id(isNotBlank(flow.getId()) ? flow.getId() : "flow_" + Math.abs(flow.getDescription().hashCode()))
                        .description(flow.getDescription())
                        .steps(flow.getSteps() == null ? List.of() : flow.getSteps().stream().filter(this::isNotBlank).toList())
                        .build())
                .toList();
    }

    private String normalizeMindMap(String mindMap, List<ProjectManifest.PageSpec> pages) {
        if (isNotBlank(mindMap)) {
            return mindMap;
        }

        return pages.stream()
                .filter(page -> "PRIMARY".equals(page.getNavRole()))
                .map(page -> presentableRouteName(page.getRoute()))
                .collect(Collectors.joining("\n"));
    }

    private ProjectManifest.DesignContract buildDesignContract(ProjectManifest manifest, List<ProjectManifest.PageSpec> pages) {
        String shellPattern = resolveShellPattern(manifest.getUxStrategy(), manifest.getArchetype(), manifest.getUserIntent());
        ShapeProfile profile = detectDetailedShapeProfile(manifest, pages);
        boolean contentCommunity = profile.isContentCommunity();
        String contentMode = contentCommunity ? "CONTENT_FIRST" : "SIDEBAR_FIRST";
        String normalizedShellPattern = contentCommunity ? shellPattern : "SIDEBAR_PRIMARY_NAV";

        long primaryCount = pages.stream().filter(page -> "PRIMARY".equals(page.getNavRole())).count();
        boolean requiresComposer = pages.stream().anyMatch(page -> "UTILITY".equals(page.getNavRole()))
                || containsAny(manifest.getUserIntent(), "发布", "社区", "内容", "社交", "post", "community", "social", "feed");
        boolean requiresSearch = containsAny(manifest.getUserIntent(), "搜索", "发现", "explore", "search", "feed")
                || pages.stream().anyMatch(page -> containsAny(page.getRoute(), "explore", "discover", "search"));
        boolean requiresDetailOverlay = pages.stream().anyMatch(page -> "OVERLAY".equals(page.getNavRole()));

        return ProjectManifest.DesignContract.builder()
                .primaryGoal(profile.primaryGoal())
                .contentUnit(profile.contentUnit())
                .consumptionMode(profile.consumptionMode())
                .mediaWeight(profile.mediaWeight())
                .layoutRhythm(profile.layoutRhythm())
                .contentDensity(profile.contentDensity())
                .signalPriority(profile.signalPriority())
                .navigationMode(profile.navigationMode())
                .mainLoop(profile.mainLoop())
                .uiTone(profile.uiTone())
                .shellPattern(normalizedShellPattern)
                .contentMode(contentMode)
                .minPrimarySections((int) Math.max(1, Math.min(primaryCount, 4)))
                .minPrimaryCards(resolveMinPrimaryCards(profile, contentMode))
                .prefersWaterfallFeed(profile.layoutRhythm() == ProjectManifest.LayoutRhythm.WATERFALL)
                .maxAuxRailSections(resolveMaxAuxRailSections(profile))
                .prefersRealMedia(profile.mediaWeight() == ProjectManifest.MediaWeight.VISUAL_HEAVY
                        || profile.mediaWeight() == ProjectManifest.MediaWeight.VIDEO_HEAVY)
                .requiresCategoryTabs(requiresCategoryTabs(profile))
                .requiresInteractiveFiltering(requiresInteractiveFiltering(profile))
                .requiresSearch(requiresSearch)
                .requiresComposer(requiresComposer)
                .requiresDetailOverlay(requiresDetailOverlay)
                .validationNotes(profile.validationNotes())
                .build();
    }

    private ShapeProfile detectDetailedShapeProfile(ProjectManifest manifest, List<ProjectManifest.PageSpec> pages) {
        String intent = safeLower(manifest.getUserIntent());
        String archetype = safeLower(manifest.getArchetype());
        String overview = safeLower(manifest.getOverview());
        String pageContext = pages == null ? "" : pages.stream()
                .map(page -> (page.getRoute() == null ? "" : page.getRoute()) + " " + (page.getDescription() == null ? "" : page.getDescription()))
                .collect(Collectors.joining(" "))
                .toLowerCase(Locale.ROOT);
        String source = String.join(" ", intent, archetype, overview, pageContext).trim();

        boolean likelyCommunity = isContentFirstArchetype(manifest.getArchetype(), manifest.getUserIntent())
                || containsAny(source, "community", "forum", "feed", "social", "circle", "社区", "论坛", "帖子", "问答", "资讯");
        boolean technical = containsAny(source, "技术", "代码", "编程", "开发", "教程", "架构", "算法", "程序员",
                "tech", "code", "coding", "developer", "engineering", "frontend", "backend", "api", "ai", "agent", "blog");
        boolean visualDiscovery = containsAny(source, "小红书", "种草", "穿搭", "灵感", "美妆", "探店", "旅行", "摄影", "生活方式", "收藏",
                "xiaohongshu", "inspiration", "style", "beauty", "travel", "outfit", "lifestyle", "gallery", "lookbook", "save");
        boolean realtimeSocial = containsAny(source, "微博", "热搜", "热点", "动态", "快讯", "实时", "关注",
                "timeline", "trending", "breaking", "realtime", "tweet", "social feed");
        boolean qaKnowledge = containsAny(source, "问答", "提问", "回答", "专家", "知识库", "解决方案",
                "question", "answer", "qa", "knowledge base", "stack overflow");
        boolean discussionForum = containsAny(source, "论坛", "贴吧", "球迷", "版块", "帖子", "回帖", "楼层", "虎扑",
                "thread", "forum", "reply", "comment war", "subforum");
        boolean editorialNews = containsAny(source, "新闻", "资讯", "快报", "创投", "商业", "报道", "栏目", "媒体", "36氪", "财经", "观察",
                "news", "brief", "report", "media", "insight", "36kr", "editorial");
        boolean enterpriseDashboard = containsAny(source, "后台", "看板", "仪表盘", "监控", "报表", "管理系统", "中台",
                "dashboard", "analytics", "admin", "metrics", "monitor", "management", "saas backend");
        boolean corporateSite = containsAny(source, "官网", "品牌", "介绍", "企业", "形象", "展示", "宣传",
                "corporate", "official site", "landing page", "brand", "about us", "company profile");
        boolean commerce = containsAny(source, "商城", "商品", "下单", "购物", "比价", "sku", "订单",
                "shop", "ecommerce", "product list", "buy", "cart");
        boolean education = containsAny(source, "课程", "训练营", "章节", "老师", "习题", "题库", "考试",
                "course", "learn", "lesson", "teacher", "education");

        if (corporateSite && !likelyCommunity && !enterpriseDashboard && !commerce && !education) {
            return new ShapeProfile(false,
                    ProjectManifest.PrimaryGoal.READ,
                    ProjectManifest.ContentUnit.MIXED,
                    ProjectManifest.ConsumptionMode.READ_FIRST,
                    ProjectManifest.MediaWeight.VISUAL_HEAVY,
                    ProjectManifest.LayoutRhythm.EDITORIAL,
                    ProjectManifest.ContentDensity.MEDIUM,
                    List.of(ProjectManifest.SignalPriority.AUTHOR_TRUST),
                    ProjectManifest.NavigationMode.CHANNEL,
                    ProjectManifest.MainLoop.READ_SAVE,
                    ProjectManifest.UiTone.PROFESSIONAL,
                    "Corporate site requires a clean, brand-first layout with visual-heavy editorial sections.");
        }

        if (enterpriseDashboard) {
            return new ShapeProfile(false,
                    ProjectManifest.PrimaryGoal.MONITOR,
                    ProjectManifest.ContentUnit.DASHBOARD,
                    ProjectManifest.ConsumptionMode.READ_FIRST,
                    ProjectManifest.MediaWeight.MIXED,
                    ProjectManifest.LayoutRhythm.DASHBOARD,
                    ProjectManifest.ContentDensity.HIGH,
                    List.of(ProjectManifest.SignalPriority.STATUS, ProjectManifest.SignalPriority.RECENCY),
                    ProjectManifest.NavigationMode.UTILITY_FIRST,
                    ProjectManifest.MainLoop.MONITOR_ACT,
                    ProjectManifest.UiTone.ENTERPRISE,
                    "Dashboard product requires status-first monitoring, dense panels, and utility-led navigation.");
        }

        if (commerce) {
            return new ShapeProfile(false,
                    ProjectManifest.PrimaryGoal.TRANSACT,
                    ProjectManifest.ContentUnit.LISTING,
                    ProjectManifest.ConsumptionMode.DISCOVER_FIRST,
                    ProjectManifest.MediaWeight.MIXED,
                    ProjectManifest.LayoutRhythm.COMPACT_CARD,
                    ProjectManifest.ContentDensity.MEDIUM,
                    List.of(ProjectManifest.SignalPriority.PRICE, ProjectManifest.SignalPriority.HEAT),
                    ProjectManifest.NavigationMode.CATEGORY,
                    ProjectManifest.MainLoop.COMPARE_BUY,
                    ProjectManifest.UiTone.EDITORIAL,
                    "Commerce product requires comparison-friendly cards, category navigation, and price/heat signals.");
        }

        if (education && !technical && !qaKnowledge && !editorialNews) {
            return new ShapeProfile(false,
                    ProjectManifest.PrimaryGoal.LEARN,
                    ProjectManifest.ContentUnit.MIXED,
                    ProjectManifest.ConsumptionMode.READ_FIRST,
                    ProjectManifest.MediaWeight.MIXED,
                    ProjectManifest.LayoutRhythm.LIST,
                    ProjectManifest.ContentDensity.MEDIUM,
                    List.of(ProjectManifest.SignalPriority.PROGRESS, ProjectManifest.SignalPriority.AUTHOR_TRUST),
                    ProjectManifest.NavigationMode.CHANNEL,
                    ProjectManifest.MainLoop.LEARN_CONTINUE,
                    ProjectManifest.UiTone.PROFESSIONAL,
                    "Education product requires structured progression, chapter-aware listing, and teacher/progress signals.");
        }

        if (technical) {
            return new ShapeProfile(likelyCommunity,
                    ProjectManifest.PrimaryGoal.READ,
                    qaKnowledge ? ProjectManifest.ContentUnit.QA : (discussionForum ? ProjectManifest.ContentUnit.THREAD : ProjectManifest.ContentUnit.ARTICLE),
                    ProjectManifest.ConsumptionMode.READ_FIRST,
                    ProjectManifest.MediaWeight.TEXT_HEAVY,
                    discussionForum ? ProjectManifest.LayoutRhythm.THREAD : ProjectManifest.LayoutRhythm.LIST,
                    ProjectManifest.ContentDensity.HIGH,
                    List.of(ProjectManifest.SignalPriority.AUTHOR_TRUST, ProjectManifest.SignalPriority.SAVE_RATE, ProjectManifest.SignalPriority.DISCUSSION),
                    discussionForum ? ProjectManifest.NavigationMode.SUBFORUM : ProjectManifest.NavigationMode.TOPIC_TAB,
                    discussionForum ? ProjectManifest.MainLoop.POST_REPLY : ProjectManifest.MainLoop.READ_SAVE,
                    discussionForum ? ProjectManifest.UiTone.FORUM : ProjectManifest.UiTone.PROFESSIONAL,
                    "Technical content requires dense information, strong author/tag signals, and reading-first card rhythm.");
        }

        if (qaKnowledge) {
            return new ShapeProfile(true,
                    ProjectManifest.PrimaryGoal.READ,
                    ProjectManifest.ContentUnit.QA,
                    ProjectManifest.ConsumptionMode.VERIFY_FIRST,
                    ProjectManifest.MediaWeight.TEXT_HEAVY,
                    ProjectManifest.LayoutRhythm.LIST,
                    ProjectManifest.ContentDensity.HIGH,
                    List.of(ProjectManifest.SignalPriority.AUTHOR_TRUST, ProjectManifest.SignalPriority.DISCUSSION),
                    ProjectManifest.NavigationMode.TOPIC_TAB,
                    ProjectManifest.MainLoop.ASK_ANSWER,
                    ProjectManifest.UiTone.EDITORIAL,
                    "Q&A products should foreground questions, answer credibility, and verification-oriented reading flow.");
        }

        if (discussionForum) {
            return new ShapeProfile(true,
                    ProjectManifest.PrimaryGoal.DISCUSS,
                    ProjectManifest.ContentUnit.THREAD,
                    ProjectManifest.ConsumptionMode.DISCUSS_FIRST,
                    ProjectManifest.MediaWeight.MIXED,
                    ProjectManifest.LayoutRhythm.THREAD,
                    ProjectManifest.ContentDensity.HIGH,
                    List.of(ProjectManifest.SignalPriority.DISCUSSION, ProjectManifest.SignalPriority.RECENCY),
                    ProjectManifest.NavigationMode.SUBFORUM,
                    ProjectManifest.MainLoop.POST_REPLY,
                    ProjectManifest.UiTone.FORUM,
                    "Forum-style communities prioritize thread lists, reply activity, and visible board navigation.");
        }

        if (realtimeSocial) {
            return new ShapeProfile(true,
                    ProjectManifest.PrimaryGoal.DISCOVER,
                    ProjectManifest.ContentUnit.POST,
                    ProjectManifest.ConsumptionMode.REAL_TIME_FIRST,
                    ProjectManifest.MediaWeight.MIXED,
                    ProjectManifest.LayoutRhythm.COMPACT_CARD,
                    ProjectManifest.ContentDensity.HIGH,
                    List.of(ProjectManifest.SignalPriority.RECENCY, ProjectManifest.SignalPriority.HEAT),
                    ProjectManifest.NavigationMode.CHANNEL,
                    ProjectManifest.MainLoop.SCROLL_DISCOVER,
                    ProjectManifest.UiTone.PLAZA,
                    "Realtime social feeds need fast-scan cards, trend signals, and dense interaction controls.");
        }

        if (editorialNews) {
            return new ShapeProfile(false,
                    ProjectManifest.PrimaryGoal.READ,
                    ProjectManifest.ContentUnit.NEWS_STORY,
                    ProjectManifest.ConsumptionMode.READ_FIRST,
                    ProjectManifest.MediaWeight.MIXED,
                    ProjectManifest.LayoutRhythm.EDITORIAL,
                    ProjectManifest.ContentDensity.MEDIUM,
                    List.of(ProjectManifest.SignalPriority.RECENCY, ProjectManifest.SignalPriority.EDITORIAL),
                    ProjectManifest.NavigationMode.CHANNEL,
                    ProjectManifest.MainLoop.READ_SAVE,
                    ProjectManifest.UiTone.EDITORIAL,
                    "Editorial information products need channelized reading flow, headlines, and recency-driven hierarchy.");
        }

        if (visualDiscovery) {
            return new ShapeProfile(true,
                    ProjectManifest.PrimaryGoal.DISCOVER,
                    ProjectManifest.ContentUnit.POST,
                    ProjectManifest.ConsumptionMode.DISCOVER_FIRST,
                    ProjectManifest.MediaWeight.VISUAL_HEAVY,
                    ProjectManifest.LayoutRhythm.WATERFALL,
                    ProjectManifest.ContentDensity.MEDIUM,
                    List.of(ProjectManifest.SignalPriority.SAVE_RATE, ProjectManifest.SignalPriority.HEAT),
                    ProjectManifest.NavigationMode.TOPIC_TAB,
                    ProjectManifest.MainLoop.SCROLL_DISCOVER,
                    ProjectManifest.UiTone.LIVELY,
                    "Visual discovery products benefit from image-led cards, save/heat signals, and waterfall browsing rhythm.");
        }

        if (likelyCommunity) {
            return new ShapeProfile(true,
                    ProjectManifest.PrimaryGoal.DISCOVER,
                    ProjectManifest.ContentUnit.MIXED,
                    ProjectManifest.ConsumptionMode.DISCOVER_FIRST,
                    ProjectManifest.MediaWeight.MIXED,
                    ProjectManifest.LayoutRhythm.COMPACT_CARD,
                    ProjectManifest.ContentDensity.MEDIUM,
                    List.of(ProjectManifest.SignalPriority.HEAT, ProjectManifest.SignalPriority.DISCUSSION),
                    ProjectManifest.NavigationMode.TOPIC_TAB,
                    ProjectManifest.MainLoop.SCROLL_DISCOVER,
                    ProjectManifest.UiTone.PLAZA,
                    "General communities should use mixed card rhythm and visible interaction signals instead of a fixed visual benchmark.");
        }

        return new ShapeProfile(false,
                ProjectManifest.PrimaryGoal.READ,
                ProjectManifest.ContentUnit.MIXED,
                ProjectManifest.ConsumptionMode.READ_FIRST,
                ProjectManifest.MediaWeight.MIXED,
                ProjectManifest.LayoutRhythm.EDITORIAL,
                ProjectManifest.ContentDensity.MEDIUM,
                List.of(ProjectManifest.SignalPriority.AUTHOR_TRUST, ProjectManifest.SignalPriority.RECENCY),
                ProjectManifest.NavigationMode.CHANNEL,
                ProjectManifest.MainLoop.READ_SAVE,
                ProjectManifest.UiTone.PROFESSIONAL,
                "Default product shape uses structured reading flow and avoids community-specific layout bias.");
    }

    private int resolveMinPrimaryCards(ShapeProfile profile, String contentMode) {
        return switch (profile.layoutRhythm()) {
            case WATERFALL -> 6;
            case COMPACT_CARD -> Math.max("CONTENT_FIRST".equals(contentMode) ? 4 : 3, 4);
            case THREAD, LIST -> "CONTENT_FIRST".equals(contentMode) ? 4 : 3;
            default -> "CONTENT_FIRST".equals(contentMode) ? 4 : 2;
        };
    }

    private int resolveMaxAuxRailSections(ShapeProfile profile) {
        return switch (profile.layoutRhythm()) {
            case WATERFALL, THREAD -> 2;
            case DASHBOARD -> 3;
            default -> 2;
        };
    }

    private boolean requiresCategoryTabs(ShapeProfile profile) {
        return switch (profile.navigationMode()) {
            case TOPIC_TAB, CATEGORY, CHANNEL -> true;
            default -> false;
        };
    }

    private boolean requiresInteractiveFiltering(ShapeProfile profile) {
        return switch (profile.layoutRhythm()) {
            case WATERFALL, COMPACT_CARD, LIST -> true;
            default -> false;
        };
    }

    private String resolveShellPattern(Map<String, String> uxStrategy, String archetype, String intent) {
        String strategyPattern = uxStrategy != null ? uxStrategy.get("shell_pattern") : null;
        if (isNotBlank(strategyPattern)) {
            return strategyPattern;
        }
        return isContentFirstArchetype(archetype, intent) ? "MINIMAL_HEADER_DRAWER_ONLY" : "SIDEBAR_PRIMARY_NAV";
    }

    private boolean isContentFirstArchetype(String archetype, String intent) {
        String source = ((archetype == null ? "" : archetype) + " " + (intent == null ? "" : intent)).toLowerCase(Locale.ROOT);
        return containsAny(source, "community", "social", "feed", "reader", "gallery", "content", "小红书", "社区", "内容", "信息流");
    }

    private String normalizeNavType(String navType) {
        String normalized = safeUpper(navType);
        return switch (normalized) {
            case "CONTEXT_WIDGET", "LEAF_DETAIL", "NAV_ANCHOR" -> normalized;
            default -> "NAV_ANCHOR";
        };
    }

    private String normalizeNavRole(String navRole) {
        String normalized = safeUpper(navRole);
        return switch (normalized) {
            case "PRIMARY", "UTILITY", "OVERLAY", "PERSONAL" -> normalized;
            default -> "PRIMARY";
        };
    }

    private String safeUpper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String safeLower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String safeName(Enum<?> value) {
        return value == null ? "" : value.name();
    }

    private String presentableRouteName(String route) {
        if (!isNotBlank(route)) {
            return "Overview";
        }
        String cleaned = route.replace(":", "").replace("/", " ").trim();
        return cleaned.isEmpty() ? "Overview" : cleaned;
    }

    private boolean containsAny(String source, String... tokens) {
        if (source == null) {
            return false;
        }
        String lower = source.toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (lower.contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private record ShapeProfile(
            boolean isContentCommunity,
            ProjectManifest.PrimaryGoal primaryGoal,
            ProjectManifest.ContentUnit contentUnit,
            ProjectManifest.ConsumptionMode consumptionMode,
            ProjectManifest.MediaWeight mediaWeight,
            ProjectManifest.LayoutRhythm layoutRhythm,
            ProjectManifest.ContentDensity contentDensity,
            List<ProjectManifest.SignalPriority> signalPriority,
            ProjectManifest.NavigationMode navigationMode,
            ProjectManifest.MainLoop mainLoop,
            ProjectManifest.UiTone uiTone,
            String validationNotes
    ) {
    }
}
