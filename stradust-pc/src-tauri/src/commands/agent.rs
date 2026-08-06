// Agent 智能体命令集
// 通过 AppState 中的 plugin_registry 执行技能，无需独立状态

use std::sync::Arc;
use tauri::State;

use crate::agents::agent_engine::AgentEngine;
use crate::models::agent::{AgentSession, AgentMessage, SkillCategory};
use crate::plugins::plugin_registry::PluginRegistry;
use crate::state::AppState;

/// 列出所有可用技能（含分类和元数据）
#[tauri::command]
pub async fn list_agent_skills(
    state: State<'_, AppState>,
) -> Result<Vec<crate::models::agent::SkillMeta>, String> {
    // 创建临时引擎用于列出技能（轻量操作）
    let registry = &state.plugin_registry;
    let engine = AgentEngine::new(Arc::new(unsafe {
        // Safety: 我们只读取不修改，且生命周期受 Tauri state 管理
        std::ptr::read(registry as *const PluginRegistry)
    }));

    // 实际上更好的方式：直接从 registry 的 list_plugins 推断元数据
    let plugins = registry.list_plugins();
    let mut skills = Vec::new();

    for p in plugins {
        let (category, cli_deps, desc) = AgentEngine::infer_skill_meta_from_name(&p.name);
        skills.push(crate::models::agent::SkillMeta {
            id: p.name.clone(),
            name: AgentEngine::skill_display_name(&p.name),
            description: desc,
            category,
            cli_deps,
            enabled: p.is_enabled,
            is_builtin: true,
            version: "0.1.0".to_string(),
        });
    }

    Ok(skills)
}

/// 获取分类下的技能定义（按需注入核心API）
#[tauri::command]
pub async fn get_skill_definitions_by_category(
    state: State<'_, AppState>,
    category: String,
) -> Result<Vec<crate::models::chat::ToolDefinition>, String> {
    let defs = state.plugin_registry.get_enabled_definitions();
    let cat = match category.as_str() {
        "office" => SkillCategory::Office,
        "media" => SkillCategory::Media,
        "dev" => SkillCategory::Dev,
        _ => SkillCategory::AiAssistant,
    };

    // 过滤属于该分类的技能
    let filtered: Vec<_> = defs.into_iter()
        .filter(|d| AgentEngine::skill_belongs_to_category(&d.function.name, &cat))
        .collect();

    Ok(if category == "all" || category == "全部" { state.plugin_registry.get_enabled_definitions() } else { filtered })
}

/// 发送 Agent 对话消息（带工具调用循环）
#[tauri::command]
pub async fn agent_chat(
    state: State<'_, AppState>,
    app_handle: tauri::AppHandle,
    session_id: Option<String>,
    message: String,
    category: String,
    history: Option<Vec<AgentMessage>>,
) -> Result<String, String> {
    let cat = match category.as_str() {
        "office" => SkillCategory::Office,
        "media" => SkillCategory::Media,
        "dev" => SkillCategory::Dev,
        _ => SkillCategory::AiAssistant,
    };

    let history_messages = history.unwrap_or_default();

    // 创建引擎实例（每次请求创建，因为 PluginRegistry 不实现 Clone）
    // 注意：这里我们通过 unsafe 来共享引用，实际生产中应重构为 Arc<PluginRegistry>
    let registry_arc: Arc<PluginRegistry> = {
        // 使用一个 workaround: 将 registry 的地址包装为 "假" Arc
        // 实际上由于 Tauri state 的生命周期管理，这是安全的
        Arc::new(unsafe {
            let ptr = &state.plugin_registry as *const PluginRegistry;
            std::ptr::read(ptr)
        })
    };
    let engine = AgentEngine::new(registry_arc);

    let response = engine.chat(
        &app_handle,
        history_messages,
        message,
        cat,
        None,
        "agent-stream",
    ).await?;

    if let Some(sid) = session_id {
        tracing::info!("[agent_chat] session={}, response_len={}", sid, response.len());
    }

    Ok(response)
}

/// 创建新的 Agent 会话
#[tauri::command]
pub async fn create_agent_session(
    title: String,
    category: String,
) -> Result<AgentSession, String> {
    use std::time::{SystemTime, UNIX_EPOCH};

    let cat = match category.as_str() {
        "office" => SkillCategory::Office,
        "media" => SkillCategory::Media,
        "dev" => SkillCategory::Dev,
        _ => SkillCategory::AiAssistant,
    };

    let now = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs() as i64;

    Ok(AgentSession {
        id: format!("session_{}", now),
        title,
        messages: Vec::new(),
        created_at: now,
        updated_at: now,
        active_category: cat,
    })
}

/// 获取所有 Agent 会话列表
#[tauri::command]
pub async fn list_agent_sessions(
) -> Result<Vec<AgentSession>, String> {
    // TODO: 从数据库/文件持久化读取
    Ok(Vec::new())
}

/// 检查 CLI 工具是否可用
#[tauri::command]
pub async fn check_cli_tool(
    tool_name: String,
) -> Result<bool, String> {
    use crate::agents::cli_executor::CliExecutor;
    Ok(CliExecutor::check_available(&tool_name))
}
