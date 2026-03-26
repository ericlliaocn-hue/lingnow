# 🎬 LingNow 用户故事场景集

> **数字军团如何工作** - 5 个真实的用户案例演绎  
> 每个场景展示 **Product Architect → UI Designer → Frontend/Backend Dev → DevOps** 的完整协作流程

---

## 📋 场景说明

这些场景用于：
1. **团队培训** - 让新成员快速理解"数字军团"模式
2. **产品演示** - 向投资人/用户展示 LingNow 的强大能力
3. **测试用例** - 端到端验证各 Agent 的协作是否流畅
4. **Prompt 优化** - 基于真实场景调整 System Prompt

---

## 场景一：🐾 宠物社交 APP (电商类)

### 用户需求
> "我想做个宠物社交 APP，用户可以晒自家宠物，点赞评论"

### 👨‍💼 Product Architect Agent 分析

#### 步骤 1: 需求拆解
```java
// 生成的 ProjectManifest.java
ProjectManifest manifest = ProjectManifest.builder()
    .id("pet-social-20260326")
    .userIntent("宠物社交 APP，晒宠物、点赞评论")
    .status(ProjectStatus.PLANNING)
    .features(List.of(
        Feature.builder()
            .name("用户注册/登录")
            .description("支持手机号和微信快捷登录")
            .priority(Priority.HIGH)
            .estimatedHours(4)
            .build(),
        Feature.builder()
            .name("发布图文动态")
            .description("上传图片 + 文字描述，支持基础滤镜")
            .priority(Priority.HIGH)
            .estimatedHours(8)
            .build(),
        Feature.builder()
            .name("信息流展示")
            .description("类似 Instagram 的瀑布流，按时间排序")
            .priority(Priority.MEDIUM)
            .estimatedHours(6)
            .build(),
        Feature.builder()
            .name("点赞评论系统")
            .description("双击点赞，支持回复评论，实时通知")
            .priority(Priority.MEDIUM)
            .estimatedHours(6)
            .build()
    ))
    .techStack(TechStack.builder()
        .frontend("Vue 3 + Vite")
        .backend("Spring Boot 3")
        .database("MySQL 8")
        .ui("TailwindCSS")
        .build()
    )
    .pages(List.of(
        PageSpec.builder().route("/home").description("首页 - 信息流").build(),
        PageSpec.builder().route("/post/new").description("发布页").build(),
        PageSpec.builder().route("/post/:id").description("详情页").build(),
        PageSpec.builder().route("/profile").description("个人中心").build(),
        PageSpec.builder().route("/login").description("登录/注册").build()
    ))
    .build();
```

#### 步骤 2: 人机交互确认

**前端展示**：
```vue
<div class="prd-confirmation">
  <h3>📋 需求确认单</h3>
  
  <div class="features-section">
    <h4>✅ 核心功能（4 个）</h4>
    <ul>
      <li>🔐 用户注册/登录 - 支持手机号和微信快捷登录</li>
      <li>📸 发布图文动态 - 上传图片 + 文字描述，支持基础滤镜</li>
      <li>📱 信息流展示 - 类似 Instagram 的瀑布流，按时间排序</li>
      <li>❤️ 点赞评论系统 - 双击点赞，支持回复评论，实时通知</li>
    </ul>
  </div>
  
  <div class="tech-stack-section">
    <h4>🛠️ 推荐技术栈</h4>
    <p>前端：Vue 3 + Vite | 后端：Spring Boot 3 | 数据库：MySQL 8</p>
  </div>
  
  <div class="pages-section">
    <h4>📄 页面列表（5 个）</h4>
    <p>首页、发布页、详情页、个人中心、登录/注册</p>
  </div>
  
  <div class="actions">
    <button @click="confirmPRD">✅ 确认，开始设计</button>
    <button @click="modifyRequirement">✏️ 修改需求</button>
  </div>
</div>
```

**用户反馈**：
> "先不做视频功能，专注图片就好"

**Product Architect 响应**：
```java
// 移除所有与视频相关的功能
manifest.getFeatures().removeIf(f -> 
    f.getDescription().contains("视频")
);
manifest.setStatus(ProjectStatus.DESIGNING);
```

---

### 🎨 UI Designer Agent 设计

#### 步骤 1: 生成 Design System
```java
DesignSystem design = DesignSystem.builder()
    .colorScheme("温暖橙色系")
    .primaryColor("#FF6B6B")    // 珊瑚红 - 活泼可爱
    .secondaryColor("#FFA07A")  // 浅鲑鱼色 - 温暖
    .accentColor("#FFD93D")     // 明黄 - 活力
    .backgroundColor("#FFF5F5") // 淡粉白 - 温馨
    .layout("Header + Main Feed + Bottom Tab Bar")
    .components(List.of(
        ComponentSpec.builder()
            .name("PostCard")
            .style("圆角卡片，白色背景，阴影柔和")
            .interactions("双击点赞动画，长按弹出菜单")
            .build(),
        ComponentSpec.builder()
            .name("BottomTabBar")
            .style("固定在底部，半透明毛玻璃效果")
            .icons("🏠 首页  📸 发布  👤 我的")
            .build()
    ))
    .build();
```

