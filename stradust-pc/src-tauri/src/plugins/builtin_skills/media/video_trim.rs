// 视频裁剪技能 — 使用 ffmpeg 实现视频片段裁剪

use async_trait::async_trait;
use crate::models::chat::ToolDefinition;
use crate::plugins::plugin_trait::{PluginContext, PluginResult, ToolPlugin};
use crate::agents::cli_executor::CliExecutor;

pub struct VideoTrimPlugin { enabled: bool }

impl VideoTrimPlugin {
    pub fn new() -> Self { VideoTrimPlugin { enabled: true } }
}

#[async_trait]
impl ToolPlugin for VideoTrimPlugin {
    fn name(&self) -> &str { "video_trim" }
    fn description(&self) -> &str { "视频裁剪与片段提取：按时间范围截取视频片段，支持多种格式" }
    fn get_definition(&self) -> ToolDefinition {
        ToolDefinition {
            r#type: "function".to_string(),
            function: crate::models::chat::ToolFunction {
                name: "video_trim".to_string(),
                description: "按时间范围裁剪视频，提取指定片段。支持 MP4/MKV/AVI/WebM 等格式".to_string(),
                parameters: serde_json::json!({
                    "type": "object",
                    "properties": {
                        "input_path": { "type": "string", "description": "源视频文件路径" },
                        "output_path": { "type": "string", "description": "输出文件路径" },
                        "start_time": { "type": "string", "description": "开始时间，如 '00:01:30' 或秒数" },
                        "end_time": { "type": "string", "description": "结束时间，如 '00:05:00' 或时长如 '00:03:30'" },
                        "codec": {
                            "type": "string",
                            "enum": ["h264", "h265", "copy"],
                            "description": "编码方式（copy=无重编码，最快）"
                        },
                        "resolution": { "type": "string", "description": "输出分辨率如 '1920x1080'" },
                        "crf": { "type": "integer", "description": "画质质量(0-51, 越小越好)" }
                    },
                    "required": ["input_path", "output_path", "start_time"]
                }),
            },
        }
    }

    async fn execute(&self, arguments: &serde_json::Value, _context: &PluginContext) -> PluginResult {
        // 解析参数
        let input_path = match arguments["input_path"].as_str() {
            Some(s) if !s.is_empty() => s,
            _ => return PluginResult::err("缺少必要参数：input_path（源视频文件路径）"),
        };
        let output_path = match arguments["output_path"].as_str() {
            Some(s) if !s.is_empty() => s,
            _ => return PluginResult::err("缺少必要参数：output_path（输出文件路径）"),
        };
        let start_time = arguments["start_time"].as_str().unwrap_or("00:00:00");
        let end_time = arguments["end_time"].as_str().unwrap_or("");
        let codec = arguments["codec"].as_str().unwrap_or("copy");
        let resolution = arguments["resolution"].as_str();
        let crf = arguments["crf"].as_i64();

        // 检查 ffmpeg 是否可用
        if !CliExecutor::check_available("ffmpeg") {
            return PluginResult::err(
                "该技能需要安装 ffmpeg。\n\
                 安装方式：\n\
                 - Windows: winget install ffmpeg\n\
                 - macOS: brew install ffmpeg\n\
                 - Linux: apt install ffmpeg\n\n\
                 安装后重启星尘即可使用此技能。"
            );
        }

        tracing::info!(
            "[video_trim] input={}, output={}, start={}, end={}, codec={}",
            input_path, output_path, start_time,
            if end_time.is_empty() { "至结尾" } else { end_time },
            codec
        );

        // 构建 ffmpeg 命令参数
        // 注意：-ss 放在 -i 前面（seek to input）速度更快
        let mut args: Vec<String> = vec![
            "-y".to_string(),                    // 覆盖已有文件
            "-ss".to_string(), start_time.to_string(),  // 起始时间（seek）
        ];

        // 如果有结束时间，使用 -to 参数
        if !end_time.is_empty() {
            args.push("-to".to_string());
            args.push(end_time.to_string());
        }

        // 输入文件
        args.push("-i".to_string());
        args.push(input_path.to_string());

        // 视频编码器
        match codec {
            "h264" => {
                args.push("-c:v".to_string());
                args.push("libx264".to_string());
            }
            "h265" => {
                args.push("-c:v".to_string());
                args.push("libx265".to_string());
            }
            _ => {
                // copy 模式
                args.push("-c:v".to_string());
                args.push("copy".to_string());
            }
        }

        // CRF 参数（仅在非 copy 模式下生效）
        if codec != "copy" {
            if let Some(crf_val) = crf {
                let crf_clamped = crf_val.max(0).min(51);
                args.push("-crf".to_string());
                args.push(crf_clamped.to_string());
            } else {
                // 默认 CRF 23（质量与体积的良好平衡）
                args.push("-crf".to_string());
                args.push("23".to_string());
            }
        }

        // 分辨率参数
        if let Some(res) = resolution {
            if !res.is_empty() {
                args.push("-s".to_string());
                args.push(res.to_string());
            }
        }

        // 音频也用 copy（保持原音轨）
        args.push("-c:a".to_string());
        args.push("copy".to_string());

        // 输出文件路径
        args.push(output_path.to_string());

        // 执行 ffmpeg 命令
        let result = CliExecutor::safe_exec("ffmpeg", &args);

        if !result.success {
            tracing::error!("[video_trim] ffmpeg 执行失败: {} (exit_code={:?})", result.stderr, result.exit_code);
            return PluginResult::err(&format!(
                "视频裁剪失败。\n\
                 错误信息：{}\n\
                 退出码：{:?}\n\
                 请检查输入文件是否存在、时间范围是否有效。",
                result.stderr, result.exit_code
            ));
        }

        // 尝试获取输出文件信息（使用 ffprobe）
        let mut output_info = String::new();
        let probe_args: Vec<String> = vec![
            "-v".to_string(), "quiet".to_string(),
            "-show_entries".to_string(), "format=duration,size:stream=width,height,codec_name".to_string(),
            "-of".to_string(), "json".to_string(),
            output_path.to_string(),
        ];
        let probe_result = CliExecutor::safe_exec("ffprobe", &probe_args);
        if probe_result.success && !probe_result.stdout.is_empty() {
            output_info = format!("输出文件信息：\n{}", probe_result.stdout.trim());
        }

        let end_display = if end_time.is_empty() { "至结尾".to_string() } else { end_time.to_string() };

        tracing::info!("[video_trim] 裁剪完成，耗时 {}ms", result.duration_ms);

        PluginResult::ok_with_data(
            format!(
                "✅ 视频裁剪完成！\n\
                 📁 输入：{}\n\
                 📁 输出：{}\n\
                 ⏱ 时间范围：{} → {}\n\
                 🎬 编码方式：{}{}\n\
                 ⏱ 处理耗时：{}ms\n\
                 {}",
                input_path,
                output_path,
                start_time,
                end_display,
                codec,
                if let Some(res) = resolution { format!("，分辨率：{}", res) } else { String::new() },
                result.duration_ms,
                if output_info.is_empty() { "输出文件已生成。".to_string() } else { output_info }
            ),
            serde_json::json!({
                "status": "success",
                "input_path": input_path,
                "output_path": output_path,
                "start_time": start_time,
                "end_time": end_time,
                "codec": codec,
                "resolution": resolution,
                "duration_ms": result.duration_ms,
                "file_info": output_info
            })
        )
    }

    fn is_enabled(&self) -> bool { self.enabled }
    fn set_enabled(&mut self, enabled: bool) { self.enabled = enabled; }
}
