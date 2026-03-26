# 🚀 LingNow.cc - 自主智能体产品工厂 (Autonomous Product Factory)

**版本**: v2.0 - Multi-Agent Edition  
**状态**: 🟢 准备启动 (Ready to Start)  
**核心使命**: **Zero-to-One in Seconds** - 从自然语言到可运行产品的秒级转化  
**愿景**: 不是代码生成器，而是 **AI 软件公司** (虚拟数字军团)  
**技术栈**: **Java 21 + Spring Boot 3 + Vue 3 + Vite**

---

## 🎯 核心理念升级

### ❌ 传统思路（单兵作战）
```
用户输入 → 一个大模型 Prompt → 吐出所有代码
```
**问题**：
- ❌ 超出 LLM 上下文限制
- ❌ 无法保证代码质量
- ❌ 难以精准修改
- ❌ 信息逐层衰减

### ✅ LingNow 模式（数字军团）
```
用户输入 
  ↓
Product Architect Agent → 拆解需求 → ProjectManifest (结构化 PRD)
  ↓ (写入共享上下文)
UI Designer Agent → 生成原型 → 可点击预览
  ↓ (用户确认"满意")
Frontend + Backend Agents → 并行编码 → QA 自动检查
  ↓
DevOps Agent → 一键部署 → 交付可用产品
```
**优势**：
- ✅ 职责分离，专业的人做专业的事
- ✅ 每个环节都有确认点（人机交互 + QA 检查）
- ✅ 增量迭代，修改成本低
- ✅ 技能可复用，越用越聪明

---

## 1️⃣ 项目背景与愿景 (Background & Vision)

### 1.1 痛点分析

#### 传统软件开发的困境
| 痛点 | 描述 | 影响 |
|------|------|------|
| **想法到原型周期长** | 绝妙想法因数天开发周期而夭折 | 90% 创意死在起点 |
| **多角色协作成本高** | PM、UI、Dev 沟通损耗巨大 | 信息丢失 30%+ |
| **外包价格高质量差** | 报价 5 万+，周期 2 个月 | 创业者望而却步 |
| **低代码平台局限** | 做出来的东西丑、功能受限 | 无法见投资人 |

#### 现有 AI 工具的不足
| 工具类型 | 代表产品 | 局限性 |
|----------|----------|--------|
| **代码补全** | GitHub Copilot | 只能写片段，无法生成完整应用 |
| **Text-to-Code** | Cursor、Windsurf | 缺少"需求→架构→部署"全链路 |
| **原型工具** | Figma AI | 只能看不能用，无法转化为代码 |

### 1.2 核心愿景

构建 **LingNow**，一个基于 **多智能体协作 (Multi-Agent Collaboration)** 的虚拟软件公司：

| 步骤 | 说明 | 对应现实角色 |
|------|------|--------------|
| 👤 **用户输入** | 一句自然语言描述 | 老板提需求 |
| 🤖 **Product Architect** | 拆解需求，生成结构化 PRD | 产品经理 |
| 🎨 **UI Designer** | 生成高保真可点击原型 | UI 设计师 |
| 💻 **Frontend Dev** | 实现前端交互逻辑 | 前端工程师 |
| ⚙️ **Backend Dev** | 设计数据库和 API | 后端工程师 |
| 🛡️ **QA Engineer** | 自动测试和质量检查 | 测试工程师 |
| 🚀 **DevOps** | 一键部署到云端 | 运维工程师 |
| 🎯 **最终交付** | 秒级生成可运行、可预览、可交互的完整 Web 应用 | 交付产品 |

### 1.3 核心价值

| 价值 | 说明 | 用户感知 |
|------|------|----------|
| ⚡ **Zero-to-One in Seconds** | 原型构建时间从 2 个月压缩到 2 小时 | "太快了！" |
| 🖥️ **Live Preview Sandbox** | 内置浏览器沙箱，代码生成即运行 | "所见即所得" |
| 🔄 **Iterative Refinement** | 多轮对话修改，保持上下文记忆 | "改得真准" |
| 🏭 **Full-Stack Delivery** | 从需求到部署，一站式交付 | "啥都不用管" |
| 💰 **100x Cost Reduction** | 成本从 5 万降到 500 元 | "太便宜了" |

---

## 2️⃣ 技术架构方案 (Technical Architecture)

### 2.1 总体架构：数字军团流水线

采用 **前后端分离 + 多 Agent 协作 + 本地沙箱渲染** 架构。

