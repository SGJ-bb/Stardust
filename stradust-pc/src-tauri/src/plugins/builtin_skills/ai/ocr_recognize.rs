// OCR 文字识别技能
// 使用 Tesseract OCR 引擎进行图片文字识别

use async_trait::async_trait;
use crate::models::chat::ToolDefinition;
use crate::plugins::plugin_trait::{PluginContext, PluginResult, ToolPlugin};
use crate::agents::cli_executor::CliExecutor;

pub struct OcrRecognizePlugin { enabled: bool }

impl OcrRecognizePlugin {
    pub fn new() -> Self { OcrRecognizePlugin { enabled: true } }

    /// 将语言数组参数转换为 Tesseract 的语言字符串（如 "chi_sim+eng"）
    fn resolve_lang(languages: &Option<Vec<String>>) -> String {
        match languages {
            Some(langs) if !langs.is_empty() => langs.join("+"),
            _ => "chi_sim+eng".to_string(), // 默认中英混合
        }
    }

    /// 获取语言的可读名称
    fn lang_display_name(lang: &str) -> String {
        match lang {
            "chi_sim" => "简体中文".to_string(),
            "chi_tra" => "繁体中文".to_string(),
            "eng" => "英语".to_string(),
            "jpn" => "日语".to_string(),
            "kor" => "韩语".to_string(),
            "fra" => "法语".to_string(),
            "deu" => "德语".to_string(),
            "spa" => "西班牙语".to_string(),
            l if l.contains('+') && l.contains("chi_sim") => "简体中文 + 其他语言".to_string(),
            l if l.contains('+') && l.contains("eng") => "英语 + 其他语言".to_string(),
            _ => lang.to_string(),
        }
    }

    /// 解析 tesseract stderr 中的置信度信息
    fn parse_confidence(stderr: &str) -> Option<f64> {
        // tesseract 输出格式: "Tesseract Open Source OCR Engine v5.x.x with Leptonica"
        // 置信度通常在 stderr 中以类似形式出现，但标准 stdout 模式下不直接输出置信度
        // 尝试从 stderr 中提取
        for line in stderr.lines() {
            let lower = line.to_lowercase();
            if lower.contains("confidence") || lower.contains("conf") {
                // 尝试提取数字
                if let Some(num_str) = line
                    .chars()
                    .filter(|c| c.is_ascii_digit() || *c == '.' || *c == '-')
                    .collect::<String>()
                    .parse::<f64>()
                    .ok()
                {
                    return Some(num_str);
                }
            }
        }
        None
    }

    /// 检查指定的语言包是否已安装
    fn check_lang_available(lang: &str) -> Result<(), String> {
        // 使用 tesseract --list-langs 检查可用语言
        let result = CliExecutor::safe_exec(
            "tesseract",
            &["--list-langs".to_string()],
        );

        if !result.success {
            // --list-langs 可能返回非零但仍有输出
            if result.stdout.is_empty() {
                return Err("无法获取已安装的语言列表".to_string());
            }
        }

        // 检查每种语言是否可用
        for part in lang.split('+') {
            if part.is_empty() { continue; }
            if !result.stdout.contains(part) {
                return Err(format!(
                    "语言包 '{}' 未安装。\n\
                     可用命令安装:\n\
                     - Windows (winget): 先安装主程序，再下载对应语言包\n\
                     - Windows (手动): https://github.com/UB-Mannheim/tesseract/wiki\n\n\
                     已安装的语言:\n{}",
                    part,
                    if result.stdout.is_empty() { "(无法获取)" } else { &result.stdout }
                ));
            }
        }

        Ok(())
    }
}

