// 聊天相关命令：send_chat, send_chat_stream, clear_chat_history, toggle_favorite

use crate::state::AppState;
use crate::models::chat::{ChatMessage, ChatRole, ApiMessage};
use crate::utils::helpers;
use tauri::State;

/// 发送聊天消息（流式）
#[tauri::command]
pub async fn send_chat_stream(
    state: State<'_, AppState>,
    app_handle: tauri::AppHandle,
    persona_id: String,
    session_id: String,
    content: String,
) -> Result<serde_json::Value, String> {
    // 记录互动
    state.interaction_engine.lock().map_err(|e| e.to_string())?.record_interaction();

    // 在一次锁获取中读取角色和供应商配置，减少锁竞争
    let (persona, provider) = {
        let db = state.db.lock().map_err(|e| e.to_string())?;
        let conn = db.conn.lock().map_err(|e| e.to_string())?;
        let persona = crate::db::persona_repo::get_persona(&conn, &persona_id)
            .map_err(|e| e.to_string())?;
        let settings = crate::db::settings_repo::get_all_settings(&conn)
            .map_err(|e| e.to_string())?;
        let provider = match settings.active_provider_id {
            Some(ref id) => crate::db::settings_repo::get_provider(&conn, id)
                .map_err(|e| e.to_string())?,
            None => return Err("未配置LLM供应商".to_string()),
        };
        (persona, provider)
    };

    // 构建提示词
    let system_messages = {
        let db = state.db.lock().map_err(|e| e.to_string())?;
        state.prompt_builder.build_full_prompt(
            &db, &persona, &session_id, &content,
        )
    };

    // 获取历史消息
    let history_messages = {
        let db = state.db.lock().map_err(|e| e.to_string())?;
        let conn = db.conn.lock().map_err(|e| e.to_string())?;
        crate::db::chat_repo::get_session_messages(&conn, &session_id, Some(50))
            .map_err(|e| e.to_string())?
    };

    // 转换历史消息为API格式，过滤掉 system 角色消息（避免与系统提示词重复）
    let mut api_messages = system_messages;
    for msg in &history_messages {
        if msg.role == ChatRole::System {
            continue; // 跳过历史中的 system 消息
        }
        api_messages.push(ApiMessage {
            role: match msg.role {
                ChatRole::User => "user",
                ChatRole::Assistant => "assistant",
                ChatRole::Tool => "tool",
                ChatRole::System => "system", // 不会到达此处
            }.to_string(),
            content: Some(msg.content.clone()),
            tool_calls: None,
            tool_call_id: None,
            name: None,
        });
    }

    // 添加用户消息
    api_messages.push(ApiMessage {
        role: "user".to_string(),
        content: Some(content.clone()),
        tool_calls: None,
        tool_call_id: None,
        name: None,
    });

    // 保存用户消息
    let user_msg = ChatMessage {
        id: helpers::new_uuid(),
        persona_id: persona_id.clone(),
        session_id: session_id.clone(),
        role: ChatRole::User,
        content: content.clone(),
        emotion: None,
        action: None,
        tool_calls: None,
        timestamp: helpers::now(),
        is_favorite: false,
    };

    {
        let db = state.db.lock().map_err(|e| e.to_string())?;
        let conn = db.conn.lock().map_err(|e| e.to_string())?;
        crate::db::chat_repo::save_message(&conn, &user_msg)
            .map_err(|e| e.to_string())?;
    }

    // 获取工具定义
    let tools = state.plugin_registry.get_enabled_definitions();

    // 发送请求
    let plugin_context = crate::plugins::plugin_trait::PluginContext {
        persona_id: persona_id.clone(),
        session_id: session_id.clone(),
        extra: Default::default(),
    };

    let response = if tools.is_empty() {
        state.llm_service.send_chat_stream(
            &provider, api_messages, None, &app_handle, "chat-stream",
        ).await.map_err(|e: crate::services::llm_service::LlmError| e.to_string())?
    } else {
        state.llm_service.send_chat_with_tool_loop(
            &provider, api_messages, tools, &state.plugin_registry,
            &plugin_context, &app_handle, "chat-stream",
        ).await.map_err(|e: crate::services::llm_service::LlmError| e.to_string())?
    };

    // 解析响应
    let (reply_content, tool_calls, emotion, action) =
        crate::services::llm_service::LlmService::parse_openai_response(&response);

    // 情绪守护：使用从标签提取的情绪直接做守护，而非重新分析文本
    let final_emotion = if let Some(ref e) = emotion {
        let parsed_emotion = crate::models::emotion::Emotion::from_str(e)
            .unwrap_or(crate::models::emotion::Emotion::Neutral);
        let ctx = crate::services::emotion_service::EmotionContext::default();
        let guard = state.emotion_service.guard_emotion(&parsed_emotion, &ctx);
        Some(guard.adjusted_emotion.to_chinese().to_string())
    } else {
        emotion
    };

    // 保存助手消息和更新记忆/好感度，合并到一次锁获取中
    {
        let db = state.db.lock().map_err(|e| e.to_string())?;
        let conn = db.conn.lock().map_err(|e| e.to_string())?;

        let assistant_msg = ChatMessage {
            id: helpers::new_uuid(),
            persona_id: persona_id.clone(),
            session_id: session_id.clone(),
            role: ChatRole::Assistant,
            content: reply_content.clone(),
            emotion: final_emotion.clone(),
            action: action.clone(),
            tool_calls,
            timestamp: helpers::now(),
            is_favorite: false,
        };

        crate::db::chat_repo::save_message(&conn, &assistant_msg)
            .map_err(|e| e.to_string())?;
    }

    // 评估记忆
    let evaluation = state.memory_service.evaluate_turn(&content, &reply_content);
    if evaluation.should_remember {
        let db = state.db.lock().map_err(|e| e.to_string())?;
        let _ = state.memory_service.add_memory_fact(
            &db, &persona_id, &content, evaluation.category, 0.5,
        );
    }

    // 更新好感度
    let is_positive = state.affection_service.is_positive_behavior(&content);
    let eval = state.affection_service.evaluate_user_behavior(&content, is_positive);
    {
        let db = state.db.lock().map_err(|e| e.to_string())?;
        let _ = state.affection_service.add_exp(
            &db, &persona_id, eval.exp_change, eval.trust_change, eval.intimacy_change,
        );
    }

    Ok(serde_json::json!({
        "content": reply_content,
        "emotion": final_emotion,
        "action": action,
    }))
}

