package com.aicompanion.pixelpet

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

/**
 * 像素动画播放引擎
 *
 * 负责:
 * - 帧调度 (基于frameDuration的定时循环)
 * - 动作状态机 (idle <-> transient <-> ambient)
 * - 帧推进逻辑 (loop / once / pingpong)
 * - 回调通知机制
 *
 * 平台无关的核心逻辑，与PC端 engine.ts 保持一致的设计
 */
class PixelAnimationEngine() {

    /** 帧就绪回调: (当前帧, 动作名称) -> Unit */
    var onFrameReady: (frame: PixelFrame?, actionName: String) -> Unit = { _, _ -> }

    /** 动作切换回调: (新动作ID, 旧动作ID) -> Unit */
    var onActionChanged: (actionId: String, prevActionId: String) -> Unit = { _, _ -> }

    private val actions = mutableMapOf<String, PetAction>()
    var currentActionId: String = ""
        private set
    private var frameIndex = 0
    private var isPlaying = false
    private var lastFrameTime = 0L

    // pingpong 方向
    private var pingpongDir = 1

    // transient 动作完成后自动回退目标
    private var fallbackActionId: String = ""

    // ambient 定时器
    private var ambientDelayMs = 10_000L  // 10秒
    private var ambientJob: Job? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** 注册所有动作到引擎 */
    fun registerActions(actionList: List<PetAction>) {
        actions.clear()
        for (action in actionList) {
            actions[action.id] = action
        }
        // 如果没有设置当前动作，默认选 idle
        if (currentActionId.isEmpty() || !actions.containsKey(currentActionId)) {
            val idle = actionList.find { it.name == "idle" }
            if (idle != null) currentActionId = idle.id
            else if (actionList.isNotEmpty()) currentActionId = actionList[0].id
        }
    }

    /** 清除所有注册的动作 */
    fun clearActions() {
        stop()
        actions.clear()
        currentActionId = ""
        frameIndex = 0
    }

    /** 开始播放循环 */
    fun play() {
        if (isPlaying) return
        isPlaying = true
        lastFrameTime = System.currentTimeMillis()
        resetAmbientTimer()
        startTickLoop()
    }

    /** 停止播放 */
    fun stop() {
        isPlaying = false
        cancelAmbientTimer()
    }

    /** 切换动作 */
    fun playAction(actionId: String) {
        val prevId = currentActionId
        val action = actions[actionId] ?: return

        currentActionId = actionId
        frameIndex = 0
        pingpongDir = 1

        // transient/once 类型完成后回退
        if (action.loopMode == LoopMode.ONCE) {
            fallbackActionId = prevId.ifEmpty { getIdleActionId() }
        }

        onActionChanged(actionId, prevId)
        resetAmbientTimer()

        // 立即渲染第一帧
        notifyFrame()
    }

    /** 触发互动动作 (一次性)，完成后自动回到之前状态 */
    fun triggerInteraction(actionName: String? = null): Boolean {
        // 如果未指定动作名，随机选一个 ONCE 类型的
        val action = if (actionName != null) {
            actions.values.find { it.name == actionName && it.loopMode == LoopMode.ONCE }
        } else {
            val onceActions = actions.values.filter { it.loopMode == LoopMode.ONCE }
            onceActions.randomOrNull()
        } ?: return false
        playAction(action.id)
        return true
    }

    /** 触发随机环境动作 */
    fun triggerRandomAmbient(): Boolean {
        val ambientNames = listOf("sleep", "eat", "think", "dance")
        val ambientActions = actions.values.filter {
            it.name in ambientNames && it.frames.isNotEmpty()
        }
        if (ambientActions.isEmpty()) return false
        val random = ambientActions.random()
        fallbackActionId = getIdleActionId()
        playAction(random.id)
        return true
    }

    // ═════════ 内部: 主循环 ═════════

    private fun startTickLoop() {
        scope.launch {
            while (isPlaying) {
                val now = System.currentTimeMillis()
                val action = getCurrentAction()

                if (action != null && action.frames.isNotEmpty()) {
                    val elapsed = now - lastFrameTime
                    if (elapsed >= action.frameDuration) {
                        advanceFrame()
                        lastFrameTime = now
                        notifyFrame()
                    }
                }

                delay(16L) // ~60fps tick rate
            }
        }
    }

    /** 帧推进 + 循环模式处理 */
    private fun advanceFrame() {
        val action = getCurrentAction() ?: return
        if (action.frames.size <= 1) return

        val totalFrames = action.frames.size

        when (action.loopMode) {
            LoopMode.LOOP -> {
                frameIndex = (frameIndex + 1) % totalFrames
            }
            LoopMode.PINGPONG -> {
                frameIndex += pingpongDir
                when {
                    frameIndex >= totalFrames - 1 -> {
                        pingpongDir = -1
                        frameIndex = totalFrames - 1
                    }
                    frameIndex <= 0 -> {
                        pingpongDir = 1
                        frameIndex = 0
                    }
                }
            }
            LoopMode.ONCE -> {
                frameIndex++
                if (frameIndex >= totalFrames) {
                    frameIndex = totalFrames - 1 // 停在最后一帧
                    // 延迟后回退到 fallback
                    if (fallbackActionId.isNotEmpty() && actions.containsKey(fallbackActionId)) {
                        scope.launch {
                            delay(action.frameDuration * 2)
                            playAction(fallbackActionId)
                        }
                    }
                }
            }
        }
    }

    // ═════════ 内部: 定时器管理 ═════════

    private fun resetAmbientTimer() {
        cancelAmbientTimer()
        val action = getCurrentAction()
        if (action?.name == "idle") {
            ambientJob = scope.launch {
                delay(ambientDelayMs)
                triggerRandomAmbient()
            }
        }
    }

    private fun cancelAmbientTimer() {
        ambientJob?.cancel()
        ambientJob = null
    }

    // ═════════ 内部: 通知 ═════════

    private fun notifyFrame() {
        val action = getCurrentAction()
        val frame = action?.frames?.getOrNull(frameIndex)
        onFrameReady(frame, action?.name ?: "")
    }

    // ═════════ 查询方法 ═════════

    fun getCurrentAction(): PetAction? = actions[currentActionId]

    fun getCurrentFrame(): PixelFrame? = getCurrentAction()?.frames?.getOrNull(frameIndex)

    fun getIdleActionId(): String {
        return actions.values.find { it.name == "idle" }?.id
            ?: currentActionId
            ?: ""
    }

    fun getIsPlaying(): Boolean = isPlaying

    /** 销毁引擎 */
    fun destroy() {
        stop()
        scope.cancel()
        actions.clear()
    }

    companion object {
        private const val TAG = "PixelAnimEngine"
    }
}
