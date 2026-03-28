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
    private final ProductArchitectAgent architectAgent;
    private final UiDesignerAgent designerAgent;
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

        architectAgent.analyze(manifest);
        manifestRegistry.save(manifest);
        
        return manifest;
    }

    /**
     * Phase 2: Designing - UI Designer creates prototype
     */
    public ProjectManifest generatePrototype(String sessionId, String lang) {
        log.info("Phase 2: Designing for session: {} (lang: {})", sessionId, lang);
        ProjectManifest manifest = manifestRegistry.get(sessionId);
        if (manifest == null) throw new RuntimeException("Manifest not found for session: " + sessionId);

        if (lang != null) {
            if (manifest.getMetaData() == null) manifest.setMetaData(new HashMap<>());
            manifest.getMetaData().put("lang", lang);
        }

        manifest.setStatus(ProjectManifest.ProjectStatus.DESIGNING);
        manifestRegistry.save(manifest);

        designerAgent.design(manifest);

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

        designerAgent.redesign(manifest, instructions);
        createSnapshot(manifest, "AI Revision: " + instructions);

        manifestRegistry.save(manifest);
        return manifest;
    }

    public ProjectManifest saveSnapshot(String sessionId, String html, String summary) {
        ProjectManifest manifest = manifestRegistry.get(sessionId);
        if (manifest == null) throw new RuntimeException("Manifest not found");

        manifest.setPrototypeHtml(html);
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
        
        manifestRegistry.save(manifest);
        return manifest;
    }

    private void createSnapshot(ProjectManifest manifest, String summary) {
        if (manifest.getSnapshots() == null) manifest.setSnapshots(new ArrayList<>());

        // Version increment logic: handle both "0.0.1" and "v1.0.0"
        String current = manifest.getVersion();
        if (current == null) current = "0.0.0";

        String cleanVersion = current.startsWith("v") ? current.substring(1) : current;
        String[] parts = cleanVersion.split("\\.");

        String nextVersion;
        if (parts.length >= 3) {
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            int patch = Integer.parseInt(parts[2]) + 1;
            nextVersion = String.format("%d.%d.%d", major, minor, patch);
        } else {
            nextVersion = cleanVersion + ".1";
        }

        if (current.startsWith("v")) nextVersion = "v" + nextVersion;

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
            String current = manifest.getVersion().substring(1);
            String[] parts = current.split("\\.");
            int patch = Integer.parseInt(parts[2]) + 1;
            manifest.setVersion("v" + parts[0] + "." + parts[1] + "." + patch);
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
            generatePrototype(request.sessionId(), request.lang());
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
}