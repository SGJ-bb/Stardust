// Git 操作技能 — 支持状态查看/提交/分支/日志/差异对比/远程同步

use async_trait::async_trait;

use crate::models::chat::ToolDefinition;
use crate::agents::cli_executor::CliExecutor;
use crate::plugins::plugin_trait::{PluginContext, PluginResult, ToolPlugin};

pub struct GitOperationPlugin { enabled: bool }

impl GitOperationPlugin {
    pub fn new() -> Self { GitOperationPlugin { enabled: true } }
}

#[async_trait]
impl ToolPlugin for GitOperationPlugin {
    fn name(&self) -> &str { "git_operation" }
    fn description(&self) -> &str { "Git版本控制操作：提交/分支/合并/日志/差异对比/远程同步" }

    fn get_definition(&self) -> ToolDefinition {
        ToolDefinition {
            r#type: "function".to_string(),
            function: crate::models::chat::ToolFunction {
                name: "git_operation".to_string(),
                description: "在指定Git仓库中执行版本控制操作。支持查看状态、提交更改、创建分支、查看日志和差异等".to_string(),
                parameters: serde_json::json!({
                    "type": "object",
                    "properties": {
                        "operation": {
                            "type": "string",
                            "enum": ["status", "log", "diff", "branch", "commit", "pull", "push", "create_branch"],
                            "description": "操作类型"
                        },
                        "repo_path": {
                            "type": "string",
                            "description": "Git仓库路径（默认当前目录）"
                        },
                        "message": {
                            "type": "string",
                            "description": "提交信息（commit 操作时必填）"
                        },
                        "branch_name": {
                            "type": "string",
                            "description": "分支名称（create_branch 操作时必填）"
                        },
                        "args": {
                            "type": "array",
                            "items": { "type": "string" },
                            "description": "额外参数列表（如 diff 指定文件，push 指定 remote/branch 等）"
                        }
                    },
                    "required": ["operation"]
                }),
            },
        }
    }

    async fn execute(&self, arguments: &serde_json::Value, _context: &PluginContext) -> PluginResult {
        let op = arguments["operation"].as_str().unwrap_or("status");
        let repo_path = arguments["repo_path"]
            .as_str()
            .map(|s| s.to_string())
            .unwrap_or_else(|| ".".to_string());
        let message = arguments["message"].as_str().unwrap_or("");
        let branch_name = arguments["branch_name"].as_str().unwrap_or("");

        // 解析额外参数
        let extra_args: Vec<String> = arguments["args"]
            .as_array()
            .map(|arr| arr.iter().filter_map(|v| v.as_str().map(|s| s.to_string())).collect())
            .unwrap_or_default();

        tracing::info!("[git_operation] operation={}, repo={}", op, repo_path);

        // 检查 git 是否可用
        if !CliExecutor::check_available("git") {
            return PluginResult::err(
                "Git 未安装或不在 PATH 中。\n请先安装 Git 并确保可通过 'git' 命令调用。\n\n安装指南:\n- Windows: https://git-scm.com/download/win\n- macOS: brew install git\n- Linux: apt install git 或 yum install git"
            );
        }

        // 根据操作类型构建 git 命令参数
        let mut git_args: Vec<String> = match op {
            "status" => vec!["status".to_string()],
            "log" => vec!["log".to_string(), "--oneline".to_string(), "-10".to_string()],
            "diff" => {
                let mut args = vec!["diff".to_string()];
                args.extend(extra_args);
                args
            }
            "branch" => vec!["branch".to_string(), "-a".to_string()],
            "commit" => {
                if message.is_empty() {
                    return PluginResult::err("commit 操作需要提供 message 参数（提交信息）。");
                }
                let mut args = vec![
                    "commit".to_string(),
                    "-m".to_string(),
                    message.to_string(),
                ];
                // 如果有额外文件参数，追加到命令中
                args.extend(extra_args);
                args
            }
            "pull" => vec!["pull".to_string()],
            "push" => {
                let mut args = vec!["push".to_string()];
                args.extend(extra_args); // 可传入 origin main 等参数
                args
            }
            "create_branch" => {
                if branch_name.is_empty() {
                    return PluginResult::err("create_branch 操作需要提供 branch_name 参数（新分支名）。");
                }
                vec!["checkout".to_string(), "-b".to_string(), branch_name.to_string()]
            }
            _ => {
                return PluginResult::err(format!(
                    "不支持的操作: '{}'。\n当前支持的操作: status, log, diff, branch, commit, pull, push, create_branch",
                    op
                ));
            }
        };

        // 执行 git 命令
        let result = CliExecutor::safe_exec("git", &git_args);

        // 构建输出内容
        let content = if result.success {
            format!(
                "✅ Git {} 执行成功 [{}ms]\n仓库: {}\n\n--- 输出 ---\n{}\n{}",
                op,
                result.duration_ms,
                repo_path,
                result.stdout.trim(),
                if result.stderr.is_empty() { String::new() } else { format!("\n--- 信息 ---\n{}", result.stderr.trim()) }
            )
        } else {
            format!(
                "❌ Git {} 执行失败 [{}ms]\n仓库: {}\n退出码: {}\n\n--- 错误 ---\n{}\n{}",
                op,
                result.duration_ms,
                repo_path,
                result.exit_code.map(|c| c.to_string()).unwrap_or_else(|| "未知".to_string()),
                result.stderr.trim(),
                if result.stdout.is_empty() { String::new() } else { format!("\n--- 输出 ---\n{}", result.stdout.trim()) }
            )
        };

        PluginResult::ok_with_data(content, serde_json::json!({
            "status": if result.success { "success" } else { "failed" },
            "operation": op,
            "repo_path": repo_path,
            "exit_code": result.exit_code,
            "duration_ms": result.duration_ms,
            "stdout": result.stdout,
            "stderr": result.stderr,
        }))
    }

    fn is_enabled(&self) -> bool { self.enabled }
    fn set_enabled(&mut self, enabled: bool) { self.enabled = enabled; }
}
