package cc.lingnow.service;

import cc.lingnow.llm.LlmClient;
import cc.lingnow.llm.LlmProperties;
import cc.lingnow.model.ProjectManifest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PrototypeShapeGeneralizationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ManifestContractValidator contractValidator = new ManifestContractValidator();
    private final UiDesignerAgent designerAgent = new UiDesignerAgent(new NoopLlmClient(objectMapper), objectMapper);

    @Test
    void bookingPrototypeDoesNotFallBackToContentCommunityShell() {
        ProjectManifest manifest = manifest(
                "booking-generalization",
                "做一个深夜食堂预约点单系统，支持排队取号、菜单浏览、预约座位、订单状态和付款确认。",
                "首页\n菜单\n预约\n订单\n门店",
                List.of(
                        page("/home", "聚合排队取号、预约座位、菜单推荐和订单提醒"),
                        page("/menu", "菜单浏览与加购"),
                        page("/reservation", "预约座位与到店时间选择"),
                        page("/orders", "订单状态追踪"),
                        page("/store", "门店信息")
                )
        );
        manifest.setUxStrategy(Map.of("shell_pattern", "MINIMAL_HEADER_DRAWER_ONLY"));

        contractValidator.normalize(manifest);
        designerAgent.rebuildShapeAlignedPrototype(manifest);

        String html = manifest.getPrototypeHtml();
        assertAll(
                () -> assertEquals("SIDEBAR_FIRST", manifest.getDesignContract().getContentMode()),
                () -> assertEquals("SIDEBAR_PRIMARY_NAV", manifest.getDesignContract().getShellPattern()),
                () -> assertTrue(html.contains("data-lingnow-flow=\"booking-order\"")),
                () -> assertTrue(html.contains("预约点单原型")),
                () -> assertTrue(html.contains("detailReturnHash")),
                () -> assertTrue(html.contains("@click=\"closeDetail()\" class=\"absolute inset-0 bg-slate-900/60 backdrop-blur-sm\"")),
                () -> assertTrue(html.contains("data-lingnow-action=\"confirm-generic-flow\"")),
                () -> assertFalse(html.contains("视觉内容社区")),
                () -> assertFalse(html.contains("AI 编程"))
        );
    }

    @Test
    void operationsDashboardSeedIsDomainSpecificAndClickable() {
        ProjectManifest manifest = manifest(
                "dashboard-generalization",
                "做一个全自动智能咖啡机运维看板，展示设备状态、补货预警、故障工单、远程控制和维护记录。",
                "dashboard\nmap\nreplenishment\nworkorders\nmaintenance",
                List.of(
                        page("/dashboard", "设备总览首页"),
                        page("/map", "门店设备地图"),
                        page("/replenishment", "补货预警"),
                        page("/workorders", "故障工单"),
                        page("/maintenance", "维护记录")
                )
        );

        contractValidator.normalize(manifest);
        designerAgent.rebuildShapeAlignedPrototype(manifest);

        String html = manifest.getPrototypeHtml();
        assertAll(
                () -> assertEquals(ProjectManifest.LayoutRhythm.DASHBOARD, manifest.getDesignContract().getLayoutRhythm()),
                () -> assertTrue(html.contains("data-lingnow-flow=\"ops-dashboard\"")),
                () -> assertTrue(html.contains("运维工作台")),
                () -> assertTrue(html.contains("虹桥门店 A12 咖啡机")),
                () -> assertTrue(html.contains("data-lingnow-action=\"advance-workflow\"")),
                () -> assertTrue(html.contains("openDetail(item)")),
                () -> assertFalse(html.contains("视觉内容社区"))
        );
    }

    @Test
    void petCommunityUsesPetSpecificContentFirstSeed() {
        ProjectManifest manifest = manifest(
                "pet-community-generalization",
                "做一个宠物社交社区，用户可以发布宠物照片、点赞评论、收藏内容，并从发现页进入宠物详情和评论互动。",
                "发现\n话题\n关注\n热门\n社区精选",
                List.of(
                        page("/discover", "宠物照片瀑布流发现"),
                        page("/topics", "宠物话题聚合"),
                        page("/following", "关注动态"),
                        page("/trending", "热门宠物内容"),
                        page("/editor-picks", "社区精选")
                )
        );

        contractValidator.normalize(manifest);
        designerAgent.rebuildShapeAlignedPrototype(manifest);

        String html = manifest.getPrototypeHtml();
        assertAll(
                () -> assertEquals("CONTENT_FIRST", manifest.getDesignContract().getContentMode()),
                () -> assertTrue(html.contains("宠物社交社区")),
                () -> assertTrue(html.contains("布偶猫第一次坐地铁")),
                () -> assertTrue(html.contains("detailReturnHash")),
                () -> assertTrue(html.contains("@click=\"closeDetail()\" class=\"absolute inset-0 bg-slate-900/60 backdrop-blur-sm\"")),
                () -> assertTrue(html.contains("openDetail(item)")),
                () -> assertFalse(html.contains("视觉内容社区")),
                () -> assertFalse(html.contains("AI 编程"))
        );
    }

    @Test
    void readingNotesCommunityUsesStudySpecificSeed() {
        ProjectManifest manifest = manifest(
                "reading-notes-generalization",
                "做一个精读会共读笔记社区，支持文章阅读、划线笔记、收藏、讨论回复和学习进度。",
                "推荐\n本周共读\n笔记\n讨论\n进度",
                List.of(
                        page("/home", "共读推荐首页"),
                        page("/weekly", "本周共读任务"),
                        page("/notes", "划线笔记"),
                        page("/discussion", "讨论回复"),
                        page("/progress", "学习进度")
                )
        );

        contractValidator.normalize(manifest);
        designerAgent.rebuildShapeAlignedPrototype(manifest);

        String html = manifest.getPrototypeHtml();
        assertAll(
                () -> assertEquals("CONTENT_FIRST", manifest.getDesignContract().getContentMode()),
                () -> assertTrue(html.contains("共读笔记社区")),
                () -> assertTrue(html.contains("本周共读：第一章慢读标注")),
                () -> assertTrue(html.contains("学习进度")),
                () -> assertFalse(html.contains("技术内容社区")),
                () -> assertFalse(html.contains("AI 编程"))
        );
    }

    @Test
    void broaderBusinessTypesUseDomainSpecificInteractiveSeeds() {
        record PrototypeCase(
                String id,
                String intent,
                String expectedFlow,
                String expectedLayout,
                String expectedCopy,
                String expectedShellLabel,
                String expectedAccent,
                String forbiddenCopy
        ) {
        }
        List<PrototypeCase> cases = List.of(
                new PrototypeCase("clinic-booking", "做一个医美诊所预约系统，支持项目咨询、医生档期、到诊提醒、就诊单和支付确认。", "booking-order", "booking-studio", "诊疗预约原型", "新建预约", "--shell-accent: #0f766e;", "深夜食堂"),
                new PrototypeCase("fitness-schedule", "做一个健身私教排课系统，支持课程预约、教练档期、课包消耗、签到和会员跟进。", "booking-order", "booking-studio", "训练预约原型", "新建排课", "--shell-accent: #16a34a;", "深夜食堂"),
                new PrototypeCase("vehicle-marketplace", "做一个二手车交易平台，支持车源列表、预约看车、议价、定金订单和交付资料。", "commerce-order", "marketplace-hub", "车源交易原型", "发布车源", "--shell-accent: #d97706;", "羊毛廓形短外套"),
                new PrototypeCase("rental-marketplace", "做一个房源租赁交易平台，支持房源浏览、预约看房、合同签约、押金支付和交付状态。", "commerce-order", "marketplace-hub", "房源租赁原型", "发布房源", "--shell-accent: #047857;", "羊毛廓形短外套"),
                new PrototypeCase("event-ticketing", "做一个活动票务报名平台，支持场次展示、门票支付、电子票、现场核销和报名状态。", "commerce-order", "marketplace-hub", "票务报名原型", "发布场次", "--shell-accent: #7c3aed;", "羊毛廓形短外套"),
                new PrototypeCase("pipeline-crm", "做一个 CRM 销售线索和客户成功管道，支持商机跟进、续费风险、负责人分配和阶段推进。", "pipeline-workflow", "pipeline-board", "管道工作台", "新建线索", "--shell-accent: #2563eb;", "虹桥门店 A12 咖啡机"),
                new PrototypeCase("logistics-ops", "做一个物流履约运营看板，支持配送延迟预警、客服队列、库存安全线和现场工单派发。", "ops-dashboard", "ops-command", "运营看板", "新建工单", "--shell-accent: #ea580c;", "虹桥门店 A12 咖啡机"),
                new PrototypeCase("course-bootcamp", "做一个在线课程训练营系统，支持课程任务、作业提交、直播答疑、学习计划和证书进度。", "learning-progress", "learning-campus", "课程学习原型", "新建课程", "--shell-accent: #4f46e5;", "第一章：慢读与标注")
        );

        for (PrototypeCase testCase : cases) {
            ProjectManifest manifest = manifest(
                    testCase.id(),
                    testCase.intent(),
                    "首页\n列表\n详情\n订单\n状态",
                    List.of(
                            page("/home", "首页总览"),
                            page("/list", "核心对象列表"),
                            page("/detail", "对象详情"),
                            page("/orders", "订单或流程状态"),
                            page("/settings", "配置与记录")
                    )
            );

            contractValidator.normalize(manifest);
            designerAgent.rebuildShapeAlignedPrototype(manifest);

            String html = manifest.getPrototypeHtml();
            assertAll(testCase.id(),
                    () -> assertTrue(html.contains("data-lingnow-flow=\"" + testCase.expectedFlow() + "\"")),
                    () -> assertTrue(html.contains("data-lingnow-layout=\"" + testCase.expectedLayout() + "\"")),
                    () -> assertTrue(html.contains(testCase.expectedCopy())),
                    () -> assertTrue(html.contains(testCase.expectedShellLabel())),
                    () -> assertTrue(html.contains(testCase.expectedAccent())),
                    () -> assertTrue(html.contains("openDetail(item)")),
                    () -> assertTrue(html.contains("data-lingnow-action=\"advance-workflow\"")),
                    () -> assertTrue(html.contains("data-lingnow-action=\"detail-tertiary\"")),
                    () -> assertFalse(html.contains(testCase.forbiddenCopy())),
                    () -> assertFalse(html.contains("视觉内容社区")),
                    () -> assertFalse(html.contains("AI 编程"))
            );
        }
    }

    private ProjectManifest manifest(String id, String intent, String mindMap, List<ProjectManifest.PageSpec> pages) {
        Map<String, String> metaData = new HashMap<>();
        metaData.put("lang", "ZH");
        return ProjectManifest.builder()
                .id(id)
                .userIntent(intent)
                .overview("LingNow")
                .mindMap(mindMap)
                .pages(pages)
                .metaData(metaData)
                .build();
    }

    private ProjectManifest.PageSpec page(String route, String description) {
        return ProjectManifest.PageSpec.builder()
                .route(route)
                .description(description)
                .navType("NAV_ANCHOR")
                .navRole("PRIMARY")
                .components(List.of("可点击卡片", "状态反馈", "流程按钮"))
                .build();
    }

    private static class NoopLlmClient extends LlmClient {
        NoopLlmClient(ObjectMapper objectMapper) {
            super(new LlmProperties(), objectMapper);
        }

        @Override
        public String chat(String systemPrompt, String userPrompt) throws IOException {
            return "VERIFIED";
        }
    }
}
