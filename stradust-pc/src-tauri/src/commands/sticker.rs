// 表情包命令：list_stickers, search_stickers, add_sticker, delete_sticker

use crate::state::AppState;
use crate::models::sticker::Sticker;
use tauri::State;

/// 列出表情包
#[tauri::command]
pub async fn list_stickers(state: State<'_, AppState>, category: Option<String>) -> Result<Vec<Sticker>, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    state.sticker_service.list_stickers(&db, category.as_deref())
        .map_err(|e: crate::db::database::DbError| e.to_string())
}

/// 搜索表情包
#[tauri::command]
pub async fn search_stickers(state: State<'_, AppState>, query: String) -> Result<Vec<Sticker>, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    state.sticker_service.search_stickers(&db, &query)
        .map_err(|e: crate::db::database::DbError| e.to_string())
}

/// 添加表情包
#[tauri::command]
pub async fn add_sticker(
    state: State<'_, AppState>,
    name: String,
    file_path: String,
    tags: Option<Vec<String>>,
    category: Option<String>,
) -> Result<Sticker, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    state.sticker_service.add_sticker(
        &db, &name, &file_path, tags.unwrap_or_default(), category.as_deref(),
    ).map_err(|e: crate::db::database::DbError| e.to_string())
}

/// 删除表情包
#[tauri::command]
pub async fn delete_sticker(state: State<'_, AppState>, id: String) -> Result<(), String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    state.sticker_service.delete_sticker(&db, &id)
        .map_err(|e: crate::db::database::DbError| e.to_string())
}
