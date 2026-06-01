package com.aicompanion.plugin

import com.aicompanion.models.ToolDefinition
import com.aicompanion.util.AppLogger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

object PluginRegistry {
    private const val TAG = "PluginRegistry"
    private val plugins = ConcurrentHashMap<String, ToolPlugin>()
    private val listeners = CopyOnWriteArrayList<(String, Boolean) -> Unit>()

    fun register(plugin: ToolPlugin) {
        plugins[plugin.name] = plugin
        plugin.onRegistered()
        AppLogger.d(TAG, "Plugin registered: ${plugin.name}")
        notifyListeners(plugin.name, true)
    }

    fun unregister(name: String) {
        plugins.remove(name)?.let {
            it.onUnregistered()
            AppLogger.d(TAG, "Plugin unregistered: $name")
            notifyListeners(name, false)
        }
    }

    fun getPlugin(name: String): ToolPlugin? = plugins[name]

    fun getAllPlugins(): List<ToolPlugin> = plugins.values.toList()

    fun getEnabledDefinitions(): List<ToolDefinition> =
        plugins.values.filter { it.isEnabled() }.map { it.getDefinition() }

    suspend fun executePlugin(name: String, arguments: String): String {
        val plugin = plugins[name]
        return if (plugin != null && plugin.isEnabled()) {
            plugin.execute(arguments)
        } else {
            "未知工具: $name"
        }
    }

    fun addOnPluginChangeListener(listener: (String, Boolean) -> Unit) {
        listeners.add(listener)
    }

    fun removeOnPluginChangeListener(listener: (String, Boolean) -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners(name: String, added: Boolean) {
        listeners.forEach { it(name, added) }
    }

    fun clear() {
        plugins.clear()
        listeners.clear()
    }
}
