package cc.lingnow.service;

import cc.lingnow.model.ProjectManifest;
import cc.lingnow.model.PrototypeBundle;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Compiles the current manifest into a reusable intermediate artifact so
 * planning, visual design, mock content, and interaction logic share the same
 * structured understanding.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrototypeBundleCompiler {

    private final ObjectMapper objectMapper;

    public PrototypeBundle compile(ProjectManifest manifest) {
        if (manifest == null) {
            return null;
        }

        PrototypeBundle bundle = PrototypeBundle.builder()
                .version("bundle-v0.1")
                .productIr(buildProductIr(manifest))
                .capabilityLayer(buildCapabilityLayer(manifest))
                .surfaceIr(buildSurfaceIr(manifest))
                .experienceBrief(buildExperienceBrief(manifest))
                .designSeed(buildDesignSeed(manifest))
                .mockGraph(buildMockGraph(manifest))
                .flowGraph(buildFlowGraph(manifest))
                .build();

        manifest.setPrototypeBundle(bundle);
        ensureMeta(manifest).put("bundle_ready", "true");
        ensureMeta(manifest).put("bundle_primary_object", safe(bundle.getProductIr() != null ? bundle.getProductIr().getPrimaryObject() : null));
        ensureMeta(manifest).put("bundle_interaction_model", safe(bundle.getExperienceBrief() != null ? bundle.getExperienceBrief().getInteractionModel() : null));
        ensureMeta(manifest).put("bundle_primary_loop", safe(bundle.getProductIr() != null ? bundle.getProductIr().getPrimaryLoop() : null));
        ensureMeta(manifest).put("bundle_shell_mode", safe(bundle.getSurfaceIr() != null ? bundle.getSurfaceIr().getShellMode() : null));
        return bundle;
    }

    private PrototypeBundle.ProductIR buildProductIr(ProjectManifest manifest) {
        DomainSignals domain = detectDomainSignals(manifest);
        StructuralProfile profile = inferStructuralProfile(manifest, domain);
        List<PrototypeBundle.EntityModel> entities = buildEntities(domain);
        List<PrototypeBundle.ActionModel> actions = buildActions(domain);

        return PrototypeBundle.ProductIR.builder()
                .domainSummary(domain.domainSummary())
                .primaryObject(domain.primaryObject())
                .primaryLoop(domain.primaryLoop())
                .primaryMode(profile.primaryMode())
                .objectMultiplicity(profile.objectMultiplicity())
                .workflowMode(profile.workflowMode())
                .timeModel(profile.timeModel())
                .stateModel(profile.stateModel())
                .collaborationMode(profile.collaborationMode())
                .detailMode(profile.detailMode())
                .interactionModes(profile.interactionModes())
                .evidenceSignals(profile.evidenceSignals())
                .entities(entities)
                .actions(actions)
                .stateVocabulary(domain.states())
                .roles(domain.roles())
                .constraints(domain.constraints())
                .build();
    }

    private PrototypeBundle.ExperienceBrief buildExperienceBrief(ProjectManifest manifest) {
        DomainSignals domain = detectDomainSignals(manifest);
        StructuralProfile profile = inferStructuralProfile(manifest, domain);
        PrototypeBundle.SurfaceIR surfaceIr = buildSurfaceIr(manifest);
        ProjectManifest.DesignContract contract = manifest.getDesignContract();
        List<PrototypeBundle.ScreenPlan> screens = buildScreenPlans(manifest, domain, profile);
        String referenceSignal = extractReferenceSignal(manifest);
        List<PrototypeBundle.ScreenBullet> screenBullets = buildScreenBullets(manifest, domain, screens, referenceSignal);
        PrototypeBundle.VisualDirection visualDirection = buildVisualDirection(manifest, domain, profile);
        String styleSummary = buildStyleSummary(manifest, domain, profile);
        return PrototypeBundle.ExperienceBrief.builder()
                .referenceSignal(referenceSignal)
                .intentSummary(buildIntentSummary(manifest, domain))
                .introduction(buildIntroductionNarrative(manifest, domain, profile, referenceSignal))
                .screenPlanTitle(buildScreenPlanTitle(referenceSignal))
                .screenBullets(screenBullets)
                .nextStepNarrative(buildNextStepNarrative(referenceSignal, profile, styleSummary))
                .interactionModel(profile.interactionModes().isEmpty() ? domain.interactionModel() : String.join(" + ", profile.interactionModes()))
                .navigationStyle(surfaceIr != null && surfaceIr.getNavigationPattern() != null ? surfaceIr.getNavigationPattern() : resolveNavigationStyle(contract))
                .contentRhythm(contract != null && contract.getLayoutRhythm() != null ? contract.getLayoutRhythm().name() : domain.layoutHint())
                .density(contract != null && contract.getContentDensity() != null ? contract.getContentDensity().name() : "MEDIUM")
                .mediaEmphasis(contract != null && contract.getMediaWeight() != null ? contract.getMediaWeight().name() : "MIXED")
                .inferredTraits(buildInferredTraits(domain, contract, referenceSignal, profile))
                .primaryLoopSteps(resolvePrimaryLoopSteps(manifest, domain))
                .executionPlan(buildExecutionPlan(domain, profile))
                .whyThisStructure(buildWhyThisStructure(domain, profile))
                .rationale(buildRationale(domain, referenceSignal))
                .confidenceNote(buildConfidenceNote(referenceSignal))
                .screens(screens)
                .visualDirection(visualDirection)
                .build();
    }

    private PrototypeBundle.DesignSeed buildDesignSeed(ProjectManifest manifest) {
        Map<String, String> meta = ensureMeta(manifest);
        DomainSignals domain = detectDomainSignals(manifest);
        return PrototypeBundle.DesignSeed.builder()
                .backgroundClass(meta.getOrDefault("visual_bgClass", "bg-slate-50"))
                .cardClass(meta.getOrDefault("visual_cardClass", "bg-white shadow-sm rounded-2xl p-6"))
                .primaryColor(meta.getOrDefault("visual_primaryColor", domain.defaultPrimaryColor()))
                .accentColor(meta.getOrDefault("visual_accentColor", domain.defaultAccentColor()))
                .fontFamily(meta.getOrDefault("visual_fontFamily", "font-sans"))
                .lineHeight(meta.getOrDefault("visual_lineHeight", "leading-normal"))
                .letterSpacing(meta.getOrDefault("visual_letterSpacing", "tracking-normal"))
                .radiusStyle(resolveRadiusStyle(meta.get("visual_cardClass")))
                .toneReasoning(meta.getOrDefault("visual_reasoning", domain.toneReasoning()))
                .build();
    }

    private PrototypeBundle.CapabilityLayer buildCapabilityLayer(ProjectManifest manifest) {
        DomainSignals domain = detectDomainSignals(manifest);
        LinkedHashSet<String> accountCapabilities = new LinkedHashSet<>(List.of("identity", "auth-state"));
        LinkedHashSet<String> engagementCapabilities = new LinkedHashSet<>();
        LinkedHashSet<String> publishingCapabilities = new LinkedHashSet<>();
        LinkedHashSet<String> navigationCapabilities = new LinkedHashSet<>();
        LinkedHashSet<String> stateCapabilities = new LinkedHashSet<>(List.of("toast-feedback"));

        for (String mode : domain.interactionModes()) {
            switch (safe(mode).toLowerCase(Locale.ROOT)) {
                case "feed-first", "detail-consumption", "creator-profile" -> {
                    engagementCapabilities.addAll(List.of("like", "save", "comment", "follow", "detail-overlay"));
                    navigationCapabilities.addAll(List.of("feed-navigation", "search", "return-to-feed"));
                }
                case "composer" -> publishingCapabilities.addAll(List.of("draft", "publish", "media-upload"));
                case "pipeline", "workflow", "assignment" -> {
                    navigationCapabilities.addAll(List.of("queue-navigation", "detail-handoff"));
                    stateCapabilities.addAll(List.of("owner-update", "stage-update"));
                }
                case "scheduler", "reservation" -> {
                    navigationCapabilities.add("schedule-navigation");
                    stateCapabilities.addAll(List.of("slot-selection", "confirmation-state"));
                }
                case "review", "document-workspace" -> {
                    stateCapabilities.addAll(List.of("review-state", "approval-state"));
                    navigationCapabilities.add("document-navigation");
                }
                default -> {
                }
            }
        }

        if (accountCapabilities.contains("identity")) {
            accountCapabilities.add("profile-entry");
        }

        return PrototypeBundle.CapabilityLayer.builder()
                .accountCapabilities(new ArrayList<>(accountCapabilities))
                .engagementCapabilities(new ArrayList<>(engagementCapabilities))
                .publishingCapabilities(new ArrayList<>(publishingCapabilities))
                .navigationCapabilities(new ArrayList<>(navigationCapabilities))
                .stateCapabilities(new ArrayList<>(stateCapabilities))
                .build();
    }

    private PrototypeBundle.SurfaceIR buildSurfaceIr(ProjectManifest manifest) {
        DomainSignals domain = detectDomainSignals(manifest);
        StructuralProfile profile = inferStructuralProfile(manifest, domain);
        ProjectManifest.DesignContract contract = manifest.getDesignContract();
        String shellMode = resolveShellMode(profile, domain);
        String navigationPattern = resolveSurfaceNavigationPattern(contract, profile);
        String contentPattern = resolveContentPattern(profile, domain);
        String layoutStrategy = contract != null && contract.getLayoutRhythm() != null
                ? contract.getLayoutRhythm().name()
                : domain.layoutHint();

        return PrototypeBundle.SurfaceIR.builder()
                .primarySurface(resolvePrimarySurface(manifest, profile))
                .shellMode(shellMode)
                .navigationPattern(navigationPattern)
                .interactionDensity(resolveInteractionDensity(contract, profile))
                .contentPattern(contentPattern)
                .layoutStrategy(layoutStrategy)
                .build();
    }

    private PrototypeBundle.MockGraph buildMockGraph(ProjectManifest manifest) {
        List<PrototypeBundle.CollectionSummary> collections = new ArrayList<>();
        int primaryCount = 0;
        List<String> primaryFields = new ArrayList<>();
        List<String> primaryTitles = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(Optional.ofNullable(manifest.getMockData()).orElse("[]"));
            if (root != null && root.isArray()) {
                primaryCount = root.size();
                if (root.size() > 0 && root.get(0).isObject()) {
                    Iterator<String> fields = root.get(0).fieldNames();
                    while (fields.hasNext() && primaryFields.size() < 8) {
                        primaryFields.add(fields.next());
                    }
                }
                for (int i = 0; i < root.size() && primaryTitles.size() < 3; i++) {
                    JsonNode node = root.get(i);
                    String title = firstText(node, "title", "标题", "name", "姓名", "service", "项目");
                    if (!title.isBlank()) {
                        primaryTitles.add(title);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Skipping mock graph parsing due to invalid mockData: {}", e.getMessage());
        }

        DomainSignals domain = detectDomainSignals(manifest);
        collections.add(PrototypeBundle.CollectionSummary.builder()
                .name(domain.primaryObject())
                .recordCount(primaryCount)
                .sampleFields(primaryFields)
                .sampleTitles(primaryTitles)
                .build());

        for (PrototypeBundle.EntityModel entity : buildEntities(domain)) {
            if (entity.getName().equalsIgnoreCase(domain.primaryObject())) {
                continue;
            }
            collections.add(PrototypeBundle.CollectionSummary.builder()
                    .name(entity.getName())
                    .recordCount(estimateSecondaryCollectionSize(entity.getName(), primaryCount))
                    .sampleFields(entity.getSampleFields())
                    .sampleTitles(List.of())
                    .build());
        }

        return PrototypeBundle.MockGraph.builder()
                .primaryCollection(domain.primaryObject())
                .primaryRecordCount(primaryCount)
                .collections(collections)
                .relations(buildRelationSummaries(domain))
                .build();
    }

    private PrototypeBundle.FlowGraph buildFlowGraph(ProjectManifest manifest) {
        DomainSignals domain = detectDomainSignals(manifest);
        List<PrototypeBundle.FlowSpec> flows = new ArrayList<>();
        List<ProjectManifest.TaskFlow> taskFlows = manifest.getTaskFlows() == null ? List.of() : manifest.getTaskFlows();
        if (!taskFlows.isEmpty()) {
            for (ProjectManifest.TaskFlow taskFlow : taskFlows) {
                List<PrototypeBundle.FlowStep> steps = new ArrayList<>();
                List<String> rawSteps = taskFlow.getSteps() == null ? List.of() : taskFlow.getSteps();
                for (int i = 0; i < rawSteps.size(); i++) {
                    String step = rawSteps.get(i);
                    steps.add(PrototypeBundle.FlowStep.builder()
                            .order(i + 1)
                            .action(inferActionName(step, domain))
                            .stateChange(inferStateChange(step, domain))
                            .targetScreen(inferTargetScreen(step, manifest))
                            .build());
                }
                flows.add(PrototypeBundle.FlowSpec.builder()
                        .id(taskFlow.getId())
                        .label(taskFlow.getDescription())
                        .entryScreen(inferEntryScreen(rawSteps, manifest))
                        .successSignal(resolveSuccessSignal(rawSteps))
                        .steps(steps)
                        .build());
            }
        } else {
            flows.add(defaultFlow(domain, manifest));
        }
        return PrototypeBundle.FlowGraph.builder().flows(flows).build();
    }

    private List<PrototypeBundle.EntityModel> buildEntities(DomainSignals domain) {
        List<PrototypeBundle.EntityModel> entities = new ArrayList<>();
        addEntity(entities, domain.primaryObject(), domain.primaryPurpose(), domain.primaryFields());
        for (DomainEntity entity : domain.secondaryEntities()) {
            addEntity(entities, entity.name(), entity.purpose(), entity.fields());
        }
        return entities;
    }

    private List<PrototypeBundle.ActionModel> buildActions(DomainSignals domain) {
        List<PrototypeBundle.ActionModel> actions = new ArrayList<>();
        for (DomainAction action : domain.actions()) {
            actions.add(PrototypeBundle.ActionModel.builder()
                    .name(action.name())
                    .targetEntity(action.targetEntity())
                    .intent(action.intent())
                    .build());
        }
        return actions;
    }

    private List<PrototypeBundle.ScreenPlan> buildScreenPlans(ProjectManifest manifest, DomainSignals domain, StructuralProfile profile) {
        List<PrototypeBundle.ScreenPlan> screens = new ArrayList<>();
        List<ProjectManifest.PageSpec> pages = manifest.getPages() == null ? List.of() : manifest.getPages();
        for (ProjectManifest.PageSpec page : pages.stream().limit(6).toList()) {
            String route = safe(page.getRoute());
            screens.add(PrototypeBundle.ScreenPlan.builder()
                    .id(resolveScreenId(route))
                    .title(page.getDescription())
                    .role(page.getNavRole())
                    .layoutHint(inferLayoutHint(page, domain))
                    .contentFocus(buildScreenContentFocus(page, domain))
                    .layoutNarrative(buildScreenLayoutNarrative(page, domain, profile))
                    .actionLayout(buildScreenActionLayout(page, domain, profile))
                    .keyModules(page.getComponents() == null ? List.of() : page.getComponents())
                    .primaryActions(buildScreenPrimaryActions(page, domain, profile))
                    .build());
        }
        if (screens.isEmpty()) {
            screens.add(PrototypeBundle.ScreenPlan.builder()
                    .id("home")
                    .title(domain.fallbackScreenTitle())
                    .role("PRIMARY")
                    .layoutHint(domain.layoutHint())
                    .contentFocus(buildFallbackScreenContentFocus(domain))
                    .layoutNarrative(buildFallbackLayoutNarrative(domain, profile))
                    .actionLayout(buildFallbackActionLayout(domain, profile))
                    .keyModules(domain.defaultModules())
                    .primaryActions(buildFallbackPrimaryActions(domain, profile))
                    .build());
        }
        return screens;
    }

    private List<PrototypeBundle.RelationSummary> buildRelationSummaries(DomainSignals domain) {
        List<PrototypeBundle.RelationSummary> relations = new ArrayList<>();
        for (DomainRelation relation : domain.relations()) {
            relations.add(PrototypeBundle.RelationSummary.builder()
                    .sourceCollection(relation.sourceCollection())
                    .sourceField(relation.sourceField())
                    .targetCollection(relation.targetCollection())
                    .targetField(relation.targetField())
                    .kind(relation.kind())
                    .build());
        }
        return relations;
    }

    private PrototypeBundle.FlowSpec defaultFlow(DomainSignals domain, ProjectManifest manifest) {
        List<PrototypeBundle.FlowStep> steps = new ArrayList<>();
        List<String> loop = domain.loopSteps();
        for (int i = 0; i < loop.size(); i++) {
            steps.add(PrototypeBundle.FlowStep.builder()
                    .order(i + 1)
                    .action(loop.get(i))
                    .stateChange(i == loop.size() - 1 ? "success feedback" : "advance " + domain.primaryObject() + " context")
                    .targetScreen(i < loop.size() - 1 ? inferTargetScreen(loop.get(i), manifest) : "#detail")
                    .build());
        }
        return PrototypeBundle.FlowSpec.builder()
                .id("default-" + slugify(domain.primaryObject()))
                .label(domain.primaryLoop())
                .entryScreen(inferEntryScreen(loop, manifest))
                .successSignal("toast + visible status change")
                .steps(steps)
                .build();
    }

    private String inferActionName(String raw, DomainSignals domain) {
        String lower = safe(raw).toLowerCase(Locale.ROOT);
        if (containsAny(lower, "assign", "分配", "owner")) return "assign-owner";
        if (containsAny(lower, "follow", "跟进", "推进")) return "advance-workflow";
        if (containsAny(lower, "book", "预约", "确认")) return "confirm-booking";
        if (containsAny(lower, "pay", "支付")) return "confirm-payment";
        if (containsAny(lower, "detail", "详情", "open")) return "open-detail";
        return slugify(raw.isBlank() ? domain.primaryObject() + "-action" : raw);
    }

    private String inferStateChange(String raw, DomainSignals domain) {
        String lower = safe(raw).toLowerCase(Locale.ROOT);
        if (containsAny(lower, "assign", "分配")) return domain.primaryObject() + ".owner updated";
        if (containsAny(lower, "follow", "推进", "stage")) return domain.primaryObject() + ".stage advanced";
        if (containsAny(lower, "book", "预约", "confirm")) return domain.primaryObject() + ".status confirmed";
        if (containsAny(lower, "pay", "支付")) return domain.primaryObject() + ".payment confirmed";
        return domain.primaryObject() + ".context updated";
    }

    private String inferTargetScreen(String raw, ProjectManifest manifest) {
        String lower = safe(raw).toLowerCase(Locale.ROOT);
        List<ProjectManifest.PageSpec> pages = manifest.getPages() == null ? List.of() : manifest.getPages();
        for (ProjectManifest.PageSpec page : pages) {
            String route = safe(page.getRoute()).toLowerCase(Locale.ROOT);
            String desc = safe(page.getDescription()).toLowerCase(Locale.ROOT);
            if (containsAny(lower, route, desc)) {
                return resolveScreenId(page.getRoute());
            }
        }
        if (containsAny(lower, "detail", "详情")) return "detail";
        return pages.isEmpty() ? "home" : resolveScreenId(pages.get(0).getRoute());
    }

    private String inferEntryScreen(List<String> rawSteps, ProjectManifest manifest) {
        if (rawSteps == null || rawSteps.isEmpty()) {
            return manifest.getPages() == null || manifest.getPages().isEmpty() ? "home" : resolveScreenId(manifest.getPages().get(0).getRoute());
        }
        return inferTargetScreen(rawSteps.get(0), manifest);
    }

    private String resolveSuccessSignal(List<String> rawSteps) {
        String joined = rawSteps == null ? "" : String.join(" ", rawSteps).toLowerCase(Locale.ROOT);
        if (containsAny(joined, "comment", "评论", "reply", "点赞")) return "count + thread update";
        if (containsAny(joined, "book", "预约", "支付", "confirm")) return "toast + status badge update";
        if (containsAny(joined, "assign", "follow", "推进", "risk")) return "owner/stage/risk signal update";
        return "toast + visible list refresh";
    }

    private List<String> resolvePrimaryLoopSteps(ProjectManifest manifest, DomainSignals domain) {
        if (manifest.getTaskFlows() != null && !manifest.getTaskFlows().isEmpty()) {
            return manifest.getTaskFlows().stream()
                    .flatMap(flow -> flow.getSteps() == null ? java.util.stream.Stream.empty() : flow.getSteps().stream())
                    .limit(4)
                    .collect(Collectors.toList());
        }
        return domain.loopSteps();
    }

    private String resolveNavigationStyle(ProjectManifest.DesignContract contract) {
        if (contract == null) {
            return "SIDEBAR_PRIMARY_NAV";
        }
        if ("CONTENT_FIRST".equalsIgnoreCase(contract.getContentMode())) {
            return "content shell + utility actions";
        }
        return switch (safe(contract.getShellPattern())) {
            case "MINIMAL_HEADER_DRAWER_ONLY" -> "header drawer";
            case "PERSISTENT_TOP_DYNAMIC_SIDEBAR" -> "top nav + dynamic sidebar";
            default -> "sidebar primary nav";
        };
    }

    private String resolveShellMode(StructuralProfile profile, DomainSignals domain) {
        List<String> modes = profile.interactionModes();
        if (modes.contains("feed-first")) return "content-feed";
        if (modes.contains("pipeline") || modes.contains("queue")) return "workflow-workspace";
        if (modes.contains("scheduler")) return "booking-flow";
        if (modes.contains("document-workspace") || modes.contains("review")) return "document-review";
        if (modes.contains("listing")) return "listing-commerce";
        if (modes.contains("workspace")) return "workspace";
        return domain.layoutHint();
    }

    private String resolvePrimarySurface(ProjectManifest manifest, StructuralProfile profile) {
        String source = safe(manifest.getUserIntent()).toLowerCase(Locale.ROOT);
        if (containsAny(source, "app", "手机", "移动端", "ios", "android", "小程序", "竖屏")) return "mobile-web";
        if (containsAny(source, "pad", "平板", "巡检", "门店", "现场")) return "tablet-web";
        if (profile.interactionModes().contains("feed-first")) return "content-web";
        return "desktop-web";
    }

    private String resolveSurfaceNavigationPattern(ProjectManifest.DesignContract contract, StructuralProfile profile) {
        if (contract != null && contract.getNavigationMode() != null) {
            return contract.getNavigationMode().name();
        }
        if (profile.interactionModes().contains("feed-first")) return "TOP_CHANNEL";
        if (profile.interactionModes().contains("scheduler")) return "STEP_FLOW";
        if (profile.interactionModes().contains("pipeline") || profile.interactionModes().contains("workspace"))
            return "SIDEBAR";
        return "HYBRID";
    }

    private String resolveInteractionDensity(ProjectManifest.DesignContract contract, StructuralProfile profile) {
        if (contract != null && contract.getContentDensity() != null) {
            return contract.getContentDensity().name();
        }
        return profile.primaryMode().equals("CONSUME") ? "MEDIUM" : "HIGH";
    }

    private String resolveContentPattern(StructuralProfile profile, DomainSignals domain) {
        if (profile.interactionModes().contains("feed-first")) return "media-feed";
        if (profile.interactionModes().contains("listing")) return "listing-flow";
        if (profile.interactionModes().contains("document-workspace")) return "document-workspace";
        if (profile.interactionModes().contains("pipeline") || profile.interactionModes().contains("workflow"))
            return "workflow-queue";
        return domain.interactionModel();
    }

    private String buildIntentSummary(ProjectManifest manifest, DomainSignals domain) {
        String intent = safe(manifest.getUserIntent());
        if (intent.isBlank()) {
            return domain.domainSummary();
        }
        return "系统将该需求理解为一个以“" + domain.primaryObject() + "”为核心对象的产品，重点围绕 " + domain.primaryLoop() + " 展开。";
    }

    private List<String> buildInferredTraits(DomainSignals domain, ProjectManifest.DesignContract contract, String referenceSignal, StructuralProfile profile) {
        List<String> traits = new ArrayList<>();
        if (!referenceSignal.isBlank()) {
            traits.add("参考信号：" + referenceSignal);
        }
        if (!profile.interactionModes().isEmpty()) {
            traits.add("交互模式：" + String.join(" + ", profile.interactionModes()));
        } else {
            traits.add("交互模型：" + domain.interactionModel());
        }
        traits.add("主循环：" + domain.primaryLoop());
        traits.add("工作方式：" + profile.primaryMode());
        traits.add("对象规模：" + profile.objectMultiplicity());
        traits.add("详情模式：" + profile.detailMode());
        if (contract != null && contract.getLayoutRhythm() != null) {
            traits.add("布局节奏：" + contract.getLayoutRhythm().name());
        }
        if (contract != null && contract.getMediaWeight() != null) {
            traits.add("内容表达：" + contract.getMediaWeight().name());
        }
        traits.add("体验气质：" + domain.toneReasoning());
        return traits;
    }

    private List<String> buildExecutionPlan(DomainSignals domain, StructuralProfile profile) {
        return List.of(
                "先基于 " + String.join(" + ", profile.interactionModes().isEmpty() ? List.of(domain.interactionModel()) : profile.interactionModes()) + " 建立与该体验匹配的设计系统与组件原语。",
                "再生成核心页面集合：" + String.join("、", domain.defaultScreenTitles()) + "。",
                "随后铺设可信的图文 mock 数据与对象关系。",
                "最后编译主流程交互，确保按钮、状态与反馈可运行。"
        );
    }

    private String buildWhyThisStructure(DomainSignals domain, StructuralProfile profile) {
        return "系统先锁定“" + domain.primaryObject() + "”作为主对象，再把“"
                + humanizeInteractionModes(profile.interactionModes(), domain.interactionModel())
                + "”拆成可连续操作的页面组合。这样后续原型生成时，页面布局、mock 数据和按钮反馈都能围绕同一套主循环展开，而不是停留在行业模板层面。";
    }

    private PrototypeBundle.VisualDirection buildVisualDirection(ProjectManifest manifest, DomainSignals domain, StructuralProfile profile) {
        Map<String, String> meta = ensureMeta(manifest);
        ProjectManifest.DesignContract contract = manifest.getDesignContract();
        String primaryColor = meta.getOrDefault("visual_primaryColor", domain.defaultPrimaryColor());
        String accentColor = meta.getOrDefault("visual_accentColor", domain.defaultAccentColor());
        String bgClass = meta.getOrDefault("visual_bgClass", "bg-slate-50");
        String cardClass = meta.getOrDefault("visual_cardClass", "bg-white shadow-sm rounded-2xl p-6");
        String fontFamily = meta.getOrDefault("visual_fontFamily", "font-sans");
        String lineHeight = meta.getOrDefault("visual_lineHeight", "leading-normal");
        String letterSpacing = meta.getOrDefault("visual_letterSpacing", "tracking-normal");
        String shadowStrategy = meta.getOrDefault("visual_shadowStrategy", "shadow-sm");
        String borderAccent = meta.getOrDefault("visual_borderAccent", "border-slate-200");

        return PrototypeBundle.VisualDirection.builder()
                .tone(buildToneNarrative(domain, profile))
                .palette("以" + humanizeColorToken(primaryColor) + "作为主色、" + humanizeColorToken(accentColor)
                        + "作为强调色，配合" + humanizeBackground(bgClass) + "建立首屏层级与状态识别。")
                .typography("采用" + humanizeFontFamily(fontFamily) + "，正文保持" + humanizeLineHeight(lineHeight)
                        + "与" + humanizeLetterSpacing(letterSpacing) + "，让" + humanizeDensity(contract) + "信息阅读依然稳定。")
                .surfaces("面板以" + humanizeCardShape(cardClass) + "和" + humanizeShadow(shadowStrategy)
                        + "为主，辅以" + humanizeBorder(borderAccent) + "，让内容层级清晰但不过度厚重。")
                .controls(buildControlNarrative(domain, profile, primaryColor, accentColor))
                .imagery(buildImageryNarrative(contract, domain, profile))
                .build();
    }

    private String buildToneNarrative(DomainSignals domain, StructuralProfile profile) {
        List<String> modes = profile.interactionModes();
        if (modes.contains("feed-first")) {
            return "整体气质偏内容优先和轻编辑感，强调发现、沉浸浏览与创作者表达。";
        }
        if (modes.contains("dashboard") || modes.contains("queue") || modes.contains("pipeline")) {
            return "整体气质偏高信号和强结构，强调优先级、状态对比与处理效率。";
        }
        if (modes.contains("scheduler") || modes.contains("service-catalog")) {
            return "整体气质偏干净可信和服务导向，重点突出确认路径与过程反馈。";
        }
        if (modes.contains("document-workspace") || modes.contains("review")) {
            return "整体气质偏克制严谨，优先保证文档、证据与状态层级的可读性。";
        }
        return "整体气质保持" + domain.primaryObject() + "驱动的专业感，让核心对象、状态与下一步动作始终清楚。";
    }

    private String buildControlNarrative(DomainSignals domain, StructuralProfile profile, String primaryColor, String accentColor) {
        List<String> modes = profile.interactionModes();
        if (modes.contains("feed-first")) {
            return "主按钮使用" + humanizeColorToken(primaryColor) + "承担发布或关键互动，次级操作保持轻边框；内容卡片本身就是进入详情的主要按钮。";
        }
        if (modes.contains("scheduler")) {
            return "选择、确认、支付等关键动作按顺序靠近结果摘要排布，主按钮使用" + humanizeColorToken(primaryColor)
                    + "突出确认，补充动作用" + humanizeColorToken(accentColor) + "做轻提醒。";
        }
        if (modes.contains("dashboard") || modes.contains("pipeline") || modes.contains("workflow")) {
            return "批量处理、分配与推进状态保持主次分层，列表头部承担筛选/批量按钮，详情区承担确认类主按钮。";
        }
        return "主按钮负责推进当前主循环，次按钮承担返回、筛选或补充信息，确保每个页面都能一眼看出下一步操作。";
    }

    private String buildImageryNarrative(ProjectManifest.DesignContract contract, DomainSignals domain, StructuralProfile profile) {
        ProjectManifest.MediaWeight mediaWeight = contract != null ? contract.getMediaWeight() : null;
        ProjectManifest.LayoutRhythm layoutRhythm = contract != null ? contract.getLayoutRhythm() : null;
        if (profile.interactionModes().contains("feed-first")
                || layoutRhythm == ProjectManifest.LayoutRhythm.WATERFALL
                || mediaWeight == ProjectManifest.MediaWeight.VISUAL_HEAVY) {
            return "封面图和卡片比例需要明显拉开差异，让用户先扫到内容质量，再进入详情和互动。";
        }
        if (profile.interactionModes().contains("document-workspace") || mediaWeight == ProjectManifest.MediaWeight.TEXT_HEAVY) {
            return "视觉素材退后，标题、摘要、标签和状态承担主要信息密度，避免文档型页面被大图打断。";
        }
        if (profile.interactionModes().contains("scheduler") || profile.interactionModes().contains("reservation")) {
            return "图片主要承担信任建立，时间、价格、状态等结构化信息要与视觉素材并列展示。";
        }
        return "媒体与文本保持平衡，让主对象既有识别度，也不会盖过关键状态和操作入口。";
    }

    private String buildScreenContentFocus(ProjectManifest.PageSpec page, DomainSignals domain) {
        String route = safe(page.getRoute()).toLowerCase(Locale.ROOT);
        String description = safe(page.getDescription()).toLowerCase(Locale.ROOT);
        if (containsAny(route + " " + description, "home", "index", "feed", "discover", "列表", "首页", "池")) {
            return "聚焦" + domain.primaryObject() + "的首轮发现、筛选信号和进入详情的决策。";
        }
        if (containsAny(route + " " + description, "detail", "详情", ":id")) {
            return "聚焦单个" + domain.primaryObject() + "的完整上下文、状态变化和下一步动作。";
        }
        if (isCreateSurface(route, description)) {
            return "聚焦创建输入、素材/字段填写与提交前确认。";
        }
        if (containsAny(route + " " + description, "profile", "user", "me", "主页")) {
            return "聚焦身份信息、内容归档和回到主循环的入口。";
        }
        if (containsAny(route + " " + description, "schedule", "slot", "order", "booking", "预约", "时段", "支付")) {
            return "聚焦时间、状态确认和结果反馈，保证关键决策一步步收束。";
        }
        if (containsAny(route + " " + description, "document", "review", "evidence", "文书", "证据", "审核")) {
            return "聚焦文档、批注、证据和审核状态的持续处理。";
        }
        return "聚焦" + splitSentence(page.getDescription())[0] + "的主要内容与关键动作。";
    }

    private String buildScreenLayoutNarrative(ProjectManifest.PageSpec page, DomainSignals domain, StructuralProfile profile) {
        String route = safe(page.getRoute()).toLowerCase(Locale.ROOT);
        String description = safe(page.getDescription()).toLowerCase(Locale.ROOT);
        String source = route + " " + description;
        List<String> modes = profile.interactionModes();

        if (isCreateSurface(route, description)) {
            return "使用单列或轻双列编辑区，内容输入按顺序展开，提交动作固定在尾部，减少来回跳动。";
        }
        if (containsAny(source, "profile", "user", "主页")) {
            return "顶部先建立身份与可信度，下方再承接内容分组或作品流，让主页继续为主循环服务。";
        }
        if (containsAny(source, "detail", "详情", ":id")) {
            if (modes.contains("detail-consumption")) {
                return "首屏先给封面和正文，作者与互动数据贴近主内容，评论和相关推荐顺序下沉。";
            }
            if (modes.contains("document-workspace")) {
                return "中间放正文或主详情，侧边并排状态、批注和相关对象，适合连续处理。";
            }
            return "主详情区承载上下文，右侧或底部固定状态轨和操作按钮，保证处理动作不脱离对象。";
        }
        if (modes.contains("document-workspace") || containsAny(source, "document", "review", "evidence", "文书", "证据", "审核")) {
            return "使用文档列表、正文/预览和批注/状态栏的分区布局，让阅读和处理保持同屏连续。";
        }
        if (modes.contains("feed-first") || containsAny(source, "feed", "discover", "首页", "瀑布流")) {
            return "主画布优先展示内容卡片，顶部只保留轻筛选与分类，辅助信息退到次层。";
        }
        if (modes.contains("dashboard") || modes.contains("queue") || modes.contains("pipeline") || containsAny(source, "dashboard", "池", "队列", "总览")) {
            return "顶部是关键指标，中间是待处理列表或队列，侧边或抽屉承接详情与状态辅助信息。";
        }
        if (modes.contains("scheduler") || containsAny(source, "预约", "时段", "schedule", "slot")) {
            return "把选择项、时间面板和确认摘要拆成顺序明确的三段式，让确认路径一眼可见。";
        }
        return "围绕“" + domain.primaryObject() + "”的主循环组织主区、详情和辅助区域，避免功能面板互相争抢。";
    }

    private String buildScreenActionLayout(ProjectManifest.PageSpec page, DomainSignals domain, StructuralProfile profile) {
        String route = safe(page.getRoute()).toLowerCase(Locale.ROOT);
        String description = safe(page.getDescription()).toLowerCase(Locale.ROOT);
        String source = route + " " + description;
        List<String> modes = profile.interactionModes();

        if (isCreateSurface(route, description)) {
            return "保存草稿、预览、提交保持清晰主次层级，主提交按钮固定，避免滚动后找不到。";
        }
        if (containsAny(source, "detail", "详情", ":id")) {
            if (modes.contains("detail-consumption")) {
                return "点赞、收藏、评论等连续动作围绕正文尾部或底栏排布，关闭/返回始终明确。";
            }
            return "推进状态、确认、补充记录放在同一组主次按钮里，点击后要立即反馈到当前详情。";
        }
        if (modes.contains("document-workspace") || containsAny(source, "document", "review", "evidence", "文书", "证据", "审核")) {
            return "批注、审核、推进阶段等动作贴近文档内容和状态栏摆放，避免频繁切屏后丢失上下文。";
        }
        if (modes.contains("feed-first") || containsAny(source, "feed", "discover", "首页", "瀑布流")) {
            return "主操作是卡片点击和分类切换，发布或新增入口保持常驻，但不能压过内容本身。";
        }
        if (modes.contains("dashboard") || modes.contains("pipeline") || modes.contains("workflow")) {
            return "筛选和批量按钮贴近列表头部，单条处理动作进入详情后集中呈现，避免首屏过多按钮。";
        }
        if (modes.contains("scheduler") || containsAny(source, "预约", "时段", "schedule", "slot")) {
            return "选择动作在中部内容区完成，确认与支付按钮固定在摘要区或底部，确保闭环明确。";
        }
        return "主按钮负责推进当前页面的下一步，次按钮承担筛选、返回或补充信息，避免所有动作挤在一起。";
    }

    private List<String> buildScreenPrimaryActions(ProjectManifest.PageSpec page, DomainSignals domain, StructuralProfile profile) {
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        List<String> components = page.getComponents() == null ? List.of() : page.getComponents();
        for (String component : components) {
            String lower = safe(component).toLowerCase(Locale.ROOT);
            if (containsAny(lower, "搜索", "筛选", "filter", "search", "category", "tab")) actions.add("搜索/筛选");
            if (containsAny(lower, "详情", "detail", "入口", "card", "列表", "瀑布流")) actions.add("进入详情");
            if (containsAny(lower, "批量", "batch")) actions.add("批量处理");
            if (containsAny(lower, "负责人", "owner", "分配", "assign")) actions.add("分配负责人");
            if (containsAny(lower, "风险", "阶段", "状态", "stage", "status", "progress")) actions.add("推进状态");
            if (containsAny(lower, "评论", "互动", "收藏", "点赞", "like", "comment", "save")) actions.add("互动反馈");
            if (containsAny(lower, "发布", "create", "composer", "新建")) actions.add("创建/发布");
            if (containsAny(lower, "时段", "slot", "预约", "booking", "schedule")) actions.add("选择时段");
            if (containsAny(lower, "支付", "payment", "确认", "confirm", "订单")) actions.add("确认结果");
            if (containsAny(lower, "文档", "批注", "审核", "document", "review", "evidence")) actions.add("批注/审核");
            if (containsAny(lower, "上传", "素材", "media", "image")) actions.add("上传素材");
        }

        String source = safe(page.getRoute()).toLowerCase(Locale.ROOT) + " " + safe(page.getDescription()).toLowerCase(Locale.ROOT);
        if (actions.isEmpty()) {
            if (isCreateSurface(page.getRoute(), page.getDescription())) {
                actions.addAll(List.of("上传素材", "填写内容", "保存/提交"));
            } else if (containsAny(source, "profile", "user", "主页")) {
                actions.addAll(List.of("切换分组", "查看作品", "关注/返回主循环"));
            } else if (containsAny(source, "detail", "详情", ":id")) {
                if (profile.interactionModes().contains("detail-consumption")) {
                    actions.addAll(List.of("浏览正文", "点赞/收藏", "评论/继续发现"));
                } else {
                    actions.addAll(List.of("查看详情", "推进状态", "补充记录"));
                }
            } else if (profile.interactionModes().contains("feed-first") || containsAny(source, "feed", "discover", "首页", "瀑布流")) {
                actions.addAll(List.of("切换分类", "打开详情", "发起发布"));
            } else if (profile.interactionModes().contains("dashboard") || profile.interactionModes().contains("pipeline")) {
                actions.addAll(List.of("筛选对象", "批量处理", "进入详情"));
            } else if (profile.interactionModes().contains("scheduler")) {
                actions.addAll(List.of("选择项目", "选择时段", "确认预约"));
            } else {
                actions.addAll(buildFallbackPrimaryActions(domain, profile));
            }
        }

        return actions.stream().limit(4).collect(Collectors.toList());
    }

    private String buildFallbackScreenContentFocus(DomainSignals domain) {
        return "聚焦" + domain.primaryObject() + "的主要列表、状态和下一步动作。";
    }

    private String buildFallbackLayoutNarrative(DomainSignals domain, StructuralProfile profile) {
        if (profile.interactionModes().contains("feed-first")) {
            return "内容卡片作为主画布，辅助工具弱化，先保证浏览和进入详情的顺畅度。";
        }
        if (profile.interactionModes().contains("dashboard") || profile.interactionModes().contains("pipeline")) {
            return "用概览、列表和详情三层关系组织界面，让处理动作始终贴着对象。";
        }
        return "围绕主对象的浏览、详情和处理关系组织页面骨架，保证后续原型可继续扩展。";
    }

    private String buildFallbackActionLayout(DomainSignals domain, StructuralProfile profile) {
        if (profile.interactionModes().contains("feed-first")) {
            return "卡片点击承担主要入口，轻量操作分散在分类和常驻发布入口里。";
        }
        if (profile.interactionModes().contains("scheduler")) {
            return "选择动作先发生，确认动作后收束，避免多个关键按钮同屏抢权重。";
        }
        return "主按钮推进主循环，次按钮辅助筛选和回退，保持清晰的操作层级。";
    }

    private List<String> buildFallbackPrimaryActions(DomainSignals domain, StructuralProfile profile) {
        if (profile.interactionModes().contains("feed-first")) {
            return List.of("切换分类", "打开详情", "发起发布");
        }
        if (profile.interactionModes().contains("pipeline")) {
            return List.of("筛选对象", "进入详情", "推进状态");
        }
        if (profile.interactionModes().contains("scheduler")) {
            return List.of("选择项目", "选择时段", "确认结果");
        }
        if (profile.interactionModes().contains("document-workspace")) {
            return List.of("查看文档", "批注/审核", "推进阶段");
        }
        return List.of("查看列表", "打开详情", "推进下一步");
    }

    private String humanizeColorToken(String token) {
        String lower = safe(token).toLowerCase(Locale.ROOT);
        if (lower.startsWith("rose") || lower.startsWith("pink")) return "暖红粉";
        if (lower.startsWith("red")) return "强调红";
        if (lower.startsWith("amber") || lower.startsWith("yellow")) return "琥珀黄";
        if (lower.startsWith("orange")) return "橙色";
        if (lower.startsWith("emerald") || lower.startsWith("green")) return "绿色";
        if (lower.startsWith("teal")) return "青绿色";
        if (lower.startsWith("sky") || lower.startsWith("blue") || lower.startsWith("indigo")) return "蓝色";
        if (lower.startsWith("violet") || lower.startsWith("purple")) return "紫色";
        if (lower.startsWith("slate") || lower.startsWith("gray") || lower.startsWith("zinc")) return "石墨灰";
        return token;
    }

    private String humanizeBackground(String bgClass) {
        String lower = safe(bgClass).toLowerCase(Locale.ROOT);
        if (lower.contains("slate-950") || lower.contains("black")) return "深色背景";
        if (lower.contains("white") || lower.contains("slate-50") || lower.contains("gray-50")) return "浅色留白背景";
        return "柔和背景";
    }

    private String humanizeFontFamily(String fontFamily) {
        return safe(fontFamily).toLowerCase(Locale.ROOT).contains("serif") ? "衬线标题体系" : "无衬线标题体系";
    }

    private String humanizeLineHeight(String lineHeight) {
        String lower = safe(lineHeight).toLowerCase(Locale.ROOT);
        if (lower.contains("snug") || lower.contains("tight")) return "更紧凑的行距";
        if (lower.contains("relaxed") || lower.contains("loose")) return "更舒展的行距";
        return "中性的行距";
    }

    private String humanizeLetterSpacing(String letterSpacing) {
        String lower = safe(letterSpacing).toLowerCase(Locale.ROOT);
        if (lower.contains("tight")) return "偏紧的字距";
        if (lower.contains("wide")) return "偏宽的字距";
        return "自然字距";
    }

    private String humanizeDensity(ProjectManifest.DesignContract contract) {
        if (contract == null || contract.getContentDensity() == null) {
            return "当前";
        }
        return switch (contract.getContentDensity()) {
            case HIGH -> "高密度";
            case LOW -> "低密度";
            default -> "中密度";
        };
    }

    private String humanizeCardShape(String cardClass) {
        String lower = safe(cardClass).toLowerCase(Locale.ROOT);
        if (lower.contains("rounded-3xl")) return "大圆角卡片";
        if (lower.contains("rounded-2xl")) return "柔和圆角卡片";
        if (lower.contains("rounded-xl")) return "中等圆角卡片";
        return "标准卡片";
    }

    private String humanizeShadow(String shadowStrategy) {
        String lower = safe(shadowStrategy).toLowerCase(Locale.ROOT);
        if (lower.contains("shadow-xl")) return "更明显的阴影层次";
        if (lower.contains("shadow-none")) return "弱阴影";
        return "轻阴影层次";
    }

    private String humanizeBorder(String borderAccent) {
        String lower = safe(borderAccent).toLowerCase(Locale.ROOT);
        if (lower.contains("transparent")) return "弱边界处理";
        return "细边框分层";
    }

    private String humanizeTypographyDescriptor(String fontFamily, StructuralProfile profile) {
        String family = safe(fontFamily).toLowerCase(Locale.ROOT);
        if (profile.interactionModes().contains("feed-first")) {
            return family.contains("serif") ? "偏编辑感的衬线排版" : "清晰的轻编辑排版";
        }
        if (profile.interactionModes().contains("document-workspace") || profile.interactionModes().contains("review")) {
            return family.contains("serif") ? "克制的文档排版" : "清晰的文档排版";
        }
        if (profile.interactionModes().contains("dashboard") || profile.interactionModes().contains("pipeline")) {
            return "高信号的结构化排版";
        }
        return family.contains("serif") ? "稳定的衬线排版" : "清晰的结构化排版";
    }

    private String humanizeImageryDescriptor(ProjectManifest.DesignContract contract, StructuralProfile profile) {
        ProjectManifest.MediaWeight mediaWeight = contract != null ? contract.getMediaWeight() : null;
        ProjectManifest.LayoutRhythm layoutRhythm = contract != null ? contract.getLayoutRhythm() : null;
        if (profile.interactionModes().contains("feed-first")
                || layoutRhythm == ProjectManifest.LayoutRhythm.WATERFALL
                || mediaWeight == ProjectManifest.MediaWeight.VISUAL_HEAVY) {
            return "内容优先的图文层级";
        }
        if (profile.interactionModes().contains("scheduler")) {
            return "可信的服务信息层级";
        }
        if (profile.interactionModes().contains("document-workspace") || mediaWeight == ProjectManifest.MediaWeight.TEXT_HEAVY) {
            return "信息优先的文本层级";
        }
        return "平衡的图文层级";
    }

    private boolean isCreateSurface(String route, String description) {
        String routeLower = safe(route).toLowerCase(Locale.ROOT);
        String descriptionLower = safe(description).toLowerCase(Locale.ROOT);
        return containsAny(routeLower, "publish", "create", "/new", "-new")
                || routeLower.endsWith("new")
                || containsAny(descriptionLower, "发布", "新建");
    }

    private String buildIntroductionNarrative(ProjectManifest manifest, DomainSignals domain, StructuralProfile profile, String referenceSignal) {
        if (!referenceSignal.isBlank()) {
            return "好的，我明白您想要设计一个参考“" + referenceSignal + "”风格的界面。系统会结合需求内容，围绕"
                    + humanizeInteractionModes(profile.interactionModes(), domain.interactionModel())
                    + "来组织页面结构和内容节奏。";
        }
        return "好的，我理解您想要构建一个围绕“" + domain.primaryObject() + "”展开的产品。系统会先拆解核心对象、主循环、状态推进和页面集合，再进入原型生成。";
    }

    private String buildScreenPlanTitle(String referenceSignal) {
        return referenceSignal == null || referenceSignal.isBlank()
                ? "我计划为您设计以下几个核心页面："
                : "我计划为您设计以下几个核心页面：";
    }

    private List<PrototypeBundle.ScreenBullet> buildScreenBullets(ProjectManifest manifest, DomainSignals domain, List<PrototypeBundle.ScreenPlan> screens, String referenceSignal) {
        RequirementEvidence evidence = buildRequirementEvidence(manifest);
        List<PrototypeBundle.ScreenBullet> evidenceBullets = buildEvidenceDrivenScreenBullets(evidence, domain);
        if (!evidenceBullets.isEmpty() && !isWeakScreenBulletPlan(evidenceBullets)) {
            return dedupeBullets(evidenceBullets);
        }

        List<PrototypeBundle.ScreenBullet> bullets = new ArrayList<>();
        List<ProjectManifest.PageSpec> pages = manifest.getPages() == null ? List.of() : manifest.getPages();
        for (ProjectManifest.PageSpec page : pages.stream().limit(4).toList()) {
            bullets.add(buildBulletFromPage(page, domain));
        }
        if (!bullets.isEmpty() && !isWeakScreenBulletPlan(bullets)) {
            return dedupeBullets(bullets);
        }

        List<PrototypeBundle.ScreenBullet> modeBullets = buildBulletsFromInteractionModes(domain);
        if (modeBullets.size() >= 2) {
            return modeBullets;
        }
        if (bullets.isEmpty() || isWeakScreenBulletPlan(bullets)) {
            if (!evidenceBullets.isEmpty()) {
                return dedupeBullets(evidenceBullets);
            }
        }
        if (bullets.isEmpty()) {
            bullets.addAll(buildDefaultScreenBullets(domain));
        }
        return dedupeBullets(bullets);
    }

    private boolean isWeakScreenBulletPlan(List<PrototypeBundle.ScreenBullet> bullets) {
        if (bullets == null || bullets.isEmpty()) {
            return true;
        }
        Set<String> labels = bullets.stream()
                .map(PrototypeBundle.ScreenBullet::getLabel)
                .map(this::safe)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        long genericCount = labels.stream()
                .filter(label -> containsAny(label.toLowerCase(Locale.ROOT), "详情页", "审核页", "发布页", "首页", "用户个人主页", "详情处理页"))
                .count();
        return labels.size() < Math.min(3, bullets.size()) || genericCount >= Math.max(2, labels.size() - 1);
    }

    private List<PrototypeBundle.ScreenBullet> buildDefaultScreenBullets(DomainSignals domain) {
        List<PrototypeBundle.ScreenBullet> bullets = new ArrayList<>();
        for (String title : domain.defaultScreenTitles().stream().limit(4).toList()) {
            bullets.add(bullet(slugify(title), title, "围绕“" + domain.primaryObject() + "”主循环展开的关键页面。"));
        }
        return dedupeBullets(bullets);
    }

    private List<PrototypeBundle.ScreenBullet> buildEvidenceDrivenScreenBullets(RequirementEvidence evidence, DomainSignals domain) {
        String source = evidence.combinedSource();
        List<PrototypeBundle.ScreenBullet> bullets = new ArrayList<>();

        if ("listing".equals(domain.primaryObject())) {
            if (containsAny(source, "直播", "主播", "挂车", "成交分析")) {
                bullets.add(bullet("sessions", "直播场次页", "查看直播日历、场次状态和排期。"));
                bullets.add(bullet("merch", "商品挂车页", "配置直播商品、价格和挂车顺序。"));
                bullets.add(bullet("orders", "订单列表页", "查看直播带货订单和履约状态。"));
                bullets.add(bullet("host", "主播中心", "查看主播表现、成交数据和内容安排。"));
            } else if (containsAny(source, "房源", "地图找房", "经纪人")) {
                bullets.add(bullet("listing", "房源列表页", "按区域、价格和带看状态浏览房源。"));
                bullets.add(bullet("map", "地图找房页", "在地图中联动筛选房源与商圈信息。"));
                bullets.add(bullet("detail", "房源详情页", "查看房源卖点、户型信息和带看安排。"));
                bullets.add(bullet("broker", "经纪人主页", "查看经纪人资料、在售房源和联系入口。"));
            } else if (containsAny(source, "车源", "看车", "车辆")) {
                bullets.add(bullet("listing", "车源列表页", "集中浏览车源、价格与车况信息。"));
                bullets.add(bullet("detail", "车辆详情页", "查看车辆配置、检测报告和卖点说明。"));
                bullets.add(bullet("visit", "预约看车页", "选择到店时间并确认试驾或看车安排。"));
                bullets.add(bullet("deal", "成交进度页", "跟踪报价、定金和交付节点。"));
            } else if (containsAny(source, "酒店", "房型", "民宿")) {
                bullets.add(bullet("listing", "房型列表页", "查看不同房型、价格和可订状态。"));
                bullets.add(bullet("detail", "房型详情页", "查看房型图片、设施和入住规则。"));
                bullets.add(bullet("date", "日期选择页", "选择入住离店日期并确认价格变化。"));
                bullets.add(bullet("order", "订单确认页", "核对入住信息、支付方式和订单状态。"));
            } else if (containsAny(source, "票务", "场次", "座位")) {
                bullets.add(bullet("sessions", "场次列表页", "浏览演出或活动场次与余票情况。"));
                bullets.add(bullet("seat", "座位选择页", "按区域或座位图选择位置。"));
                bullets.add(bullet("order", "订单确认页", "确认票价、观演人信息与支付。"));
                bullets.add(bullet("checkin", "核销签到页", "用于核销、入场和订单追踪。"));
            } else if (containsAny(source, "药房", "药品", "处方")) {
                bullets.add(bullet("search", "药品搜索页", "按症状、品类和关键字查找药品。"));
                bullets.add(bullet("review", "处方审核页", "查看处方材料与审核状态。"));
                bullets.add(bullet("order", "订单进度页", "跟踪支付、发货和配送状态。"));
                bullets.add(bullet("advisor", "药师咨询页", "承接购药问题与用药建议。"));
            } else if (containsAny(source, "线路", "出行", "目的地", "旅游")) {
                bullets.add(bullet("listing", "线路推荐页", "浏览目的地、主题和天数组合。"));
                bullets.add(bullet("detail", "行程详情页", "查看行程亮点、服务说明和咨询入口。"));
                bullets.add(bullet("order", "订单确认页", "确认出行人、优惠与支付方式。"));
                bullets.add(bullet("reminder", "出行提醒页", "查看订单状态、出发提醒和凭证信息。"));
            } else if (containsAny(source, "分销", "批发", "商品目录")) {
                bullets.add(bullet("catalog", "商品目录页", "查看商品目录、价格层级和库存状态。"));
                bullets.add(bullet("orders", "订单列表页", "集中处理客户订单和状态筛选。"));
                bullets.add(bullet("reconcile", "对账单页", "查看账期、回款和对账状态。"));
                bullets.add(bullet("shipping", "发货进度页", "跟踪备货、发运和签收节点。"));
            } else if (containsAny(source, "外贸", "询盘", "出运", "单证")) {
                bullets.add(bullet("inquiry", "询盘列表页", "查看客户询盘与跟进状态。"));
                bullets.add(bullet("quote", "报价单页", "管理报价版本、价格条款和确认情况。"));
                bullets.add(bullet("shipping", "出运节点页", "跟踪出运、报关和交付状态。"));
                bullets.add(bullet("docs", "单证归档页", "集中管理合同、发票和物流单证。"));
            } else if (containsAny(source, "跨境电商", "多站点订单", "广告投放")) {
                bullets.add(bullet("catalog", "商品列表页", "查看商品、站点状态与库存。"));
                bullets.add(bullet("orders", "多站点订单页", "集中处理多站点订单和履约状态。"));
                bullets.add(bullet("ads", "广告投放页", "查看广告投放效果和预算消耗。"));
                bullets.add(bullet("profit", "利润分析页", "查看成本、利润和渠道表现。"));
            }
        } else if ("booking".equals(domain.primaryObject())) {
            if (containsAny(source, "共享办公", "工位", "会议室")) {
                bullets.add(bullet("overview", "空间运营总览页", "查看工位、会议室、今日预约和账单状态。"));
                bullets.add(bullet("desks", "工位预订页", "浏览可用工位、工区和会员预订入口。"));
                bullets.add(bullet("rooms", "会议室排期页", "管理会议室时间轴、冲突提醒和预订状态。"));
                bullets.add(bullet("billing", "账单与会员页", "查看会员权益、账单记录和社区活动。"));
            } else if (containsAny(source, "健身", "训练计划", "打卡", "教练")) {
                bullets.add(bullet("booking", "课程预约页", "查看课程时间、余位和预约状态。"));
                bullets.add(bullet("coach", "教练主页", "查看教练信息、擅长项目和排班。"));
                bullets.add(bullet("plan", "训练计划页", "管理训练计划、打卡记录和完成情况。"));
                bullets.add(bullet("renewal", "会员续费页", "查看会籍状态、续费优惠和到期提醒。"));
            } else if (containsAny(source, "婚纱", "摄影", "摄影师")) {
                bullets.add(bullet("packages", "套餐列表页", "查看拍摄套餐、风格和价格。"));
                bullets.add(bullet("photographer", "摄影师主页", "查看摄影师作品、风格和服务城市。"));
                bullets.add(bullet("slot", "档期选择页", "选择拍摄日期、时段和到店安排。"));
                bullets.add(bullet("confirm", "预约确认页", "确认套餐、人数和预订信息。"));
            } else if (containsAny(source, "宠物", "寄养", "体检")) {
                bullets.add(bullet("pets", "宠物档案页", "查看宠物资料、免疫和体检记录。"));
                bullets.add(bullet("care", "服务预约页", "查看寄养、护理和体检预约安排。"));
                bullets.add(bullet("orders", "服务订单页", "跟踪服务状态、价格和完成情况。"));
                bullets.add(bullet("reviews", "门店评价页", "查看门店口碑、服务评价和回访。"));
            } else if (containsAny(source, "养老", "照护", "老人档案")) {
                bullets.add(bullet("elder", "老人档案页", "查看老人资料、照护等级和家属绑定。"));
                bullets.add(bullet("care", "照护计划页", "制定照护计划、班次和服务安排。"));
                bullets.add(bullet("health", "健康记录页", "查看健康指标、用药和异常提醒。"));
                bullets.add(bullet("family", "家属通知页", "同步服务状态、预约安排和通知消息。"));
            }
        } else if ("article".equals(domain.primaryObject())) {
            bullets.add(bullet("topic", "选题池", "管理选题优先级、负责人和推进状态。"));
            bullets.add(bullet("editor", "稿件编辑页", "承接正文编辑、素材整理和版本修改。"));
            bullets.add(bullet("review", "审核发布页", "处理审校意见、发布时间和发布确认。"));
            bullets.add(bullet("special", "专题运营页", "管理专题聚合页和内容分发效果。"));
        } else if ("candidate".equals(domain.primaryObject())) {
            bullets.add(bullet("candidates", "候选人列表页", "集中浏览候选人、阶段与评分信息。"));
            bullets.add(bullet("pipeline", "流程看板页", "按阶段管理候选人推进和卡点。"));
            bullets.add(bullet("interviews", "面试安排页", "查看轮次安排、时间冲突和面试官分配。"));
            bullets.add(bullet("offer", "Offer 进度页", "跟踪 offer、确认结果和入职状态。"));
        } else if ("subscription".equals(domain.primaryObject())) {
            if (containsAny(source, "共享办公", "工位", "会议室")) {
                bullets.add(bullet("overview", "空间运营总览页", "查看工位、会议室、今日预约和账单状态。"));
                bullets.add(bullet("desks", "工位预订页", "浏览可用工位、工区和会员预订入口。"));
                bullets.add(bullet("rooms", "会议室排期页", "管理会议室时间轴、冲突提醒和预订状态。"));
                bullets.add(bullet("billing", "账单与会员页", "查看会员权益、账单记录和社区活动。"));
            } else {
                bullets.add(bullet("overview", "平台总览页", "查看订阅规模、续费风险和账单状态。"));
                bullets.add(bullet("plans", "套餐管理页", "维护套餐配置、价格和权益范围。"));
                bullets.add(bullet("billing", "账单列表页", "查看账单状态、支付记录和异常账单。"));
                bullets.add(bullet("renewal", "续费进度页", "跟踪续费提醒、失败原因和生命周期变化。"));
            }
        } else if ("class".equals(domain.primaryObject())) {
            if (containsAny(source, "体育", "俱乐部", "赛事", "教练")) {
                bullets.add(bullet("events", "赛事安排页", "管理赛事时间、场地和赛程信息。"));
                bullets.add(bullet("signup", "会员报名页", "查看报名状态、缴费情况和名单。"));
                bullets.add(bullet("coach", "教练排班页", "安排教练值班、训练时段和场馆资源。"));
                bullets.add(bullet("scores", "成绩记录页", "查看成绩、排名和赛后数据。"));
            } else {
                bullets.add(bullet("overview", "教务总览页", "查看班级规模、课程安排和待处理事项。"));
                bullets.add(bullet("schedule", "课程表页", "安排课程时间、教师和教室资源。"));
                bullets.add(bullet("classroom", "班级管理页", "维护班级信息、学生名单和通知触达。"));
                bullets.add(bullet("grades", "成绩录入页", "录入成绩、发布结果并查看统计。"));
            }
        } else if ("patient".equals(domain.primaryObject())) {
            bullets.add(bullet("beds", "床位看板", "查看床位分布、占用状态和待出院信息。"));
            bullets.add(bullet("patient", "患者详情页", "查看病情摘要、护理记录和费用清单。"));
            bullets.add(bullet("orders", "医嘱执行页", "执行医嘱、回填结果并跟踪异常。"));
            bullets.add(bullet("discharge", "出院流程页", "管理出院评估、费用结算和出院确认。"));
        } else if ("sample".equals(domain.primaryObject())) {
            bullets.add(bullet("samples", "样本列表页", "查看样本状态、来源和实验分配。"));
            bullets.add(bullet("records", "实验记录页", "记录实验步骤、结果和异常说明。"));
            bullets.add(bullet("equipment", "仪器预约页", "查看仪器可用时间和预约安排。"));
            bullets.add(bullet("reports", "报告归档页", "查看结果分析、报告版本和归档状态。"));
        } else if ("event".equals(domain.primaryObject())) {
            bullets.add(bullet("events", "赛事安排页", "管理赛事时间、场地和赛程信息。"));
            bullets.add(bullet("signup", "会员报名页", "查看报名状态、缴费情况和名单。"));
            bullets.add(bullet("coach", "教练排班页", "安排教练值班、训练时段和场馆资源。"));
            bullets.add(bullet("scores", "成绩记录页", "查看成绩、排名和赛后数据。"));
        } else if ("thread".equals(domain.primaryObject())) {
            bullets.add(bullet("boards", "版块列表页", "浏览版块分组、热度和最新主题。"));
            bullets.add(bullet("thread", "帖子详情页", "查看主帖内容、回复楼层和互动状态。"));
            bullets.add(bullet("member", "用户主页", "查看成员资料、发帖记录和活跃状态。"));
            bullets.add(bullet("composer", "发帖入口", "创建新帖并选择版块与标签。"));
        } else if ("job".equals(domain.primaryObject())) {
            if (containsAny(source, "仓储", "wms", "入库", "出库", "拣货", "波次", "盘点")) {
                bullets.add(bullet("inbound", "入库上架页", "处理入库、上架和库位分配。"));
                bullets.add(bullet("wave", "拣货波次页", "管理波次分组、任务分配和执行状态。"));
                bullets.add(bullet("outbound", "出库复核页", "完成出库复核、装车和发运确认。"));
                bullets.add(bullet("inventory", "库存盘点页", "查看库存差异、盘点任务和处理结果。"));
            } else if (containsAny(source, "物流", "履约", "司机", "签收", "路线")) {
                bullets.add(bullet("overview", "履约总览页", "查看订单履约状态和异常分布。"));
                bullets.add(bullet("route", "路线分配页", "管理路线、站点和司机派发。"));
                bullets.add(bullet("proof", "签收回传页", "查看签收回传、异常说明和处理反馈。"));
                bullets.add(bullet("driver", "司机详情页", "查看司机任务、位置和完成情况。"));
            } else if (containsAny(source, "mes", "排产", "工厂", "质检", "班组")) {
                bullets.add(bullet("schedule", "工单排产页", "安排工单顺序、产线和班组资源。"));
                bullets.add(bullet("equipment", "设备状态页", "查看设备状态、停机原因和维护提醒。"));
                bullets.add(bullet("quality", "质检记录页", "记录质检结果、异常和返工情况。"));
                bullets.add(bullet("team", "班组看板", "查看班组负载、产能和异常处理。"));
            } else if (containsAny(source, "供应链", "预测", "补货", "库存分布")) {
                bullets.add(bullet("forecast", "需求预测页", "查看预测结果、趋势和计划偏差。"));
                bullets.add(bullet("replenish", "补货建议页", "查看补货建议和执行优先级。"));
                bullets.add(bullet("inventory", "库存分布页", "查看多仓库存、周转和缺货风险。"));
                bullets.add(bullet("alert", "异常预警页", "集中处理异常预警和计划偏差。"));
            }
        } else if ("record".equals(domain.primaryObject())) {
            if (containsAny(source, "政务", "办件", "材料提交")) {
                bullets.add(bullet("matters", "事项列表页", "浏览事项分类、办理条件和入口。"));
                bullets.add(bullet("materials", "材料提交页", "上传材料、补件和查看提交状态。"));
                bullets.add(bullet("progress", "审批进度页", "查看办理节点、部门流转和结果通知。"));
                bullets.add(bullet("center", "个人办件中心", "集中查看办件、消息和待处理提醒。"));
            } else if (containsAny(source, "博物馆", "展览", "展品", "导览")) {
                bullets.add(bullet("exhibitions", "展览列表页", "浏览展览主题、时间和活动安排。"));
                bullets.add(bullet("artifact", "展品详情页", "查看展品故事、图文和语音导览。"));
                bullets.add(bullet("route", "路线推荐页", "根据时间和兴趣推荐观展路线。"));
                bullets.add(bullet("events", "会员活动页", "查看会员活动、预约和权益。"));
            } else if (containsAny(source, "体育俱乐部", "赛事安排", "会员报名", "教练排班")) {
                bullets.add(bullet("events", "赛事安排页", "管理赛事时间、场地和报名信息。"));
                bullets.add(bullet("signup", "会员报名页", "查看报名状态、名单和缴费情况。"));
                bullets.add(bullet("coach", "教练排班页", "安排教练值班和训练资源。"));
                bullets.add(bullet("scores", "成绩记录页", "查看成绩、排名和赛后数据。"));
            } else if (containsAny(source, "农业", "种植", "地块", "农事", "产量")) {
                bullets.add(bullet("fields", "地块看板", "查看地块状态、作物分布和重点提醒。"));
                bullets.add(bullet("tasks", "农事记录页", "记录播种、施肥和巡检任务。"));
                bullets.add(bullet("devices", "设备监测页", "查看环境设备和异常状态。"));
                bullets.add(bullet("yield", "产量统计页", "查看产量、品质和收成趋势。"));
            }
        } else if ("application".equals(domain.primaryObject())) {
            if (containsAny(source, "理赔", "报案", "赔付")) {
                bullets.add(bullet("claims", "报案列表页", "查看报案状态、风险等级和跟进人。"));
                bullets.add(bullet("detail", "理赔详情页", "查看案件信息、材料和审核意见。"));
                bullets.add(bullet("review", "资料审核页", "处理资料完整性、补件和核验。"));
                bullets.add(bullet("payout", "赔付进度页", "跟踪赔付审核、支付和结果通知。"));
            } else if (containsAny(source, "贷款", "放款", "风控")) {
                bullets.add(bullet("applications", "申请列表页", "查看贷款申请、优先级和当前状态。"));
                bullets.add(bullet("profile", "客户画像页", "查看客户信息、信用特征和风险提示。"));
                bullets.add(bullet("review", "资料审核页", "处理资料核验、补件和审批意见。"));
                bullets.add(bullet("funding", "放款进度页", "查看审批完成后的放款流转和结果。"));
            } else if (containsAny(source, "政务", "办件", "事项", "材料提交")) {
                bullets.add(bullet("matters", "事项列表页", "浏览事项分类、办理条件和入口。"));
                bullets.add(bullet("materials", "材料提交页", "上传材料、补件和查看提交状态。"));
                bullets.add(bullet("progress", "审批进度页", "查看办理节点、部门流转和结果通知。"));
                bullets.add(bullet("center", "个人办件中心", "集中查看办件、消息和待处理提醒。"));
            }
        } else if ("quote".equals(domain.primaryObject())) {
            bullets.add(bullet("suppliers", "供应商列表页", "查看供应商分层、状态和合作记录。"));
            bullets.add(bullet("rfq", "询价单页", "发起询价、管理需求和回复截止时间。"));
            bullets.add(bullet("compare", "报价对比页", "对比价格、交期、条款和推荐结果。"));
            bullets.add(bullet("contract", "合同归档页", "查看审批流、合同版本和归档状态。"));
        }

        if (!bullets.isEmpty()) {
            return dedupeBullets(bullets);
        }
        return buildDefaultScreenBullets(domain);
    }

    private List<PrototypeBundle.ScreenBullet> dedupeBullets(List<PrototypeBundle.ScreenBullet> bullets) {
        return bullets.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(PrototypeBundle.ScreenBullet::getLabel, bullet -> bullet, (left, right) -> left, LinkedHashMap::new),
                        map -> new ArrayList<>(map.values())
                ));
    }

    private List<PrototypeBundle.ScreenBullet> buildBulletsFromInteractionModes(DomainSignals domain) {
        List<PrototypeBundle.ScreenBullet> bullets = new ArrayList<>();
        List<String> modes = domain.interactionModes();

        if (modes.contains("feed-first")) {
            bullets.add(bullet("home", "社区首页", "展示推荐流、关注流与内容发现。"));
        } else if (modes.contains("dashboard")) {
            bullets.add(bullet("dashboard", "总览看板", "集中展示关键指标、风险信号与待处理事项。"));
        } else if (modes.contains("pipeline")) {
            bullets.add(bullet("pipeline", "线索池首页", "展示主要对象列表、阶段分布与批量处理入口。"));
        } else if (modes.contains("scheduler")) {
            bullets.add(bullet("home", "预约首页", "展示服务入口、可预约时段与状态概览。"));
        } else if (modes.contains("case-management")) {
            bullets.add(bullet("home", "案件总览", "展示案件列表、关键节点与负责人信息。"));
        }

        if (modes.contains("detail-consumption")) {
            bullets.add(bullet("detail", "帖子详情页", "包含大图/视频展示、作者信息、评论区及互动按钮。"));
        } else if (modes.contains("workflow")) {
            bullets.add(bullet("detail", "详情处理页", "展示对象详情、状态推进与关键动作入口。"));
        } else if (modes.contains("document-workspace")) {
            bullets.add(bullet("documents", "文书工作区", "集中处理文档、批注、审核状态与证据材料。"));
        }

        if (modes.contains("creator-profile")) {
            bullets.add(bullet("profile", "用户个人主页", "展示用户信息、笔记分类及瀑布流作品。"));
        } else if (modes.contains("assignment")) {
            bullets.add(bullet("owners", "负责人视图", "展示负责人、分配状态与负载情况。"));
        }

        if (modes.contains("composer")) {
            bullets.add(bullet("publish", "发布笔记页", "一个简洁的发布流程界面。"));
        } else if (modes.contains("service-catalog")) {
            bullets.add(bullet("services", "服务选择页", "展示服务项目、医生信息与预约入口。"));
        } else if (modes.contains("review")) {
            bullets.add(bullet("review", "审核页", "用于审核、批注与推进当前对象。"));
        }

        return bullets.stream()
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(PrototypeBundle.ScreenBullet::getLabel, bullet -> bullet, (left, right) -> left, LinkedHashMap::new),
                        map -> new ArrayList<>(map.values())
                ));
    }

    private PrototypeBundle.ScreenBullet buildBulletFromPage(ProjectManifest.PageSpec page, DomainSignals domain) {
        String route = safe(page.getRoute()).toLowerCase(Locale.ROOT);
        String description = safe(page.getDescription());
        String label;
        String desc;

        if (containsAny(route, "discover") || description.contains("发现页")) {
            label = "发现页";
        } else if (containsAny(route, "following") || description.contains("关注流")) {
            label = "关注流页";
        } else if (containsAny(route, "home", "index") || description.contains("首页")) {
            label = domain.interactionModes().contains("feed-first") ? "社区首页" : "首页";
        } else if (containsAny(route, "publish", "post/new", "create") || description.contains("发布")) {
            label = description.contains("笔记") ? "发布笔记页" : "发布页";
        } else if (containsAny(route, "search") || description.contains("搜索")) {
            label = "搜索页";
        } else if (containsAny(route, "user", "profile", "me") || description.contains("主页")) {
            label = description.contains("创作者") ? "创作者主页" : "用户个人主页";
        } else if (containsAny(route, "detail", ":id", ":postid", ":petid", ":slug") || description.contains("详情")) {
            label = description.contains("帖子") ? "帖子详情页" : "详情页";
        } else {
            label = splitSentence(description)[0];
        }

        String[] parts = splitSentence(description);
        desc = parts.length > 1 ? parts[1] : description;
        return bullet(resolveScreenId(page.getRoute()), label, desc);
    }

    private String[] splitSentence(String description) {
        String text = safe(description).trim();
        if (text.isBlank()) {
            return new String[]{"页面", ""};
        }
        String[] parts = text.split("[，。:：]", 2);
        if (parts.length == 1) {
            return new String[]{parts[0], text};
        }
        return parts;
    }

    private PrototypeBundle.ScreenBullet bullet(String id, String label, String description) {
        return PrototypeBundle.ScreenBullet.builder()
                .id(id)
                .label(label)
                .description(description)
                .build();
    }

    private String buildNextStepNarrative(String referenceSignal, StructuralProfile profile, String styleSummary) {
        if (referenceSignal != null && !referenceSignal.isBlank()) {
            return "我将先为您创建一个符合“" + referenceSignal + "”视觉风格（" + styleSummary + "）的设计系统，然后开始设计这些页面。您看这样安排可以吗？";
        }
        return "我将先基于" + humanizeInteractionModes(profile.interactionModes(), "当前需求")
                + "整理设计系统，视觉上会采用" + styleSummary + "，再开始生成这些页面。";
    }

    private String buildStyleSummary(ProjectManifest manifest, DomainSignals domain, StructuralProfile profile) {
        Map<String, String> meta = ensureMeta(manifest);
        String primaryColor = humanizeColorToken(meta.getOrDefault("visual_primaryColor", domain.defaultPrimaryColor()));
        String cardShape = humanizeCardShape(meta.getOrDefault("visual_cardClass", "bg-white shadow-sm rounded-2xl p-6"));
        String fontTone = humanizeTypographyDescriptor(meta.getOrDefault("visual_fontFamily", "font-sans"), profile);
        String imagery = humanizeImageryDescriptor(manifest.getDesignContract(), profile);
        return primaryColor + "色调、" + cardShape + "、" + fontTone + "与" + imagery;
    }

    private String humanizeInteractionModes(List<String> interactionModes, String fallback) {
        if (interactionModes == null || interactionModes.isEmpty()) {
            return fallback;
        }
        return interactionModes.stream()
                .map(this::humanizeInteractionMode)
                .collect(Collectors.joining("、"));
    }

    private String humanizeInteractionMode(String mode) {
        return switch (safe(mode).toLowerCase(Locale.ROOT)) {
            case "feed-first" -> "内容优先发现";
            case "detail-consumption" -> "沉浸式详情消费";
            case "creator-profile" -> "创作者身份表达";
            case "composer" -> "轻量内容发布";
            case "pipeline" -> "流程推进";
            case "queue" -> "队列处理";
            case "assignment" -> "负责人分配";
            case "workflow" -> "状态流转";
            case "scheduler" -> "预约排期";
            case "case-management" -> "事项详情处理";
            case "service-catalog" -> "服务选择";
            case "visit-flow" -> "到诊流程";
            case "dashboard" -> "实时看板";
            case "dispatch" -> "调度响应";
            case "alert-workflow" -> "告警处理";
            case "document-workspace" -> "文档工作区";
            case "review" -> "审核协作";
            case "listing" -> "列表展示";
            case "reservation" -> "预约/下单";
            case "detail" -> "详情浏览";
            case "order-workflow" -> "订单流转";
            case "schedule" -> "排期管理";
            case "workspace" -> "工作台操作";
            case "progress" -> "进度跟踪";
            default -> mode;
        };
    }

    private String buildRationale(DomainSignals domain, String referenceSignal) {
        if (!referenceSignal.isBlank()) {
            return "系统从参考对象中提炼了页面节奏与视觉线索，再结合业务主循环生成页面集合，而不是直接套用行业模板。";
        }
        return "系统未依赖外部参考物，而是根据核心对象、动作、状态与约束，推导出最适合的交互结构。";
    }

    private String buildConfidenceNote(String referenceSignal) {
        if (!referenceSignal.isBlank()) {
            return "如需纠偏，可在生成前修改参考物、页面集合或体验特征。";
        }
        return "如需纠偏，可在生成前补充参考产品、关键页面或希望强化的体验气质。";
    }

    private String extractReferenceSignal(ProjectManifest manifest) {
        String intent = safe(manifest.getUserIntent()).trim();
        if (intent.isBlank()) {
            return "";
        }
        int likeIndex = intent.indexOf("像");
        if (likeIndex >= 0) {
            int end = intent.indexOf("一样", likeIndex);
            if (end > likeIndex) {
                return intent.substring(likeIndex, end + 2);
            }
            end = intent.indexOf("的", likeIndex);
            if (end > likeIndex) {
                return intent.substring(likeIndex, end);
            }
        }
        String lower = intent.toLowerCase(Locale.ROOT);
        if (lower.contains("like ")) {
            return intent.substring(lower.indexOf("like "));
        }
        return "";
    }

    private StructuralProfile inferStructuralProfile(ProjectManifest manifest, DomainSignals domain) {
        RequirementEvidence evidence = buildRequirementEvidence(manifest);
        String source = evidence.combinedSource();
        String intentSource = evidence.intentSource();

        ProjectManifest.DesignContract contract = manifest.getDesignContract();
        boolean consume = containsAny(source, "社区", "内容", "帖子", "发现", "feed", "note", "share", "social")
                || (contract != null && (contract.getPrimaryGoal() == ProjectManifest.PrimaryGoal.READ
                || contract.getPrimaryGoal() == ProjectManifest.PrimaryGoal.DISCOVER
                || contract.getPrimaryGoal() == ProjectManifest.PrimaryGoal.DISCUSS));
        boolean operate = containsAny(source, "管理", "处理", "工单", "预约", "支付", "监控", "商机", "案件", "审批", "排班", "workflow", "ticket", "ops", "schedule", "crm", "case")
                || (contract != null && (contract.getPrimaryGoal() == ProjectManifest.PrimaryGoal.TRANSACT
                || contract.getPrimaryGoal() == ProjectManifest.PrimaryGoal.MONITOR
                || contract.getPrimaryGoal() == ProjectManifest.PrimaryGoal.LEARN
                || contract.getPrimaryGoal() == ProjectManifest.PrimaryGoal.COMPARE));

        String primaryMode = consume && operate ? "HYBRID" : consume ? "CONSUME" : "OPERATE";
        String objectMultiplicity = buildEntities(domain).size() > 2 ? "MULTI" : "SINGLE";

        boolean listBatch = containsAny(source, "列表", "批量", "队列", "pipeline", "看板", "dashboard", "分配", "工单", "线索")
                || domain.interactionModes().stream().anyMatch(mode -> containsAny(mode, "queue", "pipeline", "dashboard", "dispatch"));
        boolean singleRecord = containsAny(intentSource, "详情", "档期", "预约", "就诊", "案件", "表单", "记录", "detail", "slot", "visit")
                || domain.interactionModes().stream().anyMatch(mode -> containsAny(mode, "case-management", "scheduler", "detail"));
        String workflowMode = listBatch && singleRecord ? "HYBRID" : listBatch ? "LIST_BATCH" : singleRecord ? "SINGLE_RECORD" : "HYBRID";

        String timeModel = containsAny(source, "档期", "预约", "排期", "时段", "calendar", "schedule", "slot")
                ? "SCHEDULE"
                : containsAny(source, "实时", "告警", "监控", "预警", "realtime", "alert", "monitor")
                ? "REALTIME"
                : containsAny(source, "任务", "截止", "作业", "续费", "deadline", "follow-up", "milestone", "progress")
                ? "DEADLINE"
                : "NONE";

        String stateModel = domain.states().size() >= 4 || containsAny(source, "阶段", "状态", "审批", "支付", "就诊", "progress", "status", "stage", "workflow")
                ? "STRONG"
                : "LIGHT";

        String collaborationMode = domain.roles().size() <= 2 && domain.roles().stream().allMatch(role -> containsAny(role.toLowerCase(Locale.ROOT), "viewer", "creator"))
                ? "SOLO"
                : domain.roles().size() > 2
                ? "MULTI_ROLE"
                : "ASSIGNED";

        String detailMode = containsAny(source, "详情", "评论", "收藏", "作者", "detail", "comment", "like") && primaryMode.equals("CONSUME")
                ? "CONSUME_DETAIL"
                : containsAny(source, "操作", "分配", "确认", "推进", "审批", "支付", "就诊", "assign", "confirm", "advance", "approve")
                ? "OPERATION_DETAIL"
                : primaryMode.equals("HYBRID")
                ? "MIXED"
                : primaryMode.equals("OPERATE")
                ? "OPERATION_DETAIL"
                : "CONSUME_DETAIL";

        List<String> evidenceSignals = new ArrayList<>();
        if (consume) evidenceSignals.add("consume");
        if (operate) evidenceSignals.add("operate");
        if (!extractReferenceSignal(manifest).isBlank()) evidenceSignals.add(extractReferenceSignal(manifest));
        if (!"NONE".equals(timeModel)) evidenceSignals.add("time:" + timeModel);
        if ("STRONG".equals(stateModel)) evidenceSignals.add("stateful");
        evidenceSignals.addAll(domain.interactionModes());

        return new StructuralProfile(
                primaryMode,
                objectMultiplicity,
                workflowMode,
                timeModel,
                stateModel,
                collaborationMode,
                detailMode,
                domain.interactionModes(),
                evidenceSignals
        );
    }

    private String resolveRadiusStyle(String cardClass) {
        String value = safe(cardClass);
        if (value.contains("rounded-3xl")) return "soft-xl";
        if (value.contains("rounded-2xl")) return "soft-lg";
        if (value.contains("rounded-xl")) return "rounded";
        return "soft-lg";
    }

    private int estimateSecondaryCollectionSize(String entityName, int primaryCount) {
        String lower = safe(entityName).toLowerCase(Locale.ROOT);
        if (containsAny(lower, "owner", "doctor")) return Math.max(4, Math.min(12, primaryCount / 2));
        if (containsAny(lower, "slot", "schedule", "followup", "record")) return Math.max(6, primaryCount);
        return Math.max(4, primaryCount / 2);
    }

    private void addEntity(List<PrototypeBundle.EntityModel> entities, String name, String purpose, List<String> fields) {
        entities.add(PrototypeBundle.EntityModel.builder()
                .name(name)
                .purpose(purpose)
                .sampleFields(fields)
                .build());
    }

    private Map<String, String> ensureMeta(ProjectManifest manifest) {
        if (manifest.getMetaData() == null) {
            manifest.setMetaData(new HashMap<>());
        }
        return manifest.getMetaData();
    }

    private DomainSignals detectDomainSignals(ProjectManifest manifest) {
        RequirementEvidence evidence = buildRequirementEvidence(manifest);

        LinkedHashMap<String, Integer> scores = new LinkedHashMap<>();
        scores.put("crm", scoreKeywords(evidence,
                "crm", "线索", "商机", "pipeline", "续费", "客户成功", "销售", "跟进", "机会", "lead", "opportunity")
                + scoreHints(evidence.objectHints(), "lead"));
        scores.put("legal", scoreKeywords(evidence,
                "律师", "案件", "法务", "证据", "文书", "开庭", "hearing", "legal", "lawsuit", "诉讼", "合同纠纷"));
        scores.put("clinic", scoreKeywords(evidence,
                "医美", "诊所", "医生", "到诊", "就诊", "病历", "门诊", "治疗", "术后", "患者")
                + scoreHints(evidence.objectHints(), "appointment", "patient"));
        scores.put("service-booking", scoreKeywords(evidence,
                "预约", "档期", "排期", "时段", "上门", "到店", "摄影师", "寄养", "美容师", "师傅", "服务预约", "预约下单", "照护", "套餐")
                + scoreHints(evidence.objectHints(), "booking")
                - scoreKeywords(evidence, "房源", "车源", "地图找房", "票务", "商品目录", "多站点订单"));
        scores.put("procurement", scoreKeywords(evidence,
                "采购", "寻源", "供应商", "询价", "报价", "合同归档", "报价对比", "rfq", "sourcing")
                + scoreHints(evidence.objectHints(), "quote"));
        scores.put("commerce", scoreKeywords(evidence,
                "房源", "车源", "商品", "订单", "支付", "票务", "房型", "地图找房", "经纪人", "车型", "租车", "分销", "外贸", "询盘",
                "发货", "跨境电商", "广告投放", "利润分析", "药品", "房东", "目的地", "线路", "直播", "主播", "挂车")
                + scoreHints(evidence.objectHints(), "listing", "order", "subscription"));
        scores.put("ops", scoreKeywords(evidence,
                "运维", "监控", "告警", "预警", "站点", "基站", "soc", "noc", "故障", "远程控制", "网络质量", "能耗", "设备监测", "告警队列")
                + scoreHints(evidence.objectHints(), "ticket")
                - scoreKeywords(evidence, "履约", "仓储", "排产", "波次", "库位", "盘点"));
        scores.put("operations-workflow", scoreKeywords(evidence,
                "履约", "路线", "司机", "签收", "排产", "班组", "质检", "mes", "工厂", "仓储", "wms", "入库", "出库", "库位", "拣货",
                "波次", "盘点", "补货", "供应链", "库存分布", "采购计划", "农事", "种植", "产量", "整改", "巡检", "批发分销")
                + scoreHints(evidence.objectHints(), "job", "inventory"));
        scores.put("recruiting", scoreKeywords(evidence,
                "招聘", "ats", "候选人", "简历", "面试", "offer", "岗位", "人才", "interview")
                + scoreHints(evidence.objectHints(), "candidate"));
        scores.put("people-ops", scoreKeywords(evidence,
                "薪酬", "人事", "员工", "考勤", "薪资", "工资", "补卡", "请假", "员工档案", "member center", "会员中心")
                + scoreHints(evidence.objectHints(), "employee"));
        scores.put("sports", scoreKeywords(evidence,
                "体育", "俱乐部", "赛事", "报名", "教练", "赛程", "成绩记录", "社群动态")
                + scoreHints(evidence.objectHints(), "event"));
        scores.put("education-ops", scoreKeywords(evidence,
                "教务", "班级", "成绩", "家校", "课程表", "学校", "学生", "教师", "家校沟通", "通知中心")
                + scoreHints(evidence.objectHints(), "class", "student")
                - scoreKeywords(evidence, "体育", "俱乐部", "赛事", "教练"));
        scores.put("learning", scoreKeywords(evidence,
                "在线课程", "章节", "作业提交", "学习进度", "训练营", "学习平台", "课程首页", "课程详情", "题库", "作业")
                + scoreHints(evidence.objectHints(), "course")
                - scoreKeywords(evidence, "教务", "班级", "成绩"));
        scores.put("inpatient", scoreKeywords(evidence,
                "住院", "床位", "医嘱", "出院", "病区", "患者详情", "费用清单", "护理记录")
                + scoreHints(evidence.objectHints(), "patient"));
        scores.put("lab", scoreKeywords(evidence,
                "实验室", "样本", "实验记录", "仪器预约", "结果分析", "报告归档", "检验", "实验")
                + scoreHints(evidence.objectHints(), "sample"));
        scores.put("review-workspace", scoreKeywords(evidence,
                "理赔", "报案", "赔付", "贷款", "审批", "放款", "授信", "申请列表", "资料审核", "风控评分", "材料提交", "办件", "赔付进度")
                + scoreHints(evidence.objectHints(), "claim", "application"));
        scores.put("editorial", scoreKeywords(evidence,
                "新闻", "稿件", "选题", "专题", "记者", "编辑部", "媒体", "newsroom", "article", "publish")
                + scoreHints(evidence.objectHints(), "article", "story")
                - scoreKeywords(evidence, "瀑布流", "种草", "穿搭"));
        scores.put("forum", scoreKeywords(evidence,
                "论坛", "版块", "回帖", "回复", "thread", "board", "楼层", "发帖入口", "评论回复")
                + scoreHints(evidence.objectHints(), "thread"));
        scores.put("billing", scoreKeywords(evidence,
                "计费", "账单", "续费", "续费提醒", "支付记录", "套餐管理", "订阅", "invoice", "billing")
                + scoreHints(evidence.objectHints(), "subscription"));
        scores.put("content", scoreKeywords(evidence,
                "小红书", "瀑布流", "穿搭", "种草", "探店", "灵感", "笔记", "帖子", "收藏", "发帖", "feed", "discover")
                + scoreHints(evidence.objectHints(), "post", "thread")
                - scoreKeywords(evidence, "新闻", "稿件", "论坛", "版块", "办件", "合同"));

        String bestKey = "generic";
        int bestScore = 4;
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            if (entry.getValue() > bestScore) {
                bestKey = entry.getKey();
                bestScore = entry.getValue();
            }
        }

        return switch (bestKey) {
            case "crm" -> DomainSignals.crm();
            case "legal" -> DomainSignals.legal();
            case "clinic" -> DomainSignals.clinic();
            case "service-booking" -> DomainSignals.serviceBooking();
            case "procurement" -> DomainSignals.procurement();
            case "commerce" -> DomainSignals.commerce();
            case "ops" -> DomainSignals.ops();
            case "operations-workflow" -> DomainSignals.operationsWorkflow();
            case "recruiting" -> DomainSignals.recruiting();
            case "people-ops" -> DomainSignals.peopleOps();
            case "sports" -> DomainSignals.sports();
            case "education-ops" -> DomainSignals.educationOps();
            case "learning" -> DomainSignals.learning();
            case "inpatient" -> DomainSignals.inpatient();
            case "lab" -> DomainSignals.lab();
            case "review-workspace" -> DomainSignals.reviewWorkspace();
            case "editorial" -> DomainSignals.editorial();
            case "forum" -> DomainSignals.forum();
            case "billing" -> DomainSignals.billing();
            case "content" -> DomainSignals.content();
            default -> DomainSignals.generic(manifest);
        };
    }

    private RequirementEvidence buildRequirementEvidence(ProjectManifest manifest) {
        String intentSource = safe(manifest.getUserIntent()).toLowerCase(Locale.ROOT);
        List<String> planningFragments = new ArrayList<>();
        planningFragments.add(safe(manifest.getArchetype()));
        planningFragments.add(safe(manifest.getOverview()));
        if (manifest.getPages() != null) {
            for (ProjectManifest.PageSpec page : manifest.getPages()) {
                planningFragments.add(safe(page.getRoute()));
                planningFragments.add(safe(page.getDescription()));
                if (page.getComponents() != null) {
                    planningFragments.add(String.join(" ", page.getComponents()));
                }
            }
        }
        if (manifest.getTaskFlows() != null) {
            for (ProjectManifest.TaskFlow taskFlow : manifest.getTaskFlows()) {
                planningFragments.add(safe(taskFlow.getDescription()));
                if (taskFlow.getSteps() != null) {
                    planningFragments.add(String.join(" ", taskFlow.getSteps()));
                }
            }
        }
        if (manifest.getFeatures() != null) {
            for (ProjectManifest.Feature feature : manifest.getFeatures()) {
                planningFragments.add(safe(feature.getName()));
                planningFragments.add(safe(feature.getDescription()));
            }
        }
        if (manifest.getDesignContract() != null) {
            planningFragments.add(safe(manifest.getDesignContract().getValidationNotes()));
        }

        String planningSource = String.join(" ", planningFragments).toLowerCase(Locale.ROOT);
        String combined = (intentSource + " " + planningSource).trim();
        return new RequirementEvidence(
                intentSource,
                planningSource,
                combined,
                extractObjectHints(combined),
                extractActionHints(combined),
                extractRoleHints(combined),
                extractStateHints(combined)
        );
    }

    private LinkedHashSet<String> extractObjectHints(String source) {
        LinkedHashSet<String> hints = new LinkedHashSet<>();
        if (containsAny(source, "线索", "商机", "lead", "opportunity")) hints.add("lead");
        if (containsAny(source, "预约", "档期", "booking", "slot")) hints.add("booking");
        if (containsAny(source, "患者", "住院", "patient")) hints.add("patient");
        if (containsAny(source, "赛事", "比赛", "event", "赛程")) hints.add("event");
        if (containsAny(source, "房源", "车源", "listing", "商品")) hints.add("listing");
        if (containsAny(source, "订单", "运单", "shipment", "order")) hints.add("order");
        if (containsAny(source, "candidate", "候选人", "简历")) hints.add("candidate");
        if (containsAny(source, "员工", "employee", "薪酬", "人事")) hints.add("employee");
        if (containsAny(source, "班级", "学生", "student", "class")) hints.add("class");
        if (containsAny(source, "课程", "course", "章节")) hints.add("course");
        if (containsAny(source, "理赔", "loan", "claim", "申请")) hints.add("claim");
        if (containsAny(source, "样本", "sample", "实验室")) hints.add("sample");
        if (containsAny(source, "询价", "报价", "quote", "rfq")) hints.add("quote");
        if (containsAny(source, "subscription", "计费", "账单", "billing")) hints.add("subscription");
        if (containsAny(source, "稿件", "文章", "story", "article")) hints.add("article");
        if (containsAny(source, "thread", "论坛", "帖子", "版块")) hints.add("thread");
        if (containsAny(source, "ticket", "工单", "告警")) hints.add("ticket");
        if (containsAny(source, "库存", "仓储", "排产", "job", "inventory")) hints.add("inventory");
        return hints;
    }

    private LinkedHashSet<String> extractActionHints(String source) {
        LinkedHashSet<String> hints = new LinkedHashSet<>();
        if (containsAny(source, "审核", "approve", "review", "批注")) hints.add("review");
        if (containsAny(source, "预约", "排期", "schedule")) hints.add("schedule");
        if (containsAny(source, "支付", "pay", "下单")) hints.add("transact");
        if (containsAny(source, "发布", "post", "publish")) hints.add("publish");
        if (containsAny(source, "分配", "assign", "派发")) hints.add("assign");
        if (containsAny(source, "告警", "监控", "alert", "monitor")) hints.add("monitor");
        return hints;
    }

    private LinkedHashSet<String> extractRoleHints(String source) {
        LinkedHashSet<String> hints = new LinkedHashSet<>();
        if (containsAny(source, "医生", "doctor")) hints.add("doctor");
        if (containsAny(source, "经纪人", "broker", "agent")) hints.add("agent");
        if (containsAny(source, "招聘官", "recruiter", "面试官")) hints.add("recruiter");
        if (containsAny(source, "老师", "teacher", "guardian", "家长")) hints.add("teacher");
        if (containsAny(source, "律师", "lawyer")) hints.add("lawyer");
        if (containsAny(source, "客服", "operator", "dispatcher")) hints.add("operator");
        return hints;
    }

    private LinkedHashSet<String> extractStateHints(String source) {
        LinkedHashSet<String> hints = new LinkedHashSet<>();
        if (containsAny(source, "状态", "stage", "进度", "progress")) hints.add("progress");
        if (containsAny(source, "审批", "审核", "review")) hints.add("review");
        if (containsAny(source, "支付", "发货", "出院", "完成")) hints.add("completion");
        if (containsAny(source, "告警", "异常", "风险")) hints.add("risk");
        return hints;
    }

    private int scoreKeywords(RequirementEvidence evidence, String... keywords) {
        int score = 0;
        for (String keyword : keywords) {
            String value = safe(keyword).toLowerCase(Locale.ROOT);
            if (value.isBlank()) continue;
            if (evidence.intentSource().contains(value)) score += 3;
            if (evidence.planningSource().contains(value)) score += 1;
        }
        return score;
    }

    private int scoreHints(Set<String> hints, String... values) {
        int score = 0;
        for (String value : values) {
            if (hints.contains(value)) {
                score += 2;
            }
        }
        return score;
    }

    private boolean containsAny(String source, String... values) {
        if (source == null || source.isBlank()) {
            return false;
        }
        for (String value : values) {
            if (value != null && !value.isBlank() && source.contains(value.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String resolveScreenId(String route) {
        String normalized = safe(route).trim();
        if (normalized.isBlank()) return "home";
        normalized = normalized.replaceAll("^/+", "")
                .replaceAll("/:.*", "")
                .replaceAll("[^a-zA-Z0-9]+", "-");
        return normalized.isBlank() ? "home" : normalized;
    }

    private String inferLayoutHint(ProjectManifest.PageSpec page, DomainSignals domain) {
        String route = safe(page.getRoute()).toLowerCase(Locale.ROOT);
        if (containsAny(route, "detail")) return "detail-panel";
        if (containsAny(route, "dashboard", "report")) return "dashboard";
        if (containsAny(route, "reservation", "schedule", "slot")) return "scheduler";
        if (containsAny(route, "feed", "discover", "home")) return domain.layoutHint();
        return domain.layoutHint();
    }

    private String firstText(JsonNode node, String... fields) {
        if (node == null) {
            return "";
        }
        for (String field : fields) {
            JsonNode candidate = node.path(field);
            if (!candidate.isMissingNode() && !candidate.asText("").isBlank()) {
                return candidate.asText("");
            }
        }
        return "";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String slugify(String value) {
        String normalized = safe(value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        return normalized.replaceAll("(^-|-$)", "");
    }

    private record DomainEntity(String name, String purpose, List<String> fields) {
    }

    private record DomainAction(String name, String targetEntity, String intent) {
    }

    private record DomainRelation(String sourceCollection, String sourceField, String targetCollection,
                                  String targetField, String kind) {
    }

    private record StructuralProfile(
            String primaryMode,
            String objectMultiplicity,
            String workflowMode,
            String timeModel,
            String stateModel,
            String collaborationMode,
            String detailMode,
            List<String> interactionModes,
            List<String> evidenceSignals
    ) {
    }

    private record RequirementEvidence(
            String intentSource,
            String planningSource,
            String combinedSource,
            Set<String> objectHints,
            Set<String> actionHints,
            Set<String> roleHints,
            Set<String> stateHints
    ) {
    }

    private record DomainSignals(
            String domainSummary,
            String primaryObject,
            String primaryPurpose,
            List<String> primaryFields,
            List<DomainEntity> secondaryEntities,
            List<DomainAction> actions,
            List<String> states,
            List<String> roles,
            List<String> constraints,
            String primaryLoop,
            List<String> loopSteps,
            String interactionModel,
            String layoutHint,
            String toneReasoning,
            String defaultPrimaryColor,
            String defaultAccentColor,
            List<String> interactionModes,
            List<String> defaultModules,
            String fallbackScreenTitle,
            List<String> defaultScreenTitles,
            List<DomainRelation> relations
    ) {
        static DomainSignals serviceBooking() {
            return new DomainSignals(
                    "Service-booking workspace with provider selection, schedule locking, and booking confirmation.",
                    "booking",
                    "Coordinates service discovery, provider selection, schedule confirmation, and order follow-through.",
                    List.of("id", "serviceId", "providerId", "slotId", "status", "scheduledAt", "orderState"),
                    List.of(
                            new DomainEntity("provider", "Represents service providers and their profile signals.", List.of("id", "name", "specialty", "rating")),
                            new DomainEntity("slot", "Represents available service slots.", List.of("id", "day", "time", "availability")),
                            new DomainEntity("service", "Represents purchasable services or packages.", List.of("id", "name", "price", "duration"))
                    ),
                    List.of(
                            new DomainAction("select-provider", "booking", "Choose the provider that fits the request."),
                            new DomainAction("lock-slot", "booking", "Select and reserve a service slot."),
                            new DomainAction("confirm-booking", "booking", "Submit the booking and start follow-up.")
                    ),
                    List.of("draft", "confirmed", "scheduled", "completed"),
                    List.of("customer", "provider", "operator"),
                    List.of("slot conflicts", "provider availability", "booking status must stay visible"),
                    "browse service -> pick provider/slot -> confirm booking -> complete service",
                    List.of("browse services", "pick provider", "confirm booking", "complete service"),
                    "service-booking",
                    "scheduler",
                    "Trustable, guided, and service-led with clear confirmation paths.",
                    "teal-600",
                    "emerald-500",
                    List.of("scheduler", "workflow", "service-catalog", "reservation"),
                    List.of("service cards", "provider directory", "availability rail", "booking summary", "status timeline"),
                    "Service booking home",
                    List.of("服务首页", "服务/人员选择", "预约确认", "订单进度"),
                    List.of(
                            new DomainRelation("booking", "providerId", "provider", "id", "many-to-one"),
                            new DomainRelation("booking", "slotId", "slot", "id", "many-to-one"),
                            new DomainRelation("booking", "serviceId", "service", "id", "many-to-one")
                    )
            );
        }

        static DomainSignals operationsWorkflow() {
            return new DomainSignals(
                    "Execution-first operations workspace for fulfillment, warehouse, manufacturing, and field process handling.",
                    "job",
                    "Tracks operational jobs, execution checkpoints, blockers, and assignment ownership.",
                    List.of("id", "title", "ownerId", "status", "priority", "site", "nextCheckpoint"),
                    List.of(
                            new DomainEntity("owner", "Represents the operator or supervisor responsible for the job.", List.of("id", "name", "team", "capacity")),
                            new DomainEntity("inventory", "Represents inventory, material, or stock signals tied to execution.", List.of("id", "sku", "quantity", "location")),
                            new DomainEntity("checkpoint", "Represents a stage, node, or execution checkpoint in the flow.", List.of("id", "jobId", "label", "dueAt"))
                    ),
                    List.of(
                            new DomainAction("assign-job", "job", "Allocate the operational task to the right owner."),
                            new DomainAction("advance-checkpoint", "job", "Move the job to the next execution node."),
                            new DomainAction("resolve-blocker", "job", "Record the fix and close the blocking issue.")
                    ),
                    List.of("queued", "in-progress", "blocked", "completed"),
                    List.of("operator", "planner", "supervisor"),
                    List.of("assignment visibility", "stage progression", "blocker context"),
                    "review queue -> assign -> execute -> verify -> close",
                    List.of("review queue", "assign owner", "execute job", "verify close"),
                    "operations-workflow",
                    "pipeline-board",
                    "Operational, dense, and execution-aware with visible blockers and ownership.",
                    "orange-600",
                    "amber-500",
                    List.of("dashboard", "queue", "workflow", "assignment"),
                    List.of("job queue", "checkpoint rail", "inventory strip", "owner panel", "blocker feed"),
                    "Operations control tower",
                    List.of("作业总览", "执行详情", "任务队列", "库存/节点状态"),
                    List.of(
                            new DomainRelation("job", "ownerId", "owner", "id", "many-to-one"),
                            new DomainRelation("checkpoint", "jobId", "job", "id", "many-to-one")
                    )
            );
        }

        static DomainSignals recruiting() {
            return new DomainSignals(
                    "Hiring pipeline workspace with candidate review, interview scheduling, and stage progression.",
                    "candidate",
                    "Tracks candidates from sourcing through interview, offer, and hiring outcomes.",
                    List.of("id", "name", "roleId", "stage", "ownerId", "score", "nextInterviewAt"),
                    List.of(
                            new DomainEntity("role", "Represents the open role or job posting.", List.of("id", "title", "team", "level")),
                            new DomainEntity("interview", "Represents interview rounds and scheduling details.", List.of("id", "candidateId", "round", "scheduledAt")),
                            new DomainEntity("review", "Represents interviewer feedback and scorecards.", List.of("id", "candidateId", "summary", "rating"))
                    ),
                    List.of(
                            new DomainAction("schedule-interview", "candidate", "Book interview rounds and notify participants."),
                            new DomainAction("advance-stage", "candidate", "Move the candidate through hiring stages."),
                            new DomainAction("send-offer", "candidate", "Prepare and issue an offer.")
                    ),
                    List.of("sourced", "screening", "interview", "offer", "hired"),
                    List.of("recruiter", "interviewer", "hiring-manager"),
                    List.of("stage visibility", "interview timing", "feedback traceability"),
                    "review candidate -> schedule interview -> collect feedback -> advance stage",
                    List.of("review candidates", "schedule interview", "collect feedback", "advance stage"),
                    "recruiting-pipeline",
                    "pipeline-board",
                    "Structured and fast-moving with clear pipeline ownership and interview cadence.",
                    "blue-600",
                    "violet-500",
                    List.of("pipeline", "workflow", "assignment", "review"),
                    List.of("candidate list", "pipeline board", "interview rail", "scorecard panel", "offer state"),
                    "Hiring workspace",
                    List.of("候选人列表", "流程看板", "面试安排", "Offer 进度"),
                    List.of(
                            new DomainRelation("candidate", "roleId", "role", "id", "many-to-one"),
                            new DomainRelation("interview", "candidateId", "candidate", "id", "many-to-one"),
                            new DomainRelation("review", "candidateId", "candidate", "id", "many-to-one")
                    )
            );
        }

        static DomainSignals peopleOps() {
            return new DomainSignals(
                    "People operations workspace with employee records, attendance, payroll, and approval flows.",
                    "employee",
                    "Tracks employee profiles, attendance state, payroll batches, and HR approvals.",
                    List.of("id", "name", "department", "status", "attendanceState", "payrollBatchId", "managerId"),
                    List.of(
                            new DomainEntity("attendance", "Represents attendance, leave, and exception records.", List.of("id", "employeeId", "type", "status")),
                            new DomainEntity("payroll", "Represents payroll batches and payout status.", List.of("id", "period", "status", "amount")),
                            new DomainEntity("approval", "Represents HR approval items and their current stage.", List.of("id", "employeeId", "type", "stage"))
                    ),
                    List.of(
                            new DomainAction("review-attendance", "employee", "Inspect attendance anomalies and resolve exceptions."),
                            new DomainAction("compute-payroll", "payroll", "Run payroll batch calculations and previews."),
                            new DomainAction("approve-change", "approval", "Approve HR-related requests or updates.")
                    ),
                    List.of("active", "pending-approval", "payroll-ready", "paid"),
                    List.of("hr", "manager", "employee"),
                    List.of("approval traceability", "employee status visibility", "payroll cycle awareness"),
                    "review employee -> inspect attendance -> compute payroll -> approve changes",
                    List.of("review employee", "inspect attendance", "compute payroll", "approve changes"),
                    "people-operations",
                    "sidebar-workspace",
                    "Orderly and trustworthy with strong emphasis on records, approvals, and periodic processing.",
                    "blue-600",
                    "emerald-500",
                    List.of("workspace", "progress", "review", "schedule"),
                    List.of("employee directory", "attendance summary", "payroll batch list", "approval queue", "department filters"),
                    "People operations hub",
                    List.of("员工总览", "考勤与排班", "薪酬批次", "审批中心"),
                    List.of(
                            new DomainRelation("attendance", "employeeId", "employee", "id", "many-to-one"),
                            new DomainRelation("approval", "employeeId", "employee", "id", "many-to-one")
                    )
            );
        }

        static DomainSignals sports() {
            return new DomainSignals(
                    "Sports club operations workspace with event scheduling, coach planning, and member participation.",
                    "event",
                    "Tracks club events, member signups, coach schedules, and performance records.",
                    List.of("id", "title", "coachId", "venue", "scheduleAt", "signupState", "resultState"),
                    List.of(
                            new DomainEntity("member", "Represents club members and signup status.", List.of("id", "name", "tier", "signupStatus")),
                            new DomainEntity("coach", "Represents coaches and their schedule capacity.", List.of("id", "name", "specialty", "availability")),
                            new DomainEntity("result", "Represents match or performance outcomes.", List.of("id", "eventId", "score", "rank"))
                    ),
                    List.of(
                            new DomainAction("schedule-event", "event", "Arrange events, matches, or sessions."),
                            new DomainAction("manage-signup", "member", "Handle member signup and roster state."),
                            new DomainAction("record-result", "result", "Record performance or competition results.")
                    ),
                    List.of("planned", "open-signup", "ongoing", "completed"),
                    List.of("member", "coach", "operator"),
                    List.of("schedule clarity", "member roster visibility", "result traceability"),
                    "plan event -> open signup -> assign coach -> record result",
                    List.of("plan event", "open signup", "assign coach", "record result"),
                    "sports-club-operations",
                    "learning-campus",
                    "Energetic but structured with clear schedules, rosters, and result views.",
                    "blue-600",
                    "emerald-500",
                    List.of("workspace", "schedule", "progress", "listing"),
                    List.of("event cards", "signup roster", "coach rail", "scoreboard", "schedule board"),
                    "Sports club hub",
                    List.of("赛事安排", "会员报名", "教练排班", "成绩记录"),
                    List.of(
                            new DomainRelation("result", "eventId", "event", "id", "many-to-one")
                    )
            );
        }

        static DomainSignals educationOps() {
            return new DomainSignals(
                    "Education administration workspace with class schedules, grade entry, notice publishing, and guardian communication.",
                    "class",
                    "Tracks classes, schedules, grading, and notices across teachers, students, and guardians.",
                    List.of("id", "name", "teacherId", "term", "status", "scheduleState", "gradeState"),
                    List.of(
                            new DomainEntity("student", "Represents enrolled students and their status.", List.of("id", "name", "classId", "guardianId")),
                            new DomainEntity("gradebook", "Represents grade entry and publication records.", List.of("id", "classId", "status", "updatedAt")),
                            new DomainEntity("notice", "Represents campus notices and communication items.", List.of("id", "audience", "type", "publishedAt"))
                    ),
                    List.of(
                            new DomainAction("publish-schedule", "class", "Create and adjust class schedules."),
                            new DomainAction("record-grade", "gradebook", "Enter and publish grades."),
                            new DomainAction("send-notice", "notice", "Send notices to teachers, students, or guardians.")
                    ),
                    List.of("planned", "ongoing", "grading", "completed"),
                    List.of("teacher", "student", "guardian", "admin"),
                    List.of("schedule clarity", "grade publishing control", "communication reach"),
                    "manage class -> publish schedule -> record grades -> send notice",
                    List.of("manage class", "publish schedule", "record grades", "send notice"),
                    "education-administration",
                    "learning-campus",
                    "Clear and institutional with emphasis on timetables, records, and communication reliability.",
                    "blue-600",
                    "amber-500",
                    List.of("workspace", "progress", "review", "schedule"),
                    List.of("class cards", "timetable", "grade panel", "notice stream", "guardian contact rail"),
                    "Education administration home",
                    List.of("班级总览", "课程表", "成绩录入", "家校通知"),
                    List.of(
                            new DomainRelation("student", "classId", "class", "id", "many-to-one"),
                            new DomainRelation("gradebook", "classId", "class", "id", "many-to-one")
                    )
            );
        }

        static DomainSignals reviewWorkspace() {
            return new DomainSignals(
                    "Review-heavy backoffice for applications, claims, and approval workflows with strong material and decision visibility.",
                    "application",
                    "Tracks submissions, attached materials, reviewer decisions, and approval status changes.",
                    List.of("id", "title", "applicantId", "stage", "riskLevel", "submittedAt", "decisionState"),
                    List.of(
                            new DomainEntity("document", "Represents submitted materials and supporting files.", List.of("id", "applicationId", "type", "status")),
                            new DomainEntity("applicant", "Represents the person or account behind the submission.", List.of("id", "name", "segment", "contact")),
                            new DomainEntity("decision", "Represents reviewer notes and final decisions.", List.of("id", "applicationId", "stage", "result"))
                    ),
                    List.of(
                            new DomainAction("review-submission", "application", "Inspect the submission and attached materials."),
                            new DomainAction("request-material", "application", "Ask for additional materials or corrections."),
                            new DomainAction("advance-decision", "application", "Advance the review to the next decision stage.")
                    ),
                    List.of("submitted", "reviewing", "pending-material", "approved", "rejected"),
                    List.of("reviewer", "applicant", "manager"),
                    List.of("material completeness", "decision trace", "risk visibility"),
                    "review submission -> inspect materials -> request补充 -> advance decision",
                    List.of("review submission", "inspect materials", "request supplement", "advance decision"),
                    "review-workspace",
                    "document-workspace",
                    "Careful and audit-friendly with strong material hierarchy and explicit decision steps.",
                    "slate-700",
                    "amber-600",
                    List.of("workspace", "review", "workflow", "progress"),
                    List.of("application list", "material panel", "decision rail", "risk badges", "timeline"),
                    "Review operations hub",
                    List.of("申请列表", "审核详情", "材料工作区", "决策进度"),
                    List.of(
                            new DomainRelation("document", "applicationId", "application", "id", "many-to-one"),
                            new DomainRelation("decision", "applicationId", "application", "id", "many-to-one")
                    )
            );
        }

        static DomainSignals procurement() {
            return new DomainSignals(
                    "Procurement sourcing workspace with supplier directories, RFQs, quote comparison, and contract traceability.",
                    "quote",
                    "Tracks sourcing requests, supplier responses, quote evaluation, and contract approval progress.",
                    List.of("id", "supplierId", "rfqId", "status", "price", "leadTime", "approvalState"),
                    List.of(
                            new DomainEntity("supplier", "Represents suppliers and cooperation status.", List.of("id", "name", "tier", "region")),
                            new DomainEntity("rfq", "Represents request-for-quotation documents and response windows.", List.of("id", "title", "deadline", "status")),
                            new DomainEntity("contract", "Represents contract records and approval state.", List.of("id", "quoteId", "version", "status"))
                    ),
                    List.of(
                            new DomainAction("issue-rfq", "rfq", "Create and send sourcing requests."),
                            new DomainAction("compare-quote", "quote", "Review quote responses and select suppliers."),
                            new DomainAction("archive-contract", "contract", "Complete approval and archive contract versions.")
                    ),
                    List.of("draft", "collecting", "evaluating", "approved"),
                    List.of("buyer", "reviewer", "supplier"),
                    List.of("supplier clarity", "quote comparison trace", "contract approval visibility"),
                    "issue rfq -> collect quote -> compare options -> archive contract",
                    List.of("issue rfq", "collect quote", "compare options", "archive contract"),
                    "procurement-sourcing",
                    "document-workspace",
                    "Structured and decision-oriented with strong comparison and approval clarity.",
                    "slate-700",
                    "amber-500",
                    List.of("workspace", "review", "listing", "workflow"),
                    List.of("supplier list", "rfq board", "quote compare table", "contract rail", "approval state"),
                    "Procurement sourcing hub",
                    List.of("供应商列表", "询价单", "报价对比", "合同归档"),
                    List.of(
                            new DomainRelation("quote", "supplierId", "supplier", "id", "many-to-one"),
                            new DomainRelation("contract", "quoteId", "quote", "id", "many-to-one")
                    )
            );
        }

        static DomainSignals editorial() {
            return new DomainSignals(
                    "Editorial publishing desk with topic planning, article editing, review, and publish tracking.",
                    "article",
                    "Tracks stories from topic planning through drafting, review, and publication.",
                    List.of("id", "title", "topicId", "status", "editorId", "publishAt", "section"),
                    List.of(
                            new DomainEntity("topic", "Represents story ideas and editorial themes.", List.of("id", "title", "priority", "status")),
                            new DomainEntity("editor", "Represents editors or reporters responsible for content.", List.of("id", "name", "desk", "role")),
                            new DomainEntity("review", "Represents content review comments and approval status.", List.of("id", "articleId", "summary", "status"))
                    ),
                    List.of(
                            new DomainAction("plan-topic", "topic", "Collect and prioritize editorial ideas."),
                            new DomainAction("edit-article", "article", "Draft and refine content."),
                            new DomainAction("publish-article", "article", "Approve and publish the story.")
                    ),
                    List.of("pitched", "drafting", "review", "published"),
                    List.of("editor", "reporter", "reviewer"),
                    List.of("topic visibility", "review trace", "publication cadence"),
                    "plan topic -> draft article -> review -> publish",
                    List.of("plan topic", "draft article", "review story", "publish article"),
                    "editorial-publishing",
                    "editorial",
                    "Editorial and reading-led with clear story hierarchy and review cadence.",
                    "slate-700",
                    "amber-500",
                    List.of("workspace", "review", "composer", "progress"),
                    List.of("topic pool", "article editor", "review queue", "publish calendar", "section stats"),
                    "Editorial desk",
                    List.of("选题池", "稿件编辑", "审核发布", "专题页"),
                    List.of(
                            new DomainRelation("article", "topicId", "topic", "id", "many-to-one"),
                            new DomainRelation("review", "articleId", "article", "id", "many-to-one")
                    )
            );
        }

        static DomainSignals forum() {
            return new DomainSignals(
                    "Thread-based community with boards, replies, moderation, and member identity.",
                    "thread",
                    "Tracks board conversations, threaded replies, and member participation.",
                    List.of("id", "title", "boardId", "authorId", "replyCount", "lastActiveAt", "status"),
                    List.of(
                            new DomainEntity("board", "Represents community boards or topic sections.", List.of("id", "name", "threadCount", "moderator")),
                            new DomainEntity("reply", "Represents replies in a thread.", List.of("id", "threadId", "authorId", "content")),
                            new DomainEntity("member", "Represents forum members and participation signals.", List.of("id", "name", "reputation", "joinedAt"))
                    ),
                    List.of(
                            new DomainAction("open-thread", "thread", "Open and continue a discussion thread."),
                            new DomainAction("reply-thread", "reply", "Post a reply into the conversation."),
                            new DomainAction("create-thread", "thread", "Create a new thread in a board.")
                    ),
                    List.of("new", "active", "pinned", "archived"),
                    List.of("reader", "member", "moderator"),
                    List.of("board navigation", "reply continuity", "member context"),
                    "browse board -> open thread -> reply -> create thread",
                    List.of("browse board", "open thread", "reply thread", "create thread"),
                    "forum-community",
                    "thread",
                    "Structured community with discussion-first rhythm and visible board hierarchy.",
                    "indigo-600",
                    "sky-500",
                    List.of("listing", "detail-consumption", "creator-profile", "composer"),
                    List.of("board list", "thread list", "thread detail", "reply composer", "member sidebar"),
                    "Forum home",
                    List.of("版块列表", "帖子详情", "用户主页", "发帖入口"),
                    List.of(
                            new DomainRelation("thread", "boardId", "board", "id", "many-to-one"),
                            new DomainRelation("reply", "threadId", "thread", "id", "many-to-one")
                    )
            );
        }

        static DomainSignals billing() {
            return new DomainSignals(
                    "Subscription billing workspace with plan configuration, invoices, renewals, and account status.",
                    "subscription",
                    "Tracks plans, invoices, payment status, renewals, and account lifecycle changes.",
                    List.of("id", "customerId", "planId", "status", "renewAt", "invoiceState", "amount"),
                    List.of(
                            new DomainEntity("invoice", "Represents invoices and payment status.", List.of("id", "subscriptionId", "period", "status")),
                            new DomainEntity("plan", "Represents subscription plans and feature limits.", List.of("id", "name", "price", "interval")),
                            new DomainEntity("customer", "Represents the subscribed customer account.", List.of("id", "name", "segment", "owner"))
                    ),
                    List.of(
                            new DomainAction("update-plan", "subscription", "Change the active billing plan."),
                            new DomainAction("issue-invoice", "invoice", "Generate or review invoice records."),
                            new DomainAction("renew-subscription", "subscription", "Process renewals and lifecycle changes.")
                    ),
                    List.of("trial", "active", "overdue", "renewed"),
                    List.of("operator", "finance", "customer"),
                    List.of("billing state visibility", "renewal trace", "plan clarity"),
                    "review account -> inspect invoice -> renew subscription -> confirm status",
                    List.of("review account", "inspect invoice", "renew subscription", "confirm status"),
                    "subscription-billing",
                    "sidebar-workspace",
                    "Calm and financial with clear status tags and periodic billing context.",
                    "blue-600",
                    "amber-500",
                    List.of("workspace", "progress", "review", "listing"),
                    List.of("plan list", "subscription table", "invoice history", "renewal panel", "customer summary"),
                    "Billing operations hub",
                    List.of("套餐管理", "订阅列表", "账单记录", "续费进度"),
                    List.of(
                            new DomainRelation("invoice", "subscriptionId", "subscription", "id", "many-to-one"),
                            new DomainRelation("subscription", "customerId", "customer", "id", "many-to-one")
                    )
            );
        }

        static DomainSignals crm() {
            return new DomainSignals(
                    "Pipeline-oriented revenue workspace with ownership, risk tracking, and stage progression.",
                    "lead",
                    "Tracks sales leads and commercial opportunities through assignment and follow-up.",
                    List.of("id", "title", "ownerId", "stage", "riskLevel", "amount", "nextAction"),
                    List.of(
                            new DomainEntity("owner", "Represents the person responsible for progressing a lead.", List.of("id", "name", "team", "capacity")),
                            new DomainEntity("followup", "Captures each follow-up note and stage movement.", List.of("id", "leadId", "summary", "scheduledAt"))
                    ),
                    List.of(
                            new DomainAction("assign-owner", "lead", "Allocate responsibility and rebalance workload."),
                            new DomainAction("advance-stage", "lead", "Move the lead through the sales pipeline."),
                            new DomainAction("mark-risk", "lead", "Flag renewal or churn risk for attention.")
                    ),
                    List.of("new", "in-progress", "renewal-risk", "won"),
                    List.of("sales", "customer-success", "manager"),
                    List.of("ownership required", "stage progression visible", "risk needs explicit signal"),
                    "qualify -> assign -> follow up -> advance stage",
                    List.of("qualify lead", "assign owner", "record follow-up", "advance stage"),
                    "workflow-pipeline",
                    "pipeline-board",
                    "Professional, operational, and stage-aware with strong status contrast.",
                    "blue-600",
                    "amber-500",
                    List.of("pipeline", "queue", "assignment", "workflow"),
                    List.of("stats strip", "filters", "lead list", "batch actions", "detail panel"),
                    "Pipeline workspace",
                    List.of("线索池", "商机详情", "负责人视图", "跟进记录"),
                    List.of(
                            new DomainRelation("lead", "ownerId", "owner", "id", "many-to-one"),
                            new DomainRelation("followup", "leadId", "lead", "id", "many-to-one")
                    )
            );
        }

        static DomainSignals clinic() {
            return new DomainSignals(
                    "Appointment-first clinical workflow with schedule selection, reminders, and visit confirmation.",
                    "appointment",
                    "Coordinates project inquiry, doctor selection, booking, arrival reminder, and visit completion.",
                    List.of("id", "serviceId", "doctorId", "slotId", "status", "visitState", "paymentState"),
                    List.of(
                            new DomainEntity("doctor", "Represents doctors and their availability.", List.of("id", "name", "specialty", "rating")),
                            new DomainEntity("slot", "Represents bookable time slots for appointments.", List.of("id", "day", "time", "availability")),
                            new DomainEntity("service", "Represents clinic services or treatment projects.", List.of("id", "name", "duration", "price"))
                    ),
                    List.of(
                            new DomainAction("select-slot", "appointment", "Choose a feasible doctor and time slot."),
                            new DomainAction("confirm-booking", "appointment", "Lock the appointment and create arrival reminders."),
                            new DomainAction("submit-visit", "appointment", "Advance to visit and payment confirmation.")
                    ),
                    List.of("draft", "confirmed", "reminded", "visited", "paid"),
                    List.of("patient", "doctor", "clinic-operator"),
                    List.of("time slot conflicts", "doctor availability", "visit confirmation chain"),
                    "browse service -> choose doctor/slot -> confirm booking -> visit -> pay",
                    List.of("browse services", "choose slot", "confirm booking", "complete visit"),
                    "scheduler-case-flow",
                    "scheduler",
                    "Clean, trustable, and service-oriented with strong guidance through the booking flow.",
                    "teal-600",
                    "emerald-500",
                    List.of("scheduler", "case-management", "service-catalog", "visit-flow"),
                    List.of("service cards", "booking rail", "availability state", "visit steps", "status panel"),
                    "Appointment home",
                    List.of("预约首页", "医生/时段选择", "预约确认", "到诊状态"),
                    List.of(
                            new DomainRelation("appointment", "doctorId", "doctor", "id", "many-to-one"),
                            new DomainRelation("appointment", "slotId", "slot", "id", "many-to-one"),
                            new DomainRelation("appointment", "serviceId", "service", "id", "many-to-one")
                    )
            );
        }

        static DomainSignals ops() {
            return new DomainSignals(
                    "Command-center operations surface with alerts, tickets, and dispatch decisions.",
                    "ticket",
                    "Tracks alerts, operational tickets, dispatch state, and service-level handling.",
                    List.of("id", "site", "severity", "status", "ownerId", "sla"),
                    List.of(
                            new DomainEntity("alert", "Represents operational alerts that feed tickets.", List.of("id", "source", "severity", "raisedAt")),
                            new DomainEntity("site", "Represents monitored sites or devices.", List.of("id", "name", "region", "health"))
                    ),
                    List.of(
                            new DomainAction("handle-alert", "ticket", "Acknowledge and start processing an alert-driven ticket."),
                            new DomainAction("assign-owner", "ticket", "Dispatch operational ownership."),
                            new DomainAction("resolve-ticket", "ticket", "Close the issue after remediation.")
                    ),
                    List.of("open", "assigned", "in-progress", "resolved"),
                    List.of("operator", "dispatcher", "manager"),
                    List.of("SLA awareness", "severity ordering", "dispatch response"),
                    "monitor -> triage -> dispatch -> resolve",
                    List.of("monitor alerts", "triage ticket", "assign owner", "resolve ticket"),
                    "dashboard-dispatch",
                    "ops-command",
                    "High-signal and operational with emphasis on urgency and readable status coding.",
                    "orange-600",
                    "red-500",
                    List.of("dashboard", "queue", "dispatch", "alert-workflow"),
                    List.of("hero metrics", "alert queue", "ticket list", "map or site rail", "status actions"),
                    "Operations command center",
                    List.of("总览看板", "告警队列", "工单详情", "站点状态"),
                    List.of(
                            new DomainRelation("ticket", "sourceAlertId", "alert", "id", "many-to-one"),
                            new DomainRelation("ticket", "siteId", "site", "id", "many-to-one")
                    )
            );
        }

        static DomainSignals inpatient() {
            return new DomainSignals(
                    "Inpatient care workspace with bed allocation, patient detail, order execution, and discharge tracking.",
                    "patient",
                    "Tracks inpatient status, bed occupancy, medical orders, and discharge milestones.",
                    List.of("id", "name", "ward", "bedId", "status", "orderState", "dischargeState"),
                    List.of(
                            new DomainEntity("bed", "Represents beds and occupancy state.", List.of("id", "ward", "room", "occupancy")),
                            new DomainEntity("order", "Represents inpatient medical orders and execution records.", List.of("id", "patientId", "type", "status")),
                            new DomainEntity("charge", "Represents charges and settlement items.", List.of("id", "patientId", "amount", "status"))
                    ),
                    List.of(
                            new DomainAction("assign-bed", "patient", "Allocate and manage bed occupancy."),
                            new DomainAction("execute-order", "order", "Record execution of inpatient orders."),
                            new DomainAction("complete-discharge", "patient", "Advance the discharge workflow.")
                    ),
                    List.of("admitted", "under-care", "ready-discharge", "discharged"),
                    List.of("nurse", "doctor", "operator"),
                    List.of("bed status visibility", "order execution trace", "discharge readiness"),
                    "assign bed -> review patient -> execute order -> discharge",
                    List.of("assign bed", "review patient", "execute order", "discharge patient"),
                    "inpatient-care",
                    "scheduler",
                    "Clinical and steady with high state clarity around care and discharge progress.",
                    "teal-600",
                    "emerald-500",
                    List.of("case-management", "workflow", "schedule", "review"),
                    List.of("bed board", "patient detail", "order list", "charge summary", "discharge rail"),
                    "Inpatient operations",
                    List.of("床位看板", "患者详情", "医嘱执行", "出院流程"),
                    List.of(
                            new DomainRelation("order", "patientId", "patient", "id", "many-to-one"),
                            new DomainRelation("charge", "patientId", "patient", "id", "many-to-one")
                    )
            );
        }

        static DomainSignals lab() {
            return new DomainSignals(
                    "Laboratory workspace for sample tracking, experiment logging, equipment booking, and report archiving.",
                    "sample",
                    "Tracks samples, experiment progress, instrument scheduling, and result documentation.",
                    List.of("id", "name", "status", "experimentId", "instrumentId", "ownerId", "reportState"),
                    List.of(
                            new DomainEntity("experiment", "Represents experiments and their current steps.", List.of("id", "sampleId", "status", "updatedAt")),
                            new DomainEntity("instrument", "Represents lab instruments and booking status.", List.of("id", "name", "availability", "location")),
                            new DomainEntity("report", "Represents result summaries and archived documents.", List.of("id", "sampleId", "version", "status"))
                    ),
                    List.of(
                            new DomainAction("log-experiment", "experiment", "Record experiment progress and outcomes."),
                            new DomainAction("book-instrument", "instrument", "Reserve instrument time slots."),
                            new DomainAction("archive-report", "report", "Save and version result reports.")
                    ),
                    List.of("received", "testing", "analyzing", "reported"),
                    List.of("researcher", "lab-operator", "reviewer"),
                    List.of("sample status visibility", "instrument availability", "report version trace"),
                    "register sample -> log experiment -> book instrument -> archive report",
                    List.of("register sample", "log experiment", "book instrument", "archive report"),
                    "laboratory-workspace",
                    "document-workspace",
                    "Methodical and analytical with emphasis on traceability and experiment cadence.",
                    "slate-700",
                    "sky-500",
                    List.of("workspace", "review", "schedule", "progress"),
                    List.of("sample table", "experiment log", "instrument calendar", "report rail", "status badges"),
                    "Laboratory workspace",
                    List.of("样本列表", "实验记录", "仪器预约", "报告归档"),
                    List.of(
                            new DomainRelation("experiment", "sampleId", "sample", "id", "many-to-one"),
                            new DomainRelation("report", "sampleId", "sample", "id", "many-to-one")
                    )
            );
        }

        static DomainSignals learning() {
            return new DomainSignals(
                    "Learning workflow with curriculum, progress, and milestone-driven completion.",
                    "course",
                    "Organizes course content, tasks, schedules, and learner progress checkpoints.",
                    List.of("id", "title", "coach", "progress", "nextTask", "liveSession"),
                    List.of(
                            new DomainEntity("task", "Represents learning tasks or homework milestones.", List.of("id", "courseId", "title", "status")),
                            new DomainEntity("student", "Represents learners and their progress state.", List.of("id", "name", "progress", "riskFlag"))
                    ),
                    List.of(
                            new DomainAction("start-task", "course", "Begin a learning task or milestone."),
                            new DomainAction("view-progress", "student", "Inspect learner progress and blockers."),
                            new DomainAction("schedule-session", "course", "Arrange or confirm a live learning session.")
                    ),
                    List.of("planned", "in-progress", "submitted", "completed"),
                    List.of("coach", "student", "operator"),
                    List.of("progress visibility", "task sequencing", "live schedule coordination"),
                    "review course -> start task -> submit work -> view progress",
                    List.of("review course", "start task", "submit work", "view progress"),
                    "learning-progress",
                    "learning-campus",
                    "Focused and instructional with clear progress markers and milestone calls to action.",
                    "indigo-600",
                    "violet-500",
                    List.of("workspace", "progress", "schedule", "review"),
                    List.of("course cards", "progress strip", "task list", "session rail", "student status"),
                    "Learning workspace",
                    List.of("课程首页", "任务看板", "进度详情", "直播安排"),
                    List.of(
                            new DomainRelation("task", "courseId", "course", "id", "many-to-one"),
                            new DomainRelation("student", "courseId", "course", "id", "many-to-one")
                    )
            );
        }

        static DomainSignals content() {
            return new DomainSignals(
                    "Content-first discovery surface with creator identity, engagement loops, and publishing.",
                    "post",
                    "Publishes and distributes visual-first posts with creator identity and interaction metrics.",
                    List.of("id", "title", "authorId", "likes", "comments", "collects", "topic"),
                    List.of(
                            new DomainEntity("creator", "Represents the content creator profile and activity.", List.of("id", "name", "bio", "followers")),
                            new DomainEntity("comment", "Represents discussion replies on content.", List.of("id", "postId", "author", "content"))
                    ),
                    List.of(
                            new DomainAction("open-detail", "post", "Open the post detail and continue the interaction loop."),
                            new DomainAction("engage", "post", "Like, save, comment, or follow from a post."),
                            new DomainAction("publish", "post", "Create and submit a new piece of content.")
                    ),
                    List.of("draft", "published", "saved", "liked"),
                    List.of("viewer", "creator"),
                    List.of("visual density", "auth entry", "engagement continuity"),
                    "browse feed -> open detail -> engage -> publish",
                    List.of("browse feed", "open detail", "engage", "publish"),
                    "content-discovery",
                    "waterfall",
                    "Warm, editorial, and visual-first with soft cards and a strong content loop.",
                    "rose-500",
                    "pink-500",
                    List.of("feed-first", "detail-consumption", "creator-profile", "composer"),
                    List.of("feed grid", "category tabs", "creator chips", "detail overlay", "publish entry"),
                    "Discovery feed",
                    List.of("首页瀑布流", "内容详情页", "创作者主页", "发布入口"),
                    List.of(
                            new DomainRelation("post", "authorId", "creator", "id", "many-to-one"),
                            new DomainRelation("comment", "postId", "post", "id", "many-to-one")
                    )
            );
        }

        static DomainSignals commerce() {
            return new DomainSignals(
                    "Listing-driven commerce workspace with detail views, reservations, and order confirmation.",
                    "listing",
                    "Organizes products, availability, order state, and transaction confidence signals.",
                    List.of("id", "title", "price", "status", "city", "availability"),
                    List.of(
                            new DomainEntity("order", "Represents order or reservation progress.", List.of("id", "listingId", "status", "paymentState")),
                            new DomainEntity("owner", "Represents seller or broker identity.", List.of("id", "name", "rating", "region"))
                    ),
                    List.of(
                            new DomainAction("open-detail", "listing", "Review an item or asset in detail."),
                            new DomainAction("reserve", "order", "Reserve or initiate a transaction."),
                            new DomainAction("confirm-payment", "order", "Complete the transaction confirmation.")
                    ),
                    List.of("available", "reserved", "pending-payment", "completed"),
                    List.of("buyer", "seller", "operator"),
                    List.of("availability visibility", "reservation confidence", "payment confirmation"),
                    "browse listings -> open detail -> reserve -> pay",
                    List.of("browse listings", "open detail", "reserve", "pay"),
                    "commerce-order",
                    "marketplace-hub",
                    "Commercial and trust-building with price, status, and conversion clarity.",
                    "sky-600",
                    "amber-500",
                    List.of("listing", "detail", "order-workflow", "reservation"),
                    List.of("listing cards", "filters", "detail rail", "order state", "seller context"),
                    "Marketplace home",
                    List.of("商品/资产列表", "详情页", "订单状态", "交易确认"),
                    List.of(
                            new DomainRelation("order", "listingId", "listing", "id", "many-to-one"),
                            new DomainRelation("listing", "ownerId", "owner", "id", "many-to-one")
                    )
            );
        }

        static DomainSignals generic(ProjectManifest manifest) {
            String primaryObject = "record";
            String title = manifest.getOverview() == null || manifest.getOverview().isBlank() ? "Workspace home" : manifest.getOverview();
            return new DomainSignals(
                    "General workspace compiled from the requirement when no stronger domain pattern is detected.",
                    primaryObject,
                    "Tracks the primary business item and its status updates.",
                    List.of("id", "title", "status", "owner", "updatedAt"),
                    List.of(
                            new DomainEntity("actor", "Represents the person operating on a record.", List.of("id", "name", "role"))
                    ),
                    List.of(
                            new DomainAction("create", primaryObject, "Create a new business record."),
                            new DomainAction("open-detail", primaryObject, "Inspect or edit the selected record."),
                            new DomainAction("update-status", primaryObject, "Advance the current record state.")
                    ),
                    List.of("new", "active", "done"),
                    List.of("operator", "manager"),
                    List.of("record needs an owner", "status must be visible"),
                    "create -> inspect -> update",
                    List.of("create record", "open detail", "update status"),
                    "workspace",
                    "sidebar-workspace",
                    "Professional default workspace for structured task handling.",
                    "indigo-600",
                    "emerald-500",
                    List.of("workspace", "list", "detail", "status-workflow"),
                    List.of("summary cards", "record list", "detail panel", "status actions"),
                    title,
                    List.of("工作台首页", "记录列表", "详情面板", "状态动作"),
                    List.of()
            );
        }

        static DomainSignals legal() {
            return new DomainSignals(
                    "Case-oriented legal workspace with document evidence, progress tracking, and multi-role collaboration.",
                    "case",
                    "Tracks legal cases, clients, evidence, deadlines, and document review workflows.",
                    List.of("id", "title", "clientId", "stage", "hearingDate", "ownerId", "priority"),
                    List.of(
                            new DomainEntity("client", "Represents the client attached to a legal case.", List.of("id", "name", "industry", "contact")),
                            new DomainEntity("document", "Represents filings, contracts, and evidence documents.", List.of("id", "caseId", "type", "status")),
                            new DomainEntity("evidence", "Represents evidence items and provenance.", List.of("id", "caseId", "category", "source"))
                    ),
                    List.of(
                            new DomainAction("open-case", "case", "Review the active case and its legal context."),
                            new DomainAction("review-document", "document", "Inspect, annotate, and approve legal documents."),
                            new DomainAction("advance-case-stage", "case", "Move the case through legal milestones and deadlines.")
                    ),
                    List.of("intake", "review", "filing", "hearing", "closed"),
                    List.of("lawyer", "paralegal", "client"),
                    List.of("document-heavy", "strict stage progression", "deadlines and hearing dates", "multi-role collaboration"),
                    "intake case -> review evidence -> prepare filing -> advance stage",
                    List.of("intake case", "review evidence", "prepare filing", "advance stage"),
                    "case-management-document-workflow",
                    "document-workspace",
                    "Structured, document-forward, and process-heavy with emphasis on evidence and legal milestones.",
                    "slate-700",
                    "amber-600",
                    List.of("case-management", "document-workspace", "workflow", "review"),
                    List.of("case list", "document panel", "evidence board", "deadline tracker"),
                    "Legal workspace",
                    List.of("案件总览", "案件详情", "文档工作区", "证据与时间线"),
                    List.of(
                            new DomainRelation("case", "clientId", "client", "id", "many-to-one"),
                            new DomainRelation("document", "caseId", "case", "id", "many-to-one"),
                            new DomainRelation("evidence", "caseId", "case", "id", "many-to-one")
                    )
            );
        }
    }
}
