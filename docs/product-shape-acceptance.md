# 产品形态推导器 PM 验收与回归矩阵

这份文档定义产品经理最终如何验收“产品形态推导器”。

原则：

- 不是“代码写完就算交付”
- 必须同时验：
    - 形态推导是否合理
    - 原型结果是否符合推导
    - fallback 是否仍然符合推导
    - 回归样本是否没有互相带坏

## 1. 验收顺序

每个样本都按以下顺序验收：

1. 查看 `DesignContract`
2. 查看 `metaData` 中 shape 字段
3. 查看设计阶段原型结果
4. 如触发 fallback，再查看 fallback 结果
5. 记录通过 / 基本通过 / 不通过

## 2. 形态推导必验字段

以下字段必须可见且可解释：

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

对应 `metaData` 键：

- `shape_primary_goal`
- `shape_content_unit`
- `shape_consumption_mode`
- `shape_media_weight`
- `shape_layout_rhythm`
- `shape_content_density`
- `shape_navigation_mode`
- `shape_main_loop`
- `shape_ui_tone`
- `shape_signal_priority`

## 3. PM 六项验收标准

### 3.1 首屏任务是否正确

用户第一眼应该知道当前页面主要是：

- 来读
- 来发现
- 来讨论
- 来比较
- 来监控
- 来继续学习

### 3.2 内容单元是否正确

页面里的主要内容单元是否和形态一致：

- 文章
- 帖子
- 问答
- 商品
- 新闻
- 卡片
- 仪表盘

### 3.3 布局节奏是否正确

页面节奏是否正确：

- 规整列表
- 紧凑卡片
- 瀑布流
- 楼层/帖子流
- 编辑型资讯布局
- 仪表盘

### 3.4 信号排序是否正确

最醒目的信号是否正确：

- 作者可信度
- 热度
- 最新时间
- 回复量
- 收藏量
- 价格
- 状态
- 学习进度

### 3.5 主交互闭环是否正确

页面是否在推动正确的闭环：

- 阅读并收藏
- 滑动并发现
- 发帖并回复
- 提问并回答
- 比较并购买
- 监控并处理
- 学习并继续

### 3.6 UI 气质是否正确

视觉与交互气质是否符合目标：

- 专业
- 编辑化
- 活泼
- 论坛化
- 广场化
- 企业化

## 4. 评分规则

每一项只允许三档：

- `通过`
- `基本通过`
- `不通过`

通过门槛：

- 任意一项为 `不通过`，该样本不得视为验收完成
- `基本通过` 可进入修正清单，但不得直接作为最终默认策略

## 5. 回归矩阵

### 5.1 社区类样本

#### 样本 A：类掘金技术社区

输入示例：

`做一个类似掘金的技术内容社区，支持技术文章、标签分类、作者主页、点赞收藏评论`

期望：

- `primaryGoal=READ`
- `contentUnit=ARTICLE`
- `layoutRhythm=LIST`
- `mediaWeight=TEXT_HEAVY`
- 首屏应优先显示标题、摘要、作者、标签、互动
- 不应默认落成视觉瀑布流

#### 样本 B：类小红书生活方式社区

输入示例：

`做一个生活方式内容社区，用户可以分享穿搭、探店、旅行灵感，支持收藏和关注`

期望：

- `primaryGoal=DISCOVER`
- `contentUnit=POST`
- `layoutRhythm=WATERFALL`
- `mediaWeight=VISUAL_HEAVY`
- 首屏以视觉卡片和收藏/热度信号为主

#### 样本 C：类知乎问答社区

输入示例：

`做一个问答社区，用户可以提问、回答、关注问题和收藏优质回答`

期望：

- `primaryGoal=READ`
- `contentUnit=QA`
- `consumptionMode=VERIFY_FIRST`
- `layoutRhythm=LIST`
- 首屏应以问题与回答摘要为主

#### 样本 D：类贴吧/论坛

输入示例：

`做一个兴趣论坛，用户可以按版块发帖、回帖、看最新活跃主题`

期望：

- `primaryGoal=DISCUSS`
- `contentUnit=THREAD`
- `layoutRhythm=THREAD`
- `navigationMode=SUBFORUM`
- 首屏应强调帖子标题、回复数、最后活跃时间

#### 样本 E：类微博热点广场

输入示例：

`做一个实时热点社区，用户可以发动态、追热点、评论转发`

期望：

- `consumptionMode=REAL_TIME_FIRST`
- `layoutRhythm=COMPACT_CARD`
- `signalPriority` 中有 `RECENCY` 与 `HEAT`
- 首屏应有趋势感与快速切换节奏

#### 样本 F：类 36Kr 商业资讯

输入示例：

`做一个商业资讯平台，包含头条、栏目、快讯和编辑精选`

期望：

- `contentUnit=NEWS_STORY`
- `layoutRhythm=EDITORIAL`
- `uiTone=EDITORIAL`
- 首屏应更像媒体阅读页而非社区帖子流

### 5.2 非社区类样本

#### 样本 G：SaaS 数据后台

输入示例：

`做一个运营数据后台，展示销售额、转化率、趋势图和异常告警`

期望：

- `primaryGoal=MONITOR`
- `contentUnit=DASHBOARD`
- `layoutRhythm=DASHBOARD`
- `uiTone=ENTERPRISE`

#### 样本 H：电商商品列表页

输入示例：

`做一个电商商品列表页，支持价格筛选、销量排序、商品卡片和下单入口`

期望：

- `primaryGoal=TRANSACT`
- `contentUnit=LISTING`
- `layoutRhythm=COMPACT_CARD`
- `signalPriority` 包含 `PRICE`

#### 样本 I：在线教育平台

输入示例：

`做一个在线学习平台，包含课程列表、学习进度、章节导航和老师信息`

期望：

- `primaryGoal=LEARN`
- `mainLoop=LEARN_CONTINUE`
- `signalPriority` 包含 `PROGRESS`

#### 样本 J：企业官网

输入示例：

`做一个企业官网，展示产品能力、解决方案、客户案例和联系入口`

期望：

- 不应被误判为社区
- 不应出现社区类 feed、评论量、收藏量等默认信号

## 6. 本次版本的完成门槛

只有同时满足以下条件，才算本轮大版本完成：

- 样本 A 与 B 不再同质化
- 样本 A 不再生成视觉发现型首页
- 样本 B 仍然保留视觉发现型特征
- 样本 G、H、I、J 不被社区逻辑污染
- fallback 结果与主形态判断一致
- PM 六项验收全部通过或仅有少量“基本通过”