#### 步骤 2: 生成原型代码
```html
<!-- prototype-pet-social.html -->
<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <title>宠物社交 - 首页</title>
  <script src="https://cdn.tailwindcss.com"></script>
  <style>
    .like-animation {
      animation: heartBeat 0.3s ease-in-out;
    }
    @keyframes heartBeat {
      0% { transform: scale(1); }
      50% { transform: scale(1.3); }
      100% { transform: scale(1); }
    }
  </style>
</head>
<body class="bg-[#FFF5F5] min-h-screen">
  <!-- Header -->
  <header class="bg-white shadow-sm sticky top-0 z-50">
    <div class="max-w-md mx-auto px-4 py-3 flex justify-between items-center">
      <h1 class="text-xl font-bold text-[#FF6B6B]">🐾 PetSocial</h1>
      <button class="text-gray-600">🔔</button>
    </div>
  </header>

  <!-- Main Feed -->
  <main class="max-w-md mx-auto pb-16">
    <!-- Post Card 1 -->
    <div class="bg-white m-4 rounded-xl shadow-md overflow-hidden">
      <div class="p-3 flex items-center">
        <img src="avatar1.jpg" class="w-10 h-10 rounded-full">
        <span class="ml-3 font-semibold">咪咪的日常</span>
      </div>
      <img src="cat-photo.jpg" class="w-full h-64 object-cover">
      <div class="p-3">
        <div class="flex space-x-4 mb-2">
          <button class="like-btn text-2xl">❤️</button>
          <button class="text-2xl">💬</button>
          <button class="text-2xl">↗️</button>
        </div>
        <p class="font-semibold">23 次赞 · 5 条评论</p>
        <p class="text-gray-700 mt-1">今天去公园玩啦~ 咪咪超级开心！🎉</p>
      </div>
    </div>

    <!-- Post Card 2 -->
    <!-- ... 更多帖子 ... -->
  </main>

  <!-- Bottom Tab Bar -->
  <nav class="fixed bottom-0 left-0 right-0 bg-white/90 backdrop-blur-lg border-t">
    <div class="max-w-md mx-auto flex justify-around py-3">
      <button class="text-[#FF6B6B]">🏠<br><span class="text-xs">首页</span></button>
      <button class="text-gray-400">📸<br><span class="text-xs">发布</span></button>
      <button class="text-gray-400">💬<br><span class="text-xs">消息</span></button>
      <button class="text-gray-400">👤<br><span class="text-xs">我的</span></button>
    </div>
  </nav>
</body>
</html>
```

#### 步骤 3: 原型预览与人机交互

**前端展示**：
```vue
<div class="prototype-preview">
  <h3>🎨 原型预览</h3>
  
  <iframe 
    :src="prototypeHtml" 
    class="w-full h-[600px] border rounded-xl"
  ></iframe>
  
  <div class="feedback-section mt-4">
    <h4>满意吗？</h4>
    <textarea 
      v-model="feedback"
      placeholder="例如：把点赞按钮改成红色心形图标..."
      class="w-full p-3 border rounded-lg"
    ></textarea>
    
    <div class="actions mt-3">
      <button @click="confirmPrototype">✅ 满意，开始写代码</button>
      <button @click="submitFeedback">✏️ 提交修改意见</button>
    </div>
  </div>
</div>
```

**用户反馈**：
> "把点赞按钮改成红色心形图标，现在的太普通了"

**UI Designer Agent 响应**：
```java
// 精准定位并修改 CSS
String updatedHtml = prototypeHtml.replaceAll(
    "\\.like-btn \\{[^}]*\\}",
    ".like-btn {\n" +
    "  color: #FF6B6B;\n" +
    "  font-size: 28px;\n" +
    "  transition: all 0.2s;\n" +
    "}\n" +
    ".like-btn:hover {\n" +
    "  transform: scale(1.2);\n" +
    "}"
);

// 热更新预览窗口（无需刷新）
websocket.send("UPDATE_PROTOTYPE", updatedHtml);
```

---

### 💻 Frontend Dev Agent 编码

#### 输入
- `ProjectManifest.prototypeHtml` (已确认的原型)
- `ProjectManifest.apiSchema` (从 Backend Dev 获取)

