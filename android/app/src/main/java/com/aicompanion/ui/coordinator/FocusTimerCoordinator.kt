package com.aicompanion.ui.coordinator

import android.content.SharedPreferences
import com.aicompanion.affection.AffectionManager
import com.aicompanion.gamify.AchievementManager
import com.aicompanion.models.Action
import com.aicompanion.models.Achievement
import com.aicompanion.models.Emotion

class FocusTimerCoordinator(
    context: android.content.Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    private val appPrefs: SharedPreferences,
    private val affectionManagerProvider: () -> AffectionManager?,
    private val achievementManagerProvider: () -> AchievementManager?,
    private val onPetMessage: (String, Emotion, Action) -> Unit,
    private val onUpdateAffectionDisplay: () -> Unit,
    private val onCheckAiMomentTrigger: () -> Unit,
    private val onShowAchievementUnlock: (Achievement) -> Unit
) : BaseCoordinator(context, lifecycleOwner) {

    var isActive: Boolean = false
        private set

    private var focusSecondsLeft = 0
    private var focusEndTime = 0L
    private var focusRunnable: Runnable? = null

    fun start() {
        isActive = true
        focusSecondsLeft = 25 * 60
        focusEndTime = System.currentTimeMillis() + focusSecondsLeft * 1000L
        onPetMessage("🍅 专注模式启动！25分钟，我会陪着你~", Emotion.HAPPY, Action.STRETCH)
        focusRunnable = object : Runnable {
            override fun run() {
                if (!isActive) return
                focusSecondsLeft = ((focusEndTime - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
                if (focusSecondsLeft <= 0) complete()
                else handler.postDelayed(this, 1000)
            }
        }
        focusRunnable?.let { handler.postDelayed(it, 1000) }
    }

    private fun complete() {
        isActive = false
        val pomodoros = appPrefs.getInt("total_pomodoros", 0) + 1
        appPrefs.edit().putInt("total_pomodoros", pomodoros).apply()
        val ach = achievementManagerProvider()?.updateProgress("pomodoro", pomodoros)
        if (ach != null) onShowAchievementUnlock(ach)
        affectionManagerProvider()?.addAffection(3, "专注完成")
        onUpdateAffectionDisplay()
        onCheckAiMomentTrigger()
        onPetMessage("🎉 专注完成！你真棒！好感+3", Emotion.HAPPY, Action.TAIL_FLICK)
    }

    fun cancel() {
        isActive = false
        focusRunnable?.let { handler.removeCallbacks(it) }
        onPetMessage("专注取消了，没关系，下次继续~", Emotion.NEUTRAL, Action.IDLE)
    }

    override fun onPause() {
        focusRunnable?.let { handler.removeCallbacks(it) }
    }

    override fun onResume() {
        if (isActive && focusSecondsLeft > 0) {
            focusRunnable = object : Runnable {
                override fun run() {
                    if (!isActive) return
                    focusSecondsLeft = ((focusEndTime - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
                    if (focusSecondsLeft <= 0) complete()
                    else handler.postDelayed(this, 1000)
                }
            }
            focusRunnable?.let { handler.postDelayed(it, 1000) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        focusRunnable?.let { handler.removeCallbacks(it) }
    }
}
