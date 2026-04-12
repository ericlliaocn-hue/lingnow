package cc.lingnow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Structured intermediate artifact that bridges requirement understanding,
 * content planning, interaction flow design, and final prototype rendering.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrototypeBundle {

    private String version;
    private ProductIR productIr;
    private CapabilityLayer capabilityLayer;
    private SurfaceIR surfaceIr;
    private ExperienceBrief experienceBrief;
    private DesignSeed designSeed;
    private MockGraph mockGraph;
    private FlowGraph flowGraph;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductIR {
        private String domainSummary;
        private String primaryObject;
        private String primaryLoop;
        private String primaryMode;
        private String objectMultiplicity;
        private String workflowMode;
        private String timeModel;
        private String stateModel;
        private String collaborationMode;
        private String detailMode;
        private List<String> interactionModes;
        private List<String> evidenceSignals;
        private List<EntityModel> entities;
        private List<ActionModel> actions;
        private List<String> stateVocabulary;
        private List<String> roles;
        private List<String> constraints;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EntityModel {
        private String name;
        private String purpose;
        private List<String> sampleFields;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActionModel {
        private String name;
        private String targetEntity;
        private String intent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CapabilityLayer {
        private List<String> accountCapabilities;
        private List<String> engagementCapabilities;
        private List<String> publishingCapabilities;
        private List<String> navigationCapabilities;
        private List<String> stateCapabilities;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SurfaceIR {
        private String primarySurface;
        private String shellMode;
        private String navigationPattern;
        private String interactionDensity;
        private String contentPattern;
        private String layoutStrategy;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExperienceBrief {
        private String referenceSignal;
        private String intentSummary;
        private String introduction;
        private String screenPlanTitle;
        private List<ScreenBullet> screenBullets;
        private String nextStepNarrative;
        private String interactionModel;
        private String navigationStyle;
        private String contentRhythm;
        private String density;
        private String mediaEmphasis;
        private List<String> inferredTraits;
        private List<String> primaryLoopSteps;
        private List<String> executionPlan;
        private String whyThisStructure;
        private String rationale;
        private String confidenceNote;
        private List<ScreenPlan> screens;
        private VisualDirection visualDirection;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScreenPlan {
        private String id;
        private String title;
        private String role;
        private String layoutHint;
        private String contentFocus;
        private String layoutNarrative;
        private String actionLayout;
        private List<String> keyModules;
        private List<String> primaryActions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScreenBullet {
        private String id;
        private String label;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VisualDirection {
        private String tone;
        private String palette;
        private String typography;
        private String surfaces;
        private String controls;
        private String imagery;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DesignSeed {
        private String backgroundClass;
        private String cardClass;
        private String primaryColor;
        private String accentColor;
        private String fontFamily;
        private String lineHeight;
        private String letterSpacing;
        private String radiusStyle;
        private String toneReasoning;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MockGraph {
        private String primaryCollection;
        private int primaryRecordCount;
        private List<CollectionSummary> collections;
        private List<RelationSummary> relations;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CollectionSummary {
        private String name;
        private int recordCount;
        private List<String> sampleFields;
        private List<String> sampleTitles;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelationSummary {
        private String sourceCollection;
        private String sourceField;
        private String targetCollection;
        private String targetField;
        private String kind;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FlowGraph {
        private List<FlowSpec> flows;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FlowSpec {
        private String id;
        private String label;
        private String entryScreen;
        private String successSignal;
        private List<FlowStep> steps;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FlowStep {
        private int order;
        private String action;
        private String stateChange;
        private String targetScreen;
    }
}