/// 发送聊天消息（非流式）
/// 使用 LLM 服务的非流式方法，而非调用流式接口
#[tauri::command]
pub async fn send_chat(
    state: State<'_, AppState>,
    persona_id: String,
    session_id: String,
    content: String,
) -> Result<serde_json::Value, String> {
    // 记录互动
    state.interaction_engine.lock().map_err(|e| e.to_string())?.record_interaction();

    // 在一次锁获取中读取角色和供应商配置，减少锁竞争
    let (persona, provider) = {
        let db = state.db.lock().map_err(|e| e.to_string())?;
        let conn = db.conn.lock().map_err(|e| e.to_string())?;
        let persona = crate::db::persona_repo::get_persona(&conn, &persona_id)
            .map_err(|e| e.to_string())?;
        let settings = crate::db::settings_repo::get_all_settings(&conn)
            .map_err(|e| e.to_string())?;
        let provider = match settings.active_provider_id {
            Some(ref id) => crate::db::settings_repo::get_provider(&conn, id)
                .map_err(|e| e.to_string())?,
            None => return Err("未配置LLM供应商".to_string()),
        };
        (persona, provider)
    };

    // 构建提示词
    let system_messages = {
        let db = state.db.lock().map_err(|e| e.to_string())?;
        state.prompt_builder.build_full_prompt(
            &db, &persona, &session_id, &content,
        )
    };

    // 获取历史消息
    let history_messages = {
        let db = state.db.lock().map_err(|e| e.to_string())?;
        let conn = db.conn.lock().map_err(|e| e.to_string())?;
        crate::db::chat_repo::get_session_messages(&conn, &session_id, Some(50))
            .map_err(|e| e.to_string())?
    };

    // 转换历史消息为API格式，过滤掉 system 角色消息
    let mut api_messages = system_messages;
    for msg in &history_messages {
        if msg.role == ChatRole::System {
            continue;
        }
        api_messages.push(ApiMessage {
            role: match msg.role {
                ChatRole::User => "user",
                ChatRole::Assistant => "assistant",
                ChatRole::Tool => "tool",
                ChatRole::System => "system",
            }.to_string(),
            content: Some(msg.content.clone()),
            tool_calls: None,
            tool_call_id: None,
            name: None,
        });
    }

    // 添加用户消息
    api_messages.push(ApiMessage {
        role: "user".to_string(),
        content: Some(content.clone()),
        tool_calls: None,
        tool_call_id: None,
        name: None,
    });

    // 保存用户消息
    let user_msg = ChatMessage {
        id: helpers::new_uuid(),
        persona_id: persona_id.clone(),
        session_id: session_id.clone(),
        role: ChatRole::User,
        content: content.clone(),
        emotion: None,
        action: None,
        tool_calls: None,
        timestamp: helpers::now(),
        is_favorite: false,
    };

    {
        let db = state.db.lock().map_err(|e| e.to_string())?;
        let conn = db.conn.lock().map_err(|e| e.to_string())?;
        crate::db::chat_repo::save_message(&conn, &user_msg)
            .map_err(|e| e.to_string())?;
    }

    // 调用非流式接口
    let response = state.llm_service.send_chat(
        &provider, api_messages, None,
    ).await.map_err(|e: crate::services::llm_service::LlmError| e.to_string())?;

    // 解析响应
    let (reply_content, tool_calls, emotion, action) =
        crate::services::llm_service::LlmService::parse_openai_response(&response);

    // 情绪守护
    let final_emotion = if let Some(ref e) = emotion {
        let parsed_emotion = crate::models::emotion::Emotion::from_str(e)
            .unwrap_or(crate::models::emotion::Emotion::Neutral);
        let ctx = crate::services::emotion_service::EmotionContext::default();
        let guard = state.emotion_service.guard_emotion(&parsed_emotion, &ctx);
        Some(guard.adjusted_emotion.to_chinese().to_string())
    } else {
        emotion
    };

    // 保存助手消息和更新记忆/好感度，合并到一次锁获取中
    {
        let db = state.db.lock().map_err(|e| e.to_string())?;
        let conn = db.conn.lock().map_err(|e| e.to_string())?;

        let assistant_msg = ChatMessage {
            id: helpers::new_uuid(),
            persona_id: persona_id.clone(),
            session_id: session_id.clone(),
            role: ChatRole::Assistant,
            content: reply_content.clone(),
            emotion: final_emotion.clone(),
            action: action.clone(),
            tool_calls,
            timestamp: helpers::now(),
            is_favorite: false,
        };

        crate::db::chat_repo::save_message(&conn, &assistant_msg)
            .map_err(|e| e.to_string())?;
    }

    // 评估记忆
    let evaluation = state.memory_service.evaluate_turn(&content, &reply_content);
    if evaluation.should_remember {
        let db = state.db.lock().map_err(|e| e.to_string())?;
        let _ = state.memory_service.add_memory_fact(
            &db, &persona_id, &content, evaluation.category, 0.5,
        );
    }

    // 更新好感度
    let is_positive = state.affection_service.is_positive_behavior(&content);
    let eval = state.affection_service.evaluate_user_behavior(&content, is_positive);
    {
        let db = state.db.lock().map_err(|e| e.to_string())?;
        let _ = state.affection_service.add_exp(
            &db, &persona_id, eval.exp_change, eval.trust_change, eval.intimacy_change,
        );
    }

    Ok(serde_json::json!({
        "content": reply_content,
        "emotion": final_emotion,
        "action": action,
    }))
}

