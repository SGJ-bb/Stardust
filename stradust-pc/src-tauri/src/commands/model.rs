// Live2D模型命令：list_models, import_model, delete_model, scan_models

use crate::state::AppState;
use serde::{Deserialize, Serialize};
use tauri::State;

/// Live2D模型信息
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Live2DModel {
    pub id: String,
    pub name: String,
    pub path: String,
    pub thumbnail: Option<String>,
    pub is_active: bool,
}

/// 列出模型
#[tauri::command]
pub async fn list_models(state: State<'_, AppState>) -> Result<Vec<Live2DModel>, String> {
    // 在实际实现中，这里应该扫描模型目录
    Ok(Vec::new())
}

/// 导入模型
#[tauri::command]
pub async fn import_model(state: State<'_, AppState>, path: String, name: String) -> Result<Live2DModel, String> {
    let model = Live2DModel {
        id: crate::utils::helpers::new_uuid(),
        name,
        path,
        thumbnail: None,
        is_active: false,
    };
    Ok(model)
}

/// 删除模型
#[tauri::command]
pub async fn delete_model(state: State<'_, AppState>, id: String) -> Result<(), String> {
    Ok(())
}

/// 扫描模型目录
#[tauri::command]
pub async fn scan_models(state: State<'_, AppState>) -> Result<Vec<Live2DModel>, String> {
    // 在实际实现中，这里应该扫描模型目录
    Ok(Vec::new())
}
