package cc.lingnow.util;

import cc.lingnow.model.ProjectManifestEntity;
import cc.lingnow.repository.ManifestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * High-Fidelity Screenshot Automation Service.
 * Orchestrates the browser agent to capture 200 industry prototypes.
 */
@Service
public class ScreenshotService {

    private static final String STORAGE_DIR = "backend/src/main/resources/static/stress-test/";
    @Autowired
    private ManifestRepository repository;

    /**
     * Finds all stress-test projects and prepares them for snapshotting.
     *
     * @return List of entities to be screenshotted
     */
    public List<ProjectManifestEntity> getPendingSnapshots() {
        return repository.findAll().stream()
                .filter(e -> e.getId().startsWith("stress-") && e.getSnapshotPath() == null)
                .toList();
    }

    /**
     * Updates the entity with the locally saved snapshot path.
     *
     * @param id   Project ID
     * @param path Local file path
     */
    public void markAsSnapshotted(String id, String path) {
        repository.findById(id).ifPresent(e -> {
            e.setSnapshotPath(path);
            repository.save(e);
        });
    }

    public String getUrlForProject(String id) {
        // Base URL for local development
        return "http://localhost:3000/workbench/" + id;
    }
}
