package cc.lingnow.service;

import cc.lingnow.llm.LlmClient;
import cc.lingnow.model.ProjectManifest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Data Engineer Agent - Responsible for generating high-fidelity,
 * context-aware mock data ecosystems for prototypes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataEngineerAgent {

    private final LlmClient llmClient;
    private static final List<String> VISUAL_COVER_POOL = List.of(
            "https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?q=80&w=1200",
            "https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?q=80&w=1200",
            "https://images.unsplash.com/photo-1483985988355-763728e1935b?q=80&w=1200",
            "https://images.unsplash.com/photo-1496747611176-843222e1e57c?q=80&w=1200",
            "https://images.unsplash.com/photo-1511988617509-a57c8a288659?q=80&w=1200",
            "https://images.unsplash.com/photo-1529139574466-a303027c1d8b?q=80&w=1200",
            "https://images.unsplash.com/photo-1500534314209-a25ddb2bd429?q=80&w=1200",
            "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?q=80&w=1200",
            "https://images.unsplash.com/photo-1512436991641-6745cdb1723f?q=80&w=1200",
            "https://images.unsplash.com/photo-1504593811423-6dd665756598?q=80&w=1200"
    );
    private static final List<String> TECH_COVER_POOL = List.of(
            "https://images.unsplash.com/photo-1515879218367-8466d910aaa4?q=80&w=1200",
            "https://images.unsplash.com/photo-1516321318423-f06f85e504b3?q=80&w=1200",
            "https://images.unsplash.com/photo-1517694712202-14dd9538aa97?q=80&w=1200",
            "https://images.unsplash.com/photo-1517180102446-f3ece451e9d8?q=80&w=1200",
            "https://images.unsplash.com/photo-1451187580459-43490279c0fa?q=80&w=1200",
            "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?q=80&w=1200",
            "https://images.unsplash.com/photo-1461749280684-dccba630e2f6?q=80&w=1200",
            "https://images.unsplash.com/photo-1511376777868-611b54f68947?q=80&w=1200"
    );
    private static final List<String> AVATAR_POOL = List.of(
            "https://images.unsplash.com/photo-1494790108377-be9c29b29330?q=80&w=256",
            "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?q=80&w=256",
            "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?q=80&w=256",
            "https://images.unsplash.com/photo-1438761681033-6461ffad8d80?q=80&w=256",
            "https://images.unsplash.com/photo-1504257432389-52343af06ae3?q=80&w=256",
            "https://images.unsplash.com/photo-1502685104226-ee32379fefbe?q=80&w=256",
            "https://images.unsplash.com/photo-1544005313-94ddf0286df2?q=80&w=256",
            "https://images.unsplash.com/photo-1503023345310-bd7c1de61c7d?q=80&w=256"
    );
    private static final List<String> VISUAL_TOPIC_POOL_ZH = List.of("城市漫游", "穿搭灵感", "咖啡探店", "周末去哪儿", "本地生活", "轻运动", "高赞笔记", "今日热门");
    private static final List<String> VISUAL_TOPIC_POOL_EN = List.of("City walk", "Style notes", "Coffee spots", "Weekend picks", "Local life", "Light workout", "Most saved", "Trending");
    private static final List<String> VISUAL_CATEGORY_POOL_ZH = List.of("穿搭", "美食", "彩妆", "家居", "旅行", "健身", "摄影", "情感");
    private static final List<String> VISUAL_CATEGORY_POOL_EN = List.of("Style", "Food", "Beauty", "Home", "Travel", "Fitness", "Photo", "Relationships");
    private static final List<String> VISUAL_CONTEXT_POOL_ZH = List.of("上海", "杭州", "深圳", "广州", "成都", "北京", "苏州", "厦门");
    private static final List<String> VISUAL_CONTEXT_POOL_EN = List.of("Shanghai", "Hangzhou", "Shenzhen", "Guangzhou", "Chengdu", "Beijing", "Suzhou", "Xiamen");
    private static final List<String> VISUAL_CREATOR_POOL_ZH = List.of("慢生活研究所", "橘子美妆课", "露营小盒子", "周末城市探索", "一杯不晚", "衣橱整理局", "城市咖啡地图", "好物分享站");
    private static final List<String> VISUAL_CREATOR_POOL_EN = List.of("Weekend Edit", "City Notes", "Style Daily", "Coffee Route", "Glow Journal", "Local Picks", "Sunday Mood", "Found It");
    private static final List<String> TECH_TOPIC_POOL_ZH = List.of("前端工程", "后端架构", "AI 编程", "系统设计", "工程效率", "数据库优化", "云原生", "开源实践");
    private static final List<String> TECH_TOPIC_POOL_EN = List.of("Frontend engineering", "Backend architecture", "AI coding", "System design", "Developer productivity", "Database optimization", "Cloud native", "Open source");
    private static final List<String> TECH_CATEGORY_POOL_ZH = List.of("前端", "后端", "人工智能", "架构", "数据库", "运维", "移动端", "测试");
    private static final List<String> TECH_CATEGORY_POOL_EN = List.of("Frontend", "Backend", "AI", "Architecture", "Database", "Ops", "Mobile", "Testing");
    private static final List<String> TECH_CONTEXT_POOL_ZH = List.of("工程实践", "架构设计", "平台治理", "研发效能", "团队协作", "性能优化", "稳定性", "开源社区");
    private static final List<String> TECH_CONTEXT_POOL_EN = List.of("Engineering", "Architecture", "Platform", "Productivity", "Teamwork", "Performance", "Reliability", "Open source");
    private static final List<String> TECH_CREATOR_POOL_ZH = List.of("林前端", "周后端", "阿泽 React", "韩策架构", "云原生实验室", "测试增长记", "数据库手册", "开源观察者");
    private static final List<String> TECH_CREATOR_POOL_EN = List.of("Lin Frontend", "Zhou Backend", "Aze React", "Han Architecture", "Cloud Native Lab", "Testing Notes", "DB Manual", "Open Source Watch");
    private static final List<String> DISCUSSION_TOPIC_POOL_ZH = List.of("今日热议", "楼中楼", "技术问答", "社区反馈", "版本讨论", "踩坑记录", "发布计划", "经验交流");
    private static final List<String> DISCUSSION_TOPIC_POOL_EN = List.of("Hot today", "Threaded replies", "Tech Q&A", "Community feedback", "Release talk", "Pitfalls", "Roadmap", "Experience exchange");
    private static final List<String> DISCUSSION_CATEGORY_POOL_ZH = List.of("讨论", "问答", "反馈", "版块", "公告", "经验", "建议", "活动");
    private static final List<String> DISCUSSION_CATEGORY_POOL_EN = List.of("Discussion", "Q&A", "Feedback", "Boards", "Announcements", "Experience", "Suggestions", "Events");
    private static final List<String> DISCUSSION_CONTEXT_POOL_ZH = List.of("最新回复", "热门板块", "社区广场", "版本动态", "站务公告", "技术区", "灌水区", "问答区");
    private static final List<String> DISCUSSION_CONTEXT_POOL_EN = List.of("Latest replies", "Hot boards", "Community plaza", "Release updates", "Announcements", "Tech board", "Lounge", "Q&A board");
    private static final List<String> DISCUSSION_CREATOR_POOL_ZH = List.of("热心楼主", "版主小组", "技术答疑官", "社区观察员", "站务团队", "工程踩坑集", "回帖研究所", "产品反馈站");
    private static final List<String> DISCUSSION_CREATOR_POOL_EN = List.of("Thread starter", "Moderator team", "Q&A helper", "Community watcher", "Site team", "Bug tracker", "Reply lab", "Feedback desk");
    private final ObjectMapper objectMapper;

    private String loadHandbook() {
        try {
            return java.nio.file.Files.readString(java.nio.file.Paths.get("/Users/eric/workspace/lingnow/.agents/skills/DATA_ARCHITECT_HANDBOOK.md"));
        } catch (Exception e) {
            log.warn("[DataEngineer] Handbook not found, falling back to basic data logic.");
            return "";
        }
    }

    /**
     * Generate a robust JSON dataset based on the architectural plan.
     */
    public void generateData(ProjectManifest manifest) {
        log.info("Data Engineer is synthesizing mock records for: {}", manifest.getUserIntent());

        String lang = manifest.getMetaData() != null ? manifest.getMetaData().getOrDefault("lang", "EN") : "EN";
        MockDataProfile profile = resolveProfile(manifest);
        String deterministicJson = buildDeterministicDataset(manifest, lang, profile);
        if (!deterministicJson.isBlank()) {
            manifest.setMockData(deterministicJson);
            log.info("Data synthesis complete via deterministic shape schema ({} chars).", deterministicJson.length());
            return;
        }

        try {
            String handbook = loadHandbook();
            String langInstruction = "ZH".equalsIgnoreCase(lang)
                    ? "CRITICAL: USE CHINESE for all data values (names, categories, statuses)."
                    : "Use realistic English data.";
            String systemPrompt = String.format("""
                    %s
                    
                    YOUR GOAL: Generate a high-fidelity, realistic JSON dataset that brings a prototype to life.
                    
                    RULES:
                    1. VOLUME: Generate 10-12 diverse records.
                    2. LANGUAGE: %s
                    """, handbook, langInstruction);

            String userPrompt = String.format("""
                            Requirement: %s
                            Architectural Plan (Mindmap): %s
                            Planned Pages & Field Requirements: %s
                            Shape Contract Summary: %s
                            
                            Please output a robust JSON array of objects representing the primary business entity. 
                            CRITICAL: The objects must include ALL high-fidelity metadata fields suggested in the 'Planned Pages' section to ensure the UI components can find and render the data.
                            """,
                    manifest.getUserIntent(),
                    manifest.getMindMap(),
                    summarizePages(manifest),
                    summarizeShapeContract(manifest)
            );
            String response = llmClient.chat(systemPrompt, userPrompt);
            String cleanedJson = cleanJsonResponse(response);
            String enrichedJson = enrichMockData(cleanedJson, manifest, lang);
            manifest.setMockData(enrichedJson);
            log.info("Data synthesis complete ({} chars).", enrichedJson.length());
        } catch (Exception e) {
            log.error("Data synthesis failed", e);
            // Fallback to a safe empty array
            manifest.setMockData("[]");
        }
    }

    private String buildDeterministicDataset(ProjectManifest manifest, String lang, MockDataProfile profile) {
        try {
            boolean zh = "ZH".equalsIgnoreCase(lang);
            DynamicVocabulary vocabulary = buildDynamicVocabulary(manifest, profile, zh);
            ArrayNode records = objectMapper.createArrayNode();
            int count = profile.discoveryStyle() ? 12 : 10;
            for (int index = 0; index < count; index++) {
                records.add(buildSeedRecord(manifest, profile, vocabulary, index, zh));
            }
            return objectMapper.writeValueAsString(records);
        } catch (Exception e) {
            log.warn("Deterministic data synthesis fallback failed, switching to LLM generation: {}", e.getMessage());
            return "";
        }
    }

    public void normalizeExistingData(ProjectManifest manifest) {
        if (manifest == null || manifest.getMockData() == null || manifest.getMockData().isBlank()) {
            return;
        }
        String lang = manifest.getMetaData() != null ? manifest.getMetaData().getOrDefault("lang", "EN") : "EN";
        manifest.setMockData(enrichMockData(manifest.getMockData(), manifest, lang));
    }

    private String cleanJsonResponse(String response) {
        if (response == null) return "[]";
        String cleaned = response.trim();
        int firstBracket = cleaned.indexOf("[");
        int lastBracket = cleaned.lastIndexOf("]");
        if (firstBracket != -1 && lastBracket != -1 && lastBracket >= firstBracket) {
            return cleaned.substring(firstBracket, lastBracket + 1);
        }
        return "[]";
    }

    private String enrichMockData(String cleanedJson, ProjectManifest manifest, String lang) {
        if (cleanedJson == null || cleanedJson.isBlank()) {
            return "[]";
        }
        try {
            JsonNode root = objectMapper.readTree(cleanedJson);
            if (!root.isArray()) {
                return cleanedJson;
            }
            ArrayNode array = (ArrayNode) root;
            boolean zh = "ZH".equalsIgnoreCase(lang);
            MockDataProfile profile = resolveProfile(manifest);
            for (int index = 0; index < array.size(); index++) {
                JsonNode node = array.get(index);
                if (node instanceof ObjectNode objectNode) {
                    enrichRecord(objectNode, index, zh, profile);
                    sanitizeBlockedMediaUrls(objectNode, index, profile);
                }
            }
            return objectMapper.writeValueAsString(array);
        } catch (Exception e) {
            log.warn("Data enrichment skipped due to JSON parse issue", e);
            return cleanedJson;
        }
    }

    private void enrichRecord(ObjectNode node, int index, boolean zh, MockDataProfile profile) {
        String cover = firstUrl(node, "封面图", "cover", "image", "thumbUrl");
        if (isBlank(cover)) {
            cover = profile.coverPool().get(index % profile.coverPool().size());
        }
        node.put("cover", cover);
        if (isBlank(firstText(node, "image"))) {
            node.put("image", cover);
        }
        if (isBlank(firstText(node, "thumbUrl"))) {
            node.put("thumbUrl", cover);
        }
        node.put("封面图", cover);

        String avatar = firstUrl(node, "作者头像", "avatar", "authorAvatar");
        if (isBlank(avatar)) {
            avatar = profile.avatarPool().get(index % profile.avatarPool().size());
        }
        node.put("avatar", avatar);
        if (isBlank(firstText(node, "authorAvatar"))) {
            node.put("authorAvatar", avatar);
        }
        node.put("作者头像", avatar);

        String currentAuthor = firstText(node, "author", "username", "creator");
        if (isBlank(currentAuthor) || belongsToOppositeProfile(currentAuthor, zh, profile)) {
            String creator = pickFrom(index, zh ? profile.creatorsZh() : profile.creatorsEn());
            node.put("author", creator);
            node.put("creator", creator);
            node.put("username", creator);
        }

        if (isBlank(firstText(node, "page_route"))) {
            node.put("page_route", "/home");
        }
        if (isBlank(firstText(node, "route"))) {
            node.put("route", "/home");
        }

        String category = firstText(node, "category");
        if (isBlank(category) || belongsToOppositeProfile(category, zh, profile) || belongsToTopics(category)) {
            node.put("category", pickFrom(index, zh ? profile.categoriesZh() : profile.categoriesEn()));
        }
        String topic = firstText(node, "topic");
        if (isBlank(topic) || belongsToOppositeProfile(topic, zh, profile)) {
            node.put("topic", pickFrom(index + 1, zh ? profile.topicsZh() : profile.topicsEn()));
        }
        if (isBlank(firstText(node, "mediaType", "contentType", "noteType"))) {
            String mediaType = index % 4 == 1
                    ? (zh ? "视频" : "Video")
                    : (zh ? "图文" : "Photo");
            node.put("mediaType", mediaType);
            node.put("contentType", mediaType);
            node.put("noteType", mediaType);
        }
        if (isBlank(firstText(node, "location"))) {
            node.put("location", pickFrom(index, zh ? profile.contextZh() : profile.contextEn()));
        }

        if (isBlank(firstText(node, "likes", "likeCount"))) {
            String likes = compactCount(zh ? 980 + index * 130 : 1200 + index * 160, zh);
            node.put("likes", likes);
            node.put("likeCount", likes);
        }
        if (isBlank(firstText(node, "comments", "commentCount"))) {
            String comments = compactCount(zh ? 120 + index * 18 : 140 + index * 20, zh);
            node.put("comments", comments);
            node.put("commentCount", comments);
        }
        if (isBlank(firstText(node, "collects", "saves"))) {
            String collects = compactCount(zh ? 240 + index * 35 : 320 + index * 40, zh);
            node.put("collects", collects);
            node.put("saves", collects);
        }
        if (isBlank(firstText(node, "views", "viewCount"))) {
            String views = compactCount(zh ? 8200 + index * 900 : 9500 + index * 1200, zh);
            node.put("views", views);
            node.put("viewCount", views);
        }

        if (!node.hasNonNull("tags") || !node.get("tags").isArray() || node.get("tags").isEmpty()) {
            ArrayNode tags = objectMapper.createArrayNode();
            tags.add(pickFrom(index, zh ? profile.topicsZh() : profile.topicsEn()));
            tags.add(pickFrom(index, zh ? profile.categoriesZh() : profile.categoriesEn()));
            tags.add(pickFrom(index + 2, zh ? profile.contextZh() : profile.contextEn()));
            tags.add(profile.discoveryStyle() ? (zh ? "高收藏" : "High save") : (zh ? "高价值" : "High value"));
            node.set("tags", tags);
        }

        if (!node.hasNonNull("gallery") || !node.get("gallery").isArray() || node.get("gallery").isEmpty()) {
            ArrayNode gallery = objectMapper.createArrayNode();
            gallery.add(cover);
            gallery.add(profile.coverPool().get((index + 3) % profile.coverPool().size()));
            gallery.add(profile.coverPool().get((index + 5) % profile.coverPool().size()));
            node.set("gallery", gallery);
        }
    }

    private ObjectNode buildSeedRecord(ProjectManifest manifest, MockDataProfile profile, DynamicVocabulary vocabulary, int index, boolean zh) {
        ObjectNode node = objectMapper.createObjectNode();
        String category = pickFrom(index, zh ? vocabulary.categoriesZh() : vocabulary.categoriesEn());
        String topic = pickFrom(index + 1, zh ? vocabulary.topicsZh() : vocabulary.topicsEn());
        String context = pickFrom(index + 2, zh ? vocabulary.contextZh() : vocabulary.contextEn());
        String author = pickFrom(index, zh ? vocabulary.creatorsZh() : vocabulary.creatorsEn());
        String title = buildRecordTitle(profile, category, topic, context, index, zh);
        String description = buildRecordDescription(profile, category, topic, context, zh);
        String cover = profile.coverPool().get(index % profile.coverPool().size());
        String avatar = profile.avatarPool().get(index % profile.avatarPool().size());
        String mediaType = resolveMediaType(profile, index, zh);
        String likes = compactCount(zh ? 980 + index * 130 : 1200 + index * 160, zh);
        String comments = compactCount(zh ? 120 + index * 18 : 140 + index * 20, zh);
        String collects = compactCount(zh ? 240 + index * 35 : 320 + index * 40, zh);
        String views = compactCount(zh ? 8200 + index * 900 : 9500 + index * 1200, zh);

        node.put("id", profile.profileKey().toLowerCase(Locale.ROOT) + "-" + (index + 1));
        node.put("title", title);
        node.put("name", title);
        node.put("description", description);
        node.put("summary", description);
        node.put("content", buildLongFormBody(profile, title, description, zh));
        node.put("cover", cover);
        node.put("image", cover);
        node.put("thumbUrl", cover);
        node.put("封面图", cover);
        node.put("avatar", avatar);
        node.put("authorAvatar", avatar);
        node.put("作者头像", avatar);
        node.put("author", author);
        node.put("creator", author);
        node.put("username", author);
        node.put("category", category);
        node.put("topic", topic);
        node.put("location", context);
        node.put("time", zh ? (index + 1) + "小时前" : (index + 1) + "h ago");
        node.put("publishTime", zh ? "刚刚" : "just now");
        node.put("mediaType", mediaType);
        node.put("contentType", mediaType);
        node.put("noteType", mediaType);
        node.put("likes", likes);
        node.put("likeCount", likes);
        node.put("comments", comments);
        node.put("commentCount", comments);
        node.put("collects", collects);
        node.put("saves", collects);
        node.put("views", views);
        node.put("viewCount", views);
        node.put("page_route", "/home");
        node.put("route", "/home");
        node.put("authorBadge", resolveAuthorBadge(profile, zh));
        node.put("readTime", zh ? (6 + (index % 8)) + " 分钟" : (6 + (index % 8)) + " min");
        node.put("board", category);
        node.put("replyCount", comments);
        node.put("lastActiveAt", zh ? "刚刚活跃" : "active just now");
        node.put("productTag", profile.discoveryStyle() ? (zh ? "好物推荐" : "Product pick") : (zh ? "知识点" : "Key point"));
        node.put("contextLabel", context);

        ArrayNode tags = objectMapper.createArrayNode();
        tags.add(topic);
        tags.add(category);
        tags.add(context);
        tags.add(profile.discoveryStyle() ? (zh ? "高收藏" : "High save") : (zh ? "高价值" : "High value"));
        node.set("tags", tags);

        ArrayNode gallery = objectMapper.createArrayNode();
        gallery.add(cover);
        gallery.add(profile.coverPool().get((index + 3) % profile.coverPool().size()));
        gallery.add(profile.coverPool().get((index + 5) % profile.coverPool().size()));
        node.set("gallery", gallery);
        return node;
    }

    private DynamicVocabulary buildDynamicVocabulary(ProjectManifest manifest, MockDataProfile profile, boolean zh) {
        List<String> seeds = extractSignalTerms(manifest, zh);
        List<String> topics = mergeVocabulary(zh ? profile.topicsZh() : profile.topicsEn(), seeds, 8);
        List<String> categories = mergeVocabulary(zh ? profile.categoriesZh() : profile.categoriesEn(), seeds, 8);
        List<String> context = mergeVocabulary(zh ? profile.contextZh() : profile.contextEn(), seeds, 8);
        List<String> creators = buildCreatorNames(seeds, zh ? profile.creatorsZh() : profile.creatorsEn(), zh, profile.profileKey());
        return new DynamicVocabulary(
                topics,
                zh ? topics : profile.topicsZh(),
                categories,
                zh ? categories : profile.categoriesZh(),
                context,
                zh ? context : profile.contextZh(),
                creators,
                zh ? creators : profile.creatorsZh()
        );
    }

    private List<String> extractSignalTerms(ProjectManifest manifest, boolean zh) {
        Set<String> terms = new LinkedHashSet<>();
        addTerms(terms, manifest == null ? "" : manifest.getUserIntent(), zh);
        addTerms(terms, manifest == null ? "" : manifest.getArchetype(), zh);
        addTerms(terms, manifest == null ? "" : manifest.getOverview(), zh);
        if (manifest != null && manifest.getFeatures() != null) {
            manifest.getFeatures().forEach(feature -> {
                addTerms(terms, feature.getName(), zh);
                addTerms(terms, feature.getDescription(), zh);
            });
        }
        if (manifest != null && manifest.getPages() != null) {
            manifest.getPages().forEach(page -> {
                addTerms(terms, page.getDescription(), zh);
                addTerms(terms, page.getRoute(), zh);
            });
        }
        if (terms.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(terms).subList(0, Math.min(terms.size(), 12));
    }

    private void addTerms(Set<String> terms, String source, boolean zh) {
        if (source == null || source.isBlank()) {
            return;
        }
        String[] rawTokens = source.split("[\\s,，。；;：:、/\\-()（）]+");
        for (String token : rawTokens) {
            String normalized = token == null ? "" : token.trim();
            if (isUsefulTerm(normalized, zh)) {
                terms.add(normalized);
            }
        }
    }

    private boolean isUsefulTerm(String token, boolean zh) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String normalized = token.toLowerCase(Locale.ROOT);
        if (List.of("做一个", "类似", "网址", "community", "style", "url", "platform", "content", "social", "feed").contains(normalized)) {
            return false;
        }
        return zh ? normalized.length() >= 2 && normalized.length() <= 8 : normalized.length() >= 3 && normalized.length() <= 20;
    }

    private List<String> mergeVocabulary(List<String> base, List<String> dynamic, int limit) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (dynamic != null) {
            merged.addAll(dynamic);
        }
        merged.addAll(base);
        return new ArrayList<>(merged).subList(0, Math.min(merged.size(), limit));
    }

    private List<String> buildCreatorNames(List<String> seeds, List<String> fallback, boolean zh, String profileKey) {
        List<String> suffixes = switch (profileKey) {
            case "VISUAL" ->
                    zh ? List.of("日记", "研究所", "灵感局", "地图", "周刊") : List.of("Diary", "Studio", "Guide", "Map", "Weekly");
            case "DISCUSSION" ->
                    zh ? List.of("观察员", "楼主", "答疑官", "版主", "研究会") : List.of("Host", "Starter", "Helper", "Mod", "Forum");
            default ->
                    zh ? List.of("前线", "实验室", "手册", "观察者", "笔记") : List.of("Lab", "Manual", "Notes", "Digest", "Watch");
        };
        LinkedHashSet<String> creators = new LinkedHashSet<>();
        for (int i = 0; i < Math.min(seeds.size(), 6); i++) {
            creators.add(seeds.get(i) + suffixes.get(i % suffixes.size()));
        }
        creators.addAll(fallback);
        return new ArrayList<>(creators).subList(0, Math.min(creators.size(), 8));
    }

    private String buildRecordTitle(MockDataProfile profile, String category, String topic, String context, int index, boolean zh) {
        if (profile.profileKey().equals("TECH")) {
            return zh
                    ? String.format("%s：关于%s的实战笔记 %d", category, topic, index + 1)
                    : String.format("%s: practical notes on %s %d", category, topic, index + 1);
        }
        if (profile.profileKey().equals("DISCUSSION")) {
            return zh
                    ? String.format("%s｜%s正在被热议", category, topic)
                    : String.format("%s | %s is trending right now", category, topic);
        }
        return zh
                ? String.format("%s灵感：%s里的真实分享", context, topic)
                : String.format("%s inspiration: a real share about %s", context, topic);
    }

    private String buildRecordDescription(MockDataProfile profile, String category, String topic, String context, boolean zh) {
        if (profile.profileKey().equals("TECH")) {
            return zh
                    ? String.format("围绕%s和%s整理关键观点、实践经验与可复用方法，帮助用户快速判断内容价值。", category, topic)
                    : String.format("Organizes key ideas, practices, and reusable methods around %s and %s for quick value judgment.", category, topic);
        }
        if (profile.profileKey().equals("DISCUSSION")) {
            return zh
                    ? String.format("从%s切入当前社区讨论，突出回复热度、观点碰撞和后续跟进价值。", context)
                    : String.format("Starts from %s and emphasizes reply heat, viewpoint clashes, and follow-up value.", context);
        }
        return zh
                ? String.format("结合%s与%s，突出真实体验、收藏价值与继续浏览的冲动。", category, context)
                : String.format("Blends %s and %s with real-life usefulness, save value, and browse-next momentum.", category, context);
    }

    private String buildLongFormBody(MockDataProfile profile, String title, String description, boolean zh) {
        if (profile.profileKey().equals("TECH")) {
            return zh
                    ? title + "。本文围绕问题背景、关键方法、实践步骤与风险边界展开，帮助读者快速建立判断。 " + description
                    : title + ". This post covers the context, key method, execution steps, and trade-offs so readers can form a clear judgment. " + description;
        }
        if (profile.profileKey().equals("DISCUSSION")) {
            return zh
                    ? title + "。内容聚焦观点碰撞、经验交流与后续回复脉络，适合继续参与讨论。 " + description
                    : title + ". The content focuses on viewpoint clashes, shared experience, and reply threads worth joining. " + description;
        }
        return zh
                ? title + "。通过图片、视频与真实场景分享带来可信的灵感感受，帮助用户继续收藏和转化。 " + description
                : title + ". The post uses imagery, short video, and real context to create trustworthy inspiration and save-worthy intent. " + description;
    }

    private String resolveMediaType(MockDataProfile profile, int index, boolean zh) {
        if (profile.profileKey().equals("TECH")) {
            return zh ? "文章" : "Article";
        }
        if (profile.profileKey().equals("DISCUSSION")) {
            return zh ? "讨论" : "Thread";
        }
        return index % 4 == 1 ? (zh ? "视频" : "Video") : (zh ? "图文" : "Photo");
    }

    private String resolveAuthorBadge(MockDataProfile profile, boolean zh) {
        if (profile.profileKey().equals("TECH")) {
            return zh ? "专业作者" : "Trusted author";
        }
        if (profile.profileKey().equals("DISCUSSION")) {
            return zh ? "活跃答主" : "Active voice";
        }
        return zh ? "真实分享" : "Verified vibe";
    }

    private void sanitizeBlockedMediaUrls(JsonNode node, int index, MockDataProfile profile) {
        if (node == null) {
            return;
        }
        if (node instanceof ObjectNode objectNode) {
            objectNode.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                if (value == null || value.isNull()) {
                    return;
                }
                if (value.isTextual() && isBlockedMockMediaUrl(value.asText())) {
                    String fieldName = entry.getKey().toLowerCase(Locale.ROOT);
                    String replacement = isAvatarField(fieldName)
                            ? profile.avatarPool().get(index % profile.avatarPool().size())
                            : profile.coverPool().get(index % profile.coverPool().size());
                    objectNode.put(entry.getKey(), replacement);
                    return;
                }
                sanitizeBlockedMediaUrls(value, index + 1, profile);
            });
            return;
        }
        if (node instanceof ArrayNode arrayNode) {
            for (int i = 0; i < arrayNode.size(); i++) {
                JsonNode value = arrayNode.get(i);
                if (value != null && value.isTextual() && isBlockedMockMediaUrl(value.asText())) {
                    arrayNode.set(i, objectMapper.getNodeFactory().textNode(profile.coverPool().get((index + i) % profile.coverPool().size())));
                } else {
                    sanitizeBlockedMediaUrls(value, index + i + 1, profile);
                }
            }
        }
    }

    private String firstText(ObjectNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value != null && !value.isNull() && !value.isContainerNode()) {
                String text = value.asText();
                if (!isBlank(text)) {
                    return text;
                }
            }
        }
        return "";
    }

    private String firstUrl(ObjectNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = firstText(node, fieldName);
            if (looksLikeUrl(value)) {
                return value;
            }
        }
        return "";
    }

    private String pickFrom(int index, List<String> source) {
        return source.get(Math.floorMod(index, source.size()));
    }

    private String compactCount(int value, boolean zh) {
        if (value >= 10000) {
            double w = value / 10000.0;
            return zh
                    ? String.format(Locale.US, "%.1fw", w)
                    : String.format(Locale.US, "%.1fk", value / 1000.0);
        }
        if (!zh && value >= 1000) {
            return String.format(Locale.US, "%.1fk", value / 1000.0);
        }
        return Integer.toString(value);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean looksLikeUrl(String value) {
        if (isBlank(value)) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (!(lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("data:image/"))) {
            return false;
        }
        if (lower.startsWith("data:image/")) {
            return true;
        }
        return !isBlockedMockMediaUrl(value);
    }

    private boolean isBlockedMockMediaUrl(String value) {
        try {
            java.net.URI uri = java.net.URI.create(value);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
            return "img.lingnow.cn".equals(host) || path.contains("/mocks/");
        } catch (Exception ignored) {
            return true;
        }
    }

    private boolean isAvatarField(String fieldName) {
        return fieldName.contains("avatar") || fieldName.contains("头像");
    }

    private String summarizePages(ProjectManifest manifest) {
        if (manifest == null || manifest.getPages() == null || manifest.getPages().isEmpty()) {
            return "N/A";
        }
        return manifest.getPages().stream()
                .map(page -> page.getRoute() + " [" + page.getNavRole() + "/" + page.getNavType() + "] components="
                        + (page.getComponents() == null ? List.of() : page.getComponents().stream().limit(5).toList()))
                .reduce((a, b) -> a + "\n- " + b)
                .map(summary -> "- " + summary)
                .orElse("N/A");
    }

    private String summarizeShapeContract(ProjectManifest manifest) {
        if (manifest == null || manifest.getDesignContract() == null) {
            return "No shape contract";
        }
        ProjectManifest.DesignContract contract = manifest.getDesignContract();
        return String.format(
                "goal=%s, unit=%s, consumption=%s, media=%s, rhythm=%s, density=%s, loop=%s, tone=%s",
                contract.getPrimaryGoal(),
                contract.getContentUnit(),
                contract.getConsumptionMode(),
                contract.getMediaWeight(),
                contract.getLayoutRhythm(),
                contract.getContentDensity(),
                contract.getMainLoop(),
                contract.getUiTone()
        );
    }

    private boolean belongsToTopics(String value) {
        return containsAny(value, VISUAL_TOPIC_POOL_ZH, VISUAL_TOPIC_POOL_EN, TECH_TOPIC_POOL_ZH, TECH_TOPIC_POOL_EN, DISCUSSION_TOPIC_POOL_ZH, DISCUSSION_TOPIC_POOL_EN);
    }

    private boolean belongsToOppositeProfile(String value, boolean zh, MockDataProfile profile) {
        if (isBlank(value)) {
            return false;
        }
        if (profile.profileKey().equals("TECH")) {
            return containsAny(value, zh ? VISUAL_TOPIC_POOL_ZH : VISUAL_TOPIC_POOL_EN,
                    zh ? VISUAL_CATEGORY_POOL_ZH : VISUAL_CATEGORY_POOL_EN,
                    zh ? VISUAL_CREATOR_POOL_ZH : VISUAL_CREATOR_POOL_EN);
        }
        if (profile.profileKey().equals("VISUAL")) {
            return containsAny(value, zh ? TECH_TOPIC_POOL_ZH : TECH_TOPIC_POOL_EN,
                    zh ? TECH_CATEGORY_POOL_ZH : TECH_CATEGORY_POOL_EN,
                    zh ? TECH_CREATOR_POOL_ZH : TECH_CREATOR_POOL_EN);
        }
        return false;
    }

    private boolean containsAny(String value, List<String>... pools) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        for (List<String> pool : pools) {
            for (String item : pool) {
                if (normalized.equals(item.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }

    private MockDataProfile resolveProfile(ProjectManifest manifest) {
        if (manifest != null && manifest.getDesignContract() != null) {
            ProjectManifest.DesignContract contract = manifest.getDesignContract();
            if (contract.getLayoutRhythm() == ProjectManifest.LayoutRhythm.WATERFALL
                    || contract.getMediaWeight() == ProjectManifest.MediaWeight.VISUAL_HEAVY) {
                return new MockDataProfile("VISUAL", VISUAL_COVER_POOL, AVATAR_POOL, VISUAL_TOPIC_POOL_ZH, VISUAL_TOPIC_POOL_EN,
                        VISUAL_CATEGORY_POOL_ZH, VISUAL_CATEGORY_POOL_EN, VISUAL_CREATOR_POOL_ZH, VISUAL_CREATOR_POOL_EN,
                        VISUAL_CONTEXT_POOL_ZH, VISUAL_CONTEXT_POOL_EN, true);
            }
            if (contract.getLayoutRhythm() == ProjectManifest.LayoutRhythm.THREAD
                    || contract.getPrimaryGoal() == ProjectManifest.PrimaryGoal.DISCUSS) {
                return new MockDataProfile("DISCUSSION", TECH_COVER_POOL, AVATAR_POOL, DISCUSSION_TOPIC_POOL_ZH, DISCUSSION_TOPIC_POOL_EN,
                        DISCUSSION_CATEGORY_POOL_ZH, DISCUSSION_CATEGORY_POOL_EN, DISCUSSION_CREATOR_POOL_ZH, DISCUSSION_CREATOR_POOL_EN,
                        DISCUSSION_CONTEXT_POOL_ZH, DISCUSSION_CONTEXT_POOL_EN, false);
            }
            if (contract.getPrimaryGoal() == ProjectManifest.PrimaryGoal.READ
                    || contract.getContentUnit() == ProjectManifest.ContentUnit.ARTICLE
                    || contract.getMediaWeight() == ProjectManifest.MediaWeight.TEXT_HEAVY) {
                return new MockDataProfile("TECH", TECH_COVER_POOL, AVATAR_POOL, TECH_TOPIC_POOL_ZH, TECH_TOPIC_POOL_EN,
                        TECH_CATEGORY_POOL_ZH, TECH_CATEGORY_POOL_EN, TECH_CREATOR_POOL_ZH, TECH_CREATOR_POOL_EN,
                        TECH_CONTEXT_POOL_ZH, TECH_CONTEXT_POOL_EN, false);
            }
        }
        String source = ((manifest == null ? "" : manifest.getUserIntent()) + " " + (manifest == null ? "" : manifest.getArchetype())).toLowerCase(Locale.ROOT);
        if (source.contains("小红书") || source.contains("beauty") || source.contains("travel") || source.contains("灵感")) {
            return new MockDataProfile("VISUAL", VISUAL_COVER_POOL, AVATAR_POOL, VISUAL_TOPIC_POOL_ZH, VISUAL_TOPIC_POOL_EN,
                    VISUAL_CATEGORY_POOL_ZH, VISUAL_CATEGORY_POOL_EN, VISUAL_CREATOR_POOL_ZH, VISUAL_CREATOR_POOL_EN,
                    VISUAL_CONTEXT_POOL_ZH, VISUAL_CONTEXT_POOL_EN, true);
        }
        return new MockDataProfile("TECH", TECH_COVER_POOL, AVATAR_POOL, TECH_TOPIC_POOL_ZH, TECH_TOPIC_POOL_EN,
                TECH_CATEGORY_POOL_ZH, TECH_CATEGORY_POOL_EN, TECH_CREATOR_POOL_ZH, TECH_CREATOR_POOL_EN,
                TECH_CONTEXT_POOL_ZH, TECH_CONTEXT_POOL_EN, false);
    }

    private record MockDataProfile(
            String profileKey,
            List<String> coverPool,
            List<String> avatarPool,
            List<String> topicsZh,
            List<String> topicsEn,
            List<String> categoriesZh,
            List<String> categoriesEn,
            List<String> creatorsZh,
            List<String> creatorsEn,
            List<String> contextZh,
            List<String> contextEn,
            boolean discoveryStyle
    ) {
    }

    private record DynamicVocabulary(
            List<String> topicsZh,
            List<String> topicsEn,
            List<String> categoriesZh,
            List<String> categoriesEn,
            List<String> contextZh,
            List<String> contextEn,
            List<String> creatorsZh,
            List<String> creatorsEn
    ) {
    }
}
