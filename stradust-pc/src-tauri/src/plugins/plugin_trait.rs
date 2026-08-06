// ToolPlugin trait定义，对应原 plugin/ToolPlugin.kt

use async_trait::async_trait;
use serde::{Deserialize, Serialize};
use std::collections::HashMap;

use crate::models::chat::ToolDefinition;

/// 插件执行上下文
#[derive(Debug, Clone)]
pub struct PluginContext {
    /// 当前角色ID
    pub persona_id: String,
    /// 当前会话ID
    pub session_id: String,
    /// 额外参数
    pub extra: HashMap<String, String>,
}

/// 插件执行结果
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PluginResult {
    pub success: bool,
    pub content: String,
    pub data: Option<serde_json::Value>,
}

impl PluginResult {
    /// 创建成功结果
    pub fn ok(content: impl Into<String>) -> Self {
        PluginResult {
            success: true,
            content: content.into(),
            data: None,
        }
    }

    /// 创建成功结果（带数据）
    pub fn ok_with_data(content: impl Into<String>, data: serde_json::Value) -> Self {
        PluginResult {
            success: true,
            content: content.into(),
            data: Some(data),
        }
    }

    /// 创建失败结果
    pub fn err(content: impl Into<String>) -> Self {
        PluginResult {
            success: false,
            content: content.into(),
            data: None,
        }
    }
}

/// 工具插件trait，所有插件必须实现
#[async_trait]
pub trait ToolPlugin: Send + Sync {
    /// 插件名称
    fn name(&self) -> &str;

    /// 插件描述
    fn description(&self) -> &str;

    /// 获取工具定义（OpenAI function calling 格式）
    fn get_definition(&self) -> ToolDefinition;

    /// 执行插件
    async fn execute(&self, arguments: &serde_json::Value, context: &PluginContext) -> PluginResult;

    /// 是否启用
    fn is_enabled(&self) -> bool;

    /// 设置启用状态
    fn set_enabled(&mut self, enabled: bool);
}
