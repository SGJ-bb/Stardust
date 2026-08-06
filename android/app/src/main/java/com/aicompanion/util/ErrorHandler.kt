package com.aicompanion.util

import android.content.Context
import android.util.Log
import android.widget.Toast

/**
 * 全局错误处理器
 * 统一处理各类错误，提供用户友好的提示和日志记录
 */
object ErrorHandler {
    private const val TAG = "ErrorHandler"

    /**
     * 处理错误并显示用户提示
     * @param error AppError 错误对象
     * @param context Context 用于显示 Toast
     * @param showToast 是否显示 Toast 提示（默认 true）
     */
    fun handle(error: AppError, context: Context, showToast: Boolean = true) {
        val message = getMessage(error)
        val logMessage = getLogMessage(error)

        // 记录日志
        Log.e(TAG, logMessage)

        // 显示 Toast 提示
        if (showToast) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * 获取用户友好的错误消息
     * @param error AppError 错误对象
     * @return 用户友好的错误提示
     */
    fun getMessage(error: AppError): String {
        return when (error) {
            is AppError.NetworkError -> ErrorMessages.forApiError(error.code)
            is AppError.BusinessError -> ErrorMessages.forBusinessError(error.code, error.message)
            is AppError.SystemError -> ErrorMessages.forInitFailure("", error.exception)
            is AppError.PermissionError -> ErrorMessages.forPermissionError(error.permissionName)
            is AppError.ConfigError -> ErrorMessages.forConfigError(error.configName, error.message)
        }
    }

    /**
     * 获取详细日志消息（包含技术细节）
     * @param error AppError 错误对象
     * @return 技术日志消息
     */
    fun getLogMessage(error: AppError): String {
        return when (error) {
            is AppError.NetworkError ->
                "[NetworkError] HTTP ${error.code}: ${error.message}"
            is AppError.BusinessError ->
                "[BusinessError] ${error.code}: ${error.message}"
            is AppError.SystemError ->
                "[SystemError] ${error.exception.javaClass.simpleName}: ${error.exception.message}"
            is AppError.PermissionError ->
                "[PermissionError] Missing permission: ${error.permissionName}"
            is AppError.ConfigError ->
                "[ConfigError] ${error.configName}: ${error.message}"
        }
    }

    /**
     * 处理初始化错误（带步骤名称）
     * @param name 初始化步骤名称
     * @param error 异常对象
     * @param context Context 用于显示 Toast
     */
    fun handleInitError(name: String, error: Exception, context: Context) {
        val systemError = AppError.SystemError(error)
        val message = ErrorMessages.forInitFailure(name, error)

        Log.e(TAG, "[INIT FAIL] $name: ${error.javaClass.simpleName}: ${error.message}", error)
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}