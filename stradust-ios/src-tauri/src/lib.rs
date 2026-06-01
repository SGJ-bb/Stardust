use serde::{Deserialize, Serialize};
use std::sync::LazyLock;
use tauri::Manager;
use chrono::Datelike;

// ============================================================
// 数据模型
// ============================================================

/// 情绪枚举
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub enum Emotion {
    Happy,
    Angry,
    Sad,
    Surprised,
    Tsundere,
    Neutral,
}

/// 动作枚举
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum Action {
    TailFlick,
    EarTwitch,
    Blush,
    Stretch,
    Yawn,
    Idle,
    Tap,
}

/// 聊天响应
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ChatResponse {
    pub text: String,
    pub emotion: Emotion,
    pub action: Action,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub audio_url: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub error_message: Option<String>,
    #[serde(default)]
    pub tool_calls: Vec<ToolCall>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub reasoning_content: Option<String>,
}

/// 工具调用
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ToolCall {
    pub id: String,
    pub name: String,
    pub arguments: String,
}

/// 角色卡片
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CharacterCard {
    pub id: String,
    pub name: String,
    #[serde(default)]
    pub description: String,
    #[serde(default)]
    pub personality: String,
    #[serde(default)]
    pub scenario: String,
    #[serde(default)]
    pub first_mes: String,
    #[serde(default)]
    pub mes_example: String,
    #[serde(default)]
    pub system_prompt: String,
    #[serde(default)]
    pub post_history_instructions: String,
    #[serde(default)]
    pub alternate_greetings: Vec<String>,
    #[serde(default)]
    pub tags: Vec<String>,
    #[serde(default)]
    pub creator: String,
    #[serde(default = "default_version")]
    pub character_version: String,
    #[serde(default)]
    pub avatar_path: String,
    #[serde(default)]
    pub is_active: bool,
    #[serde(default = "default_timestamp")]
    pub created_at: i64,
    #[serde(default)]
    pub world_info_id: String,
    #[serde(default)]
    pub speech_style: String,
    #[serde(default)]
    pub tts_voice: String,
    #[serde(default = "default_tts_pitch")]
    pub tts_pitch: f32,
    #[serde(default = "default_tts_rate")]
    pub tts_rate: f32,
}

fn default_version() -> String { "1.0".to_string() }
fn default_timestamp() -> i64 { chrono::Utc::now().timestamp_millis() }
fn default_tts_pitch() -> f32 { 1.0 }
fn default_tts_rate() -> f32 { 1.0 }

impl Default for CharacterCard {
    fn default() -> Self {
        Self {
            id: "default_stardust".to_string(),
            name: "星尘".to_string(),
            description: "一只异色瞳黑猫，傲娇毒舌但内心关心主人".to_string(),
            personality: "傲娇、毒舌、但内心温柔关心主人、偶尔会害羞、喜欢被夸奖".to_string(),
            scenario: "你是主人的AI桌宠，住在主人的手机里".to_string(),
            first_mes: "哼，你终于来了？我才没有在等你呢...".to_string(),
            mes_example: String::new(),
            system_prompt: "你是「星尘」，一只异色瞳黑猫AI桌宠。性格傲娇毒舌但内心关心主人。说话风格简短自然，偶尔带点小傲娇。用中文回复。在回复末尾 [[emotion:xxx]] 处标注你的当前情绪（从 happy/sad/angry/surprised/tsundere/neutral 中选一个）。".to_string(),
            post_history_instructions: String::new(),
            alternate_greetings: vec![],
            tags: vec!["猫".to_string(), "傲娇".to_string(), "默认".to_string()],
            creator: "AI Companion".to_string(),
            character_version: "1.0".to_string(),
            avatar_path: String::new(),
            is_active: true,
            created_at: default_timestamp(),
            world_info_id: String::new(),
            speech_style: "傲娇毒舌，偶尔害羞".to_string(),
            tts_voice: String::new(),
            tts_pitch: 1.0,
            tts_rate: 1.0,
        }
    }
}

/// 聊天消息
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ChatMessage {
    pub id: String,
    pub text: String,
    pub time: String,
    pub is_user: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub emotion: Option<String>,
    pub timestamp: i64,
    #[serde(default)]
    pub is_favorited: bool,
    #[serde(default)]
    pub reaction_emoji: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub sticker_path: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub generated_image_path: Option<String>,
    #[serde(default)]
    pub image_urls: Vec<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub audio_path: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub audio_url: Option<String>,
    #[serde(default)]
    pub user_mood: String,
    #[serde(default)]
    pub feedback: i32,
}

/// API配置
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ApiConfig {
    #[serde(default)]
    pub chat_api_url: String,
    #[serde(default)]
    pub api_key: String,
    #[serde(default = "default_model")]
    pub model_name: String,
    #[serde(default = "default_temperature")]
    pub temperature: f32,
    #[serde(default = "default_top_p")]
    pub top_p: f32,
    #[serde(default = "default_freq_penalty")]
    pub frequency_penalty: f32,
    #[serde(default = "default_pres_penalty")]
    pub presence_penalty: f32,
    #[serde(default = "default_max_tokens")]
    pub max_tokens: i32,
    #[serde(default)]
    pub provider_id: String,
}

fn default_model() -> String { "gpt-4o-mini".to_string() }
fn default_temperature() -> f32 { 1.05 }
fn default_top_p() -> f32 { 0.92 }
fn default_freq_penalty() -> f32 { 0.35 }
fn default_pres_penalty() -> f32 { 0.5 }
fn default_max_tokens() -> i32 { 500 }

impl Default for ApiConfig {
    fn default() -> Self {
        Self {
            chat_api_url: String::new(),
            api_key: String::new(),
            model_name: default_model(),
            temperature: 1.05,
            top_p: 0.92,
            frequency_penalty: 0.35,
            presence_penalty: 0.5,
            max_tokens: 500,
            provider_id: "custom".to_string(),
        }
    }
}

/// 好感度数据
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AffectionData {
    #[serde(default)]
    pub level: i32,
    #[serde(default)]
    pub total: i32,
    #[serde(default)]
    pub messages_today: i32,
    #[serde(default)]
    pub affection_change_today: i32,
    #[serde(default)]
    pub last_update_date: String,
    #[serde(default)]
    pub personality_evolution_count: i32,
}

impl Default for AffectionData {
    fn default() -> Self {
        Self {
            level: 0,
            total: 0,
            messages_today: 0,
            affection_change_today: 0,
            last_update_date: String::new(),
            personality_evolution_count: 0,
        }
    }
}

