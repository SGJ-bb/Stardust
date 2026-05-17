package com.aicompanion.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.NinePatch
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.NinePatchDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.aicompanion.util.AppLogger
import org.json.JSONArray
import java.io.InputStream

data class BubbleSkin(
    val id: String,
    val name: String,
    val userBgColor: Int,
    val userGradientColors: IntArray? = null,
    val userCornerRadius: Float = 18f,
    val userStrokeColor: Int = Color.TRANSPARENT,
    val userStrokeWidth: Float = 0f,
    val aiBgColor: Int,
    val aiCornerRadius: Float = 18f,
    val aiStrokeColor: Int = Color.TRANSPARENT,
    val aiStrokeWidth: Float = 0f,
    val aiAlpha: Int = 255,
    val userImageAsset: String? = null,
    val aiImageAsset: String? = null
)

data class AvatarFrame(
    val id: String,
    val name: String,
    val strokeColor: Int,
    val strokeWidth: Float = 2f,
    val cornerRadius: Float = 18f,
    val glowColor: Int = Color.TRANSPARENT,
    val glowRadius: Int = 0,
    val imageAsset: String? = null
)

data class ImageBubbleSkin(
    val id: String,
    val name: String,
    val assetFile: String
)

data class ImageAvatarFrame(
    val id: String,
    val name: String,
    val assetFile: String
)

object BubbleSkinManager {
    private const val TAG = "BubbleSkinManager"
    private const val PREFS_NAME = "bubble_skin_prefs"
    private const val KEY_BUBBLE_SKIN = "active_bubble_skin"
    private const val KEY_AI_FRAME = "active_ai_frame"
    private const val KEY_USER_FRAME = "active_user_frame"
    private const val KEY_IMAGE_BUBBLE = "active_image_bubble"
    private const val KEY_AI_IMAGE_FRAME = "active_ai_image_frame"
    private const val KEY_USER_IMAGE_FRAME = "active_user_image_frame"

    val builtinSkins = listOf(
        BubbleSkin(
            id = "default", name = "默认",
            userBgColor = Color.parseColor("#ff6b9d"),
            userCornerRadius = 18f,
            aiBgColor = Color.parseColor("#BB4a2a3a"),
            aiCornerRadius = 18f
        ),
        BubbleSkin(
            id = "glass", name = "毛玻璃",
            userBgColor = Color.parseColor("#66ff6b9d"),
            userCornerRadius = 20f,
            userStrokeColor = Color.parseColor("#33ff6b9d"),
            userStrokeWidth = 1f,
            aiBgColor = Color.parseColor("#441a1a3e"),
            aiCornerRadius = 20f,
            aiStrokeColor = Color.parseColor("#33667eea"),
            aiStrokeWidth = 1f,
            aiAlpha = 200
        ),
        BubbleSkin(
            id = "neon", name = "霓虹",
            userBgColor = Color.parseColor("#1a1a2e"),
            userCornerRadius = 16f,
            userStrokeColor = Color.parseColor("#00e5ff"),
            userStrokeWidth = 1.5f,
            aiBgColor = Color.parseColor("#1a1a2e"),
            aiCornerRadius = 16f,
            aiStrokeColor = Color.parseColor("#667eea"),
            aiStrokeWidth = 1.5f
        ),
        BubbleSkin(
            id = "candy", name = "糖果",
            userBgColor = Color.parseColor("#FF6B6B"),
            userGradientColors = intArrayOf(Color.parseColor("#FF6B6B"), Color.parseColor("#FFB347")),
            userCornerRadius = 22f,
            aiBgColor = Color.parseColor("#BBE8DAEF"),
            aiCornerRadius = 22f,
            aiStrokeColor = Color.parseColor("#D4A5FF"),
            aiStrokeWidth = 1f
        ),
        BubbleSkin(
            id = "mint", name = "薄荷",
            userBgColor = Color.parseColor("#2ECC71"),
            userCornerRadius = 14f,
            aiBgColor = Color.parseColor("#BB1a2e2e"),
            aiCornerRadius = 14f,
            aiStrokeColor = Color.parseColor("#2ECC71"),
            aiStrokeWidth = 0.5f
        )
    )

