// 字幕生成技能 — 使用 whisper 进行语音识别生成字幕

use async_trait::async_trait;
use crate::models::chat::ToolDefinition;
use crate::plugins::plugin_trait::{PluginContext, PluginResult, ToolPlugin};
use crate::agents::cli_executor::CliExecutor;

/// 检测可用的 whisper 命令（按优先级尝试）
fn detect_whisper_command() -> Option<String> {
    // 优先级1: whisper-cli (独立安装的 CLI 工具)
    if CliExecutor::check_available("whisper-cli") {
        tracing::info!("[subtitle_gen] 检测到 whisper-cli");
        return Some("whisper-cli".to_string());
    }

    // 优先级2: whisper (openai-whisper 的命令行入口)
    if CliExecutor::check_available("whisper") {
        tracing::info!("[subtitle_gen] 检测到 whisper 命令");
        return Some("whisper".to_string());
    }

    // 优先级3: python -m whisper (Python 包方式)
    if CliExecutor::check_available("python") {
        // 尝试执行 python -m whisper --help 来验证
        let result = CliExecutor::safe_exec(
            "python",
            &["-m".to_string(), "whisper".to_string(), "--help".to_string()],
        );
        if result.success || result.stdout.contains("whisper") || result.stderr.contains("whisper") {
            tracing::info!("[subtitle_gen] 检测到 python -m whisper");
            return Some("python".to_string());
        }
    }

    None
}

pub struct SubtitleGenPlugin { enabled: bool }

impl SubtitleGenPlugin {
    pub fn new() -> Self { SubtitleGenPlugin { enabled: true } }
}

#[async_trait]
impl ToolPlugin for SubtitleGenPlugin {
    fn name(&self) -> &str { "subtitle_gen" }
    fn description(&self) -> &str { "语音/视频自动生成字幕，支持多语言识别和SRT/VTT格式导出" }
    fn get_definition(&self) -> ToolDefinition {
        ToolDefinition {
            r#type: "function".to_string(),
            function: crate::models::chat::ToolFunction {
                name: "subtitle_gen".to_string(),
                description: "对视频或音频文件进行语音识别，自动生成字幕文件。支持中英文及多种语言".to_string(),
                parameters: serde_json::json!({
                    "type": "object",
                    "properties": {
                        "file_path": { "type": "string", "description": "视频/音频文件路径" },
                        "language": {
                            "type": "string",
                            "enum": ["zh", "en", "ja", "ko", "auto"],
                            "description": "语言（auto=自动检测）"
                        },
                        "output_format": {
                            "type": "string",
                            "enum": ["srt", "vtt", "ass", "txt"],
                            "description": "字幕格式（默认srt）"
                        },
                        "output_path": { "type": "string", "description": "输出字幕文件路径" },
                        "model_size": {
                            "type": "string",
                            "enum": ["tiny", "base", "small", "medium", "large"],
                            "description": "模型大小（默认base，越大越准但越慢）"
                        }
                    },
                    "required": ["file_path"]
                }),
            },
        }
    }

