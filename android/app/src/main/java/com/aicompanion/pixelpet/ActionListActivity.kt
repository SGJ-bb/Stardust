/** 动作列表 Activity: 查看宠物所有动作、编辑动作、生成帧图 */
package com.aicompanion.pixelpet

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aicompanion.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ActionListActivity : AppCompatActivity() {

    private lateinit var petManager: PixelPetManager
    private var petId: String = ""
    private var petName: String = ""
    private var actions = listOf<PetAction>()

    companion object {
        const val TAG = "ActionListActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_action_list)

        petId = intent.getStringExtra("pet_id") ?: return finish()
        petName = intent.getStringExtra("pet_name") ?: "未知宠物"
        petManager = PixelPetManager(this)

        supportActionBar?.title = "${petName} 的动作列表"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val btnAddAction = findViewById<Button>(R.id.btn_add_action)
        btnAddAction.setOnClickListener { showCreateActionDialog() }

        loadActions()
    }

    private fun loadActions() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                actions = petManager.getActionsForPet(petId)

                withContext(Dispatchers.Main) {
                    if (actions.isEmpty()) {
                        findViewById<View>(R.id.layout_action_content).visibility = View.GONE
                        findViewById<TextView>(R.id.tv_empty_actions).visibility = View.VISIBLE
                    } else {
                        findViewById<View>(R.id.layout_action_content).visibility = View.VISIBLE
                        findViewById<TextView>(R.id.tv_empty_actions).visibility = View.GONE

                        val container = findViewById<LinearLayout>(R.id.layout_action_list_container)
                        container.removeAllViews()

                        for (action in actions) {
                            val view = createActionItemView(action)
                            container.addView(view)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "加载动作失败", e)
            }
        }
    }

    private fun createActionItemView(action: PetAction): View {
        val item = layoutInflater.inflate(R.layout.item_action_card, null)
        val tvName = item.findViewById<TextView>(R.id.tv_action_name)
        val tvInfo = item.findViewById<TextView>(R.id.tv_action_info)
        val tvFrames = item.findViewById<TextView>(R.id.tv_frame_count)
        val btnEdit = item.findViewById<Button>(R.id.btn_edit_action)
        val btnGenerate = item.findViewById<Button>(R.id.btn_generate_frames)

        tvName.text = action.displayName
        tvInfo.text = "${action.loopMode} · ${action.frameDuration}ms/帧"

        val readyCount = action.frames.count { it.status == FrameStatus.READY }
        tvFrames.text = "$readyCount/${action.frameCount} 帧"

        btnEdit.setOnClickListener { showEditActionDialog(action) }
        btnGenerate.setOnClickListener { generateFrames(action) }

        return item
    }

    private fun showCreateActionDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_action, null)
        val etName = dialogView.findViewById<EditText>(R.id.et_action_name)
        val etPrompt = dialogView.findViewById<EditText>(R.id.et_action_prompt)
        val etFrameCount = dialogView.findViewById<EditText>(R.id.et_frame_count)
        val spinnerLoopMode = dialogView.findViewById<Spinner>(R.id.spinner_loop_mode)

        val modes = arrayOf("循环(loop)", "一次性(once)", "往返(pingpong)")
        val adapter = ArrayAdapter(this, R.layout.spinner_item_dark, modes)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_dark)
        spinnerLoopMode.adapter = adapter

        AlertDialog.Builder(this)
            .setTitle("新建动作")
            .setView(dialogView)
            .setPositiveButton("创建") { _, _ ->
                val name = etName.text.toString().trim()
                val prompt = etPrompt.text.toString().trim()
                val count = etFrameCount.text.toString().toIntOrNull() ?: 4
                val modeIdx = spinnerLoopMode.selectedItemPosition
                val mode = when (modeIdx) {
                    1 -> LoopMode.ONCE
                    2 -> LoopMode.PINGPONG
                    else -> LoopMode.LOOP
                }

                if (name.isEmpty()) {
                    Toast.makeText(this, "请输入动作名称", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        petManager.createAction(
                            petId = petId,
                            name = name.lowercase().replace(" ", "_"),
                            displayName = name,
                            prompt = prompt.ifEmpty { "$name animation" },
                            frameCount = count,
                            loopMode = mode,
                            isBuiltin = false
                        )
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@ActionListActivity, "动作「${name}」已创建", Toast.LENGTH_SHORT).show()
                            loadActions()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "创建动作失败", e)
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showEditActionDialog(action: PetAction) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_action, null)
        val etName = dialogView.findViewById<EditText>(R.id.et_action_name)
        val etPrompt = dialogView.findViewById<EditText>(R.id.et_action_prompt)
        val etFrameCount = dialogView.findViewById<EditText>(R.id.et_frame_count)
        val spinnerLoopMode = dialogView.findViewById<Spinner>(R.id.spinner_loop_mode)

        etName.setText(action.displayName)
        etPrompt.setText(action.prompt)
        etFrameCount.setText(action.frameCount.toString())

        val modes = arrayOf("循环(loop)", "一次性(once)", "往返(pingpong)")
        val adapter = ArrayAdapter(this, R.layout.spinner_item_dark, modes)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_dark)
        spinnerLoopMode.adapter = adapter
        spinnerLoopMode.setSelection(
            when (action.loopMode) {
                LoopMode.ONCE -> 1
                LoopMode.PINGPONG -> 2
                else -> 0
            }
        )

        AlertDialog.Builder(this)
            .setTitle("编辑动作: ${action.displayName}")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val newDisplayName = etName.text.toString().trim()
                val newPrompt = etPrompt.text.toString().trim()
                val newFrameCount = etFrameCount.text.toString().toIntOrNull() ?: action.frameCount
                val modeIdx = spinnerLoopMode.selectedItemPosition
                val newMode = when (modeIdx) {
                    1 -> LoopMode.ONCE
                    2 -> LoopMode.PINGPONG
                    else -> LoopMode.LOOP
                }

                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val updated = action.copy(
                            displayName = newDisplayName,
                            prompt = newPrompt.ifEmpty { action.prompt },
                            frameCount = newFrameCount,
                            loopMode = newMode
                        )
                        petManager.saveAction(updated)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@ActionListActivity, "已更新", Toast.LENGTH_SHORT).show()
                            loadActions()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "更新动作失败", e)
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun generateFrames(action: PetAction) {
        val genConfig = petManager.getGenConfig()
        if (genConfig.apiUrl.isBlank() || genConfig.apiKey.isBlank()) {
            Toast.makeText(this, "请先在设置中配置图片生成API", Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(this, "开始生成帧图（${action.frameCount}帧），请稍候...", Toast.LENGTH_LONG).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val client = ImageGenClient()
                for (i in 0 until action.frameCount) {
                    // buildFramePrompt 参数名是 stylePrompt（不是 styleModifier）
                    val prompt = petManager.buildFramePrompt(
                        basePrompt = petManager.getActivePet()?.basePrompt ?: "",
                        actionPrompt = action.prompt,
                        frameIndex = i,
                        totalFrames = action.frameCount,
                        stylePrompt = genConfig.stylePrompt
                    )

                    // generate() 返回 ByteArray，异常时直接抛出
                    val bytes = client.generate(genConfig, prompt)
                    val path = petManager.saveFrameImage(petId, action.id, i, bytes)
                    petManager.updateFrameStatus(petId, action.id, i, FrameStatus.READY, path)
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ActionListActivity, "帧图生成完成！", Toast.LENGTH_SHORT).show()
                    loadActions()
                }
            } catch (e: Exception) {
                Log.e(TAG, "生成帧图异常", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ActionListActivity, "生成失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