    val builtinFrames = listOf(
        AvatarFrame(id = "default", name = "默认", strokeColor = Color.parseColor("#667eea"), strokeWidth = 1.5f, cornerRadius = 18f),
        AvatarFrame(id = "pink_glow", name = "粉光", strokeColor = Color.parseColor("#ff6b9d"), strokeWidth = 2f, cornerRadius = 18f, glowColor = Color.parseColor("#44ff6b9d"), glowRadius = 6),
        AvatarFrame(id = "gold", name = "金边", strokeColor = Color.parseColor("#FFD700"), strokeWidth = 2f, cornerRadius = 18f, glowColor = Color.parseColor("#33FFD700"), glowRadius = 4),
        AvatarFrame(id = "cyber", name = "赛博", strokeColor = Color.parseColor("#00e5ff"), strokeWidth = 1.5f, cornerRadius = 18f, glowColor = Color.parseColor("#2200e5ff"), glowRadius = 8),
        AvatarFrame(id = "none", name = "无框", strokeColor = Color.TRANSPARENT, strokeWidth = 0f, cornerRadius = 18f)
    )

    private var cachedImageBubbles: List<ImageBubbleSkin>? = null
    private var cachedImageFrames: List<ImageAvatarFrame>? = null
    private val bitmapCache = mutableMapOf<String, Bitmap>()

