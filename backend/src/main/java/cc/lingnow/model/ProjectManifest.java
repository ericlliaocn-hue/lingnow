package cc.lingnow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Project Manifest - The Single Source of Truth for the whole generation process.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectManifest {
    private String id;
    private String userIntent;
    private ProjectStatus status;
    private long lastModified;
    
    // PLANNING phase products
    private List<Feature> features;
    private List<PageSpec> pages;
    private String mindMap;        // Mermaid diagram
    private String mockData;       // Simulated JSON data
    private Map<String, String> techStack;
    
    // DESIGNING phase products
    private String prototypeHtml;
    
    // CODING phase products
    private Map<String, String> generatedFiles;
    private Map<String, String> dependencies;
    
    // M5: Parallel Dev Artifacts
    private String apiSchema;      // OpenAPI/Swagger
    private String databaseSchema; // SQL/DDL
    
    // M6: Iteration & Tracking
    @Builder.Default
    private String version = "0.0.1"; // v0.0.1 format
    private List<String> changeLog; // Evolution history
    private List<Snapshot> snapshots; // Version history snapshots

    @Data
    @Builder
    public static class Snapshot {
        private String version;
        private String html;
        private long timestamp;
        private String summary;
    }
    
    // M8/v1.4: Context & Meta
    private Map<String, String> metaData; 

    // Deployment (New for M7)
    private String deploymentConfig;

    public enum ProjectStatus {
        PLANNING,   // Architect is analyzing requirements
        DESIGNING,  // UI Designer is creating prototypes
        CODING,     // Developers are writing code
        QA,         // Quality checks & Validation
        DEPLOYING,  // Generating Docker/Readme
        DONE,        // Finished
        ERROR       // Something went wrong
    }

    @Data
    @Builder
    public static class Feature {
        private String name;
        private String description;
        private Priority priority;
        
        public enum Priority { HIGH, MEDIUM, LOW }
    }

    @Data
    @Builder
    public static class PageSpec {
        private String route;
        private String description;
        private List<String> components;
    }

    @Data
    @Builder
    public static class ChatMessage {
        private String role;
        private String content;
        private long timestamp;
    }
}
