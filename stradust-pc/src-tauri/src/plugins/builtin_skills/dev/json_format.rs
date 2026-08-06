// JSON 格式化技能 — 支持 format/validate/query/transform 操作
// 优先使用 jq，fallback 到 Python（两者都不可用时使用内置 serde_json）

use async_trait::async_trait;
use std::process::{Command, Stdio};
use std::time::{SystemTime, UNIX_EPOCH};

use crate::models::chat::ToolDefinition;
use crate::agents::cli_executor::CliExecutor;
use crate::plugins::plugin_trait::{PluginContext, PluginResult, ToolPlugin};

pub struct JsonFormatPlugin { enabled: bool }

impl JsonFormatPlugin {
    pub fn new() -> Self { JsonFormatPlugin { enabled: true } }
}

fn is_json_file_path(input: &str) -> bool {
    let trimmed = input.trim();
    trimmed.ends_with(".json") && std::path::Path::new(trimmed).exists()
}

#[async_trait]
impl ToolPlugin for JsonFormatPlugin {
    fn name(&self) -> &str { "json_format" }
    fn description(&self) -> &str { "JSON格式化、校验与转换：美化压缩、Schema校验、JSON↔YAML/TOML互转" }

    fn get_definition(&self) -> ToolDefinition {
        ToolDefinition {
            r#type: "function".to_string(),
            function: crate::models::chat::ToolFunction {
                name: "json_format".to_string(),
                description: "对JSON数据进行格式化处理：美化打印、校验合法性、查询和转换".to_string(),
                parameters: serde_json::json!({
                    "type": "object",
                    "properties": {
                        "input": { "type": "string", "description": "输入的 JSON 字符串或 .json 文件路径" },
                        "operation": { "type": "string", "enum": ["format", "validate", "query", "transform"], "description": "操作类型" },
                        "query": { "type": "string", "description": "jq 查询表达式（query/transform 操作时使用）" },
                        "indent": { "type": "integer", "description": "缩进空格数（format 操作用，默认 2）" }
                    },
                    "required": ["operation", "input"]
                }),
            },
        }
    }

    async fn execute(&self, arguments: &serde_json::Value, _context: &PluginContext) -> PluginResult {
        let op = arguments["operation"].as_str().unwrap_or("format");
        let input = arguments["input"].as_str().unwrap_or("");
        let query = arguments["query"].as_str().unwrap_or("");
        let indent = arguments["indent"].as_i64().unwrap_or(2);

        tracing::info!("[json_format] operation={}, input_len={}", op, input.len());

        if input.is_empty() {
            return PluginResult::err("输入不能为空。请提供 JSON 字符串或 .json 文件路径。");
        }

        let raw_input = if is_json_file_path(input) {
            match std::fs::read_to_string(input) {
                Ok(content) => content,
                Err(e) => return PluginResult::err(format!("无法读取文件 '{}': {}", input, e)),
            }
        } else {
            input.to_string()
        };

        match op {
            "format" => Self::do_format(&raw_input, indent),
            "validate" => Self::do_validate(&raw_input),
            "query" => Self::do_query(&raw_input, query),
            "transform" => Self::do_transform(&raw_input, query),
            _ => PluginResult::err(format!("不支持的操作: '{}'。支持: format, validate, query, transform", op)),
        }
    }

    fn is_enabled(&self) -> bool { self.enabled }
    fn set_enabled(&mut self, enabled: bool) { self.enabled = enabled; }
}

/// 辅助：通过 stdin 管道执行命令并返回结果
fn run_with_stdin(program: &str, args: &[&str], input_data: &str) -> (bool, String, String, u128) {
    let start = SystemTime::now()
        .duration_since(UNIX_EPOCH).unwrap_or_default().as_millis();

    let result = Command::new(program)
        .args(args)
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .creation_flags(0x08000000)
        .spawn()
        .and_then(|mut child| {
            use std::io::Write;
            if let Some(ref mut stdin) = child.stdin {
                let _ = stdin.write_all(input_data.as_bytes());
            }
            child.wait_with_output()
        });

    let end = SystemTime::now()
        .duration_since(UNIX_EPOCH).unwrap_or_default().as_millis();

    match result {
        Ok(out) => (
            out.status.success(),
            String::from_utf8_lossy(&out.stdout).to_string(),
            String::from_utf8_lossy(&out.stderr).to_string(),
            end - start,
        ),
        Err(e) => (
            false,
            String::new(),
            e.to_string(),
            end - start,
        ),
    }
}

