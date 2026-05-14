/** Live2D模型数据类: 模型元数据(名称/版本/文件路径) */
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
