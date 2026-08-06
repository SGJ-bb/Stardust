package com.aicompanion.ui.tutorial

/**
 * 目标 View 的镂空形状枚举
 * 决定 Spotlight 遮罩层中高亮区域的几何形态
 */
enum class TargetShape {
    /** 圆形：适用于图标按钮、头像等 */
    CIRCLE,
    /** 矩形：适用于工具栏、列表项等 */
    RECTANGLE,
    /** 圆角矩形：适用于输入框、卡片等 */
    ROUNDED_RECT
}

/**
 * 手势提示动画类型枚举
 * 定义在目标区域附近播放的循环动画效果
 */
enum class HintAnimation {
    /** 无动画 */
    NONE,
    /** 点击提示：缩放圆环 + 放射短线 */
    TAP,
    /** 左滑提示：从右到左的箭头路径 */
    SWIPE_LEFT,
    /** 右滑提示：从左到右的箭头路径 */
    SWIPE_RIGHT,
    /** 上滑提示：从下到上的箭头路径 */
    SWIPE_UP,
    /** 呼吸脉冲：围绕目标的呼吸式缩放圆环 */
    PULSE,
    /** 打字光标：闪烁的光标效果 */
    TYPE
}

/**
 * 新手引导单步数据类
 *
 * 每一步引导定义一个目标 View 的聚焦区域、说明文案和手势提示动画。
 * 通过 [SpotlightTutorial] 编排器按顺序展示。
 *
 * @property targetViewId 目标 View 的资源 ID (R.id.xxx)
 * @property shape 镂空区域的几何形状，默认为圆形
 * @property title 引导标题，显示在气泡中
 * @property description 引导描述文字
 * @property hintAnimation 手势提示动画类型，默认为点击提示
 * @property hintText 可选的手势提示文字（如 "点击这里"），显示在气泡底部
 * @property paddingDp 镂空区域相对于目标 View 边缘的额外内边距(dp)，默认 16dp
 */
data class TutorialStep(
    val targetViewId: Int,
    val shape: TargetShape = TargetShape.CIRCLE,
    val title: String,
    val description: String,
    val hintAnimation: HintAnimation = HintAnimation.TAP,
    val hintText: String? = null,
    val paddingDp: Float = 16f
)
