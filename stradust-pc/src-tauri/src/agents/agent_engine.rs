// Agent 引擎 — 智能体核心：按需注入技能 + Tool Loop + 事件流

use std::sync::Arc;
use std::time::{SystemTime, UNIX_EPOCH};

use serde_json::json;
use tauri::{AppHandle, Emitter};

use crate::agents::cli_executor::CliExecutor;
use crate::models::agent::{
    AgentEvent, AgentMessage, AgentToolCall, SkillCategory, ToolCallStatus,
};
use crate::models::chat::{
    ApiMessage, ApiToolCall, ChatRequest, ToolDefinition,
};
use crate::plugins::plugin_trait::{PluginContext, PluginResult, ToolPlugin};
use crate::plugins::plugin_registry::PluginRegistry;

/// Agent 引擎配置
pub struct AgentConfig {
    /// 最大工具调用轮次
    pub max_tool_rounds: u32,
    /// 单次执行超时（毫秒）
    pub execution_timeout_ms: u64,
}

impl Default for AgentConfig {
    fn default() -> Self {
        AgentConfig {
            max_tool_rounds: 5,
            execution_timeout_ms: 30_000,
        }
    }
}

/// Agent 引擎：管理技能路由、LLM对话、工具循环、事件推送
pub struct AgentEngine {
    registry: Arc<PluginRegistry>,
    config: AgentConfig,
}

impl AgentEngine {
    pub fn new(registry: Arc<PluginRegistry>) -> Self {
        AgentEngine {
            registry,
            config: AgentConfig::default(),
        }
    }

    /// 根据分类获取可用的技能定义（按需注入的核心）
    pub fn get_skills_by_category(&self, category: &SkillCategory) -> Vec<ToolDefinition> {
        let all_plugins = self.registry.list_plugins();
        // 注意：这里需要从 plugin 获取 category 信息
        // 当前 PluginInfo 没有 category 字段，我们通过 skill_registry 的映射来处理
        self.registry.get_enabled_definitions()
            .into_iter()
            .filter(|def| {
                // 过滤属于该分类的技能（通过 name 前缀或内部映射）
                Self::skill_belongs_to_category(&def.function.name, category)
            })
            .collect()
    }

    /// 获取所有已启用技能的定义（用于"全部"分类）
    pub fn get_all_skill_definitions(&self) -> Vec<ToolDefinition> {
        self.registry.get_enabled_definitions()
    }

    /// 判断某个技能是否属于指定分类
    pub fn skill_belongs_to_category(skill_name: &str, category: &SkillCategory) -> bool {
        match category {
            SkillCategory::Office => {
                matches!(skill_name,
                    "doc_convert" | "ppt_generate" | "excel_process" | "pdf_merge"
                    | "doc_to_pdf" | "markdown_to_docx" | "extract_text_from_doc"
                )
            }
            SkillCategory::Media => {
                matches!(skill_name,
                    "video_trim" | "audio_extract" | "subtitle_gen" | "gif_create"
                    | "video_merge" | "audio_convert" | "screen_record" | "image_batch_resize"
                )
            }
            SkillCategory::Dev => {
                matches!(skill_name,
                    "code_run" | "git_operation" | "api_test" | "json_format"
                    | "regex_test" | "file_search" | "run_script" | "docker_operation"
                )
            }
            SkillCategory::AiAssistant => {
                matches!(skill_name,
                    "web_search" | "summarize" | "translate" | "ocr_recognize"
                    | "text_analyze" | "qa_knowledge" | "smart_rewrite" | "image_understand"
                )
            }
        }
    }

