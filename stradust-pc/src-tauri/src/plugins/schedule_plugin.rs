// 日程插件，对应 SchedulePlugin

use async_trait::async_trait;

use crate::models::chat::ToolDefinition;
use crate::plugins::plugin_trait::{PluginContext, PluginResult, ToolPlugin};

/// 日程插件
pub struct SchedulePlugin {
    enabled: bool,
}

impl SchedulePlugin {
    pub fn new() -> Self {
        SchedulePlugin { enabled: true }
    }
}

#[async_trait]
impl ToolPlugin for SchedulePlugin {
    fn name(&self) -> &str {
        "add_schedule"
    }

    fn description(&self) -> &str {
        "添加日程事件到日历"
    }

    fn get_definition(&self) -> ToolDefinition {
        ToolDefinition {
            r#type: "function".to_string(),
            function: crate::models::chat::ToolFunction {
                name: "add_schedule".to_string(),
                description: "添加日程事件到日历".to_string(),
                parameters: serde_json::json!({
                    "type": "object",
                    "properties": {
                        "title": {
                            "type": "string",
                            "description": "日程标题"
                        },
                        "start_time": {
                            "type": "string",
                            "description": "开始时间，格式: YYYY-MM-DD HH:MM"
                        },
                        "end_time": {
                            "type": "string",
                            "description": "结束时间，格式: YYYY-MM-DD HH:MM"
                        },
                        "description": {
                            "type": "string",
                            "description": "日程描述"
                        }
                    },
                    "required": ["title", "start_time"]
                }),
            },
        }
    }

    async fn execute(&self, arguments: &serde_json::Value, context: &PluginContext) -> PluginResult {
        let title = arguments["title"].as_str().unwrap_or("");
        let start_time = arguments["start_time"].as_str().unwrap_or("");
        let description = arguments["description"].as_str().unwrap_or("");

        if title.is_empty() || start_time.is_empty() {
            return PluginResult::err("标题和开始时间不能为空");
        }

        tracing::info!(
            "添加日程: {} - {} ({})",
            title, start_time, description
        );

        PluginResult::ok_with_data(
            format!("已添加日程「{}」，时间: {}", title, start_time),
            serde_json::json!({
                "persona_id": context.persona_id,
                "title": title,
                "start_time": start_time,
                "description": description,
            }),
        )
    }

    fn is_enabled(&self) -> bool {
        self.enabled
    }

    fn set_enabled(&mut self, enabled: bool) {
        self.enabled = enabled;
    }
}
