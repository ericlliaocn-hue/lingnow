package cc.lingnow.controller;

import cc.lingnow.dto.GenerateRequest;
import cc.lingnow.dto.GenerateResponse;
import cc.lingnow.service.GenerationService;
import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Generation Controller
 * Handle code generation requests
 * LingNow.cc - AI Powered Code Generator
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
    public ResponseEntity<cc.lingnow.model.ProjectManifest> handlePlan(@RequestBody GenerateRequest request) {
        log.info("Received planning request for session: {}", request.sessionId());
        try {
            // StpUtil.checkLogin();
            return ResponseEntity.ok(generationService.planRequirements(request.sessionId(), request.prompt()));
        } catch (Exception e) {
            log.error("Planning failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Phase 2: Designing
     */
    @PostMapping("/generate/design")
    public ResponseEntity<cc.lingnow.model.ProjectManifest> handleDesign(@RequestBody Map<String, String> body) {
        String sessionId = body.get("sessionId");
        log.info("Received design request for session: {}", sessionId);
        try {
            // StpUtil.checkLogin();
            return ResponseEntity.ok(generationService.generatePrototype(sessionId));
        } catch (Exception e) {
            log.error("Design failed", e);
            return ResponseEntity.internalServerError().build();
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
            // StpUtil.checkLogin();
            return ResponseEntity.ok(generationService.developFullStack(sessionId));
        } catch (Exception e) {
            log.error("Development failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Orchestrated generation (Legacy/All-in-one)
     */
    @PostMapping("/generate")
    public ResponseEntity<GenerateResponse> handleGenerate(@RequestBody GenerateRequest request) {
        log.info("Received generation request for session: {}", request.sessionId());

        try {
            // Get current user from Sa-Token (M8.5)
            String userId = StpUtil.getLoginIdAsString();
            log.debug("Generation triggered by user ID: {}", userId);

            // Call the service to generate code using LLM
            GenerateResponse response = generationService.generateCode(request);
            
            log.info("Successfully generated code for session: {}", request.sessionId());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error generating code for session: {}", request.sessionId(), e);
            
            // Return error response
            GenerateResponse errorResponse = new GenerateResponse(
                "Generation Error",
                "Failed to generate code: " + e.getMessage(),
                Map.of(),
                Map.of(),
                null
            );
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    @GetMapping("/projects/{sessionId}")
    public ResponseEntity<cc.lingnow.model.ProjectManifest> getProject(@PathVariable String sessionId) {
        log.info("Fetching project details for session: {}", sessionId);
        return ResponseEntity.ok(generationService.planRequirements(sessionId, "")); // 简单复用逻辑获取 Manifest
    }
}
