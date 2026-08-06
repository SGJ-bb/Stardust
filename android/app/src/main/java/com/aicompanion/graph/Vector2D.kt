package com.aicompanion.graph

import kotlin.math.sqrt

/**
 * 二维向量数据模型
 *
 * 用于表示节点位置、速度等物理量。
 * 支持向量加减、标量乘法、归一化等基本运算。
 */
data class Vector2D(val x: Float, val y: Float) {

    /**
     * 向量加法
     */
    operator fun plus(other: Vector2D): Vector2D {
        return Vector2D(x + other.x, y + other.y)
    }

    /**
     * 向量减法
     */
    operator fun minus(other: Vector2D): Vector2D {
        return Vector2D(x - other.x, y - other.y)
    }

    /**
     * 标量乘法
     */
    operator fun times(scalar: Float): Vector2D {
        return Vector2D(x * scalar, y * scalar)
    }

    /**
     * 标量除法
     */
    operator fun div(scalar: Float): Vector2D {
        return Vector2D(x / scalar, y / scalar)
    }

    /**
     * 向量长度(模)
     */
    fun length(): Float {
        return sqrt(x * x + y * y)
    }

    /**
     * 向量归一化(单位向量)
     * 如果向量为零向量,返回零向量
     */
    fun normalize(): Vector2D {
        val len = length()
        return if (len > 0) this / len else Vector2D(0f, 0f)
    }

    /**
     * 向量点积
     */
    fun dot(other: Vector2D): Float {
        return x * other.x + y * other.y
    }

    /**
     * 向量距离(与另一向量的欧氏距离)
     */
    fun distanceTo(other: Vector2D): Float {
        return (this - other).length()
    }

    /**
     * 限制向量长度
     * 如果向量长度超过maxLength,则缩放到maxLength
     */
    fun limit(maxLength: Float): Vector2D {
        val len = length()
        return if (len > maxLength) normalize() * maxLength else this
    }

    /**
     * 线性插值
     * @param target 目标向量
     * @param t 插值参数(0-1)
     * @return 插值结果
     */
    fun lerp(target: Vector2D, t: Float): Vector2D {
        return this + (target - this) * t
    }

    companion object {
        /**
         * 零向量
         */
        val ZERO = Vector2D(0f, 0f)

        /**
         * 从角度创建单位向量
         * @param angleRadians 角度(弧度)
         * @return 单位向量
         */
        fun fromAngle(angleRadians: Float): Vector2D {
            return Vector2D(
                kotlin.math.cos(angleRadians.toDouble()).toFloat(),
                kotlin.math.sin(angleRadians.toDouble()).toFloat()
            )
        }

        /**
         * 随机向量(单位长度)
         */
        fun random(): Vector2D {
            val angle = kotlin.random.Random.nextDouble() * 2 * Math.PI
            return fromAngle(angle.toFloat())
        }
    }
}