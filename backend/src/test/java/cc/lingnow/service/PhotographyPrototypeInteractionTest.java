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

class PhotographyPrototypeInteractionTest {

    @Test
    void deterministicPhotographyPrototypeSupportsClickableEndToEndFlow() {
        ObjectMapper objectMapper = new ObjectMapper();
        LlmClient llmClient = new NoopLlmClient(objectMapper);
        UiDesignerAgent designerAgent = new UiDesignerAgent(llmClient, objectMapper);
        ProjectManifest manifest = photographyManifest();

        designerAgent.rebuildShapeAlignedPrototype(manifest);

        String html = manifest.getPrototypeHtml();
        assertNotNull(html);
        assertAll(
                () -> assertTrue(html.contains("data-lingnow-flow=\"photography-discover\"")),
                () -> assertTrue(html.contains("data-lingnow-flow=\"photography-directory\"")),
                () -> assertTrue(html.contains("data-lingnow-flow=\"photography-availability\"")),
                () -> assertTrue(html.contains("data-lingnow-flow=\"photography-inquiry\"")),
                () -> assertTrue(html.contains("data-lingnow-flow=\"photography-orders\"")),
                () -> assertTrue(html.contains("data-lingnow-flow=\"photography-detail\"")),
                () -> assertTrue(html.contains("data-lingnow-action=\"open-detail\"")),
                () -> assertTrue(html.contains("data-lingnow-action=\"pick-slot\"")),
                () -> assertTrue(html.contains("data-lingnow-action=\"submit-inquiry\"")),
                () -> assertTrue(html.contains("data-lingnow-action=\"advance-order\"")),
                () -> assertTrue(html.contains("openDetail(item)")),
                () -> assertTrue(html.contains("startInquiry(item)")),
                () -> assertTrue(html.contains("pickSlot(slot)")),
                () -> assertTrue(html.contains("submitInquiry()")),
                () -> assertTrue(html.contains("advanceOrder(order)"))
        );

        FunctionalAuditorAgent auditorAgent = new FunctionalAuditorAgent(llmClient);
        FunctionalAuditorAgent.AuditOutcome outcome = auditorAgent.verify(manifest);
        assertTrue(outcome.isPassed(), outcome.getSummary());
    }

    private ProjectManifest photographyManifest() {
        Map<String, String> metaData = new HashMap<>();
        metaData.put("lang", "ZH");

        return ProjectManifest.builder()
                .id("photography-interaction-test")
                .userIntent("做一个摄影师接单平台，支持作品展示、档期预约、客户询价、订单交付")
                .overview("LingNow")
                .mindMap("""
                        发现作品
                        摄影师
                        档期预约
                        客户询价
                        订单交付
                        """)
                .metaData(metaData)
                .taskFlows(List.of(ProjectManifest.TaskFlow.builder()
                        .id("booking-flow")
                        .description("客户从作品进入详情，选择档期，提交询价，并推进订单交付。")
                        .steps(List.of("打开作品详情", "选择可约档期", "提交询价", "推进订单"))
                        .build()))
                .designContract(ProjectManifest.DesignContract.builder()
                        .primaryGoal(ProjectManifest.PrimaryGoal.TRANSACT)
                        .contentUnit(ProjectManifest.ContentUnit.LISTING)
                        .consumptionMode(ProjectManifest.ConsumptionMode.DISCOVER_FIRST)
                        .mediaWeight(ProjectManifest.MediaWeight.VISUAL_HEAVY)
                        .layoutRhythm(ProjectManifest.LayoutRhythm.WATERFALL)
                        .contentDensity(ProjectManifest.ContentDensity.HIGH)
                        .mainLoop(ProjectManifest.MainLoop.COMPARE_BUY)
                        .uiTone(ProjectManifest.UiTone.PROFESSIONAL)
                        .minPrimarySections(5)
                        .minPrimaryCards(4)
                        .prefersRealMedia(true)
                        .requiresSearch(true)
                        .requiresComposer(true)
                        .requiresDetailOverlay(true)
                        .build())
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
