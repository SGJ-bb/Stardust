package com.aicompanion.album

import android.content.Context
import android.content.SharedPreferences
import com.aicompanion.settings.SettingsManager
import com.aicompanion.virtualworld.VirtualWorldManager
import com.aicompanion.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LayoutTemplate(
    val name: String,
    val icon: String,
    val columns: Int,
    val aspectRatio: String,
    val genWidth: Int,
    val genHeight: Int
)

data class AlbumEntry(
    val id: String,
    val prompt: String,
    val imagePath: String,
    val title: String,
    val caption: String,
    val createdAt: String,
    val aspectRatio: String = "1:1"
)

object MemorialAlbumManager {

    private const val TAG = "MemorialAlbum"
    private const val PREFS_NAME = "memorial_album"
    private const val KEY_ENTRIES = "album_entries"
    private const val KEY_CHAR_REF_IMAGE = "character_ref_image"
    private const val KEY_CHAR_REF_PROMPT = "character_ref_prompt"
    private const val KEY_CURRENT_TEMPLATE = "current_template_index"
    private const val ENTRIES_FILE = "album_entries.json"

    val layoutTemplates = listOf(
        LayoutTemplate("经典方阵", "▦", 2, "1:1", 512, 512),
        LayoutTemplate("竖屏大片", "▯", 1, "9:16", 576, 1024),
        LayoutTemplate("横屏风景", "▭", 1, "16:9", 1024, 576),
        LayoutTemplate("双列4:3", "◫", 2, "4:3", 512, 384),
        LayoutTemplate("三宫格", "⊞", 3, "1:1", 512, 512),
        LayoutTemplate("双列竖图", "▥", 2, "9:16", 576, 1024)
    )

    private val builtinPrompts = listOf(
        Pair("春日漫步", "A warm spring day, cherry blossoms falling, two people walking side by side in a park, soft sunlight, anime style, romantic atmosphere, gentle breeze"),
        Pair("夏日祭典", "Summer festival night, fireworks in the sky, two people in yukata watching fireworks together, lanterns glowing, anime style, warm colors"),
        Pair("秋叶私语", "Autumn maple leaves, golden and red, two people sitting on a bench under a big tree, cozy atmosphere, warm light, anime style"),
        Pair("冬雪相依", "Winter snow scene, two people sharing a scarf, snowflakes falling, warm breath visible, cozy and romantic, anime style"),
        Pair("星空下的约定", "Starry night sky, two people lying on grass looking at stars, milky way visible, dreamy atmosphere, anime style"),
        Pair("海边日落", "Sunset at the beach, orange and purple sky, two people walking along the shore, waves gently touching their feet, anime style"),
        Pair("雨中漫步", "Rainy day, two people sharing an umbrella walking on a quiet street, reflections on wet ground, cozy atmosphere, anime style"),
        Pair("图书馆午后", "Cozy library afternoon, two people reading books side by side, warm light through windows, peaceful atmosphere, anime style"),
        Pair("花田奔跑", "Lavender flower field, two people running and laughing, bright sunny day, vibrant colors, anime style"),
        Pair("屋顶观星", "Rooftop at night, city lights below, two people sitting close watching the skyline, gentle wind, anime style"),
        Pair("咖啡馆时光", "Cozy cafe interior, two people sitting across from each other, coffee cups on table, warm lighting, anime style"),
        Pair("初雪告白", "First snow of winter, one person giving a gift to another, snowflakes around them, emotional moment, anime style"),
        Pair("生日惊喜", "Birthday celebration, cake with candles, one person surprising another with a gift, confetti, warm and happy atmosphere, anime style"),
        Pair("新年烟火", "New Year fireworks, two people celebrating together, sparklers in hand, festive atmosphere, anime style"),
        Pair("樱花树下", "Cherry blossom tree in full bloom, petals falling, two people standing close, spring breeze, romantic anime style"),
        Pair("放学路上", "After school, two students walking home together, school bags, sunset light, nostalgic atmosphere, anime style"),
        Pair("深夜食堂", "Late night ramen shop, two people eating together, steam rising, warm and comforting atmosphere, anime style"),
        Pair("游乐园约会", "Amusement park, two people on a ferris wheel, colorful lights below, happy expressions, anime style")
    )

    fun getBuiltinPrompts(): List<Pair<String, String>> = builtinPrompts

    fun getRandomPrompt(): Pair<String, String> = builtinPrompts.random()

    fun getGenSizeForRatio(aspectRatio: String): Pair<Int, Int> {
        return layoutTemplates.find { it.aspectRatio == aspectRatio }?.let { it.genWidth to it.genHeight }
            ?: (512 to 512)
    }

    fun getCurrentTemplateIndex(context: Context): Int {
        val idx = getPrefs(context).getInt(KEY_CURRENT_TEMPLATE, 0)
        return idx.coerceIn(0, layoutTemplates.size - 1)
    }

