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
        ProjectManifest.DesignContract contract = manifest.getDesignContract();
        List<PrototypeBundle.ScreenPlan> screens = buildScreenPlans(manifest, domain);
        String referenceSignal = extractReferenceSignal(manifest);
        List<PrototypeBundle.ScreenBullet> screenBullets = buildScreenBullets(manifest, domain, screens, referenceSignal);
        return PrototypeBundle.ExperienceBrief.builder()
                .referenceSignal(referenceSignal)
                .intentSummary(buildIntentSummary(manifest, domain))
                .introduction(buildIntroductionNarrative(manifest, domain, profile, referenceSignal))
                .screenPlanTitle(buildScreenPlanTitle(referenceSignal))
                .screenBullets(screenBullets)
                .nextStepNarrative(buildNextStepNarrative(referenceSignal, profile))
                .interactionModel(profile.interactionModes().isEmpty() ? domain.interactionModel() : String.join(" + ", profile.interactionModes()))
                .navigationStyle(resolveNavigationStyle(contract))
                .contentRhythm(contract != null && contract.getLayoutRhythm() != null ? contract.getLayoutRhythm().name() : domain.layoutHint())
                .density(contract != null && contract.getContentDensity() != null ? contract.getContentDensity().name() : "MEDIUM")
                .mediaEmphasis(contract != null && contract.getMediaWeight() != null ? contract.getMediaWeight().name() : "MIXED")
                .inferredTraits(buildInferredTraits(domain, contract, referenceSignal, profile))
                .primaryLoopSteps(resolvePrimaryLoopSteps(manifest, domain))
                .executionPlan(buildExecutionPlan(domain, profile))
                .rationale(buildRationale(domain, referenceSignal))
                .confidenceNote(buildConfidenceNote(referenceSignal))
                .screens(screens)
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

    private List<PrototypeBundle.ScreenPlan> buildScreenPlans(ProjectManifest manifest, DomainSignals domain) {
        List<PrototypeBundle.ScreenPlan> screens = new ArrayList<>();
        List<ProjectManifest.PageSpec> pages = manifest.getPages() == null ? List.of() : manifest.getPages();
        for (ProjectManifest.PageSpec page : pages.stream().limit(6).toList()) {
            screens.add(PrototypeBundle.ScreenPlan.builder()
                    .id(resolveScreenId(page.getRoute()))
                    .title(page.getDescription())
                    .role(page.getNavRole())
                    .layoutHint(inferLayoutHint(page, domain))
                    .keyModules(page.getComponents() == null ? List.of() : page.getComponents())
                    .build());
        }
        if (screens.isEmpty()) {
            screens.add(PrototypeBundle.ScreenPlan.builder()
                    .id("home")
                    .title(domain.fallbackScreenTitle())
                    .role("PRIMARY")
                    .layoutHint(domain.layoutHint())
                    .keyModules(domain.defaultModules())
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
        List<PrototypeBundle.ScreenBullet> modeBullets = buildBulletsFromInteractionModes(domain);
        if (!modeBullets.isEmpty()) {
            return modeBullets;
        }

        List<PrototypeBundle.ScreenBullet> bullets = new ArrayList<>();
        List<ProjectManifest.PageSpec> pages = manifest.getPages() == null ? List.of() : manifest.getPages();
        for (ProjectManifest.PageSpec page : pages.stream().limit(4).toList()) {
            bullets.add(buildBulletFromPage(page, domain));
        }
        if (bullets.isEmpty()) {
            for (PrototypeBundle.ScreenPlan screen : screens.stream().limit(4).toList()) {
                bullets.add(PrototypeBundle.ScreenBullet.builder()
                        .id(screen.getId())
                        .label(screen.getTitle())
                        .description(screen.getLayoutHint())
                        .build());
            }
        }
        return bullets;
    }

    private List<PrototypeBundle.ScreenBullet> buildBulletsFromInteractionModes(DomainSignals domain) {
        List<PrototypeBundle.ScreenBullet> bullets = new ArrayList<>();
        List<String> modes = domain.interactionModes();

        if (modes.contains("feed-first")) {
            bullets.add(bullet("home", "首页（瀑布流）", "展示各种精选帖子。"));
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

        if (containsAny(route, "home", "index") || description.contains("首页")) {
            label = domain.interactionModes().contains("feed-first") ? "首页（瀑布流）" : "首页";
        } else if (containsAny(route, "publish", "post/new", "create") || description.contains("发布")) {
            label = "发布页";
        } else if (containsAny(route, "search") || description.contains("搜索")) {
            label = "搜索页";
        } else if (containsAny(route, "user", "profile", "me") || description.contains("主页")) {
            label = "用户个人主页";
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

    private String buildNextStepNarrative(String referenceSignal, StructuralProfile profile) {
        if (referenceSignal != null && !referenceSignal.isBlank()) {
            return "我将先为您创建一个符合“" + referenceSignal + "”风格的设计系统，然后开始设计这些页面。您看这样安排可以吗？";
        }
        return "我将先基于" + humanizeInteractionModes(profile.interactionModes(), "当前需求") + "整理设计系统，再开始生成这些页面。";
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
        String source = (
                safe(manifest.getUserIntent()) + " "
                        + safe(manifest.getArchetype()) + " "
                        + safe(manifest.getOverview())
        ).toLowerCase(Locale.ROOT);

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
        boolean singleRecord = containsAny(source, "详情", "档期", "预约", "就诊", "案件", "表单", "记录", "detail", "slot", "visit")
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
        String source = (
                safe(manifest.getUserIntent()) + " "
                        + safe(manifest.getArchetype()) + " "
                        + safe(manifest.getOverview())
        ).toLowerCase(Locale.ROOT);

        if (containsAny(source, "crm", "线索", "商机", "pipeline", "续费", "客户成功")) {
            return DomainSignals.crm();
        }
        if (containsAny(source, "医美", "诊所", "医生", "预约", "档期", "就诊")) {
            return DomainSignals.clinic();
        }
        if (containsAny(source, "运维", "监控", "工单", "预警", "dashboard", "告警")) {
            return DomainSignals.ops();
        }
        if (containsAny(source, "课程", "学习", "训练营", "作业", "直播")) {
            return DomainSignals.learning();
        }
        if (containsAny(source, "律师", "案件", "法务", "证据", "合同", "诉讼", "hearing", "legal", "case", "evidence", "contract")) {
            return DomainSignals.legal();
        }
        if (containsAny(source, "社区", "小红书", "内容", "帖子", "收藏", "评论", "feed", "discover")) {
            return DomainSignals.content();
        }
        if (containsAny(source, "房源", "车源", "商品", "订单", "支付", "票务")) {
            return DomainSignals.commerce();
        }
        return DomainSignals.generic(manifest);
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
