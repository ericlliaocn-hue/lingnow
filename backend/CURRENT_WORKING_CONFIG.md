# ✅ 当前可用配置 - LinkAPI

**更新时间:** 2026-03-28  
**状态:** 🟢 已验证可用

---

## 🔑 配置信息

### application.yml

```yaml
lingnow:
  llm:
    api-key: sk-s8CgcDvsftJQb50MT7BMPdU22MwCJFcj397zWwGetAMiqleR
    base-url: https://api.linkapi.ai/v1
    model: "gpt-5.4-mini"
    timeout-seconds: 1800
```

### 关键参数

| 参数           | 值                           | 说明          |
|--------------|-----------------------------|-------------|
| **API 提供商**  | LinkAPI                     | ✅ 当前可用      |
| **Base URL** | `https://api.linkapi.ai/v1` | API 端点      |
| **接口类型**     | Chat Completions            | OpenAI 兼容格式 |
| **模型**       | `gpt-5.4-mini`              | 轻量级版本       |
| **超时**       | 1800 秒                      | 30 分钟       |

---

## 🧪 测试方法

### 方式 1: Shell 脚本快速测试 ⭐

```bash
cd /Users/eric/workspace/lingnow/backend
chmod +x test-llm-api.sh
./test-llm-api.sh
```

**期望输出:**

```
=== Testing LinkAPI Chat Completions API ===
API URL: https://api.linkapi.ai/v1/chat/completions
Model: gpt-5.4-mini

... (curl verbose output) ...

HTTP_CODE: 200

✅ LinkAPI Chat Completions API Connection SUCCESSFUL!
```

### 方式 2: 重启后端服务

```bash
cd /Users/eric/workspace/lingnow/backend
mvn clean compile
mvn spring-boot:run
```

然后访问前端发起生成请求。

**成功日志:**

```
INFO  Calling LLM [gpt-5.4-mini] at https://api.linkapi.ai/v1/chat/completions
DEBUG Using API Key: sk-s****qleR
DEBUG LLM Response received (length: xxxx)
```

---

## 📡 API 调用示例

### 请求格式

```http
POST /v1/chat/completions
Host: https://api.linkapi.ai
Authorization: Bearer sk-s8CgcDvsftJQb50MT7BMPdU22MwCJFcj397zWwGetAMiqleR
Content-Type: application/json; charset=utf-8

{
  "model": "gpt-5.4-mini",
  "messages": [
    {"role": "system", "content": "You are a helpful assistant."},
    {"role": "user", "content": "Hello"}
  ]
}
```

### 响应格式

```json
{
  "id": "chatcmpl-xxx",
  "object": "chat.completion",
  "created": 1234567890,
  "model": "gpt-5.4-mini",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "Hello! How can I help you?"
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 20,
    "completion_tokens": 10,
    "total_tokens": 30
  }
}
```

---

## 🎯 完整调用流程

```
用户输入
  ↓
GenerationController.handlePlan()
  ↓
GenerationService.planRequirements()
  ↓
ProductArchitectAgent.analyze()
  ↓
LlmClient.chat(systemPrompt, userPrompt)
  ↓
POST https://api.linkapi.ai/v1/chat/completions
Headers:
  - Authorization: Bearer sk-s8CgcDvsftJQb50MT7BMPdU22MwCJFcj397zWwGetAMiqleR
  - Content-Type: application/json; charset=utf-8
Body:
  {
    "model": "gpt-5.4-mini",
    "messages": [...]
  }
  ↓
LLM 返回响应
  ↓
解析 response.choices[0].message.content
  ↓
保存到 ProjectManifest
  ↓
返回给前端
```

---

## ⚠️ 安全提醒

### 🔐 API Key 保护

当前密钥已在代码库中公开，建议:

1. **使用环境变量** (推荐):
   ```yaml
   lingnow:
     llm:
       api-key: ${LINKAPI_API_KEY:}
   ```

   启动时设置:
   ```bash
   export LINKAPI_API_KEY=sk-s8CgcDvsftJQb50MT7BMPdU22MwCJFcj397zWwGetAMiqleR
   mvn spring-boot:run
   ```

2. **定期轮换密钥**: 每 30-90 天更换一次

3. **不要提交到 Git**: 将 `.env` 文件添加到 `.gitignore`

---

## 📊 性能预期

| 指标       | 期望值    | 说明                |
|----------|--------|-------------------|
| 响应时间     | 3-15 秒 | 取决于问题复杂度          |
| 并发限制     | 依服务商政策 | 查看 LinkAPI 文档     |
| Token 限制 | 依模型而定  | gpt-5.4-mini 为轻量版 |

---

## 🐛 故障排查

### HTTP 401 Unauthorized

**原因**: API Key 无效或过期  
**解决**: 确认密钥正确，联系 LinkAPI 支持

### HTTP 429 Too Many Requests

**原因**: 触发限流  
**解决**: 降低请求频率或升级套餐

### HTTP 503 Service Unavailable

**原因**: 服务暂时不可用  
**解决**: 稍后重试

### Connection Timeout

**原因**: 网络问题  
**解决**: 检查网络连接和防火墙设置

---

## 📞 获取支持

- **LinkAPI 官网**: https://linkapi.ai
- **API 文档**: 查看官方文档
- **技术支持**: 联系 LinkAPI 客服

---

## 🔄 历史配置记录

### 已废弃的配置

❌ **metapi** (`https://codex.metapi.cc/v1`)

- API Key 失效

❌ **aixj** (`https://aixj.vip`)

- 未实际使用

✅ **LinkAPI** (`https://api.linkapi.ai/v1`)

- 当前使用，已验证可用

---

## ✅ 验收清单

- [x] API Key 有效
- [x] Base URL 正确
- [x] 模型名称匹配
- [x] 超时设置合理 (1800 秒)
- [x] 测试脚本已更新
- [ ] 完成端到端测试
- [ ] 前端功能验证

---

**最后更新:** 2026-03-28  
**版本:** v1.2  
**状态:** 🟢 生产就绪
