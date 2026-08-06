// GIF 制作技能 — 使用 ffmpeg 将视频片段转换为 GIF 动图

use async_trait::async_trait;
use crate::models::chat::ToolDefinition;
use crate::plugins::plugin_trait::{PluginContext, PluginResult, ToolPlugin};
use crate::agents::cli_executor::CliExecutor;

pub struct GifCreatePlugin { enabled: bool }

impl GifCreatePlugin {
    pub fn new() -> Self { GifCreatePlugin { enabled: true } }
}

#[async_trait]
impl ToolPlugin for GifCreatePlugin {
    fn name(&self) -> &str { "gif_create" }
    fn description(&self) -> &str { "将视频片段转为GIF动图，支持自定义尺寸、帧率、质量和调色板优化" }
    fn get_definition(&self) -> ToolDefinition {
        ToolDefinition {
            r#type: "function".to_string(),
            function: crate::models::chat::ToolFunction {
                name: "gif_create".to_string(),
                description: "将视频的指定片段转换为GIF动图。可控制尺寸、帧率、播放速度和质量".to_string(),
                parameters: serde_json::json!({
                    "type": "object",
                    "properties": {
                        "input_path": { "type": "string", "description": "源视频文件路径" },
                        "output_path": { "type": "string", "description": "输出GIF路径" },
                        "start_time": { "type": "string", "description": "开始时间（如 '00:01:30' 或 '5.5'）" },
                        "duration": { "type": "string", "description": "GIF时长（秒），默认3" },
                        "width": { "type": "integer", "description": "宽度像素，默认480" },
                        "fps": { "type": "integer", "description": "帧率，默认15" },
                        "optimize": { "type": "boolean", "description": "是否优化文件大小（默认true）" }
                    },
                    "required": ["input_path", "output_path"]
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
            _ => return PluginResult::err("缺少必要参数：output_path（输出GIF路径）"),
        };

        let start_time = arguments["start_time"].as_str().unwrap_or("00:00:00");
        let duration = arguments["duration"].as_str().unwrap_or("3");
        let width = arguments["width"].as_i64().unwrap_or(480);
        let fps = arguments["fps"].as_i64().unwrap_or(15);
        let optimize = arguments["optimize"].as_bool().unwrap_or(true);

        // 参数合理性校验
        let width_val = width.max(1).min(1920);  // 限制最大宽度
        let fps_val = fps.max(1).min(60);         // 限制合理帧率范围

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
            "[gif_create] input={}, output={}, start={}, dur={}, w={}, fps={}, opt={}",
            input_path, output_path, start_time, duration, width_val, fps_val, optimize
        );

        // 确定输出目录（用于存放临时调色板文件）
        let output_dir = std::path::Path::new(output_path)
            .parent()
            .map(|p| p.to_string_lossy().to_string())
            .unwrap_or_else(|| ".".to_string());

        // 调色板临时文件路径
        let palette_path = format!("{}\\__palette_temp_{}.png", output_dir, 
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap_or_default()
                .as_nanos()
        );

        let total_duration_ms: u128;

        if optimize {
            // ====== 最佳实践：两步法（调色板 + 优化）======

            // 第一步：生成调色板
            let scale_filter = format!(
                "fps={},scale={}:-1:flags=lanczos,palettegen",
                fps_val, width_val
            );
            let palette_args: Vec<String> = vec![
                "-y".to_string(),
                "-ss".to_string(), start_time.to_string(),
                "-t".to_string(), duration.to_string(),
                "-i".to_string(), input_path.to_string(),
                "-vf".to_string(), scale_filter.clone(),
                palette_path.clone(),
            ];

            tracing::info!("[gif_create] 第一步：生成调色板...");
            let palette_result = CliExecutor::safe_exec("ffmpeg", &palette_args);

            if !palette_result.success {
                // 清理临时调色板文件
                let _ = std::fs::remove_file(&palette_path);
                tracing::error!("[gif_create] 调色板生成失败: {}", palette_result.stderr);
                return PluginResult::err(&format!(
                    "GIF制作失败（调色板生成阶段）。\n\
                     错误信息：{}\n\
                     请检查输入文件是否存在、时间参数是否有效。",
                    palette_result.stderr
                ));
            }

            // 第二步：使用调色板生成优化的 GIF
            let lavfi_filter = format!(
                "fps={},scale={}:-1:flags=lanczos [x]; [x][1:v] paletteuse=dither=bayer:bayer_scale=3",
                fps_val, width_val
            );
            let gif_args: Vec<String> = vec![
                "-y".to_string(),
                "-ss".to_string(), start_time.to_string(),
                "-t".to_string(), duration.to_string(),
                "-i".to_string(), input_path.to_string(),
                "-i".to_string(), palette_path.clone(),
                "-lavfi".to_string(), lavfi_filter,
                output_path.to_string(),
            ];

            tracing::info!("[gif_create] 第二步：生成GIF...");
            let gif_result = CliExecutor::safe_exec("ffmpeg", &gif_args);
            total_duration_ms = gif_result.duration_ms + palette_result.duration_ms;

            // 清理临时调色板文件
            let _ = std::fs::remove_file(&palette_path);

            if !gif_result.success {
                tracing::error!("[gif_create] GIF生成失败: {}", gif_result.stderr);
                return PluginResult::err(&format!(
                    "GIF制作失败（GIF生成阶段）。\n\
                     错误信息：{}\n\
                     调色板已成功生成但最终合成失败。",
                    gif_result.stderr
                ));
            }
        } else {
            // ====== 简单模式：一步法（无优化）======
            let vf_filter = format!(
                "fps={},scale={}:-1",
                fps_val, width_val
            );
            let args: Vec<String> = vec![
                "-y".to_string(),
                "-ss".to_string(), start_time.to_string(),
                "-t".to_string(), duration.to_string(),
                "-i".to_string(), input_path.to_string(),
                "-vf".to_string(), vf_filter,
                output_path.to_string(),
            ];

            tracing::info!("[gif_create] 简单模式：直接生成GIF...");
            let result = CliExecutor::safe_exec("ffmpeg", &args);
            total_duration_ms = result.duration_ms;

            if !result.success {
                tracing::error!("[gif_create] GIF生成失败: {}", result.stderr);
                return PluginResult::err(&format!(
                    "GIF制作失败。\n\
                     错误信息：{}\n\
                     请检查输入文件是否存在、时间参数是否有效。",
                    result.stderr
                ));
            }
        }

        // 获取输出 GIF 文件信息
        let file_size = std::fs::metadata(output_path)
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

        // 尝试用 ffprobe 获取 GIF 的详细信息
        let mut gif_info = String::new();
        let probe_args: Vec<String> = vec![
            "-v".to_string(), "error".to_string(),
            "-count_frames".to_string(),
            "-select_streams".to_string(), "v:0".to_string(),
            "-show_entries".to_string(), "stream=nb_read_frames,width,height,r_frame_rate,duration".to_string(),
            "-of".to_string(), "json".to_string(),
            output_path.to_string(),
        ];
        let probe_result = CliExecutor::safe_exec("ffprobe", &probe_args);
        if probe_result.success && !probe_result.stdout.is_empty() {
            gif_info = probe_result.stdout.trim().to_string();
        }

        // 计算预估帧数
        let estimated_frames = (fps_val as f64 * duration.parse::<f64>().unwrap_or(3.0)).round() as i64;

        tracing::info!(
            "[gif_create] GIF制作完成，耗时 {}ms，文件大小 {}，优化={}",
            total_duration_ms, size_display, optimize
        );

        PluginResult::ok_with_data(
            format!(
                "✅ GIF 制作完成！\n\
                 📁 输入：{}\n\
                 🖼️ 输出：{}\n\
                 ⏱ 时间范围：{} 开始，持续 {} 秒\n\
                 📐 尺寸：{}px 宽（高度自适应）\n\
                 🎬 帧率：{} fps\n\
                 🎞️ 预估帧数：约 {} 帧\n\
                 💾 文件大小：{}\n\
                 🔧 优化模式：{}\n\
                 ⏱ 处理耗时：{}ms\n\
                 {}",
                input_path,
                output_path,
                start_time,
                duration,
                width_val,
                fps_val,
                estimated_frames,
                size_display,
                if optimize { "开启（调色板+抖动优化）" } else { "关闭（快速生成）" },
                total_duration_ms,
                if gif_info.is_empty() { "GIF文件已生成。".to_string() } else { format!("详细信息：\n{}", gif_info) }
            ),
            serde_json::json!({
                "status": "success",
                "input_path": input_path,
                "output_path": output_path,
                "start_time": start_time,
                "duration": duration,
                "width": width_val,
                "height": "auto",
                "fps": fps_val,
                "estimated_frames": estimated_frames,
                "optimize": optimize,
                "file_size_bytes": file_size,
                "file_size_display": size_display,
                "duration_ms": total_duration_ms,
                "gif_info": gif_info
            })
        )
    }

    fn is_enabled(&self) -> bool { self.enabled }
    fn set_enabled(&mut self, enabled: bool) { self.enabled = enabled; }
}
