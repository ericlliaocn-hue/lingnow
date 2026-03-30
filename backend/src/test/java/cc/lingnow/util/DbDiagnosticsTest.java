package cc.lingnow.util;

import cc.lingnow.model.ProjectManifestEntity;
import cc.lingnow.repository.ManifestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

@SpringBootTest
@ActiveProfiles("test")
public class DbDiagnosticsTest {

    @Autowired
    private ManifestRepository repository;

    @Test
    public void checkDb() {
        List<ProjectManifestEntity> all = repository.findAll();
        System.out.println(">>> TOTAL PROJECTS IN DB: " + all.size());

        int realCount = 0;
        for (ProjectManifestEntity e : all) {
            String intent = e.getUserIntent() != null ? e.getUserIntent().toLowerCase() : "";
            if (!(intent.contains("pet") || intent.contains("social") || intent.contains("todo") || intent.contains("test") || intent.contains("ssion-"))) {
                realCount++;
                if (realCount <= 5) {
                    System.out.println(">>> [REAL SAMPLE] " + e.getUserIntent());
                }
            }
        }
        System.out.println(">>> TOTAL REAL INDUSTRIES: " + realCount);
    }
}
