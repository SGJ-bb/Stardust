package com.aicompanion.ui.coordinator

import android.content.Context
import android.util.Log
import com.aicompanion.interaction.ProactiveInteractionEngine
import com.aicompanion.memory.ContextManager
import com.aicompanion.models.Action
import com.aicompanion.models.Emotion
import com.aicompanion.network.ApiClient
import com.aicompanion.settings.SettingsManager
import com.aicompanion.ui.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProactiveChatCoordinator(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    private val messageScope: CoroutineScope,
    private val settingsManagerProvider: () -> SettingsManager?,
    private val apiClientProvider: () -> ApiClient?,
    private val contextManagerProvider: () -> ContextManager?,
    private val proactiveEngineProvider: () -> ProactiveInteractionEngine?,
    private val getPersonaInfo: suspend (String) -> Pair<String, String>,
    private val getMessages: () -> List<ChatMessage>,
    private val isInForeground: () -> Boolean,
    private val onPetMessage: (String, Emotion, Action) -> Unit,
    private val onUpdatePetDisplay: (com.aicompanion.models.ChatResponse) -> Unit
) : BaseCoordinator(context, lifecycleOwner) {

    companion object {
        private const val TAG = "ProactiveChatCoordinator"
    }

    private var proactiveRunnable: Runnable? = null

    fun schedule() {
        // 幂等：重复调用不会叠加多个定时器
        proactiveRunnable?.let { handler.removeCallbacks(it) }
        proactiveRunnable = Runnable {
            val sm = settingsManagerProvider()
            // 开关判断放在 tick 内部：启动时是关的、后来用户打开了，也能生效
            if (sm != null && sm.isNagEnabled && isAtLeast(androidx.lifecycle.Lifecycle.State.CREATED)) {
                val engine = proactiveEngineProvider()
                // 注意用 getNagFrequency()（返回 frequent/normal/infrequent）。
                // 原来传的是枚举名 low/medium/high，全都落到 else 分支 → 频率设置形同虚设。
                if (engine?.shouldTriggerInteraction(sm.getNagFrequency()) == true) {
                    trigger()
                }
            }
            schedule()
        }
        handler.postDelayed(proactiveRunnable!!, 120000L)
    }

    override fun onResume() {
        // 从后台回来要重新排：否则上一轮 onPause 移除后永远不再触发
        schedule()
    }

    private fun trigger() {
        if (isInForeground()) return
        val client = apiClientProvider() ?: return
        val sm = settingsManagerProvider() ?: return

        val chatHistory = getMessages().takeLast(sm.contextTurns).map { msg ->
            Pair(msg.isUser, msg.text)
        }

        messageScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    if (sm.chatApiUrl.isNotBlank()) {
                        val memCtx = contextManagerProvider()?.memoryPool?.getPoolBlock()
                        val recentText = chatHistory.takeLast(3).map { it.second }.joinToString(" ")
                        val persona = getPersonaInfo(recentText)
                        client.generateNagContent(persona.first, persona.second, memoryContext = memCtx, chatHistory = chatHistory)
                    } else null
                }
                if (response != null && response.text.isNotBlank() && response.errorMessage == null) {
                    proactiveEngineProvider()?.markNagSent()
                    onPetMessage(response.text, response.emotion, response.action)
                    onUpdatePetDisplay(response)
                } else {
                    val fallback = proactiveEngineProvider()?.getIdlePhrase()
                    if (fallback != null && isAtLeast(androidx.lifecycle.Lifecycle.State.CREATED)) {
                        proactiveEngineProvider()?.markNagSent()
                        onPetMessage(fallback.first, fallback.second, fallback.third)
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "LLM proactive chat failed: ${e.message}")
                val fallback = proactiveEngineProvider()?.getIdlePhrase()
                if (fallback != null && isAtLeast(androidx.lifecycle.Lifecycle.State.CREATED)) {
                    onPetMessage(fallback.first, fallback.second, fallback.third)
                }
            }
        }
    }

    override fun onPause() {
        // 故意不取消：用户切到别的 App 时正是"该主动说句话"的时候。
        // 原来的写法在 onPause 移除定时器，而 trigger() 又要求"非前台"，
        // 两个条件互斥 —— 结果是永远不发。
    }

    override fun onDestroy() {
        super.onDestroy()
        proactiveRunnable?.let { handler.removeCallbacks(it) }
    }
}