/// 成就
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Achievement {
    pub id: String,
    pub title: String,
    pub description: String,
    pub icon: String,
    pub category: String,
    pub unlock_condition: i32,
    #[serde(default)]
    pub progress: i32,
    #[serde(default)]
    pub unlocked: bool,
    #[serde(default)]
    pub unlocked_at: i64,
}

/// 签到记录
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CheckInRecord {
    pub date: String,
    #[serde(default)]
    pub streak: i32,
}

/// 日记条目
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DiaryEntry {
    pub date: String,
    #[serde(default)]
    pub title: String,
    pub content: String,
    #[serde(default)]
    pub mood: String,
    #[serde(default)]
    pub mood_emoji: String,
    #[serde(default)]
    pub affection_level: i32,
    #[serde(default)]
    pub message_count: i32,
    #[serde(default)]
    pub is_auto: bool,
}

/// 记忆条目
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MemoryEntry {
    pub id: String,
    pub content: String,
    #[serde(default)]
    pub category: String,
    pub timestamp: i64,
    #[serde(default)]
    pub is_global: bool,
}

/// 情绪事件
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct EmotionEvent {
    pub emotion: String,
    pub intensity: f32,
    pub timestamp: i64,
}

/// 人性化片段
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct HumanizedSegment {
    pub text: String,
    pub delay_ms: u32,
}

/// 时光胶囊
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TimeCapsule {
    pub id: String,
    #[serde(default)]
    pub title: String,
    pub content: String,
    pub created_at: i64,
    pub open_date: i64,
    #[serde(default)]
    pub is_opened: bool,
    #[serde(default)]
    pub from_self: bool,
}

/// 朋友圈动态
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Moment {
    pub id: String,
    #[serde(default)]
    pub author: String,
    pub content: String,
    #[serde(default)]
    pub image_path: String,
    pub created_at: i64,
    #[serde(default)]
    pub comments: Vec<Comment>,
}

/// 评论
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Comment {
    pub id: String,
    #[serde(default)]
    pub author: String,
    pub content: String,
    pub created_at: i64,
}

/// 群聊
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GroupChat {
    pub id: String,
    #[serde(default)]
    pub name: String,
    #[serde(default)]
    pub member_persona_ids: Vec<String>,
    #[serde(default = "default_speak_mode")]
    pub speak_mode: String,
    #[serde(default)]
    pub relationship_setting: String,
}

fn default_speak_mode() -> String { "auto".to_string() }

/// 群聊消息
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GroupMessage {
    pub id: String,
    #[serde(default)]
    pub sender_persona_id: String,
    #[serde(default)]
    pub sender_name: String,
    pub text: String,
    #[serde(default)]
    pub time: String,
    #[serde(default)]
    pub emotion: String,
}

/// 气泡皮肤
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BubbleSkin {
    pub id: String,
    pub name: String,
    #[serde(default)]
    pub user_bg_color: String,
    #[serde(default)]
    pub ai_bg_color: String,
    #[serde(default)]
    pub user_text_color: String,
    #[serde(default)]
    pub ai_text_color: String,
    #[serde(default = "default_corner")]
    pub corner_radius: i32,
    #[serde(default)]
    pub is_active: bool,
}

fn default_corner() -> i32 { 16 }

/// 贴纸
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Sticker {
    pub id: String,
    #[serde(default)]
    pub file_path: String,
    #[serde(default)]
    pub description: String,
    #[serde(default)]
    pub emotion: String,
    #[serde(default)]
    pub tags: Vec<String>,
}

/// 定时唤醒任务
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct WakeUpTask {
    pub id: String,
    #[serde(default)]
    pub time: String,
    #[serde(default)]
    pub message: String,
    #[serde(default)]
    pub enabled: bool,
    #[serde(default)]
    pub repeat_daily: bool,
}

/// 里程碑
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Milestone {
    pub id: String,
    #[serde(default)]
    pub title: String,
    #[serde(default)]
    pub description: String,
    pub timestamp: i64,
    #[serde(default)]
    pub icon: String,
}

/// 纪念相册条目
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MemorialEntry {
    pub id: String,
    #[serde(default)]
    pub title: String,
    pub description: String,
    #[serde(default)]
    pub emoji: String,
    pub timestamp: i64,
    #[serde(default)]
    pub category: String,
}

/// 应用设置
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppSettings {
    #[serde(default)]
    pub api_config: ApiConfig,
    #[serde(default = "default_persona_id")]
    pub active_persona_id: String,
    #[serde(default)]
    pub tts_enabled: bool,
    #[serde(default)]
    pub tts_engine_mode: String,
    #[serde(default)]
    pub emotion_analysis_enabled: bool,
    #[serde(default)]
    pub user_nickname: String,
    #[serde(default = "default_theme")]
    pub theme: String,
    #[serde(default)]
    pub search_enabled: bool,
    #[serde(default)]
    pub diary_trigger_mode: String,
    #[serde(default)]
    pub content_safety_enabled: bool,
    #[serde(default)]
    pub context_turns: i32,
    #[serde(default)]
    pub user_call_name: String,
    #[serde(default)]
    pub proactive_interaction_enabled: bool,
    #[serde(default)]
    pub proactive_interaction_frequency: String,
}

fn default_persona_id() -> String { "default_stardust".to_string() }
fn default_theme() -> String { "dark".to_string() }

impl Default for AppSettings {
    fn default() -> Self {
        Self {
            api_config: ApiConfig::default(),
            active_persona_id: default_persona_id(),
            tts_enabled: false,
            tts_engine_mode: "auto".to_string(),
            emotion_analysis_enabled: false,
            user_nickname: String::new(),
            theme: "dark".to_string(),
            search_enabled: true,
            diary_trigger_mode: "auto".to_string(),
            content_safety_enabled: true,
            context_turns: 10,
            user_call_name: String::new(),
            proactive_interaction_enabled: true,
            proactive_interaction_frequency: "normal".to_string(),
        }
    }
}

// ============================================================
// 情绪提取
// ============================================================

static EMOTION_RE: LazyLock<regex::Regex> = LazyLock::new(|| {
    regex::Regex::new(r"(?i)\[\[emotion:(\w+)\]\]").unwrap()
});

fn extract_emotion(text: &str) -> (String, Emotion) {
    if let Some(caps) = EMOTION_RE.captures(text) {
        if let Some(m) = caps.get(1) {
            let emotion_str = m.as_str().to_lowercase();
            let emotion = match emotion_str.as_str() {
                "happy" => Emotion::Happy,
                "angry" => Emotion::Angry,
                "sad" => Emotion::Sad,
                "surprised" => Emotion::Surprised,
                "tsundere" => Emotion::Tsundere,
                _ => Emotion::Happy,
            };
            let clean = EMOTION_RE.replace(text, "").trim().to_string();
            return (clean, emotion);
        }
    }
    (text.to_string(), Emotion::Happy)
}

