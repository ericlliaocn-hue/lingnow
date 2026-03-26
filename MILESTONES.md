# LingNow 自主智能体产品工厂：落地里程碑与进度看板

> 目的：把“数字军团”从愿景落到工程，确保每次迭代都可验收、可回放、可控进度  
> 最后更新：2026-03-26

## 北极星（North Star）

- **Zero-to-One in Seconds**：从自然语言到**可运行产品**的秒级转化（不是只吐代码片段）。
- **持续迭代修改**：支持多轮对话的“精准改动”，而不是每次推倒重来。
- **虚拟软件公司 / 数字军团**：每个 Agent 像真实角色一样**各司其职、可并行、可验收、可追责**。

## 工程原则（必须遵守）

- **ProjectManifest 是唯一事实来源（SSoT）**：所有 Agent 只通过 Manifest 协作（读写），禁止“口口相传”。
- **阶段确认点（Stage Gates）**：PRD 确认 → 原型确认 → 代码确认 → 部署确认；每个阶段都能收集修改并回写 Manifest。
- **QA 一票否决**：未通过编译/基础测试不得进入下一阶段或交付。
- **禁止 Mock**：对用户可见的产物（PRD/原型/代码/部署）必须由真实链路产出；不允许返回硬编码占位数据来“假跑通”。
- **安全默认**：API Key 只能来自环境变量/本地配置；严禁写入仓库、日志脱敏；最小权限与可审计。

## LLM 接入约定（中转模型：metapi）

- Base URL：`https://codex.metapi.cc/v1`
- Key：sk-meowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeow888
- 统一封装：后端提供一个 `LLMClient`（或同名服务），集中处理：
  - 重试/超时/限流（含退避）
  - 结构化输出（严格 JSON 或 JSON Schema）
  - 观测性（requestId、阶段耗时、token 统计、错误码）
  - 安全（日志脱敏、错误回传不泄露密钥）

> 提醒：如果密钥曾在聊天/截图/日志/文档中出现，应视为已泄露并尽快轮换。

---

## 里程碑路线图（从可见成果递进）

### M0：地基（可跑、可追踪、无 Mock）

**目标**
- 后端编译可用、接口契约稳定、真实 LLM 链路可调用、可观测（能定位失败原因）。

**任务清单**
- [ ] 后端 `mvn compile` 通过（修复 DTO/Service/Controller 契约不一致）。
- [ ] 固化 `/api/generate` 的请求/响应协议，与前端展示结构一致（`title/description/files/dependencies`）。
- [ ] 接入 `LLMClient`（metapi），并把“生成”统一走真实 LLM 流程（不允许硬编码占位返回）。
- [ ] 引入最小的“生成任务追踪”：每次请求生成 `generationId`，记录阶段耗时与失败原因。
- [ ] 安全基线：`.env`/密钥不入库；日志脱敏；失败回包不泄露敏感信息。

**验收标准（DoD）**
- [ ] 输入任意 prompt，后端能稳定返回结构化结果或可解释错误（含 `generationId`）。
- [ ] 任何失败能在日志中定位到：网络/鉴权/模型输出不合法/解析失败/超时等。

---

### M1：秒级惊艳（生成即运行 Live Preview）

**目标**
- 用户第一次就看到“能跑的东西”（而不是只看到代码文本）。

**任务清单**
- [ ] 前端集成 Sandpack（例如 `sandpack-vue3`），右侧预览区直接运行 `result.files`。
- [ ] 预览错误可视化（运行时报错、依赖缺失、入口文件缺失等）。
- [ ] 支持基础交互（按钮点击、表单输入）与刷新不丢“当前生成结果”。

**验收标准（DoD）**
- [ ] 输入一句话 → < 10 秒看到可运行页面（或清晰错误）。

---

### M2：编排骨架（Orchestrator + Manifest + 状态机 + 事件流）

**目标**
- 把“单次生成函数”升级为“可编排流水线”，为多 Agent 奠基。