    /// 执行 Agent 对话（带流式事件推送）
    ///
    /// # Arguments
    /// * `app_handle` - Tauri 应用句柄
    /// * `messages` - 消息历史
    /// * `user_message` - 用户最新消息
    /// * `category` - 当前激活的分类
    /// * `system_prompt` - 系统提示词
    /// * `event_name` - Tauri 事件名称
    ///
    /// # Returns
    /// * 最终的助手文本响应
    pub async fn chat(
        &self,
        app_handle: &AppHandle,
        messages: Vec<AgentMessage>,
        user_message: String,
        category: SkillCategory,
        system_prompt: Option<String>,
        event_name: &str,
    ) -> Result<String, String> {
        // 1. 根据分类获取相关技能定义
        let relevant_tools = match category {
            // "全部"分类时获取所有技能
            _ if std::mem::discriminant(&category)
                == std::mem::discriminant(&SkillCategory::AiAssistant) => {
                // 特殊处理：需要额外逻辑判断是否是"全部"
                self.get_all_skill_definitions()
            }
            _ => self.get_skills_by_category(&category),
        };

        // 如果没有匹配的技能，也尝试全量搜索
        let tools = if relevant_tools.is_empty() {
            self.get_all_skill_definitions()
        } else {
            relevant_tools
        };

        // 2. 构建消息历史（转换为 ApiMessage 格式）
        let mut api_messages: Vec<ApiMessage> = Vec::new();

        // 系统提示词
        let agent_system_prompt = system_prompt.unwrap_or_else(|| {
            format!(
                "你是一个智能体助手，擅长以下领域的任务：{}\n\
                 当用户提出需求时，你可以使用提供的工具来完成任务。\n\
                 使用工具前请确认参数正确。如果无法完成，请告知用户原因。\n\
                 回复使用中文。",
                category.display_name()
            )
        });
        api_messages.push(ApiMessage {
            role: "system".to_string(),
            content: Some(agent_system_prompt),
            tool_calls: None,
            tool_call_id: None,
            name: None,
        });

        // 历史消息
        for msg in &messages {
            match msg.role.as_str() {
                "user" => {
                    api_messages.push(ApiMessage {
                        role: "user".to_string(),
                        content: Some(msg.content.clone()),
                        tool_calls: None,
                        tool_call_id: None,
                        name: None,
                    });
                }
                "assistant" => {
                    api_messages.push(ApiMessage {
                        role: "assistant".to_string(),
                        content: Some(msg.content.clone()),
                        tool_calls: msg.tool_calls.clone().map(|tcs| {
                            tcs.into_iter()
                                .map(|tc| ApiToolCall {
                                    id: tc.id,
                                    r#type: "function".to_string(),
                                    function: crate::models::chat::ApiFunctionCall {
                                        name: tc.name,
                                        arguments: tc.arguments.to_string(),
                                    },
                                })
                                .collect()
                        }),
                        tool_call_id: None,
                        name: None,
                    });
                }
                "tool" => {
                    api_messages.push(ApiMessage {
                        role: "tool".to_string(),
                        content: Some(msg.content.clone()),
                        tool_calls: None,
                        tool_call_id: msg.tool_call_id.clone(),
                        name: msg.tool_name.clone(),
                    });
                }
                _ => {} // 忽略 system 等
            }
        }

        // 用户消息
        api_messages.push(ApiMessage {
            role: "user".to_string(),
            content: Some(user_message.clone()),
            tool_calls: None,
            tool_call_id: None,
            name: None,
        });

        // 3. 开始 Tool Loop
        let mut round = 0u32;
        let mut final_response = String::new();
        let context = PluginContext {
            persona_id: "agent".to_string(),
            session_id: format!("agent_{}", SystemTime::now()
                .duration_since(UNIX_EPOCH).unwrap_or_default().as_secs()),
            extra: std::collections::HashMap::new(),
        };

        loop {
            if round >= self.config.max_tool_rounds {
                Self::emit_event(app_handle, event_name, AgentEvent::Error {
                    message: "工具调用轮次已达上限".to_string(),
                });
                break;
            }

            round += 1;

            // 构造请求
            let request = ChatRequest {
                model: "gpt-4o-mini".to_string(), // 默认模型，实际应从设置读取
                messages: api_messages.clone(),
                tools: if tools.is_empty() { None } else { Some(tools.clone()) },
                temperature: Some(0.7),
                max_tokens: Some(2000),
                stream: Some(true),
            };

            // 发送事件：开始一轮LLM调用
            let request_json = serde_json::to_value(&request).unwrap_or_default();
            // 这里应该调用实际的 LLM 服务
            // 由于 Agent 引擎独立于现有聊天流程，我们需要一个简化的调用方式

            // 模拟/简化：通过 emit 通知前端，然后由前端决定如何处理
            // 实际实现中需要集成到现有的 LLM service

            // 先尝试直接用 registry 执行（对于简单的 CLI 类技能）
            // 这里返回一个占位符，后续接入完整 LLM 集成

            // 发送 content 事件作为模拟响应
            let response_text = if round == 1 {
                format!("收到你的消息，正在分析需求...\n当前可用 {} 个「{}」相关技能。",
                    tools.len(), category.display_name())
            } else {
                "已完成工具调用，正在汇总结果...".to_string()
            };

            for chunk in response_text.chars().collect::<Vec<_>>().chunks(4) {
                Self::emit_event(app_handle, event_name, AgentEvent::Content(
                    chunk.iter().collect()
                ));
            }

            // 第一轮：如果有工具且这是第一次，模拟一次工具调用演示
            if round == 1 && !tools.is_empty() {
                // 取第一个可用工具作为演示
                let sample_tool = &tools[0];
                let tool_name = &sample_tool.function.name;

                Self::emit_event(app_handle, event_name, AgentEvent::ToolStart {
                    name: tool_name.clone(),
                    args: json!({"query": &user_message}),
                });

                // 尝试执行
                let result = self.registry.execute_plugin(
                    tool_name,
                    &json!({"query": &user_message}),
                    &context,
                ).await;

                let success = result.success;
                Self::emit_event(app_handle, event_name, AgentEvent::ToolResult {
                    name: tool_name.clone(),
                    result: result.content.clone(),
                    success,
                });

                // 将工具调用加入历史
                let now_ts = SystemTime::now()
                    .duration_since(UNIX_EPOCH).unwrap_or_default().as_secs() as i64;

                api_messages.push(ApiMessage {
                    role: "assistant".to_string(),
                    content: Some(String::new()),
                    tool_calls: Some(vec![ApiToolCall {
                        id: format!("call_{}", now_ts),
                        r#type: "function".to_string(),
                        function: crate::models::chat::ApiFunctionCall {
                            name: tool_name.clone(),
                            arguments: json!({"query": &user_message}).to_string(),
                        },
                    }]),
                    tool_call_id: None,
                    name: None,
                });

                let result_content = result.content.clone();
                api_messages.push(ApiMessage {
                    role: "tool".to_string(),
                    content: Some(result_content.clone()),
                    tool_calls: None,
                    tool_call_id: Some(format!("call_{}", now_ts)),
                    name: Some(tool_name.clone()),
                });

                final_response = if success {
                    format!("✅ 工具 [{}] 执行成功！\n\n{}", tool_name, result_content)
                } else {
                    format!("⚠️ 工具 [{}] 执行遇到问题：\n{}", tool_name, result_content)
                };
            } else {
                final_response = response_text;
            }

            Self::emit_event(app_handle, event_name, AgentEvent::Done {
                tool_calls: vec![],
            });

            break; // 目前只做一轮演示
        }

        Ok(final_response)
    }

