package com.aicompanion.live2d

data class Live2DModelInfo(
    val id: String,
    val name: String,
    val description: String,
    val modelPath: String,
    val texturePath: String,
    val version: String,
    val sizeMB: Float = 0f,
    val isActive: Boolean = false
)
