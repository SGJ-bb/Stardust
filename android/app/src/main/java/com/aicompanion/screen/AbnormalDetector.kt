/** 异常页面检测器: 检测支付/验证码/登录等需要人工介入的场景, 暂停 AI 自动操作 */
package com.aicompanion.screen

import android.view.accessibility.AccessibilityNodeInfo

/** 异常页面检测器：检测支付/验证码/登录等需要人工介入的场景 */
object AbnormalDetector {

    enum class AbnormalType {
        NONE,           // 正常
        PAYMENT,        // 支付/转账页面
        CAPTCHA,        // 验证码
        LOGIN,          // 登录页
        DIALOG,         // 弹窗/对话框
        PERMISSION      // 权限请求弹窗
    }

    /** 支付/转账关键词 */
    private val PAYMENT_KEYWORDS = listOf("支付", "付款", "转账", "确认支付", "输入密码", "支付密码", "指纹支付", "面容支付")

    /** 验证码关键词 */
    private val CAPTCHA_KEYWORDS = listOf("验证码", "滑动验证", "拼图", "请完成验证", "安全验证")

    /** 登录关键词 */
    private val LOGIN_KEYWORDS = listOf("登录", "密码登录", "账号登录", "扫码登录", "注册登录")

    /** 检测当前屏幕是否为异常页面 */
    fun detect(root: AccessibilityNodeInfo?): AbnormalType {
        if (root == null) return AbnormalType.NONE

        val screenText = collectText(root).lowercase()

        // 支付页面优先级最高
        if (PAYMENT_KEYWORDS.any { screenText.contains(it) }) return AbnormalType.PAYMENT
        if (CAPTCHA_KEYWORDS.any { screenText.contains(it) }) return AbnormalType.CAPTCHA
        if (LOGIN_KEYWORDS.any { screenText.contains(it) }) return AbnormalType.LOGIN

        // 检测弹窗（有 Button 且父节点是 AlertDialog/Dialog）
        if (hasDialog(root)) return AbnormalType.DIALOG

        // 权限请求
        if (screenText.contains("允许") && screenText.contains("权限")) return AbnormalType.PERMISSION

        return AbnormalType.NONE
    }

    /** 递归收集所有文字 */
    private fun collectText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val sb = StringBuilder()
        node.text?.let { sb.append(it).append(" ") }
        node.contentDescription?.let { sb.append(it).append(" ") }
        for (i in 0 until node.childCount) {
            sb.append(collectText(node.getChild(i)))
        }
        return sb.toString()
    }

    /** 检测是否有对话框 */
    private fun hasDialog(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val className = node.className?.toString() ?: ""
        if (className.contains("Dialog") || className.contains("Alert")) return true
        for (i in 0 until node.childCount) {
            if (hasDialog(node.getChild(i))) return true
        }
        return false
    }

    /** 获取异常类型的中文描述 */
    fun getDescription(type: AbnormalType): String = when (type) {
        AbnormalType.PAYMENT -> "检测到支付页面，已暂停 AI 操作"
        AbnormalType.CAPTCHA -> "检测到验证码，需要人工完成"
        AbnormalType.LOGIN -> "检测到登录页面，需要人工登录"
        AbnormalType.DIALOG -> "检测到弹窗，需要人工处理"
        AbnormalType.PERMISSION -> "检测到权限请求弹窗"
        AbnormalType.NONE -> ""
    }
}
