// LLM API调用服务，支持 OpenAI / Anthropic / Ollama 三种格式
// 流式响应，工具调用循环最多3轮

use futures::StreamExt;
use reqwest::Client;
use std::time::Duration;
use thiserror::Error;

use crate::models::chat::*;
use crate::models::settings::{ProviderProfile, ProviderType};
use crate::plugins::plugin_trait::{PluginContext, PluginResult};
use tauri::Emitter;

/// LLM服务错误
#[derive(Debug, Error)]
pub enum LlmError {
    #[error("API请求失败: {0}")]
    RequestFailed(String),
    #[error("API响应解析失败: {0}")]
    ParseFailed(String),
    #[error("流式响应中断: {0}")]
    StreamInterrupted(String),
    #[error("工具调用循环超过最大轮次")]
    MaxToolRoundsExceeded,
    #[error("连接测试失败: {0}")]
    ConnectionTestFailed(String),
    #[error("未配置API密钥")]
    NoApiKey,
}

/// LLM服务
pub struct LlmService {
    client: Client,
    /// 最大工具调用轮次
    max_tool_rounds: u32,
}

impl LlmService {
    pub fn new() -> Self {
        let client = Client::builder()
            .timeout(Duration::from_secs(120))
            .build()
            .unwrap_or_else(|_| Client::new());

        LlmService {
            client,
            max_tool_rounds: 3,
        }
    }

    /// 发送聊天请求（非流式）
    pub async fn send_chat(
        &self,
        provider: &ProviderProfile,
        messages: Vec<ApiMessage>,
        tools: Option<Vec<ToolDefinition>>,
    ) -> Result<ChatResponse, LlmError> {
        let api_key = provider.api_key.as_deref().ok_or(LlmError::NoApiKey)?;

        let request = ChatRequest {
            model: provider.model_name.clone(),
            messages,
            tools,
            temperature: Some(provider.temperature),
            max_tokens: Some(provider.max_tokens),
            stream: Some(false),
        };

        let url = format!("{}/chat/completions", provider.base_url.trim_end_matches('/'));

        let response = self.client
            .post(&url)
            .header("Authorization", format!("Bearer {}", api_key))
            .header("Content-Type", "application/json")
            .json(&request)
            .send()
            .await
            .map_err(|e| LlmError::RequestFailed(e.to_string()))?;

        if !response.status().is_success() {
            let status = response.status();
            let body = response.text().await.unwrap_or_default();
            return Err(LlmError::RequestFailed(format!("HTTP {}: {}", status, body)));
        }

        let chat_response: ChatResponse = response
            .json()
            .await
            .map_err(|e| LlmError::ParseFailed(e.to_string()))?;

        Ok(chat_response)
    }

    /// 发送聊天请求（流式），通过事件发射器向前端推送数据
    /// 自动根据 ProviderType 选择正确的 API 格式
    pub async fn send_chat_stream(
        &self,
        provider: &ProviderProfile,
        messages: Vec<ApiMessage>,
        tools: Option<Vec<ToolDefinition>>,
        app_handle: &tauri::AppHandle,
        event_name: &str,
    ) -> Result<ChatResponse, LlmError> {
        match provider.provider_type {
            ProviderType::Anthropic => {
                self.send_chat_stream_anthropic(provider, messages, app_handle, event_name).await
            }
            _ => {
                // OpenAI / Custom / Ollama（base_url 含 ollama 或 local 时走 Ollama 格式）
                let is_ollama = provider.base_url.contains("ollama")
                    || provider.base_url.contains("localhost:11434")
                    || provider.base_url.contains("127.0.0.1:11434");
                if is_ollama {
                    self.send_chat_stream_ollama(provider, messages, app_handle, event_name).await
                } else {
                    self.send_chat_stream_openai(provider, messages, tools, app_handle, event_name).await
                }
            }
        }
    }

