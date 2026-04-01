# 产品形态推导器实施与验收计划

这份文档定义 LingNow 从“行业/站点模板思维”升级到“产品形态推导器”的实施方案、验收方式与回归标准。

目标不是把“类似掘金”单独调对，而是让系统具备稳定的产品经理级判断能力：

- 先推导产品形态
- 再生成结构化契约
- 再驱动原型生成与 fallback
- 最后由产品经理验收闭环

## 1. 为什么要做

当前系统的主要问题不是“社区类做得不够像某个站”，而是：

- 把大量社区类需求过度收敛到同一种视觉与交互节奏
- 让 prompt 和 fallback 都隐式绑定到固定 benchmark
- 缺少“产品任务 -> 信息结构 -> 页面布局”的中间推导层
- 缺少产品经理可执行的最终验收机制

这会导致两个后果：

- 社区类需求彼此同质化
- 非社区类需求也可能被社区默认逻辑污染

## 2. 目标

构建一个可扩展的“产品形态推导器”。

它必须满足：

- 不依赖“关键词 -> 固定模板”的硬编码映射
- 先输出结构化形态字段，再驱动设计生成
- 同时适用于社区类与非社区类产品
- fallback 与主生成链路遵循同一份结构契约
- 产品经理可对“推导结果”和“最终原型”分别验收

## 3. 核心原则

### 3.1 不是站点模板库

系统不能直接做这种事：

- `技术 -> 掘金模板`
- `生活 -> 小红书模板`
- `讨论 -> 贴吧模板`

允许参考这些产品，但只能把它们当作“形态样本”，不能当成代码层的直接目标。

### 3.2 关键词只是证据，不是结论

关键词只能提高某些形态特征的置信度，不能直接决定模板。

例如：

- “技术/代码/教程”可以提高 `read_first`、`text_heavy`、`author_trust`
- “分享/灵感/收藏”可以提高 `discover_first`、`visual_heavy`
- “讨论/圈子/回帖”可以提高 `discuss_first`、`thread`

但最终结论必须由多维特征共同决定。

### 3.3 先改契约，再改 prompt

产品形态推导必须先落在结构化契约中：

- `ProjectManifest`
- `DesignContract`

`UiDesignerAgent` 和 fallback 只能消费契约，不能自己偷偷用品牌 benchmark 再做一遍隐式分类。

### 3.4 验收不是“生成完就交货”

必须有完整闭环：

1. 推导产品形态
2. 生成契约
3. 生成原型
4. 产品经理验收
5. 回归修正
6. 达标后交付

## 4. 形态模型

### 4.1 一级目标字段

建议新增或推导以下字段：

- `primaryGoal`
    - `read`
    - `discover`
    - `compare`
    - `discuss`
    - `monitor`
    - `transact`
    - `learn`

- `contentUnit`
    - `article`
    - `post`
    - `thread`
    - `qa`
    - `listing`
    - `dashboard`
    - `news_story`
    - `mixed`

- `consumptionMode`
    - `read_first`
    - `discover_first`
    - `verify_first`
    - `discuss_first`
    - `real_time_first`

- `mediaWeight`
    - `text_heavy`
    - `mixed`
    - `visual_heavy`
    - `video_heavy`

- `layoutRhythm`
    - `list`
    - `compact_card`
    - `waterfall`
    - `thread`
    - `editorial`
    - `dashboard`

- `contentDensity`
    - `low`
    - `medium`
    - `high`

- `signalPriority`
    - `author_trust`
    - `heat`
    - `recency`
    - `discussion`
    - `save_rate`
    - `price`
    - `status`
    - `progress`

- `navigationMode`
    - `topic_tab`
    - `subforum`
    - `channel`
    - `category`
    - `utility_first`

- `mainLoop`
    - `read_save`
    - `scroll_discover`
    - `ask_answer`
    - `post_reply`
    - `compare_buy`
    - `monitor_act`
    - `learn_continue`

- `uiTone`
    - `professional`
    - `editorial`
    - `lively`
    - `forum`
    - `plaza`
    - `enterprise`

### 4.2 社区类主要形态样本

以下样本只用于帮助推导，不是模板绑定目标。

#### 技术知识型

- 代表：掘金、CSDN、Linux.do
- 共同点：
    - 阅读优先
    - 文本占比高
    - 标题、摘要、作者、标签重要
    - 更强调专业性与可读性
