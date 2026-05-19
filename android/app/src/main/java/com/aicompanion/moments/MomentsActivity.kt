package com.aicompanion.moments

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aicompanion.AppContainer
import com.aicompanion.R
import com.aicompanion.persona.PersonaManager
import com.aicompanion.sticker.StickerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MomentsActivity : AppCompatActivity() {

    private lateinit var momentsManager: MomentsManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MomentAdapter
    private var pendingImagePath: String? = null
    private var pendingStickerPath: String? = null
    private lateinit var personaManager: PersonaManager
    private var currentMomentDialog: com.google.android.material.dialog.MaterialAlertDialogBuilder? = null
    private var currentMomentContentView: View? = null
    private var currentMomentEt: EditText? = null

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val uri = result.data?.data ?: return@registerForActivityResult
            try {
                val tmp = File(cacheDir, "moment_img_${System.currentTimeMillis()}.jpg")
                contentResolver.openInputStream(uri)?.use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
                pendingImagePath = tmp.absolutePath
                Toast.makeText(this, "图片已选择", Toast.LENGTH_SHORT).show()
                updateMomentDialogPreview()
            } catch (e: Exception) {
                Toast.makeText(this, "图片选择失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val stickerPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            pendingStickerPath = result.data?.getStringExtra("sticker_path")
            Toast.makeText(this, "表情包已选择", Toast.LENGTH_SHORT).show()
            updateMomentDialogPreview()
        }
    }

    private var cachedPersonaName: String? = null
    private fun getCachedPersonaName(): String {
        if (cachedPersonaName == null) {
            cachedPersonaName = getPersonaInfo().first
        }
        return cachedPersonaName!!
    }

    private fun getPersonaInfo(): Pair<String, String> {
        val activeId = getSharedPreferences("app_prefs", MODE_PRIVATE)
            .getString("active_persona_id", "default") ?: "default"
        return getPersonaInfoById(activeId)
    }

    private fun getPersonaInfoById(personaId: String): Pair<String, String> {
        val personaPrefs = getSharedPreferences("persona_data_$personaId", MODE_PRIVATE)
        val name = com.aicompanion.persona.PersonaManager(this).let { pm ->
            pm.load()
            pm.getPersona(personaId)?.name
        } ?: personaPrefs.getString("persona_name", null)
            ?: getSharedPreferences("app_prefs", MODE_PRIVATE).getString("ai_name", "星尘") ?: "星尘"
        val prompt = buildString {
            append("你是「$name」。")
            personaPrefs.getString("persona_desc", "")?.takeIf { it.isNotBlank() }?.let { append("\n简介：$it") }
            personaPrefs.getString("persona_personality", "")?.takeIf { it.isNotBlank() }?.let { append("\n性格：$it") }
            personaPrefs.getString("persona_speech_style", "")?.takeIf { it.isNotBlank() }?.let { append("\n说话风格：$it") }
        }
        return Pair(name, prompt)
    }

    private fun getPersonaNameById(personaId: String): String {
        val persona = personaManager.getPersona(personaId)
        if (persona != null) return persona.name
        val prefs = getSharedPreferences("persona_data_$personaId", MODE_PRIVATE)
        return prefs.getString("persona_name", "AI") ?: "AI"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_moments)
        applySharedTheme()
        momentsManager = MomentsManager(this)
        momentsManager.loadMoments()
        personaManager = PersonaManager(this)
        personaManager.load()
        recyclerView = findViewById(R.id.rv_moments)
        adapter = MomentAdapter(momentsManager.getAllMoments())
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        findViewById<View>(R.id.btn_back).setOnClickListener {
            com.aicompanion.anim.AnimeUtils.pulse(it)
            finish()
        }
        findViewById<View>(R.id.btn_new_moment).setOnClickListener {
            com.aicompanion.anim.AnimeUtils.pulse(it)
            showNewMomentDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshMoments()
        recyclerView.post {
            com.aicompanion.anim.AnimeUtils.staggerFadeInScale(recyclerView)
        }
    }

    private fun refreshMoments() {
        adapter.updateData(momentsManager.getAllMoments())
    }

    private fun applySharedTheme() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val bgPath = prefs.getString("chat_background", "")

        val ivBg = findViewById<ImageView>(R.id.iv_moments_bg)
        if (!bgPath.isNullOrEmpty()) {
            val file = java.io.File(bgPath)
            if (file.exists()) {
                try {
                    val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeFile(bgPath, options)
                    options.inSampleSize = calculateSampleSize(options.outWidth, options.outHeight, 1080, 1920)
                    options.inJustDecodeBounds = false
                    val bmp = android.graphics.BitmapFactory.decodeFile(bgPath, options)
                    ivBg.setImageBitmap(bmp)
                    ivBg.alpha = 0.3f
                } catch (_: Exception) {}
            }
        } else {
            val scheme = com.aicompanion.theme.ThemeManager.getCurrentScheme(this)
            try {
                val bgColor = safeParseColor(scheme.primaryColorDark, 0xFF1a1a2e.toInt())
                ivBg.setBackgroundColor(bgColor)
            } catch (_: Exception) {}
        }

        com.aicompanion.theme.ThemeManager.applyTheme(this)
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

    private fun decodeSampledBitmap(path: String, reqW: Int, reqH: Int): android.graphics.Bitmap? {
        return try {
            val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(path, options)
            options.inSampleSize = calculateSampleSize(options.outWidth, options.outHeight, reqW, reqH)
            options.inJustDecodeBounds = false
            android.graphics.BitmapFactory.decodeFile(path, options)
        } catch (_: Exception) { null }
    }

    private fun safeParseColor(colorStr: String?, default: Int): Int {
        if (colorStr.isNullOrEmpty()) return default
        return try { android.graphics.Color.parseColor(colorStr) } catch (_: Exception) { default }
    }

    private fun showNewMomentDialog() {
        pendingImagePath = null
        pendingStickerPath = null
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_new_moment, null)
        val etContent = view.findViewById<EditText>(R.id.et_moment_content)
        val btnImage = view.findViewById<View>(R.id.btn_add_image)
        val btnSticker = view.findViewById<View>(R.id.btn_add_sticker)

        currentMomentContentView = view
        currentMomentEt = etContent

        btnImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
            imagePickerLauncher.launch(intent)
        }
        btnSticker.setOnClickListener {
            val intent = Intent(this, StickerActivity::class.java)
            stickerPickerLauncher.launch(intent)
        }

        updateMomentDialogPreview()

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("✏️ 发动态")
            .setView(view)
            .setPositiveButton("发布") { _, _ ->
                val content = etContent.text.toString().trim()
                if (content.isBlank() && pendingImagePath == null && pendingStickerPath == null) {
                    Toast.makeText(this, "请输入内容", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val activeId = getSharedPreferences("app_prefs", MODE_PRIVATE)
                    .getString("active_persona_id", "default") ?: "default"
                val moment = Moment(
                    id = java.util.UUID.randomUUID().toString(),
                    author = "user",
                    authorPersonaId = activeId,
                    content = content,
                    imagePath = copyToPermanent(pendingImagePath),
                    stickerPath = pendingStickerPath
                )
                momentsManager.addMoment(moment)
                pendingImagePath = null
                pendingStickerPath = null
                currentMomentContentView = null
                currentMomentEt = null
                refreshMoments()
                triggerAiReplyToUserMoment(moment)
            }
            .setNegativeButton("取消") { _, _ ->
                pendingImagePath = null
                pendingStickerPath = null
                currentMomentContentView = null
                currentMomentEt = null
            }
            .setOnCancelListener {
                pendingImagePath = null
                pendingStickerPath = null
                currentMomentContentView = null
                currentMomentEt = null
            }
            .show()
    }

    private fun updateMomentDialogPreview() {
        val view = currentMomentContentView ?: return
        val ivPreview = view.findViewById<ImageView>(R.id.iv_moment_image_preview)
        if (ivPreview == null) return

        if (pendingImagePath != null) {
            try {
                val bmp = decodeSampledBitmap(pendingImagePath!!, 200, 200)
                if (bmp != null) {
                    ivPreview.setImageBitmap(bmp)
                    ivPreview.visibility = View.VISIBLE
                }
            } catch (_: Exception) {}
        } else {
            ivPreview.visibility = View.GONE
        }
    }

    private fun copyToPermanent(tempPath: String?): String? {
        if (tempPath == null) return null
        val src = File(tempPath)
        if (!src.exists()) return null
        val momentsDir = File(filesDir, "moments/images").apply { mkdirs() }
        val dest = File(momentsDir, "${System.currentTimeMillis()}.jpg")
        src.copyTo(dest)
        return dest.absolutePath
    }

    private fun triggerAiReplyToUserMoment(moment: Moment) {
        val apiClient = AppContainer.apiClient ?: return
        val persona = getPersonaInfo()
        lifecycleScope.launch {
            val reply = momentsManager.generateAiCommentOnUserMoment(
                apiClient,
                persona.first,
                persona.second,
                moment.content
            )
            if (reply != null) {
                val activeId = getSharedPreferences("app_prefs", MODE_PRIVATE)
                    .getString("active_persona_id", "default") ?: "default"
                momentsManager.addComment(moment.id, Comment(
                    id = java.util.UUID.randomUUID().toString(),
                    author = "ai",
                    authorPersonaId = activeId,
                    content = reply
                ))
                refreshMoments()
            }
        }
    }

    inner class MomentAdapter(private var items: List<Moment>) : RecyclerView.Adapter<MomentAdapter.VH>() {
        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvAuthor: TextView = view.findViewById(R.id.tv_moment_author)
            val tvContent: TextView = view.findViewById(R.id.tv_moment_content)
            val ivImage: ImageView = view.findViewById(R.id.iv_moment_image)
            val ivSticker: ImageView = view.findViewById(R.id.iv_moment_sticker)
            val tvTime: TextView = view.findViewById(R.id.tv_moment_time)
            val commentsContainer: LinearLayout = view.findViewById(R.id.comments_container)
            val btnComment: View = view.findViewById(R.id.btn_comment)
            val btnDelete: View = view.findViewById(R.id.btn_delete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(this@MomentsActivity)
                .inflate(R.layout.item_moment, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val moment = items[position]
            val authorName = if (moment.author == "ai") {
                if (!moment.authorPersonaId.isNullOrBlank()) {
                    getPersonaNameById(moment.authorPersonaId)
                } else {
                    getCachedPersonaName()
                }
            } else {
                "我"
            }
            holder.tvAuthor.text = authorName
            holder.tvContent.text = moment.content
            holder.tvContent.visibility = if (moment.content.isBlank()) View.GONE else View.VISIBLE
            holder.tvTime.text = DateUtils.getRelativeTimeSpanString(
                moment.createdAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
            )

            holder.ivImage.visibility = View.GONE
            holder.ivSticker.visibility = View.GONE
            moment.imagePath?.let { path ->
                if (File(path).exists()) {
                    try {
                        val bmp = decodeSampledBitmap(path, 400, 400)
                        holder.ivImage.setImageBitmap(bmp)
                        holder.ivImage.visibility = View.VISIBLE
                    } catch (_: Exception) {}
                }
            }
            moment.stickerPath?.let { path ->
                if (File(path).exists()) {
                    try {
                        val bmp = decodeSampledBitmap(path, 400, 400)
                        holder.ivSticker.setImageBitmap(bmp)
                        holder.ivSticker.visibility = View.VISIBLE
                    } catch (_: Exception) {}
                }
            }

            holder.commentsContainer.removeAllViews()
            for (comment in moment.comments) {
                try {
                    val commentView = LayoutInflater.from(this@MomentsActivity)
                        .inflate(R.layout.item_comment, holder.commentsContainer, false)
                    val tvCommentAuthor = commentView.findViewById<TextView>(R.id.tv_comment_author)
                    val tvCommentContent = commentView.findViewById<TextView>(R.id.tv_comment_content)
                    tvCommentAuthor.text = if (comment.author == "ai") {
                        if (!comment.authorPersonaId.isNullOrBlank()) {
                            getPersonaNameById(comment.authorPersonaId)
                        } else {
                            getCachedPersonaName()
                        }
                    } else {
                        "我"
                    }
                    tvCommentContent.text = comment.content
                    holder.commentsContainer.addView(commentView)
                } catch (e: Exception) {
                    android.util.Log.w("MomentsActivity", "Failed to render comment: ${e.message}")
                }
            }

            holder.btnComment.setOnClickListener {
                showCommentDialog(moment)
            }

            holder.btnDelete.setOnClickListener {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this@MomentsActivity)
                    .setTitle("🗑 删除动态")
                    .setMessage("确定删除这条动态吗？")
                    .setPositiveButton("删除") { _, _ ->
                        momentsManager.deleteMoment(moment.id)
                        refreshMoments()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }

        override fun getItemCount() = items.size

        fun updateData(newItems: List<Moment>) {
            items = newItems
            notifyDataSetChanged()
        }
    }

    private fun showCommentDialog(moment: Moment) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val input = EditText(this).apply {
            hint = "写评论..."
            setHintTextColor(0xFF556677.toInt())
            setTextColor(0xFFe0e0f0.toInt())
            textSize = 14f
        }
        container.addView(input)

        try {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("💬 评论")
                .setView(container)
                .setPositiveButton("发送") { _, _ ->
                    val text = input.text?.toString()?.trim() ?: ""
                    if (text.isBlank()) return@setPositiveButton
                    val activeId = getSharedPreferences("app_prefs", MODE_PRIVATE)
                        .getString("active_persona_id", "default") ?: "default"
                    momentsManager.addComment(moment.id, Comment(
                        id = java.util.UUID.randomUUID().toString(),
                        author = "user",
                        authorPersonaId = activeId,
                        content = text
                    ))
                    refreshMoments()
                    triggerAiReplyToComment(moment, text)
                }
                .setNegativeButton("取消", null)
                .show()
        } catch (e: IllegalArgumentException) {
            val text = input.text?.toString()?.trim() ?: ""
            if (text.isNotBlank()) {
                val activeId = getSharedPreferences("app_prefs", MODE_PRIVATE)
                    .getString("active_persona_id", "default") ?: "default"
                momentsManager.addComment(moment.id, Comment(
                    id = java.util.UUID.randomUUID().toString(),
                    author = "user",
                    authorPersonaId = activeId,
                    content = text
                ))
                refreshMoments()
            }
        }
    }

    private fun triggerAiReplyToComment(moment: Moment, commentText: String) {
        val apiClient = AppContainer.apiClient ?: return
        val authorId = moment.authorPersonaId
        val persona = if (!authorId.isNullOrBlank()) {
            getPersonaInfoById(authorId)
        } else {
            getPersonaInfo()
        }
        lifecycleScope.launch {
            val reply = if (moment.author == "ai") {
                momentsManager.generateAiReply(
                    apiClient,
                    persona.first,
                    persona.second,
                    moment.content,
                    commentText
                )
            } else {
                momentsManager.generateAiCommentOnUserMoment(
                    apiClient,
                    persona.first,
                    persona.second,
                    "${moment.content}\n\n用户评论了：「$commentText」"
                )
            }
            if (reply != null) {
                momentsManager.addComment(moment.id, Comment(
                    id = java.util.UUID.randomUUID().toString(),
                    author = "ai",
                    authorPersonaId = authorId ?: getSharedPreferences("app_prefs", MODE_PRIVATE)
                        .getString("active_persona_id", "default") ?: "default",
                    content = reply
                ))
                refreshMoments()
            }
        }
    }
}
