// 设置命令：get_settings, update_settings, test_llm_connection

use crate::state::AppState;
use crate::models::settings::{AppSettings, ProviderProfile, ConnectionTestResult};
use tauri::State;

/// 对API Key进行脱敏：保留前4位和后4位，中间用****替代
fn mask_api_key(key: &Option<String>) -> Option<String> {
    match key {
        Some(k) if k.len() > 8 => {
            let prefix = &k[..4];
            let suffix = &k[k.len()-4..];
            Some(format!("{}****{}", prefix, suffix))
        }
        Some(k) if !k.is_empty() => Some("****".to_string()),
        _ => None,
    }
}

/// 对供应商配置中的API Key进行脱敏
fn mask_provider(provider: &ProviderProfile) -> ProviderProfile {
    ProviderProfile {
        api_key: mask_api_key(&provider.api_key),
        ..provider.clone()
    }
}

/// 获取设置
#[tauri::command]
pub async fn get_settings(state: State<'_, AppState>) -> Result<AppSettings, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    let conn = db.conn.lock().map_err(|e| e.to_string())?;
    crate::db::settings_repo::get_all_settings(&conn)
        .map_err(|e| e.to_string())
}

/// 更新设置
#[tauri::command]
pub async fn update_settings(state: State<'_, AppState>, key: String, value: serde_json::Value) -> Result<(), String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    let conn = db.conn.lock().map_err(|e| e.to_string())?;
    let value_str = match &value {
        serde_json::Value::String(s) => s.clone(),
        other => other.to_string(),
    };
    crate::db::settings_repo::set_setting(&conn, &key, &value_str)
        .map_err(|e| e.to_string())
}

/// 测试LLM连接
#[tauri::command]
pub async fn test_llm_connection(state: State<'_, AppState>, provider: ProviderProfile) -> Result<ConnectionTestResult, String> {
    state.llm_service.test_connection(&provider)
        .await
        .map_err(|e: crate::services::llm_service::LlmError| e.to_string())
}

/// 获取供应商列表（API Key脱敏后返回）
#[tauri::command]
pub async fn list_providers(state: State<'_, AppState>) -> Result<Vec<ProviderProfile>, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    let conn = db.conn.lock().map_err(|e| e.to_string())?;
    let providers = crate::db::settings_repo::list_providers(&conn)
        .map_err(|e| e.to_string())?;
    // 脱敏API Key后再返回给前端
    Ok(providers.iter().map(mask_provider).collect())
}

/// 创建供应商
#[tauri::command]
pub async fn create_provider(state: State<'_, AppState>, provider: ProviderProfile) -> Result<(), String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    let conn = db.conn.lock().map_err(|e| e.to_string())?;
    crate::db::settings_repo::create_provider(&conn, &provider)
        .map_err(|e| e.to_string())
}

/// 删除供应商
#[tauri::command]
pub async fn delete_provider(state: State<'_, AppState>, id: String) -> Result<(), String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    let conn = db.conn.lock().map_err(|e| e.to_string())?;
    crate::db::settings_repo::delete_provider(&conn, &id)
        .map_err(|e| e.to_string())
}
