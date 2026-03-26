package cc.lingnow.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.*;

/**
 * Persistable Project Manifest for M8
 */
@Entity
@Table(name = "projects")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectManifestEntity {

    @Id
    private String id;

    @Column(columnDefinition = "TEXT")
    private String userIntent;

    @Enumerated(EnumType.STRING)
    private ProjectManifest.ProjectStatus status;

    @Column(columnDefinition = "LONGTEXT")
    private String featuresJson;

    @Column(columnDefinition = "LONGTEXT")
    private String pagesJson;

    @Column(columnDefinition = "LONGTEXT")
    private String generatedFilesJson;

    @Column(columnDefinition = "TEXT")
    private String prototypeHtml;

    @Column(columnDefinition = "TEXT")
    private String apiSchema;

    @Column(columnDefinition = "TEXT")
    private String databaseSchema;

    private String version;

    @Column(columnDefinition = "TEXT")
    private String changeLogJson;

    private String owner; // Username for isolation

    private Date createdAt;
    private Date updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = new Date();
        updatedAt = new Date();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = new Date();
    }
}
