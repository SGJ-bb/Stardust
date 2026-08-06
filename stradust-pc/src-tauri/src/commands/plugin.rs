// 插件命令：list_plugins, toggle_plugin, execute_plugin

use crate::state::AppState;
use crate::plugins::plugin_registry::PluginInfo;
use crate::plugins::plugin_trait::PluginContext;
use tauri::State;

/// 列出插件
#[tauri::command]
pub async fn list_plugins(state: State<'_, AppState>) -> Result<Vec<PluginInfo>, String> {
    Ok(state.plugin_registry.list_plugins())
}

/// 切换插件
#[tauri::command]
pub async fn toggle_plugin(state: State<'_, AppState>, name: String, enabled: bool) -> Result<bool, String> {
    Ok(state.plugin_registry.toggle_plugin(&name, enabled))
}

/// 执行插件
#[tauri::command]
pub async fn execute_plugin(
    state: State<'_, AppState>,
    name: String,
    arguments: serde_json::Value,
    persona_id: String,
    session_id: String,
) -> Result<crate::plugins::plugin_trait::PluginResult, String> {
    let context = PluginContext {
        persona_id,
        session_id,
        extra: Default::default(),
    };

    let result = state.plugin_registry.execute_plugin(&name, &arguments, &context)
        .await;
    if result.success {
        Ok(result)
    } else {
        Err(result.content)
    }
}
