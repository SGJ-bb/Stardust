// 像素宠物Tauri命令

use crate::db::pixelpet_repo::PixelPetRepo;
use crate::models::pixelpet::*;
use crate::state::AppState;
use crate::utils::helpers;
use std::sync::Mutex;
use tauri::State;

/// 列出所有像素宠物
#[tauri::command]
pub async fn list_pixel_pets(state: State<'_, AppState>) -> Result<Vec<PixelPet>, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    PixelPetRepo::list_pets(&db).map_err(|e| e.to_string())
}

/// 获取单个宠物详情
#[tauri::command]
pub async fn get_pixel_pet(state: State<'_, AppState>, id: String) -> Result<Option<PixelPet>, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    PixelPetRepo::get_pet(&db, &id).map_err(|e| e.to_string())
}

/// 获取当前活跃的宠物
#[tauri::command]
pub async fn get_active_pixel_pet(state: State<'_, AppState>) -> Result<Option<PixelPet>, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    PixelPetRepo::get_active_pet(&db).map_err(|e| e.to_string())
}

/// 创建新宠物
#[tauri::command]
pub async fn create_pixel_pet(
    state: State<'_, AppState>,
    req: CreatePetRequest,
) -> Result<PixelPet, String> {
    let now = chrono::Utc::now().timestamp();
    let pet = PixelPet {
        id: helpers::new_uuid(),
        name: req.name,
        description: req.description,
        reference_image_path: req.reference_image_path,
        base_prompt: req.base_prompt,
        negative_prompt: req.negative_prompt,
        sprite_width: req.sprite_width.unwrap_or(64),
        sprite_height: req.sprite_height.unwrap_or(64),
        fps: req.fps.unwrap_or(8),
        scale: req.scale.unwrap_or(3.0),
        render_mode: req.render_mode.unwrap_or_else(|| "pixel_perfect".into()),
        is_active: false,
        created_at: now,
        updated_at: now,
    };
    let db = state.db.lock().map_err(|e| e.to_string())?;
    PixelPetRepo::insert_pet(&db, &pet).map_err(|e| e.to_string())?;
    Ok(pet)
}

/// 更新宠物信息
#[tauri::command]
pub async fn update_pixel_pet(
    state: State<'_, AppState>,
    pet: PixelPet,
) -> Result<(), String> {
    let mut updated = pet.clone();
    updated.updated_at = chrono::Utc::now().timestamp();
    let db = state.db.lock().map_err(|e| e.to_string())?;
    PixelPetRepo::update_pet(&db, &updated).map_err(|e| e.to_string())
}

/// 设为活跃宠物
#[tauri::command]
pub async fn set_active_pixel_pet(state: State<'_, AppState>, id: String) -> Result<(), String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    PixelPetRepo::set_active_pet(&db, &id).map_err(|e| e.to_string())
}

/// 删除宠物（级联删除动作和帧）
#[tauri::command]
pub async fn delete_pixel_pet(state: State<'_, AppState>, id: String) -> Result<(), String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    PixelPetRepo::delete_pet(&db, &id).map_err(|e| e.to_string())
}

// ═══════════════════════════════════════
// 动作命令
// ═══════════════════════════════════════

/// 列出宠物的所有动作（含帧数据）
#[tauri::command]
pub async fn list_pet_actions(
    state: State<'_, AppState>,
    pet_id: String,
) -> Result<Vec<PetAction>, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    let actions = PixelPetRepo::list_actions(&db, &pet_id).map_err(|e| e.to_string())?;
    // 为每个动作加载帧数据
    let mut result = Vec::new();
    for action in actions {
        let frames = PixelPetRepo::list_frames(&db, &action.id).map_err(|e| e.to_string())?;
        result.push(PetAction { frames, ..action });
    }
    Ok(result)
}

