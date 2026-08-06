// 虚拟世界CRUD

use rusqlite::{params, Connection};

use crate::db::database::DbError;
use crate::models::virtual_world::{WorldConfig, WorldState, StoryEvent, StoryEventType};
use crate::utils::helpers;

/// 保存世界配置
pub fn save_world_config(conn: &Connection, config: &WorldConfig) -> Result<(), DbError> {
    conn.execute(
        "INSERT OR REPLACE INTO world_configs (id, persona_id, name, description, rules, era, genre,
         auto_tick_enabled, tick_interval_minutes, max_events_per_tick, created_at, updated_at)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12)",
        params![
            config.id, config.persona_id, config.name, config.description,
            serde_json::to_string(&config.rules).unwrap_or_default(),
            config.era, config.genre, config.auto_tick_enabled as i32,
            config.tick_interval_minutes, config.max_events_per_tick,
            config.created_at.to_string(), config.updated_at.to_string()
        ],
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    Ok(())
}

/// 获取世界配置（按 persona_id）
pub fn get_world_config(conn: &Connection, persona_id: &str) -> Result<Option<WorldConfig>, DbError> {
    let result = conn.query_row(
        "SELECT id, persona_id, name, description, rules, era, genre,
                auto_tick_enabled, tick_interval_minutes, max_events_per_tick, created_at, updated_at
         FROM world_configs WHERE persona_id = ?1",
        params![persona_id],
        |row| {
            let rules_str: String = row.get(4)?;
            Ok(WorldConfig {
                id: row.get(0)?,
                persona_id: row.get(1)?,
                name: row.get(2)?,
                description: row.get(3)?,
                rules: serde_json::from_str(&rules_str).unwrap_or_default(),
                era: row.get(5)?,
                genre: row.get(6)?,
                auto_tick_enabled: row.get::<_, i32>(7)? != 0,
                tick_interval_minutes: row.get(8)?,
                max_events_per_tick: row.get(9)?,
                created_at: helpers::parse_dt(row.get(10)?),
                updated_at: helpers::parse_dt(row.get(11)?),
            })
        },
    );

    match result {
        Ok(config) => Ok(Some(config)),
        Err(rusqlite::Error::QueryReturnedNoRows) => Ok(None),
        Err(e) => Err(DbError::QueryFailed(e.to_string())),
    }
}

/// 根据世界ID获取世界配置（world_configs.id = world_id）
/// virtual_world_service 中 should_tick 使用此方法
pub fn get_world_config_by_id(conn: &Connection, world_id: &str) -> Result<Option<WorldConfig>, DbError> {
    let result = conn.query_row(
        "SELECT id, persona_id, name, description, rules, era, genre,
                auto_tick_enabled, tick_interval_minutes, max_events_per_tick, created_at, updated_at
         FROM world_configs WHERE id = ?1",
        params![world_id],
        |row| {
            let rules_str: String = row.get(4)?;
            Ok(WorldConfig {
                id: row.get(0)?,
                persona_id: row.get(1)?,
                name: row.get(2)?,
                description: row.get(3)?,
                rules: serde_json::from_str(&rules_str).unwrap_or_default(),
                era: row.get(5)?,
                genre: row.get(6)?,
                auto_tick_enabled: row.get::<_, i32>(7)? != 0,
                tick_interval_minutes: row.get(8)?,
                max_events_per_tick: row.get(9)?,
                created_at: helpers::parse_dt(row.get(10)?),
                updated_at: helpers::parse_dt(row.get(11)?),
            })
        },
    );

    match result {
        Ok(config) => Ok(Some(config)),
        Err(rusqlite::Error::QueryReturnedNoRows) => Ok(None),
        Err(e) => Err(DbError::QueryFailed(e.to_string())),
    }
}