    fun saveCurrentTemplateIndex(context: Context, index: Int) {
        getPrefs(context).edit().putInt(KEY_CURRENT_TEMPLATE, index).apply()
    }

    fun aspectRatioToHeightMultiplier(ratio: String): Float {
        return when (ratio) {
            "1:1" -> 1f
            "9:16" -> 16f / 9f
            "4:3" -> 3f / 4f
            "16:9" -> 9f / 16f
            else -> {
                val parts = ratio.split(":")
                if (parts.size == 2) {
                    val w = parts[0].toFloatOrNull() ?: 1f
                    val h = parts[1].toFloatOrNull() ?: 1f
                    if (w > 0f) h / w else 1f
                } else 1f
            }
        }
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getEntriesFile(context: Context): File {
        return File(context.filesDir, ENTRIES_FILE)
    }

    fun getEntries(context: Context): List<AlbumEntry> {
        migrateFromPrefsIfNeeded(context)
        val file = getEntriesFile(context)
        val json = if (file.exists()) {
            try { file.readText(Charsets.UTF_8) } catch (e: Exception) {
                AppLogger.e(TAG, "getEntries read: ${e.message}"); "[]"
            }
        } else "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                AlbumEntry(
                    id = obj.getString("id"),
                    prompt = obj.getString("prompt"),
                    imagePath = obj.getString("imagePath"),
                    title = obj.getString("title"),
                    caption = obj.optString("caption", ""),
                    createdAt = obj.getString("createdAt"),
                    aspectRatio = obj.optString("aspectRatio", "1:1")
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "getEntries: ${e.message}")
            emptyList()
        }
    }

    private fun migrateFromPrefsIfNeeded(context: Context) {
        val file = getEntriesFile(context)
        if (file.exists()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val oldJson = prefs.getString(KEY_ENTRIES, null) ?: return
        if (oldJson != "[]") {
            try {
                file.writeText(oldJson, Charsets.UTF_8)
                prefs.edit().remove(KEY_ENTRIES).apply()
                AppLogger.i(TAG, "Migrated album entries from SharedPreferences to file")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Migration failed: ${e.message}")
            }
        }
    }

    private fun saveEntries(context: Context, entries: List<AlbumEntry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.id)
                put("prompt", e.prompt)
                put("imagePath", e.imagePath)
                put("title", e.title)
                put("caption", e.caption)
                put("createdAt", e.createdAt)
                put("aspectRatio", e.aspectRatio)
            })
        }
        try {
            val file = getEntriesFile(context)
            val bak = File(file.parent, "$ENTRIES_FILE.bak")
            if (file.exists()) {
                if (bak.exists()) bak.delete()
                file.renameTo(bak)
            }
            file.writeText(arr.toString(), Charsets.UTF_8)
        } catch (e: Exception) {
            AppLogger.e(TAG, "saveEntries: ${e.message}")
        }
    }

    fun addEntry(context: Context, entry: AlbumEntry) {
        val entries = getEntries(context).toMutableList()
        entries.add(0, entry)
        saveEntries(context, entries)
    }

    fun deleteEntry(context: Context, id: String) {
        val entries = getEntries(context)
        val entry = entries.find { it.id == id }
        saveEntries(context, entries.filter { it.id != id })
        entry?.let {
            val imageFile = File(it.imagePath)
            if (imageFile.exists()) imageFile.delete()
        }
    }

    fun updateCaption(context: Context, id: String, caption: String) {
        val entries = getEntries(context).map { if (it.id == id) it.copy(caption = caption) else it }
        saveEntries(context, entries)
    }

    fun isImageModelConfigured(context: Context): Boolean {
        return VirtualWorldManager(context).hasImageModelConfigured()
    }

    fun buildPromptWithUser(context: Context, basePrompt: String): String {
        val sm = SettingsManager(context)
        val genderDesc = when (sm.userGender) {
            "male" -> "a young man"
            "female" -> "a young woman"
            else -> "a person"
        }
        val appearanceDesc = sm.userAppearance.ifBlank { "" }.let { if (it.isNotBlank()) ", with features: $it" else "" }

        val personaId = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getString("active_persona_id", "default") ?: "default"
        val personaPrefs = context.getSharedPreferences("persona_data_$personaId", Context.MODE_PRIVATE)
        val personaAppearance = personaPrefs.getString("persona_appearance", "") ?: ""
        val personaDesc = if (personaAppearance.isNotBlank()) " The companion character has: $personaAppearance." else ""

        return "$basePrompt, featuring $genderDesc$appearanceDesc.$personaDesc"
    }

    fun getCharacterRefImagePath(context: Context): String {
        return getPrefs(context).getString(KEY_CHAR_REF_IMAGE, "") ?: ""
    }

    fun saveCharacterRefImage(context: Context, imagePath: String) {
        getPrefs(context).edit().putString(KEY_CHAR_REF_IMAGE, imagePath).apply()
        AppLogger.i(TAG, "角色参考图已保存: $imagePath")
    }

    fun getCharacterRefPrompt(context: Context): String {
        return getPrefs(context).getString(KEY_CHAR_REF_PROMPT, "") ?: ""
    }

    private fun saveCharacterRefPrompt(context: Context, prompt: String) {
        getPrefs(context).edit().putString(KEY_CHAR_REF_PROMPT, prompt).apply()
    }

    fun hasCharacterRefImage(context: Context): Boolean {
        val path = getCharacterRefImagePath(context)
        return path.isNotBlank() && File(path).exists()
    }

    fun buildCharacterRefPrompt(context: Context): String {
        val personaId = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getString("active_persona_id", "default") ?: "default"
        val personaPrefs = context.getSharedPreferences("persona_data_$personaId", Context.MODE_PRIVATE)

        val personaName = personaPrefs.getString("persona_name", "") ?: ""
        val personaAppearance = personaPrefs.getString("persona_appearance", "") ?: ""
        val personaPersonality = personaPrefs.getString("persona_personality", "") ?: ""

        val sm = SettingsManager(context)
        val userGenderDesc = when (sm.userGender) {
            "male" -> "a young man"
            "female" -> "a young woman"
            else -> "a person"
        }
        val userAppearanceDesc = sm.userAppearance.ifBlank { "" }.let { if (it.isNotBlank()) ", with features: $it" else "" }

        val sb = StringBuilder("Character portrait, full body, anime style, ")
        if (personaName.isNotBlank()) sb.append("$personaName is ")
        if (personaAppearance.isNotBlank()) sb.append(personaAppearance)
        else sb.append("a cute anime character")
        sb.append(", standing pose, white background, detailed character design sheet")
        if (personaPersonality.isNotBlank()) sb.append(", personality vibe: $personaPersonality")

        sb.append(". Also include $userGenderDesc$userAppearanceDesc standing beside the character.")

        return sb.toString()
    }

    suspend fun generateCharacterRefImage(context: Context): String? = withContext(Dispatchers.IO) {
        try {
            val vwMgr = VirtualWorldManager(context)
            if (!vwMgr.hasImageModelConfigured()) {
                AppLogger.w(TAG, "图片生成模型未配置，无法生成角色参考图")
                return@withContext null
            }

            val prompt = buildCharacterRefPrompt(context)
            AppLogger.i(TAG, "角色参考图prompt: $prompt")

            val formatType = com.aicompanion.settings.ServicePresets.findImageGenPreset(
                com.aicompanion.settings.SettingsManager(context).imageGenProvider
            ).formatType
            val jsonBody = com.aicompanion.network.ProviderAdapter.buildImageRequest(formatType, vwMgr.imageModel, prompt, 1, "1024x1024")
            val headers = com.aicompanion.network.ProviderAdapter.buildImageHeaders(formatType, vwMgr.imageApiKey, vwMgr.imageApiSecretKey)
            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
            val requestBuilder = Request.Builder().url(vwMgr.imageApiUrl).post(requestBody)
            headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                val errBody = response.body?.string()?.take(200) ?: ""
                AppLogger.e(TAG, "角色参考图生成失败: HTTP ${response.code}, body=$errBody")
                return@withContext null
            }

            val bodyStr = response.body?.string() ?: return@withContext null
            val imageUrl = com.aicompanion.network.ProviderAdapter.parseImageResponse(formatType, bodyStr, vwMgr.imageApiUrl, vwMgr.imageApiKey, vwMgr.imageApiSecretKey)

            val albumDir = File(context.filesDir, "memorial_album")
            if (!albumDir.exists()) albumDir.mkdirs()
            val tempFile = File(albumDir, "character_ref_temp.png")

            if (imageUrl.isNullOrBlank()) {
                AppLogger.e(TAG, "角色参考图: 未找到图片URL或数据")
                return@withContext null
            }

            val isLocalFile = imageUrl.startsWith("/") || imageUrl.startsWith("file://")
            if (isLocalFile) {
                val srcFile = File(imageUrl.removePrefix("file://"))
                if (srcFile.exists()) {
                    srcFile.copyTo(tempFile, overwrite = true)
                    saveCharacterRefPrompt(context, prompt)
                    AppLogger.i(TAG, "角色参考图临时文件已保存(本地): ${tempFile.absolutePath}")
                    return@withContext tempFile.absolutePath
                }
            }

            val downloadReq = Request.Builder().url(imageUrl).build()
            val downloadResp = com.aicompanion.network.ApiClient.sharedClient.newCall(downloadReq).execute()
            val imageBytes = downloadResp.body?.bytes() ?: return@withContext null
            java.io.FileOutputStream(tempFile).use { it.write(imageBytes) }
            saveCharacterRefPrompt(context, prompt)
            AppLogger.i(TAG, "角色参考图临时文件已保存: ${tempFile.absolutePath}")
            return@withContext tempFile.absolutePath
        } catch (e: Exception) {
            AppLogger.e(TAG, "generateCharacterRefImage: ${e.message}")
            null
        }
    }

    fun confirmCharacterRefImage(context: Context, tempPath: String): Boolean {
        return try {
            val tempFile = File(tempPath)
            if (!tempFile.exists()) {
                AppLogger.e(TAG, "临时角色参考图不存在: $tempPath")
                return false
            }
            val albumDir = File(context.filesDir, "memorial_album")
            if (!albumDir.exists()) albumDir.mkdirs()
            val finalFile = File(albumDir, "character_ref.png")
            tempFile.copyTo(finalFile, overwrite = true)
            tempFile.delete()
            saveCharacterRefImage(context, finalFile.absolutePath)
            AppLogger.i(TAG, "角色参考图已确认保存: ${finalFile.absolutePath}")
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "confirmCharacterRefImage: ${e.message}")
            false
        }
    }

    private fun saveImageBytes(context: Context, bytes: ByteArray, prompt: String, title: String, caption: String = "", aspectRatio: String = "1:1"): AlbumEntry {
        val albumDir = File(context.filesDir, "memorial_album")
        if (!albumDir.exists()) albumDir.mkdirs()
        val imageFile = File(albumDir, "${System.currentTimeMillis()}.png")
        java.io.FileOutputStream(imageFile).use { it.write(bytes) }
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val entry = AlbumEntry(
            id = imageFile.absolutePath,
            prompt = prompt,
            imagePath = imageFile.absolutePath,
            title = title,
            caption = caption,
            createdAt = now,
            aspectRatio = aspectRatio
        )
        addEntry(context, entry)
        return entry
    }

    suspend fun generateImage(context: Context, prompt: String, title: String, caption: String = "", aspectRatio: String = "1:1"): AlbumEntry? = withContext(Dispatchers.IO) {
        try {
            val vwMgr = VirtualWorldManager(context)
            if (!vwMgr.hasImageModelConfigured()) {
                AppLogger.w(TAG, "图片生成模型未配置")
                return@withContext null
            }

            val fullPrompt = buildPromptWithUser(context, prompt)
            val (genW, genH) = getGenSizeForRatio(aspectRatio)
            val formatType = com.aicompanion.settings.ServicePresets.findImageGenPreset(
                com.aicompanion.settings.SettingsManager(context).imageGenProvider
            ).formatType
            val jsonBody = com.aicompanion.network.ProviderAdapter.buildImageRequest(formatType, vwMgr.imageModel, fullPrompt, 1, "${genW}x${genH}")
            val headers = com.aicompanion.network.ProviderAdapter.buildImageHeaders(formatType, vwMgr.imageApiKey, vwMgr.imageApiSecretKey)

            AppLogger.i(TAG, "生成图片: ratio=$aspectRatio, size=${genW}x${genH}, prompt=${fullPrompt.take(80)}")

            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
            val requestBuilder = Request.Builder().url(vwMgr.imageApiUrl).post(requestBody)
            headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                val errBody = response.body?.string()?.take(200) ?: ""
                AppLogger.e(TAG, "图片生成失败: HTTP ${response.code}, body=$errBody")
                return@withContext null
            }

            val bodyStr = response.body?.string() ?: return@withContext null
            val imageUrl = com.aicompanion.network.ProviderAdapter.parseImageResponse(formatType, bodyStr, vwMgr.imageApiUrl, vwMgr.imageApiKey, vwMgr.imageApiSecretKey)

            if (imageUrl.isNullOrBlank()) {
                AppLogger.e(TAG, "未找到图片URL或数据")
                return@withContext null
            }

            val isLocalFile = imageUrl.startsWith("/") || imageUrl.startsWith("file://")
            if (isLocalFile) {
                val srcFile = File(imageUrl.removePrefix("file://"))
                if (srcFile.exists()) {
                    val imageBytes = srcFile.readBytes()
                    return@withContext saveImageBytes(context, imageBytes, prompt, title, caption, aspectRatio)
                }
            }

            val downloadReq = Request.Builder().url(imageUrl).build()
            val downloadResp = com.aicompanion.network.ApiClient.sharedClient.newCall(downloadReq).execute()
            val imageBytes = downloadResp.body?.bytes() ?: return@withContext null
            return@withContext saveImageBytes(context, imageBytes, prompt, title, caption, aspectRatio)
        } catch (e: Exception) {
            AppLogger.e(TAG, "generateImage: ${e.message}")
            null
        }
    }
}
