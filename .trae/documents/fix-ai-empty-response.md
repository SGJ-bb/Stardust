# 修复计划：AI 返回空消息

## 问题分析

从日志中发现两个问题：

### 问题1：AI 返回空消息（核心问题）

**路径**：`sendChatWithToolLoop` → `parseOpenAIResponse` → `ChatResponse(text="")` → `sendToLLM` 中 `chunks.isEmpty() && rawText.isBlank()` → **静默丢弃，用户看不到任何回复**

**产生空 text 的场景**：
1. **Tool call 无文本**：模型返回 `tool_calls` 但 `content` 为 `null`（OpenAI 标准行为），tool loop 执行完后最终轮次 text 仍可能为空
2. **DeepSeek 推理模型**：返回 `reasoning_content` 但 `content` 为空
3. **emotion 标签提取后文本为空**：AI 回复只有 `[[emotion:HAPPY]]` 没有其他内容

### 问题2：`history=0条`（上下文丢失）

**原因**：之前把 `history` 从 `messages.takeLast(10)` 改为 `contextManager?.getRecentTurnsAsPairs()`，但 `rawTurns` 不持久化，app 重启后为空。

**日志证据**：第一条消息 `history=0条`，第二条 `history=0条`，第三条 `history=2条`（说明 rawTurns 是在内存中累积的，重启后丢失）

---

## 修复步骤

### 步骤1：修复空消息兜底（MainActivity.kt:1188-1191）

在 `chunks.isEmpty()` 且 `rawText.isBlank()` 时，添加兜底回复 + 日志：

```kotlin
if (chunks.isEmpty()) {
    if (rawText.isNotBlank()) {
        addPetMessage(rawText, response.emotion, response.action)
    } else {
        com.aicompanion.util.AppLogger.w(TAG, "sendToLLM: API响应成功但回复内容为空")
        addPetMessage("嗯...我好像走神了，能再说一次吗？", Emotion.NEUTRAL, Action.IDLE)
    }
}
```

### 步骤2：修复 history 丢失（MainActivity.kt:1112）

将 `history` 改为：优先使用 `getRecentTurnsAsPairs()`，如果为空则回退到 `messages.takeLast(10)`：

```kotlin
val ctxHistory = contextManager?.getRecentTurnsAsPairs() ?: emptyList()
val history = if (ctxHistory.isNotEmpty()) ctxHistory
              else messages.takeLast(10).filter { it.text.length < 500 }.map { it.isUser to it.text }
```

### 步骤3：修复 sendChatWithToolLoop 中 tool loop 完成但 text 为空（ApiClient.kt:247）

在 tool loop 最后一轮，如果 text 为空且无 toolCalls，记录日志：

```kotlin
if (toolCalls.isEmpty()) {
    if (response.text.isBlank()) {
        AppLogger.w(TAG, "sendChatWithToolLoop: tool loop完成但最终回复为空")
    }
    return response
}
```

### 步骤4：构建 APK 验证
