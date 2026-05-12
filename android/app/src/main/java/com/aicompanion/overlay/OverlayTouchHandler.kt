package com.aicompanion.overlay

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

class OverlayTouchHandler(
    private val context: Context,
    private val windowManager: WindowManager
) {

    private var initialX = 0
    private var initialY = 0
    private var touchX = 0f
    private var touchY = 0f
    private var moved = false

    fun handleTouch(view: View, event: MotionEvent): Boolean {
        return try {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = (view.layoutParams as? WindowManager.LayoutParams)?.x ?: 0
                    initialY = (view.layoutParams as? WindowManager.LayoutParams)?.y ?: 0
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) {
                        moved = true
                    }
                    val params = view.layoutParams as? WindowManager.LayoutParams ?: return false
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    windowManager.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    true
                }
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun hasMoved(): Boolean = moved
}
