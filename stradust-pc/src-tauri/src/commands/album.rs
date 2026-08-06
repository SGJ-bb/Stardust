// 纪念相册命令：list_album_entries, add_album_entry, delete_album_entry

use crate::state::AppState;
use crate::models::album::{AlbumEntry, AddAlbumEntryRequest};
use crate::utils::helpers;
use tauri::State;

/// 列出相册条目
#[tauri::command]
pub async fn list_album_entries(state: State<'_, AppState>, persona_id: String) -> Result<Vec<AlbumEntry>, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    let conn = db.conn.lock().map_err(|e| e.to_string())?;
    crate::db::album_repo::list_album_entries(&conn, &persona_id)
        .map_err(|e| e.to_string())
}

/// 添加相册条目
#[tauri::command]
pub async fn add_album_entry(state: State<'_, AppState>, request: AddAlbumEntryRequest) -> Result<AlbumEntry, String> {
    let now = helpers::now();
    let entry = AlbumEntry {
        id: helpers::new_uuid(),
        persona_id: request.persona_id,
        title: request.title,
        description: request.description,
        image_path: request.image_path,
        thumbnail: None,
        tags: request.tags.unwrap_or_default(),
        mood: request.mood,
        is_milestone: request.is_milestone.unwrap_or(false),
        event_date: request.event_date.and_then(|s| helpers::parse_datetime(&s)),
        created_at: now,
        updated_at: now,
    };

    let db = state.db.lock().map_err(|e| e.to_string())?;
    let conn = db.conn.lock().map_err(|e| e.to_string())?;
    crate::db::album_repo::add_album_entry(&conn, &entry)
        .map_err(|e| e.to_string())?;

    Ok(entry)
}

/// 删除相册条目
#[tauri::command]
pub async fn delete_album_entry(state: State<'_, AppState>, id: String) -> Result<(), String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    let conn = db.conn.lock().map_err(|e| e.to_string())?;
    crate::db::album_repo::delete_album_entry(&conn, &id)
        .map_err(|e| e.to_string())
}
