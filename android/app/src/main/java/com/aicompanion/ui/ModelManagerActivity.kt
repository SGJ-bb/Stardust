/** 模型管理页: Live2D模型下载/切换/删除/列表展示 */
package com.aicompanion.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aicompanion.R
import com.aicompanion.models.Live2DModel
import com.aicompanion.live2d.ModelManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class ModelManagerActivity : Activity() {

    private var modelManager: ModelManager? = null
    private var isDestroyed = false

    private var recyclerModels: RecyclerView? = null
    private var tvEmptyModels: TextView? = null
    private var ivCurrentPreview: ImageView? = null
    private var tvCurrentName: TextView? = null
    private var tvCurrentDesc: TextView? = null
    private var tvModelVersion: TextView? = null
    private var btnImport: Button? = null
    private var btnScan: Button? = null

    private var models: List<Live2DModel> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_model_manager)

        try {
            modelManager = ModelManager(this)
            ensureModelDirs()

            initViews()
            loadModels()
            setupClickListeners()
        } catch (e: Exception) {
            Log.e(TAG, "onCreate error", e)
            Toast.makeText(this, "模型管理加载失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun ensureModelDirs() {
        try {
            val modelsDir = File(getExternalFilesDir(null), "live2d_models")
            if (!modelsDir.exists()) modelsDir.mkdirs()
            val downloadDir = File(android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS), "Live2D")
            if (!downloadDir.exists()) downloadDir.mkdirs()
            Log.d(TAG, "Model dirs ensured: ${modelsDir.absolutePath}, ${downloadDir.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "ensureModelDirs error", e)
        }
    }

    private fun initViews() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        recyclerModels = findViewById(R.id.recycler_models)
        tvEmptyModels = findViewById(R.id.tv_empty_models)
        ivCurrentPreview = findViewById(R.id.iv_current_model_preview)
        tvCurrentName = findViewById(R.id.tv_current_model_name)
        tvCurrentDesc = findViewById(R.id.tv_current_model_desc)
        tvModelVersion = findViewById(R.id.tv_model_version)
        btnImport = findViewById(R.id.btn_import_model)
        btnScan = findViewById(R.id.btn_scan_models)

        recyclerModels?.layoutManager = LinearLayoutManager(this)
    }

    private fun loadModels() {
        val mm = modelManager ?: return
        try {
            models = mm.getAllModels()
            val currentModel = mm.getCurrentModel()

            tvCurrentName?.text = currentModel.name
            tvCurrentDesc?.text = currentModel.description
            tvModelVersion?.text = "v${currentModel.version}"

            val otherModels = models.filter { it.id != currentModel.id }

            if (otherModels.isEmpty()) {
                recyclerModels?.visibility = android.view.View.GONE
                tvEmptyModels?.visibility = android.view.View.VISIBLE
            } else {
                recyclerModels?.visibility = android.view.View.VISIBLE
                tvEmptyModels?.visibility = android.view.View.GONE
                recyclerModels?.adapter = ModelAdapter(otherModels, mm) {
                    if (!isDestroyed) loadModels()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadModels error", e)
        }
    }

    private fun setupClickListeners() {
        btnImport?.setOnClickListener {
            showImportDialog()
        }

        btnScan?.setOnClickListener {
            try {
                val found = scanModelsLocal()
                if (found.isNotEmpty()) {
                    Toast.makeText(this, "找到 ${found.size} 个模型", Toast.LENGTH_SHORT).show()
                    loadModels()
                } else {
                    Toast.makeText(this, "未找到新模型\n请将模型文件夹放入 Download/Live2D/ 目录", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "scan error", e)
                Toast.makeText(this, "扫描失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showImportDialog() {
        val options = arrayOf("选择模型文件夹（推荐）", "选择 .zip 压缩包", "选择 .model3.json 文件")
        android.app.AlertDialog.Builder(this)
            .setTitle("导入模型")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openFolderPicker()
                    1 -> openFilePicker("zip")
                    2 -> openFilePicker("json")
                }
            }
            .show()
    }

    private fun openFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        try {
            startActivityForResult(intent, REQUEST_IMPORT_FOLDER)
        } catch (e: Exception) {
            Log.e(TAG, "openFolderPicker error", e)
            Toast.makeText(this, "无法打开文件夹选择器，请尝试其他方式", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openFilePicker(fileType: String) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            when (fileType) {
                "zip" -> {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/zip", "application/x-zip-compressed"))
                }
                else -> {
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json"))
                }
            }
        }
        try {
            startActivityForResult(intent, REQUEST_IMPORT_MODEL)
        } catch (e: Exception) {
            Log.e(TAG, "openFilePicker error", e)
            Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data == null) return

        when (requestCode) {
            REQUEST_IMPORT_MODEL -> {
                data.data?.let { importModelFromUri(it) }
            }
            REQUEST_IMPORT_FOLDER -> {
                data.data?.let { importModelFromFolder(it) }
            }
        }
    }

    private fun getFileName(uri: Uri): String {
        var name = "unknown"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }

    private fun importModelFromFolder(treeUri: android.net.Uri) {
        try {
            val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val treeDocUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)

            val modelsDir = File(getExternalFilesDir(null), "live2d_models")
            if (!modelsDir.exists()) modelsDir.mkdirs()

            val folderName = getFolderName(treeUri)
            val targetDir = File(modelsDir, folderName)
            if (targetDir.exists()) targetDir.deleteRecursively()
            targetDir.mkdirs()

            val copiedFiles = copyFilesFromTree(treeDocUri, targetDir)
            Log.d(TAG, "Imported $copiedFiles files from folder")

            val modelJsonFile = findModelJson(targetDir)
            if (modelJsonFile != null) {
                registerImportedModel(folderName, modelJsonFile, targetDir)
                Toast.makeText(this, "模型导入成功: $folderName", Toast.LENGTH_LONG).show()
                loadModels()
            } else {
                val files = targetDir.listFiles()?.map { it.name }?.joinToString(", ") ?: "empty"
                Toast.makeText(this, "未找到 .model3.json 文件\n目录内容: $files", Toast.LENGTH_LONG).show()
                targetDir.deleteRecursively()
            }
        } catch (e: Exception) {
            Log.e(TAG, "importModelFromFolder error", e)
            Toast.makeText(this, "导入失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun getFolderName(treeUri: android.net.Uri): String {
        val path = treeUri.path ?: return "imported_model"
        val lastSegment = path.substringAfterLast("/")
        if (lastSegment.isNotEmpty() && lastSegment != "tree") return lastSegment
        return "imported_model_${System.currentTimeMillis() % 10000}"
    }

    private fun copyFilesFromTree(treeDocUri: android.net.Uri, targetDir: File): Int {
        var count = 0
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        try {
            contentResolver.query(treeDocUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

                while (cursor.moveToNext()) {
                    val docId = cursor.getString(idCol)
                    val name = cursor.getString(nameCol)
                    val mime = cursor.getString(mimeCol)

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        val childDocUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeDocUri, docId)
                        val subDir = File(targetDir, name)
                        subDir.mkdirs()
                        count += copyFilesFromTree(childDocUri, subDir)
                    } else {
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeDocUri, docId)
                        val targetFile = File(targetDir, name)
                        try {
                            contentResolver.openInputStream(docUri)?.use { input ->
                                targetFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            count++
                        } catch (e: Exception) {
                            Log.e(TAG, "Copy file error: $name", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "copyFilesFromTree query error", e)
        }
        return count
    }

    private fun registerImportedModel(name: String, modelJsonFile: File, targetDir: File) {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        prefs.edit().putString("active_model_path", modelJsonFile.absolutePath).apply()

        val mm = modelManager ?: return
        val model = Live2DModel(
            id = name,
            name = name,
            description = "用户导入模型",
            modelPath = modelJsonFile.absolutePath,
            texturePath = "",
            physicsPath = "",
            motionPath = "",
            version = "3",
            sizeMB = targetDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } / 1024f / 1024f,
            isActive = true
        )
        mm.setActiveModel(name)
        mm.addModel(model)
    }

    private fun importModelFromUri(uri: Uri) {
        try {
            val fileName = getFileName(uri)
            Log.d(TAG, "Importing: $fileName from URI: $uri")

            val modelsDir = File(getExternalFilesDir(null), "live2d_models")
            if (!modelsDir.exists()) modelsDir.mkdirs()

            val ext = fileName.substringAfterLast(".", "").lowercase()
            val baseName = fileName.substringBeforeLast(".")

            val targetDir = File(modelsDir, baseName)
            if (targetDir.exists()) targetDir.deleteRecursively()
            targetDir.mkdirs()

            if (ext == "zip") {
                importZip(uri, targetDir)
            } else if (ext == "json") {
                importJsonModel(uri, fileName, targetDir)
                return
            } else {
                Toast.makeText(this, "不支持的文件格式: .$ext\n请选择 .model3.json 或 .zip 文件", Toast.LENGTH_LONG).show()
                targetDir.deleteRecursively()
                return
            }

            val modelJsonFile = findModelJson(targetDir)
            if (modelJsonFile != null) {
                val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                prefs.edit().putString("active_model_path", modelJsonFile.absolutePath).apply()

                val mm = modelManager ?: return
                val model = Live2DModel(
                    id = baseName,
                    name = baseName,
                    description = "用户导入模型",
                    modelPath = modelJsonFile.absolutePath,
                    texturePath = "",
                    physicsPath = "",
                    motionPath = "",
                    version = "3",
                    sizeMB = targetDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } / 1024f / 1024f,
                    isActive = true
                )
                mm.setActiveModel(baseName)
                mm.addModel(model)

                Toast.makeText(this, "模型导入成功: $baseName", Toast.LENGTH_LONG).show()
                loadModels()
            } else {
                val files = targetDir.listFiles()?.map { it.name }?.joinToString(", ") ?: "empty"
                Toast.makeText(this, "未找到有效的模型文件\n目录内容: $files\n需要 .model3.json + .moc3 + 纹理文件", Toast.LENGTH_LONG).show()
                targetDir.deleteRecursively()
            }
        } catch (e: Exception) {
            Log.e(TAG, "importModelFromUri error", e)
            Toast.makeText(this, "导入失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun importJsonModel(uri: Uri, fileName: String, targetDir: File) {
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                val targetFile = File(targetDir, fileName)
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            try {
                val jsonContent = File(targetDir, fileName).readText()
                val json = org.json.JSONObject(jsonContent)
                val fileRefs = json.optJSONObject("FileReferences") ?: json.optJSONObject("file_references")
                if (fileRefs != null) {
                    val referencedFiles = mutableListOf<String>()
                    listOf("Moc", "moc").forEach { key ->
                        val f = fileRefs.optString(key)
                        if (f.isNotEmpty()) referencedFiles.add(f)
                    }
                    fileRefs.optJSONArray("Textures")?.let { arr ->
                        for (i in 0 until arr.length()) referencedFiles.add(arr.optString(i))
                    }
                    fileRefs.optJSONArray("textures")?.let { arr ->
                        for (i in 0 until arr.length()) referencedFiles.add(arr.optString(i))
                    }
                    listOf("Physics", "physics", "DisplayInfo", "displayInfo").forEach { key ->
                        val f = fileRefs.optString(key)
                        if (f.isNotEmpty()) referencedFiles.add(f)
                    }
                    fileRefs.optJSONArray("Expressions")?.let { arr ->
                        for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i)
                            val f = obj?.optString("File", obj.optString("file", "")) ?: ""
                            if (f.isNotEmpty()) referencedFiles.add(f)
                        }
                    }
                    fileRefs.optJSONObject("Motions")?.let { motions ->
                        motions.keys().forEach { group ->
                            motions.optJSONArray(group)?.let { arr ->
                                for (i in 0 until arr.length()) {
                                    val obj = arr.optJSONObject(i)
                                    val f = obj?.optString("File", obj.optString("file", "")) ?: ""
                                    if (f.isNotEmpty()) referencedFiles.add(f)
                                    val s = obj?.optString("Sound", obj.optString("sound", "")) ?: ""
                                    if (s.isNotEmpty()) referencedFiles.add(s)
                                }
                            }
                        }
                    }

                    val parentDir = uri.path?.let { File(it).parentFile }
                    var copiedCount = 0
                    for (ref in referencedFiles) {
                        val refFile = File(targetDir, ref)
                        if (!refFile.exists()) {
                            val refFileName = File(ref).name
                            val foundInParent = parentDir?.walkTopDown()
                                ?.filter { it.isFile && it.name == refFileName }
                                ?.firstOrNull()
                            if (foundInParent != null) {
                                refFile.parentFile?.mkdirs()
                                foundInParent.copyTo(refFile, overwrite = true)
                                copiedCount++
                            }
                        }
                    }
                    if (copiedCount > 0) {
                        Log.d(TAG, "Auto-copied $copiedCount referenced file(s) alongside JSON")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not parse JSON for auto-copy: ${e.message}")
            }

            val modelJsonFile = findModelJson(targetDir)
            if (modelJsonFile != null) {
                val mm = modelManager ?: return
                val name = targetDir.name
                val model = Live2DModel(
                    id = name,
                    name = name,
                    description = "用户导入模型（仅JSON）",
                    modelPath = modelJsonFile.absolutePath,
                    texturePath = "",
                    physicsPath = "",
                    motionPath = "",
                    version = "3",
                    sizeMB = targetDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } / 1024f / 1024f,
                    isActive = true
                )
                mm.setActiveModel(name)
                mm.addModel(model)
                val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                prefs.edit().putString("active_model_path", modelJsonFile.absolutePath).apply()
                Toast.makeText(this, "模型导入成功（部分文件可能缺失，建议使用文件夹导入）", Toast.LENGTH_LONG).show()
                loadModels()
            } else {
                Toast.makeText(this, "仅导入了 .json 文件，缺少 .moc3 和纹理等关联文件\n\n请使用「选择模型文件夹」方式导入完整模型", Toast.LENGTH_LONG).show()
                targetDir.deleteRecursively()
            }
        } catch (e: Exception) {
            Log.e(TAG, "importJsonModel error", e)
            Toast.makeText(this, "导入失败: ${e.message}", Toast.LENGTH_LONG).show()
            targetDir.deleteRecursively()
        }
    }

    private fun importZip(uri: Uri, targetDir: File) {
        val tempZip = File(targetDir, "_temp.zip")
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                tempZip.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            unzip(tempZip, targetDir)
        } finally {
            if (tempZip.exists()) tempZip.delete()
        }
    }

    private fun unzip(zipFile: File, targetDir: File) {
        java.util.zip.ZipFile(zipFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val outFile = File(targetDir, entry.name)
                if (!outFile.canonicalPath.startsWith(targetDir.canonicalPath + File.separator)) {
                    return@forEach
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        outFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }

    private fun scanModelsLocal(): List<File> {
        val found = mutableListOf<File>()
        val searchDirs = mutableListOf<File>()

        searchDirs.add(File(getExternalFilesDir(null), "live2d_models"))
        searchDirs.add(File(android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS), "Live2D"))

        searchDirs.forEach { searchDir ->
            try {
                if (searchDir.exists() && searchDir.isDirectory) {
                    scanDirectoryRecursive(searchDir, found)
                }
            } catch (e: Exception) {
                Log.e(TAG, "scan dir error: ${searchDir.absolutePath}", e)
            }
        }

        for (modelFile in found) {
            try {
                val modelDir = modelFile.parentFile ?: continue
                val dirName = modelDir.name
                val targetDir = File(getExternalFilesDir(null), "live2d_models/$dirName")

                val externalDir = getExternalFilesDir(null)
                if (externalDir != null && modelDir.absolutePath.startsWith(externalDir.absolutePath)) {
                    val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                    prefs.edit().putString("active_model_path", modelFile.absolutePath).apply()
                    val mm = modelManager ?: continue
                    mm.setActiveModel(dirName)
                    continue
                }

                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                    modelDir.walkTopDown().filter { it.isFile }.forEach { srcFile ->
                        val relPath = srcFile.absolutePath.substring(modelDir.absolutePath.length).trimStart('/', '\\')
                        val dstFile = File(targetDir, relPath)
                        dstFile.parentFile?.mkdirs()
                        FileInputStream(srcFile).use { input ->
                            FileOutputStream(dstFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }

                    val copiedModelJson = findModelJson(targetDir)
                    if (copiedModelJson != null) {
                        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                        prefs.edit().putString("active_model_path", copiedModelJson.absolutePath).apply()
                        val mm = modelManager ?: continue
                        val model = Live2DModel(
                            id = dirName,
                            name = dirName,
                            description = "扫描导入模型",
                            modelPath = copiedModelJson.absolutePath,
                            texturePath = "",
                            physicsPath = "",
                            motionPath = "",
                            version = "3",
                            sizeMB = targetDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } / 1024f / 1024f,
                            isActive = true
                        )
                        mm.setActiveModel(dirName)
                        mm.addModel(model)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "process model error", e)
            }
        }

        return found
    }

    private fun scanDirectoryRecursive(dir: File, found: MutableList<File>) {
        try {
            dir.listFiles()?.forEach { file ->
                if (file.isFile && (file.name.endsWith(".model3.json") || file.name.endsWith(".model.json"))) {
                    val moc3File = File(file.parentFile, file.name.replace(".model3.json", ".moc3").replace(".model.json", ".moc"))
                    val hasMoc = moc3File.exists() || file.parentFile?.listFiles()?.any {
                        it.name.endsWith(".moc3") || it.name.endsWith(".moc")
                    } == true
                    if (hasMoc) {
                        found.add(file)
                        return
                    }
                }
                if (file.isDirectory) {
                    scanDirectoryRecursive(file, found)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "scanDirectoryRecursive error", e)
        }
    }

    private fun findModelJson(dir: File): File? {
        var result: File? = null
        dir.walkTopDown().forEach { file ->
            if (file.isFile && (file.name.endsWith(".model3.json") || file.name.endsWith(".model.json"))) {
                if (result == null) result = file
            }
        }
        return result
    }

    override fun onDestroy() {
        isDestroyed = true
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ModelManagerActivity"
        const val REQUEST_IMPORT_MODEL = 1001
        const val REQUEST_IMPORT_FOLDER = 1002
    }
}

class ModelAdapter(
    private val models: List<Live2DModel>,
    private val modelManager: ModelManager,
    private val onRefresh: () -> Unit
) : RecyclerView.Adapter<ModelAdapter.ModelViewHolder>() {

    class ModelViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val ivPreview: ImageView = view.findViewById(R.id.iv_model_preview)
        val tvName: TextView = view.findViewById(R.id.tv_model_name)
        val tvDesc: TextView = view.findViewById(R.id.tv_model_desc)
        val tvSize: TextView = view.findViewById(R.id.tv_model_size)
        val btnUse: Button = view.findViewById(R.id.btn_use_model)
        val btnEdit: Button = view.findViewById(R.id.btn_edit_model)
        val btnDelete: Button = view.findViewById(R.id.btn_delete_model)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ModelViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_model, parent, false)
        return ModelViewHolder(view)
    }

    override fun onBindViewHolder(holder: ModelViewHolder, position: Int) {
        val model = models.getOrNull(position) ?: return

        holder.tvName.text = model.name
        holder.tvDesc.text = model.description
        holder.tvSize.text = String.format("%.1f MB", model.sizeMB)

        holder.btnUse.setOnClickListener {
            try {
                modelManager.setActiveModel(model.id)
                val prefs = holder.itemView.context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                val path = model.modelPath
                if (path.startsWith("file:///android_asset/") || path.startsWith("assets/")) {
                    prefs.edit().remove("active_model_path").apply()
                } else {
                    prefs.edit().putString("active_model_path", path).apply()
                }
                onRefresh()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        holder.btnEdit.setOnClickListener {
            try {
                val intent = Intent(holder.itemView.context, ModelSettingsActivity::class.java)
                intent.putExtra("model_id", model.id)
                holder.itemView.context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        holder.btnDelete.setOnClickListener {
            android.app.AlertDialog.Builder(holder.itemView.context)
                .setTitle("删除模型")
                .setMessage("确定要删除 ${model.name} 吗？")
                .setPositiveButton("删除") { _, _ ->
                    try {
                        if (modelManager.removeModel(model.id)) {
                            onRefresh()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    override fun getItemCount(): Int = models.size
}
