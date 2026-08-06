// 插件注册中心，对应原 plugin/PluginRegistry.kt

use std::collections::HashMap;
use std::sync::{Arc, Mutex, RwLock};

use crate::models::chat::ToolDefinition;
use crate::plugins::plugin_trait::{PluginContext, PluginResult, ToolPlugin};

/// 插件包装，包含启用标志和插件实例
struct PluginEntry {
    /// 插件实例
    plugin: Arc<dyn ToolPlugin>,
    /// 启用状态（独立于插件内部状态，使用内部可变性）
    enabled: RwLock<bool>,
}

/// 插件注册中心
pub struct PluginRegistry {
    plugins: Mutex<HashMap<String, PluginEntry>>,
}

impl PluginRegistry {
    /// 创建新的插件注册中心
    pub fn new() -> Self {
        PluginRegistry {
            plugins: Mutex::new(HashMap::new()),
        }
    }

    /// 注册插件
    pub fn register(&self, plugin: Box<dyn ToolPlugin>) {
        let name = plugin.name().to_string();
        let enabled = plugin.is_enabled();
        let mut plugins = self.plugins.lock().unwrap();
        plugins.insert(name, PluginEntry {
            plugin: Arc::from(plugin),
            enabled: RwLock::new(enabled),
        });
    }

    /// 注销插件
    pub fn unregister(&self, name: &str) {
        let mut plugins = self.plugins.lock().unwrap();
        plugins.remove(name);
    }

    /// 获取插件
    pub fn get_plugin(&self, name: &str) -> Option<String> {
        let plugins = self.plugins.lock().unwrap();
        if plugins.contains_key(name) {
            Some(name.to_string())
        } else {
            None
        }
    }

    /// 获取所有已启用插件的工具定义
    pub fn get_enabled_definitions(&self) -> Vec<ToolDefinition> {
        let plugins = self.plugins.lock().unwrap();
        plugins
            .values()
            .filter(|entry| *entry.enabled.read().unwrap())
            .map(|entry| entry.plugin.get_definition())
            .collect()
    }

    /// 执行插件
    pub async fn execute_plugin(
        &self,
        name: &str,
        arguments: &serde_json::Value,
        context: &PluginContext,
    ) -> PluginResult {
        // 先获取插件的Arc引用和启用状态，然后立即释放锁，避免跨await持有MutexGuard
        let plugin_opt: Option<(Arc<dyn ToolPlugin>, bool)> = {
            let plugins = self.plugins.lock().unwrap();
            plugins.get(name).map(|entry| {
                (Arc::clone(&entry.plugin), *entry.enabled.read().unwrap())
            })
        };
        if let Some((plugin, is_enabled)) = plugin_opt {
            if !is_enabled {
                return PluginResult::err(format!("插件 {} 未启用", name));
            }
            let result: PluginResult = plugin.execute(arguments, context).await;
            result
        } else {
            PluginResult::err(format!("插件 {} 不存在", name))
        }
    }

    /// 列出所有插件
    pub fn list_plugins(&self) -> Vec<PluginInfo> {
        let plugins = self.plugins.lock().unwrap();
        plugins
            .values()
            .map(|entry| PluginInfo {
                name: entry.plugin.name().to_string(),
                description: entry.plugin.description().to_string(),
                is_enabled: *entry.enabled.read().unwrap(),
            })
            .collect()
    }

    /// 切换插件启用状态
    /// 使用独立的 RwLock<bool> 管理启用状态，确保修改生效
    pub fn toggle_plugin(&self, name: &str, enabled: bool) -> bool {
        let plugins = self.plugins.lock().unwrap();
        if let Some(entry) = plugins.get(name) {
            let mut enabled_guard = entry.enabled.write().unwrap();
            *enabled_guard = enabled;
            true
        } else {
            false
        }
    }
}

/// 插件信息
#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct PluginInfo {
    pub name: String,
    pub description: String,
    pub is_enabled: bool,
}

impl Default for PluginRegistry {
    fn default() -> Self {
        Self::new()
    }
}
