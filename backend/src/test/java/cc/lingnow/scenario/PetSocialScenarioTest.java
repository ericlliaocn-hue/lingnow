package cc.lingnow.scenario;

import cc.lingnow.dto.GenerateResponse;
import cc.lingnow.model.ProjectManifest;
import cc.lingnow.service.GenerationService;
import cc.lingnow.service.ManifestRegistry;
import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
public class PetSocialScenarioTest {

    @Autowired
    private GenerationService generationService;

    @Autowired
    private ManifestRegistry manifestRegistry;

    @Test
    public void runPetSocialScenario() {
        // Mock Login for Sa-Token
        StpUtil.login("test-user");
        
        String sessionId = "scenario-1-pet-social-" + System.currentTimeMillis();
        String prompt = "我想做个宠物社交 APP，用户可以晒自家宠物，点赞评论";

        log.info("Starting Scenario 1: Pet Social APP");

        // Phase 1: Planning
        log.info("--- Phase 1: Planning ---");
        ProjectManifest plannedManifest = generationService.planRequirements(sessionId, prompt, "EN");
        assertNotNull(plannedManifest);
        assertFalse(plannedManifest.getFeatures().isEmpty(), "Should have generated features");
        log.info("Planned features: {}", plannedManifest.getFeatures().size());

        // Phase 2: Designing
        log.info("--- Phase 2: Designing ---");
        ProjectManifest designedManifest = generationService.generatePrototype(sessionId, "EN");
        assertNotNull(designedManifest.getPrototypeHtml(), "Should have generated prototype HTML");
        log.info("Prototype length: {}", designedManifest.getPrototypeHtml().length());

        // Phase 3: Coding
        log.info("--- Phase 3: Coding ---");
        GenerateResponse response = generationService.developFullStack(sessionId);
        assertNotNull(response);
        assertFalse(response.files().isEmpty(), "Should have generated code files");
        
        log.info("Generation complete for session: {}", sessionId);
        log.info("Total files generated: {}", response.files().size());
        
        // --- Added: Export to physical directory for isolation ---
        String exportRoot = "/Users/eric/workspace/lingnow-generated/pet-social";
        log.info("Exporting generated files to: {}", exportRoot);
        
        response.files().forEach((path, content) -> {
            try {
                java.nio.file.Path targetPath = java.nio.file.Paths.get(exportRoot, path);
                java.nio.file.Files.createDirectories(targetPath.getParent());
                java.nio.file.Files.writeString(targetPath, content);
                log.info("File exported: {}", path);
            } catch (Exception e) {
                log.error("Failed to export file: {}", path, e);
            }
        });
        // -----------------------------------------------------------

        // Final check from registry
        ProjectManifest finalManifest = manifestRegistry.get(sessionId);
        assertEquals(ProjectManifest.ProjectStatus.DONE, finalManifest.getStatus());
    }
}
