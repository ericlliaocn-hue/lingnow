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

import java.util.List;
import java.util.Locale;

/**
 * Data Engineer Agent - Responsible for generating high-fidelity,
 * context-aware mock data ecosystems for prototypes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataEngineerAgent {

    private final LlmClient llmClient;
    private static final List<String> COVER_POOL = List.of(
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
    private static final List<String> TOPIC_POOL_ZH = List.of("城市漫游", "穿搭灵感", "咖啡探店", "周末去哪儿", "本地生活", "轻运动", "高赞笔记", "今日热门");
    private static final List<String> TOPIC_POOL_EN = List.of("City walk", "Style notes", "Coffee spots", "Weekend picks", "Local life", "Light workout", "Most saved", "Trending");
    private static final List<String> CATEGORY_POOL_ZH = List.of("穿搭", "美食", "彩妆", "家居", "旅行", "健身", "摄影", "情感");
    private static final List<String> CATEGORY_POOL_EN = List.of("Style", "Food", "Beauty", "Home", "Travel", "Fitness", "Photo", "Relationships");
    private static final List<String> LOCATION_POOL_ZH = List.of("上海", "杭州", "深圳", "广州", "成都", "北京", "苏州", "厦门");
    private static final List<String> LOCATION_POOL_EN = List.of("Shanghai", "Hangzhou", "Shenzhen", "Guangzhou", "Chengdu", "Beijing", "Suzhou", "Xiamen");
    private static final List<String> CREATOR_POOL_ZH = List.of("慢生活研究所", "橘子美妆课", "露营小盒子", "周末城市探索", "一杯不晚", "衣橱整理局", "城市咖啡地图", "好物分享站");
    private static final List<String> CREATOR_POOL_EN = List.of("Weekend Edit", "City Notes", "Style Daily", "Coffee Route", "Glow Journal", "Local Picks", "Sunday Mood", "Found It");
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

        String handbook = loadHandbook();
        String lang = manifest.getMetaData() != null ? manifest.getMetaData().getOrDefault("lang", "EN") : "EN";
        String langInstruction = "ZH".equalsIgnoreCase(lang)
                ? "CRITICAL: USE CHINESE for all data values (names, categories, statuses)."
                : "Use realistic English data.";

        String systemPrompt = String.format("""
                %s
                
                YOUR GOAL: Generate a high-fidelity, realistic JSON dataset that brings a prototype to life.
                
                RULES:
                1. VOLUME: Generate 15-20 diverse records.
                2. LANGUAGE: %s
                """, handbook, langInstruction);

        String userPrompt = String.format("""
                        Requirement: %s
                        Architectural Plan (Mindmap): %s
                        Planned Pages & Field Requirements: %s
                        
                        Please output a robust JSON array of objects representing the primary business entity. 
                        CRITICAL: The objects must include ALL high-fidelity metadata fields suggested in the 'Planned Pages' section to ensure the UI components can find and render the data.
                        """,
                manifest.getUserIntent(),
                manifest.getMindMap(),
                manifest.getPages() != null ? manifest.getPages().toString() : "N/A"
        );

        try {
            String response = llmClient.chat(systemPrompt, userPrompt);
            String cleanedJson = cleanJsonResponse(response);
            String enrichedJson = enrichMockData(cleanedJson, lang);
            manifest.setMockData(enrichedJson);
            log.info("Data synthesis complete ({} chars).", enrichedJson.length());
        } catch (Exception e) {
            log.error("Data synthesis failed", e);
            // Fallback to a safe empty array
            manifest.setMockData("[]");
        }
    }

    public void normalizeExistingData(ProjectManifest manifest) {
        if (manifest == null || manifest.getMockData() == null || manifest.getMockData().isBlank()) {
            return;
        }
        String lang = manifest.getMetaData() != null ? manifest.getMetaData().getOrDefault("lang", "EN") : "EN";
        manifest.setMockData(enrichMockData(manifest.getMockData(), lang));
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

    private String enrichMockData(String cleanedJson, String lang) {
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
            for (int index = 0; index < array.size(); index++) {
                JsonNode node = array.get(index);
                if (node instanceof ObjectNode objectNode) {
                    enrichRecord(objectNode, index, zh);
                }
            }
            return objectMapper.writeValueAsString(array);
        } catch (Exception e) {
            log.warn("Data enrichment skipped due to JSON parse issue", e);
            return cleanedJson;
        }
    }

    private void enrichRecord(ObjectNode node, int index, boolean zh) {
        String cover = firstUrl(node, "封面图", "cover", "image", "thumbUrl");
        if (isBlank(cover)) {
            cover = COVER_POOL.get(index % COVER_POOL.size());
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
            avatar = AVATAR_POOL.get(index % AVATAR_POOL.size());
        }
        node.put("avatar", avatar);
        if (isBlank(firstText(node, "authorAvatar"))) {
            node.put("authorAvatar", avatar);
        }
        node.put("作者头像", avatar);

        if (isBlank(firstText(node, "author", "username", "creator"))) {
            String creator = pickFrom(index, zh ? CREATOR_POOL_ZH : CREATOR_POOL_EN);
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
        if (isBlank(category) || TOPIC_POOL_ZH.contains(category) || TOPIC_POOL_EN.contains(category)) {
            node.put("category", pickFrom(index, zh ? CATEGORY_POOL_ZH : CATEGORY_POOL_EN));
        }
        if (isBlank(firstText(node, "topic"))) {
            node.put("topic", pickFrom(index + 1, zh ? TOPIC_POOL_ZH : TOPIC_POOL_EN));
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
            node.put("location", pickFrom(index, zh ? LOCATION_POOL_ZH : LOCATION_POOL_EN));
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
            tags.add(pickFrom(index, zh ? TOPIC_POOL_ZH : TOPIC_POOL_EN));
            tags.add(pickFrom(index, zh ? CATEGORY_POOL_ZH : CATEGORY_POOL_EN));
            tags.add(pickFrom(index + 2, zh ? LOCATION_POOL_ZH : LOCATION_POOL_EN));
            tags.add(zh ? "高收藏" : "High save");
            node.set("tags", tags);
        }

        if (!node.hasNonNull("gallery") || !node.get("gallery").isArray() || node.get("gallery").isEmpty()) {
            ArrayNode gallery = objectMapper.createArrayNode();
            gallery.add(cover);
            gallery.add(COVER_POOL.get((index + 3) % COVER_POOL.size()));
            gallery.add(COVER_POOL.get((index + 5) % COVER_POOL.size()));
            node.set("gallery", gallery);
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
}
