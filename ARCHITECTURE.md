# LingNow 架构入口

这份文件是 LingNow 的默认架构入口，用来帮助任何协作者先快速对齐系统认知，再进入具体代码和实现细节。

## 默认阅读顺序

1. 工作流与 Agent 架构总览：`/Users/eric/workspace/lingnow/docs/workflow-agent-architecture.md`
2. 生成流程时序图：`/Users/eric/workspace/lingnow/docs/sequence-diagram.md`
3. 核心后端编排：`/Users/eric/workspace/lingnow/backend/src/main/java/cc/lingnow/service/GenerationService.java`
4. 核心状态模型：`/Users/eric/workspace/lingnow/backend/src/main/java/cc/lingnow/model/ProjectManifest.java`
5. 前端工作台：`/Users/eric/workspace/lingnow/frontend/src/views/Workbench.vue`

## 这套系统怎么理解

LingNow 不是“一句话 -> 一次生成完成”的单轮生成器，而是：

- 以 `ProjectManifest` 为单一事实源（SSOT）
- 以 `plan -> design -> audit/repair -> snapshot -> develop` 为主线
- 以多 Agent 串行编排推进需求落地
- 以契约校验、质量门、自动修复和版本回滚保证可控性

更准确地说，它是一个：

- **Workflow-first 多 Agent 编排系统**
- 而不是完全自治、自由协商式的 Agent Network

## 协作默认约定

以后凡是涉及以下场景，都优先以这份入口和工作流文档为准：

- 讨论架构
- 调整 Agent 职责
- 新增行业 benchmark
- 修改生成链路
- 调整设计约束、审计规则、fallback 策略

如果文档与实现不一致，应遵循以下顺序：

1. 先指出差异
2. 再说明当前代码真实行为
3. 最后决定是改文档还是改代码

## Agent 变更原则

对 Agent 或工作流做修改时，默认遵守以下原则：

1. **先改契约，再改 prompt**
    - 优先修改 `ProjectManifest`、`designContract`、`taskFlows`、`pages` 等结构化约束
    - 不先靠 prompt 硬扛不稳定问题

2. **先补质量门，再补 fallback**
    - 先让系统能识别坏结果
    - 再决定是否引入 deterministic fallback

3. **内部语言不外泄**
    - archetype、benchmark、contract、workflow 这些内部语义不应直接出现在用户页面文案中

4. **优先通用能力，再做行业默认值**
    - 登录/注册、分类、搜索、筛选、详情、发布等能力应尽量抽象成通用机制
    - 行业只提供默认词表、结构偏好或 benchmark

5. **先保证工作流稳定，再追求更强自治**
    - 当前系统的优势是可控编排，不是完全自治
    - 新能力要先服务稳定性、可验证性和可回滚性

6. **先看用户可见结果，再看源码是否“像对了”**
    - 审计不只看 HTML 字符串是否存在
    - 还要关注真实渲染结果、交互是否可用、需求是否真正落地

## 建议用法

以后如果要快速对齐上下文，可以直接说：

- “先按 `ARCHITECTURE.md` 理解项目”
- “先读工作流文档再继续”
- “按 Agent 变更原则来收这次改动”

这样协作方就会默认先对齐架构共识，再进入实现细节。
