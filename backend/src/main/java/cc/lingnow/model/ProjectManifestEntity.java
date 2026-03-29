package cc.lingnow.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;

/**
 * Persistable Project Manifest for M8
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
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

    @Column(columnDefinition = "LONGTEXT")
    private String prototypeHtml;

    @Column(columnDefinition = "LONGTEXT")
    private String apiSchema;

    @Column(columnDefinition = "LONGTEXT")
    private String databaseSchema;

    private String version;

    @Column(columnDefinition = "LONGTEXT")
    private String snapshotsJson;

    @Column(columnDefinition = "TEXT")
    private String changeLogJson;

    @Column(columnDefinition = "LONGTEXT")
    private String mindMap; // Text tree for mindmap

    @Column(columnDefinition = "LONGTEXT")
    private String mockData; // Captured JSON mock data

    @Column(columnDefinition = "TEXT")
    private String dependenciesJson;

    @Column(columnDefinition = "TEXT")
    private String metaDataJson;

    @Column(columnDefinition = "TEXT")
    private String techStackJson;

    private String owner; // Username for isolation

    @CreatedDate
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;

    @LastModifiedDate
    @Temporal(TemporalType.TIMESTAMP)
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
