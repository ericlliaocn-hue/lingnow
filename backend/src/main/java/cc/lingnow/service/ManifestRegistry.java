package cc.lingnow.service;

import cc.lingnow.dto.ProjectHistoryDto;
import cc.lingnow.model.ProjectManifest;
import cc.lingnow.model.ProjectManifestEntity;
import cc.lingnow.repository.ManifestRepository;
import cc.lingnow.util.JsonUtils;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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

    public List<ProjectHistoryDto> listHistory() {
        String owner = "guest";
        try {
            if (StpUtil.isLogin()) {
                owner = StpUtil.getLoginIdAsString();
            }
        } catch (Exception e) {}

        return repository.findByOwnerOrderByUpdatedAtDesc(owner).stream()
                .map(this::toHistoryDto)
                .toList();
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
        String username = "guest";
        try {
            if (StpUtil.isLogin()) {
                username = StpUtil.getLoginIdAsString();
            }
        } catch (Exception e) {
            log.warn("Failed to get login user, defaulting to guest");
        }
        
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
                .dependenciesJson(JsonUtils.toJson(m.getDependencies()))
                .changeLogJson(JsonUtils.toJson(m.getChangeLog()))
                .snapshotsJson(JsonUtils.toJson(m.getSnapshots()))
                .metaDataJson(JsonUtils.toJson(m.getMetaData()))
                .techStackJson(JsonUtils.toJson(m.getTechStack()))
                .mindMap(m.getMindMap())
                .taskFlowsJson(JsonUtils.toJson(m.getTaskFlows()))
                .designContractJson(JsonUtils.toJson(m.getDesignContract()))
                .mockData(m.getMockData())
                .uxStrategyJson(JsonUtils.toJson(m.getUxStrategy()))
                .archetype(m.getArchetype())
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
                .dependencies(JsonUtils.fromJson(e.getDependenciesJson(), new TypeReference<>() {
                }))
                .changeLog(JsonUtils.fromJson(e.getChangeLogJson(), new TypeReference<>() {}))
                .snapshots(JsonUtils.fromJson(e.getSnapshotsJson(), new TypeReference<>() {
                }))
                .metaData(JsonUtils.fromJson(e.getMetaDataJson(), new TypeReference<>() {
                }))
                .techStack(JsonUtils.fromJson(e.getTechStackJson(), new TypeReference<>() {
                }))
                .mindMap(e.getMindMap())
                .taskFlows(JsonUtils.fromJson(e.getTaskFlowsJson(), new TypeReference<>() {
                }))
                .designContract(JsonUtils.fromJson(e.getDesignContractJson(), new TypeReference<>() {
                }))
                .mockData(e.getMockData())
                .uxStrategy(JsonUtils.fromJson(e.getUxStrategyJson(), new TypeReference<>() {
                }))
                .archetype(e.getArchetype())
                .lastModified(e.getUpdatedAt() != null ? e.getUpdatedAt().getTime() : 0)
                .build();
    }

    private ProjectHistoryDto toHistoryDto(ProjectManifestEntity e) {
        return ProjectHistoryDto.builder()
                .id(e.getId())
                .userIntent(e.getUserIntent())
                .status(e.getStatus())
                .createdAt(e.getCreatedAt())
                .lastModified(e.getUpdatedAt() != null ? e.getUpdatedAt().getTime() : 0)
                .version(e.getVersion())
                .build();
    }
}
