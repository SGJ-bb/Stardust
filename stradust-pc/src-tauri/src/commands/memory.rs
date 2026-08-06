// 记忆管理命令：list_memories, add_memory, delete_memory, search_memories, get_memory_pool

use crate::state::AppState;
use crate::models::memory::{MemoryEntry, MemoryCategory, MemorySearchResult, MemoryPoolBlock};
use crate::utils::helpers;
use tauri::State;

/// 列出记忆
#[tauri::command]
pub async fn list_memories(state: State<'_, AppState>, persona_id: String) -> Result<Vec<MemoryEntry>, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    let conn = db.conn.lock().map_err(|e| e.to_string())?;
    crate::db::memory_repo::list_memories(&conn, &persona_id)
        .map_err(|e| e.to_string())
}

/// 添加记忆
#[tauri::command]
pub async fn add_memory(
    state: State<'_, AppState>,
    persona_id: String,
    content: String,
    category: String,
    importance: f32,
) -> Result<MemoryEntry, String> {
    let cat = serde_json::from_str(&format!("\"{}\"", category.to_lowercase()))
        .unwrap_or(MemoryCategory::Fact);

    let db = state.db.lock().map_err(|e| e.to_string())?;
    state.memory_service.add_memory_fact(&db, &persona_id, &content, cat, importance)
        .map_err(|e: crate::db::database::DbError| e.to_string())
}

/// 删除记忆
#[tauri::command]
pub async fn delete_memory(state: State<'_, AppState>, id: String) -> Result<(), String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    let conn = db.conn.lock().map_err(|e| e.to_string())?;
    crate::db::memory_repo::delete_memory(&conn, &id)
        .map_err(|e| e.to_string())
}

/// 搜索记忆
#[tauri::command]
pub async fn search_memories(state: State<'_, AppState>, persona_id: String, query: String) -> Result<Vec<MemorySearchResult>, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    state.memory_service.search_memories(&db, &persona_id, &query)
        .map_err(|e: crate::db::database::DbError| e.to_string())
}

/// 获取记忆池
#[tauri::command]
pub async fn get_memory_pool(state: State<'_, AppState>, persona_id: String, session_id: String) -> Result<MemoryPoolBlock, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    Ok(state.memory_service.get_pool_block(&db, &persona_id, &session_id))
}
