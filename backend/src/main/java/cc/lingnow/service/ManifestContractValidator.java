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
        boolean contentCommunity = isContentFirstArchetype(manifest.getArchetype(), manifest.getUserIntent());
        String contentMode = switch (shellPattern) {
            case "MINIMAL_HEADER_DRAWER_ONLY" -> "CONTENT_FIRST";
            case "PERSISTENT_TOP_DYNAMIC_SIDEBAR" -> "CONTENT_FIRST";
            default -> contentCommunity ? "CONTENT_FIRST" : "SIDEBAR_FIRST";
        };

        long primaryCount = pages.stream().filter(page -> "PRIMARY".equals(page.getNavRole())).count();
        boolean requiresComposer = pages.stream().anyMatch(page -> "UTILITY".equals(page.getNavRole()))
                || containsAny(manifest.getUserIntent(), "发布", "社区", "内容", "社交", "post", "community", "social", "feed");
        boolean requiresSearch = containsAny(manifest.getUserIntent(), "搜索", "发现", "explore", "search", "feed")
                || pages.stream().anyMatch(page -> containsAny(page.getRoute(), "explore", "discover", "search"));
        boolean requiresDetailOverlay = pages.stream().anyMatch(page -> "OVERLAY".equals(page.getNavRole()));

        return ProjectManifest.DesignContract.builder()
                .shellPattern(shellPattern)
                .contentMode(contentMode)
                .minPrimarySections((int) Math.max(1, Math.min(primaryCount, 4)))
                .minPrimaryCards(contentCommunity ? 6 : ("CONTENT_FIRST".equals(contentMode) ? 4 : 2))
                .requiresSearch(requiresSearch)
                .requiresComposer(requiresComposer)
                .requiresDetailOverlay(requiresDetailOverlay)
                .validationNotes(contentCommunity
                        ? "Community/content archetype requires a feed-first homepage with visible cards and detail handoff."
                        : "Normalized by contract validator")
                .build();
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
}
