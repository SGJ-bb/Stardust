// 生图插件，对应 GenerateImagePlugin

use async_trait::async_trait;

use crate::models::chat::ToolDefinition;
use crate::plugins::plugin_trait::{PluginContext, PluginResult, ToolPlugin};

/// 图片生成插件
pub struct ImageGenPlugin {
    enabled: bool,
}

impl ImageGenPlugin {
    pub fn new() -> Self {
        ImageGenPlugin { enabled: true }
    }
}

#[async_trait]
impl ToolPlugin for ImageGenPlugin {
    fn name(&self) -> &str {
        "generate_image"
    }

    fn description(&self) -> &str {
        "根据文字描述生成图片"
    }

    fn get_definition(&self) -> ToolDefinition {
        ToolDefinition {
            r#type: "function".to_string(),
            function: crate::models::chat::ToolFunction {
                name: "generate_image".to_string(),
                description: "根据文字描述生成图片".to_string(),
                parameters: serde_json::json!({
                    "type": "object",
                    "properties": {
                        "prompt": {
                            "type": "string",
                            "description": "图片描述"
                        },
                        "style": {
                            "type": "string",
                            "description": "图片风格，如: anime, realistic, watercolor",
                            "enum": ["anime", "realistic", "watercolor", "pixel_art", "oil_painting"]
                        },
                        "size": {
                            "type": "string",
                            "description": "图片尺寸，如: 512x512, 1024x1024",
                            "enum": ["512x512", "1024x1024", "1024x768"]
                        }
                    },
                    "required": ["prompt"]
                }),
            },
        }
    }

    async fn execute(&self, arguments: &serde_json::Value, _context: &PluginContext) -> PluginResult {
        let prompt = arguments["prompt"].as_str().unwrap_or("");
        let style = arguments["style"].as_str().unwrap_or("anime");
        let size = arguments["size"].as_str().unwrap_or("512x512");

        if prompt.is_empty() {
            return PluginResult::err("图片描述不能为空");
        }

        tracing::info!("生成图片: {} (风格: {}, 尺寸: {})", prompt, style, size);

        // 在实际实现中，这里应该调用图片生成API（如DALL-E、Stable Diffusion等）
        PluginResult::ok_with_data(
            format!("正在为你生成图片：{}（风格: {}）", prompt, style),
            serde_json::json!({
                "prompt": prompt,
                "style": style,
                "size": size,
                "image_url": "",
                "status": "generating",
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
