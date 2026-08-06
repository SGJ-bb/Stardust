package com.aicompanion.util

/**
 * 错误消息工具类
 * 提供用户友好的错误提示文案，包含可操作的解决方案
 */
object ErrorMessages {

    /**
     * 初始化失败错误消息
     * @param name 初始化步骤名称
     * @param error 异常对象
     * @return 用户友好的错误提示
     */
    fun forInitFailure(name: String, error: Exception): String {
        val errorMsg = error.message?.lowercase() ?: ""

        return when {
            // 网络相关错误（优先匹配，避免误匹配）
            errorMsg.contains("network") ||
            errorMsg.contains("socket") ||
            errorMsg.contains("connection") ||
            errorMsg.contains("timeout") ||
            errorMsg.contains("unable to resolve host") ||
            errorMsg.contains("ssl") ->
                "网络连接失败，请检查WiFi设置后重启App"

            // 权限相关错误（精确匹配）
            errorMsg.contains("permission denied") ||
            errorMsg.contains("permission") ->
                "缺少必要权限，请在设置中授予App必要权限后重启"

            // 文件相关错误
            errorMsg.contains("file") ||
            errorMsg.contains("io") ||
            errorMsg.contains("not found") ||
            errorMsg.contains("enoent") ->
                "文件读取失败，请尝试重新安装App"

            // 内存相关错误
            errorMsg.contains("memory") ||
            errorMsg.contains("oom") ->
                "内存不足，请关闭其他应用后重试"

            // 配置相关错误
            errorMsg.contains("config") ||
            errorMsg.contains("setting") ->
                "配置加载失败，请检查设置后重试"

            // JSON/数据解析错误
            errorMsg.contains("json") ||
            errorMsg.contains("parse") ||
            errorMsg.contains("format") ->
                "数据格式错误，请尝试清除App数据后重试"

            // 数据库错误
            errorMsg.contains("database") ||
            errorMsg.contains("sql") ->
                "数据库错误，请尝试重启App"

            // 默认错误：提供客服联系方式
            else -> {
                val errorDetail = if (errorMsg.isNotBlank()) "(${errorMsg.take(30)})" else ""
                "初始化失败: $name $errorDetail\n如问题持续，请联系客服: support@stradust.ai"
            }
        }
    }

    /**
     * API HTTP 状态码错误消息
     * @param code HTTP 状态码
     * @return 用户友好的错误提示
     */
    fun forApiError(code: Int): String {
        return when (code) {
            400 -> "请求格式错误，请检查设置后重试"
            401 -> "API密钥无效，请在设置中检查密钥是否正确"
            402 -> "余额不足，请前往API厂商充值"
            403 -> "无权限访问，请检查API密钥权限设置"
            404 -> "API地址错误，请检查设置中的地址是否正确"
            408 -> "请求超时，请检查网络后重试"
            429 -> "请求过于频繁，请稍后重试"
            500 -> "服务端错误，请稍后重试或联系客服"
            502 -> "网关错误，服务暂时不可用，请稍后重试"
            503 -> "服务暂时不可用，请稍后重试"
            504 -> "网关超时，请检查网络后重试"
            in 400..499 -> "请求错误(HTTP $code)，请检查设置后重试"
            in 500..599 -> "服务端错误(HTTP $code)，请稍后重试"
            else -> "网络错误(HTTP $code)，请检查网络后重试"
        }
    }

    /**
     * 权限错误消息
     * @param permissionName 权限名称
     * @return 用户友好的错误提示
     */
    fun forPermissionError(permissionName: String): String {
        val permissionMap = mapOf(
            "camera" to "相机",
            "microphone" to "麦克风",
            "storage" to "存储",
            "location" to "位置",
            "notification" to "通知"
        )

        val cnName = permissionMap.entries.find {
            permissionName.lowercase().contains(it.key)
        }?.value ?: permissionName

        return "需要${cnName}权限才能使用此功能，请在设置中授予权限"
    }

    /**
     * 配置错误消息
     * @param configName 配置项名称
     * @param detail 错误详情
     * @return 用户友好的错误提示
     */
    fun forConfigError(configName: String, detail: String = ""): String {
        val configMap = mapOf(
            "api" to "API配置",
            "persona" to "角色配置",
            "model" to "模型配置",
            "theme" to "主题配置"
        )

        val cnName = configMap.entries.find {
            configName.lowercase().contains(it.key)
        }?.value ?: configName

        val extraDetail = if (detail.isNotBlank()) "：$detail" else ""
        return "$cnName 错误$extraDetail，请检查设置或重启App"
    }

    /**
     * 业务错误消息
     * @param code 业务错误码
     * @param message 原始错误消息
     * @return 用户友好的错误提示
     */
    fun forBusinessError(code: String, message: String): String {
        // 根据业务错误码提供更友好的提示
        return when {
            code.startsWith("AUTH_") -> "认证失败：$message"
            code.startsWith("LIMIT_") -> "已达到限制：$message"
            code.startsWith("INVALID_") -> "输入无效：$message"
            else -> message // 使用原始消息
        }
    }
}