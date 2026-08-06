// 成就命令：list_achievements, check_in, get_affection, update_affection

use crate::state::AppState;
use crate::models::achievement::{Achievement, CheckIn, AffectionData, Growth};
use tauri::State;

/// 列出成就
#[tauri::command]
pub async fn list_achievements(state: State<'_, AppState>, persona_id: String) -> Result<Vec<Achievement>, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    state.stats_service.list_achievements(&db, &persona_id)
        .map_err(|e: crate::db::database::DbError| e.to_string())
}

/// 签到
#[tauri::command]
pub async fn check_in(state: State<'_, AppState>, persona_id: String) -> Result<CheckIn, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    state.stats_service.check_in(&db, &persona_id)
        .map_err(|e: crate::db::database::DbError| e.to_string())
}

/// 获取好感度
#[tauri::command]
pub async fn get_affection(state: State<'_, AppState>, persona_id: String) -> Result<AffectionData, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    state.stats_service.get_affection(&db, &persona_id)
        .map_err(|e: crate::db::database::DbError| e.to_string())
}

/// 更新好感度
#[tauri::command]
pub async fn update_affection(
    state: State<'_, AppState>,
    persona_id: String,
    exp_delta: i32,
    trust_delta: f32,
    intimacy_delta: f32,
) -> Result<AffectionData, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    state.stats_service.update_affection(&db, &persona_id, exp_delta, trust_delta, intimacy_delta)
        .map_err(|e: crate::db::database::DbError| e.to_string())
}
