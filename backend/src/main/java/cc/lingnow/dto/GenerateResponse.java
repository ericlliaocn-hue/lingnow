package cc.lingnow.dto;

import cc.lingnow.model.ProjectManifest;
import java.util.Map;

/**
 * Generate Response DTO
 * 
 * @param title 生成的应用标题
 * @param description 应用描述
 * @param files 文件映射表 {文件名：文件内容}
 * @param dependencies 依赖包映射表 {包名：版本号}
 * @param manifest 项目全局清单
 */
public record GenerateResponse(
    String title,
    String description,
    Map<String, String> files,
    Map<String, String> dependencies,
    ProjectManifest manifest
) {
}
