// 日记命令：list_diaries, generate_diary, delete_diary, export_diaries, import_diaries

use crate::state::AppState;
use crate::models::diary::{DiaryEntry, DiaryStyle, DiaryExport};
use tauri::State;

/// 列出日记
#[tauri::command]
pub async fn list_diaries(state: State<'_, AppState>, persona_id: String) -> Result<Vec<DiaryEntry>, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    state.diary_service.list_diaries(&db, &persona_id)
        .map_err(|e: crate::db::database::DbError| e.to_string())
}

/// 生成日记（调用 LLM 生成内容）
#[tauri::command]
pub async fn generate_diary(
    state: State<'_, AppState>,
    persona_id: String,
    date: Option<String>,
    style: Option<String>,
) -> Result<DiaryEntry, String> {
    let diary_style = match style.as_deref() {
        Some("emotional") => DiaryStyle::Emotional,
        Some("narrative") => DiaryStyle::Narrative,
        Some("poetic") => DiaryStyle::Poetic,
        _ => DiaryStyle::Daily,
    };

    let date_str = date.unwrap_or_else(|| {
        chrono::Local::now().format("%Y-%m-%d").to_string()
    });

    // 获取角色信息（锁在块结束时释放）
    let persona = {
        let db = state.db.lock().map_err(|e| e.to_string())?;
        let conn = db.conn.lock().map_err(|e| e.to_string())?;
        crate::db::persona_repo::get_persona(&conn, &persona_id)
            .map_err(|e| e.to_string())?
    };

    // 获取供应商配置（锁在块结束时释放）
    let provider = {
        let db = state.db.lock().map_err(|e| e.to_string())?;
        let conn = db.conn.lock().map_err(|e| e.to_string())?;
        let settings = crate::db::settings_repo::get_all_settings(&conn)
            .map_err(|e| e.to_string())?;
        match settings.active_provider_id {
            Some(ref id) => crate::db::settings_repo::get_provider(&conn, id)
                .map_err(|e| e.to_string())?,
            None => return Err("未配置LLM供应商".to_string()),
        }
    };

    // 调用 LLM 生成日记（异步操作，不能持有数据库锁）
    let diary = state.diary_service.generate_diary_content(
        &persona_id, &persona.name, &date_str, diary_style,
        "今天和主人度过了愉快的一天",
        &provider, &state.llm_service,
    ).await.map_err(|e: crate::db::database::DbError| e.to_string())?;

    // 保存日记到数据库（重新获取锁）
    {
        let db = state.db.lock().map_err(|e| e.to_string())?;
        let conn = db.conn.lock().map_err(|e| e.to_string())?;
        crate::db::diary_repo::create_diary(&conn, &diary)
            .map_err(|e| e.to_string())?;
    }

    Ok(diary)
}

/// 删除日记
#[tauri::command]
pub async fn delete_diary(state: State<'_, AppState>, id: String) -> Result<(), String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    state.diary_service.delete_diary(&db, &id)
        .map_err(|e: crate::db::database::DbError| e.to_string())
}

/// 导出日记
#[tauri::command]
pub async fn export_diaries(state: State<'_, AppState>, persona_id: String) -> Result<DiaryExport, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    state.diary_service.export_diaries(&db, &persona_id)
        .map_err(|e: crate::db::database::DbError| e.to_string())
}

/// 导入日记
#[tauri::command]
pub async fn import_diaries(state: State<'_, AppState>, data: DiaryExport) -> Result<u32, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    state.diary_service.import_diaries(&db, &data)
        .map_err(|e: crate::db::database::DbError| e.to_string())
}
