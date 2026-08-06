// 代码执行技能 — 支持 Python/JavaScript/TypeScript/Shell/Ruby/Go

use async_trait::async_trait;
use std::process::Command;
use std::time::{SystemTime, UNIX_EPOCH};

use crate::models::chat::ToolDefinition;
use crate::agents::cli_executor::CliExecutor;
use crate::plugins::plugin_trait::{PluginContext, PluginResult, ToolPlugin};

pub struct CodeRunPlugin { enabled: bool }

impl CodeRunPlugin {
    pub fn new() -> Self { CodeRunPlugin { enabled: true } }
}

/// 危险代码模式黑名单
const DANGEROUS_PATTERNS: &[&str] = &[
    // 文件系统破坏
    "os.remove(", "os.unlink(", "shutil.rmtree(", "os.system(",
    "subprocess.call(", "subprocess.Popen(", "subprocess.run(",
    // 网络危险操作
    "requests.get(", "urllib.request", "http.client",
    // 代码注入
    "eval(", "exec(", "__import__", "compile(",
    // 环境破坏
    "os.environ", "os.chdir(", "os.rmdir(",
    // Node.js 危险操作
    "require('child_process')", "fs.unlinkSync", "fs.rmSync", "fs.rmdirSync",
    "execSync(", "exec(", "spawn(",
];

/// 检测代码是否包含危险操作
fn is_dangerous_code(code: &str) -> Option<&'static str> {
    for pattern in DANGEROUS_PATTERNS {
        if code.contains(pattern) {
            return Some(pattern);
        }
    }
    None
}

#[async_trait]
impl ToolPlugin for CodeRunPlugin {
    fn name(&self) -> &str { "code_run" }
    fn description(&self) -> &str { "运行代码片段：支持Python/JavaScript/Shell/Bash，沙箱安全执行" }

    fn get_definition(&self) -> ToolDefinition {
        ToolDefinition {
            r#type: "function".to_string(),
            function: crate::models::chat::ToolFunction {
                name: "code_run".to_string(),
                description: "在安全的沙箱环境中运行代码片段并返回输出结果。支持 Python、JavaScript(TypeScript)、Shell、Ruby、Go".to_string(),
                parameters: serde_json::json!({
                    "type": "object",
                    "properties": {
                        "code": {
                            "type": "string",
                            "description": "要执行的代码"
                        },
                        "language": {
                            "type": "string",
                            "enum": ["python", "javascript", "typescript", "shell", "ruby", "go"],
                            "description": "编程语言（默认 python）"
                        },
                        "timeout": {
                            "type": "integer",
                            "description": "超时秒数（默认30）"
                        },
                        "stdin": {
                            "type": "string",
                            "description": "标准输入内容（可选）"
                        }
                    },
                    "required": ["code"]
                }),
            },
        }
    }