**技术栈选择**：
- **Backend**: Java 21 + Spring Boot 3 (稳定、企业级、易维护)
- **Frontend**: Vue 3 + Vite (轻量、快速、中文友好)
- **AI**: DashScope SDK (阿里云通义千问 Qwen-Turbo)
- **Preview**: Sandpack Vue (浏览器内运行时代码沙箱)

```
┌─────────────────────────────────────────────────────────┐
│                    用户界面层                              │
│              Frontend (端口 2002)                         │
│   React/Vue + Vite + TailwindCSS + Sandpack 预览引擎     │
└────────────────────┬────────────────────────────────────┘
                     │ REST API / WebSocket
┌────────────────────▼────────────────────────────────────┐
│                 Agent 编排层 (Orchestrator)               │
│              Backend (端口 2001)                          │
│   Spring Boot / Node.js + MCP 协议 + 会话状态管理        │
└─────┬──────────┬──────────┬──────────┬──────────┬───────┘
      │          │          │          │          │
┌─────▼──┐  ┌───▼────┐  ┌──▼─────┐  ┌─▼──────┐  ┌▼──────┐
│Product │  │ UI     │  │Frontend│  │Backend │  │DevOps │
│Architec│  │Designer│  │Dev     │  │Dev     │  │Engineer│
│t Agent │  │Agent   │  │Agent   │  │Agent   │  │Agent  │
└────────┘  └────────┘  └────────┘  └────────┘  └───────┘
      │          │          │          │          │
      └──────────┴────┬─────┴──────────┴──────────┘
                      │
            ┌─────────▼──────────┐
            │  Project Manifest  │
            │  (唯一事实来源)     │
            └─────────┬──────────┘
                      │
            ┌─────────▼──────────┐
            │   LLM API (Qwen)   │
            │   DashScope SDK    │
            └────────────────────┘
```

### 2.2 核心模块设计

#### A. 智能编排层 (The Orchestrator)

后端 (`backend/`) 维护 **System Chain**，通过 MCP (Model Context Protocol) 协调各 Agent。

**关键机制**：
1. **Project Manifest** - 唯一事实来源 (Single Source of Truth)
2. **阶段确认点** - 人机交互暂停，等待用户确认
3. **并行执行** - Frontend + Backend Agents 同时工作
4. **QA 检查** - 自动生成单元测试并验证

#### B. Project Manifest（核心数据结构）

这是整个系统的**心脏**，所有 Agent 都读写这个共享上下文。

```typescript
// lingnow-core/types.ts
interface ProjectManifest {
  id: string;                        // 项目唯一标识
  userIntent: string;                // 用户原始需求
  status: 'PLANNING' | 'DESIGNING' | 'CODING' | 'DEPLOYING';
  
  // === Product Architect 填充 ===
  features: Feature[];               // 功能列表
  techStack: {                       // 技术选型
    frontend: string;                // "Vue 3"
    backend: string;                 // "Spring Boot"
    database: string;                // "MySQL"
    ui: string;                      // "TailwindCSS"
  };
  
  // === UI Designer 填充 ===
  designSystem: {
    colorScheme: string;             // "温暖橙色系"
    layout: string;                  // "Header + Sidebar + Content"
    components: ComponentSpec[];     // 组件规格
  };
  prototypeHtml: string;             // 高保真原型代码
  
  // === Frontend Dev 填充 ===
  pages: PageSpec[];                 // 页面结构
  generatedFiles: Record<string, string>; // 生成的文件 {路径：内容}
  
  // === Backend Dev 填充 ===
  apiSchema: OpenAPISpec;            // API 定义
  databaseSchema: string;            // SQL Schema
  
  // === DevOps 填充 ===
  deploymentUrl?: string;            // 部署后的链接
  dockerConfig?: Dockerfile;         // Docker 配置
  
  // === 会话历史 ===
  chatHistory: Message[];            // 与用户的对话记录
  modificationRequests: Change[];    // 修改请求记录
}

interface Feature {
  name: string;
  description: string;
  priority: 'HIGH' | 'MEDIUM' | 'LOW';
  estimatedHours: number;
}

interface PageSpec {
  route: string;                     // "/home"
  description: string;               // "首页 - 信息流展示"
  components: string[];              // ["PostList", "SearchBar"]
  mockData?: any;                    // Mock 数据
}
```

