// 像素宠物数据库操作

use crate::models::pixelpet::{
    PetAction, PixelFrame, PixelPet, PixelGenConfig,
};
use rusqlite::{params, Connection, Result as SqlResult};

const SCHEMA_SQL: &str = r#"
CREATE TABLE IF NOT EXISTS pixel_pets (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT,
    reference_image_path TEXT,
    base_prompt TEXT NOT NULL,
    negative_prompt TEXT,
    sprite_width INTEGER DEFAULT 64,
    sprite_height INTEGER DEFAULT 64,
    fps INTEGER DEFAULT 8,
    scale REAL DEFAULT 3.0,
    render_mode TEXT DEFAULT 'pixel_perfect',
    is_active INTEGER DEFAULT 0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS pet_actions (
    id TEXT PRIMARY KEY,
    pet_id TEXT NOT NULL REFERENCES pixel_pets(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    display_name TEXT NOT NULL,
    description TEXT,
    prompt TEXT NOT NULL,
    frame_count INTEGER DEFAULT 4,
    frame_duration INTEGER DEFAULT 125,
    loop_mode TEXT DEFAULT 'loop',
    is_builtin INTEGER DEFAULT 0,
    trigger_events TEXT,
    sort_order INTEGER DEFAULT 0,
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS pixel_frames (
    id TEXT PRIMARY KEY,
    action_id TEXT NOT NULL REFERENCES pet_actions(id) ON DELETE CASCADE,
    frame_index INTEGER NOT NULL,
    image_path TEXT NOT NULL,
    image_hash TEXT,
    prompt_used TEXT,
    status TEXT DEFAULT 'generating',
    generated_at INTEGER,
    UNIQUE(action_id, frame_index)
);

CREATE TABLE IF NOT EXISTS pixel_gen_configs (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    provider TEXT DEFAULT 'custom',
    api_url TEXT,
    api_key TEXT,
    model TEXT,
    style_prompt TEXT DEFAULT '',
    size TEXT DEFAULT '64x64',
    steps INTEGER DEFAULT 20,
    cfg_scale REAL DEFAULT 7.0,
    batch_size INTEGER DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_actions_pet_id ON pet_actions(pet_id);
CREATE INDEX IF NOT EXISTS idx_frames_action_id ON pixel_frames(action_id);
"#;

pub struct PixelPetRepo;

impl PixelPetRepo {
    /// 初始化表结构
    pub fn init(db: &Connection) -> SqlResult<()> {
        db.execute_batch(SCHEMA_SQL)?;
        Ok(())
    }

    // ═══════════════════════════════════════
    // 宠物 CRUD
    // ═══════════════════════════════════════

    pub fn list_pets(db: &Connection) -> SqlResult<Vec<PixelPet>> {
        let mut stmt = db.prepare(
            "SELECT id, name, description, reference_image_path, base_prompt, negative_prompt,
             sprite_width, sprite_height, fps, scale, render_mode, is_active, created_at, updated_at
             FROM pixel_pets ORDER BY created_at DESC"
        )?;
        let rows = stmt.query_map([], |row| {
            Ok(PixelPet {
                id: row.get(0)?,
                name: row.get(1)?,
                description: row.get(2)?,
                reference_image_path: row.get(3)?,
                base_prompt: row.get(4)?,
                negative_prompt: row.get(5)?,
                sprite_width: row.get(6)?,
                sprite_height: row.get(7)?,
                fps: row.get(8)?,
                scale: row.get(9)?,
                render_mode: row.get(10)?,
                is_active: row.get::<_, i32>(11)? != 0,
                created_at: row.get(12)?,
                updated_at: row.get(13)?,
            })
        })?;
        rows.collect()
    }

    pub fn get_pet(db: &Connection, id: &str) -> SqlResult<Option<PixelPet>> {
        let mut stmt = db.prepare(
            "SELECT id, name, description, reference_image_path, base_prompt, negative_prompt,
             sprite_width, sprite_height, fps, scale, render_mode, is_active, created_at, updated_at
             FROM pixel_pets WHERE id = ?"
        )?;
        let mut rows = stmt.query_map(params![id], |row| {
            Ok(PixelPet {
                id: row.get(0)?,
                name: row.get(1)?,
                description: row.get(2)?,
                reference_image_path: row.get(3)?,
                base_prompt: row.get(4)?,
                negative_prompt: row.get(5)?,
                sprite_width: row.get(6)?,
                sprite_height: row.get(7)?,
                fps: row.get(8)?,
                scale: row.get(9)?,
                render_mode: row.get(10)?,
                is_active: row.get::<_, i32>(11)? != 0,
                created_at: row.get(12)?,
                updated_at: row.get(13)?,
            })
        })?;
        match rows.next() {
            Some(r) => r.map(Some),
            None => Ok(None),
        }
    }

    pub fn get_active_pet(db: &Connection) -> SqlResult<Option<PixelPet>> {
        let mut stmt = db.prepare(
            "SELECT id, name, description, reference_image_path, base_prompt, negative_prompt,
             sprite_width, sprite_height, fps, scale, render_mode, is_active, created_at, updated_at
             FROM pixel_pets WHERE is_active = 1 LIMIT 1"
        )?;
        let mut rows = stmt.query_map([], |row| {
            Ok(PixelPet {
                id: row.get(0)?,
                name: row.get(1)?,
                description: row.get(2)?,
                reference_image_path: row.get(3)?,
                base_prompt: row.get(4)?,
                negative_prompt: row.get(5)?,
                sprite_width: row.get(6)?,
                sprite_height: row.get(7)?,
                fps: row.get(8)?,
                scale: row.get(9)?,
                render_mode: row.get(10)?,
                is_active: true,
                created_at: row.get(12)?,
                updated_at: row.get(13)?,
            })
        })?;
        match rows.next() {
            Some(r) => r.map(Some),
            None => Ok(None),
        }
    }

    pub fn insert_pet(db: &Connection, pet: &PixelPet) -> SqlResult<()> {
        db.execute(
            "INSERT INTO pixel_pets (id, name, description, reference_image_path, base_prompt,
             negative_prompt, sprite_width, sprite_height, fps, scale, render_mode, is_active,
             created_at, updated_at) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14)",
            params![
                pet.id, pet.name, pet.description, pet.reference_image_path,
                pet.base_prompt, pet.negative_prompt, pet.sprite_width, pet.sprite_height,
                pet.fps, pet.scale, pet.render_mode, pet.is_active as i32,
                pet.created_at, pet.updated_at,
            ],
        )?;
        Ok(())
    }

    pub fn update_pet(db: &Connection, pet: &PixelPet) -> SqlResult<()> {
        db.execute(
            "UPDATE pixel_pets SET name=?1, description=?2, reference_image_path=?3,
             base_prompt=?4, negative_prompt=?5, sprite_width=?6, sprite_height=?7,
             fps=?8, scale=?9, render_mode=?10, is_active=?11, updated_at=?12 WHERE id=?13",
            params![
                pet.name, pet.description, pet.reference_image_path, pet.base_prompt,
                pet.negative_prompt, pet.sprite_width, pet.sprite_height, pet.fps,
                pet.scale, pet.render_mode, pet.is_active as i32, pet.updated_at, pet.id,
            ],
        )?;
        Ok(())
    }

    pub fn set_active_pet(db: &Connection, id: &str) -> SqlResult<()> {
        // 先取消所有活跃状态
        db.execute("UPDATE pixel_pets SET is_active = 0", [])?;
        // 设为活跃
        db.execute(
            "UPDATE pixel_pets SET is_active = 1, updated_at = ?1 WHERE id = ?2",
            params![chrono::Utc::now().timestamp(), id],
        )?;
        Ok(())
    }

    pub fn delete_pet(db: &Connection, id: &str) -> SqlResult<()> {
        db.execute("DELETE FROM pixel_pets WHERE id = ?", params![id])?;
        Ok(())
    }

    // ═══════════════════════════════════════
    // 动作 CRUD
    // ═══════════════════════════════════════

    pub fn list_actions(db: &Connection, pet_id: &str) -> SqlResult<Vec<PetAction>> {
        let mut stmt = db.prepare(
            "SELECT id, pet_id, name, display_name, description, prompt, frame_count,
             frame_duration, loop_mode, is_builtin, trigger_events, sort_order, created_at
             FROM pet_actions WHERE pet_id = ?1 ORDER BY sort_order ASC"
        )?;
        let rows = stmt.query_map(params![pet_id], |row| {
            Ok(PetAction {
                id: row.get(0)?,
                pet_id: row.get(1)?,
                name: row.get(2)?,
                display_name: row.get(3)?,
                description: row.get(4)?,
                prompt: row.get(5)?,
                frame_count: row.get(6)?,
                frame_duration: row.get(7)?,
                loop_mode: row.get(8)?,
                is_builtin: row::<_, i32>(9)? != 0,
                trigger_events: row.get(10)?,
                sort_order: row.get(11)?,
                created_at: row.get(12)?,
                frames: Vec::new(),
            })
        })?;
        rows.collect()
    }

    pub fn insert_action(db: &Connection, action: &PetAction) -> SqlResult<()> {
        db.execute(
            "INSERT INTO pet_actions (id, pet_id, name, display_name, description, prompt,
             frame_count, frame_duration, loop_mode, is_builtin, trigger_events, sort_order, created_at)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13)",
            params![
                action.id, action.pet_id, action.name, action.display_name,
                action.description, action.prompt, action.frame_count, action.frame_duration,
                action.loop_mode, action.is_builtin as i32, action.trigger_events,
                action.sort_order, action.created_at,
            ],
        )?;
        Ok(())
    }

    pub fn update_action(db: &Connection, action: &PetAction) -> SqlResult<()> {
        db.execute(
            "UPDATE pet_actions SET name=?1, display_name=?2, description=?3, prompt=?4,
             frame_count=?5, frame_duration=?6, loop_mode=?7, is_builtin=?8,
             trigger_events=?9, sort_order=?10 WHERE id=?11",
            params![
                action.name, action.display_name, action.description, action.prompt,
                action.frame_count, action.frame_duration, action.loop_mode,
                action.is_builtin as i32, action.trigger_events, action.sort_order, action.id,
            ],
        )?;
        Ok(())
    }

    pub fn delete_action(db: &Connection, id: &str) -> SqlResult<()> {
        db.execute("DELETE FROM pet_actions WHERE id = ?", params![id])?;
        Ok(())
    }

    // ═══════════════════════════════════════
    // 帧数据 CRUD
    // ═══════════════════════════════════════

    pub fn list_frames(db: &Connection, action_id: &str) -> SqlResult<Vec<PixelFrame>> {
        let mut stmt = db.prepare(
            "SELECT id, action_id, frame_index, image_path, image_hash, prompt_used,
             status, generated_at FROM pixel_frames WHERE action_id = ?1 ORDER BY frame_index ASC"
        )?;
        let rows = stmt.query_map(params![action_id], |row| {
            Ok(PixelFrame {
                id: row.get(0)?,
                action_id: row.get(1)?,
                frame_index: row.get(2)?,
                image_path: row.get(3)?,
                image_hash: row.get(4)?,
                prompt_used: row.get(5)?,
                status: row.get(6)?,
                generated_at: row.get(7)?,
            })
        })?;
        rows.collect()
    }

    pub fn insert_frame(db: &Connection, frame: &PixelFrame) -> SqlResult<()> {
        db.execute(
            "INSERT OR REPLACE INTO pixel_frames (id, action_id, frame_index, image_path,
             image_hash, prompt_used, status, generated_at)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)",
            params![
                frame.id, frame.action_id, frame.frame_index, frame.image_path,
                frame.image_hash, frame.prompt_used, frame.status, frame.generated_at,
            ],
        )?;
        Ok(())
    }

    pub fn update_frame_status(db: &Connection, id: &str, status: &str, image_path: Option<&str>) -> SqlResult<()> {
        if let Some(path) = image_path {
            db.execute(
                "UPDATE pixel_frames SET status = ?1, image_path = ?2, generated_at = ?3 WHERE id = ?4",
                params![status, path, chrono::Utc::now().timestamp(), id],
            )?;
        } else {
            db.execute(
                "UPDATE pixel_frames SET status = ?1 WHERE id = ?2",
                params![status, id],
            )?;
        }
        Ok(())
    }

    pub fn delete_frames_by_action(db: &Connection, action_id: &str) -> SqlResult<()> {
        db.execute("DELETE FROM pixel_frames WHERE action_id = ?", params![action_id])?;
        Ok(())
    }

    // ═══════════════════════════════════════
    // 图片生成配置
    // ═══════════════════════════════════════

    pub fn get_gen_config(db: &Connection) -> SqlResult<PixelGenConfig> {
        let mut stmt = db.prepare(
            "SELECT provider, api_url, api_key, model, style_prompt, size, steps, cfg_scale, batch_size
             FROM pixel_gen_configs WHERE id = 1"
        )?;
        let mut rows = stmt.query_map([], |row| {
            Ok(PixelGenConfig {
                provider: row.get::<_, Option<String>>(0)?.unwrap_or_default(),
                api_url: row.get(1)?,
                api_key: row.get(2)?,
                model: row.get(3)?,
                style_prompt: row.get::<_, Option<String>>(4)?.unwrap_or_default(),
                size: row.get::<_, Option<String>>(5)?.unwrap_or_else(|| "64x64".into()),
                steps: row.get::<_, Option<i32>>(6)?.unwrap_or(20),
                cfg_scale: row.get::<_, Option<f64>>(7)?.unwrap_or(7.0),
                batch_size: row.get::<_, Option<i32>>(8)?.unwrap_or(1),
            })
        })?;
        match rows.next() {
            Some(r) => r,
            None => Ok(PixelGenConfig::default()),
        }
    }

    pub fn upsert_gen_config(db: &Connection, config: &PixelGenConfig) -> SqlResult<()> {
        db.execute(
            "INSERT INTO pixel_gen_configs (id, provider, api_url, api_key, model, style_prompt, size, steps, cfg_scale, batch_size)
             VALUES (1, ?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9)
             ON CONFLICT(id) DO UPDATE SET
             provider=excluded.provider, api_url=excluded.api_url, api_key=excluded.api_key,
             model=excluded.model, style_prompt=excluded.style_prompt, size=excluded.size,
             steps=excluded.steps, cfg_scale=excluded.cfg_scale, batch_size=excluded.batch_size",
            params![
                config.provider, config.api_url, config.api_key, config.model,
                config.style_prompt, config.size, config.steps, config.cfg_scale, config.batch_size,
            ],
        )?;
        Ok(())
    }
}