    /// 列出所有技能元数据
    pub fn list_all_skills(&self) -> Vec<crate::models::agent::SkillMeta> {
        let plugins = self.registry.list_plugins();
        plugins.iter().map(|p| {
            let name = &p.name;
            let (category, cli_deps, desc) = Self::infer_skill_meta_from_name(name);

            crate::models::agent::SkillMeta {
                id: name.clone(),
                name: Self::skill_display_name(name),
                description: desc,
                category,
                cli_deps,
                enabled: p.is_enabled,
                is_builtin: true,
                version: "0.1.0".to_string(),
            }
        }).collect()
    }

    /// 根据技能名推断元数据（公开供命令层使用）
    pub fn infer_skill_meta_from_name(name: &str) -> (SkillCategory, Vec<String>, String) {
        match name {
            "doc_convert" => (
                SkillCategory::Office,
                vec!["pandoc".to_string()],
                "文档格式转换（PDF/Word/Markdown/HTML互转）".to_string(),
            ),
            "ppt_generate" => (
                SkillCategory::Office,
                vec!["python".to_string()],
                "根据内容自动生成 PPT 演示文稿".to_string(),
            ),
            "excel_process" => (
                SkillCategory::Office,
                vec!["python".to_string()],
                "Excel 数据处理与分析".to_string(),
            ),
            "pdf_merge" => (
                SkillCategory::Office,
                vec!["pdftk".to_string(), "qpdf".to_string()],
                "多个 PDF 文件合并或拆分".to_string(),
            ),
            "video_trim" => (
                SkillCategory::Media,
                vec!["ffmpeg".to_string()],
                "视频裁剪、片段提取".to_string(),
            ),
            "audio_extract" => (
                SkillCategory::Media,
                vec!["ffmpeg".to_string()],
                "从视频中提取音频".to_string(),
            ),
            "subtitle_gen" => (
                SkillCategory::Media,
                vec!["whisper".to_string()],
                "语音/视频自动生成字幕".to_string(),
            ),
            "gif_create" => (
                SkillCategory::Media,
                vec!["ffmpeg".to_string()],
                "视频片段转 GIF 动图".to_string(),
            ),
            "code_run" => (
                SkillCategory::Dev,
                vec!["python".to_string(), "node".to_string()],
                "运行 Python/JavaScript/Shell 代码片段".to_string(),
            ),
            "git_operation" => (
                SkillCategory::Dev,
                vec!["git".to_string()],
                "Git 操作：提交/分支/合并/日志查看".to_string(),
            ),
            "api_test" => (
                SkillCategory::Dev,
                vec!["curl".to_string()],
                "HTTP API 测试与调试".to_string(),
            ),
            "json_format" => (
                SkillCategory::Dev,
                vec!["jq".to_string(), "python".to_string()],
                "JSON 格式化、校验与转换".to_string(),
            ),
            "web_search" => (
                SkillCategory::AiAssistant,
                vec![],
                "联网搜索信息".to_string(),
            ),
            "summarize" => (
                SkillCategory::AiAssistant,
                vec![],
                "长文摘要与关键点提取".to_string(),
            ),
            "translate" => (
                SkillCategory::AiAssistant,
                vec![],
                "多语言翻译".to_string(),
            ),
            "ocr_recognize" => (
                SkillCategory::AiAssistant,
                vec!["tesseract".to_string()],
                "图片文字识别（OCR）".to_string(),
            ),
            // 现有内置插件
            "alarm" => (
                SkillCategory::AiAssistant,
                vec![],
                "设置定时闹钟提醒".to_string(),
            ),
            "alarm_at_time" => (
                SkillCategory::AiAssistant,
                vec![],
                "在指定时间设置闹钟".to_string(),
            ),
            "schedule" => (
                SkillCategory::Office,
                vec![],
                "添加日程安排".to_string(),
            ),
            "web_search_plugin" => (
                SkillCategory::AiAssistant,
                vec![],
                "互联网搜索".to_string(),
            ),
            "memory_search" => (
                SkillCategory::AiAssistant,
                vec![],
                "搜索记忆库".to_string(),
            ),
            "current_time" => (
                SkillCategory::AiAssistant,
                vec![],
                "获取当前时间".to_string(),
            ),
            "nickname" => (
                SkillCategory::AiAssistant,
                vec![],
                "生成昵称建议".to_string(),
            ),
            "sticker" => (
                SkillCategory::Media,
                vec![],
                "发送表情包".to_string(),
            ),
            "image_gen" => (
                SkillCategory::Media,
                vec![],
                "AI 图片生成".to_string(),
            ),
            _ => (
                SkillCategory::AiAssistant,
                vec![],
                format!("{} 技能", name),
            ),
        }
    }

