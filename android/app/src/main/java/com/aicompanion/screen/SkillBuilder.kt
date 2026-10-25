package com.aicompanion.screen

import com.aicompanion.network.ApiClient
import com.aicompanion.util.AppLogger
import org.json.JSONArray
import org.json.JSONObject

/**
 * UI 知识构建器：操作成功后调用 LLM 归纳"UI 结构知识 + 任务 workflow"，存为 AppSkillManager.Skill。
 *
 * 与旧方案（raw actions 录制）的区别：
 * 旧方案：直接复制执行过的 click/input 序列 —— 屏幕变化即失效。
 * 新方案：让 LLM 抽象出"页面布局 + 任务步骤"（自然语言），下次作为先验知识喂给 LLM 重新规划。
 *
 * 流程：执行成功的 actions + 用户请求 → LLM 归纳 appName/uiMap/knownTask → 存/合并到 AppSkillManager
 * 失败时静默跳过（不影响主流程）。
 */
object SkillBuilder {

    private const val TAG = "SkillBuilder"

    /**
     * 归纳并保存 UI 知识。
     * @param apiClient API 客户端
     * @param context 用于初始化 AppSkillManager
     * @param userRequest 用户原始指令
     * @param actionsJson 执行成功的 actions JSON 字符串（JSONArray 格式）
     */
    suspend fun buildAndSave(
        apiClient: ApiClient,
        context: android.content.Context,
        userRequest: String,
        actionsJson: String
    ) {
        try {
            // 简单任务不归纳（如单纯的 home/back/单击）
            val arr = JSONArray(actionsJson)
            if (arr.length() < 3) {
                AppLogger.d(TAG, "动作太少(${arr.length()})，跳过归纳")
                return
            }

            AppLogger.d(TAG, "开始归纳 UI 知识: $userRequest")
            val result = apiClient.summarizeSkill(userRequest, actionsJson)
            if (result.isNullOrBlank()) {
                AppLogger.d(TAG, "LLM 归纳返回空，跳过保存")
                return
            }

            // 解析 LLM 返回的 UI 知识 JSON
            val skillJson = JSONObject(result)
            val appName = skillJson.optString("appName", "")
            val uiMap = skillJson.optString("uiMap", "")
            if (appName.isBlank() || uiMap.isBlank()) {
                AppLogger.d(TAG, "归纳的 appName/uiMap 为空，跳过保存")
                return
            }

            // 提取任务 workflow
            val taskName = skillJson.optString("taskName", "")
            val workflow = skillJson.optString("workflow", "")
            if (taskName.isBlank() || workflow.isBlank()) {
                AppLogger.d(TAG, "归纳的 taskName/workflow 为空，跳过保存")
                return
            }

            val keywords = mutableListOf<String>()
            skillJson.optJSONArray("triggerKeywords")?.let { kArr ->
                (0 until kArr.length()).forEach { i -> keywords.add(kArr.getString(i)) }
            }
            // 关键词兜底：若 LLM 没给，至少用 taskName
            if (keywords.isEmpty()) keywords.add(taskName)

            val appPackage = skillJson.optString("appPackage", "")

            val knownTask = AppSkillManager.KnownTask(
                taskName = taskName,
                triggerKeywords = keywords,
                workflow = workflow,
                lastSuccessAt = System.currentTimeMillis()
            )

            // 生成 skill ID（按 appName 命名，同 APP 复用）
            val skillId = "uik_${appName.replace(" ", "_")}"
                .replace(Regex("[^a-zA-Z0-9_\\u4e00-\\u9fa5]"), "")

            val skill = AppSkillManager.Skill(
                id = skillId,
                appName = appName,
                appPackage = appPackage,
                uiMap = uiMap,
                knownTasks = listOf(knownTask)
            )

            val manager = AppSkillManager(context)
            val saved = manager.saveSkill(skill)
            AppLogger.d(TAG, "UI 知识归纳${if (saved) "成功" else "失败"}: $appName, 任务=$taskName")
        } catch (e: Exception) {
            AppLogger.e(TAG, "buildAndSave 异常: ${e.message}")
        }
    }
}
