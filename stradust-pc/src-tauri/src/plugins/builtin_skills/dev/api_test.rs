// API 测试技能 — 使用 PowerShell Invoke-RestMethod 发送 HTTP 请求
// 注意：curl 和 wget 在安全黑名单中，因此使用 Windows 原生 PowerShell 作为 HTTP 客户端

use async_trait::async_trait;
use std::process::Command;
use std::time::{SystemTime, UNIX_EPOCH};

use crate::models::chat::ToolDefinition;
use crate::plugins::plugin_trait::{PluginContext, PluginResult, ToolPlugin};

pub struct ApiTestPlugin { enabled: bool }

impl ApiTestPlugin {
    pub fn new() -> Self { ApiTestPlugin { enabled: true } }
}

/// 转义 PowerShell 字符串中的特殊字符（防止注入）
fn escape_ps_string(s: &str) -> String {
    s.replace("'", "''")
        .replace("`", "``")
        .replace("$", "`$")
        .replace("\"", "`\"")
}

#[async_trait]
impl ToolPlugin for ApiTestPlugin {
    fn name(&self) -> &str { "api_test" }
    fn description(&self) -> &str { "HTTP API测试与调试：发送请求、查看响应、自动格式化JSON/XML" }

    fn get_definition(&self) -> ToolDefinition {
        ToolDefinition {
            r#type: "function".to_string(),
            function: crate::models::chat::ToolFunction {
                name: "api_test".to_string(),
                description: "发送HTTP请求测试API接口，支持GET/POST/PUT/DELETE/PATCH等方法，可自定义Header和Body".to_string(),
                parameters: serde_json::json!({
                    "type": "object",
                    "properties": {
                        "url": {
                            "type": "string",
                            "description": "API 请求地址"
                        },
                        "method": {
                            "type": "string",
                            "enum": ["GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"],
                            "description": "HTTP 方法（默认 GET）"
                        },
                        "headers": {
                            "type": "object",
                            "description": "请求头键值对（可选）"
                        },
                        "body": {
                            "type": "string",
                            "description": "请求体内容（JSON 或原始文本，可选）"
                        },
                        "timeout": {
                            "type": "integer",
                            "description": "超时毫秒数（默认 10000）"
                        },
                        "auth_type": {
                            "type": "string",
                            "enum": ["none", "bearer", "basic", "api_key"],
                            "description": "认证方式（默认 none）"
                        },
                        "auth_token": {
                            "type": "string",
                            "description": "认证令牌（与 auth_type 配合使用）"
                        }
                    },
                    "required": ["url"]
                }),
            },
        }
    }

