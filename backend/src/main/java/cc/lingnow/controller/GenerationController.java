package cc.lingnow.controller;

import cc.lingnow.dto.GenerateRequest;
import cc.lingnow.dto.GenerateResponse;
import cc.lingnow.dto.ProjectHistoryDto;
import cc.lingnow.model.ProjectManifest;
import cc.lingnow.service.GenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Generation Controller
 * Handle code generation and prototype evolution requests
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GenerationController {

    private final GenerationService generationService;

    /**
     * Phase 1: Planning
     */
    @PostMapping("/generate/plan")
    public ResponseEntity<ProjectManifest> handlePlan(@RequestBody GenerateRequest request) {
        log.info("Received planning request for session: {}", request.sessionId());
        try {
            return ResponseEntity.ok(generationService.planRequirements(request.sessionId(), request.prompt(), request.lang()));
        } catch (Exception e) {
            log.error("Planning failed", e);
            return ResponseEntity.status(500).header("X-Error-Phase", "PLANNING").header("X-Error-Message", e.getMessage()).build();
        }
    }

    /**
     * Phase 2: Designing
     */
    @PostMapping("/generate/design")
    public ResponseEntity<ProjectManifest> handleDesign(@RequestBody Map<String, String> body) {
        String sessionId = body.get("sessionId");
        String lang = body.getOrDefault("lang", "EN");
        log.info("Received design request for session: {} (lang: {})", sessionId, lang);
        try {
            return ResponseEntity.ok(generationService.generatePrototype(sessionId, lang));
        } catch (Exception e) {
            log.error("Design failed", e);
            return ResponseEntity.status(500).header("X-Error-Phase", "DESIGNING").header("X-Error-Message", e.getMessage()).build();
        }
    }

    /**
     * M6: Iterative Redesign
     */
    @PostMapping("/generate/redesign")
    public ResponseEntity<ProjectManifest> handleRedesign(@RequestBody Map<String, String> body) {
        String sessionId = body.get("sessionId");
        String instructions = body.get("prompt");
        String lang = body.getOrDefault("lang", "EN");
        log.info("Received redesign request for session: {} with lang: {}", sessionId, lang);
        try {
            return ResponseEntity.ok(generationService.redesignPrototype(sessionId, instructions, lang));
        } catch (Exception e) {
            log.error("Redesign failed", e);
            return ResponseEntity.status(500).header("X-Error-Phase", "REDESIGN").header("X-Error-Message", e.getMessage()).build();
        }
    }

    /**
     * Phase 3: Developing
     */
    @PostMapping("/generate/develop")
    public ResponseEntity<GenerateResponse> handleDevelop(@RequestBody Map<String, String> body) {
        String sessionId = body.get("sessionId");
        log.info("Received development request for session: {}", sessionId);
        try {
            return ResponseEntity.ok(generationService.developFullStack(sessionId));
        } catch (Exception e) {
            log.error("Development failed", e);
            return ResponseEntity.status(500).header("X-Error-Phase", "DEVELOPING").header("X-Error-Message", e.getMessage()).build();
        }
    }

    /**
     * Legacy Orchestrator
     */
    @PostMapping("/generate")
    public ResponseEntity<GenerateResponse> handleGenerate(@RequestBody GenerateRequest request) {
        log.info("Orchestrated generation for: {}", request.sessionId());
        try {
            return ResponseEntity.ok(generationService.generateCode(request));
        } catch (Exception e) {
            log.error("Generation failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/generate/snapshot")
    public ResponseEntity<ProjectManifest> handleSnapshot(@RequestBody Map<String, String> body) {
        String sessionId = body.get("sessionId");
        String html = body.get("html");
        String summary = body.get("summary");
        log.info("Saving snapshot for session: {} - {}", sessionId, summary);
        try {
            return ResponseEntity.ok(generationService.saveSnapshot(sessionId, html, summary));
        } catch (Exception e) {
            log.error("Snapshot failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/generate/rollback")
    public ResponseEntity<ProjectManifest> handleRollback(@RequestBody Map<String, String> body) {
        String sessionId = body.get("sessionId");
        String targetVersion = body.get("version");
        log.info("Rolling back session: {} to version: {}", sessionId, targetVersion);
        try {
            return ResponseEntity.ok(generationService.rollbackToVersion(sessionId, targetVersion));
        } catch (Exception e) {
            log.error("Rollback failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/projects/{sessionId}")
    public ResponseEntity<ProjectManifest> getProject(@PathVariable String sessionId) {
        log.info("Fetching project details for session: {}", sessionId);
        ProjectManifest manifest = generationService.getManifest(sessionId);
        if (manifest == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(manifest);
    }

    /**
     * Get all persistent projects for history drawer
     */
    @GetMapping("/projects/all")
    public ResponseEntity<List<ProjectHistoryDto>> listAllProjects() {
        log.info("Fetching all projects for current user history");
        return ResponseEntity.ok(generationService.getHistory());
    }
}
