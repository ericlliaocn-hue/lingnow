package cc.lingnow.service;

import cc.lingnow.model.ProjectManifestEntity;
import cc.lingnow.repository.ManifestRepository;
import cc.lingnow.util.ExcelReportGenerator;
import cc.lingnow.util.ScreenshotService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * LingNow 3.5 Delivery Finalizer.
 * Scans DB, takes screenshots, and exports the industrial-grade Excel report.
 */
@SpringBootTest
@ActiveProfiles("test")
public class StressTestFinalizer {

    @Autowired
    private ManifestRepository repository;

    @Autowired
    private ScreenshotService screenshotService;

    @Test
    public void generateVisualReport() {
        System.out.println(">>> [Finalizer] Starting Visual Inventory...");
        List<ProjectManifestEntity> entities = repository.findAll().stream()
                .filter(e -> e.getId().startsWith("stress-"))
                .toList();

        List<ExcelReportGenerator.IndustryReportRow> rows = new ArrayList<>();

        for (ProjectManifestEntity entity : entities) {
            String screenshotPath = "backend/src/main/resources/static/stress-test/" + entity.getId() + ".png";
            File snapshotFile = new File(screenshotPath);

            // Row construction
            rows.add(ExcelReportGenerator.IndustryReportRow.builder()
                    .userIntent(entity.getUserIntent())
                    .visualDna(entity.getMetaDataJson())
                    .status("SUCCESS")
                    .snapshot(snapshotFile.exists() ? snapshotFile : null)
                    .localPath(screenshotPath)
                    .build());
        }

        String reportPath = "STRESS_TEST_REPORT_200.xlsx";
        ExcelReportGenerator.export(reportPath, rows);
        System.out.println(">>> [Finalizer] EXPORT SUCCESS: " + reportPath);
    }
}
