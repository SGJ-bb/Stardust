// 设置CRUD，键值对

use rusqlite::{params, Connection};

use crate::db::database::DbError;
use crate::models::settings::{AppSettings, ProviderProfile, ProviderType, ThemeSettings, ThemeMode,
    VoiceSettings, MemorySettings, InteractionSettings, SafetySettings, SafetyLevel};
use crate::utils::helpers;

/// 获取设置值
pub fn get_setting(conn: &Connection, key: &str) -> Result<Option<String>, DbError> {
    let result = conn.query_row(
        "SELECT value FROM settings WHERE key = ?1",
        params![key],
        |row| row.get(0),
    );

    match result {
        Ok(value) => Ok(Some(value)),
        Err(rusqlite::Error::QueryReturnedNoRows) => Ok(None),
        Err(e) => Err(DbError::QueryFailed(e.to_string())),
    }
}

/// 设置值
pub fn set_setting(conn: &Connection, key: &str, value: &str) -> Result<(), DbError> {
    let now = chrono::Local::now().naive_local().to_string();
    conn.execute(
        "INSERT OR REPLACE INTO settings (key, value, updated_at) VALUES (?1, ?2, ?3)",
        params![key, value, now],
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    Ok(())
}

/// 获取所有设置
pub fn get_all_settings(conn: &Connection) -> Result<AppSettings, DbError> {
    let theme = get_theme_settings(conn)?;
    let voice = get_voice_settings(conn)?;
    let memory = get_memory_settings(conn)?;
    let interaction = get_interaction_settings(conn)?;
    let safety = get_safety_settings(conn)?;

    let active_persona_id = get_setting(conn, "active_persona_id")?;
    let active_provider_id = get_setting(conn, "active_provider_id")?;
    let auto_start = get_setting(conn, "auto_start")?.map(|v| v == "true").unwrap_or(false);
    let minimize_to_tray = get_setting(conn, "minimize_to_tray")?.map(|v| v == "true").unwrap_or(true);
    let global_shortcut = get_setting(conn, "global_shortcut")?;
    let language = get_setting(conn, "language")?.unwrap_or_else(|| "zh-CN".to_string());

    Ok(AppSettings {
        active_persona_id,
        active_provider_id,
        theme,
        voice,
        memory,
        interaction,
        safety,
        auto_start,
        minimize_to_tray,
        global_shortcut,
        language,
        updated_at: chrono::Local::now().naive_local(),
    })
}

/// 获取主题设置
fn get_theme_settings(conn: &Connection) -> Result<ThemeSettings, DbError> {
    Ok(ThemeSettings {
        mode: get_setting(conn, "theme_mode")?
            .and_then(|v| serde_json::from_str(&v).ok())
            .unwrap_or(ThemeMode::Dark),
        primary_color: get_setting(conn, "theme_primary_color")?.unwrap_or_else(|| "#6C5CE7".to_string()),
        accent_color: get_setting(conn, "theme_accent_color")?.unwrap_or_else(|| "#A29BFE".to_string()),
        background: get_setting(conn, "theme_background")?.unwrap_or_else(|| "#1A1A2E".to_string()),
        font_size: get_setting(conn, "theme_font_size")?.and_then(|v| v.parse().ok()).unwrap_or(14),
        custom_css: get_setting(conn, "theme_custom_css")?,
    })
}

/// 获取语音设置
fn get_voice_settings(conn: &Connection) -> Result<VoiceSettings, DbError> {
    Ok(VoiceSettings {
        asr_enabled: get_setting(conn, "voice_asr_enabled")?.map(|v| v == "true").unwrap_or(false),
        asr_provider: get_setting(conn, "voice_asr_provider")?.unwrap_or_else(|| "openai".to_string()),
        asr_language: get_setting(conn, "voice_asr_language")?.unwrap_or_else(|| "zh".to_string()),
        tts_enabled: get_setting(conn, "voice_tts_enabled")?.map(|v| v == "true").unwrap_or(false),
        tts_provider: get_setting(conn, "voice_tts_provider")?.unwrap_or_else(|| "openai".to_string()),
        tts_voice_id: get_setting(conn, "voice_tts_voice_id")?,
        tts_speed: get_setting(conn, "voice_tts_speed")?.and_then(|v| v.parse().ok()).unwrap_or(1.0),
        tts_pitch: get_setting(conn, "voice_tts_pitch")?.and_then(|v| v.parse().ok()).unwrap_or(1.0),
        auto_speak: get_setting(conn, "voice_auto_speak")?.map(|v| v == "true").unwrap_or(false),
        vad_enabled: get_setting(conn, "voice_vad_enabled")?.map(|v| v == "true").unwrap_or(true),
        vad_threshold: get_setting(conn, "voice_vad_threshold")?.and_then(|v| v.parse().ok()).unwrap_or(0.5),
    })
}

/// 获取记忆设置
fn get_memory_settings(conn: &Connection) -> Result<MemorySettings, DbError> {
    Ok(MemorySettings {
        max_pool_tokens: get_setting(conn, "memory_max_pool_tokens")?.and_then(|v| v.parse().ok()).unwrap_or(4000),
        consolidation_threshold: get_setting(conn, "memory_consolidation_threshold")?.and_then(|v| v.parse().ok()).unwrap_or(20),
        auto_consolidate: get_setting(conn, "memory_auto_consolidate")?.map(|v| v == "true").unwrap_or(true),
        memorable_threshold: get_setting(conn, "memory_memorable_threshold")?.and_then(|v| v.parse().ok()).unwrap_or(0.7),
        max_core_memories: get_setting(conn, "memory_max_core_memories")?.and_then(|v| v.parse().ok()).unwrap_or(10),
        rag_enabled: get_setting(conn, "memory_rag_enabled")?.map(|v| v == "true").unwrap_or(true),
        rag_chunk_size: get_setting(conn, "memory_rag_chunk_size")?.and_then(|v| v.parse().ok()).unwrap_or(500),
        rag_overlap: get_setting(conn, "memory_rag_overlap")?.and_then(|v| v.parse().ok()).unwrap_or(50),
    })
}

/// 获取互动设置
fn get_interaction_settings(conn: &Connection) -> Result<InteractionSettings, DbError> {
    Ok(InteractionSettings {
        proactive_enabled: get_setting(conn, "interaction_proactive_enabled")?.map(|v| v == "true").unwrap_or(true),
        min_interval_minutes: get_setting(conn, "interaction_min_interval")?.and_then(|v| v.parse().ok()).unwrap_or(30),
        max_interval_minutes: get_setting(conn, "interaction_max_interval")?.and_then(|v| v.parse().ok()).unwrap_or(120),
        nag_enabled: get_setting(conn, "interaction_nag_enabled")?.map(|v| v == "true").unwrap_or(true),
        nag_interval_minutes: get_setting(conn, "interaction_nag_interval")?.and_then(|v| v.parse().ok()).unwrap_or(60),
        greeting_enabled: get_setting(conn, "interaction_greeting_enabled")?.map(|v| v == "true").unwrap_or(true),
        idle_timeout_minutes: get_setting(conn, "interaction_idle_timeout")?.and_then(|v| v.parse().ok()).unwrap_or(30),
    })
}

/// 获取安全设置
fn get_safety_settings(conn: &Connection) -> Result<SafetySettings, DbError> {
    let blocked_words = get_setting(conn, "safety_blocked_words")?
        .and_then(|v| serde_json::from_str(&v).ok())
        .unwrap_or_default();
    let blocked_patterns = get_setting(conn, "safety_blocked_patterns")?
        .and_then(|v| serde_json::from_str(&v).ok())
        .unwrap_or_default();

    Ok(SafetySettings {
        content_filter_enabled: get_setting(conn, "safety_filter_enabled")?.map(|v| v == "true").unwrap_or(true),
        filter_level: get_setting(conn, "safety_filter_level")?
            .and_then(|v| serde_json::from_str(&v).ok())
            .unwrap_or(SafetyLevel::Medium),
        custom_blocked_words: blocked_words,
        custom_blocked_patterns: blocked_patterns,
    })
}

/// 创建供应商配置
pub fn create_provider(conn: &Connection, provider: &ProviderProfile) -> Result<(), DbError> {
    conn.execute(
        "INSERT INTO provider_profiles (id, name, provider_type, api_key, base_url, model_name,
         temperature, max_tokens, top_p, frequency_penalty, presence_penalty, is_default,
         created_at, updated_at)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14)",
        params![
            provider.id, provider.name,
            serde_json::to_string(&provider.provider_type).unwrap_or_else(|_| "\"openai\"".to_string()),
            provider.api_key, provider.base_url, provider.model_name,
            provider.temperature, provider.max_tokens, provider.top_p,
            provider.frequency_penalty, provider.presence_penalty,
            provider.is_default as i32,
            provider.created_at.to_string(), provider.updated_at.to_string()
        ],
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    Ok(())
}

