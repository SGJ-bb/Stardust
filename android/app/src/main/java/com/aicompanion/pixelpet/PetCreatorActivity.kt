/** 像素宠物管理 Activity: 创建/编辑/删除宠物 + 切换模式 */
package com.aicompanion.pixelpet

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aicompanion.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import java.io.File
import java.io.FileOutputStream

class PetCreatorActivity : AppCompatActivity() {

    private lateinit var petManager: PixelPetManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvModeStatus: TextView
    private lateinit var btnToggleMode: Button
    private lateinit var btnCreatePet: Button
    private lateinit var layoutEmpty: View
    private lateinit var tvActivePetName: TextView
    private var pets = listOf<PixelPet>()
    private var pickedImagePath: String? = null

    companion object {
        private const val TAG = "PetCreatorActivity"
        const val REQUEST_PICK_IMAGE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pet_creator)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "像素宠物管理"

        petManager = PixelPetManager(this)

        tvModeStatus = findViewById(R.id.tv_pixel_mode_status) ?: return finish()
        btnToggleMode = findViewById(R.id.btn_toggle_pixel_mode) ?: return finish()
        btnCreatePet = findViewById(R.id.btn_create_pet) ?: return finish()
        layoutEmpty = findViewById(R.id.layout_empty_pets) ?: return finish()
        tvActivePetName = findViewById(R.id.tv_active_pet_name) ?: return finish()
        recyclerView = findViewById(R.id.recycler_pet_list) ?: return finish()