    async fn execute(&self, arguments: &serde_json::Value, _context: &PluginContext) -> PluginResult {
        // 解析参数
        let file_path = match arguments["file_path"].as_str() {
            Some(s) if !s.is_empty() => s,
            _ => return PluginResult::err("缺少必要参数：file_path（视频/音频文件路径）"),
        };

        let language = arguments["language"].as_str().unwrap_or("zh");
        let output_format = arguments["output_format"].as_str().unwrap_or("srt");
        let model_size = arguments["model_size"].as_str().unwrap_or("base");
        let custom_output_path = arguments["output_path"]
            .as_str()
            .filter(|s| !s.is_empty())
            .map(|s| s.to_string());

        // 检查输入文件是否存在
        if !std::path::Path::new(file_path).exists() {
            return PluginResult::err(&format!("输入文件不存在：{}", file_path));
        }

        // 检测 whisper 可用性
        let whisper_cmd = match detect_whisper_command() {
            Some(cmd) => cmd,
            None => {
                return PluginResult::err(
                    "该技能需要安装 whisper 语音识别工具。\n\n\
                     推荐安装方式（openai-whisper，Python 包）：\n\
                     \n\
                     1️⃣ 安装 Python 3.7+ （如未安装）\n\
                     2️⃣ 执行以下命令安装：\n\
                     pip install openai-whisper\n\
                     \n\
                     首次运行时会自动下载模型文件。\n\
                     模型大小说明：\n\
                     • tiny   — 最快，准确率一般\n\
                     • base   — 推荐，速度与精度平衡\n\
                     • small  — 更精确\n\
                     • medium — 高精度\n\
                     • large  — 最高精度，最慢\n\
                     \n\
                     其他安装选项：\n\
                     - macOS: brew install openai-whisper\n\
                     - 也可使用 faster-whisper（更快）：pip install faster-whisper\n\n\
                     安装后重启星尘即可使用此技能。"
                );
            }
        };

        tracing::info!(
            "[subtitle_gen] file={}, lang={}, format={}, model={}, cmd={}",
            file_path, language, output_format, model_size, whisper_cmd
        );

        // 确定输出目录和输出文件名
        let input_file = std::path::Path::new(file_path);
        let stem = input_file.file_stem()
            .map(|s| s.to_string_lossy().to_string())
            .unwrap_or_else(|| "output".to_string());

        // 如果用户指定了输出路径，使用指定的目录；否则使用输入文件所在目录
        let output_dir = if let Some(ref custom) = custom_output_path {
            std::path::Path::new(custom)
                .parent()
                .map(|p| p.to_string_lossy().to_string())
                .unwrap_or_else(|| ".".to_string())
        } else {
            input_file.parent()
                .map(|p| p.to_string_lossy().to_string())
                .unwrap_or_else(|| ".".to_string())
        };

        // 构建 whisper 命令参数
        let mut args: Vec<String> = Vec::new();

        // 如果是 python -m whisper 方式，需要加前缀参数
        if whisper_cmd == "python" {
            args.push("-m".to_string());
            args.push("whisper".to_string());
        }

        // 输入文件
        args.push(file_path.to_string());

        // 语言参数
        let lang_arg = if language == "auto" {
            String::new()  // whisper 默认自动检测
        } else {
            language.to_string()
        };
        if !lang_arg.is_empty() {
            args.push("--language".to_string());
            args.push(lang_arg);
        }

        // 模型大小
        args.push("--model".to_string());
        args.push(model_size.to_string());

        // 输出格式
        args.push("--output_format".to_string());
        args.push(output_format.to_string());

        // 输出目录
        args.push("--output_dir".to_string());
        args.push(output_dir.clone());

        // 执行 whisper 命令
        let start_time = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap_or_default()
            .as_millis();

        let result = CliExecutor::safe_exec(&whisper_cmd, &args);

        if !result.success {
            tracing::error!(
                "[subtitle_gen] whisper 执行失败: {} (exit_code={:?})",
                result.stderr, result.exit_code
            );

            // 针对常见错误给出更友好的提示
            let error_hint = if result.stderr.contains("Model") && result.stderr.contains("not found") {
                "\n💡 提示：模型文件未找到。首次使用会自动下载模型，请确保网络畅通。\n   可手动指定模型路径或更换已下载的模型大小。"
            } else if result.stderr.contains("No such file") || result.stderr.contains("not found") {
                "\n💡 提示：请检查输入文件路径是否正确。"
            } else if result.stderr.contains("out of memory") || result.stderr.contains("CUDA") {
                "\n💡 提示：显存不足。建议使用更小的模型（如 tiny 或 base），或在 CPU 模式下运行。"
            } else {
                ""
            };

            return PluginResult::err(&format!(
                "字幕生成失败。\n\
                 使用命令：{}\n\
                 错误信息：{}{}\n\
                 退出码：{:?}",
                whisper_cmd,
                result.stderr,
                error_hint,
                result.exit_code
            ));
        }

        // 确定生成的字幕文件路径
        let generated_path = custom_output_path.unwrap_or_else(|| {
            format!("{}\\{}.{}", output_dir, stem, output_format)
        });

        // 检查输出文件是否真的生成了
        let file_exists = std::path::Path::new(&generated_path).exists();
        let file_size = if file_exists {
            std::fs::metadata(&generated_path).ok().map(|m| m.len()).unwrap_or(0)
        } else {
            0
        };

        // 解析 whisper 输出中的关键信息
        let duration_info = extract_duration_from_whisper_output(&result.stdout, &result.stderr);

        tracing::info!(
            "[subtitle_gen] 字幕生成完成，耗时 {}ms，输出文件={} ({} bytes)",
            result.duration_ms,
            generated_path,
            file_size
        );

        PluginResult::ok_with_data(
            format!(
                "✅ 字幕生成完成！\n\
                 📁 输入文件：{}\n\
                 📝 字幕文件：{}\n\
                 🌍 识别语言：{}\n\
                 📐 字幕格式：{}\n\
                 🧠 模型大小：{}\n\
                 {}\
                 ⏱ 处理耗时：{}ms\n\
                 {}",
                file_path,
                generated_path,
                if language == "auto" { "自动检测" } else { language },
                output_format.to_uppercase(),
                model_size,
                duration_info,
                result.duration_ms,
                if file_exists {
                    format!("💾 文件大小：{} B", file_size)
                } else {
                    "⚠️ 请检查输出目录确认字幕文件是否已生成".to_string()
                }
            ),
            serde_json::json!({
                "status": "success",
                "input_file": file_path,
                "output_file": generated_path,
                "language": language,
                "format": output_format,
                "model_size": model_size,
                "whisper_command": whisper_cmd,
                "file_exists": file_exists,
                "file_size_bytes": file_size,
                "duration_ms": result.duration_ms,
                "duration_info": duration_info,
                "stdout_preview": if result.stdout.len() > 500 {
                    format!("{}...(截断)", &result.stdout[..500])
                } else {
                    result.stdout.clone()
                }
            })
        )
    }

    fn is_enabled(&self) -> bool { self.enabled }
    fn set_enabled(&mut self, enabled: bool) { self.enabled = enabled; }
}

/// 从 whisper 输出中提取时长等关键信息
fn extract_duration_from_whisper_output(stdout: &str, stderr: &str) -> String {
    let combined = format!("{} {}", stdout, stderr);

    // 尝试匹配 "Detected language: ..." 模式
    if let Some(pos) = combined.find("Detected language:") {
        if let Some(end) = combined[pos..].find('\n') {
            let info = combined[pos..pos + end].trim();
            if !info.is_empty() {
                return format!("🔍 {}\n", info);
            }
        }
    }

    // 尝试匹配时长信息（各版本 whisper 格式不同）
    for pattern in &[
        "Processing ",
        "seconds of audio",
        "audio duration",
        "[00:",
    ] {
        if let Some(pos) = combined.find(pattern) {
            let start = pos.saturating_sub(20);
            let end = (pos + pattern.len() + 80).min(combined.len());
            let snippet = combined[start..end].replace('\n', " ").trim().to_string();
            if !snippet.is_empty() {
                return format!("ℹ️ {}\n", snippet);
            }
        }
    }

    String::new()
}
