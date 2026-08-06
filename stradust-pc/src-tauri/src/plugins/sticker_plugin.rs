// 表情包插件，对应 SendStickerPlugin

use async_trait::async_trait;

use crate::models::chat::ToolDefinition;
use crate::plugins::plugin_trait::{PluginContext, PluginResult, ToolPlugin};

/// 表情包插件
pub struct StickerPlugin {
    enabled: bool,
}

impl StickerPlugin {
    pub fn new() -> Self {
        StickerPlugin { enabled: true }
    }
}

#[async_trait]
impl ToolPlugin for StickerPlugin {
    fn name(&self) -> &str {
        "send_sticker"
    }

    fn description(&self) -> &str {
        "发送表情包"
    }

    fn get_definition(&self) -> ToolDefinition {
        ToolDefinition {
            r#type: "function".to_string(),
            function: crate::models::chat::ToolFunction {
                name: "send_sticker".to_string(),
                description: "发送表情包".to_string(),
                parameters: serde_json::json!({
                    "type": "object",
                    "properties": {
                        "keyword": {
                            "type": "string",
                            "description": "表情包关键词"
                        },
                        "category": {
                            "type": "string",
                            "description": "表情包分类"
                        }
                    },
                    "required": ["keyword"]
                }),
            },
        }
    }

    async fn execute(&self, arguments: &serde_json::Value, _context: &PluginContext) -> PluginResult {
        let keyword = arguments["keyword"].as_str().unwrap_or("");
        let category = arguments["category"].as_str().unwrap_or("");

        if keyword.is_empty() {
            return PluginResult::err("表情包关键词不能为空");
        }

        tracing::info!("发送表情包: {} (分类: {})", keyword, category);

        PluginResult::ok_with_data(
            format!("[表情包: {}]", keyword),
            serde_json::json!({
                "keyword": keyword,
                "category": category,
                "sticker_path": "",
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
