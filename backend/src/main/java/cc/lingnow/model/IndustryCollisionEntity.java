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
 * Industry Collision Log - The "Wrong Question Book" for self-healing.
 * Tracks intents that triggered fallbacks or visual anomalies.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "industry_collision_log", indexes = {
        @Index(name = "idx_intent_hash", columnList = "intentHash")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndustryCollisionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String intentText;

    @Column(length = 64)
    private String intentHash; // Semantic hash for deduplication

    private String detectedIndustry; // What the engine thought it was

    @Column(columnDefinition = "TEXT")
    private String llmThemeSuggestions; // What the LLM suggested (if any)

    @Column(columnDefinition = "TEXT")
    private String errorDetail;

    @Builder.Default
    private Integer hitCount = 1;

    @Builder.Default
    private Boolean isEvolved = false; // Whether this has been fixed in code

    @CreatedDate
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;

    @LastModifiedDate
    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedAt;
}