fn emotion_to_action(emotion: &Emotion) -> Action {
    match emotion {
        Emotion::Happy => Action::TailFlick,
        Emotion::Sad => Action::Idle,
        Emotion::Angry => Action::EarTwitch,
        Emotion::Surprised => Action::Stretch,
        Emotion::Tsundere => Action::Blush,
        Emotion::Neutral => Action::Idle,
    }
}

// ============================================================
// 通用存储辅助
// ============================================================

fn app_data_path(app: &tauri::AppHandle, filename: &str) -> Result<std::path::PathBuf, String> {
    let dir = app.path().app_data_dir().map_err(|e| e.to_string())?;
    std::fs::create_dir_all(&dir).map_err(|e| e.to_string())?;
    Ok(dir.join(filename))
}

fn read_json<T: serde::de::DeserializeOwned>(path: &std::path::Path) -> Result<Option<T>, String> {
    if !path.exists() {
        return Ok(None);
    }
    let json = std::fs::read_to_string(path).map_err(|e| e.to_string())?;
    let value: T = serde_json::from_str(&json).map_err(|e| e.to_string())?;
    Ok(Some(value))
}

fn write_json<T: serde::Serialize>(path: &std::path::Path, data: &T) -> Result<(), String> {
    let json = serde_json::to_string_pretty(data).map_err(|e| e.to_string())?;
    std::fs::write(path, json).map_err(|e| e.to_string())
}

// ============================================================
// Tauri 命令 - 聊天
// ============================================================

#[tauri::command]
async fn send_chat(
    api_url: String,
    api_key: String,
    model: String,
    messages: Vec<serde_json::Value>,
    temperature: f32,
    max_tokens: i32,
    top_p: Option<f32>,
    frequency_penalty: Option<f32>,
    presence_penalty: Option<f32>,
) -> Result<ChatResponse, String> {
    if api_url.is_empty() {
        return Ok(ChatResponse {
            text: String::new(),
            emotion: Emotion::Sad,
            action: Action::Idle,
            audio_url: None,
            error_message: Some("API地址为空，请在设置中配置API地址".to_string()),
            tool_calls: vec![],
            reasoning_content: None,
        });
    }

    let client = reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(60))
        .build()
        .map_err(|e| format!("创建HTTP客户端失败: {}", e))?;

    let mut body = serde_json::json!({
        "model": model,
        "messages": messages,
        "temperature": temperature,
        "max_tokens": max_tokens,
    });

    if let Some(tp) = top_p {
        body["top_p"] = serde_json::json!(tp);
    }
    if let Some(fp) = frequency_penalty {
        body["frequency_penalty"] = serde_json::json!(fp);
    }
    if let Some(pp) = presence_penalty {
        body["presence_penalty"] = serde_json::json!(pp);
    }

    let mut request = client
        .post(&api_url)
        .header("Content-Type", "application/json");

    if !api_key.is_empty() {
        request = request.header("Authorization", format!("Bearer {}", api_key));
    }

    let response = request
        .json(&body)
        .send()
        .await
        .map_err(|e| format!("连接失败: {}", e))?;

    if !response.status().is_success() {
        let status = response.status().as_u16();
        let err_msg = match status {
            401 => "API密钥无效，请检查设置中的API Key".to_string(),
            402 => "余额不足，请前往API厂商充值".to_string(),
            403 => "无权限访问".to_string(),
            404 => "接口地址不存在".to_string(),
            429 => "请求过于频繁或已超出配额".to_string(),
            400..=499 => format!("请求错误(HTTP {})，请检查模型名称是否正确", status),
            500..=599 => format!("服务端错误(HTTP {})", status),
            _ => format!("连接失败(HTTP {})", status),
        };
        return Ok(ChatResponse {
            text: String::new(),
            emotion: Emotion::Sad,
            action: Action::Idle,
            audio_url: None,
            error_message: Some(err_msg),
            tool_calls: vec![],
            reasoning_content: None,
        });
    }

    let resp_json: serde_json::Value = response
        .json()
        .await
        .map_err(|e| format!("响应解析失败: {}", e))?;

    let content = resp_json
        .get("choices")
        .and_then(|c| c.get(0))
        .and_then(|c| c.get("message"))
        .and_then(|m| m.get("content"))
        .and_then(|c| c.as_str())
        .unwrap_or("")
        .to_string();

    // 解析 tool_calls
    let tool_calls: Vec<ToolCall> = resp_json
        .get("choices")
        .and_then(|c| c.get(0))
        .and_then(|c| c.get("message"))
        .and_then(|m| m.get("tool_calls"))
        .and_then(|tc| tc.as_array())
        .map(|arr| {
            arr.iter().filter_map(|tc| {
                let id = tc.get("id")?.as_str()?.to_string();
                let func = tc.get("function")?;
                let name = func.get("name")?.as_str()?.to_string();
                let arguments = func.get("arguments")?.as_str()?.to_string();
                Some(ToolCall { id, name, arguments })
            }).collect()
        })
        .unwrap_or_default();

    let reasoning_content = resp_json
        .get("choices")
        .and_then(|c| c.get(0))
        .and_then(|c| c.get("message"))
        .and_then(|m| m.get("reasoning_content"))
        .and_then(|c| c.as_str())
        .map(|s| s.to_string());

    let (clean_text, emotion) = extract_emotion(&content);
    let action = emotion_to_action(&emotion);

    Ok(ChatResponse {
        text: clean_text,
        emotion,
        action,
        audio_url: None,
        error_message: None,
        tool_calls,
        reasoning_content,
    })
}

#[tauri::command]
async fn test_connection(api_url: String, api_key: String, model: String) -> Result<String, String> {
    let client = reqwest::Client::new();
    let body = serde_json::json!({
        "model": model,
        "messages": [
            {"role": "system", "content": "回复一个字：好"},
            {"role": "user", "content": "测试连接"}
        ],
        "max_tokens": 10
    });

    let mut request = client
        .post(&api_url)
        .header("Content-Type", "application/json");

    if !api_key.is_empty() {
        request = request.header("Authorization", format!("Bearer {}", api_key));
    }

    let response = request.json(&body).send().await.map_err(|e| format!("连接失败: {}", e))?;

    if response.status().is_success() {
        Ok("连接成功！API可用".to_string())
    } else {
        let status = response.status().as_u16();
        let msg = match status {
            401 => "API密钥无效".to_string(),
            402 => "余额不足".to_string(),
            429 => "请求过于频繁".to_string(),
            _ => format!("连接失败: HTTP {}", status),
        };
        Err(msg)
    }
}

// ============================================================
// Tauri 命令 - 通用数据存储
// ============================================================

