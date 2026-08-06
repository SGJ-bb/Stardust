// PDF 合并/拆分技能 — 使用 qpdf / pdftk / pypdf 进行 PDF 操作

use async_trait::async_trait;
use crate::models::chat::ToolDefinition;
use crate::plugins::plugin_trait::{PluginContext, PluginResult, ToolPlugin};
use crate::agents::cli_executor::CliExecutor;

pub struct PdfMergePlugin { enabled: bool }

impl PdfMergePlugin {
    pub fn new() -> Self { PdfMergePlugin { enabled: true } }

    /// 获取文件大小（字节）
    fn file_size_bytes(path: &str) -> Option<u64> {
        std::fs::metadata(path).ok().map(|m| m.len())
    }

    /// 检测可用的 PDF 工具，按优先级返回
    /// 优先级: qpdf > pdftk > python(pypdf)
    fn detect_pdf_tool() -> &'static str {
        if CliExecutor::check_available("qpdf") {
            "qpdf"
        } else if CliExecutor::check_available("pdftk") {
            "pdftk"
        } else if CliExecutor::check_available("python") {
            "pypdf"
        } else {
            "none"
        }
    }
}

#[async_trait]
impl ToolPlugin for PdfMergePlugin {
    fn name(&self) -> &str { "pdf_merge" }
    fn description(&self) -> &str { "PDF合并与拆分：多个PDF合并为一个，或将PDF拆分为单页" }
    fn get_definition(&self) -> ToolDefinition {
        ToolDefinition {
            r#type: "function".to_string(),
            function: crate::models::chat::ToolFunction {
                name: "pdf_merge".to_string(),
                description: "合并多个PDF文件为一个，或按页码范围拆分PDF".to_string(),
                parameters: serde_json::json!({
                    "type": "object",
                    "properties": {
                        "operation": {
                            "type": "string",
                            "enum": ["merge", "split", "extract_pages", "rotate", "compress"],
                            "description": "操作类型"
                        },
                        "files": {
                            "type": "array",
                            "items": { "type": "string" },
                            "description": "输入PDF文件路径列表"
                        },
                        "output_path": { "type": "string", "description": "输出文件路径" },
                        "page_range": { "type": "string", "description": "页码范围如 '1-5,8,10-12'" },
                        "password": { "type": "string", "description": "PDF密码（如有）" }
                    },
                    "required": ["operation", "output_path"]
                }),
            },
        }
    }

    async fn execute(&self, arguments: &serde_json::Value, _context: &PluginContext) -> PluginResult {
        // 1. 解析参数
        let operation = match arguments["operation"].as_str() {
            Some(op) => op,
            None => return PluginResult::err("缺少必需参数: operation（操作类型）"),
        };

        let output_path = match arguments["output_path"].as_str() {
            Some(p) => p,
            None => return PluginResult::err("缺少必需参数: output_path（输出文件路径）"),
        };

        let files: Vec<String> = arguments["files"]
            .as_array()
            .map(|arr| arr.iter().filter_map(|v| v.as_str().map(|s| s.to_string())).collect())
            .unwrap_or_default();

        let page_range = arguments["page_range"].as_str().unwrap_or("");

        tracing::info!("[pdf_merge] 操作={}, 输出={}, 文件数={}", operation, output_path, files.len());

        // 2. 根据操作类型分发处理
        match operation {
            "merge" => self.execute_merge(&files, output_path).await,
            "split" | "extract_pages" => self.execute_split(&files, output_path, page_range).await,
            "info" => self.execute_info(&files).await,
            _ => self.execute_generic(operation, &files, output_path, page_range).await,
        }
    }

    fn is_enabled(&self) -> bool { self.enabled }
    fn set_enabled(&mut self, enabled: bool) { self.enabled = enabled; }
}

// ==================== 各操作的实现 ====================

