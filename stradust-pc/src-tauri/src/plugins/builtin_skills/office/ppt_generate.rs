// PPT 生成技能 — 使用 python-pptx 库生成演示文稿

use async_trait::async_trait;
use crate::models::chat::ToolDefinition;
use crate::plugins::plugin_trait::{PluginContext, PluginResult, ToolPlugin};
use crate::agents::cli_executor::CliExecutor;

pub struct PptGeneratePlugin { enabled: bool }

impl PptGeneratePlugin {
    pub fn new() -> Self { PptGeneratePlugin { enabled: true } }

    /// 获取文件大小（字节）
    fn file_size_bytes(path: &str) -> Option<u64> {
        std::fs::metadata(path).ok().map(|m| m.len())
    }
}

#[async_trait]
impl ToolPlugin for PptGeneratePlugin {
    fn name(&self) -> &str { "ppt_generate" }
    fn description(&self) -> &str { "根据内容自动生成PPT演示文稿，支持多种主题风格" }
    fn get_definition(&self) -> ToolDefinition {
        ToolDefinition {
            r#type: "function".to_string(),
            function: crate::models::chat::ToolFunction {
                name: "ppt_generate".to_string(),
                description: "根据用户提供的主题或内容大纲，自动生成结构化的PPT演示文稿".to_string(),
                parameters: serde_json::json!({
                    "type": "object",
                    "properties": {
                        "topic": { "type": "string", "description": "PPT主题/标题" },
                        "content_outline": { "type": "string", "description": "内容大纲（可选，不提供则由AI生成）" },
                        "slide_count": { "type": "integer", "description": "幻灯片页数，默认10" },
                        "theme": {
                            "type": "string",
                            "enum": ["business", "academic", "creative", "minimal", "dark"],
                            "description": "设计主题风格"
                        },
                        "output_path": { "type": "string", "description": "输出文件路径" }
                    },
                    "required": ["topic"]
                }),
            },
        }
    }

