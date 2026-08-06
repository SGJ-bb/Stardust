// 主题管理，对应原theme/ThemeManager.kt

use crate::db::database::Database;
use crate::db::settings_repo;
use crate::models::settings::{ThemeSettings, ThemeMode};

/// 主题服务
pub struct ThemeService;

/// 预设配色方案
#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct PresetTheme {
    pub name: String,
    pub name_cn: String,
    pub primary_color: String,
    pub accent_color: String,
    pub background: String,
}

impl ThemeService {
    pub fn new() -> Self {
        ThemeService
    }

    /// 获取当前主题
    pub fn get_theme(&self, db: &Database) -> Result<ThemeSettings, crate::db::database::DbError> {
        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;

        Ok(ThemeSettings {
            mode: settings_repo::get_setting(&conn, "theme_mode")?
                .and_then(|v| serde_json::from_str(&v).ok())
                .unwrap_or(ThemeMode::Dark),
            primary_color: settings_repo::get_setting(&conn, "theme_primary_color")?
                .unwrap_or_else(|| "#6C5CE7".to_string()),
            accent_color: settings_repo::get_setting(&conn, "theme_accent_color")?
                .unwrap_or_else(|| "#A29BFE".to_string()),
            background: settings_repo::get_setting(&conn, "theme_background")?
                .unwrap_or_else(|| "#1A1A2E".to_string()),
            font_size: settings_repo::get_setting(&conn, "theme_font_size")?
                .and_then(|v| v.parse().ok()).unwrap_or(14),
            custom_css: settings_repo::get_setting(&conn, "theme_custom_css")?,
        })
    }

    /// 更新主题
    pub fn update_theme(&self, db: &Database, theme: &ThemeSettings) -> Result<(), crate::db::database::DbError> {
        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;

        settings_repo::set_setting(&conn, "theme_mode", &serde_json::to_string(&theme.mode).unwrap_or_default())?;
        settings_repo::set_setting(&conn, "theme_primary_color", &theme.primary_color)?;
        settings_repo::set_setting(&conn, "theme_accent_color", &theme.accent_color)?;
        settings_repo::set_setting(&conn, "theme_background", &theme.background)?;
        settings_repo::set_setting(&conn, "theme_font_size", &theme.font_size.to_string())?;

        if let Some(css) = &theme.custom_css {
            settings_repo::set_setting(&conn, "theme_custom_css", css)?;
        }

        Ok(())
    }

    /// 获取9套预设配色
    pub fn get_preset_themes() -> Vec<PresetTheme> {
        vec![
            PresetTheme {
                name: "sakura_pink".to_string(),
                name_cn: "樱粉".to_string(),
                primary_color: "#FF6B9D".to_string(),
                accent_color: "#FFB3C6".to_string(),
                background: "#2D1B2E".to_string(),
            },
            PresetTheme {
                name: "peach_pink".to_string(),
                name_cn: "桃粉".to_string(),
                primary_color: "#FF8A80".to_string(),
                accent_color: "#FFCCBC".to_string(),
                background: "#2E1B1B".to_string(),
            },
            PresetTheme {
                name: "violet".to_string(),
                name_cn: "紫罗兰".to_string(),
                primary_color: "#6C5CE7".to_string(),
                accent_color: "#A29BFE".to_string(),
                background: "#1A1A2E".to_string(),
            },
            PresetTheme {
                name: "ocean_blue".to_string(),
                name_cn: "海蓝".to_string(),
                primary_color: "#0984E3".to_string(),
                accent_color: "#74B9FF".to_string(),
                background: "#0C2461".to_string(),
            },
            PresetTheme {
                name: "emerald".to_string(),
                name_cn: "翡翠".to_string(),
                primary_color: "#00B894".to_string(),
                accent_color: "#55EFC4".to_string(),
                background: "#0A2922".to_string(),
            },
            PresetTheme {
                name: "sunset".to_string(),
                name_cn: "日落".to_string(),
                primary_color: "#E17055".to_string(),
                accent_color: "#FAB1A0".to_string(),
                background: "#2D1F1A".to_string(),
            },
            PresetTheme {
                name: "rose_gold".to_string(),
                name_cn: "玫瑰金".to_string(),
                primary_color: "#B76E79".to_string(),
                accent_color: "#E8C4C4".to_string(),
                background: "#2A1F22".to_string(),
            },
            PresetTheme {
                name: "mint".to_string(),
                name_cn: "薄荷".to_string(),
                primary_color: "#00CEC9".to_string(),
                accent_color: "#81ECEC".to_string(),
                background: "#0A2A2A".to_string(),
            },
            PresetTheme {
                name: "dark_night".to_string(),
                name_cn: "暗夜".to_string(),
                primary_color: "#636E72".to_string(),
                accent_color: "#B2BEC3".to_string(),
                background: "#0D0D0D".to_string(),
            },
        ]
    }

    /// 应用预设主题
    pub fn apply_preset(&self, db: &Database, preset_name: &str) -> Result<ThemeSettings, crate::db::database::DbError> {
        let presets = Self::get_preset_themes();
        let preset = presets.iter()
            .find(|p| p.name == preset_name)
            .ok_or_else(|| crate::db::database::DbError::NotFound(format!("预设主题 {} 不存在", preset_name)))?;

        let theme = ThemeSettings {
            mode: ThemeMode::Dark,
            primary_color: preset.primary_color.clone(),
            accent_color: preset.accent_color.clone(),
            background: preset.background.clone(),
            font_size: 14,
            custom_css: None,
        };

        self.update_theme(db, &theme)?;
        Ok(theme)
    }
}
