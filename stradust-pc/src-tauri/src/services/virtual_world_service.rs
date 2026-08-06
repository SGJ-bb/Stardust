// 虚拟世界推演，对应原virtualworld/VirtualWorldManager.kt

use crate::db::database::Database;
use crate::db::virtual_world_repo;
use crate::models::virtual_world::*;
use crate::utils::helpers;

/// 虚拟世界服务
pub struct VirtualWorldService;

impl VirtualWorldService {
    pub fn new() -> Self {
        VirtualWorldService
    }

    /// 获取虚拟世界
    pub fn get_virtual_world(&self, db: &Database, persona_id: &str) -> Result<Option<WorldConfig>, crate::db::database::DbError> {
        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;
        virtual_world_repo::get_world_config(&conn, persona_id)
    }

    /// 更新世界配置
    pub fn update_world_config(&self, db: &Database, config: &WorldConfig) -> Result<(), crate::db::database::DbError> {
        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;
        virtual_world_repo::save_world_config(&conn, config)
    }

    /// 创建默认世界
    pub fn create_default_world(&self, db: &Database, persona_id: &str, name: &str) -> Result<WorldConfig, crate::db::database::DbError> {
        let now = helpers::now();
        let config = WorldConfig {
            id: helpers::new_uuid(),
            persona_id: persona_id.to_string(),
            name: name.to_string(),
            description: "一个充满可能性的世界".to_string(),
            rules: vec!["时间自然流逝".to_string(), "角色有自由意志".to_string()],
            era: None,
            genre: None,
            auto_tick_enabled: false,
            tick_interval_minutes: 60,
            max_events_per_tick: 3,
            created_at: now,
            updated_at: now,
        };

        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;
        virtual_world_repo::save_world_config(&conn, &config)?;

        // 创建初始世界状态
        let state = WorldState {
            id: helpers::new_uuid(),
            world_id: config.id.clone(),
            persona_id: persona_id.to_string(),
            current_situation: "世界刚刚诞生，一切都在等待探索...".to_string(),
            characters: serde_json::json!({}),
            environment: serde_json::json!({
                "weather": "晴朗",
                "time": "清晨",
                "season": "春天",
            }),
            timeline: Vec::new(),
            tick_count: 0,
            last_tick_at: now,
            is_running: false,
            created_at: now,
            updated_at: now,
        };

        virtual_world_repo::save_world_state(&conn, &state)?;

        Ok(config)
    }

    /// 是否应该执行tick
    pub fn should_tick(&self, db: &Database, world_id: &str) -> Result<bool, crate::db::database::DbError> {
        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;

        if let Some(state) = virtual_world_repo::get_world_state(&conn, world_id)? {
            if !state.is_running {
                return Ok(false);
            }
            // 检查配置
            if let Some(config) = virtual_world_repo::get_world_config_by_id(&conn, world_id)? {
                if !config.auto_tick_enabled {
                    return Ok(false);
                }
                let elapsed = (helpers::now() - state.last_tick_at).num_minutes();
                return Ok(elapsed >= config.tick_interval_minutes as i64);
            }
        }
        Ok(false)
    }

    /// 推进虚拟时间
    pub fn advance_virtual_time(&self, db: &Database, world_id: &str, hours: u32) -> Result<(), crate::db::database::DbError> {
        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;

        if let Some(mut state) = virtual_world_repo::get_world_state(&conn, world_id)? {
            // 更新环境中的时间
            if let Some(env) = state.environment.as_object_mut() {
                if let Some(time_str) = env.get("time").and_then(|t| t.as_str()) {
                    // 简单的时间推进逻辑
                    let new_time = advance_time_string(time_str, hours);
                    env.insert("time".to_string(), serde_json::Value::String(new_time));
                }
            }
            state.updated_at = helpers::now();
            virtual_world_repo::save_world_state(&conn, &state)?;
        }

        Ok(())
    }

    /// 执行单人模拟
    pub fn run_solo_simulation(&self, db: &Database, world_id: &str, character_id: &str) -> Result<StoryEvent, crate::db::database::DbError> {
        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;

        let mut state = virtual_world_repo::get_world_state(&conn, world_id)?
            .ok_or_else(|| crate::db::database::DbError::NotFound("世界状态不存在".to_string()))?;

        state.tick_count += 1;
        state.last_tick_at = helpers::now();

        let event = StoryEvent {
            id: helpers::new_uuid(),
            world_id: world_id.to_string(),
            tick_number: state.tick_count,
            event_type: StoryEventType::Daily,
            title: format!("{}的日常", character_id),
            description: format!("{}在世界中度过了平凡的一天。", character_id),
            participants: vec![character_id.to_string()],
            impact: 0.2,
            timestamp: helpers::now(),
        };

        virtual_world_repo::save_story_event(&conn, &event)?;
        state.timeline.push(event.clone());
        virtual_world_repo::save_world_state(&conn, &state)?;

        Ok(event)
    }