- 常见倾向：
    - `primaryGoal=read`
    - `contentUnit=article | thread`
    - `consumptionMode=read_first`
    - `mediaWeight=text_heavy | mixed`
    - `layoutRhythm=list | compact_card`
    - `signalPriority=author_trust + save_rate + discussion`
    - `uiTone=professional`

#### 视觉发现型

- 代表：小红书、Pinterest
- 共同点：
    - 发现优先
    - 视觉刺激优先
    - 封面图、收藏感、情绪价值强
- 常见倾向：
    - `primaryGoal=discover`
    - `contentUnit=post`
    - `consumptionMode=discover_first`
    - `mediaWeight=visual_heavy`
    - `layoutRhythm=waterfall`
    - `signalPriority=save_rate + heat`
    - `uiTone=lively`

#### 社交时效型

- 代表：微博、X
- 共同点：
    - 时间敏感
    - 短内容、高频流动
    - 热点与趋势榜重要
- 常见倾向：
    - `primaryGoal=discover | discuss`
    - `contentUnit=post`
    - `consumptionMode=real_time_first`
    - `mediaWeight=mixed`
    - `layoutRhythm=compact_card`
    - `signalPriority=recency + heat`
    - `uiTone=plaza`

#### 深度问答型

- 代表：知乎、StackOverflow
- 共同点：
    - 问题驱动
    - 回答质量与权威性更关键
    - 用户会对比不同回答
- 常见倾向：
    - `primaryGoal=read | verify`
    - `contentUnit=qa`
    - `consumptionMode=verify_first`
    - `mediaWeight=text_heavy`
    - `layoutRhythm=list`
    - `signalPriority=author_trust + discussion`
    - `uiTone=editorial | professional`

#### 垂直论坛型

- 代表：贴吧、虎扑、猫扑、Linux.do 部分板块
- 共同点：
    - 帖子与回复链路强
    - 版块和话题分区重要
    - 回复数与最后活跃时间重要
- 常见倾向：
    - `primaryGoal=discuss`
    - `contentUnit=thread`
    - `consumptionMode=discuss_first`
    - `mediaWeight=text_heavy | mixed`
    - `layoutRhythm=thread | list`
    - `signalPriority=discussion + recency`
    - `uiTone=forum`

#### 资讯门户型

- 代表：36Kr、商业媒体站、早报类站点
- 共同点：
    - 获取洞察
    - 编辑精选、栏目结构强
    - 媒体属性重于社区属性
- 常见倾向：
    - `primaryGoal=read`
    - `contentUnit=news_story`
    - `consumptionMode=read_first`
    - `mediaWeight=text_heavy | mixed`
    - `layoutRhythm=editorial | list`
    - `signalPriority=recency + editorial`
    - `uiTone=editorial`

#### 聊天房间型

- 代表：Discord、Slack 社区、聊天室型社区
- 共同点：
    - 在线参与优先
    - 频道结构与未读提示重要
    - 同步互动强于内容沉淀
- 常见倾向：
    - `primaryGoal=discuss`
    - `contentUnit=post | mixed`
    - `consumptionMode=real_time_first`
    - `mediaWeight=mixed`
    - `layoutRhythm=thread | dashboard`
    - `signalPriority=status + recency`
    - `uiTone=forum | enterprise`

### 4.3 非社区类产品也要共用这套逻辑

形态推导器不只用于社区。

同一套字段也要能解释：

- SaaS 后台
    - `primaryGoal=monitor`
    - `contentUnit=dashboard`
    - `layoutRhythm=dashboard`
    - `signalPriority=status`

- 电商
    - `primaryGoal=compare | transact`
    - `contentUnit=listing`
    - `layoutRhythm=compact_card | grid`
    - `signalPriority=price + heat`

- 教育平台
    - `primaryGoal=learn`
    - `contentUnit=mixed`
    - `layoutRhythm=list | compact_card`
    - `signalPriority=progress + author_trust`

- 企业官网
    - `primaryGoal=discover | compare`
    - `contentUnit=mixed`
    - `layoutRhythm=editorial`
    - `signalPriority=brand + clarity`

## 5. 代码实施方案

### 5.1 数据模型