/// 创建新动作
#[tauri::command]
pub async fn create_pet_action(
    state: State<'_, AppState>,
    req: CreateActionRequest,
) -> Result<PetAction, String> {
    let action = PetAction {
        id: helpers::new_uuid(),
        pet_id: req.pet_id,
        name: req.name,
        display_name: req.display_name,
        description: req.description,
        prompt: req.prompt,
        frame_count: req.frame_count.unwrap_or(4),
        frame_duration: req.frame_duration.unwrap_or(125),
        loop_mode: req.loop_mode.unwrap_or_else(|| "loop".into()),
        is_builtin: false,
        trigger_events: req.trigger_events.map(|v| serde_json::to_string(&v).unwrap_or_default()),
        sort_order: 0,
        created_at: chrono::Utc::now().timestamp(),
    };

    // 创建帧占位记录
    let frames: Vec<PixelFrame> = (0..action.frame_count)
        .map(|i| PixelFrame {
            id: helpers::new_uuid(),
            action_id: action.id.clone(),
            frame_index: i,
            image_path: String::new(),
            image_hash: None,
            prompt_used: String::new(),
            status: "generating".to_string(),
            generated_at: None,
        })
        .collect();

    let db = state.db.lock().map_err(|e| e.to_string())?;
    PixelPetRepo::insert_action(&db, &action).map_err(|e| e.to_string())?;
    for frame in &frames {
        PixelPetRepo::insert_frame(&db, frame).map_err(|e| e.to_string())?;
    }

    Ok(PetAction { frames, ..action })
}

/// 更新动作
#[tauri::command]
pub async fn update_pet_action(
    state: State<'_, AppState>,
    action: PetAction,
) -> Result<(), String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    PixelPetRepo::update_action(&db, &action).map_err(|e| e.to_string())
}

/// 删除动作（级联删除帧）
#[tauri::command]
pub async fn delete_pet_action(state: State<'_, AppState>, id: String) -> Result<(), String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    PixelPetRepo::delete_action(&db, &id).map_err(|e| e.to_string())
}

// ═══════════════════════════════════════
// 帧数据命令
// ═══════════════════════════════════════

/// 更新单帧状态和图片路径（生成完成后调用）
#[tauri::command]
pub async fn update_frame_status(
    state: State<'_, AppState>,
    frame_id: String,
    status: String,
    image_path: Option<String>,
) -> Result<(), String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    PixelPetRepo::update_frame_status(&db, &frame_id, &status, image_path.as_deref())
        .map_err(|e| e.to_string())
}

// ═══════════════════════════════════════
// 图片生成配置命令
// ═══════════════════════════════════════

/// 获取图片生成配置
#[tauri::command]
pub async fn get_pixel_gen_config(state: State<'_, AppState>) -> Result<PixelGenConfig, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    PixelPetRepo::get_gen_config(&db).map_err(|e| e.to_string())
}

/// 更新图片生成配置
#[tauri::command]
pub async fn update_pixel_gen_config(
    state: State<'_, AppState>,
    req: UpdateGenConfigRequest,
) -> Result<PixelGenConfig, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    let existing = PixelPetRepo::get_gen_config(&db).map_err(|e| e.to_string())?;

    let config = PixelGenConfig {
        provider: req.provider.unwrap_or(existing.provider),
        api_url: req.api_url.or(existing.api_url),
        api_key: req.api_key.or(existing.api_key),
        model: req.model.or(existing.model),
        style_prompt: req.style_prompt.unwrap_or(existing.style_prompt),
        size: req.size.unwrap_or(existing.size),
        steps: req.steps.unwrap_or(existing.steps),
        cfg_scale: req.cfg_scale.unwrap_or(existing.cfg_scale),
        batch_size: req.batch_size.unwrap_or(existing.batch_size),
    };

    PixelPetRepo::upsert_gen_config(&db, &config).map_err(|e| e.to_string())?;
    Ok(config)
}

// ═══════════════════════════════════════
// 图片保存 + API测试命令
// ═══════════════════════════════════════

