package com.aicompanion.ui

import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.aicompanion.util.AppLogger
import java.io.File

class ChatBubblePopup(private val context: Context) {

    companion object {
        private const val TAG = "ChatBubblePopup"
        private const val AUTO_DISMISS_MS = 4000L
        private const val MAX_TEXT_LENGTH = 120
    }

    private var bubbleView: View? = null
    private var rootView: ViewGroup? = null
    private val handler = Handler(Looper.getMainLooper())
    private var dismissRunnable: Runnable? = null

    fun show(senderName: String, message: String, avatarPath: String? = null) {
        dismiss()

        try {
            val activity = findActivity() ?: return
            rootView = activity.findViewById(android.R.id.content) as? ViewGroup ?: return

            val bubble = createBubbleView(senderName, message, avatarPath)
            bubbleView = bubble

            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                bottomMargin = dpToPx(100)
                leftMargin = dpToPx(16)
                rightMargin = dpToPx(60)
            }

            rootView?.addView(bubble, params)

            bubble.alpha = 0f
            bubble.translationY = dpToPx(40).toFloat()
            bubble.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .setInterpolator(android.view.animation.OvershootInterpolator(0.8f))
                .start()

            dismissRunnable = Runnable { dismissWithAnimation() }
            handler.postDelayed(dismissRunnable!!, AUTO_DISMISS_MS)

            bubble.setOnClickListener {
                handler.removeCallbacks(dismissRunnable!!)
                dismissWithAnimation()
            }

        } catch (e: Exception) {
            AppLogger.e(TAG, "show failed: ${e.message}")
        }
    }

    private fun createBubbleView(senderName: String, message: String, avatarPath: String?): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
            val bg = GradientDrawable().apply {
                setColor(0xE6222244.toInt())
                cornerRadius = dpToPx(18).toFloat()
                setStroke(dpToPx(1), 0x409c7cff.toInt())
            }
            background = bg
            elevation = dpToPx(6).toFloat()
        }

        val avatarSize = dpToPx(36)
        val avatarView = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize).apply {
                marginEnd = dpToPx(10)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            val bg = GradientDrawable().apply {
                setColor(0xFF1e1e44.toInt())
                cornerRadius = avatarSize / 2f
            }
            background = bg
        }

        if (!avatarPath.isNullOrBlank() && File(avatarPath).exists()) {
            try {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(avatarPath, opts)
                opts.inSampleSize = calculateSampleSize(opts.outWidth, opts.outHeight, 72, 72)
                opts.inJustDecodeBounds = false
                val bmp = BitmapFactory.decodeFile(avatarPath, opts)
                val circleBmp = cropCircle(bmp)
                avatarView.setImageBitmap(circleBmp)
            } catch (_: Exception) {
                avatarView.setImageResource(android.R.drawable.ic_menu_myplaces)
            }
        } else {
            avatarView.setImageResource(android.R.drawable.ic_menu_myplaces)
        }

        container.addView(avatarView)

        val textContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val nameView = TextView(context).apply {
            text = senderName
            setTextColor(0xFFc4b5fd.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        textContainer.addView(nameView)

        val displayText = if (message.length > MAX_TEXT_LENGTH) {
            message.take(MAX_TEXT_LENGTH) + "…"
        } else {
            message
        }

        val msgView = TextView(context).apply {
            text = displayText
            setTextColor(0xFFe0e0f0.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setLineSpacing(dpToPx(2).toFloat(), 1f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(2)
            }
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        textContainer.addView(msgView)

        container.addView(textContainer)

        return container
    }

    private fun dismissWithAnimation() {
        val bubble = bubbleView ?: return
        try {
            bubble.animate()
                .alpha(0f)
                .translationY(dpToPx(30).toFloat())
                .setDuration(250)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        removeBubble()
                    }
                })
                .start()
        } catch (e: Exception) {
            removeBubble()
        }
    }

    fun dismiss() {
        handler.removeCallbacksAndMessages(null)
        dismissRunnable = null
        removeBubble()
    }

    private fun removeBubble() {
        try {
            bubbleView?.let { bv ->
                (bv.parent as? ViewGroup)?.removeView(bv)
            }
        } catch (_: Exception) {}
        bubbleView = null
    }

    private fun cropCircle(bitmap: Bitmap?): Bitmap? {
        if (bitmap == null) return null
        val size = minOf(bitmap.width, bitmap.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        val dx = (bitmap.width - size) / 2f
        val dy = (bitmap.height - size) / 2f
        canvas.drawBitmap(bitmap, -dx, -dy, paint)
        bitmap.recycle()
        return output
    }

    private fun calculateSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (width > reqWidth || height > reqHeight) {
            val halfW = width / 2
            val halfH = height / 2
            while (halfW / inSampleSize >= reqWidth && halfH / inSampleSize >= reqHeight) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    private fun findActivity(): android.app.Activity? {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is android.app.Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}
