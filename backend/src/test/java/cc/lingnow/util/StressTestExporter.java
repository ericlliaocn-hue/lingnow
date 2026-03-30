package cc.lingnow.util;

import cc.lingnow.model.ProjectManifestEntity;
import cc.lingnow.repository.ManifestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

@SpringBootTest
@ActiveProfiles("test")
public class StressTestExporter {

    @Autowired
    private ManifestRepository repository;

    @Test
    public void exportToCsv() throws IOException {
        List<ProjectManifestEntity> results = repository.findAll(); // 获取全部压测结果

        try (FileWriter writer = new FileWriter("/Users/eric/workspace/lingnow/LINGNOW_STRESS_TEST_200.csv")) {
            writer.write("ID,Intent,Status,Metadata\n");
            int count = 0;
            int totalChecked = 0;

            for (ProjectManifestEntity entity : results) {
                totalChecked++;
                String intent = entity.getUserIntent() != null ? entity.getUserIntent() : "";
                String intentLower = intent.toLowerCase();

                // Debug 打印：看看库里到底有什么（只打印前 10 条）
                if (totalChecked <= 10) {
                    System.out.println(">>> [Debug] Found Project ID: " + entity.getId() + " | Intent: " + intent);
                }

                // 修正过滤器：保留 test- 开头的，过滤掉 session-
                boolean isJunk = intentLower.contains("pet") || intentLower.contains("social") ||
                        intentLower.contains("todo") || intentLower.contains("test app") ||
                        intentLower.contains("宠物") || intentLower.contains("社交") ||
                        intentLower.contains("待办") || intentLower.contains("测试") ||
                        entity.getId().contains("ssion-") || entity.getId().contains("scenario-");

                if (isJunk) continue;

                writer.write(String.format("%s,\"%s\",\"%s\",\"%s\"\n",
                        entity.getId(),
                        intent.replace("\"", "'"),
                        entity.getStatus(),
                        entity.getMetaDataJson() != null ? entity.getMetaDataJson().replace("\"", "'") : "{}"));

                count++;
                if (count >= 200) break;
            }
            System.out.println(">>> [SUCCESS] Total Checked: " + totalChecked + " | Harvested " + count + " PURE lifestyle industry cases.");
        }
        System.out.println(">>> [SUCCESS] 200 Industry Data Exported to LINGNOW_STRESS_TEST_200.csv");
    }
}