/// 清除聊天历史
#[tauri::command]
pub async fn clear_chat_history(
    state: State<'_, AppState>,
    session_id: String,
) -> Result<(), String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    let conn = db.conn.lock().map_err(|e| e.to_string())?;
    crate::db::chat_repo::clear_session_messages(&conn, &session_id)
        .map_err(|e| e.to_string())
}

/// 切换收藏
#[tauri::command]
pub async fn toggle_favorite(
    state: State<'_, AppState>,
    message_id: String,
) -> Result<bool, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    let conn = db.conn.lock().map_err(|e| e.to_string())?;
    crate::db::chat_repo::toggle_favorite(&conn, &message_id)
        .map_err(|e| e.to_string())
}

/// 获取收藏列表
#[tauri::command]
pub async fn get_favorites(
    state: State<'_, AppState>,
    persona_id: String,
) -> Result<Vec<ChatMessage>, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    state.favorite_service.get_favorites(&db, &persona_id)
        .map_err(|e: crate::db::database::DbError| e.to_string())
}

/// 触发主动互动
#[tauri::command]
pub async fn trigger_proactive_interaction(
    state: State<'_, AppState>,
) -> Result<serde_json::Value, String> {
    let engine = state.interaction_engine.lock().map_err(|e| e.to_string())?;

    let should_proactive = engine.should_proactive_interact();
    let should_nag = engine.should_nag();

    if should_proactive {
        let content = engine.generate_proactive_content();
        Ok(serde_json::json!({
            "type": "proactive",
            "content": content,
        }))
    } else if should_nag {
        let content = engine.generate_nag_content();
        Ok(serde_json::json!({
            "type": "nag",
            "content": content,
        }))
    } else {
        Ok(serde_json::json!({
            "type": "none",
            "content": "",
        }))
    }
}

