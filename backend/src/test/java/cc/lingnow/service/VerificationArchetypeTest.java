package cc.lingnow.service;

import cc.lingnow.model.ProjectManifest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class VerificationArchetypeTest {

    @Autowired
    private GenerationService generationService;

    @Test
    public void verifyReaderVsDashboard() throws Exception {
        // Case 1: READER (Content-heavy)
        String readerIntent = "精读会深夜共读笔记社区";
        String readerSession = "verify-reader-5.0";
        ProjectManifest readerManifest = generationService.planRequirements(readerSession, readerIntent, "ZH");
        readerManifest = generationService.generatePrototype(readerSession, "ZH", null);

        System.out.println(">>> [READER Verification] Intent: " + readerIntent);
        System.out.println("Archetype: " + readerManifest.getArchetype());
        System.out.println("DNA Reasoning: " + readerManifest.getMetaData().get("visual_reasoning"));

        String readerHtml = readerManifest.getPrototypeHtml();
        Files.writeString(Path.of("target", "verify-reader.html"), readerHtml, StandardCharsets.UTF_8);

        // Verification for READER
        assertEquals("READER", readerManifest.getArchetype());
        assertTrue(readerHtml.contains("font-serif"), "READER should contain font-serif");
        assertTrue(readerHtml.contains("max-w-2xl"), "READER should contain max-w-2xl");
        assertFalse(readerHtml.contains("drawerOpen = true"), "READER should NOT contain hardcoded drawerOpen = true in components");

        // Case 2: DASHBOARD (Action-heavy)
        String dashboardIntent = "全自动智能咖啡机运维屏";
        String dashboardSession = "verify-dashboard-5.0";
        ProjectManifest dashboardManifest = generationService.planRequirements(dashboardSession, dashboardIntent, "ZH");
        dashboardManifest = generationService.generatePrototype(dashboardSession, "ZH", null);

        System.out.println(">>> [DASHBOARD Verification] Intent: " + dashboardIntent);
        System.out.println("Archetype: " + dashboardManifest.getArchetype());

        String dashboardHtml = dashboardManifest.getPrototypeHtml();
        Files.writeString(Path.of("target", "verify-dashboard.html"), dashboardHtml, StandardCharsets.UTF_8);

        // Verification for DASHBOARD
        assertEquals("DASHBOARD", dashboardManifest.getArchetype());
        assertTrue(dashboardHtml.contains("drawerOpen = true"), "DASHBOARD should contain drawerOpen interaction");
        assertTrue(dashboardHtml.contains("backdrop-blur"), "DASHBOARD should likely contain backdrop-blur (Glassmorphism)");

        // Case 3: LingNow 6.1 - Automotive Experience (TOP_NAV + Full-screen Sections)
        String carIntent = "高端纯电跑车 brand 官网";
        String carSession = "verify-car-6.1";
        ProjectManifest carManifest = generationService.planRequirements(carSession, carIntent, "ZH");
        carManifest = generationService.generatePrototype(carSession, "ZH", null);

        System.out.println(">>> [CAR Verification] Intent: " + carIntent);
        System.out.println("Nav Pattern: " + carManifest.getUxStrategy().get("nav_pattern"));

        String carHtml = carManifest.getPrototypeHtml();
        Files.writeString(Path.of("target", "verify-car.html"), carHtml, StandardCharsets.UTF_8);

        // Verification for CAR (Strategy-based)
        if ("TOP_NAV".equals(carManifest.getUxStrategy().get("nav_pattern"))) {
            assertTrue(carHtml.contains("sticky") || carHtml.contains("fixed top-0"), "Car site should have top nav");
        }
        assertFalse(carHtml.contains("Article Detail"), "Car site should not have generic blog detail names in sidebar");
    }

    @Test
    public void verifyCommunity70() throws Exception {
        // Case 4: LingNow 7.0 - Community/Social discovery (High Density + Modal Details)
        String communityIntent = "类小红书的时尚穿搭社区";
        String session = "verify-social-7.0";
        ProjectManifest manifest = generationService.planRequirements(session, communityIntent, "ZH");

        // Assert PM Logic: Pages like 'Post Note' or 'Detail' should be LEAF_DETAIL or special actions
        // assertTrue(manifest.getPages().stream().anyMatch(p -> "LEAF_DETAIL".equals(p.getNavType())), 
        //    "Social community should have LEAF_DETAIL nodes.");

        manifest = generationService.generatePrototype(session, "ZH", null);
        String html = manifest.getPrototypeHtml();

        // Ensure direct file output for delivery
        Files.writeString(Path.of("target", "verify-social-7.0.html"), html, StandardCharsets.UTF_8);

        System.out.println(">>> [SOCIAL 7.0 Verification] Intent: " + communityIntent);
        System.out.println("Shell Pattern: " + manifest.getUxStrategy().get("shell_pattern"));
        System.out.println("Essentials: " + manifest.getUxStrategy().get("essential_modules"));
    }
}