    /// 执行群组模拟
    pub fn run_group_simulation(&self, db: &Database, world_id: &str, character_ids: &[String]) -> Result<Vec<StoryEvent>, crate::db::database::DbError> {
        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;

        let mut state = virtual_world_repo::get_world_state(&conn, world_id)?
            .ok_or_else(|| crate::db::database::DbError::NotFound("世界状态不存在".to_string()))?;

        state.tick_count += 1;
        state.last_tick_at = helpers::now();

        let event = StoryEvent {
            id: helpers::new_uuid(),
            world_id: world_id.to_string(),
            tick_number: state.tick_count,
            event_type: StoryEventType::Social,
            title: "群组互动".to_string(),
            description: format!("{}等人在一起互动。", character_ids.join("、")),
            participants: character_ids.to_vec(),
            impact: 0.4,
            timestamp: helpers::now(),
        };

        virtual_world_repo::save_story_event(&conn, &event)?;
        state.timeline.push(event.clone());
        virtual_world_repo::save_world_state(&conn, &state)?;

        Ok(vec![event])
    }

    /// 为事件生成图片描述
    pub fn generate_image_for_event(&self, event: &StoryEvent) -> String {
        format!("一幅描绘「{}」场景的插画：{}", event.title, event.description)
    }

    /// 获取最新故事摘要
    pub fn get_latest_story_summary(&self, db: &Database, world_id: &str) -> Result<String, crate::db::database::DbError> {
        let events = self.get_story_events(db, world_id, Some(5))?;
        if events.is_empty() {
            return Ok("世界刚刚诞生，还没有故事发生。".to_string());
        }
        let summary: Vec<String> = events.iter()
            .map(|e| format!("【{}】{}", e.title, e.description))
            .collect();
        Ok(summary.join("\n"))
    }

    /// 解析 [[location:]][[weather:]][[mood:]] 标签
    pub fn parse_virtual_tags(text: &str) -> VirtualTags {
        let location = regex::Regex::new(r"\[\[location:(.*?)\]\]")
            .ok()
            .and_then(|re| re.captures(text))
            .and_then(|caps| caps.get(1).map(|m| m.as_str().to_string()));

        let weather = regex::Regex::new(r"\[\[weather:(.*?)\]\]")
            .ok()
            .and_then(|re| re.captures(text))
            .and_then(|caps| caps.get(1).map(|m| m.as_str().to_string()));

        let mood = regex::Regex::new(r"\[\[mood:(.*?)\]\]")
            .ok()
            .and_then(|re| re.captures(text))
            .and_then(|caps| caps.get(1).map(|m| m.as_str().to_string()));

        VirtualTags { location, weather, mood }
    }

    /// 执行世界推演（tick）
    pub fn run_world_tick(&self, db: &Database, world_id: &str) -> Result<Vec<StoryEvent>, crate::db::database::DbError> {
        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;

        let mut state = virtual_world_repo::get_world_state(&conn, world_id)?
            .ok_or_else(|| crate::db::database::DbError::NotFound("世界状态不存在".to_string()))?;

        state.tick_count += 1;
        state.last_tick_at = helpers::now();

        // 生成事件（在实际实现中，这里应该调用LLM生成）
        let event = StoryEvent {
            id: helpers::new_uuid(),
            world_id: world_id.to_string(),
            tick_number: state.tick_count,
            event_type: StoryEventType::Daily,
            title: format!("第{}个时间节点", state.tick_count),
            description: "世界在平静中流逝...".to_string(),
            participants: Vec::new(),
            impact: 0.3,
            timestamp: helpers::now(),
        };

        virtual_world_repo::save_story_event(&conn, &event)?;
        state.timeline.push(event.clone());
        virtual_world_repo::save_world_state(&conn, &state)?;

        Ok(vec![event])
    }

    /// 切换世界运行状态
    pub fn toggle_world(&self, db: &Database, world_id: &str) -> Result<bool, crate::db::database::DbError> {
        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;

        let mut state = virtual_world_repo::get_world_state(&conn, world_id)?
            .ok_or_else(|| crate::db::database::DbError::NotFound("世界状态不存在".to_string()))?;

        state.is_running = !state.is_running;
        state.updated_at = helpers::now();
        virtual_world_repo::save_world_state(&conn, &state)?;

        Ok(state.is_running)
    }

    /// 获取故事事件
    pub fn get_story_events(&self, db: &Database, world_id: &str, limit: Option<u32>) -> Result<Vec<StoryEvent>, crate::db::database::DbError> {
        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;
        virtual_world_repo::get_story_events(&conn, world_id, limit)
    }
}

/// 虚拟标签解析结果
#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct VirtualTags {
    pub location: Option<String>,
    pub weather: Option<String>,
    pub mood: Option<String>,
}

/// 简单的时间推进
fn advance_time_string(current: &str, hours: u32) -> String {
    let hour = match current {
        "清晨" => 6,
        "上午" => 9,
        "中午" => 12,
        "下午" => 15,
        "傍晚" => 18,
        "晚上" => 21,
        "深夜" => 0,
        _ => 12,
    };
    let new_hour = (hour + hours as u32) % 24;
    match new_hour {
        0..=5 => "深夜",
        6..=8 => "清晨",
        9..=11 => "上午",
        12..=13 => "中午",
        14..=17 => "下午",
        18..=19 => "傍晚",
        20..=23 => "晚上",
        _ => "深夜",
    }.to_string()
}