/// 触发角色性格进化
#[tauri::command]
pub async fn evolve_personality(
    state: State<'_, AppState>,
    persona_id: String,
    interaction_summary: String,
) -> Result<String, String> {
    let (provider, current_personality) = {
        let db = state.db.lock().map_err(|e| e.to_string())?;
        let conn = db.conn.lock().map_err(|e| e.to_string())?;
        let persona = crate::db::persona_repo::get_persona(&conn, &persona_id)
            .map_err(|e| e.to_string())?;
        let settings = crate::db::settings_repo::get_all_settings(&conn)
            .map_err(|e| e.to_string())?;
        let provider = match settings.active_provider_id {
            Some(ref id) => crate::db::settings_repo::get_provider(&conn, id)
                .map_err(|e| e.to_string())?,
            None => return Err("未配置LLM供应商".to_string()),
        };
        (provider, persona.personality)
    };

    state.llm_service.evolve_personality(&provider, &current_personality, &interaction_summary)
        .await
        .map_err(|e: crate::services::llm_service::LlmError| e.to_string())
}

/// 管理难忘时刻
#[tauri::command]
pub async fn get_memorable_moments(
    state: State<'_, AppState>,
    persona_id: String,
    min_score: Option<f32>,
) -> Result<Vec<crate::models::memory::MemorableMoment>, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    state.memory_service.get_memorable_moments(&db, &persona_id, min_score.unwrap_or(0.8))
        .map_err(|e: crate::db::database::DbError| e.to_string())
}

/// 管理昵称
#[tauri::command]
pub async fn get_nickname(
    state: State<'_, AppState>,
    persona_id: String,
) -> Result<Option<String>, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    state.nickname_service.get_nickname(&db, &persona_id)
        .map_err(|e: crate::db::database::DbError| e.to_string())
}

/// 设置昵称
#[tauri::command]
pub async fn set_nickname(
    state: State<'_, AppState>,
    persona_id: String,
    nickname: String,
) -> Result<(), String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    state.nickname_service.set_nickname(&db, &persona_id, &nickname)
        .map_err(|e: crate::db::database::DbError| e.to_string())
}

/// 导入Android数据
#[tauri::command]
pub async fn import_from_android(
    state: State<'_, AppState>,
    data: serde_json::Value,
) -> Result<crate::services::migration_service::MigrationResult, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    state.migration_service.import_from_android(&db, &data)
        .map_err(|e: crate::db::database::DbError| e.to_string())
}

/// 构建RAG索引
#[tauri::command]
pub async fn build_rag_index(
    state: State<'_, AppState>,
    persona_id: String,
) -> Result<(), String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    // 获取该角色的所有记忆内容用于构建索引
    let memories = state.memory_service.search_memories(&db, &persona_id, "")
        .map_err(|e: crate::db::database::DbError| e.to_string())?;
    let content: Vec<String> = memories.iter().map(|m| m.memory.content.clone()).collect();
    let full_content = content.join("\n");
    state.rag_service.index_document(&db, &persona_id, "persona", &persona_id, &full_content)
        .map_err(|e: crate::db::database::DbError| e.to_string())
}
