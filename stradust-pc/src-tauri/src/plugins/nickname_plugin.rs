// 昵称插件，对应 NicknamePlugin
// summarize_nicknames: nicknames 数组

use async_trait::async_trait;

use crate::models::chat::ToolDefinition;
use crate::plugins::plugin_trait::{PluginContext, PluginResult, ToolPlugin};

/// 昵称插件
pub struct NicknamePlugin {
    enabled: bool,
}

impl NicknamePlugin {
    pub fn new() -> Self {
        NicknamePlugin { enabled: true }
    }
}

#[async_trait]
impl ToolPlugin for NicknamePlugin {
    fn name(&self) -> &str {
        "summarize_nicknames"
    }

    fn description(&self) -> &str {
        "总结或设置用户昵称列表"
    }

    fn get_definition(&self) -> ToolDefinition {
        ToolDefinition {
            r#type: "function".to_string(),
            function: crate::models::chat::ToolFunction {
                name: "summarize_nicknames".to_string(),
                description: "总结或设置用户昵称列表，可以一次设置多个昵称".to_string(),
                parameters: serde_json::json!({
                    "type": "object",
                    "properties": {
                        "nicknames": {
                            "type": "array",
                            "items": { "type": "string" },
                            "description": "昵称列表"
                        }
                    },
                    "required": ["nicknames"]
                }),
            },
        }
    }

    async fn execute(&self, arguments: &serde_json::Value, _context: &PluginContext) -> PluginResult {
        let nicknames: Vec<String> = arguments["nicknames"]
            .as_array()
            .map(|arr| arr.iter().filter_map(|v| v.as_str().map(|s| s.to_string())).collect())
            .unwrap_or_default();

        if nicknames.is_empty() {
            return PluginResult::err("昵称列表不能为空");
        }

        tracing::info!("设置昵称列表: {:?}", nicknames);

        let primary = &nicknames[0];
        let others = if nicknames.len() > 1 {
            format!("（还有{}个别名：{}）", nicknames.len() - 1, nicknames[1..].join("、"))
        } else {
            String::new()
        };

        PluginResult::ok_with_data(
            format!("好的，以后就叫你「{}」啦~{}", primary, others),
            serde_json::json!({
                "nicknames": nicknames,
                "primary": primary,
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
