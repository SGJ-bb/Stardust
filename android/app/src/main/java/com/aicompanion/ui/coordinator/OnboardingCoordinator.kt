package com.aicompanion.ui.coordinator

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.aicompanion.R
import com.aicompanion.models.Action
import com.aicompanion.models.Emotion
import com.aicompanion.settings.SettingsManager
import com.aicompanion.ui.tutorial.SpotlightTutorial
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class OnboardingCoordinator(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    private val messageScope: CoroutineScope,
    private val settingsManagerProvider: () -> SettingsManager?,
    private val getAppPrefs: () -> android.content.SharedPreferences,
    private val getPersonaInfo: suspend () -> Pair<String, String>,
    private val onPetMessage: (String, Emotion, Action) -> Unit
) : BaseCoordinator(context, lifecycleOwner) {

    fun loadWelcomeMessage() {
        val prefs = getAppPrefs()
        val sm = settingsManagerProvider() ?: return
        if (!sm.onboardingCompleted) {
            showOnboardingDialog()
        } else if (SpotlightTutorial.shouldShow(context)) {
            // 使用新的高亮聚焦式动画引导替代旧 AlertDialog 步进引导
            showSpotlightTutorial()
        } else if (prefs.getBoolean("first_launch", true)) {
            prefs.edit().putBoolean("first_launch", false).apply()
            messageScope.launch {
                val name = getPersonaInfo().first
                onPetMessage("你好呀！我是${name}，你的AI伙伴~", Emotion.HAPPY, Action.TAIL_FLICK)
            }
        }
    }

    private fun showOnboardingDialog() {
        if (!isAtLeast(androidx.lifecycle.Lifecycle.State.CREATED)) return
        val sm = settingsManagerProvider() ?: return
        val activity = context as? android.app.Activity ?: return
        if (activity.isFinishing || activity.isDestroyed) return

        val contentView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * context.resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        val tvGenderLabel = TextView(context).apply {
            text = "你是男生还是女生？"
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
        }
        contentView.addView(tvGenderLabel)

        val genderGroup = RadioGroup(context).apply { orientation = RadioGroup.HORIZONTAL }
        val rbMale = RadioButton(context).apply { text = "男生"; textSize = 15f; setTextColor(ContextCompat.getColor(context, R.color.text_primary)) }
        val rbFemale = RadioButton(context).apply { text = "女生"; textSize = 15f; setTextColor(ContextCompat.getColor(context, R.color.text_primary)) }
        genderGroup.addView(rbMale)
        genderGroup.addView(rbFemale)
        contentView.addView(genderGroup)

        val spacer1 = View(context)
        contentView.addView(spacer1, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (16 * context.resources.displayMetrics.density).toInt()
        ))

        val tvBirthdayLabel = TextView(context).apply {
            text = "你的生日是？"
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
        }
        contentView.addView(tvBirthdayLabel)

        val tvBirthday = TextView(context).apply {
            text = if (sm.userBirthday.isNotBlank()) sm.userBirthday else "点击选择生日"
            textSize = 15f
            setTextColor(0xFF667eea.toInt())
            setPadding(0, (8 * context.resources.displayMetrics.density).toInt(), 0, (8 * context.resources.displayMetrics.density).toInt())
        }
        tvBirthday.setOnClickListener {
            val cal = java.util.Calendar.getInstance()
            val currentText = tvBirthday.text?.toString() ?: ""
            if (currentText.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                val parts = currentText.split("-")
                cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
            }
            android.app.DatePickerDialog(
                context,
                R.style.ThemeOverlay_Companion_DatePicker,
                { _, year, month, day ->
                    val dateStr = String.format("%04d-%02d-%02d", year, month + 1, day)
                    tvBirthday.text = dateStr
                },
                cal.get(java.util.Calendar.YEAR),
                cal.get(java.util.Calendar.MONTH),
                cal.get(java.util.Calendar.DAY_OF_MONTH)
            ).show()
        }
        contentView.addView(tvBirthday)

        val dialog = AlertDialog.Builder(context)
            .setTitle("初次见面，认识一下吧 ✨")
            .setView(contentView)
            .setCancelable(false)
            .setPositiveButton("开始使用") { _, _ ->
                val gender = when (genderGroup.checkedRadioButtonId) {
                    rbMale.id -> "male"
                    rbFemale.id -> "female"
                    else -> ""
                }
                sm.userGender = gender
                val birthday = tvBirthday.text?.toString()?.trim() ?: ""
                if (birthday.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                    sm.userBirthday = birthday
                }
                sm.onboardingCompleted = true
                getAppPrefs().edit().putBoolean("first_launch", false).apply()
                // 完成初次设置后展示高亮聚焦引导
                showSpotlightTutorial()
                messageScope.launch {
                    val name = getPersonaInfo().first
                    onPetMessage("你好呀！我是${name}，很高兴认识你~", Emotion.HAPPY, Action.TAIL_FLICK)
                }
            }
            .create()

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_settings_card)
    }

    /** 使用新的 Spotlight 高亮聚焦式动画引导 */
    private fun showSpotlightTutorial() {
        if (!isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return
        val activity = context as? android.app.Activity ?: return
        if (activity.isFinishing || activity.isDestroyed) return

        try {
            SpotlightTutorial(activity).start(
                onComplete = {
                    SpotlightTutorial.markCompleted(context)
                    // 引导完成后发送欢迎消息
                    messageScope.launch {
                        val name = getPersonaInfo().first
                        onPetMessage("准备好啦！有什么想聊的吗？~", Emotion.HAPPY, Action.TAIL_FLICK)
                    }
                },
                onSkip = {
                    SpotlightTutorial.markCompleted(context)
                }
            )
        } catch (e: Exception) {
            // 如果 Spotlight 引导失败（如找不到目标 View），回退到旧版 AlertDialog 引导
            android.util.Log.w("OnboardingCoordinator", "Spotlight tutorial failed, falling back to dialog", e)
            showLegacyTutorial()
        }
    }

    /** 旧版 AlertDialog 步进引导（作为回退方案保留） */
    @Suppress("unused")
    fun showLegacyTutorial() {
        if (!isAtLeast(androidx.lifecycle.Lifecycle.State.CREATED)) return
        val activity = context as? android.app.Activity ?: return
        if (activity.isFinishing || activity.isDestroyed) return

        val steps = listOf(
            "👋 欢迎使用星尘 AI 桌宠！" to "我是你的专属AI伙伴，可以陪你聊天、记录心情和日记。\n\n点击「下一步」了解基本操作~",
            "💬 聊天与表情" to "在底部输入框发消息和我聊天~\n\n点击 😊 按钮选择你的心情，我会根据你的情绪回复哦！",
            "⚙️ API 配置" to "点击右上角 ⚙ 设置按钮，配置你的 AI API。\n\n选择厂商后会自动填充地址和模型，只需填入 API 密钥即可~",
            "🎮 更多功能" to "• 📅 签到 — 每日打卡领好感\n• 🏆 成就 — 解锁各种有趣成就\n• 📔 日记 — 自动生成每日日记\n• 🍅 专注 — 番茄钟计时",
            "🐾 Live2D 互动" to "• 点击 ⋮ 按钮打开功能面板\n• 在设置中可以调整模型大小\n• 悬浮窗模式让我在桌面陪伴你~"
        )
        var step = 0
        val dialog = AlertDialog.Builder(context)
            .setTitle(steps[step].first)
            .setMessage(steps[step].second)
            .setPositiveButton("下一步") { _, _ -> }
            .setNegativeButton("跳过", null)
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            step++
            if (step < steps.size) {
                dialog.setTitle(steps[step].first)
                dialog.setMessage(steps[step].second)
                if (step == steps.size - 1) dialog.getButton(AlertDialog.BUTTON_POSITIVE).text = "完成"
            } else {
                dialog.dismiss()
            }
        }
    }
}