#### 输出
```vue
<!-- /src/components/PostCard.vue -->
<template>
  <div class="bg-white rounded-xl shadow-md overflow-hidden">
    <div class="p-3 flex items-center">
      <img :src="post.authorAvatar" class="w-10 h-10 rounded-full">
      <span class="ml-3 font-semibold">{{ post.authorName }}</span>
    </div>
    
    <img :src="post.imageUrl" class="w-full h-64 object-cover" @dblclick="handleLike">
    
    <div class="p-3">
      <div class="flex space-x-4 mb-2">
        <button 
          @click="handleLike" 
          class="text-2xl like-btn"
          :class="{ 'liked': isLiked }"
        >
          {{ isLiked ? '❤️' : '🤍' }}
        </button>
        <button class="text-2xl">💬</button>
        <button class="text-2xl">↗️</button>
      </div>
      
      <p class="font-semibold">{{ likeCount }} 次赞 · {{ commentCount }} 条评论</p>
      <p class="text-gray-700 mt-1">{{ post.content }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useApi } from '@/composables/useApi'

const props = defineProps({
  post: Object
})

const { api } = useApi()
const isLiked = ref(false)
const likeCount = ref(props.post.likeCount)

const handleLike = async () => {
  if (isLiked.value) return
  
  isLiked.value = true
  likeCount.value++
  
  await api.post(`/posts/${props.post.id}/like`)
}
</script>

<style scoped>
.like-btn {
  transition: all 0.2s;
}
.like-btn:hover {
  transform: scale(1.2);
}
</style>
```

---

### ⚙️ Backend Dev Agent 编码

#### 输出 1: Database Schema
```sql
-- /src/main/resources/db/schema.sql
CREATE TABLE users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) UNIQUE NOT NULL,
  phone VARCHAR(20) UNIQUE,
  wechat_open_id VARCHAR(100),
  avatar_url VARCHAR(255),
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
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE likes (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  post_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY unique_like (post_id, user_id),
  FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
```

#### 输出 2: Spring Boot Controller
```java
// /src/main/java/cc/petsocial/controller/PostController.java
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {
    
    private final PostService postService;
    
    @GetMapping
    public ResponseEntity<List<Post>> getFeed(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(postService.getFeed(page, size));
    }
    
    @PostMapping("/{id}/like")
    public ResponseEntity<Void> likePost(@PathVariable Long id) {
        postService.likePost(id, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok().build();
    }
}
```

---

## 场景二：📝 个人博客系统 (内容类)

### 用户需求
> "我想建个个人博客，写文章、分类、标签，别人可以评论"

### Product Architect 输出
```json
{
  "features": [
    {
      "name": "Markdown 编辑器",
      "description": "支持实时预览、代码高亮、图片拖拽上传",
      "priority": "HIGH"
    },
    {
      "name": "文章管理",
      "description": "草稿箱、定时发布、版本历史",
      "priority": "HIGH"
    },
    {
      "name": "分类与标签",
      "description": "多级分类、标签云、热门文章",
      "priority": "MEDIUM"
    },
    {
      "name": "评论系统",
      "description": "支持 Markdown、@回复、邮件通知",
      "priority": "MEDIUM"
    }
  ],
  "techStack": {
    "frontend": "Nuxt 3 (SEO 友好)",
    "backend": "Spring Boot 3",
    "database": "PostgreSQL"
  },
  "pages": [
    {"route": "/", "description": "首页 - 文章列表"},
    {"route": "/post/:slug", "description": "文章详情"},
    {"route": "/category/:name", "description": "分类归档"},
    {"route": "/admin/write", "description": "写作后台"}
  ]
}
```

### UI Designer 特色设计
- **配色方案**: "极简黑白灰 + 蓝色点缀"
- **布局**: "左侧导航 + 右侧内容 + 固定目录"
- **特色组件**: 
  - 文章进度条（顶部）
  - 目录导航（右侧悬浮）
  - 代码块一键复制

---

## 场景三：🛒 电商小程序商城 (商业类)

### 用户需求
> "我要开个卖手工饰品的小程序，有商品详情、购物车、下单支付"

### Product Architect 输出
```json
{
  "features": [
    {
      "name": "商品展示",
      "description": "多图轮播、规格选择（颜色/尺寸）、库存显示",
      "priority": "HIGH"
    },
    {
      "name": "购物车",
      "description": "加减数量、选中/取消、批量结算",
      "priority": "HIGH"
    },
    {
      "name": "订单系统",
      "description": "下单、支付（微信/支付宝）、订单状态跟踪",
      "priority": "HIGH"
    },
    {
      "name": "用户中心",
      "description": "订单管理、收货地址、收藏夹",
      "priority": "MEDIUM"
    }
  ],
  "techStack": {
    "frontend": "Uni-app (跨平台)",
    "backend": "Spring Boot 3 + MyBatis Plus",
    "database": "MySQL 8",
    "payment": "微信支付 + 支付宝 SDK"
  },
  "pages": [
    {"route": "/pages/index/index", "description": "首页 - 轮播 + 分类 + 推荐"},
    {"route": "/pages/goods/detail", "description": "商品详情"},
    {"route": "/pages/cart/cart", "description": "购物车"},
    {"route": "/pages/order/confirm", "description": "确认订单"},
    {"route": "/pages/user/user", "description": "个人中心"}
  ]
}
```

