# LingNow.cc | 灵现

> **AI Powered Vue Code Generator** - 让灵感，即刻现世  
> 🌐 官方网站：https://lingnow.cc

---

## 📁 项目结构

```
lingnow/
├── backend/                      # Spring Boot 后端 (端口 2001)
│   ├── pom.xml
│   └── src/main/java/cc/lingnow/
│       ├── LingNowApplication.java
│       ├── controller/
│       │   └── GenerationController.java
│       ├── service/
│       │   └── GenerationService.java
│       └── dto/
│           ├── GenerateRequest.java
│           └── GenerateResponse.java
│
└── frontend/                     # Vue 3 前端 (端口 2002)
    ├── package.json
    ├── vite.config.js
    ├── tailwind.config.js
    ├── index.html
    └── src/
        ├── main.js
        ├── style.css
        └── App.vue
```

---

## 🛠️ 环境要求

- **Java**: JDK 21+
- **Node.js**: v18.0+
- **Maven**: 3.6+
- **npm/yarn/pnpm**: 最新版
- **DashScope API Key**（可选）: [获取地址](https://dashscope.console.aliyun.com/)

---

## 🚀 快速开始

### 1️⃣ 启动后端（端口 2001）

```bash
# 进入后端目录
cd backend

# 设置环境变量（配置 DashScope API Key）
export DASHSCOPE_API_KEY=your-dashscope-api-key-here

# 使用 Maven 启动
mvn spring-boot:run

# 或者先编译再运行
mvn clean install
java -jar target/server-0.0.1-SNAPSHOT.jar
```

**✅ 后端将在**: `http://localhost:2001`

> **CORS 配置**：已允许 `http://localhost:2002` 跨域访问

---

### 2️⃣ 启动前端（端口 2002）

打开新终端：

```bash
# 进入前端目录
cd frontend

# 安装依赖（首次运行需要）
npm install

# 启动开发服务器
npm run dev
```

**✅ 前端将在**: `http://localhost:2002`

> **代理配置**：Vite 已配置 `/api` 请求自动转发到 `http://localhost:2001`

---

## 🎯 测试功能

1. 前后端都启动后，访问 **http://localhost:2002**
2. 在输入框中输入描述（例如："创建一个简单的欢迎页面"）
3. 点击 **"生成"** 按钮
4. 查看生成的代码文件和结果
5. 按 `F12` 打开控制台查看详细日志

---

## 📡 API 接口

### POST `/api/generate`

生成 Vue 代码接口

#### 请求体

```json
{
  "prompt": "创建一个简单的欢迎页面",
  "sessionId": "test-session-001",
  "isModification": false
}
```

#### 响应

```json
{
  "title": "Generated App",
  "description": "AI-generated Vue application from LingNow.cc",
  "files": {
    "/App.vue": "...",
    "/main.js": "...",
    "/style.css": "..."
  },
  "dependencies": {
    "vue": "^3.4.0",
    "tailwindcss": "^3.4.0"
  }
}
```

---

## 💻 技术栈

### Backend（后端）

| 技术 | 版本/说明 |
|------|-----------|
| **语言** | Java 21（使用 record 等特性） |
| **框架** | Spring Boot 3.2+ |
| **包路径** | `cc.lingnow` |
| **AI SDK** | DashScope SDK（阿里云通义千问 Qwen-Turbo） |
| **工具库** | Lombok, Spring DevTools |
| **构建工具** | Maven |

### Frontend（前端）

| 技术 | 版本/说明 |
|------|-----------|
| **框架** | Vue 3（Script Setup 语法） |
| **构建工具** | Vite 5 |
| **样式** | TailwindCSS 3 |
| **端口** | 2002 |
| **预览引擎** | Sandpack Vue（待集成） |
| **状态管理** | Pinia |
| **HTTP 客户端** | Axios |
| **图标库** | Lucide Vue Next |

---

## 🔑 核心特性

### GenerationService 关键方法

[`GenerationService`](backend/src/main/java/cc/lingnow/service/GenerationService.java) 包含 AI 生成的核心逻辑：

#### `buildSystemPrompt()`

专为 Qwen Lite/Turbo 模型优化的严格 Prompt：

```java
private String buildSystemPrompt() {
    return """
        你是一个 Vue 3 专家。根据用户输入生成单文件组件。
        【规则】
        1. 只输出纯 JSON，严禁 Markdown (```), 严禁解释文字。
        2. 格式：{"fileName": "App.vue", "code": "<template>...</template><script>...</script>"}
        3. 代码含 template, script setup, style scoped。
        4. 样式用 TailwindCSS。
        5. 逻辑简单，用 Mock 数据。
        【示例】
        {"fileName": "Demo.vue", "code": "<template><div class='p-4'>Hello</div></template><script setup>const msg = 'Hi'</script>"}
        现在请生成：
        """;
}
```

**特点：**
- ✅ 严格 JSON 格式输出
- ✅ 无 Markdown 包装
- ✅ 包含清晰示例
- ✅ 适配免费模型能力

#### 其他核心方法

- [`generateVueCode()`](backend/src/main/java/cc/lingnow/service/GenerationService.java#L55): 调用 DashScope API 生成代码
- [`cleanJsonResponse()`](backend/src/main/java/cc/lingnow/service/GenerationService.java#L98): 清理 Markdown 格式
- [`generateMockCode()`](backend/src/main/java/cc/lingnow/service/GenerationService.java#L113): Mock 数据用于测试

---

## 📋 配置检查清单

| 配置项 | 要求 | 状态 |
|--------|------|------|
| 后端包名 | `cc.lingnow` | ✅ |
| 后端端口 | `2001` | ✅ |
| 前端端口 | `2002` | ✅ |
| CORS 配置 | 允许 `http://localhost:2002` | ✅ |
| 前端代理 | `/api` → `http://localhost:2001` | ✅ |
| GroupId | `cc.lingnow` | ✅ |
| 域名标识 | `LingNow.cc` | ✅ |
| Prompt 约束 | 严格 JSON 格式，适配 Qwen-Turbo | ✅ |

---

## 🗺️ 下一步开发

### Phase 1（当前阶段）✅

- ✅ 前后端分离架构 (`backend/frontend`)
- ✅ CORS 跨域配置 (`2001 ↔ 2002`)
- ✅ Mock 数据返回
- ✅ 完整 UI 界面（深色主题）
- ✅ GenerationService 核心服务
- ✅ 严格的 AI Prompt 约束（适配 Qwen-Turbo）

### Phase 2（后续计划）🔲

- 🔲 **真实 AI 调用**：取消 `GenerationController` 第 42 行的注释
- 🔲 **实时预览**：集成 Sandpack 替换占位区域
- 🔲 **多轮对话**：支持 `isModification` 参数修改代码
- 🔲 **代码导出**：下载 ZIP 功能
- 🔲 **历史记录**：保存生成历史

---

## ⚠️ 重要提示

### 启用真实 AI 调用

当前默认使用 **Mock 数据**（无需 API Key 即可测试）。要启用真实的 AI 生成：

1. **获取 API Key**  
   访问 [DashScope 控制台](https://dashscope.console.aliyun.com/) 获取

2. **设置环境变量**
   ```bash
   export DASHSCOPE_API_KEY=sk-xxxxx
   ```

3. **修改代码**  
   取消 [`GenerationController.java:42`](backend/src/main/java/cc/lingnow/controller/GenerationController.java#L42) 的注释：
   ```java
   // 取消这行注释以启用真实 AI
   String generatedCode = generationService.generateVueCode(request.prompt());
   ```

---

## 📄 License

MIT © 2026 LingNow.cc Team | https://lingnow.cc
