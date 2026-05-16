package com.aicompanion.sticker

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.aicompanion.AppContainer
import com.aicompanion.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class StickerActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "StickerActivity"
        private const val PICK_IMAGE = 1001
    }

    private lateinit var stickerManager: StickerManager
    private lateinit var gridView: GridView
    private lateinit var adapter: StickerGridAdapter
    private var currentTab = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sticker)
        stickerManager = StickerManager(this)
        stickerManager.loadStickers()
        gridView = findViewById(R.id.sticker_grid)
        adapter = StickerGridAdapter(stickerManager.getAllStickers())
        gridView.adapter = adapter
        findViewById<View>(R.id.btn_add_sticker).setOnClickListener { pickImage() }
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.tab_all).setOnClickListener { switchTab(0) }
        findViewById<View>(R.id.tab_builtin).setOnClickListener { switchTab(1) }
        findViewById<View>(R.id.tab_user).setOnClickListener { switchTab(2) }
        updateTabHighlight()
    }

    private fun switchTab(tab: Int) {
        currentTab = tab
        adapter.updateData(when (tab) {
            1 -> stickerManager.getBuiltinStickers()
            2 -> stickerManager.getUserStickers()
            else -> stickerManager.getAllStickers()
        })
        updateTabHighlight()
    }

    private fun updateTabHighlight() {
        val tabs = listOf<View>(findViewById(R.id.tab_all), findViewById(R.id.tab_builtin), findViewById(R.id.tab_user))
        val labels = listOf("全部", "内置", "我的")
        tabs.forEachIndexed { i, v ->
            val tv = v as? TextView
            tv?.let {
                it.text = labels[i]
                it.setTextColor(if (i == currentTab) 0xFF5C6BC0.toInt() else 0xFF999999.toInt())
                it.paint.isFakeBoldText = (i == currentTab)
            }
        }
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
        startActivityForResult(intent, PICK_IMAGE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE && resultCode == Activity.RESULT_OK && data != null) {
            val uri = data.data ?: return
            val tmpFile = copyToTemp(uri)
            showAddDialog(tmpFile.absolutePath)
        }
    }

    private fun copyToTemp(uri: Uri): File {
        val tmp = File(cacheDir, "sticker_tmp_${System.currentTimeMillis()}.png")
        contentResolver.openInputStream(uri)?.use { input ->
            tmp.outputStream().use { output -> input.copyTo(output) }
        }
        return tmp
    }

    private fun showAddDialog(imagePath: String) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_sticker, null)
        val ivPreview = view.findViewById<ImageView>(R.id.iv_sticker_preview)
        val etDesc = view.findViewById<EditText>(R.id.et_sticker_description)
        val etEmotion = view.findViewById<EditText>(R.id.et_sticker_emotion)
        val etTags = view.findViewById<EditText>(R.id.et_sticker_tags)
        val rgOwner = view.findViewById<android.widget.RadioGroup>(R.id.rg_sticker_owner)
        try {
            val bmp = android.graphics.BitmapFactory.decodeFile(imagePath)
            ivPreview.setImageBitmap(bmp)
        } catch (_: Exception) {}
        AlertDialog.Builder(this)
            .setTitle("添加表情包")
            .setView(view)
            .setPositiveButton("添加") { _, _ ->
                val desc = etDesc.text.toString().trim()
                val emotion = etEmotion.text.toString().trim()
                val tags = etTags.text.toString().trim().split(Regex("[,，\\s]+")).filter { it.isNotBlank() }
                val owner = when (rgOwner.checkedRadioButtonId) {
                    R.id.rb_owner_ai -> "ai"
                    else -> "user"
                }
                if (desc.isBlank() && emotion.isBlank()) {
                    Toast.makeText(this, "请至少填写描述或情感", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val searchText = "$desc $emotion ${tags.joinToString(" ")}"
                CoroutineScope(Dispatchers.Main).launch {
                    var embedding: FloatArray? = null
                    try {
                        val apiClient = AppContainer.apiClient
                        if (apiClient != null) {
                            withContext(Dispatchers.IO) {
                                embedding = apiClient.getEmbedding(searchText)
                            }
                        }
                    } catch (_: Exception) {}
                    val sticker = stickerManager.addSticker(
                        sourcePath = imagePath,
                        description = desc,
                        emotion = emotion,
                        tags = tags,
                        owner = owner,
                        embedding = embedding
                    )
                    switchTab(currentTab)
                    Toast.makeText(this@StickerActivity, "表情包已添加", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    inner class StickerGridAdapter(private var items: List<Sticker>) : android.widget.BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(pos: Int) = items[pos]
        override fun getItemId(pos: Int) = pos.toLong()
        override fun getView(pos: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(this@StickerActivity)
                .inflate(R.layout.item_sticker, parent, false)
            val sticker = items[pos]
            val iv = view.findViewById<ImageView>(R.id.iv_sticker_thumb)
            val tvEmotion = view.findViewById<TextView>(R.id.tv_sticker_emotion)
            val tvBadge = view.findViewById<TextView>(R.id.tv_sticker_badge)
            try {
                if (sticker.filePath.isNotBlank() && File(sticker.filePath).exists()) {
                    val bmp = android.graphics.BitmapFactory.decodeFile(sticker.filePath)
                    iv.setImageBitmap(bmp)
                } else {
                    iv.setImageResource(android.R.drawable.ic_menu_gallery)
                }
            } catch (_: Exception) {
                iv.setImageResource(android.R.drawable.ic_menu_gallery)
            }
            tvEmotion.text = sticker.emotion.ifBlank { sticker.description.take(6) }
            tvBadge.visibility = if (sticker.owner == "builtin") View.VISIBLE else View.GONE
            view.setOnClickListener {
                val resultIntent = Intent().apply {
                    putExtra("sticker_path", sticker.filePath)
                    putExtra("sticker_id", sticker.id)
                    putExtra("sticker_emotion", sticker.emotion)
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
            view.setOnLongClickListener {
                if (sticker.owner == "builtin") {
                    Toast.makeText(this@StickerActivity, "内置表情包不可删除", Toast.LENGTH_SHORT).show()
                    true
                }
                AlertDialog.Builder(this@StickerActivity)
                    .setTitle("删除表情包")
                    .setMessage("确定删除这个表情包吗？")
                    .setPositiveButton("删除") { _, _ ->
                        stickerManager.deleteSticker(sticker.id)
                        switchTab(currentTab)
                    }
                    .setNegativeButton("取消", null)
                    .show()
                true
            }
            return view
        }

        fun updateData(newItems: List<Sticker>) {
            items = newItems
            notifyDataSetChanged()
        }
    }
}
