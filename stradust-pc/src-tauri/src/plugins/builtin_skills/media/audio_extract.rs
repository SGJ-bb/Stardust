// 音频提取技能 — 使用 ffmpeg 从视频中提取音频并转换格式

use async_trait::async_trait;
use crate::models::chat::ToolDefinition;
use crate::plugins::plugin_trait::{PluginContext, PluginResult, ToolPlugin};
use crate::agents::cli_executor::CliExecutor;

/// 根据输出格式获取对应的 ffmpeg 音频编码器
fn get_audio_codec(format: &str) -> (&'static str, &'static str) {
    match format.to_lowercase().as_str() {
        "mp3"   => ("libmp3lame", ".mp3"),
        "aac"   => ("aac",       ".aac"),
        "flac"  => ("flac",      ".flac"),
        "wav"   => ("pcm_s16le", ".wav"),
        "ogg"   => ("libvorbis", ".ogg"),
        "m4a"   => ("aac",       ".m4a"),
        _       => ("libmp3lame", ".mp3"),  // 默认 mp3
    }
}

pub struct AudioExtractPlugin { enabled: bool }

impl AudioExtractPlugin {
    pub fn new() -> Self { AudioExtractPlugin { enabled: true } }
}

#[async_trait]
impl ToolPlugin for AudioExtractPlugin {
    fn name(&self) -> &str { "audio_extract" }
    fn description(&self) -> &str { "从视频中提取音频，支持转换为MP3/WAV/AAC/FLAC等格式" }
    fn get_definition(&self) -> ToolDefinition {
        ToolDefinition {
            r#type: "function".to_string(),
            function: crate::models::chat::ToolFunction {
                name: "audio_extract".to_string(),
                description: "从视频中提取音轨并保存为音频文件。可同时进行格式转换和音质调整".to_string(),
                parameters: serde_json::json!({
                    "type": "object",
                    "properties": {
                        "input_path": { "type": "string", "description": "源视频文件路径" },
                        "output_path": { "type": "string", "description": "输出音频文件路径" },
                        "format": {
                            "type": "string",
                            "enum": ["mp3", "wav", "aac", "flac", "ogg", "m4a"],
                            "description": "输出格式（默认mp3）"
                        },
                        "bitrate": { "type": "string", "description": "比特率如 '192k', '320k'" },
                        "sample_rate": { "type": "integer", "description": "采样率(Hz)，默认44100" }
                    },
                    "required": ["input_path"]
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

        let output_path = arguments["output_path"]
            .as_str()
            .filter(|s| !s.is_empty())
            .map(|s| s.to_string());

        let fmt = arguments["format"].as_str().unwrap_or("mp3");
        let bitrate = arguments["bitrate"].as_str();
        let sample_rate = arguments["sample_rate"].as_i64();

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

        tracing::info!("[audio_extract] input={}, format={}", input_path, fmt);

        // 获取编码器和扩展名
        let (codec, default_ext) = get_audio_codec(fmt);

        // 确定最终输出路径
        let final_output = match output_path {
            Some(path) => path,
            None => {
                // 根据输入路径生成输出路径（替换扩展名）
                if let Some(stem) = std::path::Path::new(input_path).file_stem() {
                    format!("{}{}", stem.to_string_lossy(), default_ext)
                } else {
                    format!("output{}", default_ext)
                }
            }
        };

        // 构建 ffmpeg 命令参数
        let mut args: Vec<String> = vec![
            "-y".to_string(),           // 覆盖已有文件
            "-i".to_string(),          // 输入文件
            input_path.to_string(),
            "-vn".to_string(),         // 不包含视频流
            "-acodec".to_string(),     // 音频编码器
            codec.to_string(),
        ];

        // 比特率参数（无损格式不需要）
        if matches!(fmt, "mp3" | "aac" | "ogg") {
            args.push("-ab".to_string());
            args.push(bitrate.unwrap_or("192k").to_string());
        }

        // 采样率参数
        if let Some(sr) = sample_rate {
            args.push("-ar".to_string());
            args.push(sr.to_string());
        }

        // 输出文件路径
        args.push(final_output.clone());

        // 执行 ffmpeg 命令
        let result = CliExecutor::safe_exec("ffmpeg", &args);

        if !result.success {
            tracing::error!(
                "[audio_extract] ffmpeg 执行失败: {} (exit_code={:?})",
                result.stderr, result.exit_code
            );
            return PluginResult::err(&format!(
                "音频提取失败。\n\
                 错误信息：{}\n\
                 退出码：{:?}\n\
                 请检查输入文件是否为有效音视频文件。",
                result.stderr, result.exit_code
            ));
        }

        // 使用 ffprobe 获取输出音频信息
        let mut audio_info = String::new();
        let probe_args: Vec<String> = vec![
            "-v".to_string(), "quiet".to_string(),
            "-show_entries".to_string(),
            "format=duration,size:stream=codec_name,sample_rate,channels,bit_rate".to_string(),
            "-of".to_string(), "json".to_string(),
            final_output.clone(),
        ];
        let probe_result = CliExecutor::safe_exec("ffprobe", &probe_args);
        if probe_result.success && !probe_result.stdout.is_empty() {
            audio_info = format!("音频文件信息：\n{}", probe_result.stdout.trim());
        }

        // 获取文件大小
        let file_size = std::fs::metadata(&final_output)
            .ok()
            .map(|m| m.len())
            .unwrap_or(0);
        let size_display = if file_size >= 1024 * 1024 {
            format!("{:.2} MB", file_size as f64 / (1024.0 * 1024.0))
        } else if file_size >= 1024 {
            format!("{:.2} KB", file_size as f64 / 1024.0)
        } else {
            format!("{} B", file_size)
        };

        tracing::info!("[audio_extract] 提取完成，耗时 {}ms，文件大小 {}", result.duration_ms, size_display);

        PluginResult::ok_with_data(
            format!(
                "✅ 音频提取完成！\n\
                 📁 输入：{}\n\
                 🎵 输出：{}\n\
                 🎼 格式：{}（编码器：{}）\n\
                 🔊 比特率：{}\n\
                 💾 文件大小：{}\n\
                 ⏱ 处理耗时：{}ms\n\
                 {}",
                input_path,
                final_output,
                fmt.to_uppercase(),
                codec,
                bitrate.unwrap_or(if matches!(fmt, "mp3") { "192k" } else { "自动" }),
                size_display,
                result.duration_ms,
                if audio_info.is_empty() { "音频文件已生成。".to_string() } else { audio_info }
            ),
            serde_json::json!({
                "status": "success",
                "input_path": input_path,
                "output_path": final_output,
                "format": fmt,
                "codec": codec,
                "bitrate": bitrate.unwrap_or("192k"),
                "file_size_bytes": file_size,
                "file_size_display": size_display,
                "duration_ms": result.duration_ms,
                "audio_info": audio_info
            })
        )
    }

    fn is_enabled(&self) -> bool { self.enabled }
    fn set_enabled(&mut self, enabled: bool) { self.enabled = enabled; }
}