        setupRecyclerView()
        setupClickListeners()
        loadData()
        applyTheme()
    }

    private fun applyTheme() {
        try {
            val scheme = com.aicompanion.theme.ThemeManager.getCurrentScheme(this)
            val tbColor = android.graphics.Color.parseColor(scheme.toolbarColor)
            findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)?.setBackgroundColor(tbColor)
                ?: findViewById<View>(R.id.toolbar_container)?.setBackgroundColor(tbColor)
            com.aicompanion.theme.ThemeManager.applyTheme(this)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "applyTheme error: ${e.message}")
        }
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun setupClickListeners() {
        btnToggleMode.setOnClickListener { toggleMode() }
        btnCreatePet.setOnClickListener { showCreateDialog() }
    }

    private fun loadData() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                pets = petManager.loadPets()
                val activePet = petManager.getActivePet()
                val mode = petManager.getPetMode()

                withContext(Dispatchers.Main) {
                    updateUI(mode, activePet)
                    updatePetList()
                    updateEmptyState()
                }
            } catch (e: Exception) {
                Log.e(TAG, "加载数据失败", e)
            }
        }
    }

    private fun updateUI(mode: String, activePet: PixelPet?) {
        tvModeStatus.text = when (mode) {
            "pixel" -> "当前模式: 像素宠物"
            else -> "当前模式: Live2D"
        }
        btnToggleMode.text = if (mode == "pixel") "切换到Live2D" else "切换到像素宠物"
        tvActivePetName.text = if (activePet != null) "活跃宠物: ${activePet.name}" else "未选择宠物"
    }

    private fun toggleMode() {
        lifecycleScope.launch(Dispatchers.IO) {
            val currentMode = petManager.getPetMode()
            val newMode = if (currentMode == "pixel") "live2d" else "pixel"
            petManager.setPetMode(newMode)
            val activePet = petManager.getActivePet()

            withContext(Dispatchers.Main) {
                updateUI(newMode, activePet)
                Toast.makeText(this@PetCreatorActivity,
                    "已切换到 ${if (newMode == "pixel") "像素宠物" else "Live2D"} 模式",
                    Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showCreateDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_pet, null)
        val etName = dialogView.findViewById<EditText>(R.id.et_pet_name) ?: return
        val etPrompt = dialogView.findViewById<EditText>(R.id.et_base_prompt) ?: return
        val ivPreview = dialogView.findViewById<ImageView>(R.id.iv_reference_preview) ?: return
        val btnPickImage = dialogView.findViewById<Button>(R.id.btn_pick_image) ?: return

        btnPickImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
            startActivityForResult(intent, REQUEST_PICK_IMAGE)
        }

        AlertDialog.Builder(this)
            .setTitle("创建新宠物")
            .setView(dialogView)
            .setPositiveButton("创建") { _, _ ->
                val name = etName.text.toString().trim()
                val prompt = etPrompt.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "请输入宠物名称", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val newPet = petManager.createPet(
                            name = name,
                            description = "",
                            referenceImagePath = pickedImagePath,
                            basePrompt = prompt.ifEmpty { "A cute pixel art character" },
                            negativePrompt = ""
                        )

                        petManager.createBuiltinActions(newPet.id,
                            listOf("idle", "walk", "jump", "happy", "sleep", "wave"))
                        petManager.setActivePet(newPet)

                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@PetCreatorActivity,
                                "「${newPet.name}」创建成功！", Toast.LENGTH_SHORT).show()
                            loadData()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "创建宠物失败", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@PetCreatorActivity,
                                "创建失败: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_IMAGE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val destFile = File(filesDir, "pixelpet_refs/${java.util.UUID.randomUUID()}.png")
                        destFile.parentFile?.mkdirs()
                        contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(destFile).use { output -> input.copyTo(output) }
                        }
                        pickedImagePath = destFile.absolutePath
                    } catch (e: Exception) {
                        Log.e(TAG, "复制图片失败", e)
                    }
                }
            }
        }
    }

    private fun activatePet(pet: PixelPet) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                petManager.setActivePet(pet)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PetCreatorActivity, "已切换到「${pet.name}」", Toast.LENGTH_SHORT).show()
                    loadData()
                }
            } catch (e: Exception) {
                Log.e(TAG, "激活宠物失败", e)
            }
        }
    }

    private fun deletePet(pet: PixelPet) {
        AlertDialog.Builder(this)
            .setTitle("删除宠物")
            .setMessage("确定删除「${pet.name}」吗？相关动作和帧图也将被删除。")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        petManager.deletePet(pet.id)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@PetCreatorActivity, "已删除「${pet.name}」", Toast.LENGTH_SHORT).show()
                            loadData()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "删除宠物失败", e)
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun editPet(pet: PixelPet) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_pet, null)
        val etName = dialogView.findViewById<EditText>(R.id.et_pet_name) ?: return
        val etPrompt = dialogView.findViewById<EditText>(R.id.et_base_prompt) ?: return

        etName.setText(pet.name)
        etPrompt.setText(pet.basePrompt)

        AlertDialog.Builder(this)
            .setTitle("编辑宠物: ${pet.name}")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val newName = etName.text.toString().trim()
                val newPrompt = etPrompt.text.toString().trim()
                if (newName.isEmpty()) return@setPositiveButton

                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val updatedPet = pet.copy(name = newName, basePrompt = newPrompt.ifEmpty { pet.basePrompt })
                        petManager.savePets(listOf(updatedPet))
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@PetCreatorActivity, "已更新", Toast.LENGTH_SHORT).show()
                            loadData()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "更新宠物失败", e)
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun updatePetList() {
        recyclerView.adapter = SimplePetAdapter(pets, ::activatePet, ::deletePet, ::editPet) { pet ->
            val intent = Intent(this, ActionListActivity::class.java).apply {
                putExtra("pet_id", pet.id)
                putExtra("pet_name", pet.name)
            }
            startActivity(intent)
        }
    }

    private fun updateEmptyState() {
        layoutEmpty.visibility = if (pets.isEmpty()) View.VISIBLE else View.GONE
        recyclerView.visibility = if (pets.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    // ═══ 简单适配器（用于 RecyclerView 模式） ═══
    private class PetVH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName = v.findViewById<TextView>(R.id.tv_pet_name)!!
        val tvInfo = v.findViewById<TextView>(R.id.tv_pet_info)!!
        val ivThumb = v.findViewById<ImageView>(R.id.iv_pet_thumb)!!
        val btnAct = v.findViewById<ImageButton>(R.id.btn_activate)!!
        val btnDel = v.findViewById<ImageButton>(R.id.btn_delete)!!
        val badge = v.findViewById<View>(R.id.view_active_badge)!!
    }

    private class SimplePetAdapter(
        private val items: List<PixelPet>,
        private val onActivate: (PixelPet) -> Unit,
        private val onDelete: (PixelPet) -> Unit,
        private val onEdit: (PixelPet) -> Unit,
        private val onClick: (PixelPet) -> Unit,
    ) : RecyclerView.Adapter<PetVH>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PetVH =
            PetVH(LayoutInflater.from(parent.context).inflate(R.layout.item_pet_card, parent, false))

        override fun onBindViewHolder(h: PetVH, pos: Int) {
            val p = items[pos]
            h.tvName.text = p.name
            h.tvInfo.text = "${p.spriteWidth}x${p.spriteHeight}px · ${p.fps}fps"
            h.badge.visibility = if (p.isActive) View.VISIBLE else View.GONE
            if (!p.referenceImagePath.isNullOrBlank()) {
                val f = File(p.referenceImagePath); if (f.exists()) h.ivThumb.setImageBitmap(BitmapFactory.decodeFile(p.referenceImagePath))
            }
            h.itemView.setOnClickListener { onClick(p) }
            h.btnAct.setOnClickListener { onActivate(p) }
            h.btnDel.setOnClickListener { onDelete(p) }
        }

        override fun getItemCount(): Int = items.size
    }
}
