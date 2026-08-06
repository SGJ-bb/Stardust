package com.aicompanion.util

/**
 * 统一错误分类
 * 用于区分不同类型的错误，提供更好的错误处理和用户提示
 */
sealed class AppError {
    /**
     * 网络错误：HTTP 状态码错误
     * @param code HTTP 状态码
     * @param message 错误消息
     */
    data class NetworkError(val code: Int, val message: String) : AppError()

    /**
     * 业务错误：服务端返回的业务逻辑错误
     * @param code 业务错误码
     * @param message 错误消息
     */
    data class BusinessError(val code: String, val message: String) : AppError()

    /**
     * 系统错误：本地异常或系统级错误
     * @param exception 异常对象
     */
    data class SystemError(val exception: Exception) : AppError()

    /**
     * 权限错误：缺少必要权限
     * @param permissionName 权限名称
     */
    data class PermissionError(val permissionName: String) : AppError()

    /**
     * 配置错误：配置缺失或错误
     * @param configName 配置项名称
     * @param message 错误消息
     */
    data class ConfigError(val configName: String, val message: String) : AppError()
}