// 虚拟世界命令：get_virtual_world, update_world_config, run_world_tick, toggle_world, get_story_events

use crate::state::AppState;
use crate::models::virtual_world::{WorldConfig, StoryEvent, UpdateWorldConfigRequest};
use tauri::State;

/// 获取虚拟世界
#[tauri::command]
pub async fn get_virtual_world(state: State<'_, AppState>, persona_id: String) -> Result<Option<WorldConfig>, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    state.virtual_world_service.get_virtual_world(&db, &persona_id)
        .map_err(|e: crate::db::database::DbError| e.to_string())
}

/// 更新世界配置
#[tauri::command]
pub async fn update_world_config(state: State<'_, AppState>, config: WorldConfig) -> Result<(), String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    state.virtual_world_service.update_world_config(&db, &config)
        .map_err(|e: crate::db::database::DbError| e.to_string())
}

/// 执行世界推演
#[tauri::command]
pub async fn run_world_tick(state: State<'_, AppState>, world_id: String) -> Result<Vec<StoryEvent>, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    state.virtual_world_service.run_world_tick(&db, &world_id)
        .map_err(|e: crate::db::database::DbError| e.to_string())
}

/// 切换世界运行
#[tauri::command]
pub async fn toggle_world(state: State<'_, AppState>, world_id: String) -> Result<bool, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    state.virtual_world_service.toggle_world(&db, &world_id)
        .map_err(|e: crate::db::database::DbError| e.to_string())
}

/// 获取故事事件
#[tauri::command]
pub async fn get_story_events(state: State<'_, AppState>, world_id: String, limit: Option<u32>) -> Result<Vec<StoryEvent>, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    state.virtual_world_service.get_story_events(&db, &world_id, limit)
        .map_err(|e: crate::db::database::DbError| e.to_string())
}