    /// 技能ID → 显示名称（公开）
    pub fn skill_display_name(name: &str) -> String {
        match name {
            "doc_convert" => "文档转换",
            "ppt_generate" => "PPT 生成",
            "excel_process" => "Excel 处理",
            "pdf_merge" => "PDF 合并",
            "video_trim" => "视频裁剪",
            "audio_extract" => "音频提取",
            "subtitle_gen" => "字幕生成",
            "gif_create" => "GIF 制作",
            "code_run" => "代码执行",
            "git_operation" => "Git 操作",
            "api_test" => "API 测试",
            "json_format" => "JSON 格式化",
            "web_search" => "网络搜索",
            "summarize" => "智能摘要",
            "translate" => "翻译",
            "ocr_recognize" => "文字识别",
            "alarm" => "闹钟提醒",
            "alarm_at_time" => "定时闹钟",
            "schedule" => "日程安排",
            "web_search_plugin" => "网页搜索",
            "memory_search" => "记忆搜索",
            "current_time" => "当前时间",
            "nickname" => "昵称建议",
            "sticker" => "表情包",
            "image_gen" => "AI 生图",
            other => other,
        }.to_string()
    }

    /// 推送事件到前端
    fn emit_event(app_handle: &AppHandle, event_name: &str, event: AgentEvent) {
        let payload = serde_json::to_value(&event).unwrap_or_else(|_| json!({}));
        let _ = app_handle.emit(event_name, payload);
    }
}
