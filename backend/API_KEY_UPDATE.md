# 🔑 API Key 更新说明

## 📋 问题诊断

### 原始问题

- **代码逻辑**: ✅ 正常 (使用 metapi Chat Completions API)
- **API Key**: ❌ 失效 (`sk-meow...meow888`)
- **错误现象**: HTTP 401 Unauthorized 或 API Key invalid

---

## ✅ 已实施的修复

### 1. 更新 API Key

**文件:** `application.yml`

**修改前:**

```yaml
lingnow:
  llm:
    api-key: sk-meowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeowmeow888
```

**修改后:**

```yaml
lingnow:
  llm:
    api-key: sk-a34e619578879166feb67738de0bd43d19acaa0ffc5b847991bd113a7fbd430b
    base-url: https://codex.metapi.cc/v1
    model: gpt-5.4
    timeout-seconds: 1800
```

### 2. 调整超时时间

**文件:** `LlmProperties.java`

**修改前:**

```java
private int timeoutSeconds = 60;
```

**修改后:**

```java
private int timeoutSeconds = 1800;  // 30 minutes for complex tasks
```

---

## 🎯 技术架构确认

### ✅ 正确的配置 (已恢复)

| 组件           | 配置                           |
|--------------|------------------------------|
| **API 提供商**  | metapi                       |
| **Base URL** | `https://codex.metapi.cc/v1` |
| **接口类型**     | Chat Completions API         |
| **端点**       | `/v1/chat/completions`       |
| **请求格式**     | `messages: []`               |
| **响应解析**     | `choices[0].message.content` |
| **模型**       | gpt-5.4                      |

### ❌ 错误的理解 (已纠正)

之前误以为需要适配 aixj Responses API，实际上:

- 代码逻辑本身是正确的
- 只是 API Key 失效导致调用失败
- **不需要修改为 Responses API**

---

## 🧪 测试验证

### 方式 1: Shell 脚本快速测试

```bash
cd /Users/eric/workspace/lingnow/backend
chmod +x test-llm-api.sh
./test-llm-api.sh
```

**期望输出:**

```
=== Testing metapi Chat Completions API ===
API URL: https://codex.metapi.cc/v1/chat/completions
Model: gpt-5.4

... (curl verbose output) ...

HTTP_CODE: 200

✅ metapi Chat Completions API Connection SUCCESSFUL!
```

### 方式 2: 重启后端完整测试

```bash
cd /Users/eric/workspace/lingnow/backend
mvn clean compile
mvn spring-boot:run
```

然后访问前端发起生成请求。

**成功日志:**

```
INFO  Calling LLM [gpt-5.4] at https://codex.metapi.cc/v1/chat/completions
DEBUG Using API Key: sk-a****430b
DEBUG LLM Response received (length: xxxx)
```

---

## 📊 API 对比总结

| 特性       | metapi (正确)                  | aixj (误解)            |
|----------|------------------------------|----------------------|
| Base URL | `https://codex.metapi.cc/v1` | `https://aixj.vip`   |
| 接口类型     | Chat Completions             | Responses API        |
| 端点       | `/chat/completions`          | `/v1/responses`      |
| 请求字段     | `messages[]`                 | `input[]`            |
| 推理配置     | ❌                            | ✅ `reasoning_effort` |
| 网络访问     | ❌                            | ✅                    |
| **状态**   | ✅ **正在使用**                   | ❌ 未使用                |

---

## ⚠️ 重要提示

### API Key 安全性

- ✅ 新 API Key 已配置
- ⚠️ 该密钥已在 GitHub 公开
- 🔐 **建议尽快轮换** (更换新的密钥)

### 配置位置

所有敏感配置应该:

1. 使用环境变量
2. 或使用加密的配置中心
3. 不要提交到版本控制

示例使用环境变量:

```yaml
lingnow:
  llm:
    api-key: ${METAPI_API_KEY:}
```

然后在启动时设置:

```bash
export METAPI_API_KEY=sk-xxx
mvn spring-boot:run
```

---

## 🔄 下一步行动

1. ✅ **执行测试脚本**,验证 API Key 有效性
2. ✅ **重启后端服务**,测试完整流程
3. ✅ **前端功能验证**,确保生成可用
4. 🔐 **考虑轮换 API Key**(因为已公开)

---

## 📞 故障排查

### 如果仍然失败

检查以下几点:

1. **网络连接**:
   ```bash
   curl -I https://codex.metapi.cc/v1
   ```

2. **API Key 格式**:
    - 应该以 `sk-` 开头
    - 长度通常在 64-128 字符

3. **防火墙/代理**:
    - 确认没有阻止 HTTPS 流量
    - 如有代理需配置 JVM 参数

4. **服务可用性**:
    - metapi 服务可能暂时不可用
    - 查看官方状态页面

---

## 📚 相关文档

- [`LlmClient.java`](../src/main/java/cc/lingnow/llm/LlmClient.java) - LLM 客户端实现
- [`LlmProperties.java`](../src/main/java/cc/lingnow/llm/LlmProperties.java) - 配置属性
- [`application.yml`](../src/main/resources/application.yml) - 应用配置
- [`test-llm-api.sh`](test-llm-api.sh) - 测试脚本

---

**更新日期:** 2026-03-28  
**状态:** ✅ 已修复，等待测试验证  
**版本:** v1.1