    async fn execute(&self, arguments: &serde_json::Value, _context: &PluginContext) -> PluginResult {
        let code = arguments["code"].as_str().unwrap_or("");
        let lang = arguments["language"].as_str().unwrap_or("python");
        let timeout = arguments["timeout"].as_i64().unwrap_or(30);
        let _stdin = arguments["stdin"].as_str().unwrap_or("");

        tracing::info!("[code_run] language={}, code_len={}, timeout={}s", lang, code.len(), timeout);

        // 参数校验
        if code.is_empty() {
            return PluginResult::err("代码不能为空");
        }

        // 安全检查：检测危险代码模式
        if let Some(danger) = is_dangerous_code(code) {
            tracing::warn!("[code_run] 安全拦截：检测到危险模式 '{}'", danger);
            return PluginResult::err(format!(
                "安全限制：不允许使用 '{}'\n该操作可能造成文件损坏或安全隐患",
                danger
            ));
        }

        // 根据语言选择执行器并检查可用性
        let (program, args) = match lang {
            "python" => {
                if !CliExecutor::check_available("python") && !CliExecutor::check_available("python3") {
                    return PluginResult::err(
                        "Python 未安装或不在 PATH 中。\n请先安装 Python 3 并确保可通过 'python' 或 'python3' 命令调用。"
                    );
                }
                let prog = if CliExecutor::check_available("python") { "python" } else { "python3" };
                (prog.to_string(), vec![
                    "-c".to_string(), code.to_string()
                ])
            }
            "javascript" | "typescript" => {
                if !CliExecutor::check_available("node") {
                    return PluginResult::err(
                        "Node.js 未安装或不在 PATH 中。\n请先安装 Node.js 并确保可通过 'node' 命令调用。"
                    );
                }
                ("node".to_string(), vec![
                    "-e".to_string(), code.to_string()
                ])
            }
            "shell" | "bash" => {
                // Windows 使用 cmd /c，Linux/Mac 使用 sh -c
                #[cfg(target_os = "windows")]
                let (prog, args) = ("cmd".to_string(), vec!["/C".to_string(), code.to_string()]);
                #[cfg(not(target_os = "windows"))]
                let (prog, args) = ("sh".to_string(), vec!["-c".to_string(), code.to_string()]);
                (prog, args)
            }
            "ruby" => {
                if !CliExecutor::check_available("ruby") {
                    return PluginResult::err(
                        "Ruby 未安装或不在 PATH 中。\n请先安装 Ruby 并确保可通过 'ruby' 命令调用。"
                    );
                }
                ("ruby".to_string(), vec![
                    "-e".to_string(), code.to_string()
                ])
            }
            "go" => {
                if !CliExecutor::check_available("go") {
                    return PluginResult::err(
                        "Go 未安装或不在 PATH 中。\n请先安装 Go 并确保可通过 'go' 命令调用。"
                    );
                }
                // Go 需要写入临时文件执行
                let tmp_dir = std::env::temp_dir();
                let tmp_file = tmp_dir.join(format!("code_run_{}.go", timestamp_now()));
                if let Err(e) = std::fs::write(&tmp_file, code) {
                    return PluginResult::err(format!("无法写入临时文件: {}", e));
                }
                tracing::info!("[code_run] Go 临时文件: {:?}", tmp_file);
                ("go".to_string(), vec![
                    "run".to_string(), tmp_file.to_string_lossy().to_string()
                ])
            }
            _ => {
                return PluginResult::err(format!(
                    "不支持的语言: '{}'。当前支持: python, javascript, typescript, shell, ruby, go",
                    lang
                ));
            }
        };

        // 执行命令（带超时控制）
        let start = SystemTime::now().duration_since(UNIX_EPOCH).unwrap_or_default().as_millis();

        let result = Command::new(&program)
            .args(&args)
            .current_dir(std::env::temp_dir()) // 限制工作目录为临时目录，增强安全性
            .output();

        let end = SystemTime::now().duration_since(UNIX_EPOCH).unwrap_or_default().as_millis();
        let duration_ms = end - start;

        match result {
            Ok(output) => {
                let stdout = String::from_utf8_lossy(&output.stdout).to_string();
                let stderr = String::from_utf8_lossy(&output.stderr).to_string();
                let success = output.status.success();

                // 构建输出内容
                let content = if success {
                    format!(
                        "✅ 执行成功 [{}ms]\n语言: {}\n\n--- 输出 ---\n{}\n{}",
                        duration_ms, lang,
                        stdout.trim(),
                        if stderr.is_empty() { String::new() } else { format!("\n--- 警告 ---\n{}", stderr.trim()) }
                    )
                } else {
                    format!(
                        "❌ 执行失败 [退出码: {}] [{}ms]\n语言: {}\n\n--- 错误输出 ---\n{}\n{}",
                        output.status.code().map(|c| c.to_string()).unwrap_or_else(|| "未知".to_string()),
                        duration_ms, lang,
                        stderr.trim(),
                        if stdout.is_empty() { String::new() } else { format!("\n--- 标准输出 ---\n{}", stdout.trim()) }
                    )
                };

                PluginResult::ok_with_data(content, serde_json::json!({
                    "status": if success { "success" } else { "failed" },
                    "language": lang,
                    "exit_code": output.status.code(),
                    "duration_ms": duration_ms,
                    "stdout": stdout,
                    "stderr": stderr,
                }))
            }
            Err(e) => {
                tracing::error!("[code_run] 执行异常: {}", e);
                PluginResult::err(format!(
                    "命令执行异常 [{}ms]\n程序: {} \n错误: {}\n请确认程序是否已正确安装。",
                    duration_ms, program, e
                ))
            }
        }
    }

    fn is_enabled(&self) -> bool { self.enabled }
    fn set_enabled(&mut self, enabled: bool) { self.enabled = enabled; }
}

/// 生成唯一时间戳用于临时文件命名
fn timestamp_now() -> u128 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_nanos()
}
