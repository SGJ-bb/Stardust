package com.aicompanion.ui.tutorial

import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import com.aicompanion.R

/**
 * 聚光灯引导编排器 (Spotlight Tutorial)
 *
 * 管理整个新手引导流程的生命周期，包括：
 * - 按顺序展示 6 步引导，每步聚焦一个目标 View
 * - 自动处理目标 View 不可见时的跳过逻辑
 * - 管理步骤间镂空区域的平滑过渡动画（morphing）
 * - 通过 SharedPreferences 记录引导完成状态和版本号
 *
 * 使用方式：
 * ```kotlin
 * // 在 Activity 中启动引导
 * SpotlightTutorial(this).start(
 *     onComplete = { /* 引导全部完成 */ },
 *     onSkip = { /* 用户跳过 */ }
 * )
 *
 * // 检查是否需要展示
 * if (SpotlightTutorial.shouldShow(this)) {
 *     SpotlightTutorial(this).start()
 * }
 * ```
 *
 * @property activity 当前宿主 Activity，用于查找 View 和添加遮罩层
 */
class SpotlightTutorial(private val activity: Activity) {

    companion object {
        private const val TAG = "SpotlightTutorial"

        /** SharedPreferences 文件名 */
        private const val PREFS_NAME = "app_prefs"

        /** 引导完成标记的 key */
        const val PREF_TUTORIAL_COMPLETED = "spotlight_tutorial_completed"

        /** 引导版本号：变更此值可强制重新展示引导 */
        const val PREF_TUTORIAL_VERSION = 2

        /**
         * 检查是否需要展示新手引导
         *
         * 以下任一条件满足时返回 true：
         * 1. 用户从未完成过引导
         * 2. 引导版本号已升级（新增了引导步骤）
         *
         * @param context 上下文
         * @return 是否需要展示引导
         */
        fun shouldShow(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val completed = prefs.getBoolean(PREF_TUTORIAL_COMPLETED, false)
            val version = prefs.getInt("tutorial_version", 0)
            val shouldShow = !completed || version < PREF_TUTORIAL_VERSION

            if (shouldShow) {
                Log.d(TAG, "需要展示引导: completed=$completed, storedVersion=$version, currentVersion=$PREF_TUTORIAL_VERSION")
            }
            return shouldShow
        }

        /**
         * 标记引导已完成
         * 将完成状态和当前版本号写入 SharedPreferences
         *
         * @param context 上下文
         */
        fun markCompleted(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean(PREF_TUTORIAL_COMPLETED, true)
                .putInt("tutorial_version", PREF_TUTORIAL_VERSION)
                .apply()
            Log.d(TAG, "引导已标记为完成, version=$PREF_TUTORIAL_VERSION")
        }
    }

    // ======================== 引导步骤定义 ========================

    /**
     * 6 步引导配置
     *
     * 每步聚焦应用中的一个关键交互元素，
     * 配合对应的手势动画引导用户操作。
     */
    private val steps = listOf(
        TutorialStep(
            targetViewId = R.id.layout_input,
            shape = TargetShape.ROUNDED_RECT,
            title = "开始对话",
            description = "在这里输入你想说的话，AI 会回复你~",
            hintAnimation = HintAnimation.TYPE,
            hintText = "试试输入 \"你好\"",
            paddingDp = 20f
        ),
        TutorialStep(
            targetViewId = R.id.btn_sticker_chat,
            shape = TargetShape.CIRCLE,
            title = "发送表情",
            description = "点击这里打开表情包，让对话更有趣！",
            hintAnimation = HintAnimation.TAP,
            hintText = "点击选择心情",
            paddingDp = 8f
        ),
        TutorialStep(
            targetViewId = R.id.scroll_predictions,
            shape = TargetShape.RECTANGLE,
            title = "快捷功能",
            description = "日记、日历、专注...常用功能都在这里~",
            hintAnimation = HintAnimation.SWIPE_LEFT,
            hintText = "左右滑动浏览",
            paddingDp = 8f
        ),
        TutorialStep(
            targetViewId = R.id.iv_ai_avatar_small_card,
            shape = TargetShape.CIRCLE,
            title = "你的 AI 伙伴",
            description = "点击头像查看档案和详细设置",
            hintAnimation = HintAnimation.TAP,
            hintText = "点击头像",
            paddingDp = 6f
        ),
        TutorialStep(
            targetViewId = R.id.btn_settings,
            shape = TargetShape.CIRCLE,
            title = "个性化设置",
            description = "换肤、换壁纸、调整参数...都在这里",
            hintAnimation = HintAnimation.TAP,
            hintText = "点击设置",
            paddingDp = 6f
        ),
        TutorialStep(
            targetViewId = R.id.fab_add_persona,
            shape = TargetShape.CIRCLE,
            title = "创建角色",
            description = "可以创建多个不同性格的 AI 伙伴哦~",
            hintAnimation = HintAnimation.PULSE,
            hintText = "点击新建",
            paddingDp = 10f
        )
    )

    // ======================== 运行时状态 ========================

    /** 遮罩层实例 */
    private var overlay: SpotlightOverlay? = null

    /** 当前步骤索引 */
    private var currentStepIndex = 0

    /** 引导是否正在运行 */
    @Volatile
    private var isActive = false

    // ======================== 公开 API：启动引导 ========================

    /**
     * 开始展示新手引导
     *
     * 流程说明：
     * 1. 防重复检查：如果已在运行则直接返回
     * 2. 延迟 500ms 启动：等待 Activity 布局完全稳定
     * 3. 创建全屏遮罩层并添加到 decorView
     * 4. 从第 0 步开始展示
     *
     * @param onComplete 全部步骤完成后的回调
     * @param onSkip 用户点击"跳过"按钮的回调
     */
    fun start(onComplete: () -> Unit = {}, onSkip: () -> Unit = {}) {
        if (isActive) {
            Log.w(TAG, "引导已在运行中，忽略重复调用")
            return
        }

        isActive = true
        currentStepIndex = 0
        Log.i(TAG, "开始新手引导，共 ${steps.size} 步")

        // 延迟等待布局完成，确保所有 View 已测量完毕
        activity.window.decorView.postDelayed({
            // 二次检查：Activity 可能已在延迟期间被销毁
            if (activity.isFinishing || activity.isDestroyed) {
                Log.w(TAG, "Activity 已销毁，取消引导启动")
                isActive = false
                return@postDelayed
            }
            try {
                createOverlay()
                showStep(0, onComplete, onSkip)
            } catch (e: Exception) {
                Log.e(TAG, "启动引导失败", e)
                isActive = false
                Toast.makeText(activity, "引导初始化失败", Toast.LENGTH_SHORT).show()
            }
        }, 500L)
    }

    // ======================== 内部方法：创建遮罩层 ========================

    /**
     * 创建并挂载 [SpotlightOverlay] 到 decorView 上方
     *
     * 将遮罩层作为覆盖层添加到 Activity 的窗口装饰视图上，
     * 使其能够覆盖所有内容（包括状态栏和导航栏）。
     */
    private fun createOverlay() {
        overlay = SpotlightOverlay(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            // 添加到 decorView 的 content 区域上方
            val decorView = activity.window.decorView as? ViewGroup
            if (decorView != null) {
                decorView.addView(this)
                Log.d(TAG, "遮罩层已添加到 decorView")
            } else {
                Log.e(TAG, "无法获取 decorView，遮罩层添加失败")
            }
        }
    }

    // ======================== 内部方法：展示单步引导 ========================

    /**
     * 展示指定索引的引导步骤
     *
     * 执行流程：
     * 1. 边界检查：超出范围则结束引导
     * 2. 查找目标 View：通过 [Activity.findViewById]
     * 3. 可见性检查：不可见则自动跳到下一步
     * 4. 获取屏幕坐标并应用到遮罩层
     * 5. 首步直接设置，后续步使用 morph 过渡动画
     * 6. 绑定按钮回调
     * 7. 更新步骤指示器
     *
     * @param index 步骤索引（从 0 开始）
     * @param onComplete 全部完成回调
     * @param onSkip 跳过回调
     */
    private fun showStep(index: Int, onComplete: () -> Unit, onSkip: () -> Unit) {
        // 边界检查
        if (index >= steps.size || overlay == null) {
            finish(onComplete)
            return
        }

        currentStepIndex = index
        val step = steps[index]

        Log.d(TAG, "展示第 ${index + 1}/${steps.size} 步: ${step.title}, 目标ID=${step.targetViewId}")

        // ---- 第1步：查找目标 View ----
        val targetView = activity.findViewById<View>(step.targetViewId)

        if (targetView == null) {
            Log.w(TAG, "第 ${index + 1} 步目标 View 不存在(ID=${step.targetViewId})，自动跳过")
            showStep(index + 1, onComplete, onSkip)
            return
        }

        if (targetView.visibility != View.VISIBLE) {
            Log.w(TAG, "第 ${index + 1} 步目标 View 不可见，自动跳过")
            showStep(index + 1, onComplete, onSkip)
            return
        }

        // ---- 第2步：获取目标 View 的屏幕坐标 ----
        val location = IntArray(2)
        targetView.getLocationOnScreen(location)
        val targetRect = Rect(
            location[0],
            location[1],
            location[0] + targetView.width,
            location[1] + targetView.height
        )

        // ---- 第3步：根据是否为首步决定展示方式 ----
        val isLast = index >= steps.size - 1

        if (index == 0) {
            // 首步：直接设置镂空区域、气泡和手势动画
            overlay?.setTarget(targetRect, step.shape, step.paddingDp)
            overlay?.showTooltip(step.title, step.description, step.hintText)
            overlay?.startHintAnimation(
                step.hintAnimation,
                RectF(
                    targetRect.left.toFloat(),
                    targetRect.top.toFloat(),
                    targetRect.right.toFloat(),
                    targetRect.bottom.toFloat()
                )
            )
        } else {
            // 后续步：使用 morph 过渡动画平滑移动镂空区域
            overlay?.transitionTo(targetRect, step.shape, step.paddingDp)

            // 延迟更新气泡和手势动画（等 morph 动画执行约一半时）
            overlay?.postDelayed({
                overlay?.showTooltip(step.title, step.description, step.hintText)
                overlay?.startHintAnimation(
                    step.hintAnimation,
                    RectF(
                        targetRect.left.toFloat(),
                        targetRect.top.toFloat(),
                        targetRect.right.toFloat(),
                        targetRect.bottom.toFloat()
                    )
                )
            }, 150L) // MORPH_DURATION(300ms) 的一半
        }

        // ---- 第4步：设置按钮回调 ----
        overlay?.onNextClick = {
            if (isLast) {
                finish(onComplete)
            } else {
                showStep(index + 1, onComplete, onSkip)
            }
        }

        overlay?.onSkipClick = {
            Log.i(TAG, "用户在第 ${index + 1} 步点击了跳过")
            finish(onSkip)
        }

        // ---- 第5步：更新 UI 状态 ----
        overlay?.updateStepIndicators(steps.size, index)
        overlay?.updateNextButton(isLast)
    }

    // ======================== 内部方法：结束引导 ========================

    /**
     * 结束引导流程
     *
     * 执行以下清理工作：
     * 1. 重置运行状态标志
     * 2. 以淡出动画移除遮罩层
     * 3. 从父容器移除遮罩 View
     * 4. 释放引用
     * 5. 触发完成回调
     *
     * @param callback 结束后的回调（可能是 onComplete 或 onSkip）
     */
    private fun finish(callback: () -> Unit) {
        if (!isActive && overlay == null) {
            // 已经结束过了，避免重复处理
            callback()
            return
        }

        isActive = false
        Log.i(TAG, "引导结束，共展示了 ${currentStepIndex + 1}/${steps.size} 步")

        val overlayRef = overlay
        overlayRef?.dismiss {
            // 动画完成后从父容器移除
            try {
                (activity.window.decorView as? ViewGroup)?.removeView(overlayRef)
            } catch (e: Exception) {
                Log.w(TAG, "从父容器移除遮罩层时异常（可能已被移除）", e)
            }
            overlay = null
            callback()
        }
    }

    // ======================== 外部控制 API ========================

    /**
     * 强制停止引导（不触发任何回调）
     * 用于 Activity 被销毁等异常场景下的紧急清理
     */
    fun forceStop() {
        if (!isActive) return

        isActive = false
        Log.w(TAG, "引导被强制停止")

        try {
            overlay?.stopHintAnimation()
            (activity.window.decorView as? ViewGroup)?.removeView(overlay)
        } catch (e: Exception) {
            Log.w(TAG, "强制停止时清理异常", e)
        }
        overlay = null
    }

    /**
     * 查询引导是否正在运行
     */
    fun isRunning(): Boolean = isActive

    /**
     * 获取当前步骤索引（从 0 开始），未开始时返回 -1
     */
    fun getCurrentStep(): Int = if (isActive) currentStepIndex else -1

    /**
     * 获取总步骤数
     */
    fun getTotalSteps(): Int = steps.size
}