#[async_trait]
impl ToolPlugin for OcrRecognizePlugin {
    fn name(&self) -> &str { "ocr_recognize" }
    fn description(&self) -> &str { "图片文字识别(OCR)：提取图片中的文字，支持中英文混合、表格、手写体" }
    fn get_definition(&self) -> ToolDefinition {
        ToolDefinition {
            r#type: "function".to_string(),
            function: crate::models::chat::ToolFunction {
                name: "ocr_recognize".to_string(),
                description: "从图片中提取文字内容。支持扫描件、截图、照片中的印刷体和手写体中文/英文/日文等".to_string(),
                parameters: serde_json::json!({
                    "type": "object",
                    "properties": {
                        "image_path": { "type": "string", "description": "图片文件路径（支持 png/jpg/bmp/tiff/webp）" },
                        "lang": {
                            "type": "string",
                            "enum": ["chi_sim+eng", "eng", "chi_sim", "chi_tra+jpn", "jpn", "kor"],
                            "description": "识别语言，默认 chi_sim+eng（中文+英文）"
                        },
                        "output_format": {
                            "type": "string",
                            "enum": ["text", "json"],
                            "description": "输出格式：text=纯文本, json=结构化数据（含元信息）"
                        },
                        "psm": {
                            "type": "integer",
                            "description": "页面分割模式(0-13)，默认3=自动。6=单块文本, 11=稀疏文字"
                        }
                    },
                    "required": ["image_path"]
                }),
            },
        }
    }

