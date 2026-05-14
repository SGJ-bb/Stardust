/** 模型管理器: Live2D模型下载/切换/删除/列表管理 */
package com.aicompanion.live2d

import android.content.Context
import android.content.SharedPreferences
import com.aicompanion.models.Live2DModel
import com.aicompanion.models.TextureQuality
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ModelManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("model_manager", Context.MODE_PRIVATE)
    private val modelsDir = File(context.getExternalFilesDir(null), "live2d_models")

    init {
        try {
            if (!modelsDir.exists()) {
                modelsDir.mkdirs()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getAllModels(): List<Live2DModel> {
        val modelsJson = prefs.getString("models_list", null) ?: return getDefaultModels()
        return try {
            val array = JSONArray(modelsJson)
            (0 until array.length()).mapNotNull { i ->
                try {
                    parseModelFromJson(array.getJSONObject(i))
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            getDefaultModels()
        }
    }

    fun getCurrentModel(): Live2DModel {
        val models = getAllModels()
        return models.find { it.isActive } ?: models.firstOrNull() ?: getDefaultModels().first()
    }

    fun setActiveModel(modelId: String): Boolean {
        val models = getAllModels().toMutableList()
        var success = false
        models.replaceAll { model ->
            if (model.id == modelId) {
                success = true
                model.copy(isActive = true)
            } else {
                model.copy(isActive = false)
            }
        }
        if (success) {
            saveModels(models)
        }
        return success
    }

    fun addModel(model: Live2DModel) {
        val models = getAllModels().toMutableList()
        models.add(model)
        saveModels(models)
    }

    fun removeModel(modelId: String): Boolean {
        val models = getAllModels().toMutableList()
        val model = models.find { it.id == modelId } ?: return false
        if (model.isActive) return false
        models.remove(model)
        saveModels(models)
        try {
            val modelFile = File(model.modelPath)
            val dirToDelete = if (modelFile.isDirectory) modelFile else modelFile.parentFile
            dirToDelete?.deleteRecursively()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return true
    }

    fun updateModelSettings(modelId: String, textureQuality: TextureQuality, fps: Int) {
        val models = getAllModels().toMutableList()
        models.replaceAll { model ->
            if (model.id == modelId) {
                model.copy(textureQuality = textureQuality, fps = fps)
            } else {
                model
            }
        }
        saveModels(models)
    }

    fun scanForModels(): List<Live2DModel> {
        val foundModels = mutableListOf<Live2DModel>()
        val searchDirs = listOf(
            modelsDir,
            android.os.Environment.getExternalStorageDirectory(),
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        )
        searchDirs.forEach { searchDir ->
            try {
                if (searchDir != null && searchDir.exists()) {
                    scanDirectory(searchDir, foundModels)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return foundModels
    }

    private fun scanDirectory(dir: File, foundModels: MutableList<Live2DModel>) {
        try {
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    val modelFiles = file.listFiles()?.map { it.name } ?: emptyList()
                    val hasModelJson = modelFiles.any { name ->
                        name.endsWith(".model3.json") || name.endsWith(".model.json")
                    }
                    val hasMoc = modelFiles.any { name ->
                        name.endsWith(".moc3") || name.endsWith(".moc")
                    }
                    if (hasModelJson && hasMoc) {
                        val model = loadModelFromFolder(file)
                        if (model != null && foundModels.none { it.modelPath == model.modelPath }) {
                            foundModels.add(model)
                        }
                    } else {
                        scanDirectory(file, foundModels)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getDefaultModels(): List<Live2DModel> {
        return listOf(
            Live2DModel(
                id = "purple_bird",
                name = "PurpleBird",
                description = "紫鸟，默认模型",
                modelPath = "file:///android_asset/vtuber/PurpleBird/PurpleBird.model3.json",
                texturePath = "",
                physicsPath = "",
                motionPath = "",
                version = "Cubism 4",
                isActive = true
            ),
            Live2DModel(
                id = "xiaodemao",
                name = "小恶魔",
                description = "小恶魔风格，活泼可爱",
                modelPath = "file:///android_asset/vtuber/小恶魔.model3.json",
                texturePath = "",
                physicsPath = "",
                motionPath = "",
                version = "Cubism 4",
                isActive = false
            ),
            Live2DModel(
                id = "default_stardust",
                name = "星尘",
                description = "异色瞳黑猫，毒舌但关心主人",
                modelPath = "assets/models/stardust/",
                texturePath = "assets/models/stardust/texture.png",
                physicsPath = "assets/models/stardust/physics.json",
                motionPath = "assets/models/stardust/motions/",
                version = "1.0",
                isActive = false
            )
        )
    }

    private fun loadModelFromFolder(folder: File): Live2DModel? {
        return try {
            val modelJson = File(folder, "model.json")
            val json = JSONObject(modelJson.readText())
            Live2DModel(
                id = folder.name,
                name = json.optString("name", folder.name),
                description = json.optString("description", ""),
                modelPath = folder.absolutePath,
                texturePath = File(folder, json.optString("texture", "texture.png")).absolutePath,
                physicsPath = File(folder, json.optString("physics", "physics.json")).absolutePath,
                motionPath = File(folder, json.optString("motions", "motions/")).absolutePath,
                version = json.optString("version", "1.0"),
                sizeMB = folder.getFolderSizeMB()
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun File.getFolderSizeMB(): Float {
        var size = 0L
        try {
            listFiles()?.forEach { file ->
                size += if (file.isDirectory) file.getFolderSizeMB().toLong() * 1024 * 1024 else file.length()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return size / (1024f * 1024f)
    }

    private fun saveModels(models: List<Live2DModel>) {
        try {
            val array = JSONArray()
            models.forEach { model ->
                array.put(modelToJson(model))
            }
            prefs.edit().putString("models_list", array.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun modelToJson(model: Live2DModel): JSONObject {
        return JSONObject().apply {
            put("id", model.id)
            put("name", model.name)
            put("description", model.description)
            put("modelPath", model.modelPath)
            put("texturePath", model.texturePath)
            put("physicsPath", model.physicsPath)
            put("motionPath", model.motionPath)
            put("version", model.version)
            put("sizeMB", model.sizeMB)
            put("isActive", model.isActive)
            put("textureQuality", model.textureQuality.name)
            put("fps", model.fps)
        }
    }

    private fun parseModelFromJson(json: JSONObject): Live2DModel {
        return Live2DModel(
            id = json.getString("id"),
            name = json.getString("name"),
            description = json.optString("description", ""),
            modelPath = json.getString("modelPath"),
            texturePath = json.getString("texturePath"),
            physicsPath = json.getString("physicsPath"),
            motionPath = json.getString("motionPath"),
            version = json.optString("version", "1.0"),
            sizeMB = json.optDouble("sizeMB", 0.0).toFloat(),
            isActive = json.optBoolean("isActive", false),
            textureQuality = try {
                TextureQuality.valueOf(json.optString("textureQuality", "HIGH"))
            } catch (e: Exception) {
                TextureQuality.HIGH
            },
            fps = json.optInt("fps", 30)
        )
    }
}
