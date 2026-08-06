// 日历命令：list_events, add_event, delete_event

use crate::state::AppState;
use crate::models::calendar::{CalendarEvent, AddEventRequest};
use crate::utils::helpers;
use tauri::State;

/// 列出日历事件
#[tauri::command]
pub async fn list_events(
    state: State<'_, AppState>,
    persona_id: String,
    start: Option<String>,
    end: Option<String>,
) -> Result<Vec<CalendarEvent>, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    let conn = db.conn.lock().map_err(|e| e.to_string())?;
    crate::db::calendar_repo::list_events(&conn, &persona_id, start.as_deref(), end.as_deref())
        .map_err(|e| e.to_string())
}

/// 添加日历事件
#[tauri::command]
pub async fn add_event(state: State<'_, AppState>, request: AddEventRequest) -> Result<CalendarEvent, String> {
    let now = helpers::now();
    let start_time = helpers::parse_datetime(&request.start_time)
        .ok_or("无效的开始时间格式")?;

    let event = CalendarEvent {
        id: helpers::new_uuid(),
        persona_id: request.persona_id,
        title: request.title,
        description: request.description,
        event_type: request.event_type,
        start_time,
        end_time: request.end_time.and_then(|s| helpers::parse_datetime(&s)),
        is_all_day: request.is_all_day.unwrap_or(false),
        recurrence: request.recurrence,
        reminder_minutes: request.reminder_minutes,
        color: request.color,
        created_at: now,
        updated_at: now,
    };

    let db = state.db.lock().map_err(|e| e.to_string())?;
    let conn = db.conn.lock().map_err(|e| e.to_string())?;
    crate::db::calendar_repo::add_event(&conn, &event)
        .map_err(|e| e.to_string())?;

    Ok(event)
}

/// 删除日历事件
#[tauri::command]
pub async fn delete_event(state: State<'_, AppState>, id: String) -> Result<(), String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    let conn = db.conn.lock().map_err(|e| e.to_string())?;
    crate::db::calendar_repo::delete_event(&conn, &id)
        .map_err(|e| e.to_string())
}
