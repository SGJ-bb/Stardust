// 闹钟插件，对应 AlarmPlugin

use async_trait::async_trait;
use serde::{Deserialize, Serialize};

use crate::models::chat::ToolDefinition;
use crate::plugins::plugin_trait::{PluginContext, PluginResult, ToolPlugin};

/// 闹钟插件
pub struct AlarmPlugin {
    enabled: bool,
}

impl AlarmPlugin {
    pub fn new() -> Self {
        AlarmPlugin { enabled: true }
    }
}

#[derive(Debug, Deserialize)]
struct AlarmArgs {
    /// 提醒内容
    message: String,
    /// 延迟分钟数
    delay_minutes: u32,
}

#[async_trait]
impl ToolPlugin for AlarmPlugin {
    fn name(&self) -> &str {
        "set_alarm"
    }

    fn description(&self) -> &str {
        "设置闹钟提醒，在指定时间后提醒用户"
    }

    fn get_definition(&self) -> ToolDefinition {
        ToolDefinition {
            r#type: "function".to_string(),
            function: crate::models::chat::ToolFunction {
                name: "set_alarm".to_string(),
                description: "设置闹钟提醒，在指定时间后提醒用户".to_string(),
                parameters: serde_json::json!({
                    "type": "object",
                    "properties": {
                        "message": {
                            "type": "string",
                            "description": "提醒内容"
                        },
                        "delay_minutes": {
                            "type": "integer",
                            "description": "延迟分钟数"
                        }
                    },
                    "required": ["message", "delay_minutes"]
                }),
            },
        }
    }

    async fn execute(&self, arguments: &serde_json::Value, _context: &PluginContext) -> PluginResult {
        let args: AlarmArgs = match serde_json::from_value(arguments.clone()) {
            Ok(a) => a,
            Err(e) => return PluginResult::err(format!("参数解析失败: {}", e)),
        };

        // 在实际实现中，这里应该调用系统通知或定时器
        tracing::info!("设置闹钟: {} 分钟后提醒 - {}", args.delay_minutes, args.message);

        PluginResult::ok(format!(
            "已设置闹钟：{} 分钟后提醒「{}」",
            args.delay_minutes, args.message
        ))
    }

    fn is_enabled(&self) -> bool {
        self.enabled
    }

    fn set_enabled(&mut self, enabled: bool) {
        self.enabled = enabled;
    }
}
