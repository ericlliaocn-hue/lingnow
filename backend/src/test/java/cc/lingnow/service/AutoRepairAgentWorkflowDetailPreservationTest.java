package cc.lingnow.service;

import cc.lingnow.llm.LlmClient;
import cc.lingnow.llm.LlmProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoRepairAgentWorkflowDetailPreservationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AutoRepairAgent autoRepairAgent = new AutoRepairAgent(new StubLlmClient(objectMapper), objectMapper);

    @Test
    void preservesWorkflowDetailTemplateDuringTargetedRepair() {
        String html = """
                <html>
                <body>
                <header><div>LingNow</div></header>
                <main>%s</main>
                <template x-if="selectedItem">
                  <div class="fixed inset-0">
                    <div data-lingnow-flow="pipeline-workflow-detail">
                      <div>原型状态</div>
                      <button data-lingnow-action="detail-secondary">@安排跟进</button>
                      <button data-lingnow-action="detail-tertiary">推进阶段</button>
                    </div>
                  </div>
                </template>
                </body>
                </html>
                """.formatted("x".repeat(41000));

        String repaired = autoRepairAgent.checkAndFix(
                html,
                "做一个 CRM 销售线索和客户成功管道，支持商机跟进、续费风险、负责人分配和阶段推进。",
                "[]",
                "CORRECTION_NEEDED: R6 失败，详情模态不是 CRM 详情处理面板，而是通用内容详情/评论区/点赞收藏模板。"
        );

        assertTrue(repaired.contains("data-lingnow-action=\"detail-tertiary\""));
        assertTrue(repaired.contains("原型状态"));
        assertFalse(repaired.contains("评论区"));
        assertFalse(repaired.contains("内容详情"));
    }

    private static class StubLlmClient extends LlmClient {
        StubLlmClient(ObjectMapper objectMapper) {
            super(new LlmProperties(), objectMapper);
        }

        @Override
        public String chat(String systemPrompt, String userPrompt) throws IOException {
            return """
                    {
                      "header": "<header><div>LingNow</div></header>",
                      "main": "<main>patched</main>",
                      "detailTemplate": "<template x-if=\\"selectedItem\\"><div><h2>内容详情</h2><div>评论区</div><div>点赞 收藏</div></div></template>"
                    }
                    """;
        }
    }
}
