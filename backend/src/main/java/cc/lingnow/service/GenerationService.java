package cc.lingnow.service;

import cc.lingnow.dto.GenerateRequest;
import cc.lingnow.dto.GenerateResponse;
import cc.lingnow.dto.ProjectHistoryDto;
import cc.lingnow.model.ProjectManifest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        manifest.getMetaData().put("design_ready", "false");

        // Phase 1.5: Data Engineering & Visual DNA Synthesis
        dataEngineerAgent.generateData(manifest);
        visualDNAAgent.synthesize(manifest);
        
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

        if (overriddenMindMap != null && !overriddenMindMap.isEmpty()) {
            log.info("Applying frontend-driven mindmap override (length: {})", overriddenMindMap.length());
            manifest.setMindMap(overriddenMindMap);
        }
        manifestContractValidator.normalize(manifest);
        dataEngineerAgent.normalizeExistingData(manifest);
        manifest.getMetaData().put("design_ready", "false");

        manifest.setStatus(ProjectManifest.ProjectStatus.DESIGNING);
        manifestRegistry.save(manifest);

        designerAgent.design(manifest);
        runPrototypeQualityPass(manifest);
        manifest.setStatus(ProjectManifest.ProjectStatus.QA);

        // Initial snapshot after design
        createSnapshot(manifest, "Initial Design");
        
        manifestRegistry.save(manifest);
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
        dataEngineerAgent.normalizeExistingData(manifest);
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
        return manifestRegistry.get(sessionId);
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
            String repairedHtml = autoRepairAgent.checkAndFix(
                    manifest.getPrototypeHtml(),
                    manifest.getUserIntent(),
                    manifest.getMockData(),
                    auditResult
            );
            manifest.setPrototypeHtml(repairedHtml);
            auditOutcome = functionalAuditorAgent.verify(manifest);
            auditResult = auditOutcome.getSummary();
        }

        manifest.getMetaData().put("functional_audit_result", auditResult);
        manifest.getMetaData().put("design_ready", String.valueOf(auditOutcome.isPassed()));
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
}