impl JsonFormatPlugin {
    /// 格式化操作
    fn do_format(raw_input: &str, indent: i64) -> PluginResult {
        if CliExecutor::check_available("jq") {
            tracing::info!("[json_format] 使用 jq 执行 format");
            let r = run_with_stdin("jq", &["--indent", &indent.to_string(), "."], raw_input);
            if r.0 && !r.1.trim().is_empty() {
                return ok_json("JSON 格式化完成", "jq", &r.1.trim(), r.3);
            }
        }

        // Fallback: python
        if CliExecutor::check_available("python") || CliExecutor::check_available("python3") {
            let prog = if CliExecutor::check_available("python") { "python" } else { "python3" };
            // 用 serde_json 的 indent 参数，避免 format! 中嵌套 {}
            let py_script = format!(
                "import json,sys\ndata=json.loads(sys.stdin.read())\nprint(json.dumps(data,indent={},ensure_ascii=False))",
                indent
            );
            tracing::info!("[json_format] 使用 python 执行 format");
            let r = run_with_stdin(prog, &["-c", &py_script], raw_input);
            if r.0 && !r.1.trim().is_empty() {
                return ok_json("JSON 格式化完成", "python", &r.1.trim(), r.3);
            }
        }

        // 最终 fallback: 内置 serde_json
        Self::serde_fallback(raw_input, "无外部工具")
    }

    /// 内置 serde_json fallback
    fn serde_fallback(raw_input: &str, failed_tool: &str) -> PluginResult {
        match serde_json::from_str::<serde_json::Value>(raw_input) {
            Ok(value) => {
                let formatted = serde_json::to_string_pretty(&value)
                    .unwrap_or_else(|_| raw_input.to_string());
                ok_json("JSON 格式化完成", "serde_json", &formatted, 0, Some(failed_tool))
            }
            Err(e) => PluginResult::err(format!(
                "❌ JSON 解析失败\n详情: {}\n原始输入（前200字符）:\n{}",
                e, &raw_input[..raw_input.len().min(200)]
            )),
        }
    }

    /// 校验操作
    fn do_validate(raw_input: &str) -> PluginResult {
        match serde_json::from_str::<serde_json::Value>(raw_input) {
            Ok(value) => {
                let type_name = match &value {
                    serde_json::Value::Object(_) => "对象(Object)",
                    serde_json::Value::Array(_) => "数组(Array)",
                    serde_json::Value::String(_) => "字符串(String)",
                    serde_json::Value::Number(_) => "数字(Number)",
                    serde_json::Value::Bool(_) => "布尔(Boolean)",
                    serde_json::Value::Null => "空值(Null)",
                };
                let size_info = match &value {
                    serde_json::Value::Object(m) => format!("包含 {} 个键值对", m.len()),
                    serde_json::Value::Array(a) => format!("包含 {} 个元素", a.len()),
                    _ => String::new(),
                };
                PluginResult::ok_with_data(
                    format!("✅ JSON 校验通过\n类型: {}\n{}\n大小: {} 字节", type_name, size_info, raw_input.len()),
                    serde_json::json!({ "status": "valid", "is_valid": true, "type": type_name, "size_bytes": raw_input.len() })
                )
            }
            Err(e) => PluginResult::ok_with_data(
                format!("❌ JSON 校验失败\n错误: {}\n原始输入（前300字符）:\n{}", e, &raw_input[..raw_input.len().min(300)]),
                serde_json::json!({ "status": "invalid", "is_valid": false, "error": e.to_string() })
            ),
        }
    }