修改 [ProjectManifest.java](/Users/eric/workspace/lingnow/backend/src/main/java/cc/lingnow/model/ProjectManifest.java)

动作：

- 扩展 `DesignContract`
- 优先使用 enum 而不是自由字符串
- 保持 Jackson 可序列化/反序列化

建议新增：

- `primaryGoal`
- `contentUnit`
- `consumptionMode`
- `mediaWeight`
- `layoutRhythm`
- `contentDensity`
- `signalPriority`
- `navigationMode`
- `mainLoop`
- `uiTone`

### 5.2 推导器

修改 [ManifestContractValidator.java](/Users/eric/workspace/lingnow/backend/src/main/java/cc/lingnow/service/ManifestContractValidator.java)

建议：

- 新增 `detectDetailedShapeProfile(...)`
- 或拆出独立类 `ProductShapeInferer`

实现步骤：

1. 从 `userIntent`、`archetype`、`pages`、`taskFlows` 中提取证据
2. 给候选字段打分
3. 形成形态推导结果
4. 写入 `DesignContract`
5. 衍生旧有 contract 字段

关键要求：

- 不允许直接输出“像掘金/像小红书”
- 不允许简单地 `contains("技术") -> 技术模板`
- 必须输出可解释的结构字段

### 5.3 原型生成器

修改 [UiDesignerAgent.java](/Users/eric/workspace/lingnow/backend/src/main/java/cc/lingnow/service/UiDesignerAgent.java)

动作：

- 删除品牌 benchmark 硬编码
- 删除生活方式固定 seed 数据
- 删除社区默认瀑布流假设
- 改为根据 `DesignContract` 生成 `shapeInstruction`

Prompt 改造要求：

- 强调布局节奏
- 强调内容密度
- 强调信号排序
- 强调交互闭环
- 禁止出现品牌 benchmark 名称

### 5.4 Fallback 改造

`buildFallbackComponent` 与相关辅助方法必须一起改。

要求：

- fallback 与主生成链路消费同一份形态契约
- 不允许使用固定生活方式文案
- 不允许写死“穿搭/美妆/探店/灵感”
- 需要准备多套通用 seed 结构：
    - 知识流
    - 视觉流
    - 帖子讨论流
    - 资讯流
    - 非社区流

## 6. 产品经理验收机制

这是本次改造的强制闭环。

### 6.1 验收顺序

1. 验形态推导
2. 验原型结果
3. 验 fallback 结果
4. 验跨样本回归
5. 全部通过才算交付

### 6.2 PM 验收六项

每个样本都要看：

1. 首屏任务是否正确
2. 内容单元是否正确
3. 布局节奏是否正确
4. 信号排序是否正确
5. 主交互闭环是否正确
6. UI 气质是否正确

评分：

- `通过`
- `基本通过`
- `不通过`

规则：

- 任一核心项 `不通过`，不得算交付完成

### 6.3 推导结果也必须可验

推导器必须输出日志或可读结果，例如：

- `primaryGoal=read`
- `contentUnit=article`
- `layoutRhythm=list`
- `signalPriority=author_trust,save_rate`
- `reason=article-heavy + text-heavy + technical intent`

PM 必须先确认“推导是否合理”，再看页面效果。

## 7. 回归矩阵

建议最小回归样本：

社区类：

- 类掘金技术社区
- 类小红书生活方式社区
- 类知乎问答社区
- 类贴吧兴趣论坛
- 类微博热点广场
- 类 36Kr 商业资讯站

非社区类：

- SaaS 数据后台
- 电商商品列表页
- 在线教育课程平台
- 企业官网

每个样本至少检查：

- 形态推导结果
- 原型结果
- fallback 结果
- PM 六项验收结果

## 8. 上线策略

建议采用灰度切换：

1. 加特性开关
2. 先保留旧逻辑
3. 新推导器用于测试与灰度
4. 回归全部通过后再切默认

## 9. 交付清单

本次工作最终必须交付：

1. 代码实现
2. 结构字段说明
3. 推导规则说明
4. PM 验收标准
5. 回归样本清单
6. 改前改后差异总结

## 10. 完成标准

满足以下条件才算完成：

- 社区类需求不再同质化
- 非社区需求不再被社区规则污染
- prompt 与 fallback 不再含品牌 benchmark 硬编码
- 形态推导结果可解释
- PM 验收通过
- 回归矩阵通过