**为什么重要？**
- ✅ 避免"前端以为做 A，后端以为做 B"的混乱
- ✅ 支持增量修改（只改 Manifest 的某一部分）
- ✅ 可追溯（每次修改都有记录）
- ✅ 可复用（积累模板库）

#### C. 五大核心 Agent 详解

##### 1️⃣ **Product Architect Agent**（产品架构师）

**职责**：解析用户模糊需求，拆解为功能列表，定义技术栈

**输入**：
```
"我想做个宠物社交 APP，用户可以晒自家宠物，点赞评论"
```

**输出**（ProjectManifest）：
```json
{
  "features": [
    {
      "name": "用户注册/登录",
      "description": "支持手机号和微信登录",
      "priority": "HIGH"
    },
    {
      "name": "发布图文动态",
      "description": "上传图片 + 文字描述，支持滤镜",
      "priority": "HIGH"
    },
    {
      "name": "信息流展示",
      "description": "类似 Instagram 的瀑布流",
      "priority": "MEDIUM"
    },
    {
      "name": "点赞评论系统",
      "description": "双击点赞，支持回复评论",
      "priority": "MEDIUM"
    }
  ],
  "techStack": {
    "frontend": "Vue 3 + Vite",
    "backend": "Spring Boot 3",
    "database": "MySQL 8",
    "ui": "TailwindCSS"
  },
  "pages": [
    {"route": "/home", "description": "首页 - 信息流"},
    {"route": "/post/new", "description": "发布页"},
    {"route": "/post/:id", "description": "详情页"},
    {"route": "/profile", "description": "个人中心"},
    {"route": "/login", "description": "登录页"}
  ]
}
```

**关键技能**：
- 🔍 搜索竞品分析（调用 Search API）
- 📊 架构决策树（电商→推荐 Next.js，博客→推荐 Nuxt）
- ✅ 生成可执行的 PRD（非聊天式描述）

**人机交互点**：
```
📋 【需求确认单】

我已理解你的需求，以下是拆解方案：

✅ 核心功能（4 个）：
   1. 用户注册/登录
   2. 发布图文动态
   3. 信息流展示
   4. 点赞评论系统

🛠️ 推荐技术栈：
   - 前端：Vue 3 + Vite
   - 后端：Spring Boot 3
   - 数据库：MySQL 8

📄 页面列表（5 个）：
   - 首页、发布页、详情页、个人中心、登录页

[✅ 确认，开始设计]  [✏️ 修改需求]
```

---

##### 2️⃣ **UI Designer Agent**（UI 设计师）

**职责**：根据 PRD 生成页面结构、配色方案、组件库，输出可交互原型

**输入**：`ProjectManifest.features`

**输出**：
- `designSystem.colorScheme`: "温暖橙色系 (#FF6B6B, #FFA07A)"
- `designSystem.layout`: "Header (Logo + Nav) + Main (Feed) + BottomTab"
- `prototypeHtml`: 完整的 HTML + Tailwind 代码（可直接在 iframe 渲染）

**关键技能**：
- 🎨 TailwindCSS 生成专家
- 📐 布局算法（自动计算间距、对齐）
- 🖱️ 交互逻辑（按钮 hover、加载动画）

**人机交互点**：
```
🎨 【原型预览】

我已经生成了高保真原型，请点击体验：

┌─────────────────────────────────┐
│  [预览窗口 - 可点击的 HTML]      │
│                                 │
│  🏠 首页                         │
│  ┌─────────────────────┐        │
│  │ 🐱 咪咪的日常        │        │
│  │ [图片]              │ ❤️ 23  │
│  │ 今天去公园玩啦~     │ 💬 5   │
│  └─────────────────────┘        │
│                                 │
│  [底部导航] 🏠 ➕ 👤           │
└─────────────────────────────────┘

满意吗？
[✅ 满意，开始写代码]  [✏️ 修改设计]
```

**修改示例**：
```
用户："把点赞按钮改成红色心形图标"

UI Designer Agent:
1. 定位到 `.like-button` CSS 类
2. 修改 `color: gray` → `color: #FF6B6B`
3. 替换图标 SVG
4. 热更新预览窗口（无需刷新）
```

---

##### 3️⃣ **Frontend Dev Agent**（前端工程师）

**职责**：将设计稿转化为 Vue/React 代码，实现交互逻辑，对接 Mock/API 数据

**输入**：
- `ProjectManifest.prototypeHtml`（原型）
- `ProjectManifest.apiSchema`（API 定义，从 Backend 获取）

**输出**：
- `generatedFiles["/src/App.vue"]`
- `generatedFiles["/src/components/PostCard.vue"]`
- `generatedFiles["/src/api/client.ts"]`

**关键技能**：
- 💻 Vue 3 / React 代码生成
- 🔗 Fetch/Axios API 调用
- 🧪 Playwright 自测脚本

**协作机制**：
```
1. Backend Dev Agent 先行动
   → 生成 Database Schema
   → 生成 OpenAPI Spec (写入 Manifest)

