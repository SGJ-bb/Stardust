package com.aicompanion.album

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.aicompanion.R
import com.aicompanion.theme.ThemeManager
import kotlinx.coroutines.launch
import java.io.File

class MemorialAlbumActivity : AppCompatActivity() {

    private lateinit var gridContainer: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyView: LinearLayout
    private lateinit var scrollView: ScrollView
    private var currentTemplateIndex = 0
    private val templateChips = mutableListOf<com.google.android.material.chip.Chip>()

    private val slideshowHandler = Handler(Looper.getMainLooper())
    private var slideshowRunnable: Runnable? = null
    private var slideshowDialog: android.app.Dialog? = null
    private var slideshowIndex = 0
    private var allEntries: List<AlbumEntry> = emptyList()

    private var fullScreenDialog: android.app.Dialog? = null

    private var charRefDialog: android.app.AlertDialog? = null
    private var charRefTempPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        currentTemplateIndex = MemorialAlbumManager.getCurrentTemplateIndex(this)

        val scheme = ThemeManager.getCurrentScheme(this)
        val bgColor = parseColor(scheme.backgroundDark, "#0a0a1a")
        val cardColor = parseColor(scheme.cardColor, "#1a1a30")
        val primaryColor = parseColor(scheme.primaryColor, "#667eea")
        val textColor = parseColor(scheme.textColor, "#ececf4")
        val textSecColor = parseColor(scheme.textSecondaryColor, "#99aabb")

