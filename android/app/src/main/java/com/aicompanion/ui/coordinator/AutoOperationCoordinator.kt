package com.aicompanion.ui.coordinator

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import com.aicompanion.models.Action
import com.aicompanion.models.Emotion
import com.aicompanion.network.ApiClient
import com.aicompanion.settings.SettingsManager
import com.aicompanion.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AutoOperationCoordinator(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    private val messageScope: CoroutineScope,
    private val settingsManagerProvider: () -> SettingsManager?,
    private val apiClientProvider: () -> ApiClient?,
    private val onPetMessage: (String, Emotion, Action) -> Unit
) : BaseCoordinator(context, lifecycleOwner) {

    companion object {
        private const val TAG = "AutoOperationCoordinator"
    }

    fun showAutoOperationDialog() {
        if (!isAtLeast(androidx.lifecycle.Lifecycle.State.CREATED)) return
        val activity = context as? android.app.Activity ?: return
        if (activity.isFinishing || activity.isDestroyed) return

        val sm = settingsManagerProvider() ?: return
        if (sm.chatApiUrl.isBlank()) {
            Toast.makeText(context, "请先在设置中配置API", Toast.LENGTH_SHORT).show()
            return
        }

        if (!com.aicompanion.screen.AutoOperator.isServiceReady()) {
            AlertDialog.Builder(context)
                .setTitle("需要无障碍权限")
                .setMessage("手机自动化需要开启「星尘AI」的无障碍服务才能操作手机。\n\n请前往：系统设置 → 无障碍 → 已安装应用 → 星尘AI → 开启服务\n\n开启后重新进入此功能即可使用。")
                .setPositiveButton("去开启") { _, _ ->
                    try {
                        activity.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "showAutoOperationDialog: ${e.message}")
                    }
                }
                .setNegativeButton("取消", null)
                .show()
            return
        }

        val input = EditText(context).apply {
            hint = "例：帮我把音量调到最大 / 打开微信找张三"
            setTextColor(0xFFe8e8f0.toInt())
            setHintTextColor(0xFF667788.toInt())
            setBackgroundResource(android.R.color.transparent)
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
            setBackgroundColor(0xFF0f0c29.toInt())
            addView(input)
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle("🤖 手机自动化")
            .setView(container)
            .setPositiveButton("开始执行", null)
            .setNegativeButton("取消", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val request = input.text.toString().trim()
                if (request.isEmpty()) return@setOnClickListener
                dialog.dismiss()
                executeAutoOperation(request)
            }
        }

        dialog.show()
    }

    private fun executeAutoOperation(userRequest: String) {
        if (!isAtLeast(androidx.lifecycle.Lifecycle.State.CREATED)) return

        Toast.makeText(context, "🤖 正在分析指令...", Toast.LENGTH_LONG).show()
        onPetMessage("主人让我帮你操作手机：$userRequest", Emotion.HAPPY, Action.IDLE)

        messageScope.launch {
            try {
                val screenInfo = withContext(Dispatchers.IO) {
                    com.aicompanion.screen.AutoOperator.formatScreenForLLM()
                }

                val llmResult = withContext(Dispatchers.IO) {
                    apiClientProvider()?.analyzeAutoOperation(userRequest, screenInfo)
                }

                if (llmResult.isNullOrBlank() || llmResult == "[]") {
                    onPetMessage("抱歉，我没法理解这个操作该怎么办😅", Emotion.SAD, Action.IDLE)
                    return@launch
                }

                val actions = com.aicompanion.screen.AutoOperator.parseActionsFromLLM(llmResult)
                if (actions.isEmpty()) {
                    onPetMessage("抱歉，我没法理解这个操作该怎么办😅", Emotion.SAD, Action.IDLE)
                    return@launch
                }

                onPetMessage("明白了！正在执行${actions.size}个步骤...", Emotion.TSUNDERE, Action.EAR_TWITCH)

                var successCount = 0
                for ((i, action) in actions.withIndex()) {
                    if (!isAtLeast(androidx.lifecycle.Lifecycle.State.CREATED)) return@launch

                    val stepResult = withContext(Dispatchers.IO) {
                        com.aicompanion.screen.AutoOperator.executeAction(action)
                    }

                    if (stepResult.success) successCount++
                    withContext(Dispatchers.IO) { kotlinx.coroutines.delay(800) }
                }

                val reportStr = buildString {
                    append("完成！${successCount}/${actions.size}个步骤执行成功")
                    if (successCount == actions.size) append("，搞定啦~")
                    else append("，有几个步骤没成功，可能界面不太一样")
                }
                onPetMessage(reportStr, Emotion.HAPPY, Action.IDLE)

            } catch (e: Exception) {
                Log.e(TAG, "AutoOperation failed: ${e.message}")
                onPetMessage("操作失败了：${e.message}", Emotion.SAD, Action.IDLE)
            }
        }
    }
}