2. Frontend Dev Agent 监听
   → 读取 Manifest.apiSchema
   → 生成真实的 API Client
   → 替换原型中的 Mock 数据
   → 编写真实的 fetch 请求
```

---

##### 4️⃣ **Backend Dev Agent**（后端工程师）

**职责**：设计数据库 Schema，生成 API 接口代码，编写业务逻辑

**输入**：`ProjectManifest.features`

**输出**：
- `databaseSchema`: SQL CREATE TABLE 语句
- `apiSchema`: OpenAPI 3.0 规范
- `generatedFiles["/controller/PostController.java"]`
- `generatedFiles["/service/UserService.java"]`

**关键技能**：
- 🗄️ SQL 生成（MySQL/PostgreSQL/Supabase）
- 🔌 API Framework（Spring Boot/FastAPI/NestJS）
- 🔐 Auth 逻辑（JWT、OAuth2）

**示例输出**：
```sql
-- Database Schema
CREATE TABLE users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) UNIQUE NOT NULL,
  email VARCHAR(100) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE posts (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  content TEXT,
  image_urls JSON,
  like_count INT DEFAULT 0,
  comment_count INT DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id)
);
```

```yaml
# OpenAPI Spec
paths:
  /api/posts:
    get:
      summary: 获取信息流
      responses:
        200:
          description: 成功
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/Post'
```

---

##### 5️⃣ **DevOps Agent**（运维工程师）

**职责**：配置 Dockerfile，CI/CD 脚本，执行一键部署到云环境

**输入**：`ProjectManifest.generatedFiles`

**输出**：
- `docker-compose.yml`
- `deploy_script.sh`
- `deploymentUrl`: "https://preview-pet-social.lingnow.cc"

**关键技能**：
- 🐳 Docker/K8s 配置
- ☁️ Cloud Provider API（Vercel/AWS/阿里云）
- 🔄 CI/CD 自动化

**部署流程**：
```bash
# 1. 打包代码
zip -r project.zip /generatedFiles

# 2. 调用 Vercel API
vercel deploy --project=pet-social

# 3. 返回预览链接
https://preview-pet-social-abc123.vercel.app
```

---

##### 6️⃣ **QA Agent**（隐形质量监督员）

**职责**：实时检查生成代码的质量，自动回滚并修复

**检查项**：
- ✅ 语法正确性（编译通过）
- ✅ Import 完整性（无缺失依赖）
- ✅ 基础功能测试（Playwright 脚本）
- ✅ 安全性扫描（SQL 注入/XSS）

**自动修复机制**：
```
检测到问题：
❌ PostController.java 第 32 行 - 缺少 @Autowired 注解

触发回滚：
1. 标记该文件为 "Needs Fix"
2. 通知 Backend Dev Agent
3. Backend Dev Agent 修复后重新提交
4. QA Agent 再次检查