impl PdfMergePlugin {
    /// 合并多个 PDF 文件
    async fn execute_merge(&self, files: &[String], output_path: &str) -> PluginResult {
        if files.is_empty() {
            return PluginResult::err("缺少输入文件: merge 操作需要通过 files 参数指定至少一个 PDF 文件");
        }

        // 验证所有文件是否存在
        for f in files {
            if !std::path::Path::new(f).exists() {
                return PluginResult::err(format!("输入文件不存在: {}", f));
            }
        }

        let tool = Self::detect_pdf_tool();

        match tool {
            "qpdf" => self.merge_with_qpdf(files, output_path),
            "pdftk" => self.merge_with_pdftk(files, output_path),
            "pypdf" => self.merge_with_pypdf(files, output_path).await,
            _ => PluginResult::err(
                "该技能需要安装以下工具之一：\n\
                 1. qpdf (推荐): winget install qpdf / brew install qpdf\n\
                 2. pdftk: winget install pdftk-java / brew install pdftk-java\n\
                 3. Python + pypdf: pip install pypdf\n\n\
                 安装后重启星尘即可使用此技能。"
            ),
        }
    }

    /// 使用 qpdf 合并 PDF
    fn merge_with_qpdf(&self, files: &[String], output_path: &str) -> PluginResult {
        tracing::info!("[pdf_merge] 使用 qpdf 合并 {} 个文件", files.len());
        // qpdf --empty --pages input1.pdf input2.pdf -- output.pdf
        let mut args = vec![
            "--empty".to_string(),
            "--pages".to_string(),
        ];
        for f in files {
            args.push(f.clone());
        }
        args.push("--".to_string());
        args.push(output_path.to_string());

        let result = CliExecutor::safe_exec("qpdf", &args);
        Self::handle_result(result, "merge", output_path, Some(files), None)
    }

    /// 使用 pdftk 合并 PDF
    fn merge_with_pdftk(&self, files: &[String], output_path: &str) -> PluginResult {
        tracing::info!("[pdf_merge] 使用 pdftk 合并 {} 个文件", files.len());
        // pdftk A=input1.pdf B=input2.pdf cat A B output output.pdf
        let mut args = Vec::new();
        let letters: &[&str] = &["A", "B", "C", "D", "E", "F", "G", "H", "I", "J"];
        for (i, f) in files.iter().enumerate() {
            let letter = letters.get(i).unwrap_or(&"Z");
            args.push(format!("{}={}", letter, f));
        }
        args.push("cat".to_string());
        for i in 0..files.len() {
            args.push(letters.get(i).unwrap_or(&"Z").to_string());
        }
        args.push("output".to_string());
        args.push(output_path.to_string());

        let result = CliExecutor::safe_exec("pdftk", &args);
        Self::handle_result(result, "merge", output_path, Some(files), None)
    }

    /// 使用 Python pypdf 合并 PDF
    async fn merge_with_pypdf(&self, files: &[String], output_path: &str) -> PluginResult {
        tracing::info!("[pdf_merge] 使用 pypdf 合并 {} 个文件", files.len());

        let data_json = serde_json::json!({
            "files": files,
            "output": output_path,
        });

        let script = r#"import json, sys

try:
    from pypdf import PdfMerger

    data = json.loads(sys.argv[1])
    files = data['files']
    output = data['output']

    merger = PdfMerger()
    for f in files:
        merger.append(f)
    merger.write(output)
    merger.close()

    print(json.dumps({'status': 'ok'}))
except ImportError:
    print(json.dumps({'status': 'error', 'message': '缺少 pypdf 库，请执行: pip install pypdf'}))
except Exception as e:
    print(json.dumps({'status': 'error', 'message': str(e)}))
"#;

        let result = CliExecutor::safe_exec("python", &["-c".into(), script.into(), data_json.to_string()]);
        Self::handle_result(result, "merge", output_path, Some(files), None)
    }