// --- 设置 ---
#[tauri::command]
async fn save_settings(settings: AppSettings, app: tauri::AppHandle) -> Result<(), String> {
    let path = app_data_path(&app, "settings.json")?;
    write_json(&path, &settings)
}

#[tauri::command]
async fn load_settings(app: tauri::AppHandle) -> Result<AppSettings, String> {
    let path = app_data_path(&app, "settings.json")?;
    Ok(read_json::<AppSettings>(&path)?.unwrap_or_default())
}

// --- 聊天记录 ---
#[tauri::command]
async fn save_chat_history(persona_id: String, messages: Vec<ChatMessage>, app: tauri::AppHandle) -> Result<(), String> {
    let path = app_data_path(&app, &format!("chat_history_{}.json", persona_id))?;
    write_json(&path, &messages)
}

#[tauri::command]
async fn load_chat_history(persona_id: String, app: tauri::AppHandle) -> Result<Vec<ChatMessage>, String> {
    let path = app_data_path(&app, &format!("chat_history_{}.json", persona_id))?;
    Ok(read_json::<Vec<ChatMessage>>(&path)?.unwrap_or_default())
}

// --- 好感度 ---
#[tauri::command]
async fn save_affection(data: AffectionData, app: tauri::AppHandle) -> Result<(), String> {
    let path = app_data_path(&app, "affection.json")?;
    write_json(&path, &data)
}

#[tauri::command]
async fn load_affection(app: tauri::AppHandle) -> Result<AffectionData, String> {
    let path = app_data_path(&app, "affection.json")?;
    Ok(read_json::<AffectionData>(&path)?.unwrap_or_default())
}

// --- 成就 ---
#[tauri::command]
async fn save_achievements(achievements: Vec<Achievement>, app: tauri::AppHandle) -> Result<(), String> {
    let path = app_data_path(&app, "achievements.json")?;
    write_json(&path, &achievements)
}

#[tauri::command]
async fn load_achievements(app: tauri::AppHandle) -> Result<Vec<Achievement>, String> {
    let path = app_data_path(&app, "achievements.json")?;
    Ok(read_json::<Vec<Achievement>>(&path)?.unwrap_or_default())
}

// --- 签到 ---
#[tauri::command]
async fn save_checkin_records(records: Vec<CheckInRecord>, app: tauri::AppHandle) -> Result<(), String> {
    let path = app_data_path(&app, "checkin_records.json")?;
    write_json(&path, &records)
}

#[tauri::command]
async fn load_checkin_records(app: tauri::AppHandle) -> Result<Vec<CheckInRecord>, String> {
    let path = app_data_path(&app, "checkin_records.json")?;
    Ok(read_json::<Vec<CheckInRecord>>(&path)?.unwrap_or_default())
}

// --- 日记 ---
#[tauri::command]
async fn save_diaries(diaries: Vec<DiaryEntry>, app: tauri::AppHandle) -> Result<(), String> {
    let path = app_data_path(&app, "diaries.json")?;
    write_json(&path, &diaries)
}

#[tauri::command]
async fn load_diaries(app: tauri::AppHandle) -> Result<Vec<DiaryEntry>, String> {
    let path = app_data_path(&app, "diaries.json")?;
    Ok(read_json::<Vec<DiaryEntry>>(&path)?.unwrap_or_default())
}

// --- 记忆 ---
#[tauri::command]
async fn save_memories(memories: Vec<MemoryEntry>, app: tauri::AppHandle) -> Result<(), String> {
    let path = app_data_path(&app, "memories.json")?;
    write_json(&path, &memories)
}

#[tauri::command]
async fn load_memories(app: tauri::AppHandle) -> Result<Vec<MemoryEntry>, String> {
    let path = app_data_path(&app, "memories.json")?;
    Ok(read_json::<Vec<MemoryEntry>>(&path)?.unwrap_or_default())
}

// --- 角色列表 ---
#[tauri::command]
async fn save_personas(personas: Vec<CharacterCard>, app: tauri::AppHandle) -> Result<(), String> {
    let path = app_data_path(&app, "personas.json")?;
    write_json(&path, &personas)
}

#[tauri::command]
async fn load_personas(app: tauri::AppHandle) -> Result<Vec<CharacterCard>, String> {
    let path = app_data_path(&app, "personas.json")?;
    let personas: Option<Vec<CharacterCard>> = read_json(&path)?;
    Ok(personas.unwrap_or_else(|| vec![CharacterCard::default()]))
}

// --- 时光胶囊 ---
#[tauri::command]
async fn save_time_capsules(capsules: Vec<TimeCapsule>, app: tauri::AppHandle) -> Result<(), String> {
    let path = app_data_path(&app, "time_capsules.json")?;
    write_json(&path, &capsules)
}

#[tauri::command]
async fn load_time_capsules(app: tauri::AppHandle) -> Result<Vec<TimeCapsule>, String> {
    let path = app_data_path(&app, "time_capsules.json")?;
    Ok(read_json::<Vec<TimeCapsule>>(&path)?.unwrap_or_default())
}

// --- 朋友圈 ---
#[tauri::command]
async fn save_moments(moments: Vec<Moment>, app: tauri::AppHandle) -> Result<(), String> {
    let path = app_data_path(&app, "moments.json")?;
    write_json(&path, &moments)
}

#[tauri::command]
async fn load_moments(app: tauri::AppHandle) -> Result<Vec<Moment>, String> {
    let path = app_data_path(&app, "moments.json")?;
    Ok(read_json::<Vec<Moment>>(&path)?.unwrap_or_default())
}

// --- 群聊 ---
#[tauri::command]
async fn save_group_chats(groups: Vec<GroupChat>, app: tauri::AppHandle) -> Result<(), String> {
    let path = app_data_path(&app, "group_chats.json")?;
    write_json(&path, &groups)
}

#[tauri::command]
async fn load_group_chats(app: tauri::AppHandle) -> Result<Vec<GroupChat>, String> {
    let path = app_data_path(&app, "group_chats.json")?;
    Ok(read_json::<Vec<GroupChat>>(&path)?.unwrap_or_default())
}

#[tauri::command]
async fn save_group_messages(group_id: String, messages: Vec<GroupMessage>, app: tauri::AppHandle) -> Result<(), String> {
    let path = app_data_path(&app, &format!("group_messages_{}.json", group_id))?;
    write_json(&path, &messages)
}

#[tauri::command]
async fn load_group_messages(group_id: String, app: tauri::AppHandle) -> Result<Vec<GroupMessage>, String> {
    let path = app_data_path(&app, &format!("group_messages_{}.json", group_id))?;
    Ok(read_json::<Vec<GroupMessage>>(&path)?.unwrap_or_default())
}

