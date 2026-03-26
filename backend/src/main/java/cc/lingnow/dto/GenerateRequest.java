package cc.lingnow.dto;

/**
 * Generate Request DTO
 * 
 * @param prompt 用户输入的自然语言描述
 * @param sessionId 会话 ID
 * @param isModification 是否是修改请求
 */
public record GenerateRequest(
    String prompt,
    String sessionId,
    boolean isModification
) {
}
