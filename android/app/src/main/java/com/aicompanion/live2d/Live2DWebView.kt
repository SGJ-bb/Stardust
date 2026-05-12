package com.aicompanion.live2d

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.graphics.BitmapFactory
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.aicompanion.models.Action
import com.aicompanion.models.Emotion
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Collections

class Live2DWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    private val modelCacheDir = File(context.filesDir, "live2d_cache")
    private var onEmotionChange: ((Emotion) -> Unit)? = null
    var onActionTrigger: ((Action) -> Unit)? = null

    private var onModelLoaded: ((Boolean) -> Unit)? = null
    var isDestroyed = false
        private set
    private var modelBasePath: String = ""
    private var onModelInfo: ((Float, Float, Float) -> Unit)? = null
    var touchHandler: ((MotionEvent) -> Boolean)? = null

    private val logLines = Collections.synchronizedList(mutableListOf<String>())
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS")
    private val maxTextureSize by lazy { detectMaxTextureSize() }
    private val textureCache = Collections.synchronizedMap(mutableMapOf<String, File>())

    private fun detectMaxTextureSize(): Int {
        return try {
            val manager = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            val display = manager.defaultDisplay
            val width = display.width
            val height = display.height
            val maxDim = maxOf(width, height)
            when {
                maxDim >= 2560 -> 4096
                maxDim >= 1440 -> 4096
                maxDim >= 1080 -> 2048
                else -> 2048
            }
        } catch (e: Exception) {
            2048
        }
    }

    init {
        try {
            logLines.clear()
            if (!modelCacheDir.exists()) modelCacheDir.mkdirs()

            addLog("=== Live2D WebView Init ===")
            addLog("Cache dir: ${modelCacheDir.absolutePath}")

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                allowUniversalAccessFromFileURLs = true
                allowFileAccessFromFileURLs = true
                mediaPlaybackRequiresUserGesture = false
                useWideViewPort = true
                loadWithOverviewMode = true
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(false)
                setBuiltInZoomControls(false)
                setSupportZoom(false)
                displayZoomControls = false
            }

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(msg: android.webkit.ConsoleMessage): Boolean {
                    addLog("CONSOLE: ${msg.message()}")
                    return true
                }
            }

            webViewClient = object : WebViewClient() {
                private fun resolveResourcePath(requestedPath: String, modelDir: File): File? {
                    if (textureCache.containsKey(requestedPath)) {
                        return textureCache[requestedPath]
                    }

                    val normalizedPath = requestedPath.replace("\\", "/").replace("//", "/")

                    val file = File(modelDir, normalizedPath)
                    if (file.exists() && file.isFile) {
                        textureCache[requestedPath] = file
                        return file
                    }

                    val canonicalPath = file.canonicalPath
                    val canonicalFile = File(canonicalPath)
                    if (canonicalFile.exists() && canonicalFile.isFile && canonicalFile.absolutePath.startsWith(modelDir.absolutePath + File.separator)) {
                        textureCache[requestedPath] = canonicalFile
                        return canonicalFile
                    }

                    val fileName = File(requestedPath).name
                    try {
                        modelDir.walkTopDown().filter { it.isFile && it.name.equals(fileName, ignoreCase = true) }.firstOrNull()?.let { found ->
                            textureCache[requestedPath] = found
                            return found
                        }
                    } catch (e: Exception) {}

                    return null
                }

                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val url = request?.url?.toString() ?: return null
                    try {
                        if (url.startsWith("file:///android_asset/")) {
                            val raw = url.removePrefix("file:///android_asset/")
                            val path = URLDecoder.decode(raw, "UTF-8")
                            try {
                                val input = context.assets.open(path)
                                val mime = getMime(path)
                                return WebResourceResponse(mime, getEncoding(mime), input)
                            } catch (e: Exception) {
                                addLog("ASSET MISS: $path")
                            }
                        }

                        if (url.startsWith("file://") && modelBasePath.isNotEmpty()) {
                            val decodedUrl = URLDecoder.decode(url, "UTF-8")
                            var pathPart = decodedUrl.removePrefix("file://")
                            while (pathPart.startsWith("//")) pathPart = pathPart.removePrefix("/")
                            if (!pathPart.startsWith("/")) pathPart = "/$pathPart"

                            val file = File(pathPart)
                            if (file.exists() && file.isFile) {
                                val mime = getMime(file.name)
                                return WebResourceResponse(mime, getEncoding(mime), file.inputStream())
                            }

                            val ext = file.extension.lowercase()
                            val modelDir = File(modelBasePath)

                            var requestedPath = if (pathPart.startsWith(modelDir.absolutePath)) {
                                pathPart.substring(modelDir.absolutePath.length).trimStart('/', '\\')
                            } else {
                                file.name
                            }

                            var foundFile = resolveResourcePath(requestedPath, modelDir)
                            if (foundFile != null && foundFile.exists()) {
                                val mime = getMime(foundFile.name)
                                return WebResourceResponse(mime, getEncoding(mime), foundFile.inputStream())
                            }

                            val parentDir = modelDir.parentFile
                            if (parentDir != null) {
                                foundFile = resolveResourcePath(requestedPath, parentDir)
                                if (foundFile != null && foundFile.exists()) {
                                    val mime = getMime(foundFile.name)
                                    return WebResourceResponse(mime, getEncoding(mime), foundFile.inputStream())
                                }
                            }

                            val justName = file.name
                            foundFile = resolveResourcePath(justName, modelDir)
                            if (foundFile != null && foundFile.exists()) {
                                val mime = getMime(foundFile.name)
                                return WebResourceResponse(mime, getEncoding(mime), foundFile.inputStream())
                            }

                            if (parentDir != null) {
                                foundFile = resolveResourcePath(justName, parentDir)
                                if (foundFile != null && foundFile.exists()) {
                                    val mime = getMime(foundFile.name)
                                    return WebResourceResponse(mime, getEncoding(mime), foundFile.inputStream())
                                }
                            }

                            if (ext in listOf("mp3", "ogg", "wav", "m4a")) {
                                val audioMime = when (ext) {
                                    "ogg" -> "audio/ogg"
                                    "wav" -> "audio/wav"
                                    "m4a" -> "audio/mp4"
                                    else -> "audio/mpeg"
                                }
                                return WebResourceResponse(audioMime, null,
                                    java.io.ByteArrayInputStream(ByteArray(0)))
                            }

                            addLog("FILE MISS: $requestedPath in ${modelDir.absolutePath}")
                        }
                    } catch (e: Exception) {
                        addLog("INTERCEPT ERR: ${e.message}")
                    }
                    return null
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
                    super.onReceivedError(view, request, error)
                    addLog("LOAD ERR: ${request?.url} - ${error?.description}")
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    addLog("PAGE START: $url")
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    addLog("PAGE FINISHED: $url")
                }
            }

            addJavascriptInterface(Live2DBridge(), "Live2DBridge")
            setBackgroundColor(0x00000000)
            isHorizontalScrollBarEnabled = false
            isVerticalScrollBarEnabled = false
            isFocusable = true
            isFocusableInTouchMode = true

        } catch (e: Exception) {
            addLog("Init exception: ${e.message}")
            e.printStackTrace()
        }
    }

    

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return touchHandler?.invoke(event) ?: super.onTouchEvent(event)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (touchHandler != null) {
            return true
        }
        return super.onInterceptTouchEvent(ev)
    }

    private fun getMime(name: String): String = when {
        name.endsWith(".png") -> "image/png"
        name.endsWith(".jpg") || name.endsWith(".jpeg") -> "image/jpeg"
        name.endsWith(".json") -> "application/json"
        name.endsWith(".js") -> "application/javascript"
        name.endsWith(".html") -> "text/html"
        name.endsWith(".moc3") -> "application/octet-stream"
        name.endsWith(".moc") -> "application/octet-stream"
        else -> "application/octet-stream"
    }

    private fun getEncoding(mime: String): String? = when {
        mime.startsWith("text/") -> "UTF-8"
        mime.contains("json") -> "UTF-8"
        mime.contains("javascript") -> "UTF-8"
        else -> null
    }

    fun getLog(): String = logLines.joinToString("\n")

    fun clearLog() {
        logLines.clear()
    }

    fun loadLive2DModelFromPath(modelJsonAbsolutePath: String) {
        if (isDestroyed) return
        try {
            addLog("Loading model: $modelJsonAbsolutePath")
            val modelFile = File(modelJsonAbsolutePath)
            if (!modelFile.exists()) {
                addLog("ERROR: File not found: $modelJsonAbsolutePath")
                onModelLoaded?.invoke(false)
                return
            }

            val srcDir = modelFile.parentFile ?: return
            val cacheDirName = srcDir.name
            val localDir = File(modelCacheDir, cacheDirName)
            if (localDir.exists()) {
                localDir.deleteRecursively()
            }
            localDir.mkdirs()

            addLog("Copying model to cache: ${localDir.absolutePath}")
            srcDir.walkTopDown().filter { it.isFile }.forEach { srcFile ->
                try {
                    val relPath = srcFile.absolutePath.substring(srcDir.absolutePath.length).trimStart('/', '\\')
                    if (shouldSkipFile(relPath, srcFile.name)) {
                        return@forEach
                    }
                    val dstFile = File(localDir, relPath)
                    dstFile.parentFile?.mkdirs()
                    srcFile.inputStream().use { input ->
                        dstFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: Exception) {
                    addLog("Copy warn: ${e.message}")
                }
            }

            checkAndCompressTextures(localDir)

            val cachedModelJson = findModelJsonRecursive(localDir)
            if (cachedModelJson == null) {
                addLog("ERROR: model3.json not found in cache after copy")
                onModelLoaded?.invoke(false)
                return
            }

            val modelDir = cachedModelJson.parentFile ?: return
            modelBasePath = modelDir.absolutePath
            textureCache.clear()

            validateAndFixModel(cachedModelJson, modelDir)

            val html = buildLive2DHtml(cachedModelJson.name)
            val htmlFile = File(modelDir, "view.html")
            htmlFile.writeText(html, Charsets.UTF_8)

            addLog("Loading HTML: file://${htmlFile.absolutePath}")

            post {
                if (!isDestroyed) {
                    loadUrl("about:blank")
                    postDelayed({
                        if (!isDestroyed) {
                            loadUrl("file://" + htmlFile.absolutePath)
                        }
                    }, 150)
                }
            }
        } catch (e: Exception) {
            addLog("Exception: ${e.message}")
            onModelLoaded?.invoke(false)
        }
    }

    private fun shouldSkipFile(relPath: String, fileName: String): Boolean {
        if (relPath.startsWith("_backup") || relPath.contains("/_backup/") || relPath.contains("\\_backup\\")) return true
        if (fileName.endsWith(".baiduyun.uploading.cfg")) return true
        if (fileName.endsWith(".cfg") && !fileName.endsWith(".cdi3.json")) return true
        if (fileName.endsWith(".tmp") || fileName.endsWith(".temp")) return true
        if (fileName.endsWith(".bak")) return true
        if (fileName.startsWith(".") && !fileName.endsWith(".model3.json") && !fileName.endsWith(".model.json")) return true
        if (fileName.contains(".uploading.")) return true
        if (fileName.equals("items_pinned_to_model.json", ignoreCase = true)) return true
        if (fileName.equals("items_pinned_to_model.zip", ignoreCase = true)) return true
        if (fileName.endsWith(".vtube.json")) return true
        return false
    }

    private fun validateAndFixModel(modelJsonFile: File, modelDir: File) {
        try {
            val content = modelJsonFile.readText()
            val json = org.json.JSONObject(content)
            val fileRefs = json.optJSONObject("FileReferences") ?: json.optJSONObject("file_references")

            if (fileRefs == null) {
                addLog("WARN: No FileReferences in model JSON")
                return
            }

            val missingFiles = mutableListOf<String>()
            val fixedRefs = mutableListOf<String>()

            listOf("Moc", "moc").forEach { key ->
                val mocFile = fileRefs.optString(key)
                if (mocFile.isNotEmpty()) {
                    val mocPath = File(modelDir, mocFile)
                    if (!mocPath.exists()) {
                        val found = findFileByName(mocPath.name, modelDir)
                        if (found != null) {
                            fileRefs.put(key, found.relativeTo(modelDir).path.replace("\\", "/"))
                            fixedRefs.add("$mocFile -> ${found.relativeTo(modelDir).path.replace("\\", "/")}")
                        } else {
                            missingFiles.add(mocFile)
                        }
                    }
                }
            }

            val texturesKey = listOf("Textures", "textures").firstOrNull { fileRefs.has(it) }
            if (texturesKey != null) {
                val textures = fileRefs.optJSONArray(texturesKey)
                if (textures != null) {
                    for (i in 0 until textures.length()) {
                        val texPath = textures.optString(i)
                        if (texPath.isNotEmpty()) {
                            val texFile = File(modelDir, texPath)
                            if (!texFile.exists()) {
                                val found = findFileByName(texFile.name, modelDir)
                                if (found != null) {
                                    val newPath = found.relativeTo(modelDir).path.replace("\\", "/")
                                    textures.put(i, newPath)
                                    fixedRefs.add("$texPath -> $newPath")
                                } else {
                                    missingFiles.add(texPath)
                                }
                            }
                        }
                    }
                }
            }

            listOf("Physics", "physics").forEach { key ->
                val physFile = fileRefs.optString(key)
                if (physFile.isNotEmpty()) {
                    val physPath = File(modelDir, physFile)
                    if (!physPath.exists()) {
                        val found = findFileByName(physPath.name, modelDir)
                        if (found != null) {
                            fileRefs.put(key, found.relativeTo(modelDir).path.replace("\\", "/"))
                            fixedRefs.add("$physFile -> ${found.relativeTo(modelDir).path.replace("\\", "/")}")
                        }
                    }
                }
            }

            if (fixedRefs.isNotEmpty()) {
                modelJsonFile.writeText(json.toString(2))
                addLog("Fixed refs: ${fixedRefs.joinToString(", ")}")
            }

            if (missingFiles.isNotEmpty()) {
                addLog("Missing files: ${missingFiles.joinToString(", ")}")
            }

            val texCount = texturesKey?.let { fileRefs.optJSONArray(it)?.length() } ?: 0
            val mocExists = listOf("Moc", "moc").any { key ->
                val moc = fileRefs.optString(key)
                moc.isNotEmpty() && File(modelDir, moc).exists()
            }
            addLog("Validation: moc=$mocExists textures=$texCount dir=${modelDir.name}")

        } catch (e: Exception) {
            addLog("Validate error: ${e.message}")
        }
    }

    private fun findFileByName(fileName: String, searchDir: File): File? {
        return try {
            searchDir.walkTopDown()
                .filter { it.isFile && it.name.equals(fileName, ignoreCase = true) }
                .firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    private fun findModelJsonRecursive(dir: File): File? {
        dir.walkTopDown().forEach { file ->
            if (file.isFile && (file.name.endsWith(".model3.json") || file.name.endsWith(".model.json"))) {
                return file
            }
        }
        return null
    }

    fun loadLive2DModelFromAssets(modelJsonPath: String) {
        if (isDestroyed) return
        try {
            copyModelFromAssets(modelJsonPath)
            val modelDir = modelJsonPath.substringBeforeLast("/")
            val localDirName = modelDir.replace("vtuber/", "")
            val localDir = File(modelCacheDir, localDirName)

            checkAndCompressTextures(localDir)

            modelBasePath = localDir.absolutePath

            val modelJsonFile = modelJsonPath.substringAfterLast("/")
            val html = buildLive2DHtml(modelJsonFile)
            val htmlFile = File(localDir, "view.html")
            htmlFile.writeText(html, Charsets.UTF_8)

            post {
                if (!isDestroyed) {
                    loadUrl("about:blank")
                    postDelayed({
                        if (!isDestroyed) {
                            loadUrl("file://" + htmlFile.absolutePath)
                        }
                    }, 100)
                }
            }
        } catch (e: Exception) {
            addLog("Exception: ${e.message}")
            onModelLoaded?.invoke(false)
        }
    }

    private fun checkAndCompressTextures(modelDir: File) {
        try {
            var compressed = 0
            var skipped = 0
            addLog("Checking textures in: ${modelDir.absolutePath}, maxTextureSize=$maxTextureSize")
            modelDir.walkTopDown().filter { it.isFile && (it.extension.equals("png", ignoreCase = true) || it.extension.equals("jpg", ignoreCase = true) || it.extension.equals("jpeg", ignoreCase = true)) }.forEach { file ->
                try {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.absolutePath, options)

                    if (options.outWidth <= 0 || options.outHeight <= 0) {
                        addLog("Texture decode bounds failed: ${file.name}, skipping")
                        skipped++
                        return@forEach
                    }

                    addLog("Texture: ${file.name} ${options.outWidth}x${options.outHeight}")

                    val needsResize = options.outWidth > maxTextureSize || options.outHeight > maxTextureSize
                    val needsReencode = file.extension.equals("png", ignoreCase = true) &&
                            options.outWidth * options.outHeight > 2048 * 2048

                    if (needsResize || needsReencode) {
                        val targetW = if (needsResize) {
                            val scale = minOf(maxTextureSize.toFloat() / options.outWidth, maxTextureSize.toFloat() / options.outHeight)
                            (options.outWidth * scale).toInt()
                        } else options.outWidth

                        val targetH = if (needsResize) {
                            val scale = minOf(maxTextureSize.toFloat() / options.outWidth, maxTextureSize.toFloat() / options.outHeight)
                            (options.outHeight * scale).toInt()
                        } else options.outHeight

                        val backupDir = File(file.parentFile, "_backup")
                        if (!backupDir.exists()) backupDir.mkdirs()

                        val backupFile = File(backupDir, file.name)
                        if (!backupFile.exists()) {
                            file.copyTo(backupFile)
                        }

                        val inSampleSize = calculateInSampleSize(options, targetW, targetH)
                        val decodeOpts = BitmapFactory.Options().apply {
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                            this.inSampleSize = inSampleSize
                        }
                        val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
                        if (bitmap != null) {
                            val scaled = if (bitmap.width != targetW || bitmap.height != targetH) {
                                Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
                            } else {
                                bitmap
                            }
                            ByteArrayOutputStream().use { baos ->
                                scaled.compress(Bitmap.CompressFormat.PNG, 100, baos)
                                file.writeBytes(baos.toByteArray())
                            }
                            if (bitmap !== scaled) bitmap.recycle()
                            scaled.recycle()
                            compressed++
                            addLog("Compressed: ${file.name} ${options.outWidth}x${options.outHeight} -> ${targetW}x${targetH} (sample=$inSampleSize)")
                        } else {
                            addLog("Decode failed for: ${file.name}")
                            if (backupFile.exists()) {
                                backupFile.copyTo(file, overwrite = true)
                            }
                            skipped++
                        }
                    }
                } catch (e: Exception) {
                    addLog("Texture process error ${file.name}: ${e.message}")
                    skipped++
                }
            }
            if (compressed > 0) addLog("Compressed $compressed texture(s)")
            if (skipped > 0) addLog("Skipped $skipped texture(s)")
        } catch (e: Exception) {
            addLog("Texture check error: ${e.message}")
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun copyModelFromAssets(modelJsonPath: String) {
        try {
            val assetManager = context.assets
            val modelDir = modelJsonPath.substringBeforeLast("/")
            val localDirName = modelDir.replace("vtuber/", "")
            val localDir = File(modelCacheDir, localDirName)
            if (localDir.exists()) {
                localDir.deleteRecursively()
            }
            localDir.mkdirs()
            copyAssetDir(assetManager, modelDir, localDir)
        } catch (e: Exception) {
            addLog("Copy failed: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun copyAssetDir(
        assetManager: android.content.res.AssetManager,
        assetDir: String,
        targetDir: File
    ) {
        try {
            val files = assetManager.list(assetDir) ?: return
            for (file in files) {
                val assetPath = if (assetDir.isEmpty()) file else "$assetDir/$file"
                val targetFile = File(targetDir, file)
                val isFile = try {
                    assetManager.open(assetPath).use { true }
                } catch (e: Exception) {
                    false
                }
                if (isFile) {
                    assetManager.open(assetPath).use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                } else {
                    targetFile.mkdirs()
                    copyAssetDir(assetManager, assetPath, targetFile)
                }
            }
        } catch (e: Exception) {
            addLog("copyAssetDir error: ${e.message}")
            e.printStackTrace()
        }
    }

    fun tapModel(x: Float, y: Float) {
        if (isDestroyed) return
        post {
            try {
                evaluateJavascript("window.tapModel && window.tapModel($x, $y)", null)
            } catch (e: Exception) {}
        }
    }

    fun setEmotion(emotion: Emotion) {
        if (isDestroyed) return
        try {
            evaluateJavascript("window.setLive2DEmotion && window.setLive2DEmotion('${emotion.name.lowercase()}')", null)
        } catch (e: Exception) {}
    }

    fun setAction(action: Action) {
        if (isDestroyed) return
        try {
            evaluateJavascript("window.triggerLive2DAction && window.triggerLive2DAction('${action.name.lowercase()}')", null)
        } catch (e: Exception) {}
    }

    fun setAdjustMode(enabled: Boolean) {
        post {
            try {
                evaluateJavascript("window.setAdjustMode && window.setAdjustMode($enabled)", null)
            } catch (e: Exception) {}
        }
    }

    fun setModelScale(scaleMultiplier: Float) {
        if (isDestroyed) return
        post {
            try {
                evaluateJavascript("window.setModelScale && window.setModelScale($scaleMultiplier)", null)
            } catch (e: Exception) {
                addLog("setModelScale error: ${e.message}")
            }
        }
    }

    fun setOnModelLoaded(listener: (Boolean) -> Unit) { onModelLoaded = listener }
    fun setOnEmotionChange(listener: (Emotion) -> Unit) { onEmotionChange = listener }
    fun setOnModelInfo(listener: (Float, Float, Float) -> Unit) { onModelInfo = listener }

    private fun addLog(msg: String) {
        logLines.add("[${dateFormat.format(Date())}] $msg")
        if (logLines.size > 500) logLines.removeAt(0)
        android.util.Log.d("Live2DWebView", msg)
    }

    private fun buildLive2DHtml(modelFileName: String): String {
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                html, body { width: 100%; height: 100%; overflow: hidden; background: transparent; touch-action: none; }
                #live2d-canvas { width: 100%; height: 100%; display: block; touch-action: none; }
                #loading { position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); color: #667eea; font-size: 14px; font-family: monospace; text-align: center; max-width: 90%; word-wrap: break-word; }
                #loading.error { color: #f44336; }
            </style>
        </head>
        <body>
            <div id="loading">[0] Loading...</div>
            <canvas id="live2d-canvas"></canvas>
            <script src="file:///android_asset/js/live2dcubismcore.min.js"></script>
            <script src="file:///android_asset/js/live2d.min.js"></script>
            <script src="file:///android_asset/js/pixi.min.js"></script>
            <script src="file:///android_asset/js/pixi-live2d-display.min.js"></script>
            <script>
                var loadStep = 0;
                var loadingEl = document.getElementById('loading');
                var canvas = document.getElementById('live2d-canvas');
                var app = null, model = null;
                var errors = [];
                var canvasW = 0, canvasH = 0;
                var baseScale = 1.0;
                var modelNaturalW = 0, modelNaturalH = 0;

                var lookTargetX = 0, lookTargetY = 0;
                var currentLookX = 0, currentLookY = 0;
                var isLooking = false;
                var lookTimeout = null, lookAnimFrame = null;

                function cancelLook() {
                    isLooking = false;
                    if (lookAnimFrame) { cancelAnimationFrame(lookAnimFrame); lookAnimFrame = null; }
                    if (lookTimeout) { clearTimeout(lookTimeout); lookTimeout = null; }
                    lookTargetX = 0; lookTargetY = 0;
                    currentLookX = 0; currentLookY = 0;
                    try {
                        if (model && model.internalModel && model.internalModel.coreModel) {
                            var core = model.internalModel.coreModel;
                            ['ParamAngleX','ParamAngleY','ParamEyeBallX','ParamEyeBallY'].forEach(function(p) {
                                try { if (core.getParameterIndex(p) !== -1) core.setParameterValueById(p, 0); } catch(e) {}
                            });
                        }
                    } catch(e) {}
                }

                function step(msg) {
                    loadStep++;
                    var text = '[' + loadStep + '] ' + msg;
                    console.log('[L2D] ' + text);
                    if (loadingEl && !loadingEl.classList.contains('error')) loadingEl.textContent = text;
                }

                function err(msg) {
                    errors.push(msg);
                    console.error('[L2D] ' + msg);
                    loadingEl.className = 'error';
                    loadingEl.textContent = 'ERROR:\n' + errors.join('\n');
                }

                var checkCount = 0;
                function checkReady() {
                    checkCount++;
                    if (checkCount > 50) { err('PIXI or live2d-display failed to load after timeout'); return; }
                    if (typeof PIXI === 'undefined') { step('Wait PIXI...'); setTimeout(checkReady, 200); return; }
                    step('PIXI ' + PIXI.VERSION + ' OK');
                    if (typeof PIXI.live2d === 'undefined') { step('Wait live2d-display...'); setTimeout(checkReady, 200); return; }
                    step('live2d-display OK');
                    initLive2D();
                }

                function initLive2D() {
                    try {
                        step('Create PIXI app...');
                        var w = window.innerWidth;
                        var h = window.innerHeight;
                        canvasW = w; canvasH = h;
                        app = new PIXI.Application({
                            view: canvas, width: w, height: h, backgroundAlpha: 0,
                            antialias: true, autoStart: true,
                            resolution: window.devicePixelRatio || 1
                        });
                        step('OK');
                        var modelPath = '$modelFileName';
                        step('Model: ' + modelPath);
                        step('Loading...');
                        PIXI.live2d.Live2DModel.from(modelPath, {
                            autoInteract: false, autoUpdate: true
                        }).then(function(m) {
                            step('LOADED!');
                            model = m;
                            modelNaturalW = m.width; modelNaturalH = m.height;
                            step('Size: ' + m.width + 'x' + m.height);
                            m.anchor.set(0.5, 0.5);
                            var sx = w / m.width, sy = h / m.height;
                            baseScale = Math.min(sx, sy) * 0.8;
                            m.scale.set(baseScale);
                            m.x = w / 2; m.y = h / 2;
                            app.stage.addChild(m);
                            loadingEl.style.display = 'none';
                            step('Done!');
                            if (window.Live2DBridge) {
                                window.Live2DBridge.onModelInfo(modelNaturalW, modelNaturalH, baseScale);
                                window.Live2DBridge.onModelLoaded(true);
                            }
                        }).catch(function(e) {
                            var errMsg = e.message || e.toString();
                            if (e.stack) errMsg += '\\nStack: ' + e.stack.substring(0, 800);
                            err('Load failed: ' + errMsg);
                            try {
                                var req = new XMLHttpRequest();
                                req.open('GET', modelPath, false);
                                req.send();
                                step('Model JSON status: ' + req.status + ' len:' + req.responseText.length);
                                var modelData = JSON.parse(req.responseText);
                                var texRefs = modelData.FileReferences && modelData.FileReferences.Textures;
                                if (texRefs && texRefs.length > 0) {
                                    step('Textures referenced: ' + texRefs.length);
                                    texRefs.forEach(function(tex, idx) {
                                        try {
                                            var tReq = new XMLHttpRequest();
                                            tReq.open('HEAD', tex, false);
                                            tReq.send();
                                            step('Texture[' + idx + '] ' + tex + ' status=' + tReq.status);
                                        } catch(texEx) {
                                            step('Texture[' + idx + '] ' + tex + ' ERROR: ' + texEx.message);
                                        }
                                    });
                                }
                            } catch(ex) { step('Model JSON check failed: ' + ex.message); }
                            if (window.Live2DBridge) window.Live2DBridge.onModelLoaded(false);
                        });
                    } catch (e) {
                        var errMsg = e.message || e.toString();
                        if (e.stack) errMsg += '\\nStack: ' + e.stack.substring(0, 300);
                        err('Init error: ' + errMsg);
                        if (window.Live2DBridge) window.Live2DBridge.onModelLoaded(false);
                    }
                }

                window.tapModel = function(screenX, screenY) {
                    if (!model) return;
                    try { model.motion('Tap'); } catch(e) {}
                    lookAt(screenX, screenY);
                };

                function lerp(a, b, t) { return a + (b - a) * t; }

                function animateLook() {
                    if (!model || !isLooking) return;
                    currentLookX = lerp(currentLookX, lookTargetX, 0.08);
                    currentLookY = lerp(currentLookY, lookTargetY, 0.08);
                    try {
                        var core = model.internalModel.coreModel;
                        if (core.getParameterIndex('ParamAngleX') !== -1) core.setParameterValueById('ParamAngleX', currentLookX * 30);
                        if (core.getParameterIndex('ParamAngleY') !== -1) core.setParameterValueById('ParamAngleY', currentLookY * 30);
                        if (core.getParameterIndex('ParamEyeBallX') !== -1) core.setParameterValueById('ParamEyeBallX', currentLookX);
                        if (core.getParameterIndex('ParamEyeBallY') !== -1) core.setParameterValueById('ParamEyeBallY', currentLookY);
                    } catch(e) {}
                    if (Math.abs(currentLookX - lookTargetX) > 0.01 || Math.abs(currentLookY - lookTargetY) > 0.01) {
                        lookAnimFrame = requestAnimationFrame(animateLook);
                    } else isLooking = false;
                }

                function lookAt(screenX, screenY) {
                    if (!model) return;
                    var rect = canvas.getBoundingClientRect();
                    var relX = (screenX - rect.left) / rect.width - 0.5;
                    var relY = (screenY - rect.top) / rect.height - 0.5;
                    lookTargetX = relX * 2; lookTargetY = -relY * 2;
                    isLooking = true;
                    if (lookAnimFrame) cancelAnimationFrame(lookAnimFrame);
                    animateLook();
                    if (lookTimeout) clearTimeout(lookTimeout);
                    lookTimeout = setTimeout(function() {
                        lookTargetX = 0; lookTargetY = 0; isLooking = true;
                        if (lookAnimFrame) cancelAnimationFrame(lookAnimFrame);
                        animateLook();
                    }, 3000);
                }

                window.setLive2DEmotion = function(emotion) {
                    if (!model) return;
                    try {
                        var expressions = model.internalModel.settings.expressions || [];
                        if (expressions.length > 0) {
                            var best = null;
                            for (var i = 0; i < expressions.length; i++) {
                                var nm = (expressions[i].name || '').toLowerCase();
                                if (nm === emotion || nm.indexOf(emotion) !== -1 || emotion.indexOf(nm) !== -1) {
                                    best = expressions[i].name; break;
                                }
                            }
                            if (best) { model.expression(best); return; }
                        }
                    } catch(e) {}
                };

                window.triggerLive2DAction = function(action) {
                    if (!model) return;
                    try {
                        var motions = model.internalModel.settings.motions || {};
                        var keys = Object.keys(motions);
                        if (motions['TapBody'] && motions['TapBody'].length > 0) model.motion('TapBody');
                        else if (motions['Tap'] && motions['Tap'].length > 0) model.motion('Tap');
                        else if (keys.length > 0) model.motion(keys[0]);
                    } catch(e) {}
                };

                window.setAdjustMode = function(enabled) { console.log('[L2D] setAdjustMode(' + enabled + ')'); };
                window.applyModelTransform = function() {};
                window.resetModelPosition = function() {};

                window.setModelScale = function(scaleMultiplier) {
                    if (!model) return;
                    try {
                        var newScale = baseScale * scaleMultiplier;
                        model.scale.set(newScale);
                        console.log('[L2D] setModelScale: base=' + baseScale + ' mul=' + scaleMultiplier + ' result=' + newScale);
                    } catch(e) {
                        console.error('[L2D] setModelScale error: ' + e.message);
                    }
                };

                window.getModelScale = function() {
                    if (!model) return 1.0;
                    try {
                        return model.scale.x / baseScale;
                    } catch(e) { return 1.0; }
                };

                if (typeof PIXI !== 'undefined' && typeof PIXI.live2d !== 'undefined') initLive2D();
                else window.addEventListener('load', function() { setTimeout(checkReady, 100); });
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    fun loadLocalModel() {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val modelPath = prefs.getString("model_local_path", null)
        addLog("loadLocalModel: model_local_path = $modelPath")

        if (modelPath != null && File(modelPath).exists()) {
            addLog("Loading saved local model: $modelPath")
            loadLive2DModelFromPath(modelPath)
            return
        }

        val defaultPath = context.filesDir.path + "/live2d/Hiyori/Hiyori.model3.json"
        addLog("Checking default path: $defaultPath")
        if (File(defaultPath).exists()) {
            addLog("Loading default model")
            loadLive2DModelFromPath(defaultPath)
            return
        }

        addLog("Default model not found at: $defaultPath")
        addLog("Searching for models in: ${context.filesDir.path}")
        try {
            val filesDir = context.filesDir
            val foundModels = filesDir.walkTopDown().filter { it.name.endsWith(".model3.json") || it.name.endsWith(".model.json") }.toList()
            if (foundModels.isNotEmpty()) {
                val firstModel = foundModels.first()
                addLog("  Found ${foundModels.size} model(s), loading first: ${firstModel.absolutePath}")
                loadLive2DModelFromPath(firstModel.absolutePath)
            } else {
                addLog("  No models found")
            }
        } catch (e: Exception) {
            addLog("  Search error: ${e.message}")
        }
    }

    fun loadDefaultModel() {
        val defaultPath = context.filesDir.path + "/live2d/Hiyori/Hiyori.model3.json"
        if (File(defaultPath).exists()) {
            loadLive2DModelFromPath(defaultPath)
        } else {
            addLog("Default model not found")
        }
    }

    override fun destroy() {
        isDestroyed = true
        logLines.clear()
        textureCache.clear()
        onModelLoaded = null
        onEmotionChange = null
        onActionTrigger = null
        try {
            stopLoading()
            post { try { loadUrl("about:blank") } catch (_: Exception) {} }
        } catch (e: Exception) {}
        super.destroy()
    }

    fun reloadView() {
        try {
            post {
                if (!isDestroyed) {
                    super.reload()
                }
            }
        } catch (_: Exception) { }
    }

    inner class Live2DBridge {
        @JavascriptInterface
        fun onModelLoaded(success: Boolean) {
            if (isDestroyed) return
            addLog("Callback: success=$success")
            post { onModelLoaded?.invoke(success) }
        }

        @JavascriptInterface
        fun onModelInfo(width: Float, height: Float, baseScale: Float) {
            if (isDestroyed) return
            post { onModelInfo?.invoke(width, height, baseScale) }
        }

        @JavascriptInterface
        fun onEmotionChanged(emotion: String) {
            if (isDestroyed) return
            post { try { onEmotionChange?.invoke(Emotion.valueOf(emotion.uppercase())) } catch (e: Exception) {} }
        }

        @JavascriptInterface
        fun onActionTriggered(action: String) {
            if (isDestroyed) return
            post { try { onActionTrigger?.invoke(Action.valueOf(action.uppercase())) } catch (e: Exception) {} }
        }
    }
}
