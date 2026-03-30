package cc.lingnow.util;

import cc.lingnow.model.ProjectManifestEntity;
import cc.lingnow.repository.ManifestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.stream.Collectors;

@SpringBootTest
@ActiveProfiles("test")
public class DbCleanupTest {

    @Autowired
    private ManifestRepository repository;

    @Test
    public void cleanupJunk() {
        List<ProjectManifestEntity> all = repository.findAll();
        List<ProjectManifestEntity> junk = all.stream()
                .filter(e -> {
                    String intent = e.getUserIntent() != null ? e.getUserIntent().toLowerCase() : "";
                    String id = e.getId() != null ? e.getId().toLowerCase() : "";
                    return intent.contains("pet") || intent.contains("social") ||
                            intent.contains("todo") || intent.contains("test app") ||
                            intent.contains("宠物") || intent.contains("社交") ||
                            intent.contains("待办") || intent.contains("测试") ||
                            id.contains("session-") || id.contains("scenario-") || id.contains("test-");
                })
                .collect(Collectors.toList());

        System.out.println(">>> Deleting " + junk.size() + " junk projects...");
        repository.deleteAll(junk);
        System.out.println(">>> Cleanup complete.");
    }
}
