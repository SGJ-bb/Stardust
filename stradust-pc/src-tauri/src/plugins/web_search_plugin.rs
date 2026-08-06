// 搜索插件，对应 WebSearchPlugin

use async_trait::async_trait;

use crate::models::chat::ToolDefinition;
use crate::plugins::plugin_trait::{PluginContext, PluginResult, ToolPlugin};

/// 网页搜索插件
pub struct WebSearchPlugin {
    enabled: bool,
}

impl WebSearchPlugin {
    pub fn new() -> Self {
        WebSearchPlugin { enabled: true }
    }
}

#[async_trait]
impl ToolPlugin for WebSearchPlugin {
    fn name(&self) -> &str {
        "web_search"
    }

    fn description(&self) -> &str {
        "搜索互联网获取信息"
    }

    fn get_definition(&self) -> ToolDefinition {
        ToolDefinition {
            r#type: "function".to_string(),
            function: crate::models::chat::ToolFunction {
                name: "web_search".to_string(),
                description: "搜索互联网获取信息".to_string(),
                parameters: serde_json::json!({
                    "type": "object",
                    "properties": {
                        "query": {
                            "type": "string",
                            "description": "搜索关键词"
                        },
                        "num_results": {
                            "type": "integer",
                            "description": "返回结果数量，默认5"
                        }
                    },
                    "required": ["query"]
                }),
            },
        }
    }

    async fn execute(&self, arguments: &serde_json::Value, _context: &PluginContext) -> PluginResult {
        let query = arguments["query"].as_str().unwrap_or("");

        if query.is_empty() {
            return PluginResult::err("搜索关键词不能为空");
        }

        // 在实际实现中，这里应该调用搜索API
        tracing::info!("执行网页搜索: {}", query);

        PluginResult::ok(format!(
            "搜索「{}」的结果：[此处为模拟搜索结果，实际实现需接入搜索API]",
            query
        ))
    }

    fn is_enabled(&self) -> bool {
        self.enabled
    }

    fn set_enabled(&mut self, enabled: bool) {
        self.enabled = enabled;
    }
}
