// 角色CRUD

use rusqlite::{params, Connection};

use crate::db::database::DbError;
use crate::models::persona::{Persona, CreatePersonaRequest, UpdatePersonaRequest};
use crate::utils::helpers;

/// 创建角色
pub fn create_persona(conn: &Connection, req: &CreatePersonaRequest) -> Result<Persona, DbError> {
    let id = uuid::Uuid::new_v4().to_string();
    let now = chrono::Local::now().naive_local();

    let persona = Persona {
        id: id.clone(),
        name: req.name.clone(),
        description: req.description.clone(),
        avatar: req.avatar.clone(),
        system_prompt: req.system_prompt.clone().unwrap_or_default(),
        personality: req.personality.clone().unwrap_or_default(),
        speaking_style: req.speaking_style.clone().unwrap_or_default(),
        background_story: req.background_story.clone(),
        world_lore: req.world_lore.clone(),
        default_emotion: req.default_emotion.clone().unwrap_or_else(|| "neutral".to_string()),
        model_id: req.model_id.clone(),
        voice_id: req.voice_id.clone(),
        live2d_model: req.live2d_model.clone(),
        is_default: false,
        created_at: now,
        updated_at: now,
    };

    conn.execute(
        "INSERT INTO personas (id, name, avatar, system_prompt, personality, speaking_style,
         background_story, world_lore, default_emotion, model_id, voice_id, live2d_model,
         is_default, created_at, updated_at)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15)",
        params![
            persona.id, persona.name, persona.avatar, persona.system_prompt,
            persona.personality, persona.speaking_style, persona.background_story,
            persona.world_lore, persona.default_emotion, persona.model_id,
            persona.voice_id, persona.live2d_model, persona.is_default as i32,
            persona.created_at.to_string(), persona.updated_at.to_string()
        ],
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    Ok(persona)
}

/// 获取所有角色
pub fn list_personas(conn: &Connection) -> Result<Vec<Persona>, DbError> {
    let mut stmt = conn.prepare(
        "SELECT id, name, avatar, system_prompt, personality, speaking_style,
                background_story, world_lore, default_emotion, model_id, voice_id,
                live2d_model, is_default, created_at, updated_at FROM personas ORDER BY created_at"
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    let personas = stmt.query_map([], |row| {
        Ok(Persona {
            id: row.get(0)?,
            name: row.get(1)?,
            description: None, // 数据库尚无此列，默认为 None
            avatar: row.get(2)?,
            system_prompt: row.get(3)?,
            personality: row.get(4)?,
            speaking_style: row.get(5)?,
            background_story: row.get(6)?,
            world_lore: row.get(7)?,
            default_emotion: row.get(8)?,
            model_id: row.get(9)?,
            voice_id: row.get(10)?,
            live2d_model: row.get(11)?,
            is_default: row.get::<_, i32>(12)? != 0,
            created_at: helpers::parse_dt(row.get(13)?),
            updated_at: helpers::parse_dt(row.get(14)?),
        })
    }).map_err(|e| DbError::QueryFailed(e.to_string()))?
      .filter_map(|p| p.ok())
      .collect();

    Ok(personas)
}

/// 获取单个角色
pub fn get_persona(conn: &Connection, id: &str) -> Result<Persona, DbError> {
    conn.query_row(
        "SELECT id, name, avatar, system_prompt, personality, speaking_style,
                background_story, world_lore, default_emotion, model_id, voice_id,
                live2d_model, is_default, created_at, updated_at FROM personas WHERE id = ?1",
        params![id],
        |row| {
            Ok(Persona {
                id: row.get(0)?,
                name: row.get(1)?,
                description: None, // 数据库尚无此列，默认为 None
                avatar: row.get(2)?,
                system_prompt: row.get(3)?,
                personality: row.get(4)?,
                speaking_style: row.get(5)?,
                background_story: row.get(6)?,
                world_lore: row.get(7)?,
                default_emotion: row.get(8)?,
                model_id: row.get(9)?,
                voice_id: row.get(10)?,
                live2d_model: row.get(11)?,
                is_default: row.get::<_, i32>(12)? != 0,
                created_at: helpers::parse_dt(row.get(13)?),
                updated_at: helpers::parse_dt(row.get(14)?),
            })
        },
    ).map_err(|e| match e {
        rusqlite::Error::QueryReturnedNoRows => DbError::NotFound(format!("角色 {} 不存在", id)),
        e => DbError::QueryFailed(e.to_string()),
    })
}

/// 更新角色
pub fn update_persona(conn: &Connection, req: &UpdatePersonaRequest) -> Result<Persona, DbError> {
    let mut persona = get_persona(conn, &req.id)?;
    let now = chrono::Local::now().naive_local();

    if let Some(name) = &req.name { persona.name = name.clone(); }
    if let Some(avatar) = &req.avatar { persona.avatar = Some(avatar.clone()); }
    if let Some(system_prompt) = &req.system_prompt { persona.system_prompt = system_prompt.clone(); }
    if let Some(personality) = &req.personality { persona.personality = personality.clone(); }
    if let Some(speaking_style) = &req.speaking_style { persona.speaking_style = speaking_style.clone(); }
    if let Some(background_story) = &req.background_story { persona.background_story = Some(background_story.clone()); }
    if let Some(world_lore) = &req.world_lore { persona.world_lore = Some(world_lore.clone()); }
    if let Some(default_emotion) = &req.default_emotion { persona.default_emotion = default_emotion.clone(); }
    if let Some(model_id) = &req.model_id { persona.model_id = Some(model_id.clone()); }
    if let Some(voice_id) = &req.voice_id { persona.voice_id = Some(voice_id.clone()); }
    if let Some(live2d_model) = &req.live2d_model { persona.live2d_model = Some(live2d_model.clone()); }

    persona.updated_at = now;

    conn.execute(
        "UPDATE personas SET name=?1, avatar=?2, system_prompt=?3, personality=?4,
         speaking_style=?5, background_story=?6, world_lore=?7, default_emotion=?8,
         model_id=?9, voice_id=?10, live2d_model=?11, updated_at=?12 WHERE id=?13",
        params![
            persona.name, persona.avatar, persona.system_prompt, persona.personality,
            persona.speaking_style, persona.background_story, persona.world_lore,
            persona.default_emotion, persona.model_id, persona.voice_id,
            persona.live2d_model, persona.updated_at.to_string(), persona.id
        ],
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    Ok(persona)
}

/// 删除角色
pub fn delete_persona(conn: &Connection, id: &str) -> Result<(), DbError> {
    conn.execute("DELETE FROM personas WHERE id = ?1", params![id])
        .map_err(|e| DbError::QueryFailed(e.to_string()))?;
    Ok(())
}

/// 设置默认角色
pub fn set_default_persona(conn: &Connection, id: &str) -> Result<(), DbError> {
    // 先清除所有默认
    conn.execute("UPDATE personas SET is_default = 0", [])
        .map_err(|e| DbError::QueryFailed(e.to_string()))?;
    // 设置新的默认
    conn.execute("UPDATE personas SET is_default = 1 WHERE id = ?1", params![id])
        .map_err(|e| DbError::QueryFailed(e.to_string()))?;
    Ok(())
}
