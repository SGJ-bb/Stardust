/**
 * 通知监听服务: 监听系统通知，供主动互动引擎消费
 *
 * 使用 NotificationListenerService（系统级 API），监听所有 App 的通知。
 * 用户可通过 setEnabled() 开关、setWhitelist() 配置白名单过滤。
 *
 * 使用前需用户在「设置→通知访问」手动授权。
 * 消费方调用 getAndClearRecent() 获取并清空积压通知。
 */
package com.aicompanion.notification

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.aicompanion.util.AppLogger

class NotificationReaderService : NotificationListenerService() {

    companion object {
        private const val TAG = "NotificationReader"
        private const val PREFS_NAME = "notification_reader"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_WHITELIST = "whitelist"  // 逗号分隔的包名列表（空=监听全部）
        private const val MAX_RECENT = 50

        private var instance: NotificationReaderService? = null

        /** 当前实例是否可用（已连接且已授权） */
        fun isAvailable(): Boolean = instance != null

        /** 通知信息 */
        data class NotificationInfo(
            val packageName: String,
            val title: String,
            val text: String,
            val timestamp: Long,
        )

        /** 积压通知队列（供主动互动引擎消费） */
        private val recentNotifications = mutableListOf<NotificationInfo>()

        /**
         * 获取并清空积压通知（消费式读取）
         * @return 自上次消费以来的所有通知列表
         */
        fun getAndClearRecent(): List<NotificationInfo> {
            synchronized(recentNotifications) {
                val copy = recentNotifications.toList()
                recentNotifications.clear()
                return copy
            }
        }

        /** 设置总开关（关闭后不再积累通知） */
        fun setEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, enabled).apply()
        }

        /** 设置白名单（空字符串=监听全部 App） */
        fun setWhitelist(context: Context, packages: List<String>) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_WHITELIST, packages.joinToString(",")).apply()
        }

        /** 是否已启用 */
        fun isEnabled(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false)
        }
    }

    override fun onListenerConnected() {
        instance = this
        AppLogger.i(TAG, "NotificationListener connected")
    }

    override fun onListenerDisconnected() {
        instance = null
        AppLogger.i(TAG, "NotificationListener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        try {
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            if (!prefs.getBoolean(KEY_ENABLED, false)) return  // 总开关关闭

            val pkg = sbn.packageName?.toString() ?: return

            // 白名单过滤（空白名单=监听全部）
            val whitelist = prefs.getString(KEY_WHITELIST, "") ?: ""
            if (whitelist.isNotBlank()) {
                val allowed = whitelist.split(",").map { it.trim() }
                if (pkg !in allowed) return
            }

            val notification = sbn.notification ?: return
            val extras = notification.extras
            val title = extras.getString(Notification.EXTRA_TITLE, "") ?: ""
            val text = extras.getString(Notification.EXTRA_TEXT, "") ?: ""
            if (title.isBlank() && text.isBlank()) return

            val info = NotificationInfo(pkg, title, text, System.currentTimeMillis())
            synchronized(recentNotifications) {
                recentNotifications.add(info)
                // 限制列表大小，防内存泄漏
                if (recentNotifications.size > MAX_RECENT) {
                    recentNotifications.removeAt(0)
                }
            }
            AppLogger.d(TAG, "Notification from $pkg: $title - $text")
        } catch (e: Exception) {
            AppLogger.e(TAG, "onNotificationPosted error: ${e.message}")
        }
    }
}
