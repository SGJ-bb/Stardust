// 系统命令：show_overlay, get_active_window, search_web, get_app_version

use crate::state::AppState;
use tauri::{Manager, State};

/// 显示/隐藏桌宠覆盖层
#[tauri::command]
pub async fn show_overlay(app_handle: tauri::AppHandle, visible: bool) -> Result<(), String> {
    if let Some(window) = app_handle.get_webview_window("overlay") {
        if visible {
            window.show().map_err(|e: tauri::Error| e.to_string())?;
        } else {
            window.hide().map_err(|e: tauri::Error| e.to_string())?;
        }
    }
    Ok(())
}

/// 设置桌宠窗口位置
#[tauri::command]
pub async fn set_overlay_position(app_handle: tauri::AppHandle, x: f64, y: f64) -> Result<(), String> {
    if let Some(window) = app_handle.get_webview_window("overlay") {
        window.set_position(tauri::Position::Physical(tauri::PhysicalPosition { x: x as i32, y: y as i32 }))
            .map_err(|e| e.to_string())?;
    }
    Ok(())
}

/// 设置桌宠窗口大小
#[tauri::command]
pub async fn set_overlay_size(app_handle: tauri::AppHandle, width: f64, height: f64) -> Result<(), String> {
    if let Some(window) = app_handle.get_webview_window("overlay") {
        window.set_size(tauri::Size::Physical(tauri::PhysicalSize { width: width as u32, height: height as u32 }))
            .map_err(|e| e.to_string())?;
    }
    Ok(())
}

/// 切换桌宠窗口置顶
#[tauri::command]
pub async fn toggle_overlay_always_on_top(app_handle: tauri::AppHandle) -> Result<bool, String> {
    if let Some(window) = app_handle.get_webview_window("overlay") {
        let is_top = window.is_always_on_top().map_err(|e| e.to_string())?;
        window.set_always_on_top(!is_top).map_err(|e| e.to_string())?;
        Ok(!is_top)
    } else {
        Err("Overlay window not found".to_string())
    }
}

/// 获取活动窗口信息
#[tauri::command]
pub async fn get_active_window() -> Result<serde_json::Value, String> {
    // 在实际实现中，这里应该获取当前活动窗口信息
    Ok(serde_json::json!({
        "title": "",
        "process": "",
    }))
}

/// 搜索网页
#[tauri::command]
pub async fn search_web(state: State<'_, AppState>, query: String, num_results: Option<u32>) -> Result<Vec<crate::services::search_service::SearchResult>, String> {
    state.search_service.search(&query, num_results.unwrap_or(5))
        .await
        .map_err(|e: crate::services::search_service::SearchError| e.to_string())
}

/// 获取应用版本
#[tauri::command]
pub async fn get_app_version() -> Result<String, String> {
    Ok(env!("CARGO_PKG_VERSION").to_string())
}