### 特殊处理
- **必须集成真实支付 SDK**（不能 Mock）
- **库存并发控制**（分布式锁）
- **订单超时自动取消**（延迟队列）

---

## 场景四：📊 SaaS 数据看板 (企业级)

### 用户需求
> "我们公司是做教育培训的，想要个看板看每天的招生数据、学员出勤、老师绩效"

### Product Architect 输出
```json
{
  "features": [
    {
      "name": "数据概览 Dashboard",
      "description": "关键指标卡片（今日报名、本月营收、出勤率）",
      "priority": "HIGH"
    },
    {
      "name": "招生管理",
      "description": "线索录入、跟进记录、转化漏斗",
      "priority": "HIGH"
    },
    {
      "name": "学员管理",
      "description": "学员档案、课程表、出勤打卡",
      "priority": "HIGH"
    },
    {
      "name": "老师绩效",
      "description": "课时统计、学员评分、工资计算",
      "priority": "MEDIUM"
    }
  ],
  "techStack": {
    "frontend": "Vue 3 + Element Plus + ECharts",
    "backend": "Spring Boot 3 + Spring Security",
    "database": "MySQL 8 + Redis (缓存)",
    "deploy": "Docker + Nginx"
  },
  "pages": [
    {"route": "/dashboard", "description": "数据概览"},
    {"route": "/leads", "description": "招生线索"},
    {"route": "/students", "description": "学员管理"},
    {"route": "/teachers/performance", "description": "老师绩效"}
  ]
}
```

### UI Designer 特色设计
- **深色主题**（适合长时间观看）
- **可拖拽布局**（用户自定义 Dashboard）
- **导出 Excel**（老板需求）

---

## 场景五：🍳 菜谱分享社区 (UGC 类)

### 用户需求
> "做个菜谱 APP，用户上传自己的菜谱，其他人可以收藏、跟着做"

### Product Architect 输出
```json
{
  "features": [
    {
      "name": "菜谱发布",
      "description": "分步骤图文、食材清单、烹饪时长、难度等级",
      "priority": "HIGH"
    },
    {
      "name": "智能搜索",
      "description": "按食材/菜系/口味筛选，模糊搜索",
      "priority": "HIGH"
    },
    {
      "name": "互动功能",
      "description": "收藏、跟做上传、评分、评论",
      "priority": "MEDIUM"
    },
    {
      "name": "个性化推荐",
      "description": "根据浏览历史推荐菜谱",
      "priority": "LOW"
    }
  ],
  "techStack": {
    "frontend": "Vue 3 + Vite",
    "backend": "Spring Boot 3 + Elasticsearch (搜索)",
    "storage": "阿里云 OSS (图片存储)"
  }
}
```

---

## 🎯 场景使用指南

### 1. 团队培训
```markdown
新成员入职第一天：
1. 阅读 plan.md（理解架构）
2. 阅读 scenes.md（理解业务）
3. 选择一个场景，手动模拟一遍 Agent 流程
4. 编写该场景的端到端测试用例
```

### 2. 产品演示
```markdown
给投资人演示：
1. 打开 lingnow.cc 官网
2. 选择"宠物社交 APP"场景
3. 现场输入需求 → 生成 PRD → 生成原型 → 生成代码
4. 展示真实可用的应用
```

### 3. 测试验证
```bash
# 运行场景一的端到端测试
mvn test -Dtest=PetSocialScenarioTest

# 预期输出：
✅ Product Architect 生成了 4 个功能点
✅ UI Designer 生成了可点击原型
✅ Frontend Dev 生成了 15 个 Vue 组件
✅ Backend Dev 生成了 8 个 API 接口
✅ QA Agent 通过了所有检查
```

---

## 📊 场景复杂度对比

| 场景 | 功能点数 | 预计代码量 | 开发周期 (传统) | LingNow 耗时 |
|------|----------|------------|-----------------|--------------|
| 宠物社交 APP | 4 | 2000 行 | 2 周 | 10 分钟 |
| 个人博客 | 4 | 1500 行 | 1 周 | 8 分钟 |
| 电商小程序 | 4+ | 3000 行 | 1 个月 | 15 分钟 |
| SaaS 看板 | 4 | 2500 行 | 3 周 | 12 分钟 |
| 菜谱社区 | 4 | 2000 行 | 2 周 | 10 分钟 |

---

**📅 创建日期**: 2026-03-26  
**👥 用途**: LingNow.cc 数字军团场景库  
**🔄 更新**: 持续添加新场景
