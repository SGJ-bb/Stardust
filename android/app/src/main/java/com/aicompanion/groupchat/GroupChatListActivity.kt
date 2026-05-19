package com.aicompanion.groupchat

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aicompanion.R
import com.aicompanion.anim.AnimeUtils
import com.aicompanion.network.ApiClient
import com.aicompanion.persona.PersonaManager
import com.aicompanion.prompt.PromptBuilder
import com.aicompanion.settings.SettingsManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class GroupChatListActivity : AppCompatActivity() {

    private lateinit var groupChatManager: GroupChatManager
    private lateinit var personaManager: PersonaManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: GroupChatListAdapter
    private lateinit var tvEmptyHint: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group_chat_list)

        groupChatManager = GroupChatManager(this)
        groupChatManager.load()
        personaManager = PersonaManager(this)
        personaManager.load()

        recyclerView = findViewById(R.id.rv_groups)
        tvEmptyHint = findViewById(R.id.tv_empty_hint)
        adapter = GroupChatListAdapter(groupChatManager.getAllGroups())
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        findViewById<View>(R.id.btn_back).setOnClickListener {
            AnimeUtils.pulse(it)
            finish()
        }

        findViewById<View>(R.id.fab_add_group).setOnClickListener {
            AnimeUtils.pulse(it)
            AnimeUtils.springScale(it, 0.9f, 1f, 400)
            showCreateGroupBottomSheet()
        }

        updateEmptyState()

        recyclerView.post {
            AnimeUtils.staggerSlideIn(recyclerView, fromRight = false)
        }
    }

    override fun onResume() {
        super.onResume()
        groupChatManager.load()
        adapter.updateData(groupChatManager.getAllGroups())
        updateEmptyState()
    }

    private fun updateEmptyState() {
        if (groupChatManager.getAllGroups().isEmpty()) {
            tvEmptyHint.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            tvEmptyHint.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun showCreateGroupBottomSheet() {
        val personas = personaManager.getAllPersonas()
        if (personas.size < 2) {
            Toast.makeText(this, "至少需要2个角色才能创建群聊", Toast.LENGTH_SHORT).show()
            return
        }

        val sheet = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_create_group, null)
        sheet.setContentView(view)
        sheet.behavior.peekHeight = (resources.displayMetrics.heightPixels * 0.75).toInt()
        sheet.behavior.isDraggable = false

        val step1 = view.findViewById<View>(R.id.step1_content)
        val step2 = view.findViewById<View>(R.id.step2_content)
        val step3 = view.findViewById<View>(R.id.step3_content)
        val btnNext = view.findViewById<TextView>(R.id.btn_next)
        val btnPrev = view.findViewById<TextView>(R.id.btn_prev)
        val spacer = view.findViewById<View>(R.id.spacer_buttons)
        val tvStepTitle = view.findViewById<TextView>(R.id.tv_step_title)
        val dotStep1 = view.findViewById<View>(R.id.dot_step1)
        val dotStep2 = view.findViewById<View>(R.id.dot_step2)
        val dotStep3 = view.findViewById<View>(R.id.dot_step3)
        val etGroupName = view.findViewById<EditText>(R.id.et_group_name)
        val etRelationship = view.findViewById<EditText>(R.id.et_relationship_setting)
        val btnAiGenRelationship = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_ai_gen_relationship)
        val progressRelationship = view.findViewById<ProgressBar>(R.id.progress_relationship)
        val rvMemberSelect = view.findViewById<RecyclerView>(R.id.rv_member_select)
        val tvSelectedCount = view.findViewById<TextView>(R.id.tv_selected_count)
        val tvPreviewName = view.findViewById<TextView>(R.id.tv_preview_name)
        val tvPreviewMembers = view.findViewById<TextView>(R.id.tv_preview_members)

        var currentStep = 1
        val selectedIds = mutableSetOf<String>()

        btnAiGenRelationship.setOnClickListener {
            AnimeUtils.pulse(it)
            val sm = SettingsManager(this)
            if (sm.chatApiUrl.isBlank()) {
                Toast.makeText(this, "请先配置聊天API", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            btnAiGenRelationship.isEnabled = false
            btnAiGenRelationship.text = "生成中..."
            progressRelationship.visibility = View.VISIBLE
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    generateRelationshipWithAI(personas, selectedIds.toList())
                }
                btnAiGenRelationship.isEnabled = true
                btnAiGenRelationship.text = "🪄 AI生成关系"
                progressRelationship.visibility = View.GONE
                if (result != null) {
                    etRelationship.setText(result)
                    Toast.makeText(this@GroupChatListActivity, "关系设定已生成", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@GroupChatListActivity, "生成失败，请检查API配置", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val memberAdapter = MemberSelectAdapter(personas, selectedIds) { count ->
            tvSelectedCount.text = "已选择 $count 人"
        }
        rvMemberSelect.layoutManager = LinearLayoutManager(this)
        rvMemberSelect.adapter = memberAdapter

        fun updateStepUI() {
            step1.visibility = if (currentStep == 1) View.VISIBLE else View.GONE
            step2.visibility = if (currentStep == 2) View.VISIBLE else View.GONE
            step3.visibility = if (currentStep == 3) View.VISIBLE else View.GONE

            dotStep1.setBackgroundResource(if (currentStep >= 1) R.drawable.bg_dot_active else R.drawable.bg_dot_inactive)
            dotStep2.setBackgroundResource(if (currentStep >= 2) R.drawable.bg_dot_active else R.drawable.bg_dot_inactive)
            dotStep3.setBackgroundResource(if (currentStep >= 3) R.drawable.bg_dot_active else R.drawable.bg_dot_inactive)

            val titles = listOf("创建群聊", "选择成员", "确认创建")
            tvStepTitle.text = titles[currentStep - 1]

            btnPrev.visibility = if (currentStep > 1) View.VISIBLE else View.GONE
            spacer.visibility = if (currentStep > 1) View.VISIBLE else View.GONE

            when (currentStep) {
                1 -> {
                    btnNext.text = "下一步"
                    AnimeUtils.slideInFromBottom(step1, 100)
                }
                2 -> {
                    btnNext.text = "下一步"
                    AnimeUtils.slideInFromBottom(step2, 100)
                }
                3 -> {
                    btnNext.text = "创建群聊"
                    val name = etGroupName.text.toString().trim().ifEmpty { "群聊" }
                    tvPreviewName.text = name
                    val memberNames = selectedIds.mapNotNull { personaManager.getPersona(it)?.name }
                    tvPreviewMembers.text = memberNames.joinToString("、")
                    AnimeUtils.fadeInScale(step3, 100)
                }
            }

            AnimeUtils.bounceIn(dotStep1, 50)
            if (currentStep >= 2) AnimeUtils.bounceIn(dotStep2, 100)
            if (currentStep >= 3) AnimeUtils.bounceIn(dotStep3, 150)
        }

        btnNext.setOnClickListener {
            AnimeUtils.pulse(it)
            when (currentStep) {
                1 -> {
                    val name = etGroupName.text.toString().trim()
                    if (name.isBlank()) {
                        etGroupName.animate().translationXBy(-20f).setDuration(60).withEndAction {
                            etGroupName.animate().translationXBy(40f).setDuration(60).withEndAction {
                                etGroupName.animate().translationXBy(-20f).setDuration(60).withEndAction {
                                    etGroupName.animate().translationX(0f).setDuration(100).start()
                                }.start()
                            }.start()
                        }.start()
                        return@setOnClickListener
                    }
                    currentStep = 2
                    updateStepUI()
                }
                2 -> {
                    if (selectedIds.size < 2) {
                        Toast.makeText(this, "至少选择2个角色", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    currentStep = 3
                    updateStepUI()
                }
                3 -> {
                    val name = etGroupName.text.toString().trim().ifEmpty { "群聊" }
                    val relationship = etRelationship.text.toString().trim()
                    val group = GroupChat(
                        name = name,
                        memberPersonaIds = selectedIds.toList(),
                        relationshipSetting = relationship
                    )
                    groupChatManager.addGroup(group)
                    adapter.updateData(groupChatManager.getAllGroups())
                    updateEmptyState()
                    sheet.dismiss()
                    openGroupChat(group.id)
                }
            }
        }

        btnPrev.setOnClickListener {
            AnimeUtils.pulse(it)
            if (currentStep > 1) {
                currentStep--
                updateStepUI()
            }
        }

        updateStepUI()
        sheet.show()
    }

    private fun openGroupChat(groupId: String) {
        val intent = Intent(this, GroupChatActivity::class.java)
        intent.putExtra("group_id", groupId)
        startActivity(intent)
    }

    inner class GroupChatListAdapter(private var items: List<GroupChat>) :
        RecyclerView.Adapter<GroupChatListAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvGroupName: TextView = view.findViewById(R.id.tv_group_name)
            val tvGroupMembers: TextView = view.findViewById(R.id.tv_group_members)
            val tvGroupLastMsg: TextView = view.findViewById(R.id.tv_group_last_msg)
            val btnDelete: ImageView = view.findViewById(R.id.btn_delete_group)
        }

        override fun getItemCount() = items.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_group_chat, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val group = items[position]
            holder.tvGroupName.text = group.name

            val memberNames = group.memberPersonaIds.mapNotNull {
                personaManager.getPersona(it)?.name
            }
            holder.tvGroupMembers.text = memberNames.joinToString("、")
            holder.tvGroupLastMsg.text = group.lastMessagePreview.ifEmpty { "暂无消息" }

            holder.itemView.setOnClickListener {
                AnimeUtils.pulse(it)
                openGroupChat(group.id)
            }

            holder.btnDelete.setOnClickListener {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this@GroupChatListActivity)
                    .setTitle("删除群聊")
                    .setMessage("确定删除「${group.name}」吗？聊天记录将被清除。")
                    .setPositiveButton("删除") { _, _ ->
                        groupChatManager.deleteGroup(group.id)
                        updateData(groupChatManager.getAllGroups())
                        updateEmptyState()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }

        fun updateData(newItems: List<GroupChat>) {
            items = newItems
            notifyDataSetChanged()
        }
    }

    inner class MemberSelectAdapter(
        private val personas: List<com.aicompanion.persona.Persona>,
        private val selectedIds: MutableSet<String>,
        private val onCountChanged: (Int) -> Unit
    ) : RecyclerView.Adapter<MemberSelectAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val ivAvatar: ImageView = view.findViewById(R.id.iv_member_avatar)
            val tvName: TextView = view.findViewById(R.id.tv_member_name)
            val cardCheck: MaterialCardView = view.findViewById(R.id.card_checkbox)
            val ivCheck: ImageView = view.findViewById(R.id.iv_check)
        }

        override fun getItemCount() = personas.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_member_select, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val persona = personas[position]
            holder.tvName.text = persona.name

            val avatarPath = persona.avatarPath
            if (avatarPath.isNotBlank() && File(avatarPath).exists()) {
                try {
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(avatarPath, opts)
                    opts.inSampleSize = calculateSampleSize(opts.outWidth, opts.outHeight, 64, 64)
                    opts.inJustDecodeBounds = false
                    val bmp = BitmapFactory.decodeFile(avatarPath, opts)
                    holder.ivAvatar.setImageBitmap(bmp)
                } catch (_: Exception) {}
            }

            val isSelected = selectedIds.contains(persona.id)
            updateCheckState(holder, isSelected)

            holder.itemView.setOnClickListener {
                AnimeUtils.pulse(it)
                if (selectedIds.contains(persona.id)) {
                    selectedIds.remove(persona.id)
                    updateCheckState(holder, false)
                } else {
                    selectedIds.add(persona.id)
                    updateCheckState(holder, true)
                    AnimeUtils.springScale(holder.cardCheck, 0.7f, 1f, 300)
                }
                onCountChanged(selectedIds.size)
            }
        }

        private fun updateCheckState(holder: VH, selected: Boolean) {
            if (selected) {
                holder.ivCheck.visibility = View.VISIBLE
                holder.cardCheck.setCardBackgroundColor(0xFF667eea.toInt())
                holder.cardCheck.strokeColor = 0xFF667eea.toInt()
            } else {
                holder.ivCheck.visibility = View.GONE
                holder.cardCheck.setCardBackgroundColor(0xFF2a2a4a.toInt())
                holder.cardCheck.strokeColor = 0xFF3a3a5a.toInt()
            }
        }
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

    private fun generateRelationshipWithAI(
        personas: List<com.aicompanion.persona.Persona>,
        selectedIds: List<String>
    ): String? {
        val sm = SettingsManager(this)
        if (sm.chatApiUrl.isBlank()) return null

        val targetPersonas = if (selectedIds.isNotEmpty()) {
            personas.filter { it.id in selectedIds }
        } else {
            personas
        }

        if (targetPersonas.size < 2) return null

        val personaDescs = targetPersonas.map { p ->
            val identity = PromptBuilder.buildIdentity(this, p.id)
            val prefs = getSharedPreferences("persona_data_${p.id}", MODE_PRIVATE)
            buildString {
                append("「${identity.name}」性格${identity.personality}。${identity.speechStyle}。")
                prefs.getString("persona_appearance", "")?.takeIf { it.isNotBlank() }?.let { append(" 外貌：$it。") }
                prefs.getString("world_relationship", "")?.takeIf { it.isNotBlank() }?.let { append(" 关系：$it。") }
            }
        }.joinToString("\n")

        val prompt = buildString {
            append("根据以下角色设定，生成他们之间的群聊关系设定。\n\n")
            append("【角色设定】\n$personaDescs\n\n")
            append("请生成角色之间的关系网络，包括：\n")
            append("- 角色之间的互相称呼和关系（朋友、恋人、师徒、对手等）\n")
            append("- 角色与用户的关系\n")
            append("- 角色之间的互动方式（谁更主动、谁更害羞等）\n")
            append("- 有趣的关系冲突或张力\n\n")
            append("直接输出关系设定文本，100-200字，不要加标题。")
        }

        val client = ApiClient(sm.chatApiUrl, sm.chatApiKey, sm.chatModel,
            sm.llmTemperature, sm.llmTopP, sm.llmFrequencyPenalty, sm.llmPresencePenalty, sm.llmMaxTokens,
            sm.apiProvider)

        return try {
            val response = client.sendSimplePrompt(prompt, "生成关系设定")
            response?.text?.trim()
        } catch (e: Exception) {
            null
        }
    }
}
