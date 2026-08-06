package com.aicompanion.ui.coordinator

import android.content.Context
import android.widget.Toast
import com.aicompanion.affection.AffectionManager
import com.aicompanion.diary.DiaryManager
import com.aicompanion.memory.ContextManager
import com.aicompanion.models.Action
import com.aicompanion.models.Emotion
import com.aicompanion.milestone.MilestoneManager
import com.aicompanion.network.ApiClient
import com.aicompanion.settings.DiaryTriggerMode
import com.aicompanion.settings.SettingsManager
import com.aicompanion.ui.ChatMessage
import com.aicompanion.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DiaryCoordinator(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    private val memoryScope: CoroutineScope,
    private val settingsManagerProvider: () -> SettingsManager?,
    private val apiClientProvider: () -> ApiClient?,
    private val affectionManagerProvider: () -> AffectionManager?,
    private val contextManagerProvider: () -> ContextManager?,
    private val milestoneManagerProvider: () -> MilestoneManager?,
    private val getPersonaInfo: suspend () -> Pair<String, String>,
    private val getMessages: () -> List<ChatMessage>,
    private val onPetMessage: (String, Emotion, Action) -> Unit,
    private val onSetLoading: (Boolean) -> Unit
) : BaseCoordinator(context, lifecycleOwner) {

    companion object {
        private const val TAG = "DiaryCoordinator"
    }

    private var diaryRunnable: Runnable? = null
    private var diaryWriting = false

    fun scheduleDiaryTimer(personaId: String) {
        val sm = settingsManagerProvider() ?: return
        val mode = sm.diaryTriggerMode
        if (mode == DiaryTriggerMode.MANUAL) return
        if (mode == DiaryTriggerMode.MSG_50) return

        val delayMs = when (mode) {
            DiaryTriggerMode.HOURLY -> 60 * 60 * 1000L
            DiaryTriggerMode.EVERY_2H -> 2 * 60 * 60 * 1000L
            else -> {
                val cal = java.util.Calendar.getInstance()
                cal.set(java.util.Calendar.HOUR_OF_DAY, 22)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                cal.timeInMillis - System.currentTimeMillis()
            }
        }

        diaryRunnable = Runnable {
            if (!isAtLeast(androidx.lifecycle.Lifecycle.State.CREATED)) return@Runnable
            memoryScope.launch {
                try {
                    val dm = DiaryManager(context, personaId)
                    val am = affectionManagerProvider() ?: return@launch
                    val todayDiary = dm.getDiaryByDate(
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    )
                    if (todayDiary == null && getMessages().count { it.isUser } >= 5) {
                        withContext(Dispatchers.Main) {
                            autoTriggerDiary(dm, am, personaId)
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "diaryRunnable: ${e.message}")
                }
                withContext(Dispatchers.Main) {
                    if (isAtLeast(androidx.lifecycle.Lifecycle.State.CREATED)) scheduleDiaryTimer(personaId)
                }
            }
        }
        handler.postDelayed(diaryRunnable!!, delayMs)
    }

    fun checkTurnsDiaryTrigger(personaId: String) {
        val sm = settingsManagerProvider() ?: return
        val mode = sm.diaryTriggerMode
        if (mode != DiaryTriggerMode.MSG_50) return

        val ctxMgr = contextManagerProvider() ?: return
        val totalTurns = ctxMgr.sessionManager.currentTurnCount
        if (totalTurns < 50) return

        val prefs = context.getSharedPreferences("diary_trigger_$personaId", Context.MODE_PRIVATE)
        val lastTriggeredTurns = prefs.getInt("last_diary_turns_trigger", 0)
        if (totalTurns - lastTriggeredTurns < 50) return

        val dm = DiaryManager(context, personaId)
        val am = affectionManagerProvider() ?: return
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val todayDiary = dm.getDiaryByDate(today)
        if (todayDiary != null) return

        if (getMessages().count { it.isUser } < 5) return

        prefs.edit().putInt("last_diary_turns_trigger", totalTurns).apply()
        autoTriggerDiary(dm, am, personaId)
    }

    fun triggerManualDiary(personaId: String) {
        val dm = DiaryManager(context, personaId)
        val am = affectionManagerProvider() ?: return
        onSetLoading(true)
        onPetMessage("正在为你写今天的日记...", Emotion.NEUTRAL, Action.IDLE)
        autoTriggerDiary(dm, am, personaId)
        memoryScope.launch {
            kotlinx.coroutines.delay(3000)
            withContext(Dispatchers.Main) { onSetLoading(false) }
        }
    }

    private fun autoTriggerDiary(dm: DiaryManager, am: AffectionManager, personaId: String) {
        val client = apiClientProvider() ?: return
        val sm = settingsManagerProvider() ?: return
        if (sm.chatApiUrl.isBlank()) return
        if (diaryWriting) return

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val existingDiary = dm.getDiaryByDate(today)
        if (existingDiary != null && dm.getTodayDiaryAppendCount() >= 3) return
        if (!dm.canUpdateDiary()) return

        diaryWriting = true
        dm.markDiaryUpdated()

        memoryScope.launch {
            try {
                val persona = getPersonaInfo()
                val chatTexts = getMessages().map { it.text }
                if (chatTexts.isEmpty()) return@launch

                val combined = chatTexts.joinToString(" | ")
                val poolBlock = contextManagerProvider()?.memoryPool?.getPoolBlock() ?: ""
                val diaryContext = if (poolBlock.isNotBlank()) {
                    "今天记忆池内容:\n$poolBlock\n\n聊天记录:\n$combined"
                } else {
                    combined
                }

                val localMood = analyzeLocalMood(diaryContext)

                val llmContent = withContext(Dispatchers.IO) {
                    client.generateDiaryContent(
                        chatTexts, persona.first, persona.second, localMood,
                        when (localMood) {
                            "happy" -> "🥰"; "sad" -> "😢"; "excited" -> "🤩"
                            "calm" -> "😌"; "sentimental" -> "🌙"; else -> "😊"
                        },
                        am.affectionLevel,
                        isUpdate = existingDiary != null
                    )
                }

                if (llmContent != null && llmContent.isNotBlank()) {
                    val currentExisting = dm.getDiaryByDate(today)
                    if (currentExisting != null) {
                        if (dm.getTodayDiaryAppendCount() < 3) {
                            dm.appendLlmDiaryUpdate(llmContent, chatTexts, am.affectionLevel)
                        }
                    } else {
                        dm.saveLlmDiary(llmContent, chatTexts, am.affectionLevel)
                        milestoneManagerProvider()?.recordMilestone("first_diary", "第一篇日记", "星尘写的第一篇日记", "diary")
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "📔 日记已更新", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    dm.updateOrGenerateDailyDiary(chatTexts, am.affectionLevel)
                    milestoneManagerProvider()?.recordMilestone("first_diary", "第一篇日记", "星尘写的第一篇日记", "diary")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "📔 日记已生成（本地模式）", Toast.LENGTH_SHORT).show()
                    }
                }

                val sm2 = settingsManagerProvider()
                if (sm2 != null && sm2.userPersonalityDef.isBlank()) {
                    try {
                        val summaryResult = client.summarizeUserPersonality(
                            personaName = persona.first,
                            recentChatSummary = chatTexts.takeLast(30).joinToString("\n"),
                            currentSummary = sm2.getAiSummarizedPersonality(personaId),
                            affectionLevel = am.affectionLevel
                        )
                        if (!summaryResult.isNullOrBlank()) {
                            sm2.setAiSummarizedPersonality(personaId, summaryResult)
                            com.aicompanion.prompt.PromptBuilder.invalidateCache()
                        }
                    } catch (e: Exception) { AppLogger.e(TAG, "autoTriggerDiary: ${e.message}") }
                }
            } catch (e: Exception) {
                android.util.Log.d(TAG, "LLM diary failed, falling back: ${e.message}")
                val chatTexts = getMessages().map { it.text }
                dm.updateOrGenerateDailyDiary(chatTexts, am.affectionLevel)
                milestoneManagerProvider()?.recordMilestone("first_diary", "第一篇日记", "星尘写的第一篇日记", "diary")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "📔 日记已生成（本地模式）", Toast.LENGTH_SHORT).show()
                }
            } finally {
                diaryWriting = false
            }
        }
    }

    private fun analyzeLocalMood(text: String): String {
        val lower = text.lowercase()
        val happyWords = listOf("哈哈", "开心", "喜欢", "太好了", "棒", "nice", "love", "good", "可爱")
        val sadWords = listOf("难过", "伤心", "哭", "不好", "烦", "生气", "sad", "bad", "讨厌")
        val excitedWords = listOf("厉害", "冲", "加油", "go", "yes", "完美", "了不起", "冲啊")
        val calmWords = listOf("安静", "舒服", "平静", "放松", "休息", "calm", "peace", "冥想")
        val sentimentalWords = listOf("回忆", "想念", "记得", "曾经", "星空", "月光", "诗", "夜晚")
        val scores = mapOf(
            "happy" to happyWords.count { lower.contains(it) },
            "sad" to sadWords.count { lower.contains(it) },
            "excited" to excitedWords.count { lower.contains(it) },
            "calm" to calmWords.count { lower.contains(it) },
            "sentimental" to sentimentalWords.count { lower.contains(it) }
        )
        val max = scores.maxByOrNull { it.value }
        return if (max != null && max.value > 0) max.key else "normal"
    }

    override fun onPause() {
        diaryRunnable?.let { handler.removeCallbacks(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        diaryRunnable?.let { handler.removeCallbacks(it) }
    }
}
