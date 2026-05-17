package com.aicompanion.ui

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aicompanion.AppContainer
import com.aicompanion.R
import com.aicompanion.persona.Persona
import com.aicompanion.persona.PersonaManager
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File
import java.io.FileOutputStream

class HomeActivity : AppCompatActivity() {

    private lateinit var personaManager: PersonaManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PersonaAdapter
    private var pendingAvatarPath: String? = null
    private var pendingAvatarIv: ImageView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("is_activated", false)) {
            startActivity(Intent(this, ActivationActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_home)

        personaManager = PersonaManager(this)
        personaManager.load()

        recyclerView = findViewById(R.id.rv_personas)
        adapter = PersonaAdapter(personaManager.getAllPersonas())
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        findViewById<FloatingActionButton>(R.id.fab_add_persona).setOnClickListener {
            com.aicompanion.anim.AnimeUtils.pulse(it)
            showAddPersonaDialog()
        }

        val btnMoments = findViewById<View>(R.id.btn_moments_entry)
        com.aicompanion.anim.AnimeUtils.setupTouchScale(btnMoments)
        btnMoments.setOnClickListener {
            startActivity(Intent(this, com.aicompanion.moments.MomentsActivity::class.java))
        }

        val btnGroupChat = findViewById<View>(R.id.btn_group_chat_entry)
        com.aicompanion.anim.AnimeUtils.setupTouchScale(btnGroupChat)
        btnGroupChat.setOnClickListener {
            startActivity(Intent(this, com.aicompanion.groupchat.GroupChatListActivity::class.java))
        }

        val btnSettings = findViewById<View>(R.id.btn_settings_entry)
        com.aicompanion.anim.AnimeUtils.setupTouchScale(btnSettings)
        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        applyTheme()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
        recyclerView.post {
            com.aicompanion.anim.AnimeUtils.staggerSlideIn(recyclerView, fromRight = false)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && data != null) {
            val uri = data.data
            if (uri != null && (requestCode == 1001 || requestCode == 1002)) {
                val savedPath = copyAvatarToPersonaDir(uri, requestCode.toString())
                if (savedPath != null) {
                    pendingAvatarPath = savedPath
                    pendingAvatarIv?.let { iv ->
                        loadAvatarIntoView(iv, savedPath)
                    }
                }
            }
        }
    }

    private fun copyAvatarToPersonaDir(uri: Uri, suffix: String): String? {
        return try {
            val dir = File(filesDir, "personas/avatars")
            dir.mkdirs()
            val file = File(dir, "avatar_${System.currentTimeMillis()}_$suffix.jpg")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            Toast.makeText(this, "头像保存失败", Toast.LENGTH_SHORT).show()
            null
        }
    }

    private fun loadAvatarIntoView(iv: ImageView, path: String) {
        try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, opts)
            opts.inSampleSize = calculateSampleSize(opts.outWidth, opts.outHeight, 128, 128)
            opts.inJustDecodeBounds = false
            val bmp = BitmapFactory.decodeFile(path, opts)
            iv.setImageBitmap(bmp)
        } catch (_: Exception) {}
    }

    private fun refreshList() {
        adapter.updateData(personaManager.getAllPersonas())
    }

    private fun applyTheme() {
        val scheme = com.aicompanion.theme.ThemeManager.getCurrentScheme(this)
        try {
            val bgColor = try {
                android.graphics.Color.parseColor(scheme.backgroundDark)
            } catch (_: Exception) {
                0xFF0a0a1a.toInt()
            }
            findViewById<View>(R.id.home_root)?.setBackgroundColor(bgColor)
        } catch (_: Exception) {}
    }

    private fun showAddPersonaDialog() {
        pendingAvatarPath = null
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_persona, null)
        val etName = view.findViewById<EditText>(R.id.et_persona_name)
        val etPersonality = view.findViewById<EditText>(R.id.et_persona_personality)
        val etSpeech = view.findViewById<EditText>(R.id.et_persona_speech)
        val etPrompt = view.findViewById<EditText>(R.id.et_persona_prompt)
        val ivPreview = view.findViewById<ImageView>(R.id.iv_persona_avatar_preview)
        pendingAvatarIv = ivPreview

        view.findViewById<View>(R.id.btn_select_avatar).setOnClickListener {
            com.aicompanion.anim.AnimeUtils.pulse(it)
            val intent = Intent(Intent.ACTION_PICK).apply {
                type = "image/*"
            }
            startActivityForResult(intent, 1001)
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("✨ 新建角色")
            .setView(view)
            .setPositiveButton("创建") { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(this, "请输入角色名", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val persona = Persona(
                    id = java.util.UUID.randomUUID().toString(),
                    name = name,
                    personality = etPersonality.text.toString().trim(),
                    speechStyle = etSpeech.text.toString().trim(),
                    prompt = etPrompt.text.toString().trim(),
                    avatarPath = pendingAvatarPath ?: ""
                )
                personaManager.addPersona(persona)
                refreshList()
                Toast.makeText(this, "角色「$name」已创建", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showEditPersonaDialog(persona: Persona) {
        pendingAvatarPath = persona.avatarPath
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_persona, null)
        val etName = view.findViewById<EditText>(R.id.et_persona_name)
        val etPersonality = view.findViewById<EditText>(R.id.et_persona_personality)
        val etSpeech = view.findViewById<EditText>(R.id.et_persona_speech)
        val etPrompt = view.findViewById<EditText>(R.id.et_persona_prompt)
        val ivPreview = view.findViewById<ImageView>(R.id.iv_persona_avatar_preview)
        pendingAvatarIv = ivPreview

        etName.setText(persona.name)
        etPersonality.setText(persona.personality)
        etSpeech.setText(persona.speechStyle)
        etPrompt.setText(persona.prompt)

        if (persona.avatarPath.isNotBlank() && File(persona.avatarPath).exists()) {
            loadAvatarIntoView(ivPreview, persona.avatarPath)
        }

        view.findViewById<View>(R.id.btn_select_avatar).setOnClickListener {
            com.aicompanion.anim.AnimeUtils.pulse(it)
            val intent = Intent(Intent.ACTION_PICK).apply {
                type = "image/*"
            }
            startActivityForResult(intent, 1002)
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("✏️ 编辑角色")
            .setView(view)
            .setPositiveButton("保存") { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(this, "请输入角色名", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                personaManager.updatePersona(persona.id) {
                    it.copy(
                        name = name,
                        personality = etPersonality.text.toString().trim(),
                        speechStyle = etSpeech.text.toString().trim(),
                        prompt = etPrompt.text.toString().trim(),
                        avatarPath = pendingAvatarPath ?: it.avatarPath
                    )
                }
                refreshList()
            }
            .setNegativeButton("取消", null)
            .setNeutralButton("删除") { _, _ ->
                if (persona.id == "default") {
                    Toast.makeText(this, "默认角色不可删除", Toast.LENGTH_SHORT).show()
                } else {
                    personaManager.deletePersona(persona.id)
                    refreshList()
                }
            }
            .show()
    }

    inner class PersonaAdapter(private var items: List<Persona>) : RecyclerView.Adapter<PersonaAdapter.VH>() {
        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tv_persona_name)
            val tvDesc: TextView = view.findViewById(R.id.tv_persona_desc)
            val ivAvatar: ImageView = view.findViewById(R.id.iv_persona_avatar)
            val cardAvatar: MaterialCardView = view.findViewById(R.id.card_persona_avatar)
            val tvLastMsg: TextView = view.findViewById(R.id.tv_last_message)
            val btnEdit: View = view.findViewById(R.id.btn_edit_persona)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(this@HomeActivity)
                .inflate(R.layout.item_persona, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val persona = items[position]
            holder.tvName.text = persona.name
            holder.tvDesc.text = persona.personality.ifBlank { persona.description.ifBlank { "暂无简介" } }

            val lastMsg = getLastMessage(persona.id)
            holder.tvLastMsg.text = lastMsg

            val avatarPath = persona.avatarPath.ifBlank {
                val avatarPrefs = this@HomeActivity.getSharedPreferences("avatar_data", MODE_PRIVATE)
                avatarPrefs.getString("ai_avatar", "") ?: ""
            }
            if (avatarPath.isNotBlank() && File(avatarPath).exists()) {
                try {
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(avatarPath, opts)
                    opts.inSampleSize = calculateSampleSize(opts.outWidth, opts.outHeight, 96, 96)
                    opts.inJustDecodeBounds = false
                    val bmp = BitmapFactory.decodeFile(avatarPath, opts)
                    holder.ivAvatar.setImageBitmap(bmp)
                } catch (_: Exception) {}
            }

            holder.itemView.setOnClickListener {
                com.aicompanion.anim.AnimeUtils.pulse(holder.itemView)
                personaManager.setActivePersona(persona.id)
                val intent = Intent(this@HomeActivity, MainActivity::class.java).apply {
                    putExtra("persona_id", persona.id)
                }
                startActivity(intent)
            }

            holder.btnEdit.setOnClickListener {
                com.aicompanion.anim.AnimeUtils.pulse(holder.btnEdit)
                showEditPersonaDialog(persona)
            }
        }

        override fun getItemCount() = items.size

        fun updateData(newItems: List<Persona>) {
            items = newItems
            notifyDataSetChanged()
        }
    }

    private fun getLastMessage(personaId: String): String {
        val prefsName = personaManager.getChatPrefsName(personaId)
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val json = prefs.getString("messages", "[]") ?: "[]"
        try {
            val arr = org.json.JSONArray(json)
            if (arr.length() > 0) {
                val last = arr.getJSONObject(arr.length() - 1)
                val text = last.optString("text", "")
                return if (text.length > 30) text.take(30) + "..." else text
            }
        } catch (_: Exception) {}
        return "暂无消息"
    }

    private fun calculateSampleSize(width: Int, height: Int, reqW: Int, reqH: Int): Int {
        var inSampleSize = 1
        if (width > reqW || height > reqH) {
            val halfW = width / 2
            val halfH = height / 2
            while (halfW / inSampleSize >= reqW && halfH / inSampleSize >= reqH) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