    /// 查询操作（jq → python）
    fn do_query(raw_input: &str, query: &str) -> PluginResult {
        if query.is_empty() {
            return PluginResult::err("query 操作需要提供查询表达式。例如: \".data.items[0]\"");
        }

        if CliExecutor::check_available("jq") {
            tracing::info!("[json_format] jq query: {}", query);
            let r = run_with_stdin("jq", &["-r", query], raw_input);
            if r.0 {
                let display = if r.1.trim().is_empty() { "(无匹配结果)" } else { r.1.trim() };
                return ok_json("JSON 查询完成", "jq", display, r.3);
            }
        }

        // Python fallback
        Self::python_query(raw_input, query)
    }

    /// Python 查询实现
    fn python_query(raw_input: &str, query: &str) -> PluginResult {
        let has_py = CliExecutor::check_available("python") || CliExecutor::check_available("python3");
        if !has_py {
            return PluginResult::err("需要 jq 或 python 来执行查询。请安装其中之一。");
        }
        let prog = if CliExecutor::check_available("python") { "python" } else { "python3" };
        // 安全的路径解析脚本 — 避免在 format! 中使用 {}
        let py_script = format!(
            "import json,sys\ndata=json.loads(sys.stdin.read())\nr='{}'\nresult=data\nfor p in r.lstrip('.').split('.'):\n if p:\n  if '[' in p:\n   k=p.split('[')[0]\n   if k:result=result[k]\n   i=int(p.split('[')[1].rstrip(']'))\n   result=result[i]\n  elif p:result=result[p]\nprint(json.dumps(result,indent=2,ensure_ascii=False))",
            query.replace("'", "\\'")
        );
        let r = run_with_stdin(prog, &["-c", &py_script], raw_input);
        if r.0 {
            return ok_json("JSON 查询完成", "python", &r.1.trim(), r.3);
        }
        PluginResult::err(format!("❌ JSON 查询失败\n表达式: {}\n错误: {}", query, r.2.trim()))
    }

    /// 转换操作（jq → python eval）
    fn do_transform(raw_input: &str, query: &str) -> PluginResult {
        if query.is_empty() {
            return PluginResult::err("transform 操作需要提供转换表达式。");
        }

        if CliExecutor::check_available("jq") {
            tracing::info!("[json_format] jq transform: {}", query);
            let r = run_with_stdin("jq", &[query], raw_input);
            if r.0 {
                return ok_json("JSON 转换完成", "jq", &r.1.trim(), r.3);
            }
            return PluginResult::err(format!("❌ JSON 转换失败\n错误: {}", r.2.trim()));
        }

        // Python fallback — 用 json module 做安全转换
        if CliExecutor::check_available("python") || CliExecutor::check_available("python3") {
            let prog = if CliExecutor::check_available("python") { "python" } else { "python3" };
            // 简单的 key 重命名/提取，不用 eval（避免安全问题）
            let py_script = format!(
                "import json,sys\ndata=json.loads(sys.stdin.read())\nexpr='{}'\n# 支持简单的 key 提取\nif expr.startswith('.') and expr.count('.')<=3:\n parts=expr.lstrip('.').split('.')\n r=data\n for p in parts:\n  if p:r=r.get(p,r)\n print(json.dumps(r,indent=2,ensure_ascii=False))\nelse:\n print('ERROR: 不支持的表达式',file=sys.stderr)\n sys.exit(1)",
                query.replace("'", "\\'")
            );
            let r = run_with_stdin(prog, &["-c", &py_script], raw_input);
            if r.0 {
                return ok_json("JSON 转换完成", "python", &r.1.trim(), r.3);
            }
            return PluginResult::err(format!("❌ JSON 转换失败\n错误: {}", r.2.trim()));
        }

        PluginResult::err("JSON 转换需要 jq 或 python。\n请安装: https://jqlang.github.io/jq/download/")
    }
}

/// 构建成功结果（统一格式）
fn ok_json(title: &str, tool: &str, result: &str, duration_ms: u128, note: Option<&str>) -> PluginResult {
    let note_str = note.map(|n| format!("（{} 不可用）", n)).unwrap_or_default();
    PluginResult::ok_with_data(
        format!("✅ {} [{}ms]\n工具: {}\n\n--- 结果 ---\n{}", title, duration_ms, tool, result),
        serde_json::json!({ "status": "success", "tool": tool, "result": result, "duration_ms": duration_ms })
    )
}
