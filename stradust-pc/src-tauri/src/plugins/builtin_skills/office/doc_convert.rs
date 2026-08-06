// 文档格式转换技能 — 使用 pandoc 进行文档格式互转

use async_trait::async_trait;
use crate::models::chat::ToolDefinition;
use crate::plugins::plugin_trait::{PluginContext, PluginResult, ToolPlugin};
use crate::agents::cli_executor::CliExecutor;

pub struct DocConvertPlugin { enabled: bool }

impl DocConvertPlugin {
    pub fn new() -> Self { DocConvertPlugin { enabled: true } }

    /// 根据文件扩展名推断 pandoc 格式名称
    fn detect_format_from_ext(path: &str) -> Option<&'static str> {
        let path_lower = path.to_lowercase();
        let ext = if let Some(pos) = path_lower.rfind('.') {
            &path_lower[pos..]
        } else {
            return None;
        };
        match ext {
            ".doc" | ".docx" => Some("docx"),
            ".md" | ".markdown" => Some("markdown"),
            ".htm" | ".html" => Some("html"),
            ".pdf" => Some("pdf"),
            ".txt" | ".text" => Some("plain"),
            ".rst" => Some("rst"),
            ".epub" => Some("epub"),
            ".odt" => Some("odt"),
            ".rtf" => Some("rtf"),
            _ => None,
        }
    }

    /// 获取文件大小（字节），用于结果展示
    fn file_size_bytes(path: &str) -> Option<u64> {
        std::fs::metadata(path).ok().map(|m| m.len())
    }
}

#[async_trait]
impl ToolPlugin for DocConvertPlugin {
    fn name(&self) -> &str { "doc_convert" }
    fn description(&self) -> &str { "文档格式转换：PDF/Word/Markdown/HTML互转，支持批量处理" }
    fn get_definition(&self) -> ToolDefinition {
        ToolDefinition {
            r#type: "function".to_string(),
            function: crate::models::chat::ToolFunction {
                name: "doc_convert".to_string(),
                description: "将文档从一种格式转换为另一种。支持 PDF、Word(DOCX)、Markdown、HTML、TXT 等格式的互相转换".to_string(),
                parameters: serde_json::json!({
                    "type": "object",
                    "properties": {
                        "input_path": { "type": "string", "description": "源文件路径" },
                        "output_path": { "type": "string", "description": "输出文件路径" },
                        "from_format": {
                            "type": "string",
                            "enum": ["pdf", "docx", "md", "html", "txt"],
                            "description": "源格式（可选，自动检测）"
                        },
                        "to_format": {
                            "type": "string",
                            "enum": ["pdf", "docx", "md", "html", "txt"],
                            "description": "目标格式"
                        },
                        "batch_files": {
                            "type": "array",
                            "items": { "type": "string" },
                            "description": "批量处理的文件路径列表"
                        }
                    },
                    "required": ["to_format"]
                }),
            },
        }
    }

    async fn execute(&self, arguments: &serde_json::Value, _context: &PluginContext) -> PluginResult {
        // 1. 检查 pandoc 是否可用
        if !CliExecutor::check_available("pandoc") {
            return PluginResult::err(
                "该技能需要安装 pandoc 工具。\n\
                 安装方式：\n\
                 - Windows: winget install JohnMacFarlane.pandoc\n\
                 - macOS: brew install pandoc\n\
                 - Linux: apt install pandoc\n\n\
                 安装后重启星尘即可使用此技能。"
            );
        }

        // 2. 解析参数
        let input_path = match arguments["input_path"].as_str() {
            Some(p) => p,
            None => return PluginResult::err("缺少必需参数: input_path（源文件路径）"),
        };

        let output_path = arguments["output_path"].as_str().unwrap_or("");

        let to_format = match arguments["to_format"].as_str() {
            Some(f) => f,
            None => return PluginResult::err("缺少必需参数: to_format（目标格式）"),
        };

        let from_format = arguments["from_format"].as_str();

        // 3. 如果未指定 output_path，根据输入路径和目标格式自动生成
        let output_path = if output_path.is_empty() {
            if let Some(dot_pos) = input_path.rfind('.') {
                format!("{}.{}", &input_path[..dot_pos], to_format)
            } else {
                format!("{}.{}", input_path, to_format)
            }
        } else {
            output_path.to_string()
        };

        // 4. 检查输入文件是否存在
        if !std::path::Path::new(input_path).exists() {
            return PluginResult::err(format!(
                "源文件不存在: {}\n请确认文件路径是否正确。", input_path
            ));
        }

        // 5. 推断或使用指定的源格式
        let from_fmt = from_format.unwrap_or_else(|| {
            Self::detect_format_from_ext(input_path).unwrap_or("auto")
        });

        // 6. 构建 pandoc 命令参数
        let mut args = vec![
            input_path.to_string(),
            "-o".to_string(),
            output_path.clone(),
        ];

        // 添加 --from 和 --to 参数（pandoc 格式）
        args.push("--from".to_string());
        args.push(from_fmt.to_string());
        args.push("--to".to_string());
        args.push(to_format.to_string());

        tracing::info!("[doc_convert] 执行转换: {}({}) → {}({})", input_path, from_fmt, output_path, to_format);

        // 7. 记录输入文件大小
        let input_size = Self::file_size_bytes(input_path);

        // 8. 通过 CliExecutor 安全执行 pandoc 命令
        let result = CliExecutor::safe_exec("pandoc", &args);

        if !result.success {
            let err_msg = if result.stderr.is_empty() {
                format!("pandoc 执行失败 (退出码: {:?})", result.exit_code)
            } else {
                result.stderr.clone()
            };
            tracing::error!("[doc_convert] 转换失败: {}", err_msg);
            return PluginResult::err(format!(
                "文档转换失败:\n{}\n命令: pandoc {}",
                err_msg,
                args.join(" ")
            ));
        }

        // 9. 获取输出文件大小
        let output_size = Self::file_size_bytes(&output_path);
        let size_info = match (input_size, output_size) {
            (Some(in_s), Some(out_s)) => {
                format!(
                    "\n📁 输入文件: {:.1} KB\n📄 输出文件: {:.1} KB",
                    in_s as f64 / 1024.0,
                    out_s as f64 / 1024.0
                )
            }
            (Some(in_s), None) => {
                format!("\n📁 输入文件: {:.1} KB\n⚠️ 输出文件大小获取失败", in_s as f64 / 1024.0)
            }
            (_, Some(out_s)) => {
                format!("\n📄 输出文件: {:.1} KB", out_s as f64 / 1024.0)
            }
            _ => String::new(),
        };

        // 10. 返回成功结果
        let content = format!(
            "✅ 文档转换成功！\n\
             📂 源文件: {}\n\
             🎯 目标文件: {}\n\
             ⏱️  耗时: {} ms{}",
            input_path,
            output_path,
            result.duration_ms,
            size_info
        );

        tracing::info!("[doc_convert] 转换完成: {} → {}, 耗时 {}ms", input_path, output_path, result.duration_ms);

        PluginResult::ok_with_data(content, serde_json::json!({
            "status": "success",
            "input_path": input_path,
            "output_path": output_path,
            "from_format": from_fmt,
            "to_format": to_format,
            "duration_ms": result.duration_ms,
            "input_size_bytes": input_size,
            "output_size_bytes": output_size,
        }))
    }

    fn is_enabled(&self) -> bool { self.enabled }
    fn set_enabled(&mut self, enabled: bool) { self.enabled = enabled; }
}
