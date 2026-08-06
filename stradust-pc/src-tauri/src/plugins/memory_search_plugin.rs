// 记忆搜索插件，对应 SearchMemoryPlugin

use async_trait::async_trait;

use crate::models::chat::ToolDefinition;
use crate::plugins::plugin_trait::{PluginContext, PluginResult, ToolPlugin};

/// 记忆搜索插件
pub struct MemorySearchPlugin {
    enabled: bool,
}

impl MemorySearchPlugin {
    pub fn new() -> Self {
        MemorySearchPlugin { enabled: true }
    }
}

#[async_trait]
impl ToolPlugin for MemorySearchPlugin {
    fn name(&self) -> &str {
        "search_memory"
    }

    fn description(&self) -> &str {
        "搜索AI的记忆库，查找关于用户的记忆信息"
    }

    fn get_definition(&self) -> ToolDefinition {
        ToolDefinition {
            r#type: "function".to_string(),
            function: crate::models::chat::ToolFunction {
                name: "search_memory".to_string(),
                description: "搜索AI的记忆库，查找关于用户的记忆信息".to_string(),
                parameters: serde_json::json!({
                    "type": "object",
                    "properties": {
                        "query": {
                            "type": "string",
                            "description": "搜索关键词"
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

        // 在实际实现中，这里应该调用记忆服务搜索
        tracing::info!("搜索记忆: {}", query);

        PluginResult::ok_with_data(
            format!("搜索记忆「{}」完成", query),
            serde_json::json!({
                "query": query,
                "results": []
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
