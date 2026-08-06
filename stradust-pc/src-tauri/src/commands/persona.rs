// 角色管理命令：list_personas, get_persona, create_persona, update_persona, delete_persona, export_personas, import_personas

use crate::state::AppState;
use crate::models::persona::{Persona, CreatePersonaRequest, UpdatePersonaRequest, PersonaExport};
use crate::utils::helpers;
use tauri::State;

/// 列出所有角色
#[tauri::command]
pub async fn list_personas(state: State<'_, AppState>) -> Result<Vec<Persona>, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    let conn = db.conn.lock().map_err(|e| e.to_string())?;
    crate::db::persona_repo::list_personas(&conn)
        .map_err(|e| e.to_string())
}

/// 获取单个角色
#[tauri::command]
pub async fn get_persona(state: State<'_, AppState>, id: String) -> Result<Persona, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    let conn = db.conn.lock().map_err(|e| e.to_string())?;
    crate::db::persona_repo::get_persona(&conn, &id)
        .map_err(|e| e.to_string())
}

/// 创建角色
#[tauri::command]
pub async fn create_persona(state: State<'_, AppState>, request: CreatePersonaRequest) -> Result<Persona, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    let conn = db.conn.lock().map_err(|e| e.to_string())?;
    crate::db::persona_repo::create_persona(&conn, &request)
        .map_err(|e| e.to_string())
}

/// 更新角色
#[tauri::command]
pub async fn update_persona(state: State<'_, AppState>, request: UpdatePersonaRequest) -> Result<Persona, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    let conn = db.conn.lock().map_err(|e| e.to_string())?;
    crate::db::persona_repo::update_persona(&conn, &request)
        .map_err(|e| e.to_string())
}

/// 删除角色
#[tauri::command]
pub async fn delete_persona(state: State<'_, AppState>, id: String) -> Result<(), String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    let conn = db.conn.lock().map_err(|e| e.to_string())?;
    crate::db::persona_repo::delete_persona(&conn, &id)
        .map_err(|e| e.to_string())
}

/// 导出角色
#[tauri::command]
pub async fn export_personas(state: State<'_, AppState>) -> Result<PersonaExport, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    let conn = db.conn.lock().map_err(|e| e.to_string())?;
    let personas = crate::db::persona_repo::list_personas(&conn)
        .map_err(|e| e.to_string())?;

    Ok(PersonaExport {
        version: "1.0".to_string(),
        exported_at: helpers::now(),
        personas,
    })
}

/// 导入角色
#[tauri::command]
pub async fn import_personas(state: State<'_, AppState>, data: PersonaExport) -> Result<u32, String> {
    let mut count = 0u32;
    for persona in &data.personas {
        let db = state.db.lock().map_err(|e| e.to_string())?;
        let conn = db.conn.lock().map_err(|e| e.to_string())?;
        let request = CreatePersonaRequest {
            name: persona.name.clone(),
            description: persona.description.clone(),
            avatar: persona.avatar.clone(),
            system_prompt: Some(persona.system_prompt.clone()),
            personality: Some(persona.personality.clone()),
            speaking_style: Some(persona.speaking_style.clone()),
            background_story: persona.background_story.clone(),
            world_lore: persona.world_lore.clone(),
            default_emotion: Some(persona.default_emotion.clone()),
            model_id: persona.model_id.clone(),
            voice_id: persona.voice_id.clone(),
            live2d_model: persona.live2d_model.clone(),
        };
        if crate::db::persona_repo::create_persona(&conn, &request).is_ok() {
            count += 1;
        }
    }
    Ok(count)
}