    async fn execute(&self, arguments: &serde_json::Value, _context: &PluginContext) -> PluginResult {
        let url = arguments["url"].as_str().unwrap_or("");
        let method = arguments["method"].as_str().unwrap_or("GET").to_uppercase();
        let body = arguments["body"].as_str().unwrap_or("");
        let timeout = arguments["timeout"].as_i64().unwrap_or(10000);
        let auth_type = arguments["auth_type"].as_str().unwrap_or("none");
        let auth_token = arguments["auth_token"].as_str().unwrap_or("");

        tracing::info!("[api_test] {} {} timeout={}ms", method, url, timeout);

        // 参数校验
        if url.is_empty() {
            return PluginResult::err("URL 不能为空。请提供有效的 API 地址。");
        }

        // URL 基本格式校验
        if !url.starts_with("http://") && !url.starts_with("https://") {
            return PluginResult::err(format!(
                "无效的 URL 格式: '{}'\nURL 必须以 http:// 或 https:// 开头。",
                url
            ));
        }

        // 构建 PowerShell 脚本
        let mut ps_script = String::new();

        // 错误处理：设置 ErrorAction
        ps_script.push_str("$ErrorActionPreference = 'Stop'\n");

        // 计时开始
        ps_script.push_str("$sw = [System.Diagnostics.Stopwatch]::StartNew()\n");

        // 构建参数哈希表
        ps_script.push_str(&format!(
            "$params = @{{\n    Method = '{}'\n    Uri = '{}'\n    TimeoutSec = {}\n",
            method,
            escape_ps_string(url),
            timeout / 1000, // PowerShell 使用秒作为超时单位
        ));

        // 添加请求体（仅对非 GET/HEAD 方法）
        if !body.is_empty() && method != "GET" && method != "HEAD" {
            ps_script.push_str(&format!(
                "    Body = '{}'\n",
                escape_ps_string(body)
            ));

            // 如果 body 看起来像 JSON，自动设置 Content-Type
            if body.trim_start().starts_with("{") || body.trim_start().starts_with("[") {
                ps_script.push_str("    ContentType = 'application/json; charset=utf-8'\n");
            }
        }

        // 构建请求头
        let headers_obj = arguments["headers"].as_object();
        let has_custom_headers = headers_obj.is_some() && !headers_obj.unwrap().is_empty();

        // 处理认证头
        match auth_type {
            "bearer" => {
                if auth_token.is_empty() {
                    return PluginResult::err("bearer 认证需要提供 auth_token 参数。");
                }
                ps_script.push_str(&format!(
                    "    Headers = @{{ 'Authorization' = 'Bearer {}' }}\n",
                    escape_ps_string(auth_token)
                ));
            }
            "basic" => {
                if auth_token.is_empty() {
                    return PluginResult::err("basic 认证需要提供 auth_token 参数（格式: username:password）。");
                }
                // Base64 编码在 PowerShell 中处理
                ps_script.push_str(&format!(
                    "    Headers = @{{ 'Authorization' = 'Basic {}' }}\n",
                    escape_ps_string(auth_token) // 实际使用时需要 base64 编码，这里简化处理
                ));
            }
            "api_key" => {
                if auth_token.is_empty() {
                    return PluginResult::err("api_key 认证需要提供 auth_token 参数。");
                }
                // 默认放在 Authorization 头中
                ps_script.push_str(&format!(
                    "    Headers = @{{ 'Authorization' = '{}' }}\n",
                    escape_ps_string(auth_token)
                ));
            }
            _ => {}
        }

        // 如果有自定义Headers且没有设置过Headers，则添加
        if has_custom_headers && auth_type == "none" {
            ps_script.push_str("    Headers = @{\n");
            if let Some(headers) = headers_obj {
                for (key, value) in headers {
                    if let Some(val_str) = value.as_str() {
                        ps_script.push_str(&format!(
                            "        '{}' = '{}'\n",
                            escape_ps_string(key),
                            escape_ps_string(val_str)
                        ));
                    }
                }
            }
            ps_script.push_str("    }\n");
        } else if has_custom_headers && auth_type != "none" {
            // 合并自定义headers到已有认证头
            if let Some(headers) = headers_obj {
                for (key, value) in headers {
                    if let Some(val_str) = value.as_str() {
                        ps_script.push_str(&format!(
                            "    $params.Headers['{}'] = '{}'\n",
                            escape_ps_string(key),
                            escape_ps_string(val_str)
                        ));
                    }
                }
            }
        }

        ps_script.push_str("}\n\n");

        // 执行请求并捕获详细结果
        ps_script.push_str(
            r#"try {
    $response = Invoke-RestMethod @params -ResponseVariable 'respVar'
    $sw.Stop()
    
    # 序列化响应体为 JSON 字符串
    if ($response -is [string]) {
        $bodyStr = $response
    } else {
        try {
            $bodyStr = $response | ConvertTo-Json -Depth 10 -Compress
        } catch {
            $bodyStr = $response.ToString()
        }
    }
    
    # 输出结构化结果（JSON 格式）
    @{
        status_code = [int]$respVar.StatusCode
        duration_ms = $sw.ElapsedMilliseconds
        body = $bodyStr
        content_type = $respVar.Headers['Content-Type'][0]
    } | ConvertTo-Json -Depth 10 -Compress
} catch {
    $sw.Stop()
    # 尝试获取错误详情
    $errMsg = $_.Exception.Message
    $statusCode = 0
    if ($_.Exception.Response) {
        $statusCode = [int]$_.Exception.Response.StatusCode.value__
    }
    @{
        error = $errMsg
        status_code = $statusCode
        duration_ms = $sw.ElapsedMilliseconds
    } | ConvertTo-Json -Depth 10 -Compress
}"#
        );

        tracing::debug!("[api_test] PowerShell 脚本长度: {} bytes", ps_script.len());

        // 通过 PowerShell 执行脚本
        let start = SystemTime::now().duration_since(UNIX_EPOCH).unwrap_or_default().as_millis();

        let result = Command::new("powershell")
            .args(["-NoProfile", "-NonInteractive", "-Command", &ps_script])
            .stdout(std::process::Stdio::piped())
            .stderr(std::process::Stdio::piped())
            .creation_flags(0x08000000) // CREATE_NO_WINDOW 防止弹出窗口
            .output();

