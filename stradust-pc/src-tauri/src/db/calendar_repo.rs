// 日历CRUD

use rusqlite::{params, Connection};

use crate::db::database::DbError;
use crate::models::calendar::{CalendarEvent, CalendarEventType};

/// 添加日历事件
pub fn add_event(conn: &Connection, event: &CalendarEvent) -> Result<(), DbError> {
    conn.execute(
        "INSERT INTO calendar_events (id, persona_id, title, description, event_type, start_time,
         end_time, is_all_day, recurrence, reminder_minutes, color, created_at, updated_at)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13)",
        params![
            event.id, event.persona_id, event.title, event.description,
            serde_json::to_string(&event.event_type).unwrap_or_else(|_| "\"schedule\"".to_string()),
            event.start_time.to_string(),
            event.end_time.map(|t| t.to_string()),
            event.is_all_day as i32, event.recurrence, event.reminder_minutes,
            event.color, event.created_at.to_string(), event.updated_at.to_string()
        ],
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    Ok(())
}

/// 列出日历事件
pub fn list_events(conn: &Connection, persona_id: &str, start: Option<&str>, end: Option<&str>) -> Result<Vec<CalendarEvent>, DbError> {
    let events: Vec<CalendarEvent> = if let (Some(s), Some(e)) = (start, end) {
        let sql = "SELECT id, persona_id, title, description, event_type, start_time, end_time,
                    is_all_day, recurrence, reminder_minutes, color, created_at, updated_at
             FROM calendar_events WHERE persona_id = ?1 AND start_time >= ?2 AND start_time <= ?3
             ORDER BY start_time";
        let mut stmt = conn.prepare(sql).map_err(|e| DbError::QueryFailed(e.to_string()))?;
        let rows: Vec<CalendarEvent> = stmt.query_map(params![persona_id, s, e], |row| row_to_event(row))
            .map_err(|e| DbError::QueryFailed(e.to_string()))?
            .filter_map(|r| r.ok()).collect();
        rows
    } else {
        let sql = "SELECT id, persona_id, title, description, event_type, start_time, end_time,
              is_all_day, recurrence, reminder_minutes, color, created_at, updated_at
              FROM calendar_events WHERE persona_id = ?1 ORDER BY start_time";
        let mut stmt = conn.prepare(sql).map_err(|e| DbError::QueryFailed(e.to_string()))?;
        let rows: Vec<CalendarEvent> = stmt.query_map(params![persona_id], |row| row_to_event(row))
            .map_err(|e| DbError::QueryFailed(e.to_string()))?
            .filter_map(|r| r.ok()).collect();
        rows
    };

    Ok(events)
}

/// 删除日历事件
pub fn delete_event(conn: &Connection, id: &str) -> Result<(), DbError> {
    conn.execute("DELETE FROM calendar_events WHERE id = ?1", params![id])
        .map_err(|e| DbError::QueryFailed(e.to_string()))?;
    Ok(())
}

fn row_to_event(row: &rusqlite::Row) -> rusqlite::Result<CalendarEvent> {
    let type_str: String = row.get(4)?;
    let end_time_str: Option<String> = row.get(6)?;
    Ok(CalendarEvent {
        id: row.get(0)?,
        persona_id: row.get(1)?,
        title: row.get(2)?,
        description: row.get(3)?,
        event_type: serde_json::from_str(&type_str).unwrap_or(CalendarEventType::Schedule),
        start_time: parse_dt(row.get(5)?),
        end_time: end_time_str.map(|s| parse_dt(s)),
        is_all_day: row.get::<_, i32>(7)? != 0,
        recurrence: row.get(8)?,
        reminder_minutes: row.get(9)?,
        color: row.get(10)?,
        created_at: parse_dt(row.get(11)?),
        updated_at: parse_dt(row.get(12)?),
    })
}

fn parse_dt(s: String) -> chrono::NaiveDateTime {
    chrono::NaiveDateTime::parse_from_str(&s, "%Y-%m-%d %H:%M:%S")
        .unwrap_or_else(|_| chrono::Local::now().naive_local())
}
