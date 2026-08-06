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
        val sm = settingsManagerProvider() ?: return
        if (!sm.isNagEnabled) return
        proactiveRunnable = Runnable {
            if (!isAtLeast(androidx.lifecycle.Lifecycle.State.CREATED)) return@Runnable
            val engine = proactiveEngineProvider()
            if (engine?.shouldTriggerInteraction(sm.nagFrequency.name.lowercase()) == true) {
                trigger()
            }
            schedule()
        }
        handler.postDelayed(proactiveRunnable!!, 120000L)
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
                    onPetMessage(response.text, response.emotion, response.action)
                    onUpdatePetDisplay(response)
                } else {
                    val fallback = proactiveEngineProvider()?.getIdlePhrase()
                    if (fallback != null && isAtLeast(androidx.lifecycle.Lifecycle.State.CREATED)) {
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
        proactiveRunnable?.let { handler.removeCallbacks(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        proactiveRunnable?.let { handler.removeCallbacks(it) }
    }
}
