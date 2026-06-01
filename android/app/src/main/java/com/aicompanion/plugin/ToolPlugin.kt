package com.aicompanion.plugin

import com.aicompanion.models.ToolDefinition

interface ToolPlugin {
    val name: String
    val description: String
    fun getDefinition(): ToolDefinition
    suspend fun execute(arguments: String): String
    fun isEnabled(): Boolean = true
    fun onRegistered() {}
    fun onUnregistered() {}
}
