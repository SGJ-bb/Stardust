// 时间插件，对应 CurrentTimePlugin

use async_trait::async_trait;
use chrono::Datelike;

use crate::models::chat::ToolDefinition;
use crate::plugins::plugin_trait::{PluginContext, PluginResult, ToolPlugin};

/// 当前时间插件
pub struct CurrentTimePlugin {
    enabled: bool,
}

impl CurrentTimePlugin {
    pub fn new() -> Self {
        CurrentTimePlugin { enabled: true }
    }
}

#[async_trait]
impl ToolPlugin for CurrentTimePlugin {
    fn name(&self) -> &str {
        "get_current_time"
    }

    fn description(&self) -> &str {
        "获取当前日期和时间"
    }

    fn get_definition(&self) -> ToolDefinition {
        ToolDefinition {
            r#type: "function".to_string(),
            function: crate::models::chat::ToolFunction {
                name: "get_current_time".to_string(),
                description: "获取当前日期和时间".to_string(),
                parameters: serde_json::json!({
                    "type": "object",
                    "properties": {
                        "timezone": {
                            "type": "string",
                            "description": "时区，默认为 Asia/Shanghai"
                        }
                    }
                }),
            },
        }
    }

    async fn execute(&self, arguments: &serde_json::Value, _context: &PluginContext) -> PluginResult {
        let timezone = arguments["timezone"].as_str().unwrap_or("Asia/Shanghai");

        let now = chrono::Local::now();
        let date_str = now.format("%Y年%m月%d日").to_string();
        let time_str = now.format("%H:%M:%S").to_string();
        let weekday = match now.weekday() {
            chrono::Weekday::Mon => "星期一",
            chrono::Weekday::Tue => "星期二",
            chrono::Weekday::Wed => "星期三",
            chrono::Weekday::Thu => "星期四",
            chrono::Weekday::Fri => "星期五",
            chrono::Weekday::Sat => "星期六",
            chrono::Weekday::Sun => "星期日",
        };

        let result = format!(
            "当前时间（{}）：{} {} {}",
            timezone, date_str, weekday, time_str
        );

        PluginResult::ok_with_data(
            result,
            serde_json::json!({
                "date": date_str,
                "time": time_str,
                "weekday": weekday,
                "timezone": timezone,
                "timestamp": now.timestamp(),
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