直到全部通过 ✅
```

---

## 3️⃣ 详细落地执行方案 (Implementation Plan)

### 🏁 阶段一：原型生成器 (MVP) - 1 个月

**目标**: 用户输入需求 → 生成可交互的高保真 HTML 原型（无后端逻辑）

**参战 Agent**：Product Architect + UI Designer

#### 任务清单

##### 1. 创建 Project Manifest 数据模型
- [ ] `backend/src/main/java/cc/lingnow/model/ProjectManifest.java`
- [ ] `backend/src/main/java/cc/lingnow/model/Feature.java`
- [ ] `backend/src/main/java/cc/lingnow/model/PageSpec.java`

##### 2. 实现 Product Architect Agent
- [ ] `backend/src/main/java/cc/lingnow/agent/ProductArchitectAgent.java`
- [ ] System Prompt：需求拆解规则
- [ ] 集成搜索 API（竞品分析）
- [ ] 输出结构化 JSON（非聊天格式）

##### 3. 实现 UI Designer Agent
- [ ] `backend/src/main/java/cc/lingnow/agent/UIDesignerAgent.java`
- [ ] System Prompt：设计规范生成
- [ ] TailwindCSS 代码生成器
- [ ] 原型热更新机制

##### 4. 前端增加人机交互确认点
- [ ] 需求确认页面（展示功能列表）
- [ ] 原型预览页面（iframe 嵌入 HTML）
- [ ] "确认/修改"双按钮逻辑
- [ ] 修改反馈收集（用户说"改哪里"）

##### 5. 编写端到端测试
- [ ] 测试用例：输入"宠物社交 APP" → 输出完整 PRD
- [ ] 测试用例：用户修改需求 → Manifest 同步更新
- [ ] 测试用例：原型预览 → 点击按钮有反应

#### ✅ Verify（验收标准）
- [ ] 用户输入一句话，30 秒内生成 PRD
- [ ] PRD 包含至少 3 个功能点、技术栈推荐、5 个页面
- [ ] 用户在预览窗口可以点击按钮、切换页面
- [ ] 用户说"改颜色"，原型即时更新

---

### 🧠 阶段二：全栈代码生成 (Beta) - 3 个月

**目标**: 用户确认原型 → 生成完整前后端代码 → 本地/云端运行

**参战 Agent**：Frontend Dev + Backend Dev + QA

#### 任务清单

##### 1. 实现 Backend Dev Agent
- [ ] `backend/src/main/java/cc/lingnow/agent/BackendDevAgent.java`
- [ ] Database Schema 生成器
- [ ] Spring Boot Controller/Service 生成
- [ ] OpenAPI Spec 输出

##### 2. 实现 Frontend Dev Agent
- [ ] `backend/src/main/java/cc/lingnow/agent/FrontendDevAgent.java`
- [ ] Vue 3 组件生成器
- [ ] API Client 自动生成
- [ ] Mock 数据替换为真实 fetch

##### 3. 实现 QA Agent
- [ ] `backend/src/main/java/cc/lingnow/agent/QAAgent.java`
- [ ] Java/Vue 语法检查
- [ ] 编译验证（调用 Maven/Node 编译）
- [ ] Playwright 基础测试脚本

##### 4. 并行编码协调机制
- [ ] CountDownLatch 确保 Backend 先行
- [ ] Manifest 状态机管理（CODING → QA_CHECK → READY）
- [ ] 自动回滚修复循环

##### 5. 集成 Sandpack 预览引擎
- [ ] 安装 `@codesandbox/sandpack-vue`
- [ ] 接收 `generatedFiles` 即时渲染
- [ ] 支持热重载（HMR）

#### ✅ Verify（验收标准）
- [ ] 生成的代码可以 `mvn spring-boot:run` 和 `npm run dev`
- [ ] 真的能注册账号、发布内容、点赞评论
- [ ] QA Agent 拦截至少 90% 的编译错误
- [ ] 从确认原型到可运行代码 < 5 分钟

---

### 🚀 阶段三：一键部署与闭环 (GA) - 6 个月

**目标**: 一键部署到公网，支持真实数据库，支持用户迭代修改

**参战 Agent**：DevOps + 增强版所有 Agent

#### 任务清单

##### 1. 实现 DevOps Agent
- [ ] `backend/src/main/java/cc/lingnow/agent/DevOpsAgent.java`
- [ ] 集成 Vercel API
- [ ] 集成 AWS RDS API（或 Supabase）
- [ ] Docker Compose 生成器

##### 2. 建立 Skill Registry（技能注册中心）
- [ ] `/api/skill-registry/intake` 端点
- [ ] 预置技能包：
  - `nextjs-generator`: Next.js 代码生成
  - `supabase-deployer`: Supabase 部署
  - `tailwind-stylist`: 样式调整专家
- [ ] 支持用户上传自定义技能

##### 3. 用户反馈循环
- [ ] 部署后满意度评分
- [ ] Bug 报告自动收集
- [ ] 修改请求自动路由到对应 Agent
- [ ] A/B 测试框架

##### 4. 私有化技能库
- [ ] 电商模板（商品详情、购物车、订单）
- [ ] 博客模板（文章列表、Markdown 渲染、评论）
- [ ] SaaS 模板（Dashboard、订阅管理）

#### ✅ Verify（验收标准）
- [ ] 用户点击"部署"，3 分钟内获得公网链接
- [ ] 真的能访问 https://pet-social.lingnow.cc
- [ ] 数据库持久化存储（重启不丢数据）
- [ ] 用户说"加个分享功能"，DevOps 自动重新部署

---

## 4️⃣ 快速启动指南 (Quick Start)

### 方式一：当前 Java Spring Boot 技术栈

#### 1. 启动后端 (端口 2001)

```bash
cd backend
export DASHSCOPE_API_KEY=sk-xxxxx
mvn spring-boot:run
```

#### 2. 启动前端 (端口 2002)

```bash
cd frontend
npm install
npm run dev
```

---

### 方式二：按 plan.md 重构为 Node.js 技术栈（推荐）

#### 1. 创建后端 (Node.js + Express)

```bash
cd backend-node
npm init -y
npm install express cors dotenv openai
node server.js
```

#### 2. 创建前端 (React + Vite)

```bash
cd frontend-react
npm create vite@latest . -- --template react
npm install @codesandbox/sandpack-react lucide-react axios tailwindcss
npm run dev
```

---

## 5️⃣ 下一步行动 (Next Step)

### 本周内完成（Phase 1 启动）

1. ✅ **创建 Project Manifest 数据模型**
   - [ ] `ProjectManifest.java`
   - [ ] `Feature.java`
   - [ ] `PageSpec.java`

2. ✅ **拆分 GenerationService 为双 Agent**
   - [ ] `ProductArchitectAgent.java` - 需求解析
   - [ ] `UIDesignerAgent.java` - 原型设计

3. ✅ **前端增加确认步骤**
   - [ ] 需求确认页面
   - [ ] 原型预览页面

### 下周完成

4. ✅ **实现 ProductArchitectAgent 的需求拆解功能**
   - [ ] System Prompt 优化
   - [ ] 集成搜索 API（可选）

5. ✅ **实现 UIDesignerAgent 的原型生成功能**
   - [ ] TailwindCSS 生成器
   - [ ] 热更新机制

6. ✅ **端到端测试**
   - [ ] 输入"宠物社交 APP" → 输出完整 PRD + 可点击原型

---

## 📊 成功指标 (Success Metrics)

| 指标 | Phase 1 (MVP) | Phase 2 (Beta) | Phase 3 (GA) |
|------|---------------|----------------|--------------|
| **原型生成时间** | < 1 分钟 | < 1 分钟 | < 1 分钟 |
| **代码生成时间** | - | < 5 分钟 | < 3 分钟 |
| **部署时间** | - | - | < 3 分钟 |
| **用户满意度** | > 4.0/5.0 | > 4.5/5.0 | > 4.8/5.0 |
| **需求准确率** | > 70% | > 85% | > 95% |
| **代码可运行率** | - | > 90% | > 98% |
| **Bug 自动修复率** | - | > 80% | > 95% |

---

## 🎓 关键技术原则

### 1. **Project Manifest 是唯一事实来源**
- ❌ 禁止：Agent 之间直接聊天传递信息
- ✅ 必须：所有信息写入 Manifest，其他 Agent 读取

### 2. **每个环节都有确认点**
- ❌ 禁止：一镜到底生成所有代码
- ✅ 必须：PRD 确认 → 原型确认 → 代码确认 → 部署确认

### 3. **QA Agent 有一票否决权**
- ❌ 禁止：带病上线（编译失败、测试不通过）
- ✅ 必须：QA 检查通过后才能进入下一环节

### 4. **支持增量修改**
- ❌ 禁止：用户说"改按钮颜色"就重新生成所有代码
- ✅ 必须：精准定位到具体文件、具体 CSS 类

### 5. **Skill Registry 开放扩展**
- ❌ 禁止：硬编码所有生成逻辑
- ✅ 必须：支持插件化技能包（用户可上传）

---

## 🏆 长期愿景

### 1 年后
- ✅ 积累 100+ 预制 Skill（电商、博客、SaaS、论坛等）
- ✅ 日均生成 1000+ 个项目
- ✅ 用户满意度 > 4.8/5.0

### 3 年后
- ✅ 支持 10+ 技术栈（Vue/React/Angular/Svelte）
- ✅ 一键部署到 20+ 云平台
- ✅ 成为"AI 软件公司"的事实标准

### 终极目标
**让软件开发像搭积木一样简单**  
**消灭"不会编程"和"请不起外包"的鸿沟**  
**人人都是产品经理，人人都是开发者**

---

**📅 最后更新**: 2026-03-26  
**👥 团队**: LingNow.cc - The Digital Legion