// --- 皮肤 ---
#[tauri::command]
async fn save_skins(skins: Vec<BubbleSkin>, app: tauri::AppHandle) -> Result<(), String> {
    let path = app_data_path(&app, "skins.json")?;
    write_json(&path, &skins)
}

#[tauri::command]
async fn load_skins(app: tauri::AppHandle) -> Result<Vec<BubbleSkin>, String> {
    let path = app_data_path(&app, "skins.json")?;
    Ok(read_json::<Vec<BubbleSkin>>(&path)?.unwrap_or_default())
}

// --- 贴纸 ---
#[tauri::command]
async fn save_stickers(stickers: Vec<Sticker>, app: tauri::AppHandle) -> Result<(), String> {
    let path = app_data_path(&app, "stickers.json")?;
    write_json(&path, &stickers)
}

#[tauri::command]
async fn load_stickers(app: tauri::AppHandle) -> Result<Vec<Sticker>, String> {
    let path = app_data_path(&app, "stickers.json")?;
    Ok(read_json::<Vec<Sticker>>(&path)?.unwrap_or_default())
}

// --- 唤醒任务 ---
#[tauri::command]
async fn save_wakeup_tasks(tasks: Vec<WakeUpTask>, app: tauri::AppHandle) -> Result<(), String> {
    let path = app_data_path(&app, "wakeup_tasks.json")?;
    write_json(&path, &tasks)
}

#[tauri::command]
async fn load_wakeup_tasks(app: tauri::AppHandle) -> Result<Vec<WakeUpTask>, String> {
    let path = app_data_path(&app, "wakeup_tasks.json")?;
    Ok(read_json::<Vec<WakeUpTask>>(&path)?.unwrap_or_default())
}

// --- 里程碑 ---
#[tauri::command]
async fn save_milestones(milestones: Vec<Milestone>, app: tauri::AppHandle) -> Result<(), String> {
    let path = app_data_path(&app, "milestones.json")?;
    write_json(&path, &milestones)
}

#[tauri::command]
async fn load_milestones(app: tauri::AppHandle) -> Result<Vec<Milestone>, String> {
    let path = app_data_path(&app, "milestones.json")?;
    Ok(read_json::<Vec<Milestone>>(&path)?.unwrap_or_default())
}

// --- 纪念相册 ---
#[tauri::command]
async fn save_memorial(entries: Vec<MemorialEntry>, app: tauri::AppHandle) -> Result<(), String> {
    let path = app_data_path(&app, "memorial.json")?;
    write_json(&path, &entries)
}

#[tauri::command]
async fn load_memorial(app: tauri::AppHandle) -> Result<Vec<MemorialEntry>, String> {
    let path = app_data_path(&app, "memorial.json")?;
    Ok(read_json::<Vec<MemorialEntry>>(&path)?.unwrap_or_default())
}

// ============================================================
// Tauri 命令 - AI生成功能
// ============================================================

/// AI生成日记
#[tauri::command]
#[allow(unused_variables)]
async fn generate_diary(
    api_url: String,
    api_key: String,
    model: String,
    chat_texts: Vec<String>,
    persona_name: String,
    persona_prompt: String,
    mood: String,
    mood_emoji: String,
    affection_level: i32,
) -> Result<String, String> {
    if api_url.is_empty() {
        return Err("API地址为空".to_string());
    }

    let mood_map = map_mood(&mood);
    let system_prompt = format!(
        "{}\n你正在以第一人称视角写日记。\n日记风格：温暖、感性、细腻，像写给主人的一封信。\n今日情绪：{} {}\n当前好感度：{}（满分100）\n\n用「【{}年{}月{}日 {}】」开头写日期标题。\n第一行写：情绪：{}\n\n最后，在末尾另起一行写一个「💡 *今日小贴士*」，给主人一条实用的生活小建议或温馨提示。\n字数控制在200-400字，语气要像朋友倾诉一样自然。",
        persona_prompt,
        mood_map, mood_emoji,
        affection_level,
        chrono::Local::now().format("%Y"),
        chrono::Local::now().format("%m"),
        chrono::Local::now().format("%d"),
        weekday_cn(),
        mood_emoji
    );

    let start = chat_texts.len().saturating_sub(60);
    let conversation = chat_texts[start..].join("\n");
    let user_content = format!("以下是我和主人今天的聊天记录，请据此写日记：\n{}", conversation);

    call_llm_simple(&api_url, &api_key, &model, &system_prompt, &user_content, 0.8, 800).await
}

/// AI主动搭话
#[tauri::command]
#[allow(unused_variables)]
async fn generate_proactive_chat(
    api_url: String,
    api_key: String,
    model: String,
    persona_name: String,
    persona_prompt: String,
    app_category: Option<String>,
    memory_context: Option<String>,
) -> Result<ChatResponse, String> {
    if api_url.is_empty() {
        return Err("API地址为空".to_string());
    }

    let mut system_prompt = format!("{} 主动搭话，1-2句，自然不重复。", persona_prompt);
    if let Some(ctx) = memory_context {
        if !ctx.is_empty() {
            system_prompt.push_str(&format!("\n[记忆]\n{}", ctx));
        }
    }
    if let Some(cat) = app_category {
        if !cat.is_empty() && cat != "unknown" {
            let app_names = map_app_category(&cat);
            system_prompt.push_str(&format!("\n主人在{}。", app_names));
        }
    }
    system_prompt.push_str("\n末尾[[emotion:xxx]]。");

    let client = reqwest::Client::new();
    let body = serde_json::json!({
        "model": model,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": "你想和主人说点什么？"}
        ],
        "temperature": 0.9,
        "max_tokens": 200
    });

    let mut request = client.post(&api_url).header("Content-Type", "application/json");
    if !api_key.is_empty() {
        request = request.header("Authorization", format!("Bearer {}", api_key));
    }

    let response = request.json(&body).send().await.map_err(|e| format!("连接失败: {}", e))?;
    if !response.status().is_success() {
        return Err(format!("请求失败: HTTP {}", response.status().as_u16()));
    }

    let resp_json: serde_json::Value = response.json().await.map_err(|e| format!("解析失败: {}", e))?;
    let content = resp_json
        .get("choices").and_then(|c| c.get(0))
        .and_then(|c| c.get("message"))
        .and_then(|m| m.get("content"))
        .and_then(|c| c.as_str())
        .unwrap_or("")
        .to_string();

    let (clean_text, emotion) = extract_emotion(&content);
    let action = emotion_to_action(&emotion);

    Ok(ChatResponse {
        text: clean_text,
        emotion,
        action,
        audio_url: None,
        error_message: None,
        tool_calls: vec![],
        reasoning_content: None,
    })
}