/// 列出供应商配置
pub fn list_providers(conn: &Connection) -> Result<Vec<ProviderProfile>, DbError> {
    let mut stmt = conn.prepare(
        "SELECT id, name, provider_type, api_key, base_url, model_name,
                temperature, max_tokens, top_p, frequency_penalty, presence_penalty,
                is_default, created_at, updated_at
         FROM provider_profiles ORDER BY created_at"
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    let providers = stmt.query_map([], |row| {
        let type_str: String = row.get(2)?;
        Ok(ProviderProfile {
            id: row.get(0)?,
            name: row.get(1)?,
            provider_type: serde_json::from_str(&type_str).unwrap_or(ProviderType::OpenAI),
            api_key: row.get(3)?,
            base_url: row.get(4)?,
            model_name: row.get(5)?,
            temperature: row.get(6)?,
            max_tokens: row.get(7)?,
            top_p: row.get(8)?,
            frequency_penalty: row.get(9)?,
            presence_penalty: row.get(10)?,
            is_default: row.get::<_, i32>(11)? != 0,
            created_at: helpers::parse_dt(row.get(12)?),
            updated_at: helpers::parse_dt(row.get(13)?),
        })
    }).map_err(|e| DbError::QueryFailed(e.to_string()))?
      .filter_map(|p| p.ok())
      .collect();

    Ok(providers)
}

/// 获取供应商配置
pub fn get_provider(conn: &Connection, id: &str) -> Result<ProviderProfile, DbError> {
    conn.query_row(
        "SELECT id, name, provider_type, api_key, base_url, model_name,
                temperature, max_tokens, top_p, frequency_penalty, presence_penalty,
                is_default, created_at, updated_at
         FROM provider_profiles WHERE id = ?1",
        params![id],
        |row| {
            let type_str: String = row.get(2)?;
            Ok(ProviderProfile {
                id: row.get(0)?,
                name: row.get(1)?,
                provider_type: serde_json::from_str(&type_str).unwrap_or(ProviderType::OpenAI),
                api_key: row.get(3)?,
                base_url: row.get(4)?,
                model_name: row.get(5)?,
                temperature: row.get(6)?,
                max_tokens: row.get(7)?,
                top_p: row.get(8)?,
                frequency_penalty: row.get(9)?,
                presence_penalty: row.get(10)?,
                is_default: row.get::<_, i32>(11)? != 0,
                created_at: helpers::parse_dt(row.get(12)?),
                updated_at: helpers::parse_dt(row.get(13)?),
            })
        },
    ).map_err(|e| match e {
        rusqlite::Error::QueryReturnedNoRows => DbError::NotFound(format!("供应商 {} 不存在", id)),
        e => DbError::QueryFailed(e.to_string()),
    })
}

/// 删除供应商配置
pub fn delete_provider(conn: &Connection, id: &str) -> Result<(), DbError> {
    conn.execute("DELETE FROM provider_profiles WHERE id = ?1", params![id])
        .map_err(|e| DbError::QueryFailed(e.to_string()))?;
    Ok(())
}
