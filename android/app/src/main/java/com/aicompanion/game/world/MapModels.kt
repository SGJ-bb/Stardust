package com.aicompanion.game.world

/**
 * 地图坐标模型(M1) —— 世界书 schema 地图在运行期的确定性表示。
 * 像素坐标 (x, y) ↔ 格子坐标 (col, row) 双向换算，tile 默认 32px。
 */
data class MapCell(val col: Int, val row: Int) {
    fun toPixel(tileSize: Int = 32): PixelPos =
        PixelPos(col * tileSize + tileSize / 2f, row * tileSize + tileSize / 2f)
}

data class PixelPos(val x: Float, val y: Float) {
    fun toCell(tileSize: Int = 32): MapCell =
        MapCell((x / tileSize).toInt(), (y / tileSize).toInt())

    fun distance(other: PixelPos): Float = kotlin.math.sqrt(
        (x - other.x) * (x - other.x) + (y - other.y) * (y - other.y)
    )
}