    fun loadImageBubbles(context: Context): List<ImageBubbleSkin> {
        cachedImageBubbles?.let { return it }
        val list = mutableListOf<ImageBubbleSkin>()
        try {
            val json = context.assets.open("bubble_skins/bubble_metadata.json").bufferedReader().use { it.readText() }
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(ImageBubbleSkin(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    assetFile = "bubble_skins/${obj.getString("file")}"
                ))
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "loadImageBubbles failed: ${e.message}")
        }
        cachedImageBubbles = list
        return list
    }

    fun loadImageFrames(context: Context): List<ImageAvatarFrame> {
        cachedImageFrames?.let { return it }
        val list = mutableListOf<ImageAvatarFrame>()
        try {
            val json = context.assets.open("avatar_frames/frame_metadata.json").bufferedReader().use { it.readText() }
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(ImageAvatarFrame(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    assetFile = "avatar_frames/${obj.getString("file")}"
                ))
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "loadImageFrames failed: ${e.message}")
        }
        cachedImageFrames = list
        return list
    }

    fun loadBitmapFromAsset(context: Context, assetPath: String): Bitmap? {
        bitmapCache[assetPath]?.let { return it }
        return try {
            val `is`: InputStream = context.assets.open(assetPath)
            val options = BitmapFactory.Options().apply { inSampleSize = 1 }
            val bmp = BitmapFactory.decodeStream(`is`, null, options)
            `is`.close()
            if (bmp != null) bitmapCache[assetPath] = bmp
            bmp
        } catch (e: Exception) {
            AppLogger.e(TAG, "loadBitmapFromAsset failed: $assetPath - ${e.message}")
            null
        }
    }

    fun getActiveSkin(context: Context): BubbleSkin {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val id = prefs.getString(KEY_BUBBLE_SKIN, "default") ?: "default"
        return builtinSkins.find { it.id == id } ?: builtinSkins.first()
    }

    fun setActiveSkin(context: Context, skinId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_BUBBLE_SKIN, skinId).apply()
    }

    fun getActiveImageBubble(context: Context): ImageBubbleSkin? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val id = prefs.getString(KEY_IMAGE_BUBBLE, null) ?: return null
        return loadImageBubbles(context).find { it.id == id }
    }

    fun setActiveImageBubble(context: Context, skinId: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_IMAGE_BUBBLE, skinId).apply()
    }

    fun getActiveAiFrame(context: Context): AvatarFrame {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val id = prefs.getString(KEY_AI_FRAME, "default") ?: "default"
        return builtinFrames.find { it.id == id } ?: builtinFrames.first()
    }

    fun getActiveUserFrame(context: Context): AvatarFrame {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val id = prefs.getString(KEY_USER_FRAME, "default") ?: "default"
        return builtinFrames.find { it.id == id } ?: builtinFrames.first()
    }

    fun setActiveAiFrame(context: Context, frameId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_AI_FRAME, frameId).apply()
    }

    fun setActiveUserFrame(context: Context, frameId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_USER_FRAME, frameId).apply()
    }

    fun getActiveAiImageFrame(context: Context): ImageAvatarFrame? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val id = prefs.getString(KEY_AI_IMAGE_FRAME, null) ?: return null
        return loadImageFrames(context).find { it.id == id }
    }

    fun getActiveUserImageFrame(context: Context): ImageAvatarFrame? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val id = prefs.getString(KEY_USER_IMAGE_FRAME, null) ?: return null
        return loadImageFrames(context).find { it.id == id }
    }

    fun setActiveAiImageFrame(context: Context, frameId: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_AI_IMAGE_FRAME, frameId).apply()
    }

    fun setActiveUserImageFrame(context: Context, frameId: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_USER_IMAGE_FRAME, frameId).apply()
    }

    fun applyBubbleSkin(bubble: View, skin: BubbleSkin, isUser: Boolean) {
        try {
            if (bubble is FrameLayout) {
                val overlayIv = bubble.findViewWithTag<ImageView>("bubble_bg_overlay")
                overlayIv?.visibility = View.GONE
            }
            val drawable = if (isUser) {
                if (skin.userGradientColors != null && skin.userGradientColors.size >= 2) {
                    GradientDrawable(GradientDrawable.Orientation.TL_BR, skin.userGradientColors).apply {
                        cornerRadius = skin.userCornerRadius
                    }
                } else {
                    GradientDrawable().apply {
                        setColor(skin.userBgColor)
                        cornerRadius = skin.userCornerRadius
                    }
                }
            } else {
                GradientDrawable().apply {
                    setColor(skin.aiBgColor)
                    cornerRadius = skin.aiCornerRadius
                    alpha = skin.aiAlpha
                }
            }
            if (isUser && skin.userStrokeWidth > 0 && skin.userStrokeColor != Color.TRANSPARENT) {
                (drawable as? GradientDrawable)?.setStroke(skin.userStrokeWidth.toInt(), skin.userStrokeColor)
            }
            if (!isUser && skin.aiStrokeWidth > 0 && skin.aiStrokeColor != Color.TRANSPARENT) {
                (drawable as? GradientDrawable)?.setStroke(skin.aiStrokeWidth.toInt(), skin.aiStrokeColor)
            }
            bubble.background = drawable
        } catch (e: Exception) {
            AppLogger.e(TAG, "applyBubbleSkin failed: ${e.message}")
        }
    }

    fun applyImageBubbleSkin(bubble: View, context: Context, imageSkin: ImageBubbleSkin) {
        try {
            val bmp = loadBitmapFromAsset(context, imageSkin.assetFile) ?: return
            val is9Patch = bmp.ninePatchChunk != null
            if (is9Patch) {
                val overlayIv = (bubble as? FrameLayout)?.findViewWithTag<ImageView>("bubble_bg_overlay")
                overlayIv?.visibility = View.GONE
                val ninePatchDrawable = NinePatchDrawable(context.resources, bmp, bmp.ninePatchChunk, null, null)
                bubble.background = ninePatchDrawable
            } else {
                bubble.setBackgroundResource(0)
                if (bubble is FrameLayout) {
                    var overlayIv = bubble.findViewWithTag<ImageView>("bubble_bg_overlay")
                    if (overlayIv == null) {
                        overlayIv = ImageView(context).apply {
                            tag = "bubble_bg_overlay"
                            layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                            )
                            scaleType = ImageView.ScaleType.FIT_XY
                        }
                        bubble.addView(overlayIv, 0)
                    }
                    overlayIv.setImageBitmap(bmp)
                    overlayIv.visibility = View.VISIBLE
                } else {
                    bubble.background = BitmapDrawable(context.resources, bmp)
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "applyImageBubbleSkin failed: ${e.message}")
        }
    }

    fun applyAvatarFrame(cardView: com.google.android.material.card.MaterialCardView, frame: AvatarFrame) {
        try {
            cardView.radius = frame.cornerRadius
            cardView.strokeColor = frame.strokeColor
            cardView.strokeWidth = frame.strokeWidth.toInt()
        } catch (e: Exception) {
            AppLogger.e(TAG, "applyAvatarFrame failed: ${e.message}")
        }
    }

    fun applyImageAvatarFrame(frameLayout: FrameLayout, context: Context, imageFrame: ImageAvatarFrame) {
        try {
            frameLayout.clipChildren = false
            frameLayout.clipToPadding = false
            var overlayIv = frameLayout.findViewWithTag<ImageView>("frame_overlay")
            if (overlayIv == null) {
                overlayIv = ImageView(context).apply {
                    tag = "frame_overlay"
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    scaleType = ImageView.ScaleType.FIT_XY
                }
                frameLayout.addView(overlayIv)
            }
            val bmp = loadBitmapFromAsset(context, imageFrame.assetFile)
            if (bmp != null) {
                overlayIv.setImageBitmap(bmp)
                overlayIv.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "applyImageAvatarFrame failed: ${e.message}")
        }
    }

    fun clearImageAvatarFrame(frameLayout: FrameLayout) {
        val overlayIv = frameLayout.findViewWithTag<ImageView>("frame_overlay")
        overlayIv?.visibility = View.GONE
    }
}
