package com.aicompanion.groupchat

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aicompanion.R
import com.aicompanion.anim.AnimeUtils
import com.aicompanion.persona.PersonaManager

class GroupChatListActivity : AppCompatActivity() {

    private lateinit var groupChatManager: GroupChatManager
    private lateinit var personaManager: PersonaManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: GroupChatListAdapter
    private lateinit var tvEmptyHint: TextView

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
            showCreateGroupDialog()
        }

        updateEmptyState()
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

    private fun showCreateGroupDialog() {
        val personas = personaManager.getAllPersonas()
        if (personas.size < 2) {
            Toast.makeText(this, "至少需要2个角色才能创建群聊", Toast.LENGTH_SHORT).show()
            return
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("⚠️ Token消耗提醒")
            .setMessage("群聊中每个AI角色都会独立调用LLM，Token消耗会随角色数量成倍增加。\n\n例如：3人群聊 = 每轮对话消耗3倍Token\n\n确定要创建群聊吗？")
            .setPositiveButton("继续创建") { _, _ ->
                showGroupNameDialog(personas)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showGroupNameDialog(personas: List<com.aicompanion.persona.Persona>) {
        val input = android.widget.EditText(this).apply {
            hint = "群聊名称"
            setTextColor(0xFFe0e0f0.toInt())
            setHintTextColor(0xFF556677.toInt())
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("📝 群聊名称")
            .setView(input)
            .setPositiveButton("下一步") { _, _ ->
                val name = input.text.toString().trim().ifEmpty { "群聊" }
                showMemberSelectDialog(name, personas)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showMemberSelectDialog(groupName: String, personas: List<com.aicompanion.persona.Persona>) {
        val names = personas.map { it.name }.toTypedArray()
        val checked = BooleanArray(names.size)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("👥 选择群成员")
            .setMultiChoiceItems(names, checked) { _, _, _ -> }
            .setPositiveButton("创建") { _, _ ->
                val selectedIds = mutableListOf<String>()
                checked.forEachIndexed { idx, isSelected ->
                    if (isSelected) selectedIds.add(personas[idx].id)
                }
                if (selectedIds.size < 2) {
                    Toast.makeText(this, "至少选择2个角色", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val group = GroupChat(
                    name = groupName,
                    memberPersonaIds = selectedIds
                )
                groupChatManager.addGroup(group)
                adapter.updateData(groupChatManager.getAllGroups())
                updateEmptyState()
                openGroupChat(group.id)
            }
            .setNegativeButton("取消", null)
            .show()
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
            holder.tvGroupName.text = "👥 ${group.name}"

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
}