/// 保存世界状态
pub fn save_world_state(conn: &Connection, state: &WorldState) -> Result<(), DbError> {
    conn.execute(
        "INSERT OR REPLACE INTO world_states (id, world_id, persona_id, current_situation, characters,
         environment, timeline, tick_count, last_tick_at, is_running, created_at, updated_at)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12)",
        params![
            state.id, state.world_id, state.persona_id, state.current_situation,
            serde_json::to_string(&state.characters).unwrap_or_default(),
            serde_json::to_string(&state.environment).unwrap_or_default(),
            serde_json::to_string(&state.timeline).unwrap_or_default(),
            state.tick_count, state.last_tick_at.to_string(),
            state.is_running as i32, state.created_at.to_string(), state.updated_at.to_string()
        ],
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    Ok(())
}

/// 获取世界状态
pub fn get_world_state(conn: &Connection, world_id: &str) -> Result<Option<WorldState>, DbError> {
    let result = conn.query_row(
        "SELECT id, world_id, persona_id, current_situation, characters, environment,
                timeline, tick_count, last_tick_at, is_running, created_at, updated_at
         FROM world_states WHERE world_id = ?1",
        params![world_id],
        |row| {
            let chars_str: String = row.get(4)?;
            let env_str: String = row.get(5)?;
            let timeline_str: String = row.get(6)?;
            Ok(WorldState {
                id: row.get(0)?,
                world_id: row.get(1)?,
                persona_id: row.get(2)?,
                current_situation: row.get(3)?,
                characters: serde_json::from_str(&chars_str).unwrap_or_default(),
                environment: serde_json::from_str(&env_str).unwrap_or_default(),
                timeline: serde_json::from_str(&timeline_str).unwrap_or_default(),
                tick_count: row.get(7)?,
                last_tick_at: helpers::parse_dt(row.get(8)?),
                is_running: row.get::<_, i32>(9)? != 0,
                created_at: helpers::parse_dt(row.get(10)?),
                updated_at: helpers::parse_dt(row.get(11)?),
            })
        },
    );

    match result {
        Ok(state) => Ok(Some(state)),
        Err(rusqlite::Error::QueryReturnedNoRows) => Ok(None),
        Err(e) => Err(DbError::QueryFailed(e.to_string())),
    }
}

/// 保存故事事件
pub fn save_story_event(conn: &Connection, event: &StoryEvent) -> Result<(), DbError> {
    conn.execute(
        "INSERT INTO story_events (id, world_id, tick_number, event_type, title, description,
         participants, impact, timestamp)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9)",
        params![
            event.id, event.world_id, event.tick_number,
            serde_json::to_string(&event.event_type).unwrap_or_else(|_| "\"daily\"".to_string()),
            event.title, event.description,
            serde_json::to_string(&event.participants).unwrap_or_default(),
            event.impact, event.timestamp.to_string()
        ],
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    Ok(())
}

/// 获取故事事件
pub fn get_story_events(conn: &Connection, world_id: &str, limit: Option<u32>) -> Result<Vec<StoryEvent>, DbError> {
    let sql = match limit {
        Some(l) => format!(
            "SELECT id, world_id, tick_number, event_type, title, description, participants, impact, timestamp
             FROM story_events WHERE world_id = ?1 ORDER BY timestamp DESC LIMIT {}", l
        ),
        None => "SELECT id, world_id, tick_number, event_type, title, description, participants, impact, timestamp
                 FROM story_events WHERE world_id = ?1 ORDER BY timestamp DESC".to_string(),
    };

    let mut stmt = conn.prepare(&sql).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    let events = stmt.query_map(params![world_id], |row| {
        let event_type_str: String = row.get(3)?;
        let participants_str: String = row.get(6)?;
        Ok(StoryEvent {
            id: row.get(0)?,
            world_id: row.get(1)?,
            tick_number: row.get(2)?,
            event_type: serde_json::from_str(&event_type_str).unwrap_or(StoryEventType::Daily),
            title: row.get(4)?,
            description: row.get(5)?,
            participants: serde_json::from_str(&participants_str).unwrap_or_default(),
            impact: row.get(7)?,
            timestamp: helpers::parse_dt(row.get(8)?),
        })
    }).map_err(|e| DbError::QueryFailed(e.to_string()))?
      .filter_map(|e| e.ok())
      .collect();

    Ok(events)
}