    /// 拆分 PDF（按页码范围）
    async fn execute_split(&self, files: &[String], output_path: &str, page_range: &str) -> PluginResult {
        if files.is_empty() {
            return PluginResult::err("缺少输入文件: split/extract_pages 操作需要通过 files 参数指定源 PDF 文件");
        }

        let input_file = &files[0];
        if !std::path::Path::new(input_file).exists() {
            return PluginResult::err(format!("输入文件不存在: {}", input_file));
        }

        let tool = Self::detect_pdf_tool();
        let range_str = if page_range.is_empty() { "all" } else { page_range };

        match tool {
            "qpdf" => self.split_with_qpdf(input_file, output_path, range_str),
            "pdftk" => self.split_with_pdftk(input_file, output_path, range_str),
            "pypdf" => self.split_with_pypdf(input_file, output_path, range_str).await,
            _ => PluginResult::err(
                "该技能需要安装以下工具之一：\n\
                 1. qpdf (推荐): winget install qpdf\n\
                 2. pdftk: winget install pdftk-java\n\
                 3. Python + pypdf: pip install pypdf"
            ),
        }
    }

    /// 使用 qpdf 拆分 PDF
    fn split_with_qpdf(&self, input: &str, output: &str, page_range: &str) -> PluginResult {
        tracing::info!("[pdf_merge] 使用 qpdf 拆分: 页码={}", page_range);
        // qpdf input.pdf --pages . <range> -- output.pdf
        let args = if page_range == "all" || page_range.is_empty() {
            vec![input.to_string(), "--empty".to_string(), "--pages".to_string(), ".".to_string(), "--".to_string(), output.to_string()]
        } else {
            vec![
                input.to_string(),
                "--pages".to_string(),
                ".".to_string(),
                page_range.to_string(),
                "--".to_string(),
                output.to_string(),
            ]
        };

        let result = CliExecutor::safe_exec("qpdf", &args);
        Self::handle_result(result, "split", output, None, Some(page_range))
    }

    /// 使用 pdftk 拆分 PDF
    fn split_with_pdftk(&self, input: &str, output: &str, page_range: &str) -> PluginResult {
        tracing::info!("[pdf_merge] 使用 pdftk 拆分: 页码={}", page_range);
        // pdftk input.pdf cat <range> output output.pdf
        let range_arg = if page_range == "all" || page_range.is_empty() {
            String::new()
        } else {
            page_range.to_string()
        };

        let mut args = vec![input.to_string()];
        if !range_arg.is_empty() {
            args.push("cat".to_string());
            args.push(range_arg);
        }
        args.push("output".to_string());
        args.push(output.to_string());

        let result = CliExecutor::safe_exec("pdftk", &args);
        Self::handle_result(result, "split", output, None, Some(page_range))
    }

    /// 使用 pypdf 拆分 PDF
    async fn split_with_pypdf(&self, input: &str, output: &str, page_range: &str) -> PluginResult {
        tracing::info!("[pdf_merge] 使用 pypdf 拆分: 页码={}", page_range);

        let data_json = serde_json::json!({
            "input": input,
            "output": output,
            "page_range": page_range,
        });

        let script = r#"import json, sys

try:
    from pypdf import PdfReader, PdfWriter

    data = json.loads(sys.argv[1])
    input_path = data['input']
    output_path = data['output']
    page_range = data.get('page_range', '')

    reader = PdfReader(input_path)
    total_pages = len(reader.pages)

    # 解析页码范围，如 "1-3,5,7-9"
    def parse_page_range(range_str, total):
        pages = set()
        for part in range_str.split(','):
            part = part.strip()
            if '-' in part:
                start_end = part.split('-')
                start = int(start_end[0].strip()) - 1  # 转为0索引
                end = int(start_end[1].strip()) - 1
                for i in range(max(0, start), min(end + 1, total)):
                    pages.add(i)
            elif part.lower() == 'all':
                pages.update(range(total))
            else:
                idx = int(part.strip()) - 1
                if 0 <= idx < total:
                    pages.add(idx)
        return sorted(pages)

    writer = PdfWriter()

    if not page_range or page_range.lower() == 'all':
        for page in reader.pages:
            writer.add_page(page)
    else:
        selected = parse_page_range(page_range, total_pages)
        for idx in selected:
            writer.add_page(reader.pages[idx])

    with open(output_path, 'wb') as f:
        writer.write(f)

    print(json.dumps({
        'status': 'ok',
        'total_pages': total_pages,
        'extracted_pages': len(writer.pages),
    }))
except ImportError:
    print(json.dumps({'status': 'error', 'message': '缺少 pypdf 库，请执行: pip install pypdf'}))
except Exception as e:
    print(json.dumps({'status': 'error', 'message': str(e)}))
"#;

        let result = CliExecutor::safe_exec("python", &["-c".into(), script.into(), data_json.to_string()]);
        Self::handle_result(result, "split", output, None, Some(page_range))
    }

