/** AI 操作编排器: 管理操作流程/进度跟踪/异常检测/陪伴式确认 */
package com.aicompanion.screen

import android.content.Context
import android.util.Log
import com.aicompanion.settings.SettingsManager

/** AI 操作编排器：管理操作流程、进度跟踪、异常检测、陪伴式确认 */
class AIOperationOrchestrator(private val context: Context) {

    companion object {
        private const val TAG = "AIOperator"
    }

    private val settings = SettingsManager(context)

    /** 操作进度 */
    data class OperationProgress(
        val currentRound: Int,
        val maxRounds: Int,
        val completedActions: Int,
        val failedActions: Int,
        val lastError: String
    )

    /** 操作结果 */
    data class OperationResult(
        val success: Boolean,
        val message: String,
        val progress: OperationProgress,
        val abnormalType: AbnormalDetector.AbnormalType = AbnormalDetector.AbnormalType.NONE,
        val needUserConfirm: Boolean = false,
        val confirmPrompt: String = ""
    )

    /**
     * 执行 LLM 返回的动作序列
     * - 自动检测异常页面并暂停
     * - 动态轮次上限
     * - 进度跟踪
     * - 失败反馈
     */
    suspend fun executeActions(
        actions: List<AutoAction>,
        onProgress: (OperationProgress) -> Unit = {}
    ): OperationResult {
        val maxRounds = settings.aiOperationMaxRounds
        var completed = 0
        var failed = 0
        var lastError = ""

        for ((index, action) in actions.withIndex()) {
            // 异常检测
            if (settings.abnormalDetectionEnabled) {
                val service = ScreenRecognitionService.getInstance()
                val root = service?.rootInActiveWindow
                val abnormal = AbnormalDetector.detect(root)
                if (abnormal != AbnormalDetector.AbnormalType.NONE) {
                    val desc = AbnormalDetector.getDescription(abnormal)
                    Log.w(TAG, desc)
                    return OperationResult(
                        success = false,
                        message = desc,
                        progress = OperationProgress(index, maxRounds, completed, failed, lastError),
                        abnormalType = abnormal
                    )
                }
            }

            // 执行动作
            val result = AutoOperator.executeAction(action)
            if (result.success) {
                completed++
            } else {
                failed++
                lastError = result.errorMsg
                Log.w(TAG, "动作失败: ${action.type} - ${result.errorMsg}")
            }

            // 进度回调
            onProgress(OperationProgress(index + 1, maxRounds, completed, failed, lastError))
        }

        return OperationResult(
            success = failed == 0,
            message = if (failed > 0) "完成 $completed 个动作，$failed 个失败" else "全部完成",
            progress = OperationProgress(actions.size, maxRounds, completed, failed, lastError)
        )
    }

    /**
     * 陪伴式能力感知：判断用户意图是否需要 AI 操作提议
     * 返回 null 表示不需要操作，返回非空字符串表示提议内容
     */
    fun checkCapabilityOffer(userMessage: String): String? {
        val msg = userMessage.lowercase()
        return when {
            msg.contains("饿了") || msg.contains("外卖") || msg.contains("点餐") ->
                "需要我帮你打开美团下个单吗？"
            msg.contains("打车") || msg.contains("出行") ->
                "需要我帮你打开打车软件叫个车吗？"
            msg.contains("天气") ->
                "需要我帮你查一下天气吗？"
            msg.contains("音乐") || msg.contains("听歌") ->
                "需要我帮你打开音乐软件放首歌吗？"
            msg.contains("消息") || msg.contains("回复") ->
                "需要我帮你打开微信看看消息吗？"
            else -> null
        }
    }

    /**
     * 检查是否需要用户确认（根据操作模式）
     */
    fun needUserConfirm(): Boolean {
        return settings.aiOperationMode == "confirm"
    }
}