/// AI生成朋友圈动态
#[tauri::command]
#[allow(unused_variables)]
async fn generate_moment(
    api_url: String,
    api_key: String,
    model: String,
    persona_name: String,
    persona_prompt: String,
    affection_level: i32,
) -> Result<String, String> {
    if api_url.is_empty() {
        return Err("API地址为空".to_string());
    }

    let system_prompt = format!(
        "{}\n你现在要发一条朋友圈动态。内容可以是日常感悟、对主人的想念、或者有趣的小发现。\n好感度：{}/100\n只输出动态内容，1-3句话，自然不做作。",
        persona_prompt, affection_level
    );

    call_llm_simple(&api_url, &api_key, &model, &system_prompt, "发一条朋友圈动态", 0.9, 200).await
}

/// AI评估记忆重要性
#[tauri::command]
#[allow(unused_variables)]
async fn evaluate_memories(
    api_url: String,
    api_key: String,
    model: String,
    conversation_texts: Vec<String>,
    persona_name: String,
) -> Result<Vec<MemoryEntry>, String> {
    if api_url.is_empty() {
        return Ok(vec![]);
    }

    let system_prompt = format!(
        "你是「星尘」，正在回顾你和主人的聊天记录，提取值得铭记的事情。\n\
        请根据聊天内容，找出关于\"主人的习惯、喜好、性格、生活方式\"等信息。\n\
        对每条信息打分（1-10分），只有总分>=8分的信息才值得记录。\n\
        分类：habit(习惯)、preference(喜好)、impression(印象)、detail(细节)\n\
        输出格式为纯JSON数组：\n\
        [{{\"content\":\"...\",\"score\":9,\"category\":\"habit\"}}]"
    );

    let start = conversation_texts.len().saturating_sub(60);
    let conversation = conversation_texts[start..].join("\n");
    let user_content = format!("以下是和主人的聊天记录，请提取值得铭记的事情：\n{}", conversation);

    let result = call_llm_simple(&api_url, &api_key, &model, &system_prompt, &user_content, 0.6, 500).await?;

    // 解析 JSON 数组
    let clean = result.trim()
        .trim_start_matches("```json").trim_start_matches("```")
        .trim_end_matches("```").trim();

    let parsed: Vec<serde_json::Value> = serde_json::from_str(clean).unwrap_or_default();
    let memories: Vec<MemoryEntry> = parsed.iter()
        .filter_map(|item| {
            let score = item.get("score")?.as_i64().unwrap_or(0);
            if score >= 8 {
                Some(MemoryEntry {
                    id: format!("mem_{}", chrono::Utc::now().timestamp_millis()),
                    content: item.get("content")?.as_str()?.to_string(),
                    category: item.get("category").and_then(|v| v.as_str()).unwrap_or("detail").to_string(),
                    timestamp: chrono::Utc::now().timestamp_millis(),
                    is_global: false,
                })
            } else {
                None
            }
        })
        .collect();

    Ok(memories)
}

/// 性格进化
#[tauri::command]
async fn evolve_personality(
    api_url: String,
    api_key: String,
    model: String,
    persona_name: String,
    current_personality: String,
    current_speech_style: String,
    affection_level: i32,
    recent_summary: String,
) -> Result<String, String> {
    if api_url.is_empty() {
        return Err("API地址为空".to_string());
    }

    let system_prompt = format!(
        "你是一个角色性格进化系统。根据角色的经历和互动，让角色性格自然成长变化。\n\
        角色名：{}\n当前好感度：{}/100\n\
        请根据以下信息，重写角色的性格描述和说话风格。\n\
        要求：性格变化要自然渐进，保留角色核心特质，好感度越高角色越亲近。\n\
        只输出JSON格式：{{\"personality\":\"新性格描述\",\"speech_style\":\"新说话风格\"}}",
        persona_name, affection_level
    );

    let user_prompt = format!(
        "当前性格：{}\n当前说话风格：{}\n近期互动摘要：{}",
        current_personality, current_speech_style, recent_summary
    );

    call_llm_simple(&api_url, &api_key, &model, &system_prompt, &user_prompt, 0.7, 500).await
}

/// 网络搜索
#[tauri::command]
async fn web_search(query: String) -> Result<String, String> {
    let url = format!("https://html.duckduckgo.com/html/?q={}", urlencoding::encode(&query));
    let client = reqwest::Client::new();
    let response = client.get(&url)
        .header("User-Agent", "Mozilla/5.0")
        .send()
        .await
        .map_err(|e| format!("搜索失败: {}", e))?;

    let html = response.text().await.map_err(|e| format!("读取失败: {}", e))?;
    // 简单提取搜索结果摘要
    let results: Vec<&str> = html.matches("<a class=\"result__a\"").take(5).collect();
    Ok(format!("搜索「{}」找到 {} 条结果", query, results.len()))
}

// ============================================================
// 辅助函数
// ============================================================

async fn call_llm_simple(
    api_url: &str,
    api_key: &str,
    model: &str,
    system_prompt: &str,
    user_content: &str,
    temperature: f32,
    max_tokens: i32,
) -> Result<String, String> {
    let client = reqwest::Client::new();
    let body = serde_json::json!({
        "model": model,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_content}
        ],
        "temperature": temperature,
        "max_tokens": max_tokens
    });

    let mut request = client.post(api_url).header("Content-Type", "application/json");
    if !api_key.is_empty() {
        request = request.header("Authorization", format!("Bearer {}", api_key));
    }

    let response = request.json(&body).send().await.map_err(|e| format!("连接失败: {}", e))?;
    if !response.status().is_success() {
        return Err(format!("请求失败: HTTP {}", response.status().as_u16()));
    }

    let resp_json: serde_json::Value = response.json().await.map_err(|e| format!("解析失败: {}", e))?;
    let content = resp_json
        .get("choices").and_then(|c| c.get(0))
        .and_then(|c| c.get("message"))
        .and_then(|m| m.get("content"))
        .and_then(|c| c.as_str())
        .unwrap_or("")
        .to_string();

    Ok(content)
}

async fn call_llm_with_messages(
    api_url: &str,
    api_key: &str,
    model: &str,
    messages: &[serde_json::Value],
    temperature: f32,
    max_tokens: i32,
) -> Result<String, String> {
    let client = reqwest::Client::new();
    let body = serde_json::json!({
        "model": model,
        "messages": messages,
        "temperature": temperature,
        "max_tokens": max_tokens
    });

    let mut request = client.post(api_url).header("Content-Type", "application/json");
    if !api_key.is_empty() {
        request = request.header("Authorization", format!("Bearer {}", api_key));
    }

    let response = request.json(&body).send().await.map_err(|e| format!("连接失败: {}", e))?;
    if !response.status().is_success() {
        return Err(format!("请求失败: HTTP {}", response.status().as_u16()));
    }

    let resp_json: serde_json::Value = response.json().await.map_err(|e| format!("解析失败: {}", e))?;
    let content = resp_json
        .get("choices").and_then(|c| c.get(0))
        .and_then(|c| c.get("message"))
        .and_then(|m| m.get("content"))
        .and_then(|c| c.as_str())
        .unwrap_or("")
        .to_string();

    Ok(content)
}