    /// 获取 PDF 信息
    async fn execute_info(&self, files: &[String]) -> PluginResult {
        if files.is_empty() {
            return PluginResult::err("缺少输入文件: info 操作需要通过 files 参数指定 PDF 文件");
        }

        let input_file = &files[0];
        if !std::path::Path::new(input_file).exists() {
            return PluginResult::err(format!("输入文件不存在: {}", input_file));
        }

        let tool = Self::detect_pdf_tool();

        match tool {
            "qpdf" => self.info_with_qpdf(input_file),
            "pdftk" => self.info_with_pdftk(input_file),
            "pypdf" => self.info_with_pypdf(input_file).await,
            _ => PluginResult::err(
                "该技能需要安装以下工具之一来读取 PDF 信息：\n\
                 1. qpdf (推荐)\n\
                 2. pdftk\n\
                 3. Python + pypdf"
            ),
        }
    }

    /// 使用 qpdf 获取 PDF 信息
    fn info_with_qpdf(&self, input: &str) -> PluginResult {
        tracing::info!("[pdf_merge] 使用 qpdf 读取信息: {}", input);
        // qpdf --show-input-info input.pdf
        let args = vec!["--show-input-info".to_string(), input.to_string()];
        let result = CliExecutor::safe_exec("qpdf", &args);

        if result.success {
            let size = Self::file_size_bytes(input);
            let content = format!(
                "📄 PDF 文件信息\n📂 文件: {}\n{}\n⏱️  耗时: {} ms\n📦 大小: {:.1} KB",
                input,
                result.stdout.trim(),
                result.duration_ms,
                size.map(|s| s as f64 / 1024.0).unwrap_or(0.0)
            );
            PluginResult::ok_with_data(content, serde_json::json!({
                "status": "success",
                "file_path": input,
                "raw_info": result.stdout.trim(),
                "duration_ms": result.duration_ms,
                "size_bytes": size,
            }))
        } else {
            PluginResult::err(format!("读取 PDF 信息失败: {}", result.stderr))
        }
    }

    /// 使用 pdftk 获取 PDF 信息
    fn info_with_pdftk(&self, input: &str) -> PluginResult {
        tracing::info!("[pdf_merge] 使用 pdftk 读取信息: {}", input);
        // pdftk input.pdf dump_data
        let args = vec![input.to_string(), "dump_data".to_string()];
        let result = CliExecutor::safe_exec("pdftk", &args);

        if result.success {
            let size = Self::file_size_bytes(input);
            let content = format!(
                "📄 PDF 文件信息\n📂 文件: {}\n{}\n⏱️  耗时: {} ms\n📦 大小: {:.1} KB",
                input,
                result.stdout.trim(),
                result.duration_ms,
                size.map(|s| s as f64 / 1024.0).unwrap_or(0.0)
            );
            PluginResult::ok_with_data(content, serde_json::json!({
                "status": "success",
                "file_path": input,
                "raw_info": result.stdout.trim(),
                "duration_ms": result.duration_ms,
                "size_bytes": size,
            }))
        } else {
            PluginResult::err(format!("读取 PDF 信息失败: {}", result.stderr))
        }
    }