    async fn execute(&self, arguments: &serde_json::Value, _context: &PluginContext) -> PluginResult {
        let image_path = arguments["image_path"].as_str().unwrap_or("");
        if image_path.is_empty() { return PluginResult::err("图片路径不能为空"); }

        let lang = arguments["lang"].as_str().unwrap_or("chi_sim+eng");
        let output_format = arguments["output_format"].as_str().unwrap_or("text");
        let psm = arguments["psm"].as_u64().unwrap_or(3);

        tracing::info!("[ocr_recognize] image={}, lang={}, format={}", image_path, lang, output_format);

        // 1. 检查 tesseract 是否可用
        if !CliExecutor::check_available("tesseract") {
            return PluginResult::err(format!(
                "❌ Tesseract OCR 引擎未安装或不在 PATH 中。\n\n\
                 🔧 安装方法：\n\
                 ┌─────────────────────────────────────────────┐\n\
                 │ Windows 推荐（一键安装）：                  │\n\
                 │   winget install UB-Mannheim.TesseractOCR   │\n\
                 │                                             │\n\
                 │ 或手动下载：                                │\n\
                 │   https://github.com/UB-Mannheim/tesseract │\n\
                 │   → Wiki → Download                        │\n\
                 │                                             │\n\
                 │ 安装时请勾选中文语言包！                     │\n\
                 └─────────────────────────────────────────────┘\n\n\
                 安装完成后重启终端/应用即可使用。"
            ));
        }

        // 2. 检查图片文件是否存在
        let path = std::path::Path::new(image_path);
        if !path.exists() {
            return PluginResult::err(format!(
                "❌ 图片文件不存在: {}\n\n\
                 请检查：\n\
                 1. 文件路径是否正确\n\
                 2. 文件名拼写是否有误\n\
                 3. 是否使用了正确的路径分隔符（Windows 用 \\ 或 / 均可）\n\
                 4. 文件是否被其他程序占用",
                image_path
            ));
        }

        // 3. 检查语言包是否可用
        if let Err(e) = Self::check_lang_available(lang) {
            return PluginResult::err(format!("❌ 语言包检查失败：{}", e));
        }

        // 4. 执行 OCR 识别（使用 stdout 模式输出到控制台）
        let start_time = std::time::SystemTime::now();

        let mut args: Vec<String> = vec![
            image_path.to_string(),
            "stdout".to_string(), // 输出到标准输出
            "-l".to_string(),
            lang.to_string(),
            "--psm".to_string(),
            psm.to_string(),
        ];

        // 如果是 JSON 格式，添加 tsv 输出选项以便后续解析
        if output_format == "json" {
            args.push("-c".to_string());
            args.push("tessedit_create_tsv=1".to_string());
        }

        let result = CliExecutor::safe_exec("tesseract", &args);

        let duration_ms = start_time.elapsed().map_or(0, |d| d.as_millis());

        if !result.success {
            // tesseract 返回非零状态码时的处理
            let error_hint = if result.stderr.contains("empty page") ||
                result.stderr.contains("Please call with a valid image") ||
                result.stderr.contains("Image too small")
            {
                "图片可能为空、损坏或不包含可识别的文字。请尝试：\n\
                 - 使用更清晰的图片\n\
                 - 确保图片分辨率不低于 300 DPI\n\
                 - 检查图片格式是否支持（PNG/JPG/BMP/TIFF/WebP）"
            } else if result.stderr.contains("language") || result.stderr.contains("lang") {
                "语言包可能未正确安装。请重新安装 Tesseract 并勾选所需语言包。"
            } else if result.stderr.contains("permission") || result.stderr.contains("access") {
                "没有权限访问该文件。请检查文件权限或以管理员身份运行。"
            } else {
                "未知错误，请查看下方详细信息。"
            };

            return PluginResult::err(format!(
                "❌ OCR 识别失败（退出码: {:?}）：{}\n\n\
                 详细错误: {}\n\n\
                 💡 提示: {}",
                result.exit_code, error_hint, result.stderr, error_hint
            ));
        }

        // 5. 处理识别结果
        let recognized_text = result.stdout.trim();
        let confidence = Self::parse_confidence(&result.stderr);

        if recognized_text.is_empty() {
            return PluginResult::ok_with_data(
                format!(
                    "⚠️ 图片「{}」未识别出任何文字。\n\n\
                     可能的原因：\n\
                     - 图片不包含文字内容\n\
                     - 文字过于模糊或倾斜角度过大\n\
                     - 文字颜色与背景色过于接近\n\
                     - 字体过于艺术化或手写体难以辨认\n\n\
                     建议：尝试更换更清晰的图片，或调整 PSM 参数。\n\n\
                     ⏱ 耗时: {}ms | 语言: {}",
                    image_path, duration_ms, Self::lang_display_name(lang)
                ),
                serde_json::json!({
                    "status": "no_text_found",
                    "image_path": image_path,
                    "lang": lang,
                    "duration_ms": duration_ms,
                    "has_content": false
                })
            );
        }

        // 统计信息
        let char_count = recognized_text.chars().count();
        let line_count = recognized_text.lines().count();
        let word_count = recognized_text.split_whitespace().count();

        match output_format {
            "json" => {
                // 结构化 JSON 输出
                PluginResult::ok_with_data(
                    format!(
                        "📷 OCR 识别完成\n\
                         图片: {}\n\
                         语言: {}\n\
                         字符数: {} | 行数: {} | 词数: {}\n\
                         耗时: {}ms\n\n\
                         识别结果:\n\
                         {}",
                        image_path,
                        Self::lang_display_name(lang),
                        char_count,
                        line_count,
                        word_count,
                        duration_ms,
                        recognized_text
                    ),
                    serde_json::json!({
                        "status": "success",
                        "text": recognized_text,
                        "image_path": image_path,
                        "lang": lang,
                        "lang_display": Self::lang_display_name(lang),
                        "char_count": char_count,
                        "line_count": line_count,
                        "word_count": word_count,
                        "confidence": confidence,
                        "duration_ms": duration_ms,
                        "psm": psm,
                        "engine": "tesseract",
                        "stderr_preview": if result.stderr.len() > 200 {
                            Some(result.stderr.chars().take(200).collect::<String>())
                        } else if !result.stderr.is_empty() {
                            Some(result.stderr.clone())
                        } else {
                            None
                        }
                    })
                )
            }
            _ => {
                // 默认纯文本输出
                let mut output = String::new();
                output.push_str(&format!("📷 OCR 识别结果（语言: {}）：\n", Self::lang_display_name(lang)));
                output.push_str(&"─".repeat(50));
                output.push('\n');
                output.push('\n');
                output.push_str(recognized_text);
                output.push('\n');

                output.push_str(&format!(
                    "\n\
                     ────────────────────────────────\n\
                     📊 统计: {}字符 | {}行 | {}词\n\
                     🏷 语言: {} | 引擎: Tesseract\n\
                     ⏱ 耗时: {}ms",
                    char_count,
                    line_count,
                    word_count,
                    Self::lang_display_name(lang),
                    duration_ms
                ));

                if let Some(conf) = confidence {
                    output.push_str(&format!("\n🎯 置信度: {:.1}%", conf));
                }

                PluginResult::ok_with_data(
                    output,
                    serde_json::json!({
                        "status": "success",
                        "text": recognized_text,
                        "char_count": char_count,
                        "line_count": line_count,
                        "word_count": word_count,
                        "lang": lang,
                        "confidence": confidence,
                        "duration_ms": duration_ms,
                        "engine": "tesseract"
                    })
                )
            }
        }
    }

    fn is_enabled(&self) -> bool { self.enabled }
    fn set_enabled(&mut self, enabled: bool) { self.enabled = enabled; }
}