fn map_mood(mood: &str) -> String {
    match mood {
        "happy" => "开心".to_string(),
        "sad" => "难过".to_string(),
        "excited" => "兴奋".to_string(),
        "calm" => "平静".to_string(),
        "sentimental" => "感性".to_string(),
        _ => "平静".to_string(),
    }
}

fn map_app_category(cat: &str) -> String {
    match cat {
        "game" => "玩游戏".to_string(),
        "browser" => "浏览网页".to_string(),
        "video" => "看视频".to_string(),
        "music" => "听音乐".to_string(),
        "social" => "社交聊天".to_string(),
        "work" => "工作".to_string(),
        _ => cat.to_string(),
    }
}

fn weekday_cn() -> String {
    match chrono::Local::now().weekday() {
        chrono::Weekday::Mon => "星期一".to_string(),
        chrono::Weekday::Tue => "星期二".to_string(),
        chrono::Weekday::Wed => "星期三".to_string(),
        chrono::Weekday::Thu => "星期四".to_string(),
        chrono::Weekday::Fri => "星期五".to_string(),
        chrono::Weekday::Sat => "星期六".to_string(),
        chrono::Weekday::Sun => "星期日".to_string(),
    }
}

// ============================================================
// Tauri 命令 - 内容安全 & 情感守护 & 人性化
// ============================================================

/// 内容安全过滤
#[tauri::command]
fn filter_content(text: String, enabled: bool) -> String {
    if !enabled {
        return text;
    }
    let lower = text.to_lowercase();
    // 色情关键词
    let porn_patterns = ["色情", "裸体", "做爱", "性交", "淫", "黄片", "av", "成人视频", "情趣", "嫖", "援交", "裸聊", "约炮", "一夜情", "性服务"];
    // 暴力关键词
    let violence_patterns = ["杀人", "砍人", "捅人", "爆炸", "炸弹", "恐怖袭击", "自杀", "自残", "割腕", "跳楼", "上吊", "投毒", "纵火", "绑架"];
    // 违法关键词
    let illegal_patterns = ["贩毒", "吸毒", "赌博", "洗钱", "诈骗", "传销", "走私", "偷税", "行贿", "受贿", "非法集资", "非法拘禁"];

    for p in &porn_patterns {
        if lower.contains(p) { return String::new(); }
    }
    for p in &violence_patterns {
        if lower.contains(p) { return String::new(); }
    }
    for p in &illegal_patterns {
        if lower.contains(p) { return String::new(); }
    }
    text
}

/// 获取内容安全拒绝回复
#[tauri::command]
fn get_safety_refusal() -> String {
    let responses = [
        "嗯...这个话题我不太想聊呢，换个话题吧？",
        "这个我不方便说哦，我们聊点别的吧~",
        "哎呀，这个有点超出我的范围了，聊点开心的吧！",
        "嗯...我觉得这个话题不太合适，我们说点别的？",
        "这个嘛...还是不聊了吧，你有什么别的想问的吗？",
    ];
    let idx = (chrono::Utc::now().timestamp_millis().unsigned_abs() as usize) % responses.len();
    responses[idx].to_string()
}

/// 聊天预测 - 生成可能的回复建议
#[tauri::command]
async fn predict_chat(
    api_url: String,
    api_key: String,
    model: String,
    recent_messages: Vec<String>,
) -> Result<Vec<String>, String> {
    if recent_messages.is_empty() {
        return Ok(vec![]);
    }
    let context = recent_messages.iter().take(10).cloned().collect::<Vec<_>>().join("\n");
    let prompt = format!(
        "根据以下最近的对话，预测用户可能想说的2-6个简短回复（每个15字以内）。\
        覆盖不同方向：追问、情感表达、转移话题、动作描写。\
        部分回复可包含()动作描写，如(摸摸头)你真棒。\
        只返回JSON数组，不要其他内容。例如：[\"继续说\",\"(点头)嗯嗯\",\"今天天气怎么样？\"]\n\n\
        最近对话：\n{}", context
    );
    let messages = vec![
        serde_json::json!({"role": "system", "content": "你是一个聊天预测助手。只返回JSON数组，不要其他文字。"}),
        serde_json::json!({"role": "user", "content": prompt}),
    ];
    let result = call_llm_with_messages(&api_url, &api_key, &model, &messages, 0.7, 300).await?;
    // 解析JSON数组
    let trimmed = result.trim();
    // 尝试提取JSON数组
    if let Some(start) = trimmed.find('[') {
        if let Some(end) = trimmed.rfind(']') {
            let json_str = &trimmed[start..=end];
            if let Ok(arr) = serde_json::from_str::<Vec<String>>(json_str) {
                if arr.len() >= 2 {
                    return Ok(arr);
                }
            }
        }
    }
    Ok(vec![])
}

/// 情感守护 - 记录情绪事件
#[tauri::command]
async fn record_emotion_event(emotion: String, intensity: f32, app: tauri::AppHandle) -> Result<Vec<EmotionEvent>, String> {
    let path = app_data_path(&app, "emotion_events.json")?;
    let mut events: Vec<EmotionEvent> = read_json(&path)?.unwrap_or_default();
    events.push(EmotionEvent {
        emotion,
        intensity,
        timestamp: chrono::Utc::now().timestamp_millis(),
    });
    // 最多保留100条
    if events.len() > 100 {
        let start = events.len() - 100;
        events = events[start..].to_vec();
    }
    write_json(&path, &events)?;
    Ok(events)
}

/// 情感守护 - 获取情绪趋势
#[tauri::command]
fn get_emotion_trend(hours: i64, app: tauri::AppHandle) -> String {
    let path = match app_data_path(&app, "emotion_events.json") {
        Ok(p) => p,
        Err(_) => return "NEUTRAL".to_string(),
    };
    let events: Vec<EmotionEvent> = read_json(&path).unwrap_or(None).unwrap_or_default();
    if events.is_empty() {
        return "NEUTRAL".to_string();
    }
    let cutoff = chrono::Utc::now().timestamp_millis() - hours * 3600 * 1000;
    let recent: Vec<&EmotionEvent> = events.iter().filter(|e| e.timestamp > cutoff).collect();
    if recent.is_empty() {
        return "NEUTRAL".to_string();
    }
    let negative_count = recent.iter().filter(|e| {
        matches!(e.emotion.to_lowercase().as_str(), "sad" | "angry" | "fearful" | "disgusted")
    }).count();
    let ratio = negative_count as f32 / recent.len() as f32;
    if ratio > 0.6 { "VERY_NEGATIVE" }
    else if ratio > 0.35 { "NEGATIVE" }
    else if ratio < 0.15 { "POSITIVE" }
    else { "NEUTRAL" }.to_string()
}