    /// OpenAI 兼容格式流式请求（DeepSeek/Moonshot/通义/智谱 等）
    async fn send_chat_stream_openai(
        &self,
        provider: &ProviderProfile,
        messages: Vec<ApiMessage>,
        tools: Option<Vec<ToolDefinition>>,
        app_handle: &tauri::AppHandle,
        event_name: &str,
    ) -> Result<ChatResponse, LlmError> {
        let api_key = provider.api_key.as_deref().ok_or(LlmError::NoApiKey)?;

        let request = ChatRequest {
            model: provider.model_name.clone(),
            messages,
            tools,
            temperature: Some(provider.temperature),
            max_tokens: Some(provider.max_tokens),
            stream: Some(true),
        };

        let url = format!("{}/chat/completions", provider.base_url.trim_end_matches('/'));

        let response = self.client
            .post(&url)
            .header("Authorization", format!("Bearer {}", api_key))
            .header("Content-Type", "application/json")
            .json(&request)
            .send()
            .await
            .map_err(|e| LlmError::RequestFailed(e.to_string()))?;

        if !response.status().is_success() {
            let status = response.status();
            let body = response.text().await.unwrap_or_default();
            return Err(LlmError::RequestFailed(format!("HTTP {}: {}", status, body)));
        }

        // 处理流式响应
        let mut stream = response.bytes_stream();
        let mut full_content = String::new();
        let mut tool_calls: Vec<ApiToolCall> = Vec::new();
        let mut finish_reason: Option<String> = None;
        let mut response_id = String::new();

        while let Some(chunk_result) = stream.next().await {
            let chunk = chunk_result.map_err(|e| LlmError::StreamInterrupted(e.to_string()))?;
            let text = String::from_utf8_lossy(&chunk);

            for line in text.lines() {
                let line = line.trim();
                if !line.starts_with("data: ") {
                    continue;
                }

                let data = &line[6..];
                if data == "[DONE]" {
                    break;
                }

                match serde_json::from_str::<StreamChunk>(data) {
                    Ok(chunk) => {
                        response_id = chunk.id;
                        for choice in chunk.choices {
                            finish_reason = choice.finish_reason;

                            // 处理文本内容
                            if let Some(content) = choice.delta.content {
                                full_content.push_str(&content);
                                let _ = app_handle.emit(event_name, serde_json::json!({
                                    "type": "content",
                                    "content": content,
                                }));
                            }

                            // 处理工具调用
                            if let Some(tc_deltas) = choice.delta.tool_calls {
                                for tc_delta in tc_deltas {
                                    let idx = tc_delta.index as usize;
                                    while tool_calls.len() <= idx {
                                        tool_calls.push(ApiToolCall {
                                            id: String::new(),
                                            r#type: "function".to_string(),
                                            function: ApiFunctionCall {
                                                name: String::new(),
                                                arguments: String::new(),
                                            },
                                        });
                                    }

                                    if let Some(id) = tc_delta.id {
                                        tool_calls[idx].id = id;
                                    }
                                    if let Some(t) = tc_delta.r#type {
                                        tool_calls[idx].r#type = t;
                                    }
                                    if let Some(func) = tc_delta.function {
                                        if let Some(name) = func.name {
                                            tool_calls[idx].function.name = name;
                                        }
                                        if let Some(args) = func.arguments {
                                            tool_calls[idx].function.arguments.push_str(&args);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Err(_) => continue,
                }
            }
        }

        // 发送完成事件
        let _ = app_handle.emit(event_name, serde_json::json!({
            "type": "done",
            "finish_reason": finish_reason,
        }));

        // 构建最终响应
        let message = ChatChoiceMessage {
            role: "assistant".to_string(),
            content: if full_content.is_empty() { None } else { Some(full_content) },
            tool_calls: if tool_calls.is_empty() { None } else { Some(tool_calls) },
            reasoning_content: None,
        };

        Ok(ChatResponse {
            id: response_id,
            choices: vec![ChatChoice {
                index: 0,
                message,
                finish_reason,
            }],
            usage: None,
        })
    }

    /// Anthropic Claude 格式流式请求（/v1/messages）
    async fn send_chat_stream_anthropic(
        &self,
        provider: &ProviderProfile,
        messages: Vec<ApiMessage>,
        app_handle: &tauri::AppHandle,
        event_name: &str,
    ) -> Result<ChatResponse, LlmError> {
        let api_key = provider.api_key.as_deref().ok_or(LlmError::NoApiKey)?;

        // 构建 Anthropic 消息格式（分离 system 和 user/assistant）
        let mut system_content: Option<String> = None;
        let mut api_messages: Vec<serde_json::Value> = Vec::new();

        for msg in &messages {
            match msg.role.as_str() {
                "system" => {
                    system_content = msg.content.clone();
                }
                _ => {
                    let mut entry = serde_json::json!({
                        "role": msg.role,
                    });
                    if let Some(content) = &msg.content {
                        entry["content"] = serde_json::json!(content);
                    }
                    api_messages.push(entry);
                }
            }
        }

        // 构建请求体
        let mut request_body = serde_json::json!({
            "model": provider.model_name,
            "max_tokens": provider.max_tokens,
            "temperature": provider.temperature,
            "stream": true,
            "messages": api_messages,
        });
        if let Some(sys) = system_content {
            request_body["system"] = serde_json::json!(sys);
        }

        // 构建端点 URL
        let base = provider.base_url.trim_end_matches('/');
        let url = if base.ends_with("/messages") {
            base.to_string()
        } else if base.ends_with("/v1") {
            format!("{}/messages", base)
        } else {
            format!("{}/v1/messages", base)
        };

        let response = self.client
            .post(&url)
            .header("x-api-key", api_key)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .json(&request_body)
            .send()
            .await
            .map_err(|e| LlmError::RequestFailed(format!("Anthropic: {}", e)))?;

        if !response.status().is_success() {
            let status = response.status();
            let body = response.text().await.unwrap_or_default();
            return Err(LlmError::RequestFailed(format!("Anthropic HTTP {}: {}", status, body)));
        }

        // 处理 SSE 流式响应
        let mut stream = response.bytes_stream();
        let mut full_content = String::new();

        while let Some(chunk_result) = stream.next().await {
            let chunk = chunk_result.map_err(|e| LlmError::StreamInterrupted(e.to_string()))?;
            let text = String::from_utf8_lossy(&chunk);

            for line in text.lines() {
                let line = line.trim();
                if line.is_empty() || line.starts_with(":") {
                    continue;
                }

                // Anthropic SSE: data: {...}
                if line.starts_with("data: ") {
                    let data = &line[6..];
                    if data == "[DONE]" { continue; }

                    if let Ok(parsed) = serde_json::from_str::<serde_json::Value>(data) {
                        let event_type = parsed.get("type").and_then(|t| t.as_str()).unwrap_or("");

                        if event_type == "content_block_delta" {
                            if let Some(text_val) = parsed
                                .get("delta")
                                .and_then(|d| d.get("text"))
                                .and_then(|t| t.as_str())
                            {
                                full_content.push_str(text_val);
                                let _ = app_handle.emit(event_name, serde_json::json!({
                                    "type": "content",
                                    "content": text_val,
                                }));
                            }
                        }
                    }
                }
            }
        }

        // 发送完成事件
        let _ = app_handle.emit(event_name, serde_json::json!({
            "type": "done",
            "finish_reason": "end_turn",
        }));

        // 构建最终响应（兼容 OpenAI 格式，方便前端统一处理）
        let message = ChatChoiceMessage {
            role: "assistant".to_string(),
            content: if full_content.is_empty() { None } else { Some(full_content) },
            tool_calls: None,
            reasoning_content: None,
        };

        Ok(ChatResponse {
            id: format!("anthropic_{}", uuid::Uuid::new_v4()),
            choices: vec![ChatChoice {
                index: 0,
                message,
                finish_reason: Some("end_turn".to_string()),
            }],
            usage: None,
        })
    }

    /// Ollama 本地模型流式请求（/api/chat）
    async fn send_chat_stream_ollama(
        &self,
        provider: &ProviderProfile,
        messages: Vec<ApiMessage>,
        app_handle: &tauri::AppHandle,
        event_name: &str,
    ) -> Result<ChatResponse, LlmError> {
        // Ollama 不需要 API Key

        // 构建端点 URL（去除 /v1 后缀）
        let mut base = provider.base_url.trim_end_matches('/').to_string();
        if base.ends_with("/v1") {
            base = base[..base.len() - 3].to_string();
        }
        let url = format!("{}/api/chat", base);

        // 构建 Ollama 消息格式
        let ollama_messages: Vec<serde_json::Value> = messages.iter()
            .filter(|m| m.role != "system") // Ollama 不支持 system 角色（放在 prompt 中）
            .map(|m| {
                serde_json::json!({
                    "role": m.role,
                    "content": m.content.clone().unwrap_or_default(),
                })
            })
            .collect();

        let request_body = serde_json::json!({
            "model": provider.model_name,
            "messages": ollama_messages,
            "stream": true,
        });

        let response = self.client
            .post(&url)
            .header("Content-Type", "application/json")
            .json(&request_body)
            .send()
            .await
            .map_err(|e| LlmError::RequestFailed(format!("Ollama: {}", e)))?;

        if !response.status().is_success() {
            let status = response.status();
            let body = response.text().await.unwrap_or_default();
            return Err(LlmError::RequestFailed(format!("Ollama HTTP {}: {}", status, body)));
        }

        // 处理 Ollama 流式响应（每行一个 JSON 对象）
        let mut stream = response.bytes_stream();
        let mut full_content = String::new();

        while let Some(chunk_result) = stream.next().await {
            let chunk = chunk_result.map_err(|e| LlmError::StreamInterrupted(e.to_string()))?;
            let text = String::from_utf8_lossy(&chunk);

            for line in text.lines() {
                let line = line.trim();
                if line.is_empty() { continue; }

                // Ollama 返回格式：每行一个完整 JSON 对象
                if let Ok(parsed) = serde_json::from_str::<serde_json::Value>(line) {
                    // 提取 message.content 字段中的 token
                    if let Some(text_val) = parsed
                        .get("message")
                        .and_then(|m| m.get("content"))
                        .and_then(|c| c.as_str())
                    {
                        full_content.push_str(text_val);
                        let _ = app_handle.emit(event_name, serde_json::json!({
                            "type": "content",
                            "content": text_val,
                        }));
                    }

                    // done=true 表示结束
                    if parsed.get("done").and_then(|d| d.as_bool()).unwrap_or(false) {
                        break;
                    }
                }
            }
        }

        // 发送完成事件
        let _ = app_handle.emit(event_name, serde_json::json!({
            "type": "done",
            "finish_reason": "stop",
        }));

        // 构建最终响应（兼容 OpenAI 格式）
        let message = ChatChoiceMessage {
            role: "assistant".to_string(),
            content: if full_content.is_empty() { None } else { Some(full_content) },
            tool_calls: None,
            reasoning_content: None,
        };

        Ok(ChatResponse {
            id: format!("ollama_{}", uuid::Uuid::new_v4()),
            choices: vec![ChatChoice {
                index: 0,
                message,
                finish_reason: Some("stop".to_string()),
            }],
            usage: None,
        })
    }

    /// 带工具调用循环的聊天（最多3轮）
    pub async fn send_chat_with_tool_loop(
        &self,
        provider: &ProviderProfile,
        mut messages: Vec<ApiMessage>,
        tools: Vec<ToolDefinition>,
        plugin_registry: &crate::plugins::plugin_registry::PluginRegistry,
        plugin_context: &PluginContext,
        app_handle: &tauri::AppHandle,
        event_name: &str,
    ) -> Result<ChatResponse, LlmError> {
        let mut round = 0;

        loop {
            if round >= self.max_tool_rounds {
                return Err(LlmError::MaxToolRoundsExceeded);
            }

            let response = self.send_chat_stream(
                provider,
                messages.clone(),
                Some(tools.clone()),
                app_handle,
                event_name,
            ).await?;

            // 检查是否有工具调用
            let choice = &response.choices[0];
            let has_tool_calls = choice.message.tool_calls.is_some();

            if !has_tool_calls {
                return Ok(response);
            }

            // 将助手消息（含工具调用）加入历史
            let assistant_msg = ApiMessage {
                role: "assistant".to_string(),
                content: choice.message.content.clone(),
                tool_calls: choice.message.tool_calls.clone(),
                tool_call_id: None,
                name: None,
            };
            messages.push(assistant_msg);

            // 执行每个工具调用
            if let Some(tool_calls) = &choice.message.tool_calls {
                for tc in tool_calls {
                    let args: serde_json::Value = serde_json::from_str(&tc.function.arguments)
                        .unwrap_or(serde_json::Value::Null);

                    let result = plugin_registry.execute_plugin(
                        &tc.function.name,
                        &args,
                        plugin_context,
                    ).await;

                    let result_content = match result {
                        PluginResult { success: true, content, .. } => content,
                        PluginResult { content, .. } => format!("工具执行失败: {}", content),
                    };

                    // 发送工具结果事件
                    let _ = app_handle.emit(event_name, serde_json::json!({
                        "type": "tool_result",
                        "tool_name": tc.function.name,
                        "result": result_content,
                    }));

                    let tool_msg = ApiMessage {
                        role: "tool".to_string(),
                        content: Some(result_content),
                        tool_calls: None,
                        tool_call_id: Some(tc.id.clone()),
                        name: Some(tc.function.name.clone()),
                    };
                    messages.push(tool_msg);
                }
            }

            round += 1;
        }
    }

    /// 测试连接
    pub async fn test_connection(&self, provider: &ProviderProfile) -> Result<crate::models::settings::ConnectionTestResult, LlmError> {
        let api_key = provider.api_key.as_deref().ok_or(LlmError::NoApiKey)?;

        let start = std::time::Instant::now();

        let request = ChatRequest {
            model: provider.model_name.clone(),
            messages: vec![ApiMessage {
                role: "user".to_string(),
                content: Some("Hi".to_string()),
                tool_calls: None,
                tool_call_id: None,
                name: None,
            }],
            tools: None,
            temperature: Some(0.1),
            max_tokens: Some(10),
            stream: Some(false),
        };

        let url = format!("{}/chat/completions", provider.base_url.trim_end_matches('/'));

        let response = self.client
            .post(&url)
            .header("Authorization", format!("Bearer {}", api_key))
            .header("Content-Type", "application/json")
            .json(&request)
            .send()
            .await
            .map_err(|e| LlmError::ConnectionTestFailed(e.to_string()))?;

        let latency = start.elapsed().as_millis() as u64;

        if response.status().is_success() {
            Ok(crate::models::settings::ConnectionTestResult {
                success: true,
                message: "连接成功".to_string(),
                latency_ms: Some(latency),
                model_info: Some(provider.model_name.clone()),
            })
        } else {
            let status = response.status();
            let body = response.text().await.unwrap_or_default();
            Ok(crate::models::settings::ConnectionTestResult {
                success: false,
                message: format!("HTTP {}: {}", status, body),
                latency_ms: Some(latency),
                model_info: None,
            })
        }
    }

    /// 发送简单提示（无工具调用）
    /// 覆盖 temperature 为 0.3，用于确定性任务
    pub async fn send_simple_prompt(
        &self,
        provider: &ProviderProfile,
        system_prompt: &str,
        user_prompt: &str,
    ) -> Result<String, LlmError> {
        let messages = vec![
            ApiMessage {
                role: "system".to_string(),
                content: Some(system_prompt.to_string()),
                tool_calls: None,
                tool_call_id: None,
                name: None,
            },
            ApiMessage {
                role: "user".to_string(),
                content: Some(user_prompt.to_string()),
                tool_calls: None,
                tool_call_id: None,
                name: None,
            },
        ];

        let request = ChatRequest {
            model: provider.model_name.clone(),
            messages,
            tools: None,
            temperature: Some(0.3), // 简单提示使用低温度，提高确定性
            max_tokens: Some(provider.max_tokens),
            stream: Some(false),
        };

        let api_key = provider.api_key.as_deref().ok_or(LlmError::NoApiKey)?;
        let url = format!("{}/chat/completions", provider.base_url.trim_end_matches('/'));

        let response = self.client
            .post(&url)
            .header("Authorization", format!("Bearer {}", api_key))
            .header("Content-Type", "application/json")
            .json(&request)
            .send()
            .await
            .map_err(|e| LlmError::RequestFailed(e.to_string()))?;

        if !response.status().is_success() {
            let status = response.status();
            let body = response.text().await.unwrap_or_default();
            return Err(LlmError::RequestFailed(format!("HTTP {}: {}", status, body)));
        }

        let chat_response: ChatResponse = response
            .json()
            .await
            .map_err(|e| LlmError::ParseFailed(e.to_string()))?;

        Ok(chat_response.choices
            .first()
            .and_then(|c| c.message.content.clone())
            .unwrap_or_default())
    }

    /// 获取嵌入向量
    /// 使用配置中的 embedding_model，如未配置则使用默认值
    pub async fn get_embedding(
        &self,
        provider: &ProviderProfile,
        text: &str,
        embedding_model: Option<&str>,
    ) -> Result<Vec<f32>, LlmError> {
        let api_key = provider.api_key.as_deref().ok_or(LlmError::NoApiKey)?;

        let url = format!("{}/embeddings", provider.base_url.trim_end_matches('/'));

        #[derive(serde::Serialize)]
        struct EmbeddingRequest {
            model: String,
            input: String,
        }

        let model_name = embedding_model.unwrap_or("text-embedding-3-small");
        let request = EmbeddingRequest {
            model: model_name.to_string(),
            input: text.to_string(),
        };

        let response = self.client
            .post(&url)
            .header("Authorization", format!("Bearer {}", api_key))
            .header("Content-Type", "application/json")
            .json(&request)
            .send()
            .await
            .map_err(|e| LlmError::RequestFailed(e.to_string()))?;

        if !response.status().is_success() {
            return Err(LlmError::RequestFailed("嵌入请求失败".to_string()));
        }

        #[derive(serde::Deserialize)]
        struct EmbeddingResponse {
            data: Vec<EmbeddingData>,
        }
        #[derive(serde::Deserialize)]
        struct EmbeddingData {
            embedding: Vec<f32>,
        }

        let result: EmbeddingResponse = response
            .json()
            .await
            .map_err(|e| LlmError::ParseFailed(e.to_string()))?;

        result.data
            .first()
            .map(|d| d.embedding.clone())
            .ok_or_else(|| LlmError::ParseFailed("无嵌入数据".to_string()))
    }

    /// 从响应中提取情绪和动作
    /// 使用双括号格式 [[emotion:xxx]] 和 [[action:xxx]]，与原 Android 一致
    pub fn extract_emotion_action(content: &str) -> (Option<String>, Option<String>) {
        let mut emotion = None;
        let mut action = None;

        // 解析 [[emotion:xxx]] 和 [[action:xxx]] 标签（双括号，与原 Android 一致）
        let emotion_re = regex::Regex::new(r"\[\[emotion:(\w+)\]\]").ok();
        let action_re = regex::Regex::new(r"\[\[action:(\w+)\]\]").ok();

        if let Some(re) = &emotion_re {
            if let Some(caps) = re.captures(content) {
                emotion = caps.get(1).map(|m| m.as_str().to_string());
            }
        }

        if let Some(re) = &action_re {
            if let Some(caps) = re.captures(content) {
                action = caps.get(1).map(|m| m.as_str().to_string());
            }
        }

        (emotion, action)
    }

    /// 解析OpenAI响应
    /// 返回 (内容, 工具调用, 情绪, 动作)
    pub fn parse_openai_response(response: &ChatResponse) -> (String, Option<Vec<ToolCall>>, Option<String>, Option<String>) {
        let choice = response.choices.first();
        match choice {
            Some(c) => {
                let content = c.message.content.clone().unwrap_or_default();
                let (emotion, action) = Self::extract_emotion_action(&content);

                // 清理内容中的标签（双括号格式）
                let clean_content = regex::Regex::new(r"\[\[(?:emotion|action):\w+\]\]")
                    .ok()
                    .map(|re| re.replace_all(&content, "").trim().to_string())
                    .unwrap_or(content);

                let tool_calls = c.message.tool_calls.as_ref().map(|tcs| {
                    tcs.iter().map(|tc| ToolCall {
                        id: tc.id.clone(),
                        name: tc.function.name.clone(),
                        arguments: serde_json::from_str(&tc.function.arguments)
                            .unwrap_or(serde_json::Value::Null),
                    }).collect()
                });

                (clean_content, tool_calls, emotion, action)
            }
            None => (String::new(), None, None, None),
        }
    }

    /// 提取推理内容（思维链模型如 DeepSeek-R1）
    pub fn extract_reasoning_content(response: &ChatResponse) -> Option<String> {
        response.choices.first().and_then(|c| c.message.reasoning_content.clone())
    }

    /// 主动互动聊天（低温度、短回复）
    pub async fn send_proactive_chat(
        &self,
        provider: &ProviderProfile,
        system_prompt: &str,
        context: &str,
    ) -> Result<String, LlmError> {
        self.send_simple_prompt(provider, system_prompt, context).await
    }

    /// 评分难忘时刻（返回 1-10 分）
    pub async fn score_memorable_moments(
        &self,
        provider: &ProviderProfile,
        user_msg: &str,
        assistant_msg: &str,
    ) -> Result<f32, LlmError> {
        let system = "你是一个对话重要性评分器。根据对话内容评估其作为难忘时刻的分数（1-10分）。只返回一个数字。";
        let user = format!("用户：{}\n助手：{}\n\n请评分（1-10）：", user_msg, assistant_msg);
        let result = self.send_simple_prompt(provider, system, &user).await?;
        Ok(result.trim().parse::<f32>().unwrap_or(5.0).clamp(1.0, 10.0))
    }

    /// 进化角色性格
    pub async fn evolve_personality(
        &self,
        provider: &ProviderProfile,
        current_personality: &str,
        interaction_summary: &str,
    ) -> Result<String, LlmError> {
        let system = "你是一个角色性格进化引擎。根据互动历史，微调角色的性格描述，使其更自然、更有深度。只返回新的性格描述。";
        let user = format!("当前性格：{}\n\n互动摘要：{}\n\n请微调性格描述：", current_personality, interaction_summary);
        self.send_simple_prompt(provider, system, &user).await
    }

    /// 生成日记内容
    pub async fn generate_diary_content(
        &self,
        provider: &ProviderProfile,
        persona_name: &str,
        date: &str,
        conversation_summary: &str,
        style: &str,
    ) -> Result<String, LlmError> {
        let system = format!("你是「{}」，正在写日记。风格：{}。请用第一人称写一篇日记。", persona_name, style);
        let user = format!("日期：{}\n今日对话摘要：{}\n\n请写日记：", date, conversation_summary);
        self.send_simple_prompt(provider, &system, &user).await
    }

    /// 生成唠叨内容
    pub async fn generate_nag_content(
        &self,
        provider: &ProviderProfile,
        persona_name: &str,
        persona_personality: &str,
        idle_duration_minutes: u64,
    ) -> Result<String, LlmError> {
        let system = format!("你是「{}」，性格：{}。用户已经{}分钟没有和你说话了，你想主动搭话。只说一句话，简短自然。", persona_name, persona_personality, idle_duration_minutes);
        let user = "请生成一句主动搭话的内容。".to_string();
        self.send_simple_prompt(provider, &system, &user).await
    }

    /// 分析自动操作意图
    pub async fn analyze_auto_operation(
        &self,
        provider: &ProviderProfile,
        context: &str,
    ) -> Result<serde_json::Value, LlmError> {
        let system = "你是一个意图分析器。分析用户行为上下文，判断是否需要执行自动操作。返回JSON格式：{\"should_act\": bool, \"action_type\": string, \"reason\": string}";
        let result = self.send_simple_prompt(provider, system, context).await?;
        // 手动解析 JSON，避免要求 LlmError 实现 Deserialize
        match serde_json::from_str::<serde_json::Value>(&result) {
            Ok(v) => Ok(v),
            Err(_) => Ok(serde_json::json!({
                "should_act": false,
                "action_type": "none",
                "reason": "解析失败"
            })),
        }
    }
}
