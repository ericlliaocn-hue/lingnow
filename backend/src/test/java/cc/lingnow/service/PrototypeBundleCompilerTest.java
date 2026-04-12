package cc.lingnow.service;

import cc.lingnow.model.ProjectManifest;
import cc.lingnow.model.PrototypeBundle;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PrototypeBundleCompilerTest {

    private final PrototypeBundleCompiler compiler = new PrototypeBundleCompiler(new ObjectMapper());

    @Test
    void compilesCrmManifestIntoReusableBundle() {
        ProjectManifest manifest = baseManifest(
                "crm-bundle",
                "做一个 CRM 销售线索和客户成功管道，支持商机跟进、续费风险、负责人分配和阶段推进。"
        );
        manifest.setPages(List.of(
                page("/home", "线索池首页", "PRIMARY", List.of("搜索", "统计卡", "列表", "批量操作", "详情入口")),
                page("/detail", "线索详情", "OVERLAY", List.of("详情面板", "流程按钮", "跟进记录", "负责人", "风险标签"))
        ));
        manifest.setTaskFlows(List.of(ProjectManifest.TaskFlow.builder()
                .id("flow_assign_followup")
                .description("销售从线索池进入详情，分配负责人并推进阶段。")
                .steps(List.of("打开线索详情", "分配负责人", "记录跟进", "推进阶段"))
                .build()));
        manifest.setMockData("""
                [
                  {"id":"L-1","title":"华东续费机会","ownerId":"U-1","stage":"跟进中","riskLevel":"续费风险","amount":8200},
                  {"id":"L-2","title":"商机升级线索","ownerId":"U-2","stage":"待跟进","riskLevel":"低","amount":9100}
                ]
                """);
        manifest.setMetaData(new HashMap<>(Map.of(
                "visual_bgClass", "bg-slate-50",
                "visual_cardClass", "bg-white shadow-sm rounded-2xl p-6",
                "visual_primaryColor", "blue-600",
                "visual_accentColor", "amber-500",
                "visual_reasoning", "Pipeline-first enterprise workspace."
        )));
        manifest.setDesignContract(ProjectManifest.DesignContract.builder()
                .primaryGoal(ProjectManifest.PrimaryGoal.TRANSACT)
                .layoutRhythm(ProjectManifest.LayoutRhythm.DASHBOARD)
                .contentDensity(ProjectManifest.ContentDensity.HIGH)
                .mediaWeight(ProjectManifest.MediaWeight.MIXED)
                .shellPattern("SIDEBAR_PRIMARY_NAV")
                .contentMode("SIDEBAR_FIRST")
                .build());

        PrototypeBundle bundle = compiler.compile(manifest);

        assertNotNull(bundle);
        assertEquals("lead", bundle.getProductIr().getPrimaryObject());
        assertEquals("OPERATE", bundle.getProductIr().getPrimaryMode());
        assertEquals("MULTI", bundle.getProductIr().getObjectMultiplicity());
        assertEquals("LIST_BATCH", bundle.getProductIr().getWorkflowMode());
        assertEquals("STRONG", bundle.getProductIr().getStateModel());
        assertTrue(bundle.getProductIr().getInteractionModes().contains("pipeline"));
        assertTrue(bundle.getProductIr().getInteractionModes().contains("workflow"));
        assertTrue(bundle.getExperienceBrief().getInteractionModel().contains("pipeline"));
        assertFalse(bundle.getExperienceBrief().getExecutionPlan().isEmpty());
        assertFalse(bundle.getExperienceBrief().getWhyThisStructure().isBlank());
        assertFalse(bundle.getExperienceBrief().getRationale().isBlank());
        assertNotNull(bundle.getExperienceBrief().getVisualDirection());
        assertFalse(bundle.getExperienceBrief().getVisualDirection().getControls().isBlank());
        assertTrue(bundle.getExperienceBrief().getScreens().stream().allMatch(screen -> !screen.getLayoutNarrative().isBlank()));
        assertTrue(bundle.getExperienceBrief().getScreens().stream().allMatch(screen -> !screen.getPrimaryActions().isEmpty()));
        assertEquals(2, bundle.getMockGraph().getPrimaryRecordCount());
        assertFalse(bundle.getFlowGraph().getFlows().isEmpty());
        assertEquals("true", manifest.getMetaData().get("bundle_ready"));
    }

    @Test
    void compilesClinicManifestIntoSchedulerBundle() {
        ProjectManifest manifest = baseManifest(
                "clinic-bundle",
                "做一个医美诊所预约系统，支持项目咨询、医生档期、到诊提醒、就诊单和支付确认。"
        );
        manifest.setPages(List.of(
                page("/home", "诊疗预约原型", "PRIMARY", List.of("项目卡片", "预约状态", "到诊闭环", "医生档期", "支付确认")),
                page("/orders", "预约单状态", "PRIMARY", List.of("状态列表", "提醒面板", "操作按钮", "支付状态", "就诊单"))
        ));
        manifest.setTaskFlows(List.of(ProjectManifest.TaskFlow.builder()
                .id("flow_booking_visit")
                .description("用户选择项目和时段，完成预约并到诊。")
                .steps(List.of("浏览项目", "选择时段", "确认预约", "提交就诊单"))
                .build()));
        manifest.setMockData("""
                [
                  {"id":"A-1","service":"光电项目","doctorId":"D-1","slotId":"S-1","status":"已确认","paymentState":"待支付"},
                  {"id":"A-2","service":"皮肤管理","doctorId":"D-2","slotId":"S-2","status":"待确认","paymentState":"已支付"}
                ]
                """);
        manifest.setMetaData(new HashMap<>(Map.of(
                "visual_primaryColor", "teal-600",
                "visual_accentColor", "emerald-500"
        )));
        manifest.setDesignContract(ProjectManifest.DesignContract.builder()
                .primaryGoal(ProjectManifest.PrimaryGoal.TRANSACT)
                .layoutRhythm(ProjectManifest.LayoutRhythm.LIST)
                .contentDensity(ProjectManifest.ContentDensity.MEDIUM)
                .mediaWeight(ProjectManifest.MediaWeight.MIXED)
                .shellPattern("SIDEBAR_PRIMARY_NAV")
                .contentMode("SIDEBAR_FIRST")
                .build());

        PrototypeBundle bundle = compiler.compile(manifest);

        assertNotNull(bundle);
        assertEquals("appointment", bundle.getProductIr().getPrimaryObject());
        assertEquals("OPERATE", bundle.getProductIr().getPrimaryMode());
        assertEquals("MULTI", bundle.getProductIr().getObjectMultiplicity());
        assertEquals("SCHEDULE", bundle.getProductIr().getTimeModel());
        assertEquals("STRONG", bundle.getProductIr().getStateModel());
        assertTrue(bundle.getProductIr().getInteractionModes().contains("scheduler"));
        assertTrue(bundle.getProductIr().getInteractionModes().contains("case-management"));
        assertTrue(bundle.getExperienceBrief().getInteractionModel().contains("scheduler"));
        assertTrue(bundle.getExperienceBrief().getVisualDirection().getImagery().contains("信任") || bundle.getExperienceBrief().getVisualDirection().getImagery().contains("时间"));
        assertTrue(bundle.getExperienceBrief().getScreens().stream().anyMatch(screen -> screen.getPrimaryActions().contains("选择时段") || screen.getPrimaryActions().contains("确认结果")));
        assertTrue(bundle.getMockGraph().getCollections().stream().anyMatch(c -> "doctor".equals(c.getName())));
        assertTrue(bundle.getFlowGraph().getFlows().stream()
                .flatMap(flow -> flow.getSteps().stream())
                .anyMatch(step -> "confirm-booking".equals(step.getAction())));
    }

    @Test
    void compilesLegalCaseRequirementWithoutReferenceIntoStructuredIr() {
        ProjectManifest manifest = baseManifest(
                "legal-bundle",
                "做一个律师案件管理系统，支持客户档案、证据材料、文书审核、案件进度和开庭节点跟踪。"
        );
        manifest.setPages(List.of(
                page("/cases", "案件总览", "PRIMARY", List.of("案件列表", "阶段筛选", "负责人", "证据概览", "时间线")),
                page("/documents", "文书工作区", "PRIMARY", List.of("文档列表", "审核状态", "批注", "版本", "关联案件"))
        ));
        manifest.setTaskFlows(List.of(ProjectManifest.TaskFlow.builder()
                .id("flow_case_review")
                .description("律师从案件列表进入详情，审核文书并推进案件阶段。")
                .steps(List.of("进入案件详情", "审核文书", "补充证据", "推进案件阶段"))
                .build()));
        manifest.setMockData("""
                [
                  {"id":"C-1","title":"劳动纠纷案件","clientId":"CL-1","stage":"文书审核","hearingDate":"2026-05-18","ownerId":"U-1"},
                  {"id":"C-2","title":"商业合同争议","clientId":"CL-2","stage":"证据整理","hearingDate":"2026-05-26","ownerId":"U-2"}
                ]
                """);
        manifest.setMetaData(new HashMap<>(Map.of(
                "visual_primaryColor", "slate-700",
                "visual_accentColor", "amber-600"
        )));
        manifest.setDesignContract(ProjectManifest.DesignContract.builder()
                .primaryGoal(ProjectManifest.PrimaryGoal.COMPARE)
                .layoutRhythm(ProjectManifest.LayoutRhythm.LIST)
                .contentDensity(ProjectManifest.ContentDensity.HIGH)
                .mediaWeight(ProjectManifest.MediaWeight.TEXT_HEAVY)
                .shellPattern("SIDEBAR_PRIMARY_NAV")
                .contentMode("SIDEBAR_FIRST")
                .build());

        PrototypeBundle bundle = compiler.compile(manifest);

        assertNotNull(bundle);
        assertEquals("case", bundle.getProductIr().getPrimaryObject());
        assertEquals("OPERATE", bundle.getProductIr().getPrimaryMode());
        assertEquals("MULTI", bundle.getProductIr().getObjectMultiplicity());
        assertEquals("STRONG", bundle.getProductIr().getStateModel());
        assertEquals("MULTI_ROLE", bundle.getProductIr().getCollaborationMode());
        assertTrue(bundle.getProductIr().getInteractionModes().contains("case-management"));
        assertTrue(bundle.getProductIr().getInteractionModes().contains("document-workspace"));
        assertTrue(bundle.getExperienceBrief().getInteractionModel().contains("case-management"));
        assertTrue(bundle.getExperienceBrief().getVisualDirection().getTone().contains("严谨") || bundle.getExperienceBrief().getVisualDirection().getTone().contains("文档"));
        assertTrue(bundle.getExperienceBrief().getScreens().stream().anyMatch(screen -> screen.getActionLayout().contains("批注") || screen.getActionLayout().contains("状态")));
        assertTrue(bundle.getMockGraph().getCollections().stream().anyMatch(c -> "document".equals(c.getName())));
    }

    @Test
    void compilesRecruitingRequirementIntoCandidatePipeline() {
        ProjectManifest manifest = baseManifest(
                "recruiting-bundle",
                "做一个招聘 ATS 系统，支持候选人列表、简历详情、面试安排、流程推进和 offer 管理。"
        );

        PrototypeBundle bundle = compiler.compile(manifest);

        assertEquals("candidate", bundle.getProductIr().getPrimaryObject());
        assertTrue(bundle.getProductIr().getInteractionModes().contains("pipeline"));
        assertTrue(bundle.getExperienceBrief().getInteractionModel().contains("pipeline"));
    }

    @Test
    void compilesRealEstateBrokerRequirementIntoListingCommerce() {
        ProjectManifest manifest = baseManifest(
                "real-estate-bundle",
                "做一个房产经纪平台，支持房源列表、地图找房、房源详情、带看安排和经纪人主页。"
        );

        PrototypeBundle bundle = compiler.compile(manifest);

        assertEquals("listing", bundle.getProductIr().getPrimaryObject());
        assertTrue(bundle.getProductIr().getInteractionModes().contains("listing"));
        assertFalse(bundle.getProductIr().getInteractionModes().contains("scheduler"));
    }

    @Test
    void compilesNewsroomRequirementIntoEditorialWorkspace() {
        ProjectManifest manifest = baseManifest(
                "editorial-bundle",
                "做一个新闻内容运营平台，支持选题池、稿件编辑、审核发布、专题页和数据看板。"
        );

        PrototypeBundle bundle = compiler.compile(manifest);

        assertEquals("article", bundle.getProductIr().getPrimaryObject());
        assertTrue(bundle.getProductIr().getInteractionModes().contains("composer"));
        assertTrue(bundle.getProductIr().getInteractionModes().contains("review"));
    }

    @Test
    void compilesBillingRequirementIntoSubscriptionWorkspace() {
        ProjectManifest manifest = baseManifest(
                "billing-bundle",
                "做一个 SaaS 订阅计费平台，支持套餐管理、账单列表、支付记录、续费提醒和客户详情。"
        );

        PrototypeBundle bundle = compiler.compile(manifest);

        assertEquals("subscription", bundle.getProductIr().getPrimaryObject());
        assertTrue(bundle.getExperienceBrief().getInteractionModel().contains("workspace"));
        assertFalse(bundle.getExperienceBrief().getInteractionModel().contains("pipeline"));
    }

    @Test
    void compilesWarehouseRequirementIntoOperationsWorkflow() {
        ProjectManifest manifest = baseManifest(
                "warehouse-bundle",
                "做一个仓储 WMS 系统，支持入库上架、库位管理、拣货波次、出库复核和库存盘点。"
        );

        PrototypeBundle bundle = compiler.compile(manifest);

        assertEquals("job", bundle.getProductIr().getPrimaryObject());
        assertTrue(bundle.getProductIr().getInteractionModes().contains("queue"));
        assertTrue(bundle.getProductIr().getInteractionModes().contains("workflow"));
    }

    private ProjectManifest baseManifest(String id, String intent) {
        ProjectManifest manifest = new ProjectManifest();
        manifest.setId(id);
        manifest.setUserIntent(intent);
        manifest.setOverview("LingNow");
        manifest.setMetaData(new HashMap<>(Map.of("lang", "ZH")));
        return manifest;
    }

    private ProjectManifest.PageSpec page(String route, String description, String role, List<String> components) {
        return ProjectManifest.PageSpec.builder()
                .route(route)
                .description(description)
                .navType("NAV_ANCHOR")
                .navRole(role)
                .components(components)
                .build();
    }
}
