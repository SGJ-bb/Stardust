// 定时闹钟插件，对应原 AlarmAtTimePlugin
// set_alarm_at_time: hour, minute, label

use async_trait::async_trait;

use crate::models::chat::ToolDefinition;
use crate::plugins::plugin_trait::{PluginContext, PluginResult, ToolPlugin};

/// 定时闹钟插件
pub struct AlarmAtTimePlugin {
    enabled: bool,
}

impl AlarmAtTimePlugin {
    pub fn new() -> Self {
        AlarmAtTimePlugin { enabled: true }
    }
}

#[async_trait]
impl ToolPlugin for AlarmAtTimePlugin {
    fn name(&self) -> &str {
        "set_alarm_at_time"
    }

    fn description(&self) -> &str {
        "在指定时间设置闹钟提醒"
    }

    fn get_definition(&self) -> ToolDefinition {
        ToolDefinition {
            r#type: "function".to_string(),
            function: crate::models::chat::ToolFunction {
                name: "set_alarm_at_time".to_string(),
                description: "在指定的小时和分钟设置闹钟提醒".to_string(),
                parameters: serde_json::json!({
                    "type": "object",
                    "properties": {
                        "hour": {
                            "type": "integer",
                            "description": "小时（0-23）"
                        },
                        "minute": {
                            "type": "integer",
                            "description": "分钟（0-59）"
                        },
                        "label": {
                            "type": "string",
                            "description": "提醒内容"
                        }
                    },
                    "required": ["hour", "minute", "label"]
                }),
            },
        }
    }

    async fn execute(&self, arguments: &serde_json::Value, _context: &PluginContext) -> PluginResult {
        let hour = arguments["hour"].as_u64().unwrap_or(0) as u32;
        let minute = arguments["minute"].as_u64().unwrap_or(0) as u32;
        let label = arguments["label"].as_str().unwrap_or("");

        if hour > 23 || minute > 59 {
            return PluginResult::err("时间格式错误，小时范围0-23，分钟范围0-59");
        }

        if label.is_empty() {
            return PluginResult::err("提醒内容不能为空");
        }

        tracing::info!("设置定时闹钟: {:02}:{:02} - {}", hour, minute, label);

        PluginResult::ok(format!(
            "已设置闹钟：{:02}:{:02} 提醒「{}」",
            hour, minute, label
        ))
    }

    fn is_enabled(&self) -> bool {
        self.enabled
    }

    fn set_enabled(&mut self, enabled: bool) {
        self.enabled = enabled;
    }
}
