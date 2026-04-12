package cc.lingnow.service;

import cc.lingnow.dto.GenerateRequest;
import cc.lingnow.dto.GenerateResponse;
import cc.lingnow.dto.ProjectHistoryDto;
import cc.lingnow.model.ProjectManifest;
import cc.lingnow.model.PrototypeBundle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Generation Service - Orchestrator for Multi-Agent Collaboration.
 * Coordinates Architect, Designer, Frontend, and Backend Agents.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenerationService {

    private final ManifestRegistry manifestRegistry;
    private final IndustryIntelligenceAgent intelligenceAgent;
    private final ProductArchitectAgent architectAgent;
    private final ManifestContractValidator manifestContractValidator;
    private final DataEngineerAgent dataEngineerAgent;
    private final VisualDNAAgent visualDNAAgent;
    private final PrototypeBundleCompiler prototypeBundleCompiler;
    private final UiDesignerAgent designerAgent;
    private final FunctionalAuditorAgent functionalAuditorAgent;
    private final AutoRepairAgent autoRepairAgent;
    private final FrontendDeveloperAgent frontendAgent;
    private final BackendDeveloperAgent backendAgent;
    private final DeploymentAgent deploymentAgent;

    /**
     * Phase 1: Planning - Architect analyzes requirements
     */
    public ProjectManifest planRequirements(String sessionId, String prompt, String lang) {
        log.info("Phase 1: Planning for session: {} (lang: {})", sessionId, lang);
        ProjectManifest manifest = manifestRegistry.getOrCreate(sessionId, prompt);

        // Persist language context in metadata
        if (manifest.getMetaData() == null) manifest.setMetaData(new HashMap<>());
        manifest.getMetaData().put("lang", lang != null ? lang : "EN");

        manifest.setStatus(ProjectManifest.ProjectStatus.PLANNING);
        manifestRegistry.save(manifest);

        // Step 0: Strategic Intelligence (Think before Act)
        intelligenceAgent.synthesizeStrategy(manifest);

        architectAgent.analyze(manifest);
        manifestContractValidator.normalize(manifest);
        PrototypeBundle initialBundle = prototypeBundleCompiler.compile(manifest);
        manifest.getMetaData().put("design_ready", "false");
        manifest.getMetaData().put("data_ready", "false");
        manifest.getMetaData().put("visual_ready", "false");
        if (initialBundle != null) {
            manifest.getMetaData().put("bundle_ready", "true");
        }
        
        manifestRegistry.save(manifest);
        
        return manifest;
    }

    /**
     * Phase 2: Designing - UI Designer creates prototype
     */
    public ProjectManifest generatePrototype(String sessionId, String lang, String overriddenMindMap) {
        log.info("Phase 2: Designing for session: {} (lang: {})", sessionId, lang);
        ProjectManifest manifest = manifestRegistry.get(sessionId);
        if (manifest == null) throw new RuntimeException("Manifest not found for session: " + sessionId);

        if (lang != null) {
            if (manifest.getMetaData() == null) manifest.setMetaData(new HashMap<>());
            manifest.getMetaData().put("lang", lang);
        }

        if (shouldApplyMindMapOverride(manifest, overriddenMindMap)) {
            log.info("Applying frontend-driven mindmap override (length: {})", overriddenMindMap.length());
            manifest.setMindMap(overriddenMindMap);
        } else if (overriddenMindMap != null && !overriddenMindMap.isBlank()) {
            log.warn("Ignoring suspicious frontend mindmap override (length: {}) to preserve planning quality.",
                    overriddenMindMap.length());
        }
        manifestContractValidator.normalize(manifest);
        ensureDesignInputs(manifest);
        manifest.getMetaData().put("design_ready", "false");

        manifest.setStatus(ProjectManifest.ProjectStatus.DESIGNING);

        // Phase 1: Instant Seed (Commit 0) - Build and save deterministic framework immediately
        log.info("[Generation] Triggering Phase 1 Instant Seed design...");
        designerAgent.rebuildShapeAlignedPrototype(manifest);
        manifestRegistry.save(manifest);

        // Phase 2: Background Refinement (Polishing with LLM) - Run ASYNC for instant UX
        log.info("[Generation] Offloading Phase 2 LLM refinement to background thread...");
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                designerAgent.design(manifest);
                runPrototypeQualityPass(manifest);
                manifest.setStatus(ProjectManifest.ProjectStatus.QA);
                createSnapshot(manifest, "Initial Polished Design");
                manifestRegistry.save(manifest);
                log.info("[Generation] Phase 2 background refinement completed for session: {}", sessionId);
            } catch (Exception e) {
                log.error("[Generation] Phase 2 background refinement failed", e);
            }
        });

        return manifest;
    }

    /**
     * M6: Iterative Redesign
     */
    public ProjectManifest redesignPrototype(String sessionId, String instructions, String lang) {
        log.info("Iterative Design for session: {} with instructions: {}", sessionId, instructions);
        ProjectManifest manifest = manifestRegistry.get(sessionId);
        if (manifest == null) throw new RuntimeException("Manifest not found for session: " + sessionId);

        if (lang != null) {
            if (manifest.getMetaData() == null) manifest.setMetaData(new HashMap<>());
            manifest.getMetaData().put("lang", lang);
        }
        manifestContractValidator.normalize(manifest);
        ensureDesignInputs(manifest);
        manifest.getMetaData().put("design_ready", "false");

        manifest.setStatus(ProjectManifest.ProjectStatus.DESIGNING);
        manifestRegistry.save(manifest);

        designerAgent.redesign(manifest, instructions);
        runPrototypeQualityPass(manifest);
        manifest.setStatus(ProjectManifest.ProjectStatus.QA);
        createSnapshot(manifest, "AI Revision: " + instructions);

        manifestRegistry.save(manifest);
        return manifest;
    }

    public ProjectManifest saveSnapshot(String sessionId, String html, String summary) {
        ProjectManifest manifest = manifestRegistry.get(sessionId);
        if (manifest == null) throw new RuntimeException("Manifest not found");

        manifest.setPrototypeHtml(html);
        runPrototypeQualityPass(manifest);
        manifest.setStatus(ProjectManifest.ProjectStatus.QA);
        createSnapshot(manifest, summary != null ? summary : "Manual Save");
        
        manifestRegistry.save(manifest);
        return manifest;
    }

    public ProjectManifest rollbackToVersion(String sessionId, String targetVersion) {
        ProjectManifest manifest = manifestRegistry.get(sessionId);
        if (manifest == null) throw new RuntimeException("Manifest not found");

        if (manifest.getSnapshots() != null) {
            manifest.getSnapshots().stream()
                    .filter(s -> s.getVersion().equals(targetVersion))
                    .findFirst()
                    .ifPresent(s -> {
                        manifest.setPrototypeHtml(s.getHtml());
                        manifest.setVersion(s.getVersion());
                    });
        }
        runPrototypeQualityPass(manifest);
        manifest.setStatus(ProjectManifest.ProjectStatus.QA);
        
        manifestRegistry.save(manifest);
        return manifest;
    }

    private void createSnapshot(ProjectManifest manifest, String summary) {
        if (manifest.getSnapshots() == null) manifest.setSnapshots(new ArrayList<>());
        String nextVersion = incrementPatchVersion(manifest.getVersion());

        manifest.setVersion(nextVersion);

        manifest.getSnapshots().add(ProjectManifest.Snapshot.builder()
                .version(nextVersion)
                .html(manifest.getPrototypeHtml())
                .timestamp(System.currentTimeMillis())
                .summary(summary)
                .build());
    }

    /**
     * Phase 3: Coding - Full-stack development
     */
    public GenerateResponse developFullStack(String sessionId) {
        log.info("Phase 3: Coding for session: {}", sessionId);
        ProjectManifest manifest = manifestRegistry.get(sessionId);
        if (manifest == null) throw new RuntimeException("Manifest not found for session: " + sessionId);

        manifest.setStatus(ProjectManifest.ProjectStatus.CODING);
        
        // Versioning and ChangeLog
        if (manifest.getVersion() == null) {
            manifest.setVersion("v1.0.0");
            manifest.setChangeLog(new ArrayList<>());
            manifest.getChangeLog().add("Initial project creation: " + manifest.getUserIntent());
        } else {
            manifest.setVersion(incrementPatchVersion(manifest.getVersion()));
            if (manifest.getChangeLog() == null) {
                manifest.setChangeLog(new ArrayList<>());
            }
            manifest.getChangeLog().add("Iterative update (" + manifest.getVersion() + "): " + manifest.getUserIntent());
        }
        manifestRegistry.save(manifest);

        Map<String, String> feFiles;
        Map<String, String> beFiles;

        // Execute Frontend and Backend Agents
        feFiles = frontendAgent.develop(manifest);
        beFiles = backendAgent.develop(manifest);

        // Phase 4: Deploying
        manifest.setStatus(ProjectManifest.ProjectStatus.DEPLOYING);
        manifestRegistry.save(manifest);

        deploymentAgent.generate(manifest);

        // Build Response and Update Manifest
        Map<String, String> allFiles = new HashMap<>();
        allFiles.putAll(feFiles);
        allFiles.putAll(beFiles);

        Map<String, String> dependencies = new HashMap<>();
        dependencies.put("vue", "^3.4.0");
        dependencies.put("tailwindcss", "^3.4.0");
        dependencies.put("lucide-vue-next", "latest");

        manifest.setGeneratedFiles(allFiles);
        manifest.setDependencies(dependencies);
        manifest.setStatus(ProjectManifest.ProjectStatus.DONE);
        manifestRegistry.save(manifest);

        return new GenerateResponse(
            (manifest.getPages() == null || manifest.getPages().isEmpty()) ? "Generated App" : manifest.getPages().get(0).getDescription(),
            "AI-generated full-stack application from LingNow.cc",
            allFiles,
            dependencies,
            manifest
        );
    }

    /**
     * Legacy orchestrator for Full-Stack code generation
     */
    public GenerateResponse generateCode(GenerateRequest request) {
        log.info("Orchestrating full-stack generation for session: {}", request.sessionId());

        try {
            planRequirements(request.sessionId(), request.prompt(), request.lang());
            generatePrototype(request.sessionId(), request.lang(), null);
            return developFullStack(request.sessionId());
        } catch (Exception e) {
            log.error("Full-stack generation failed", e);
            ProjectManifest manifest = manifestRegistry.get(request.sessionId());
            if (manifest != null) {
                manifest.setStatus(ProjectManifest.ProjectStatus.ERROR);
                manifestRegistry.save(manifest);
            }
            throw new RuntimeException("AI Full-Stack Generation failed: " + e.getMessage(), e);
        }
    }

    public ProjectManifest getManifest(String sessionId) {
        ProjectManifest manifest = manifestRegistry.get(sessionId);
        if (manifest == null) {
            return null;
        }
        if (shouldRefreshPrototypeDisplay(manifest)) {
            designerAgent.rebuildShapeAlignedPrototype(manifest);
            runPrototypeQualityPass(manifest);
            manifestRegistry.save(manifest);
        }
        if (shouldRefreshBundle(manifest)) {
            prototypeBundleCompiler.compile(manifest);
            manifestRegistry.save(manifest);
        }
        repairScreenBulletsForDisplay(manifest);
        return manifest;
    }

    public List<ProjectHistoryDto> getHistory() {
        return manifestRegistry.listHistory();
    }

    private void runPrototypeQualityPass(ProjectManifest manifest) {
        if (manifest.getPrototypeHtml() == null) {
            return;
        }
        if (manifest.getMetaData() == null) {
            manifest.setMetaData(new HashMap<>());
        }

        FunctionalAuditorAgent.AuditOutcome auditOutcome = functionalAuditorAgent.verify(manifest);
        String auditResult = auditOutcome.getSummary();

        if (!auditOutcome.isPassed()) {
            if (shouldRebuildFromShape(manifest, auditOutcome)) {
                designerAgent.rebuildShapeAlignedPrototype(manifest);
            } else {
                String repairedHtml = autoRepairAgent.checkAndFix(
                        manifest.getPrototypeHtml(),
                        manifest.getUserIntent(),
                        manifest.getMockData(),
                        auditResult
                );
                manifest.setPrototypeHtml(repairedHtml);
            }
            auditOutcome = functionalAuditorAgent.verify(manifest);
            auditResult = auditOutcome.getSummary();

            if (!auditOutcome.isPassed() && shouldRebuildFromShape(manifest, auditOutcome)) {
                designerAgent.rebuildShapeAlignedPrototype(manifest);
                auditOutcome = functionalAuditorAgent.verify(manifest);
                auditResult = auditOutcome.getSummary();
            }
        }

        manifest.getMetaData().put("functional_audit_result", auditResult);
        manifest.getMetaData().put("design_ready", String.valueOf(auditOutcome.isPassed()));
    }

    private boolean shouldRefreshBundle(ProjectManifest manifest) {
        if (manifest.getPrototypeBundle() == null) {
            return true;
        }
        if (manifest.getPrototypeBundle().getCapabilityLayer() == null || manifest.getPrototypeBundle().getSurfaceIr() == null) {
            return true;
        }
        if (manifest.getPrototypeBundle().getExperienceBrief() == null) {
            return true;
        }
        List<PrototypeBundle.ScreenBullet> bullets = manifest.getPrototypeBundle().getExperienceBrief().getScreenBullets();
        if (bullets == null || bullets.isEmpty()) {
            return true;
        }
        return bullets.stream()
                .map(PrototypeBundle.ScreenBullet::getDescription)
                .filter(Objects::nonNull)
                .allMatch(desc -> desc.contains("围绕“"));
    }

    private void repairScreenBulletsForDisplay(ProjectManifest manifest) {
        if (manifest.getPrototypeBundle() == null
                || manifest.getPrototypeBundle().getExperienceBrief() == null
                || manifest.getPages() == null
                || manifest.getPages().isEmpty()) {
            return;
        }
        List<PrototypeBundle.ScreenBullet> bullets = new ArrayList<>();
        for (ProjectManifest.PageSpec page : manifest.getPages().stream().limit(4).toList()) {
            String route = page.getRoute() == null ? "" : page.getRoute().toLowerCase();
            String description = page.getDescription() == null ? "" : page.getDescription();
            String label;
            if (description.contains("首页")) {
                label = "社区首页";
            } else if (route.contains("discover") || description.contains("发现页")) {
                label = "发现页";
            } else if (route.contains("following") || description.contains("关注流")) {
                label = "关注流页";
            } else if (route.contains("publish") || description.contains("发布")) {
                label = description.contains("笔记") ? "发布笔记页" : "发布页";
            } else if (route.contains("profile") || route.contains("user") || description.contains("创作者")) {
                label = "创作者主页";
            } else if (route.contains("post") || route.contains("detail") || description.contains("详情")) {
                label = "帖子详情页";
            } else {
                label = description;
            }
            String desc;
            if (route.contains("detail") || route.contains("post")) {
                desc = "查看完整内容、作者信息与互动详情。";
            } else if (route.contains("publish")) {
                desc = "用于创建新的穿搭笔记并发布到社区。";
            } else if (route.contains("profile") || route.contains("user")) {
                desc = "展示创作者身份、内容资产与粉丝关系。";
            } else if (route.contains("discover")) {
                desc = "支持搜索、话题筛选与趋势浏览，帮助用户定位感兴趣内容。";
            } else if (route.contains("following")) {
                desc = "展示用户已关注创作者发布的最新穿搭内容。";
            } else {
                desc = description;
            }
            int splitIndex = description.indexOf('，');
            if ((route.isBlank() || (!route.contains("detail") && !route.contains("post") && !route.contains("publish")
                    && !route.contains("profile") && !route.contains("user") && !route.contains("discover") && !route.contains("following")))
                    && splitIndex > 0 && splitIndex < description.length() - 1) {
                desc = description.substring(splitIndex + 1).trim();
            }
            bullets.add(PrototypeBundle.ScreenBullet.builder()
                    .id(route.replaceAll("[^a-z0-9]+", "-"))
                    .label(label)
                    .description(desc)
                    .build());
        }
        manifest.getPrototypeBundle().getExperienceBrief().setScreenBullets(
                bullets.stream().collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toMap(PrototypeBundle.ScreenBullet::getLabel, bullet -> bullet, (left, right) -> left, java.util.LinkedHashMap::new),
                        map -> new java.util.ArrayList<>(map.values())
                ))
        );
    }

    private boolean shouldRefreshPrototypeDisplay(ProjectManifest manifest) {
        if (manifest == null || manifest.getPrototypeHtml() == null || manifest.getPrototypeHtml().isBlank()) {
            return false;
        }
        String source = ((manifest.getUserIntent() == null ? "" : manifest.getUserIntent()) + " "
                + (manifest.getArchetype() == null ? "" : manifest.getArchetype())).toLowerCase();
        if (!(source.contains("小红书") || source.contains("穿搭") || source.contains("ootd") || source.contains("搭配"))) {
            return false;
        }
        String htmlLower = manifest.getPrototypeHtml().toLowerCase();
        return htmlLower.contains("共读")
                || htmlLower.contains("learning")
                || htmlLower.contains("今天值得继续逛的穿搭内容")
                || htmlLower.contains("穿搭内容瀑布流首页")
                || htmlLower.contains("utility publishing");
    }

    private void ensureDesignInputs(ProjectManifest manifest) {
        if (manifest.getMetaData() == null) {
            manifest.setMetaData(new HashMap<>());
        }
        boolean dataReady = "true".equalsIgnoreCase(manifest.getMetaData().getOrDefault("data_ready", "false"));
        boolean visualReady = "true".equalsIgnoreCase(manifest.getMetaData().getOrDefault("visual_ready", "false"));

        if (!dataReady || manifest.getMockData() == null || manifest.getMockData().isBlank()) {
            dataEngineerAgent.generateData(manifest);
            manifest.getMetaData().put("data_ready", "true");
        } else {
            dataEngineerAgent.normalizeExistingData(manifest);
        }

        if (!visualReady || manifest.getMetaData().get("visual_reasoning") == null) {
            visualDNAAgent.synthesize(manifest);
            manifest.getMetaData().put("visual_ready", "true");
        }

        prototypeBundleCompiler.compile(manifest);
    }

    private boolean shouldRebuildFromShape(ProjectManifest manifest, FunctionalAuditorAgent.AuditOutcome auditOutcome) {
        if (manifest == null || manifest.getDesignContract() == null || auditOutcome == null || auditOutcome.getBlockers() == null) {
            return false;
        }
        if ("CONTENT_FIRST".equalsIgnoreCase(manifest.getDesignContract().getContentMode()) && !auditOutcome.isPassed()) {
            return true;
        }
        if (manifest.getDesignContract().getLayoutRhythm() == ProjectManifest.LayoutRhythm.WATERFALL
                || manifest.getDesignContract().getLayoutRhythm() == ProjectManifest.LayoutRhythm.LIST
                || manifest.getDesignContract().getLayoutRhythm() == ProjectManifest.LayoutRhythm.THREAD) {
            return auditOutcome.getBlockers().stream().anyMatch(blocker -> {
                String lower = blocker.toLowerCase();
                return lower.contains("portal-like internal sidebars")
                        || lower.contains("escaped quote syntax")
                        || lower.contains("primary views rendered only")
                        || lower.contains("too sparse")
                        || lower.contains("raw user prompt")
                        || lower.contains("internal benchmark")
                        || lower.contains("technical knowledge homepage is leaking")
                        || lower.contains("visual discovery homepage is leaking")
                        || lower.contains("content-first homepage does not expose enough feed cards");
            });
        }
        return false;
    }

    private String incrementPatchVersion(String currentVersion) {
        String version = (currentVersion == null || currentVersion.isBlank()) ? "0.0.0" : currentVersion.trim();
        boolean hasPrefix = version.startsWith("v") || version.startsWith("V");
        String cleanVersion = hasPrefix ? version.substring(1) : version;
        String[] parts = cleanVersion.split("\\.");

        int major = parseVersionPart(parts, 0);
        int minor = parseVersionPart(parts, 1);
        int patch = parseVersionPart(parts, 2) + 1;

        String nextVersion = String.format("%d.%d.%d", major, minor, patch);
        return hasPrefix ? "v" + nextVersion : nextVersion;
    }

    private int parseVersionPart(String[] parts, int index) {
        if (parts.length <= index) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[index]);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private boolean shouldApplyMindMapOverride(ProjectManifest manifest, String overriddenMindMap) {
        if (overriddenMindMap == null || overriddenMindMap.isBlank()) {
            return false;
        }

        String trimmed = overriddenMindMap.trim();
        if (trimmed.length() < 8) {
            return false;
        }

        long overrideLineCount = trimmed.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .count();

        long primaryPageCount = manifest.getPages() == null
                ? 0
                : manifest.getPages().stream()
                .filter(page -> "PRIMARY".equals(page.getNavRole()))
                .count();

        if (primaryPageCount > 1 && overrideLineCount < Math.min(primaryPageCount, 2)) {
            return false;
        }

        return true;
    }
}