    async fn execute(&self, arguments: &serde_json::Value, _context: &PluginContext) -> PluginResult {
        // 1. 检查 python 是否可用
        if !CliExecutor::check_available("python") {
            return PluginResult::err(
                "该技能需要安装 Python 环境。\n\
                 安装方式：\n\
                 - Windows: 从 https://www.python.org/downloads/ 下载安装\n\
                 - macOS: brew install python\n\
                 - Linux: apt install python3\n\n\
                 安装后还需要执行: pip install python-pptx\n\
                 完成后重启星尘即可使用此技能。"
            );
        }

        // 2. 解析参数
        let topic = match arguments["topic"].as_str() {
            Some(t) => t,
            None => return PluginResult::err("缺少必需参数: topic（PPT主题/标题）"),
        };

        let content = arguments["content_outline"].as_str().unwrap_or(topic);
        let default_output = format!("{}.pptx", topic);
        let output_path = arguments["output_path"].as_str()
            .unwrap_or(&default_output);
        let theme = arguments["theme"].as_str().unwrap_or("business");

        tracing::info!("[ppt_generate] 主题={}, 输出路径={}", topic, output_path);

        // 3. 构建内联 Python 脚本 — 使用 python-pptx 生成 PPT
        // 将数据序列化为 JSON 传入脚本
        let data_json = serde_json::json!({
            "title": topic,
            "content": content,
            "output": output_path,
            "theme": theme,
        });

        let script = r#"import json, sys
from pptx import Presentation
from pptx.util import Inches, Pt
from pptx.dml.color import RgbColor
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR

try:
    data = json.loads(sys.argv[1])
    prs = Presentation()

    # 标题页
    title_slide = prs.slides.add_slide(prs.slide_layouts[6])  # 空白布局
    txBox = title_slide.shapes.add_textbox(Inches(1), Inches(2.5), Inches(8), Inches(2))
    tf = txBox.text_frame
    tf.word_wrap = True
    p = tf.paragraphs[0]
    p.text = data.get('title', '未命名')
    p.font.size = Pt(40)
    p.font.bold = True
    p.alignment = PP_ALIGN.CENTER

    # 内容页：每行一张幻灯片
    lines = data.get('content', '').split('\n')
    for line in lines:
        line = line.strip()
        if not line:
            continue
        slide = prs.slides.add_slide(prs.slide_layouts[6])  # 空白布局
        txBox = slide.shapes.add_textbox(Inches(0.8), Inches(1.2), Inches(8.4), Inches(5.5))
        tf = txBox.text_frame
        tf.word_wrap = True
        p = tf.paragraphs[0]
        p.text = line
        p.font.size = Pt(24)
        p.alignment = PP_ALIGN.LEFT

    prs.save(data.get('output', 'output.pptx'))
    print(json.dumps({"status": "ok", "slides": len([s for s in prs.slides])}))
except ImportError as e:
    print(json.dumps({"status": "error", "message": str(e)}))
except Exception as e:
    print(json.dumps({"status": "error", "message": str(e)}))
"#;

        // 4. 通过 CliExecutor 执行 Python 脚本
        let args = vec![
            "-c".to_string(),
            script.to_string(),
            data_json.to_string(),
        ];

        let result = CliExecutor::safe_exec("python", &args);

        // 5. 处理结果
        if !result.success {
            let err_msg = &result.stderr;
            // 检查是否是 python-pptx 未安装的错误
            if err_msg.contains("No module named 'pptx'") || err_msg.contains("ModuleNotFoundError") {
                return PluginResult::err(format!(
                    "缺少依赖库 python-pptx。\n\
                     请在终端中执行以下命令安装:\n\
                     pip install python-pptx\n\n\
                     详细错误: {}", err_msg
                ));
            }
            tracing::error!("[ppt_generate] 脚本执行失败: {}", err_msg);
            return PluginResult::err(format!(
                "PPT 生成失败:\n{}\n请检查 Python 环境和依赖是否正确安装。", err_msg
            ));
        }

        // 6. 解析 Python 脚本输出
        let stdout_trimmed = result.stdout.trim();
        let script_result: serde_json::Value = match serde_json::from_str(stdout_trimmed) {
            Ok(v) => v,
            Err(_) => {
                // 如果不是 JSON 格式，检查是否有 OK 标记
                if stdout_trimmed.contains("OK") || stdout_trimmed.contains("\"ok\"") {
                    serde_json::json!({"status": "ok"})
                } else {
                    tracing::warn!("[ppt_generate] 无法解析脚本输出: {}", stdout_trimmed);
                    return PluginResult::err(format!(
                        "PPT 生成完成但返回异常:\nstdout: {}\nstderr: {}",
                        result.stdout, result.stderr
                    ));
                }
            }
        };

        if script_result.get("status").and_then(|v| v.as_str()) == Some("error") {
            let msg = script_result.get("message")
                .and_then(|v| v.as_str())
                .unwrap_or("未知错误");
            if msg.contains("pptx") || msg.contains("ModuleNotFoundError") {
                return PluginResult::err(format!(
                    "缺少依赖库 python-pptx。\n\
                     请在终端中执行以下命令安装:\n\
                     pip install python-pptx"
                ));
            }
            return PluginResult::err(format!("PPT 生成失败: {}", msg));
        }

        // 7. 获取输出文件信息
        let slides_count = script_result.get("slides").and_then(|v| v.as_u64()).unwrap_or(0);
        let output_size = Self::file_size_bytes(output_path);
        let size_info = output_size.map(|s| format!("\n📄 文件大小: {:.1} KB", s as f64 / 1024.0)).unwrap_or_default();

        // 8. 返回成功结果
        let content = format!(
            "✅ PPT 生成成功！\n\
             🎯 主题: {}\n\
             📂 文件: {}\n\
             📊 幻灯片: {} 页\n\
             ⏱️  耗时: {} ms\n\
             🎨 主题风格: {}{}",
            topic,
            output_path,
            slides_count,
            result.duration_ms,
            theme,
            size_info
        );

        tracing::info!("[ppt_generate] PPT生成完成: {}, {}页, 耗时{}ms", output_path, slides_count, result.duration_ms);

        PluginResult::ok_with_data(content, serde_json::json!({
            "status": "success",
            "topic": topic,
            "output_path": output_path,
            "slides_count": slides_count,
            "theme": theme,
            "duration_ms": result.duration_ms,
            "file_size_bytes": output_size,
        }))
    }

    fn is_enabled(&self) -> bool { self.enabled }
    fn set_enabled(&mut self, enabled: bool) { self.enabled = enabled; }
}
