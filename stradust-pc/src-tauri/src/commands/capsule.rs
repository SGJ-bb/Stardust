// 时光胶囊命令：list_capsules, create_capsule, open_capsule

use crate::state::AppState;
use crate::models::capsule::{TimeCapsule, CreateCapsuleRequest};
use crate::utils::helpers;
use tauri::State;

/// 列出时光胶囊
#[tauri::command]
pub async fn list_capsules(state: State<'_, AppState>, persona_id: String) -> Result<Vec<TimeCapsule>, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    let conn = db.conn.lock().map_err(|e| e.to_string())?;
    crate::db::capsule_repo::list_capsules(&conn, &persona_id)
        .map_err(|e| e.to_string())
}

/// 创建时光胶囊
#[tauri::command]
pub async fn create_capsule(state: State<'_, AppState>, request: CreateCapsuleRequest) -> Result<TimeCapsule, String> {
    let now = helpers::now();
    let open_at = helpers::parse_datetime(&request.open_at)
        .ok_or("无效的打开时间格式")?;

    let capsule = TimeCapsule {
        id: helpers::new_uuid(),
        persona_id: request.persona_id,
        title: request.title,
        content: request.content,
        mood: request.mood,
        images: request.images.unwrap_or_default(),
        sealed_at: now,
        open_at,
        is_opened: false,
        opened_at: None,
        created_at: now,
    };

    let db = state.db.lock().map_err(|e| e.to_string())?;
    let conn = db.conn.lock().map_err(|e| e.to_string())?;
    crate::db::capsule_repo::create_capsule(&conn, &capsule)
        .map_err(|e| e.to_string())?;

    Ok(capsule)
}

/// 打开时光胶囊
#[tauri::command]
pub async fn open_capsule(state: State<'_, AppState>, id: String) -> Result<TimeCapsule, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    let conn = db.conn.lock().map_err(|e| e.to_string())?;
    crate::db::capsule_repo::open_capsule(&conn, &id)
        .map_err(|e| e.to_string())
}
