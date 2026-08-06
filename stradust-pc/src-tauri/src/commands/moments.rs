// 朋友圈命令：list_moments, create_moment, delete_moment, add_comment, toggle_like

use crate::state::AppState;
use crate::models::moments::{Moment, CreateMomentRequest, AddCommentRequest, ToggleLikeRequest};
use tauri::State;

/// 列出朋友圈
#[tauri::command]
pub async fn list_moments(state: State<'_, AppState>, persona_id: String) -> Result<Vec<Moment>, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    state.moments_service.list_moments(&db, &persona_id)
        .map_err(|e: crate::db::database::DbError| e.to_string())
}

/// 创建朋友圈
#[tauri::command]
pub async fn create_moment(state: State<'_, AppState>, request: CreateMomentRequest) -> Result<Moment, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    state.moments_service.create_moment(&db, &request)
        .map_err(|e: crate::db::database::DbError| e.to_string())
}

/// 删除朋友圈
#[tauri::command]
pub async fn delete_moment(state: State<'_, AppState>, id: String) -> Result<(), String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    state.moments_service.delete_moment(&db, &id)
        .map_err(|e: crate::db::database::DbError| e.to_string())
}

/// 添加评论
#[tauri::command]
pub async fn add_comment(state: State<'_, AppState>, request: AddCommentRequest) -> Result<crate::models::moments::Comment, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    state.moments_service.add_comment(
        &db, &request.moment_id, &request.author_id,
        &request.author_name, &request.content, request.reply_to.as_deref(),
    ).map_err(|e: crate::db::database::DbError| e.to_string())
}

/// 切换点赞
#[tauri::command]
pub async fn toggle_like(state: State<'_, AppState>, request: ToggleLikeRequest) -> Result<bool, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    state.moments_service.toggle_like(
        &db, &request.moment_id, &request.liker_id, &request.liker_name,
    ).map_err(|e: crate::db::database::DbError| e.to_string())
}