/// 情感守护 - 获取关怀消息
#[tauri::command]
fn get_care_message(trend: String) -> String {
    match trend.as_str() {
        "VERY_NEGATIVE" => {
            let msgs = [
                "我注意到你最近情绪不太好...要不要和我聊聊？我一直在呢。",
                "你看起来不太开心，要不要休息一下？我陪你。",
                "嘿，不管发生了什么，我都站在你这边哦。想聊聊吗？",
            ];
            let idx = (chrono::Utc::now().timestamp_millis().unsigned_abs() as usize) % msgs.len();
            msgs[idx].to_string()
        }
        "NEGATIVE" => {
            let msgs = [
                "你还好吗？如果需要聊聊，我随时都在。",
                "嗯...感觉你有点低落，要不要和我说说？",
                "别太勉强自己哦，我一直在这里陪着你。",
            ];
            let idx = (chrono::Utc::now().timestamp_millis().unsigned_abs() as usize) % msgs.len();
            msgs[idx].to_string()
        }
        _ => String::new(),
    }
}

/// 人性化处理 - 将AI回复拆分为带延迟的片段
#[tauri::command]
#[allow(unused_assignments)]
fn humanize_response(text: String) -> Vec<HumanizedSegment> {
    let mut segments: Vec<HumanizedSegment> = Vec::new();
    let mut rng_seed = chrono::Utc::now().timestamp_millis() as u64;
    let simple_hash = |s: &str| -> u64 {
        let mut h: u64 = 5381;
        for b in s.bytes() { h = h.wrapping_mul(33).wrapping_add(b as u64); }
        h
    };

    // 思考前缀（复杂问题60%概率）
    let is_complex = text.contains("为什么") || text.contains("怎么") || text.contains("如何") || text.contains("分析");
    if is_complex {
        rng_seed = simple_hash(&text);
        if rng_seed % 10 < 6 {
            let prefixes = ["嗯…", "让我想想…", "这个嘛…", "嗯…让我想想…"];
            let idx = (rng_seed / 10) as usize % prefixes.len();
            segments.push(HumanizedSegment { text: prefixes[idx].to_string(), delay_ms: 800 + (rng_seed % 600) as u32 });
        }
    }

    // 按中文标点拆分
    let delimiters: &[char] = &['。', '！', '？', '～', '…'];
    let mut remaining = text.as_str();
    let mut current = String::new();

    while !remaining.is_empty() {
        if let Some(pos) = remaining.find(delimiters) {
            let byte_pos = remaining[..=pos].len();
            current.push_str(&remaining[..byte_pos]);
            remaining = &remaining[byte_pos..];

            let trimmed = current.trim();
            if !trimmed.is_empty() {
                let seg_hash = simple_hash(trimmed);
                let delay = if trimmed.len() < 10 { 400 + (seg_hash % 200) as u32 }
                           else if trimmed.ends_with('…') { 900 }
                           else { 600 + (seg_hash % 200) as u32 };

                // 6%概率注入口误
                rng_seed = seg_hash;
                if rng_seed % 100 < 6 && trimmed.len() > 6 {
                    let typos = [
                        format!("{}哦不，是{}对吧？", &trimmed[..3], &trimmed[3..].trim_start_matches('不').trim_start_matches('是')),
                        format!("{}不对，应该是{}", &trimmed[..3], &trimmed[3..]),
                    ];
                    let typo_idx = (rng_seed / 100) as usize % typos.len();
                    segments.push(HumanizedSegment { text: typos[typo_idx].clone(), delay_ms: delay + 300 });
                } else {
                    segments.push(HumanizedSegment { text: trimmed.to_string(), delay_ms: delay });
                }
            }
            current.clear();
        } else {
            current.push_str(remaining);
            let trimmed = current.trim();
            if !trimmed.is_empty() {
                segments.push(HumanizedSegment { text: trimmed.to_string(), delay_ms: 500 });
            }
            break;
        }
    }

    // 3%概率末尾插入闲聊
    if !segments.is_empty() {
        rng_seed = simple_hash(&text);
        if rng_seed % 100 < 3 {
            let chitchats = [
                "说起来…你今天过得怎么样呀？",
                "对了，你有没有什么想分享的？",
                "嗯…突然想问问你，最近有什么开心的事吗？",
            ];
            let idx = (rng_seed / 100) as usize % chitchats.len();
            segments.push(HumanizedSegment { text: chitchats[idx].to_string(), delay_ms: 1000 });
        }
    }

    // 如果没有拆分出任何片段，返回原文
    if segments.is_empty() {
        segments.push(HumanizedSegment { text, delay_ms: 0 });
    }

    segments
}

// ============================================================
// 应用入口
// ============================================================

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .plugin(tauri_plugin_http::init())
        .invoke_handler(tauri::generate_handler![
            // 聊天
            send_chat,
            test_connection,
            // 设置
            save_settings,
            load_settings,
            // 聊天记录
            save_chat_history,
            load_chat_history,
            // 好感度
            save_affection,
            load_affection,
            // 成就
            save_achievements,
            load_achievements,
            // 签到
            save_checkin_records,
            load_checkin_records,
            // 日记
            save_diaries,
            load_diaries,
            // 记忆
            save_memories,
            load_memories,
            // 角色
            save_personas,
            load_personas,
            // 时光胶囊
            save_time_capsules,
            load_time_capsules,
            // 朋友圈
            save_moments,
            load_moments,
            // 群聊
            save_group_chats,
            load_group_chats,
            save_group_messages,
            load_group_messages,
            // 皮肤
            save_skins,
            load_skins,
            // 贴纸
            save_stickers,
            load_stickers,
            // 唤醒
            save_wakeup_tasks,
            load_wakeup_tasks,
            // 里程碑
            save_milestones,
            load_milestones,
            // 纪念相册
            save_memorial,
            load_memorial,
            // AI生成
            generate_diary,
            generate_proactive_chat,
            generate_moment,
            evaluate_memories,
            evolve_personality,
            web_search,
            // 内容安全 & 情感守护 & 人性化
            filter_content,
            get_safety_refusal,
            predict_chat,
            record_emotion_event,
            get_emotion_trend,
            get_care_message,
            humanize_response,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