/// 保存生成的帧图片到本地文件系统，并更新帧状态
#[tauri::command]
pub async fn save_generated_frame(
    state: State<'_, AppState>,
    frame_data: String,   // base64 encoded image data
    pet_id: String,
    action_id: String,
    frame_index: i32,
    prompt: String,
) -> Result<String, String> {
    // 解码 base64 图片数据
    let image_bytes = base64::decode(&frame_data)
        .map_err(|e| format!("base64 decode error: {}", e))?;

    // 构建保存路径: pixel_pets/{pet_id}/{action_id}/frame_{index:02}.png
    let dir_path = std::path::Path::new("pixel_pets")
        .join(&pet_id)
        .join(&action_id);

    // 确保目录存在 (使用项目统一的 get_app_data_dir)
    let db = state.db.lock().map_err(|e| e.to_string())?;
    let app_dir = crate::utils::helpers::get_app_data_dir();
    let full_dir = app_dir.join(&dir_path);
    std::fs::create_dir_all(&full_dir)
        .map_err(|e| format!("Failed to create dir {}: {}", full_dir.display(), e))?;

    let file_name = format!("frame_{:02}.png", frame_index);
    let file_path = full_dir.join(&file_name);

    // 写入文件
    std::fs::write(&file_path, &image_bytes)
        .map_err(|e| format!("Failed to write file {}: {}", file_path.display(), e))?;

    // 更新数据库帧记录状态
    let path_str = file_path.to_string_lossy().to_string();
    // 查找该 action+index 对应的帧ID
    let frames = PixelPetRepo::list_frames(&db, &action_id).map_err(|e| e.to_string())?;
    if let Some(frame) = frames.iter().find(|f| f.frame_index == frame_index) {
        PixelPetRepo::update_frame_status(&db, &frame.id, "ready", Some(&path_str))
            .map_err(|e| e.to_string())?;
    }

    Ok(path_str)
}

/// 测试图片生成API连接（用最小提示词测试）
#[tauri::command]
pub async fn test_pixel_gen_api(
    state: State<'_, AppState>,
    prompt: String,
) -> Result<String, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    let config = PixelPetRepo::get_gen_config(&db).map_err(|e| e.to_string())?;

    if config.api_url.is_none() || config.api_url.as_deref() == Some("") {
        return Err("API URL 未配置".into());
    }
    if config.api_key.is_none() || config.api_key.as_deref() == Some("") {
        return Err("API Key 未配置".into());
    }

    let url = config.api_url.unwrap();
    let key = config.api_key.unwrap();

    // 根据provider发送测试请求
    match config.provider.as_str() {
        "openai" => {
            let client = reqwest::Client::new();
            let resp = client
                .post(&url)
                .header("Content-Type", "application/json")
                .header("Authorization", format!("Bearer {}", key))
                .body(format!(r#"{{"model":"{}","prompt":"{}","n":1,"size":"256x256","response_format":"b64_json"}}",
                    config.model.unwrap_or_else(|| "dall-e-3".into()),
                    prompt))
                .send()
                .await
                .map_err(|e| format!("请求失败: {}", e))?;

            if resp.status().is_success() {
                Ok("连接成功！API响应正常".into())
            } else {
                let status = resp.status();
                let body = resp.text().await.unwrap_or_default();
                Err(format!("API 返回错误 ({}): {}", status, body))
            }
        },
        _ => {
            // SD WebUI 兼容测试
            let client = reqwest::Client::new();
            let test_url = format!("{}/sdapi/v1/txt2img", url.trim_end_matches('/'));
            let resp = client
                .post(&test_url)
                .header("Content-Type", "application/json")
                .header("Authorization", format!("Bearer {}", key))
                .body(format!(r#"{{"prompt":"{}","width":64,"height":64,"steps":1}}"#, prompt))
                .send()
                .await
                .map_err(|e| format!("请求失败: {}", e))?;

            if resp.status().is_success() {
                Ok("连接成功！SD API响应正常".into())
            } else {
                let status = resp.status();
                let body = resp.text().await.unwrap_or_default();
                Err(format!("API 返回错误 ({}): {}", status, body))
            }
        }
    }
}
