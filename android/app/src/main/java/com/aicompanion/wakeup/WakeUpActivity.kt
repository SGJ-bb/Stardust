package com.aicompanion.wakeup

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aicompanion.R
import com.google.android.material.floatingactionbutton.FloatingActionButton

class WakeUpActivity : AppCompatActivity() {

    private lateinit var taskManager: WakeUpTaskManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TaskAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wakeup)

        taskManager = WakeUpTaskManager(this)
        taskManager.load()

        recyclerView = findViewById(R.id.rv_wakeup_tasks)
        adapter = TaskAdapter(taskManager.getAllTasks())
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        findViewById<FloatingActionButton>(R.id.fab_add_task).setOnClickListener {
            com.aicompanion.anim.AnimeUtils.pulse(it)
            showAddTaskDialog()
        }

        findViewById<View>(R.id.btn_back_wakeup).setOnClickListener {
            com.aicompanion.anim.AnimeUtils.pulse(it)
            finish()
        }

        applyTheme()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun refreshList() {
        adapter.updateData(taskManager.getAllTasks())
    }

    private fun applyTheme() {
        val scheme = com.aicompanion.theme.ThemeManager.getCurrentScheme(this)
        try {
            findViewById<View>(R.id.wakeup_root)?.setBackgroundColor(android.graphics.Color.parseColor(scheme.backgroundDark))
        } catch (_: Exception) {}
    }

    private fun showAddTaskDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_wakeup_task, null)
        val etName = view.findViewById<EditText>(R.id.et_task_name)
        val etDesc = view.findViewById<EditText>(R.id.et_task_desc)
        var selectedHour = 9
        var selectedMinute = 0

        val btnTime = view.findViewById<View>(R.id.btn_pick_time)
        val tvTime = view.findViewById<TextView>(R.id.tv_selected_time)
        btnTime.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                selectedHour = h
                selectedMinute = m
                tvTime.text = String.format("%02d:%02d", h, m)
            }, selectedHour, selectedMinute, true).show()
        }

        AlertDialog.Builder(this)
            .setTitle("新建唤醒任务")
            .setView(view)
            .setPositiveButton("创建") { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(this, "请输入任务名", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val task = WakeUpTask(
                    name = name,
                    description = etDesc.text.toString().trim(),
                    hour = selectedHour,
                    minute = selectedMinute,
                    enabled = true
                )
                taskManager.addTask(task)
                refreshList()
                Toast.makeText(this, "任务「$name」已创建", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showEditTaskDialog(task: WakeUpTask) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_wakeup_task, null)
        val etName = view.findViewById<EditText>(R.id.et_task_name)
        val etDesc = view.findViewById<EditText>(R.id.et_task_desc)
        var selectedHour = task.hour
        var selectedMinute = task.minute

        etName.setText(task.name)
        etDesc.setText(task.description)

        val btnTime = view.findViewById<View>(R.id.btn_pick_time)
        val tvTime = view.findViewById<TextView>(R.id.tv_selected_time)
        tvTime.text = String.format("%02d:%02d", task.hour, task.minute)
        btnTime.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                selectedHour = h
                selectedMinute = m
                tvTime.text = String.format("%02d:%02d", h, m)
            }, selectedHour, selectedMinute, true).show()
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("编辑任务")
            .setView(view)
            .setPositiveButton("保存") { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(this, "请输入任务名", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                taskManager.updateTask(task.id) {
                    it.copy(name = name, description = etDesc.text.toString().trim(), hour = selectedHour, minute = selectedMinute)
                }
                refreshList()
            }
            .setNegativeButton("取消", null)

        if (!task.isDefault) {
            dialog.setNeutralButton("删除") { _, _ ->
                taskManager.deleteTask(task.id)
                refreshList()
                Toast.makeText(this, "任务已删除", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    inner class TaskAdapter(private var items: List<WakeUpTask>) : RecyclerView.Adapter<TaskAdapter.VH>() {
        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tv_task_name)
            val tvDesc: TextView = view.findViewById(R.id.tv_task_desc)
            val tvTime: TextView = view.findViewById(R.id.tv_task_time)
            val switchEnabled: Switch = view.findViewById(R.id.switch_task_enabled)
            val btnEdit: View = view.findViewById(R.id.btn_edit_task)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(this@WakeUpActivity)
                .inflate(R.layout.item_wakeup_task, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val task = items[position]
            holder.tvName.text = task.name
            holder.tvDesc.text = task.description.ifBlank { "暂无描述" }
            holder.tvTime.text = String.format("%02d:%02d", task.hour, task.minute)
            holder.switchEnabled.setOnCheckedChangeListener(null)
            holder.switchEnabled.isChecked = task.enabled

            holder.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                taskManager.toggleTask(task.id, isChecked)
            }

            holder.btnEdit.setOnClickListener {
                showEditTaskDialog(task)
            }

            holder.itemView.setOnLongClickListener {
                if (!task.isDefault) {
                    AlertDialog.Builder(this@WakeUpActivity)
                        .setTitle("删除任务")
                        .setMessage("确定要删除「${task.name}」吗？")
                        .setPositiveButton("删除") { _, _ ->
                            taskManager.deleteTask(task.id)
                            refreshList()
                        }
                        .setNegativeButton("取消", null)
                        .show()
                }
                true
            }
        }

        override fun getItemCount() = items.size

        fun updateData(newItems: List<WakeUpTask>) {
            items = newItems
            notifyDataSetChanged()
        }
    }
}