    /// 使用 pypdf 获取 PDF 信息
    async fn info_with_pypdf(&self, input: &str) -> PluginResult {
        tracing::info!("[pdf_merge] 使用 pypdf 读取信息: {}", input);

        let data_json = serde_json::json!({ "input": input });

        let script = r#"import json, sys

try:
    from pypdf import PdfReader

    data = json.loads(sys.argv[1])
    reader = PdfReader(data['input'])

    meta = reader.metadata or {}
    info = {
        'total_pages': len(reader.pages),
        'title': meta.get('Title', ''),
        'author': meta.get('Author', ''),
        'subject': meta.get('Subject', ''),
        'creator': meta.get('Creator', ''),
        'producer': meta.get('Producer', ''),
        'creation_date': str(meta.get('CreationDate', '')),
        'modification_date': str(meta.get('ModDate', '')),
        'is_encrypted': reader.is_encrypted,
    }

    print(json.dumps({'status': 'ok', 'data': info}))
except ImportError:
    print(json.dumps({'status': 'error', 'message': '缺少 pypdf 库，请执行: pip install pypdf'}))
except Exception as e:
    print(json.dumps({'status': 'error', 'message': str(e)}))
"#;

        let result = CliExecutor::safe_exec("python", &["-c".into(), script.into(), data_json.to_string()]);

        if !result.success {
            return PluginResult::err(format!("读取 PDF 信息失败: {}", result.stderr));
        }

        let stdout_trimmed = result.stdout.trim();
        let script_result: serde_json::Value = match serde_json::from_str(stdout_trimmed) {
            Ok(v) => v,
            Err(_) => {
                return PluginResult::ok_with_data(
                    format!("📄 PDF 信息:\n{}", stdout_trimmed),
                    serde_json::json!({"status": "partial", "raw_output": stdout_trimmed})
                );
            }
        };

        if script_result.get("status").and_then(|v| v.as_str()) == Some("error") {
            let msg = script_result.get("message").and_then(|v| v.as_str()).unwrap_or("");
            return PluginResult::err(format!("读取失败: {}", msg));
        }

        let size = Self::file_size_bytes(input);
        let data = script_result.get("data").cloned().unwrap_or(serde_json::json!({}));
        let total_pages = data.get("total_pages")
            .and_then(|v| v.as_u64())
            .unwrap_or(0);

        let content = format!(
            "📄 PDF 文件信息\n\
             📂 文件: {}\n\
             📊 总页数: {} 页\n\
             🔒 加密: {}\n\
             ⏱️  耗时: {} ms\n\
             📦 大小: {:.1} KB",
            input,
            total_pages,
            if data.get("is_encrypted").and_then(|v| v.as_bool()).unwrap_or(false) { "是" } else { "否" },
            result.duration_ms,
            size.map(|s| s as f64 / 1024.0).unwrap_or(0.0)
        );

        PluginResult::ok_with_data(content, serde_json::json!({
            "status": "success",
            "file_path": input,
            "duration_ms": result.duration_ms,
            "size_bytes": size,
            "pdf_info": data,
        }))
    }

