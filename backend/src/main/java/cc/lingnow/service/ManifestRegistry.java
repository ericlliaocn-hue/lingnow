package cc.lingnow.service;

import cc.lingnow.model.ProjectManifest;
import cc.lingnow.model.ProjectManifestEntity;
import cc.lingnow.repository.ManifestRepository;
import cc.lingnow.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

/**
 * DB backed registry for Project Manifests.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ManifestRegistry {

    private final ManifestRepository repository;

    public void save(ProjectManifest manifest) {
        ProjectManifestEntity entity = toEntity(manifest);
        repository.save(entity);
        log.debug("Manifest saved to DB: {}", manifest.getId());
    }

    public ProjectManifest get(String id) {
        return repository.findById(id)
                .map(this::toManifest)
                .orElse(null);
    }

    public ProjectManifest getOrCreate(String id, String userIntent) {
        return repository.findById(id)
                .map(this::toManifest)
                .orElseGet(() -> {
                    ProjectManifest m = ProjectManifest.builder()
                            .id(id)
                            .userIntent(userIntent)
                            .status(ProjectManifest.ProjectStatus.PLANNING)
                            .version("v1.0.0")
                            .changeLog(new ArrayList<>())
                            .build();
                    save(m);
                    return m;
                });
    }

    // -- Helpers --
    private ProjectManifestEntity toEntity(ProjectManifest m) {
        String username = StpUtil.getLoginIdAsString();
        return ProjectManifestEntity.builder()
                .id(m.getId())
                .userIntent(m.getUserIntent())
                .status(m.getStatus())
                .prototypeHtml(m.getPrototypeHtml())
                .apiSchema(m.getApiSchema())
                .databaseSchema(m.getDatabaseSchema())
                .version(m.getVersion())
                .featuresJson(JsonUtils.toJson(m.getFeatures()))
                .pagesJson(JsonUtils.toJson(m.getPages()))
                .generatedFilesJson(JsonUtils.toJson(m.getGeneratedFiles()))
                .changeLogJson(JsonUtils.toJson(m.getChangeLog()))
                .owner(username) 
                .build();
    }

    private ProjectManifest toManifest(ProjectManifestEntity e) {
        return ProjectManifest.builder()
                .id(e.getId())
                .userIntent(e.getUserIntent())
                .status(e.getStatus())
                .prototypeHtml(e.getPrototypeHtml())
                .apiSchema(e.getApiSchema())
                .databaseSchema(e.getDatabaseSchema())
                .version(e.getVersion())
                .features(JsonUtils.fromJson(e.getFeaturesJson(), new TypeReference<>() {}))
                .pages(JsonUtils.fromJson(e.getPagesJson(), new TypeReference<>() {}))
                .generatedFiles(JsonUtils.fromJson(e.getGeneratedFilesJson(), new TypeReference<>() {}))
                .changeLog(JsonUtils.fromJson(e.getChangeLogJson(), new TypeReference<>() {}))
                .build();
    }
}
