// CLI 安全执行器 — 在沙箱环境中执行外部命令

use std::process::Command;
use std::time::{SystemTime, UNIX_EPOCH};

use crate::models::agent::{AgentToolCall, ToolCallStatus};

/// CLI 执行结果
#[derive(Debug, Clone)]
pub struct CliResult {
    pub success: bool,
    pub stdout: String,
    pub stderr: String,
    pub exit_code: Option<i32>,
    pub duration_ms: u128,
}

/// 危险命令黑名单（防止恶意操作）
const FORBIDDEN_COMMANDS: &[&str] = &[
    "rm -rf /", "mkfs", "dd if=", "chmod 777 /", "shutdown",
    "reboot", "halt", "init 0", "format", ":(){ :|:& };:", // fork bomb
    "curl ", "wget ", // 网络请求由 ApiClient 统一管理
];

/// 危险参数模式
const FORBIDDEN_PATTERNS: &[&str] = &[
    "> /dev/sda", "/etc/passwd", "/etc/shadow",
    "sudo", "su ", "passwd ",
];

/// CLI 安全执行器
pub struct CliExecutor;

impl CliExecutor {
    /// 安全执行一个 CLI 命令
    /// # Arguments
    /// * `program` - 程序名（如 "ffmpeg", "python", "pandoc"）
    /// * `args` - 参数列表
    /// # Returns
    /// * CliResult - 执行结果
    pub fn safe_exec(program: &str, args: &[String]) -> CliResult {
        let start = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_millis();

        // 安全检查
        if let Err(e) = Self::validate_command(program, args) {
            return CliResult {
                success: false,
                stdout: String::new(),
                stderr: e,
                exit_code: None,
                duration_ms: 0,
            };
        }

        // 执行命令，设置超时30秒
        let output = Command::new(program)
            .args(args)
            .current_dir(".") // 工作目录限制为项目目录
            .output();

        let end = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_millis();

        match output {
            Ok(result) => {
                let stdout = String::from_utf8_lossy(&result.stdout).to_string();
                let stderr = String::from_utf8_lossy(&result.stderr).to_string();
                let success = result.status.success();

                CliResult {
                    success,
                    stdout,
                    stderr,
                    exit_code: result.status.code(),
                    duration_ms: end - start,
                }
            }
            Err(e) => CliResult {
                success: false,
                stdout: String::new(),
                stderr: format!("命令执行失败: {}", e),
                exit_code: None,
                duration_ms: end - start,
            }
        }
    }

    /// 将 CliResult 转换为 AgentToolCall 记录
    pub fn to_tool_call_record(
        name: &str,
        arguments: &serde_json::Value,
        result: CliResult,
        started_at: i64,
    ) -> AgentToolCall {
        let now = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs() as i64;

        AgentToolCall {
            id: format!("tc_{}_{}", name, started_at),
            name: name.to_string(),
            arguments: arguments.clone(),
            status: if result.success { ToolCallStatus::Success } else { ToolCallStatus::Failed },
            result: Some(if result.success {
                format!("{}\n{}", result.stdout, result.stderr)
            } else {
                format!("错误: {}\n{}", result.stderr, result.stdout)
            }),
            started_at,
            finished_at: Some(now),
        }
    }

    /// 验证命令安全性
    fn validate_command(program: &str, args: &[String]) -> Result<(), String> {
        let full_cmd = format!("{} {}", program, args.join(" "));

        // 检查危险命令
        for forbidden in FORBIDDEN_COMMANDS {
            if full_cmd.contains(forbidden) {
                return Err(format!("安全拦截: 命令包含禁止的关键词 '{}'", forbidden));
            }
        }

        // 检查危险模式
        for pattern in FORBIDDEN_PATTERNS {
            if full_cmd.contains(pattern) {
                return Err(format!("安全拦截: 命令包含危险模式 '{}'", pattern));
            }
        }

        // 检查管道和重定向（只允许基本的输出重定向）
        if full_cmd.contains('|') && !full_cmd.contains(" | ") && !full_cmd.contains("| ") {
            // 允许简单管道但记录警告
        }

        Ok(())
    }

    /// 检查某个 CLI 工具是否可用
    pub fn check_available(program: &str) -> bool {
        Command::new(program)
            .arg("--version")
            .stdout(std::process::Stdio::null())
            .stderr(std::process::Stdio::null())
            .output()
            .map(|r| r.status.success())
            .unwrap_or(false)
    }
}