    /// 通用操作处理器（用于 rotate、compress 等未单独实现的操作）
    async fn execute_generic(&self, operation: &str, files: &[String], output_path: &str, page_range: &str) -> PluginResult {
        let tool = Self::detect_pdf_tool();

        match tool {
            "qpdf" => {
                // qpdf 支持大多数通用操作
                let mut args = vec![files.first().cloned().unwrap_or_default()];
                match operation {
                    "rotate" => {
                        args.extend(vec!["--rotate=180".to_string(), "--".to_string(), output_path.to_string()]);
                    }
                    "compress" => {
                        args.extend(vec!["--optimize".to_string(), "--".to_string(), output_path.to_string()]);
                    }
                    _ => {
                        args.extend(vec!["--empty".to_string(), "--pages".to_string(), ".".to_string(), "--".to_string(), output_path.to_string()]);
                    }
                }
                let result = CliExecutor::safe_exec("qpdf", &args);
                Self::handle_result(result, operation, output_path, Some(files), None)
            }
            "pypdf" => {
                // 回退到 pypdf 处理
                let data_json = serde_json::json!({
                    "files": files,
                    "output": output_path,
                    "operation": operation,
                    "page_range": page_range,
                });

                let script = r#"import json, sys

try:
    from pypdf import PdfReader, PdfWriter

    data = json.loads(sys.argv[1])
    op = data.get('operation', '')
    files = data.get('files', [])
    output = data.get('output', '')

    if not files:
        raise ValueError('缺少输入文件')

    reader = PdfReader(files[0])
    writer = PdfWriter()

    for page in reader.pages:
        if op == 'rotate':
            page.rotate(90)
        writer.add_page(page)

    with open(output, 'wb') as f:
        writer.write(f)

    print(json.dumps({'status': 'ok', 'pages': len(writer.pages)}))
except ImportError:
    print(json.dumps({'status': 'error', 'message': '缺少 pypdf 库，请执行: pip install pypdf'}))
except Exception as e:
    print(json.dumps({'status': 'error', 'message': str(e)}))
"#;

                let result = CliExecutor::safe_exec("python", &["-c".into(), script.into(), data_json.to_string()]);
                Self::handle_result(result, operation, output_path, Some(files), None)
            }
            _ => PluginResult::err(
                format!("操作「{}」需要安装 PDF 工具。推荐安装 qpdf:\nwinget install qpdf", operation)
            ),
        }
    }

    /// 统一的结果处理 — 格式化输出、记录日志
    fn handle_result(
        result: crate::agents::cli_executor::CliResult,
        operation: &str,
        output_path: &str,
        files: Option<&[String]>,
        page_range: Option<&str>,
    ) -> PluginResult {
        if !result.success {
            let err_msg = if result.stderr.is_empty() {
                format!("命令执行失败 (退出码: {:?})", result.exit_code)
            } else {
                result.stderr.clone()
            };

            // 检查是否是 pypdf 未安装的错误
            if err_msg.contains("No module named 'pypdf'") || err_msg.contains("ModuleNotFoundError") && err_msg.contains("pypdf") {
                return PluginResult::err(
                    "缺少依赖库 pypdf。\n请在终端中执行: pip install pypdf"
                );
            }

            tracing::error!("[pdf_merge] {} 操作失败: {}", operation, err_msg);
            return PluginResult::err(format!(
                "PDF「{}」操作失败:\n{}", operation, err_msg
            ));
        }

        // 成功：收集输出文件信息和统计
        let output_size = Self::file_size_bytes(output_path);
        let size_info = output_size
            .map(|s| format!("\n📄 输出文件大小: {:.1} KB", s as f64 / 1024.0))
            .unwrap_or_default();

        let files_info = files
            .map(|f| format!("\n📁 输入文件: {} 个", f.len()))
            .unwrap_or_default();

        let range_info = page_range
            .filter(|r| !r.is_empty() && *r != "all")
            .map(|r| format!("\n📑 页码范围: {}", r))
            .unwrap_or_default();

        let content = format!(
            "✅ PDF「{}」操作完成！\n\
             🎯 输出文件: {}\n\
             ⏱️  耗时: {} ms{}{}{}",
            operation,
            output_path,
            result.duration_ms,
            files_info,
            range_info,
            size_info
        );

        tracing::info!(
            "[pdf_merge] {} 完成 → {}, 耗时{}ms, 大小{:?}B",
            operation, output_path, result.duration_ms, output_size
        );

        PluginResult::ok_with_data(content, serde_json::json!({
            "status": "success",
            "operation": operation,
            "output_path": output_path,
            "duration_ms": result.duration_ms,
            "output_size_bytes": output_size,
            "input_files_count": files.map(|f| f.len()),
            "page_range": page_range,
        }))
    }
}