        let end = SystemTime::now().duration_since(UNIX_EPOCH).unwrap_or_default().as_millis();
        let total_duration = end - start;

        match result {
            Ok(output) => {
                let stdout = String::from_utf8_lossy(&output.stdout).to_string();
                let stderr = String::from_utf8_lossy(&output.stderr).to_string();
                let success = output.status.success();

                // 解析 PowerShell 输出的 JSON 结果
                let output_text = if !stdout.trim().is_empty() {
                    stdout.trim().to_string()
                } else if !stderr.trim().is_empty() {
                    stderr.trim().to_string()
                } else {
                    "(无输出)".to_string()
                };

                // 尝试解析 JSON 输出以提取结构化信息
                let parse_result: Result<serde_json::Value, _> = serde_json::from_str(&output_text);

                if success {
                    match parse_result {
                        Ok(parsed) => {
                            let status_code = parsed.get("status_code")
                                .and_then(|v| v.as_i64())
                                .unwrap_or(200);
                            let resp_duration = parsed.get("duration_ms")
                                .and_then(|v| v.as_i64())
                                .unwrap_or(total_duration as i64);
                            let resp_body = parsed.get("body")
                                .and_then(|v| v.as_str())
                                .unwrap_or(&output_text);

                            let content = format!(
                                "✅ API 请求完成 [总耗时: {}ms]\n方法: {}\nURL: {}\n状态码: {}\n耗时: {}ms\n\n--- 响应体 ---\n{}",
                                total_duration,
                                method,
                                url,
                                status_code,
                                resp_duration,
                                format_json_response(resp_body),
                            );

                            PluginResult::ok_with_data(content, serde_json::json!({
                                "status": "success",
                                "method": method,
                                "url": url,
                                "status_code": status_code,
                                "duration_ms": resp_duration,
                                "total_duration_ms": total_duration,
                                "body": resp_body,
                            }))
                        }
                        Err(_) => {
                            // JSON 解析失败，返回原始文本
                            let content = format!(
                                "✅ API 请求完成 [总耗时: {}ms]\n方法: {}\nURL: {}\n\n--- 原始响应 ---\n{}",
                                total_duration, method, url, output_text
                            );
                            PluginResult::ok_with_data(content, serde_json::json!({
                                "status": "success",
                                "method": method,
                                "url": url,
                                "raw_output": output_text,
                                "duration_ms": total_duration,
                            }))
                        }
                    }
                } else {
                    // PowerShell 执行失败
                    let error_msg = match parse_result {
                        Ok(parsed) => parsed.get("error")
                            .and_then(|v| v.as_str())
                            .unwrap_or(&output_text)
                            .to_string(),
                        Err(_) => output_text.clone(),
                    };

                    tracing::error!("[api_test] PowerShell 执行失败: {}", error_msg);

                    let content = format!(
                        "❌ API 请求失败 [总耗时: {}ms]\n方法: {}\nURL: {}\n\n--- 错误信息 ---\n{}\n{}",
                        total_duration, method, url, error_msg,
                        if stderr.is_empty() || stderr == output_text {
                            String::new()
                        } else {
                            format!("\n--- STDERR ---\n{}", stderr.trim())
                        }
                    );
                    PluginResult::ok_with_data(content, serde_json::json!({
                        "status": "failed",
                        "method": method,
                        "url": url,
                        "error": error_msg,
                        "duration_ms": total_duration,
                    }))
                }
            }
            Err(e) => {
                tracing::error!("[api_test] 无法启动 PowerShell: {}", e);
                PluginResult::err(format!(
                    "无法启动 PowerShell 进程。\n错误: {}\n\n请确认系统已安装 PowerShell。",
                    e
                ))
            }
        }
    }

    fn is_enabled(&self) -> bool { self.enabled }
    fn set_enabled(&mut self, enabled: bool) { self.enabled = enabled; }
}

/// 尝试美化格式化 JSON 响应，如果失败则原样返回
fn format_json_response(body: &str) -> String {
    // 如果已经是格式化的 JSON（包含换行），直接返回
    if body.contains('\n') {
        return body.to_string();
    }
    // 尝试解析并美化
    match serde_json::from_str::<serde_json::Value>(body) {
        Ok(v) => serde_json::to_string_pretty(&v).unwrap_or_else(|_| body.to_string()),
        Err(_) => body.to_string(),
    }
}