        window.statusBarColor = bgColor

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
        }

        val toolbar = com.google.android.material.appbar.MaterialToolbar(this).apply {
            title = "纪念相册"
            setNavigationIcon(android.R.drawable.ic_menu_revert)
            setNavigationOnClickListener { finish() }
            setTitleTextColor(textColor)
            setBackgroundColor(bgColor)
        }
        root.addView(toolbar)

        progressBar = ProgressBar(this).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
        }
        root.addView(progressBar)

        if (!MemorialAlbumManager.isImageModelConfigured(this)) {
            val warningCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val pad = dp(14); setPadding(pad, dp(10), pad, dp(10))
                layoutParams = llMatchWrap().apply { setMargins(dp(12), dp(8), dp(12), 0) }
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(0xFF221a1a.toInt())
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), 0xFF4a2a2a.toInt())
                }
            }
            warningCard.addView(TextView(this).apply {
                text = "⚠️ 图片生成模型未配置\n请前往 设置 → AI功能 → 图片生成配置 中填写API地址和密钥"
                textSize = 13f; setTextColor(0xFFff8866.toInt())
            })
            root.addView(warningCard)
        }

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val pad = dp(12); setPadding(pad, dp(6), pad, dp(6))
        }

        val btnGenerate = com.google.android.material.button.MaterialButton(this).apply {
            text = "✨ 随机生成"
            textSize = 13f; setTextColor(android.graphics.Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(primaryColor)
            cornerRadius = 20
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setOnClickListener { generateRandomImage() }
        }
        actionRow.addView(btnGenerate)

        actionRow.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(8), 1) })

        val btnGenChar = com.google.android.material.button.MaterialButton(this).apply {
            text = "🎭 角色形象"
            textSize = 13f; setTextColor(android.graphics.Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF9C27B0.toInt())
            cornerRadius = 20
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setOnClickListener { showCharacterRefDialog() }
        }
        actionRow.addView(btnGenChar)

        actionRow.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(8), 1) })

        val btnSlideshow = com.google.android.material.button.MaterialButton(this).apply {
            text = "🎬 幻灯片"
            textSize = 13f; setTextColor(android.graphics.Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF00897B.toInt())
            cornerRadius = 20
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setOnClickListener { startSlideshow() }
        }
        actionRow.addView(btnSlideshow)

        actionRow.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(8), 1) })

        val captionHint = TextView(this).apply {
            text = "点击图片查看大图"
            textSize = 11f; setTextColor(textSecColor)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        actionRow.addView(captionHint)
        root.addView(actionRow)

        val templateScroll = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            val pad = dp(8); setPadding(pad, dp(2), pad, dp(2))
        }
        val templateRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val templates = MemorialAlbumManager.layoutTemplates
        for ((i, tmpl) in templates.withIndex()) {
            val chip = com.google.android.material.chip.Chip(this).apply {
                text = "${tmpl.icon} ${tmpl.name}"
                textSize = 11f
                tag = i
                setOnClickListener {
                    currentTemplateIndex = i
                    MemorialAlbumManager.saveCurrentTemplateIndex(this@MemorialAlbumActivity, i)
                    updateTemplateChipStyles()
                    loadAlbum()
                }
            }
            templateChips.add(chip)
            templateRow.addView(chip)
        }
        templateScroll.addView(templateRow)
        root.addView(templateScroll)
        updateTemplateChipStyles()

        if (MemorialAlbumManager.hasCharacterRefImage(this)) {
            val refPath = MemorialAlbumManager.getCharacterRefImagePath(this)
            val refFile = File(refPath)
            if (refFile.exists()) {
                val refPreviewRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    val pad = dp(12); setPadding(pad, dp(4), pad, dp(4))
                }
                val refThumbSize = dp(40)
                val refImageView = ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(refThumbSize, refThumbSize)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(0xFF2a2a4a.toInt())
                        cornerRadius = dp(6).toFloat()
                    }
                    setOnClickListener { showCharacterRefDialog() }
                }
                val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(refPath, opts)
                opts.inSampleSize = calculateInSampleSize(opts, refThumbSize, refThumbSize)
                opts.inJustDecodeBounds = false
                val bitmap = android.graphics.BitmapFactory.decodeFile(refPath, opts)
                if (bitmap != null) refImageView.setImageBitmap(bitmap)
                refPreviewRow.addView(refImageView)
                refPreviewRow.addView(TextView(this).apply {
                    text = "  🎭 角色形象已设定"
                    textSize = 11f; setTextColor(0xFFc4b5fd.toInt())
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                })
                root.addView(refPreviewRow)
            }
        }

        val chipScroll = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            val pad = dp(8); setPadding(pad, 0, pad, dp(6))
        }
        val chipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((title, _) in MemorialAlbumManager.getBuiltinPrompts()) {
            chipRow.addView(com.google.android.material.chip.Chip(this).apply {
                text = title; textSize = 11f
                chipBackgroundColor = android.content.res.ColorStateList.valueOf(cardColor)
                setTextColor(0xFFc4b5fd.toInt())
                setOnClickListener { generateImageForTitle(title) }
            })
        }
        chipScroll.addView(chipRow)
        root.addView(chipScroll)

        emptyView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dp(40), 0, 0)
            }
        }
        emptyView.addView(TextView(this).apply {
            text = "📷"
            textSize = 40f
            gravity = Gravity.CENTER
        })
        emptyView.addView(TextView(this).apply {
            text = "还没有纪念图片\n点击上方按钮生成吧~"
            textSize = 14f; setTextColor(textSecColor)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        })
        root.addView(emptyView)

        scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        gridContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(8); setPadding(pad, 0, pad, dp(16))
        }
        scrollView.addView(gridContainer)
        root.addView(scrollView)

        setContentView(root)
        loadAlbum()
    }

    private fun updateTemplateChipStyles() {
        val scheme = ThemeManager.getCurrentScheme(this)
        val cardColor = parseColor(scheme.cardColor, "#1a1a30")
        val primaryColor = parseColor(scheme.primaryColor, "#667eea")
        for ((i, chip) in templateChips.withIndex()) {
            chip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                if (i == currentTemplateIndex) primaryColor else cardColor
            )
            chip.setTextColor(
                if (i == currentTemplateIndex) android.graphics.Color.WHITE else 0xFFc4b5fd.toInt()
            )
        }
    }

    private fun formatMonthHeader(monthKey: String): String {
        val parts = monthKey.split("-")
        if (parts.size == 2) {
            val year = parts[0]
            val month = parts[1].toIntOrNull() ?: return monthKey
            return "${year}年${month}月"
        }
        return monthKey
    }

    private fun loadAlbum() {
        gridContainer.removeAllViews()
        val currentTemplate = MemorialAlbumManager.layoutTemplates[currentTemplateIndex]

        val entries = MemorialAlbumManager.getEntries(this)
            .sortedByDescending { it.createdAt }
        allEntries = entries

        emptyView.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        scrollView.visibility = if (entries.isEmpty()) View.GONE else View.VISIBLE

        val scheme = ThemeManager.getCurrentScheme(this)
        val cardColor = parseColor(scheme.cardColor, "#1a1a30")
        val textColor = parseColor(scheme.textColor, "#ececf4")
        val textSecColor = parseColor(scheme.textSecondaryColor, "#99aabb")
        val primaryColor = parseColor(scheme.primaryColor, "#667eea")

        val screenWidth = resources.displayMetrics.widthPixels
        val colWidth = (screenWidth - dp(32)) / currentTemplate.columns

        val grouped = entries.groupBy { it.createdAt.substring(0, 7) }

        for ((monthKey, monthEntries) in grouped) {
            val monthHeader = TextView(this).apply {
                text = formatMonthHeader(monthKey)
                textSize = 14f
                setTextColor(primaryColor)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(dp(4), dp(12), dp(4), dp(2))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            gridContainer.addView(monthHeader)

            val divider = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
                    setMargins(0, 0, 0, dp(8))
                }
                setBackgroundColor(0x22FFFFFF)
            }
            gridContainer.addView(divider)

            val monthGrid = GridLayout(this).apply {
                columnCount = currentTemplate.columns
                val pad = dp(4); setPadding(pad, 0, pad, 0)
            }

            for (entry in monthEntries) {
                val heightMultiplier = MemorialAlbumManager.aspectRatioToHeightMultiplier(entry.aspectRatio)
                val imageHeight = (colWidth * heightMultiplier).toInt()

                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(cardColor)
                        cornerRadius = dp(12).toFloat()
                        setStroke(dp(1), parseColor(scheme.surfaceColor, "#2a2a4a"))
                    }
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = colWidth
                        setMargins(dp(4), dp(4), dp(4), dp(4))
                        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    }
                }

                val imageFrame = FrameLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, imageHeight)
                }

                val imageFile = File(entry.imagePath)
                if (imageFile.exists()) {
                    val imageView = ImageView(this).apply {
                        layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                    val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeFile(imageFile.absolutePath, opts)
                    opts.inSampleSize = calculateInSampleSize(opts, colWidth, imageHeight)
                    opts.inJustDecodeBounds = false
                    val bitmap = android.graphics.BitmapFactory.decodeFile(imageFile.absolutePath, opts)
                    if (bitmap != null) imageView.setImageBitmap(bitmap)
                    imageFrame.addView(imageView)
                }

                val captionOverlay = TextView(this).apply {
                    text = entry.caption
                    textSize = 11f; setTextColor(0xFFFFFFFF.toInt())
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(dp(6), dp(4), dp(6), dp(4))
                    visibility = if (entry.caption.isNotBlank()) View.VISIBLE else View.GONE
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                        gravity = Gravity.BOTTOM
                    }
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(0x88000000.toInt())
                    }
                }
                imageFrame.addView(captionOverlay)
                imageFrame.setOnClickListener { showFullScreenViewer(entry.imagePath) }
                card.addView(imageFrame)

                val infoRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    val pad = dp(8); setPadding(pad, dp(6), pad, dp(6))
                }

                infoRow.addView(TextView(this).apply {
                    text = entry.title
                    textSize = 12f; setTextColor(textColor)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })

                infoRow.addView(TextView(this).apply {
                    text = entry.createdAt.substring(5, 10)
                    textSize = 10f; setTextColor(textSecColor)
                })
                card.addView(infoRow)

                if (entry.caption.isNotBlank()) {
                    card.addView(TextView(this).apply {
                        text = entry.caption
                        textSize = 11f; setTextColor(textSecColor)
                        maxLines = 2
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        setPadding(dp(8), 0, dp(8), dp(6))
                    })
                }

                card.setOnClickListener { showEntryDetail(entry) }

                monthGrid.addView(card)
            }
            gridContainer.addView(monthGrid)
        }
    }

    private fun showFullScreenViewer(imagePath: String) {
        fullScreenDialog?.dismiss()

        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val imageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }

        val imageFile = File(imagePath)
        if (imageFile.exists()) {
            val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(imageFile.absolutePath, opts)
            opts.inSampleSize = calculateInSampleSize(opts, resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
            opts.inJustDecodeBounds = false
            val bitmap = android.graphics.BitmapFactory.decodeFile(imageFile.absolutePath, opts)
            if (bitmap != null) imageView.setImageBitmap(bitmap)
        }

        val hintText = TextView(this).apply {
            text = "点击关闭 · 双指缩放 · 下滑退出"
            textSize = 12f
            setTextColor(0x88FFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(24))
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM
            }
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            addView(imageView)
            addView(hintText)
        }

        var currentScale = 1f
        val scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                currentScale *= detector.scaleFactor
                currentScale = currentScale.coerceIn(0.5f, 5f)
                imageView.scaleX = currentScale
                imageView.scaleY = currentScale
                return true
            }
        })

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                dialog.dismiss()
                return true
            }
        })

        var downY = 0f
        var isSwiping = false

        root.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downY = event.rawY
                    isSwiping = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!scaleDetector.isInProgress && currentScale < 1.1f) {
                        val dy = event.rawY - downY
                        if (dy > dp(10)) {
                            isSwiping = true
                            imageView.translationY = dy
                            imageView.alpha = 1f - (dy / resources.displayMetrics.heightPixels).coerceIn(0f, 1f)
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isSwiping && imageView.translationY > dp(150)) {
                        dialog.dismiss()
                    } else {
                        imageView.animate().translationY(0f).alpha(1f).setDuration(200).start()
                    }
                    isSwiping = false
                }
            }
            true
        }

        dialog.setContentView(root)
        fullScreenDialog = dialog
        dialog.setOnDismissListener { fullScreenDialog = null }
        dialog.show()

        imageView.alpha = 0f
        imageView.animate().alpha(1f).setDuration(200).start()
        hintText.animate().alpha(0f).setStartDelay(3000).setDuration(500).start()
    }

    private fun shareImage(imagePath: String) {
        val file = File(imagePath)
        if (!file.exists()) {
            Toast.makeText(this, "图片文件不存在", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "分享图片"))
        } catch (e: Exception) {
            Toast.makeText(this, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEntryDetail(entry: AlbumEntry) {
        val scheme = ThemeManager.getCurrentScheme(this)
        val textColor = parseColor(scheme.textColor, "#ececf4")
        val textSecColor = parseColor(scheme.textSecondaryColor, "#99aabb")

        val contentView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(16); setPadding(pad, pad, pad, pad)
        }

        val imageFile = File(entry.imagePath)
        if (imageFile.exists()) {
            val imageView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
            }
            val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(imageFile.absolutePath, opts)
            opts.inSampleSize = calculateInSampleSize(opts, 512, 512)
            opts.inJustDecodeBounds = false
            val bitmap = android.graphics.BitmapFactory.decodeFile(imageFile.absolutePath, opts)
            if (bitmap != null) imageView.setImageBitmap(bitmap)
            imageView.setOnClickListener { showFullScreenViewer(entry.imagePath) }
            contentView.addView(imageView)
        }

        contentView.addView(TextView(this).apply {
            text = entry.title
            textSize = 16f; setTextColor(textColor)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(2))
        })

        contentView.addView(TextView(this).apply {
            text = "${entry.createdAt}  ·  ${entry.aspectRatio}"
            textSize = 12f; setTextColor(textSecColor)
        })

        val captionLabel = TextView(this).apply {
            text = if (entry.caption.isNotBlank()) entry.caption else "点击添加文字注释..."
            textSize = 13f
            setTextColor(if (entry.caption.isNotBlank()) textColor else textSecColor)
            setPadding(0, dp(8), 0, dp(4))
        }
        contentView.addView(captionLabel)

        contentView.addView(TextView(this).apply {
            text = "Prompt: ${entry.prompt.take(100)}"
            textSize = 10f; setTextColor(0xFF556677.toInt())
            setPadding(0, dp(4), 0, 0)
        })

        val dialog = android.app.AlertDialog.Builder(this)
            .setView(contentView)
            .apply {
                setNeutralButton("分享") { _, _ ->
                    shareImage(entry.imagePath)
                }
                setNegativeButton("编辑注释") { _, _ ->
                    showCaptionEditor(entry)
                }
                setPositiveButton("删除") { _, _ ->
                    MemorialAlbumManager.deleteEntry(this@MemorialAlbumActivity, entry.id)
                    loadAlbum()
                    Toast.makeText(this@MemorialAlbumActivity, "已删除", Toast.LENGTH_SHORT).show()
                }
            }
            .create()
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_settings_card)
    }

    private fun showCaptionEditor(entry: AlbumEntry) {
        val scheme = ThemeManager.getCurrentScheme(this)
        val textColor = parseColor(scheme.textColor, "#ececf4")

        val contentView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(20); setPadding(pad, pad, pad, pad)
        }

        contentView.addView(TextView(this).apply {
            text = "编辑「${entry.title}」的文字注释"
            textSize = 14f; setTextColor(textColor)
            setPadding(0, 0, 0, dp(8))
        })

        val editText = EditText(this).apply {
            text = android.text.Editable.Factory.getInstance().newEditable(entry.caption)
            textSize = 14f; setTextColor(textColor)
            hint = "写下这一刻的故事..."
            setHintTextColor(0xFF667788.toInt())
            minLines = 3
            maxLines = 6
            gravity = Gravity.TOP
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF1a1a38.toInt())
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), 0xFF2a2a4a.toInt())
            }
        }
        contentView.addView(editText)

        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("文字注释")
            .setView(contentView)
            .setPositiveButton("保存") { _, _ ->
                val newCaption = editText.text?.toString()?.trim() ?: ""
                MemorialAlbumManager.updateCaption(this, entry.id, newCaption)
                loadAlbum()
                Toast.makeText(this, "注释已保存", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .create()
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_settings_card)
    }

    private fun generateRandomImage() {
        val (title, prompt) = MemorialAlbumManager.getRandomPrompt()
        doGenerate(title, prompt)
    }

    private fun generateImageForTitle(title: String) {
        MemorialAlbumManager.getBuiltinPrompts().find { it.first == title }?.let { doGenerate(it.first, it.second) }
    }

    private fun doGenerate(title: String, prompt: String) {
        if (!MemorialAlbumManager.isImageModelConfigured(this)) {
            Toast.makeText(this, "请先在设置中配置图片生成模型（API地址+密钥）", Toast.LENGTH_LONG).show()
            return
        }
        val currentTemplate = MemorialAlbumManager.layoutTemplates[currentTemplateIndex]
        val aspectRatio = currentTemplate.aspectRatio
        progressBar.visibility = View.VISIBLE
        Toast.makeText(this, "正在生成「$title」(${currentTemplate.name}, ${aspectRatio})...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val entry = MemorialAlbumManager.generateImage(this@MemorialAlbumActivity, prompt, title, aspectRatio = aspectRatio)
            progressBar.visibility = View.GONE
            if (entry != null) {
                Toast.makeText(this@MemorialAlbumActivity, "「$title」生成成功！", Toast.LENGTH_SHORT).show()
                loadAlbum()
            } else {
                Toast.makeText(this@MemorialAlbumActivity, "生成失败，请检查图片生成API配置", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showCharacterRefDialog() {
        if (!MemorialAlbumManager.isImageModelConfigured(this)) {
            Toast.makeText(this, "请先在设置中配置图片生成模型（API地址+密钥）", Toast.LENGTH_LONG).show()
            return
        }

        val scheme = ThemeManager.getCurrentScheme(this)
        val textColor = parseColor(scheme.textColor, "#ececf4")
        val textSecColor = parseColor(scheme.textSecondaryColor, "#99aabb")

        val contentView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val pad = dp(20); setPadding(pad, dp(16), pad, dp(16))
        }

        contentView.addView(TextView(this).apply {
            text = "角色参考图"
            textSize = 16f; setTextColor(textColor)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(8))
        })

        contentView.addView(TextView(this).apply {
            text = "生成角色和你的形象参考图，后续所有图片都会参考此形象保持一致性。"
            textSize = 12f; setTextColor(textSecColor)
            setPadding(0, 0, 0, dp(12))
        })

        val imageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(300))
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF1a1a38.toInt())
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), 0xFF2a2a4a.toInt())
            }
        }
        contentView.addView(imageView)

        val existingRefPath = MemorialAlbumManager.getCharacterRefImagePath(this)
        val existingFile = File(existingRefPath)
        if (existingFile.exists()) {
            val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(existingRefPath, opts)
            opts.inSampleSize = calculateInSampleSize(opts, 512, 512)
            opts.inJustDecodeBounds = false
            val bitmap = android.graphics.BitmapFactory.decodeFile(existingRefPath, opts)
            if (bitmap != null) imageView.setImageBitmap(bitmap)
            charRefTempPath = existingRefPath
        } else {
            imageView.setImageDrawable(null)
            charRefTempPath = null
        }

        val statusText = TextView(this).apply {
            text = if (existingFile.exists()) "当前已保存角色形象" else "尚未生成角色形象"
            textSize = 12f; setTextColor(textSecColor)
            setPadding(0, dp(8), 0, dp(4))
            gravity = Gravity.CENTER
        }
        contentView.addView(statusText)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        }

        val btnRegenerate = com.google.android.material.button.MaterialButton(this).apply {
            text = "重新生成"
            textSize = 13f; setTextColor(android.graphics.Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF9C27B0.toInt())
            cornerRadius = 20
        }
        btnRow.addView(btnRegenerate)

        btnRow.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(12), 1) })

        val btnSave = com.google.android.material.button.MaterialButton(this).apply {
            text = "保存"
            textSize = 13f; setTextColor(android.graphics.Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF4CAF50.toInt())
            cornerRadius = 20
            isEnabled = charRefTempPath != null
        }
        btnRow.addView(btnSave)

        contentView.addView(btnRow)

        val dialog = android.app.AlertDialog.Builder(this)
            .setView(contentView)
            .setPositiveButton("关闭", null)
            .create()
        charRefDialog = dialog
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_settings_card)

        btnRegenerate.setOnClickListener {
            if (!MemorialAlbumManager.isImageModelConfigured(this)) {
                Toast.makeText(this, "请先配置图片生成模型", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            btnRegenerate.isEnabled = false
            btnSave.isEnabled = false
            statusText.text = "正在生成角色参考图..."
            progressBar.visibility = View.VISIBLE
            lifecycleScope.launch {
                val tempPath = MemorialAlbumManager.generateCharacterRefImage(this@MemorialAlbumActivity)
                progressBar.visibility = View.GONE
                btnRegenerate.isEnabled = true
                if (tempPath != null) {
                    charRefTempPath = tempPath
                    val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeFile(tempPath, opts)
                    opts.inSampleSize = calculateInSampleSize(opts, 512, 512)
                    opts.inJustDecodeBounds = false
                    val bitmap = android.graphics.BitmapFactory.decodeFile(tempPath, opts)
                    if (bitmap != null) imageView.setImageBitmap(bitmap)
                    statusText.text = "新形象已生成，点击「保存」确认"
                    btnSave.isEnabled = true
                    Toast.makeText(this@MemorialAlbumActivity, "角色参考图生成成功！", Toast.LENGTH_SHORT).show()
                } else {
                    statusText.text = "生成失败，请检查API配置"
                    Toast.makeText(this@MemorialAlbumActivity, "角色参考图生成失败", Toast.LENGTH_LONG).show()
                }
            }
        }

        btnSave.setOnClickListener {
            val tempPath = charRefTempPath
            if (tempPath.isNullOrBlank()) {
                Toast.makeText(this, "没有可保存的参考图", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (MemorialAlbumManager.confirmCharacterRefImage(this, tempPath)) {
                Toast.makeText(this, "角色参考图已保存！", Toast.LENGTH_SHORT).show()
                charRefDialog?.dismiss()
                charRefDialog = null
                recreate()
            } else {
                Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startSlideshow() {
        val validEntries = allEntries.filter { File(it.imagePath).exists() }
        if (validEntries.isEmpty()) {
            Toast.makeText(this, "没有可播放的图片", Toast.LENGTH_SHORT).show()
            return
        }

        slideshowIndex = 0
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)

        val imageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }

        val counterText = TextView(this).apply {
            textSize = 14f
            setTextColor(0x88FFFFFF.toInt())
            setPadding(dp(16), dp(24), dp(16), dp(16))
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.END
            }
        }

        val titleText = TextView(this).apply {
            textSize = 16f
            setTextColor(0xCCFFFFFF.toInt())
            setPadding(dp(16), 0, dp(16), dp(24))
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM or Gravity.START
            }
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            addView(imageView)
            addView(counterText)
            addView(titleText)
        }

        dialog.setContentView(root)
        dialog.show()
        slideshowDialog = dialog
        dialog.setOnDismissListener {
            slideshowRunnable?.let { slideshowHandler.removeCallbacks(it) }
            slideshowRunnable = null
            slideshowDialog = null
        }

        fun showSlide() {
            if (slideshowIndex >= validEntries.size) slideshowIndex = 0
            val entry = validEntries[slideshowIndex]
            val file = File(entry.imagePath)
            counterText.text = "${slideshowIndex + 1} / ${validEntries.size}"
            titleText.text = entry.title
            slideshowIndex++

            imageView.animate().alpha(0f).setDuration(300).withEndAction {
                if (file.exists()) {
                    val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts)
                    opts.inSampleSize = calculateInSampleSize(opts, 1024, 1024)
                    opts.inJustDecodeBounds = false
                    val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts)
                    if (bitmap != null) imageView.setImageBitmap(bitmap)
                }
                imageView.animate().alpha(1f).setDuration(500).start()
            }.start()
        }

        showSlide()
        slideshowRunnable = object : Runnable {
            override fun run() {
                showSlide()
                slideshowHandler.postDelayed(this, 3000)
            }
        }
        slideshowHandler.postDelayed(slideshowRunnable!!, 3000)

        root.setOnClickListener { dialog.dismiss() }
    }

    private fun stopSlideshow() {
        slideshowRunnable?.let { slideshowHandler.removeCallbacks(it) }
        slideshowRunnable = null
        slideshowDialog?.dismiss()
        slideshowDialog = null
    }

    override fun onDestroy() {
        stopSlideshow()
        fullScreenDialog?.dismiss()
        charRefDialog?.dismiss()
        super.onDestroy()
    }

    private fun calculateInSampleSize(options: android.graphics.BitmapFactory.Options, reqW: Int, reqH: Int): Int {
        val (h, w) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (h > reqH || w > reqW) {
            val halfH = h / 2; val halfW = w / 2
            while (halfH / inSampleSize >= reqH && halfW / inSampleSize >= reqW) inSampleSize *= 2
        }
        return inSampleSize
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun llMatchWrap() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

    private fun parseColor(colorStr: String, fallback: String): Int {
        return try { android.graphics.Color.parseColor(colorStr) } catch (_: Exception) {
            try { android.graphics.Color.parseColor(fallback) } catch (_: Exception) { 0xFF1a1a2e.toInt() }
        }
    }
}