**任务清单**
- [ ] 定义并落地 `ProjectManifest`（最小可用字段：`id/userIntent/status/features/pages/prototypeHtml/generatedFiles/chatHistory/modificationRequests`）。
- [ ] 状态机：`PLANNING -> DESIGNING -> CODING -> QA -> DONE`（支持暂停等待用户确认）。
- [ ] 事件流：SSE/WebSocket 推送阶段进度（先推骨架再补全细节，保证秒级反馈）。
- [ ] Manifest 持久化策略（先本地文件/内存，后迁移 DB），确保可回放。

**验收标准（DoD）**
- [ ] 前端能看到每个阶段的进度与阶段产物，并能“确认/提交修改意见”。

---

### M3：Product Architect Agent（结构化 PRD）

**目标**
- 把模糊需求变成可确认、可验收的结构化 PRD。

**任务清单**
- [ ] Agent 产出严格 JSON：功能列表、页面列表、技术栈建议、非功能约束、风险点。
- [ ] PRD 确认 UI：确认/修改双路径；修改写入 `modificationRequests` 并触发 PRD 再生成。
- [ ] PRD 输出校验（JSON Schema / 结构检查），失败可自我修复或回退重试。

**验收标准（DoD）**
- [ ] 用户输入一句话，30 秒内得到可确认 PRD；用户修改后能精准更新对应字段。

---

### M4：UI Designer Agent（可点击原型先于代码）

**目标**
- 让用户在写代码前先确认交互与布局，降低返工。

**任务清单**
- [ ] Agent 产出 `designSystem + prototypeHtml`（尽量自包含、可在 iframe 运行）。
- [ ] 原型预览 UI：`iframe srcdoc` 渲染；支持“提修改意见 → 热更新原型”。
- [ ] 原型与 PRD 的一致性检查（页面/组件覆盖率、关键路径交互）。

**验收标准（DoD）**
- [ ] 原型可点击、可交互；用户反馈能在下一次迭代中体现且可追踪。

---

### M5：并行开发（Frontend/Backend Dev Agents + 依赖协同）

**目标**
- 从“单文件组件”升级到“可运行项目（前后端都能启动）”。

**任务清单**
- [ ] Backend Dev Agent 先产出 `databaseSchema + apiSchema(OpenAPI)` 写入 Manifest。
- [ ] Frontend Dev Agent 基于 `apiSchema` 生成 API client、页面骨架、把原型拆成组件并接数据。
- [ ] 产物统一汇总到 `generatedFiles`（可打包下载/可预览/可落盘生成工程）。

**验收标准（DoD）**
- [ ] 原型确认后，5 分钟内得到可运行的最小全栈闭环（本地可启动）。

---

### M6：持续迭代修改（差量更新，不推倒重来）

**目标**
- 用户说“改颜色/加字段/换布局”，系统只改必要文件与必要片段，并保留历史与回滚点。

**任务清单**
- [ ] 修改请求 → 先产出“变更计划”（影响范围、文件列表、风险）→ 用户确认 → 执行 patch。
- [ ] 基于 Manifest 的变更日志：每次修改有原因、有 diff、有回滚点。
- [ ] 关键文件的结构化定位（组件树、路由表、API 映射）支撑精准改动。

**验收标准（DoD）**
- [ ] 同一项目连续 10 次修改仍能保持可运行；每次修改可解释、可回滚。

---

### M7：质量与交付闭环（QA Gate + 一键部署）

**目标**
- 让“能生成”变成“能交付”，QA 拦截错误，DevOps 自动出公网链接。

**任务清单**
- [ ] QA：前端构建、后端编译、基础 e2e（从 `scenes.md` 抽场景做回归）。
- [ ] DevOps：生成 Dockerfile/docker-compose；一键部署；回写 `deploymentUrl`。
- [ ] 发布记录：生成版本号、变更摘要、可追溯。

**验收标准（DoD）**
- [ ] 用户点击“部署”，3 分钟内拿到可访问链接；失败可定位并可重试。

---

## 建议的“每周节奏”（可选）

- 周一：确定本周里程碑与 DoD（只选 1 个里程碑推进）
- 周三：中期验收（必须能演示用户可见成果）
- 周五：完成 DoD + 写回本文件勾选进度 + 补充下周风险清单

