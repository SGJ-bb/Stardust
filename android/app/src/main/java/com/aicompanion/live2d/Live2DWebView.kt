/** Live2D WebView核心容器: 基于WebView的Live2D渲染容器, 加载Cubism SDK for Web, 提供JS接口控制表情/动作/日志, 支持模型下载/解压/缓存/截图 */
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
import fi.iki.elonen.NanoHTTPD
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
    private var localServer: NanoHTTPD? = null
    private var serverPort: Int = 0

    private val logLines = Collections.synchronizedList(mutableListOf<String>())
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS")
    private val maxTextureSize by lazy { detectMaxTextureSize() }
    private val textureCache = Collections.synchronizedMap(mutableMapOf<String, File>())

    private fun detectMaxTextureSize(): Int {
        return try {
            val egl = javax.microedition.khronos.egl.EGLContext.getEGL() as javax.microedition.khronos.egl.EGL10
            val display = egl.eglGetDisplay(javax.microedition.khronos.egl.EGL10.EGL_DEFAULT_DISPLAY)
            val version = IntArray(2)
            egl.eglInitialize(display, version)
            val configAttribs = intArrayOf(
                0x3040, 4,
                javax.microedition.khronos.egl.EGL10.EGL_RED_SIZE, 8,
                javax.microedition.khronos.egl.EGL10.EGL_GREEN_SIZE, 8,
                javax.microedition.khronos.egl.EGL10.EGL_BLUE_SIZE, 8,
                javax.microedition.khronos.egl.EGL10.EGL_ALPHA_SIZE, 8,
                javax.microedition.khronos.egl.EGL10.EGL_NONE
            )
            val configs = arrayOfNulls<javax.microedition.khronos.egl.EGLConfig>(1)
            val numConfigs = IntArray(1)
            egl.eglChooseConfig(display, configAttribs, configs, 1, numConfigs)
            val contextAttribs = intArrayOf(0x3098, 2, javax.microedition.khronos.egl.EGL10.EGL_NONE)
            val context = egl.eglCreateContext(display, configs[0], javax.microedition.khronos.egl.EGL10.EGL_NO_CONTEXT, contextAttribs)
            val surfaceAttribs = intArrayOf(javax.microedition.khronos.egl.EGL10.EGL_WIDTH, 1, javax.microedition.khronos.egl.EGL10.EGL_HEIGHT, 1, javax.microedition.khronos.egl.EGL10.EGL_NONE)
            val surface = egl.eglCreatePbufferSurface(display, configs[0], surfaceAttribs)
            egl.eglMakeCurrent(display, surface, surface, context)
            val maxTex = IntArray(1)
            android.opengl.GLES20.glGetIntegerv(android.opengl.GLES20.GL_MAX_TEXTURE_SIZE, maxTex, 0)
            egl.eglMakeCurrent(display, javax.microedition.khronos.egl.EGL10.EGL_NO_SURFACE, javax.microedition.khronos.egl.EGL10.EGL_NO_SURFACE, javax.microedition.khronos.egl.EGL10.EGL_NO_CONTEXT)
            egl.eglDestroySurface(display, surface)
            egl.eglDestroyContext(display, context)
            egl.eglTerminate(display)
            addLog("GPU maxTextureSize detected: ${maxTex[0]}")
            maxTex[0].coerceIn(2048, 8192)
        } catch (e: Exception) {
            addLog("GPU detection failed: ${e.message}, using 4096")
            4096
        }
    }

    private inner class LocalModelServer : NanoHTTPD("127.0.0.1", 0) {
        override fun serve(session: IHTTPSession): Response {
            val uri = session.uri.trimStart('/')
            addLog("HTTP ${session.method} /$uri")

            if (session.method == Method.OPTIONS) {
                return newFixedLengthResponse(Response.Status.OK, "text/plain", "").apply {
                    addHeader("Access-Control-Allow-Origin", "*")
                    addHeader("Access-Control-Allow-Methods", "GET, OPTIONS")
                    addHeader("Access-Control-Allow-Headers", "*")
                }
            }

            if (uri.startsWith("js/") || uri.startsWith("css/")) {
                try {
                    val input = context.assets.open(uri)
                    val mime = serverMime(uri)
                    val bytes = input.readBytes()
                    input.close()
                    return newFixedLengthResponse(Response.Status.OK, mime, java.io.ByteArrayInputStream(bytes), bytes.size.toLong()).apply {
                        addHeader("Access-Control-Allow-Origin", "*")
                    }
                } catch (e: Exception) {
                    addLog("Server asset miss: $uri")
                    return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Asset not found: $uri")
                }
            }

            if (modelBasePath.isNotEmpty()) {
                val modelDir = File(modelBasePath)
                val decodedUri = URLDecoder.decode(uri, "UTF-8")
                val file = File(modelDir, decodedUri)
                if (file.exists() && file.isFile) {
                    try {
                        val canonical = file.canonicalPath
                        if (canonical.startsWith(modelDir.canonicalPath)) {
                            val mime = serverMime(uri)
                            val fis = file.inputStream()
                            return newFixedLengthResponse(Response.Status.OK, mime, fis, file.length()).apply {
                                addHeader("Access-Control-Allow-Origin", "*")
                                addHeader("Content-Length", file.length().toString())
                            }
                        }
                    } catch (e: Exception) {
                        addLog("Server file error: ${e.message}")
                    }
                }

                val fileName = File(decodedUri).name
                val found = findFileByName(fileName, modelDir)
                if (found != null && found.exists() && found.isFile) {
                    try {
                        val mime = serverMime(fileName)
                        val fis = found.inputStream()
                        return newFixedLengthResponse(Response.Status.OK, mime, fis, found.length()).apply {
                            addHeader("Access-Control-Allow-Origin", "*")
                        }
                    } catch (e: Exception) {
                        addLog("Server fallback error: ${e.message}")
                    }
                }

                val parentDir = modelDir.parentFile
                if (parentDir != null) {
                    val parentFile = File(parentDir, decodedUri)
                    if (parentFile.exists() && parentFile.isFile) {
                        try {
                            val canonical = parentFile.canonicalPath
                            if (canonical.startsWith(parentDir.canonicalPath)) {
                                val mime = serverMime(uri)
                                val fis = parentFile.inputStream()
                                return newFixedLengthResponse(Response.Status.OK, mime, fis, parentFile.length()).apply {
                                    addHeader("Access-Control-Allow-Origin", "*")
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            }

            addLog("Server 404: $uri")
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found: $uri")
        }

        private fun serverMime(path: String): String {
            val p = path.lowercase()
            return when {
                p.endsWith(".js") -> "application/javascript"
                p.endsWith(".json") -> "application/json"
                p.endsWith(".png") -> "image/png"
                p.endsWith(".jpg") || p.endsWith(".jpeg") -> "image/jpeg"
                p.endsWith(".webp") -> "image/webp"
                p.endsWith(".moc3") || p.endsWith(".moc") -> "application/octet-stream"
                p.endsWith(".mp3") -> "audio/mpeg"
                p.endsWith(".ogg") -> "audio/ogg"
                p.endsWith(".wav") -> "audio/wav"
                p.endsWith(".m4a") -> "audio/mp4"
                p.endsWith(".html") -> "text/html"
                p.endsWith(".css") -> "text/css"
                else -> "application/octet-stream"
            }
        }
    }

    private fun ensureServerRunning() {
        if (localServer == null || !localServer!!.isAlive) {
            try {
                localServer = LocalModelServer().apply {
                    start()
                    serverPort = listeningPort
                }
                addLog("Local HTTP server started on port $serverPort")
            } catch (e: Exception) {
                addLog("Failed to start server: ${e.message}")
                serverPort = 0
            }
        }
    }

    fun cleanup() {
        try {
            localServer?.stop()
        } catch (_: Exception) {}
        localServer = null
        serverPort = 0
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
                allowUniversalAccessFromFileURLs = false
                allowFileAccessFromFileURLs = false
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
                private val corsHeaders = mapOf(
                    "Access-Control-Allow-Origin" to "*",
                    "Access-Control-Allow-Methods" to "GET, OPTIONS",
                    "Access-Control-Allow-Headers" to "*"
                )

                private fun buildResponse(mime: String, encoding: String?, input: java.io.InputStream, fileLength: Long = -1): WebResourceResponse {
                    val headers = mutableMapOf<String, String>(
                        "Access-Control-Allow-Origin" to "*"
                    )
                    if (fileLength > 0) {
                        headers["Content-Length"] = fileLength.toString()
                    }
                    return WebResourceResponse(mime, encoding, 200, "OK", headers, input)
                }

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
                        if (url.startsWith("data:")) return null
                        if (url.startsWith("http://127.0.0.1") || url.startsWith("http://localhost")) return null

                        if (request.method.equals("OPTIONS", ignoreCase = true)) {
                            return WebResourceResponse("text/plain", "UTF-8", 200, "OK", corsHeaders, java.io.ByteArrayInputStream(ByteArray(0)))
                        }

                        if (url.startsWith("file:///android_asset/")) {
                            val raw = url.removePrefix("file:///android_asset/")
                            val path = URLDecoder.decode(raw, "UTF-8")
                            try {
                                val input = context.assets.open(path)
                                val mime = getMime(path)
                                return buildResponse(mime, getEncoding(mime), input)
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
                                addLog("SERVE: ${file.name} mime=$mime size=${file.length()}")
                                return buildResponse(mime, getEncoding(mime), java.io.BufferedInputStream(file.inputStream()), file.length())
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
                                addLog("SERVE(resolved): ${foundFile.name} mime=$mime size=${foundFile.length()}")
                                return buildResponse(mime, getEncoding(mime), java.io.BufferedInputStream(foundFile.inputStream()), foundFile.length())
                            }

                            val parentDir = modelDir.parentFile
                            if (parentDir != null) {
                                foundFile = resolveResourcePath(requestedPath, parentDir)
                                if (foundFile != null && foundFile.exists()) {
                                    val mime = getMime(foundFile.name)
                                    addLog("SERVE(parent): ${foundFile.name} mime=$mime size=${foundFile.length()}")
                                    return buildResponse(mime, getEncoding(mime), java.io.BufferedInputStream(foundFile.inputStream()), foundFile.length())
                                }
                            }

                            val justName = file.name
                            foundFile = resolveResourcePath(justName, modelDir)
                            if (foundFile != null && foundFile.exists()) {
                                val mime = getMime(foundFile.name)
                                addLog("SERVE(name): ${foundFile.name} mime=$mime size=${foundFile.length()}")
                                return buildResponse(mime, getEncoding(mime), java.io.BufferedInputStream(foundFile.inputStream()), foundFile.length())
                            }

                            if (parentDir != null) {
                                foundFile = resolveResourcePath(justName, parentDir)
                                if (foundFile != null && foundFile.exists()) {
                                    val mime = getMime(foundFile.name)
                                    addLog("SERVE(parentName): ${foundFile.name} mime=$mime size=${foundFile.length()}")
                                    return buildResponse(mime, getEncoding(mime), java.io.BufferedInputStream(foundFile.inputStream()), foundFile.length())
                                }
                            }

                            if (ext in listOf("mp3", "ogg", "wav", "m4a")) {
                                val audioMime = when (ext) {
                                    "ogg" -> "audio/ogg"
                                    "wav" -> "audio/wav"
                                    "m4a" -> "audio/mp4"
                                    else -> "audio/mpeg"
                                }
                                return buildResponse(audioMime, null,
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
            var copyCount = 0
            var skipCount = 0
            srcDir.walkTopDown().filter { it.isFile }.forEach { srcFile ->
                try {
                    val relPath = srcFile.absolutePath.substring(srcDir.absolutePath.length).trimStart('/', '\\')
                    if (shouldSkipFile(relPath, srcFile.name)) {
                        skipCount++
                        addLog("SKIP: $relPath")
                        return@forEach
                    }
                    val dstFile = File(localDir, relPath)
                    dstFile.parentFile?.mkdirs()
                    srcFile.inputStream().use { input ->
                        dstFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    copyCount++
                    if (srcFile.name.endsWith(".png") || srcFile.name.endsWith(".moc3") || srcFile.name.endsWith(".json")) {
                        addLog("COPY: $relPath (${srcFile.length()} bytes)")
                    }
                } catch (e: Exception) {
                    addLog("Copy warn: ${srcFile.name} - ${e.message}")
                }
            }
            addLog("Copy done: $copyCount files copied, $skipCount skipped")

            // Validate cache integrity
            val cacheFiles = localDir.walkTopDown().filter { it.isFile }.toList()
            addLog("Cache validation: ${cacheFiles.size} files in cache dir ${localDir.name}")
            val modelJsonInCache = cacheFiles.find { it.name.endsWith(".model3.json") || it.name.endsWith(".model.json") }
            if (modelJsonInCache == null) {
                addLog("ERROR: No model JSON found in cache after copy!")
                onModelLoaded?.invoke(false)
                return
            }
            val mocInCache = cacheFiles.find { it.name.endsWith(".moc3") || it.name.endsWith(".moc") }
            if (mocInCache == null) {
                addLog("WARN: No .moc3/.moc file found in cache")
            }
            val texturesInCache = cacheFiles.filter { it.extension.lowercase() in listOf("png", "jpg", "jpeg") }
            addLog("Cache contents: modelJson=${modelJsonInCache.name}, moc=${mocInCache?.name ?: "NONE"}, textures=${texturesInCache.size}")

            val preCompressTextures = localDir.walkTopDown().filter { it.isFile && it.extension.lowercase() in listOf("png", "jpg", "jpeg") }.toList()
            addLog("Pre-compress: ${preCompressTextures.size} texture files")
            preCompressTextures.forEach { addLog("  SRC TEX: ${it.relativeTo(localDir).path.replace("\\", "/")} ${it.length()}B") }

            checkAndCompressTextures(localDir)

            val postCompressTextures = localDir.walkTopDown().filter { it.isFile && it.extension.lowercase() in listOf("png", "jpg", "jpeg") }.toList()
            addLog("Post-compress: ${postCompressTextures.size} texture files remain")
            postCompressTextures.forEach { addLog("  TEX: ${it.relativeTo(localDir).path.replace("\\", "/")} ${it.length()}B") }

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

            verifyAndRepairCache(cachedModelJson, modelDir, srcDir)

            val html = buildLive2DHtml(cachedModelJson.name)
            val htmlFile = File(modelDir, "view.html")
            htmlFile.writeText(html, Charsets.UTF_8)

            ensureServerRunning()
            if (serverPort > 0) {
                addLog("Loading HTML: http://127.0.0.1:$serverPort/view.html")
                post {
                    if (!isDestroyed) {
                        loadUrl("about:blank")
                        postDelayed({
                            if (!isDestroyed) {
                                loadUrl("http://127.0.0.1:$serverPort/view.html")
                            }
                        }, 150)
                    }
                }
            } else {
                addLog("Fallback: Loading HTML: file://${htmlFile.absolutePath}")
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
        if (fileName.contains("Thumbs.db") || fileName.contains("Desktop.ini") || fileName.contains(".DS_Store")) return true
        if (fileName.endsWith(".log")) return true
        if (fileName.contains("readme", ignoreCase = true) && fileName.endsWith(".txt")) return true
        if (fileName.endsWith(".url")) return true
        if (fileName.endsWith(".lnk")) return true
        return false
    }

    private fun verifyAndRepairCache(modelJsonFile: File, modelDir: File, srcDir: File) {
        try {
            val content = modelJsonFile.readText()
            val json = org.json.JSONObject(content)
            val fileRefs = json.optJSONObject("FileReferences") ?: json.optJSONObject("file_references")
            if (fileRefs == null) return

            val allRefPaths = mutableListOf<String>()

            listOf("Moc", "moc").forEach { key ->
                val f = fileRefs.optString(key)
                if (f.isNotEmpty()) allRefPaths.add(f)
            }

            listOf("Textures", "textures").firstOrNull { fileRefs.has(it) }?.let { texKey ->
                fileRefs.optJSONArray(texKey)?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val f = arr.optString(i)
                        if (f.isNotEmpty()) allRefPaths.add(f)
                    }
                }
            }

            listOf("Physics", "physics", "DisplayInfo", "displayInfo", "display_info").forEach { key ->
                val f = fileRefs.optString(key)
                if (f.isNotEmpty()) allRefPaths.add(f)
            }

            fileRefs.optJSONArray("Expressions")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i)
                    val f = obj?.optString("File", obj.optString("file", "")) ?: ""
                    if (f.isNotEmpty()) allRefPaths.add(f)
                }
            }
            fileRefs.optJSONArray("expressions")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i)
                    val f = obj?.optString("File", obj.optString("file", "")) ?: ""
                    if (f.isNotEmpty()) allRefPaths.add(f)
                }
            }

            fileRefs.optJSONObject("Motions")?.let { motions ->
                motions.keys().forEach { group ->
                    motions.optJSONArray(group)?.let { arr ->
                        for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i)
                            val f = obj?.optString("File", obj.optString("file", "")) ?: ""
                            if (f.isNotEmpty()) allRefPaths.add(f)
                            val s = obj?.optString("Sound", obj.optString("sound", "")) ?: ""
                            if (s.isNotEmpty()) allRefPaths.add(s)
                        }
                    }
                }
            }
            fileRefs.optJSONObject("motions")?.let { motions ->
                motions.keys().forEach { group ->
                    motions.optJSONArray(group)?.let { arr ->
                        for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i)
                            val f = obj?.optString("File", obj.optString("file", "")) ?: ""
                            if (f.isNotEmpty()) allRefPaths.add(f)
                            val s = obj?.optString("Sound", obj.optString("sound", "")) ?: ""
                            if (s.isNotEmpty()) allRefPaths.add(s)
                        }
                    }
                }
            }

            var repairedCount = 0
            for (refPath in allRefPaths) {
                val cacheFile = File(modelDir, refPath)
                val isImage = refPath.lowercase().let { it.endsWith(".png") || it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".webp") }
                var needsRepair = !cacheFile.exists() || cacheFile.length() == 0L

                if (!needsRepair && isImage) {
                    try {
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(cacheFile.absolutePath, opts)
                        if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                            addLog("INTEGRITY CHECK: corrupted image $refPath (invalid decode)")
                            needsRepair = true
                        }
                    } catch (e: Exception) {
                        addLog("INTEGRITY CHECK: corrupted image $refPath (${e.message})")
                        needsRepair = true
                    }
                }

                if (needsRepair) {
                    addLog("INTEGRITY CHECK: repairing $refPath from source")
                    val srcFile = File(srcDir, refPath)
                    if (srcFile.exists() && srcFile.isFile && srcFile.length() > 0) {
                        try {
                            cacheFile.parentFile?.mkdirs()
                            srcFile.inputStream().use { input ->
                                cacheFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            repairedCount++
                            addLog("Repaired from source: $refPath (${srcFile.length()} bytes)")
                        } catch (e: Exception) {
                            addLog("Repair copy failed: $refPath - ${e.message}")
                            val found = findFileByName(File(refPath).name, srcDir)
                            if (found != null && found.exists() && found.length() > 0) {
                                try {
                                    cacheFile.parentFile?.mkdirs()
                                    found.inputStream().use { input ->
                                        cacheFile.outputStream().use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                    repairedCount++
                                    addLog("Repaired by name search: ${found.name} -> $refPath")
                                } catch (e2: Exception) {
                                    addLog("Repair by name also failed: ${e2.message}")
                                }
                            }
                        }
                    } else {
                        val found = findFileByName(File(refPath).name, srcDir)
                        if (found != null && found.exists() && found.length() > 0) {
                            try {
                                cacheFile.parentFile?.mkdirs()
                                found.inputStream().use { input ->
                                    cacheFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                repairedCount++
                                addLog("Repaired by name search: ${found.name} -> $refPath")
                            } catch (e: Exception) {
                                addLog("Repair by name failed: ${e.message}")
                            }
                        } else {
                            addLog("INTEGRITY CHECK: cannot repair $refPath, file not found in source")
                        }
                    }
                }
            }

            if (repairedCount > 0) {
                addLog("Integrity check: repaired $repairedCount file(s) from source")
            } else {
                addLog("Integrity check: all referenced files present")
            }
        } catch (e: Exception) {
            addLog("Integrity check error: ${e.message}")
        }
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
            var needsSave = false

            listOf("Moc", "moc").forEach { key ->
                val mocFile = fileRefs.optString(key)
                if (mocFile.isNotEmpty()) {
                    val mocPath = File(modelDir, mocFile)
                    if (!mocPath.exists()) {
                        val found = findFileByName(mocPath.name, modelDir)
                        if (found != null) {
                            val newPath = found.relativeTo(modelDir).path.replace("\\", "/")
                            fileRefs.put(key, newPath)
                            fixedRefs.add("$mocFile -> $newPath")
                            needsSave = true
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
                    val validTextures = org.json.JSONArray()
                    var removedCount = 0
                    var textureFixed = false
                    for (i in 0 until textures.length()) {
                        val texPath = textures.optString(i)
                        if (texPath.isNotEmpty()) {
                            val texFile = File(modelDir, texPath)
                            if (texFile.exists()) {
                                validTextures.put(texPath)
                            } else {
                                val found = findFileByName(texFile.name, modelDir)
                                if (found != null) {
                                    val newPath = found.relativeTo(modelDir).path.replace("\\", "/")
                                    validTextures.put(newPath)
                                    fixedRefs.add("$texPath -> $newPath")
                                    textureFixed = true
                                } else {
                                    addLog("Missing texture: $texPath")
                                    missingFiles.add(texPath)
                                    removedCount++
                                }
                            }
                        }
                    }
                    if (removedCount > 0 || textureFixed) {
                        fileRefs.put(texturesKey, validTextures)
                        needsSave = true
                        if (removedCount > 0) {
                            addLog("Removed $removedCount missing texture(s), ${validTextures.length()} remaining")
                        }
                        if (textureFixed) {
                            addLog("Fixed texture path(s)")
                        }
                    }
                    if (validTextures.length() == 0) {
                        addLog("ERROR: All textures are missing! Creating placeholder")
                        val placeholderFile = File(modelDir, "placeholder.png")
                        if (!placeholderFile.exists()) {
                            try {
                                val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                                bitmap.setPixel(0, 0, 0xFFFFFFFF.toInt())
                                placeholderFile.outputStream().use { out ->
                                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                                }
                                bitmap.recycle()
                            } catch (e: Exception) {
                                addLog("Failed to create placeholder: ${e.message}")
                            }
                        }
                        if (placeholderFile.exists()) {
                            validTextures.put("placeholder.png")
                            fileRefs.put(texturesKey, validTextures)
                            needsSave = true
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
                            val newPath = found.relativeTo(modelDir).path.replace("\\", "/")
                            fileRefs.put(key, newPath)
                            fixedRefs.add("$physFile -> $newPath")
                            needsSave = true
                        } else {
                            addLog("Missing physics file (non-critical): $physFile")
                        }
                    }
                }
            }

            listOf("DisplayInfo", "displayInfo", "display_info").forEach { key ->
                val dispFile = fileRefs.optString(key)
                if (dispFile.isNotEmpty()) {
                    val dispPath = File(modelDir, dispFile)
                    if (!dispPath.exists()) {
                        val found = findFileByName(dispPath.name, modelDir)
                        if (found != null) {
                            val newPath = found.relativeTo(modelDir).path.replace("\\", "/")
                            fileRefs.put(key, newPath)
                            fixedRefs.add("$dispFile -> $newPath")
                            needsSave = true
                        } else {
                            addLog("Missing DisplayInfo file (non-critical): $dispFile")
                        }
                    }
                }
            }

            val referencedExprFiles = mutableSetOf<String>()
            val expressionsKey = listOf("Expressions", "expressions").firstOrNull { fileRefs.has(it) }
            if (expressionsKey != null) {
                val expressions = fileRefs.optJSONArray(expressionsKey)
                if (expressions != null) {
                    val validExpressions = org.json.JSONArray()
                    var removedExpr = 0
                    var expressionFixed = false
                    for (i in 0 until expressions.length()) {
                        val exprObj = expressions.optJSONObject(i)
                        if (exprObj != null) {
                            val exprFile = exprObj.optString("File", exprObj.optString("file", ""))
                            if (exprFile.isNotEmpty()) {
                                referencedExprFiles.add(File(exprFile).name)
                                val exprPath = File(modelDir, exprFile)
                                if (exprPath.exists()) {
                                    validExpressions.put(exprObj)
                                } else {
                                    val found = findFileByName(exprPath.name, modelDir)
                                    if (found != null) {
                                        val newPath = found.relativeTo(modelDir).path.replace("\\", "/")
                                        val fileKeyName = if (exprObj.has("File")) "File" else "file"
                                        exprObj.put(fileKeyName, newPath)
                                        validExpressions.put(exprObj)
                                        fixedRefs.add("$exprFile -> $newPath")
                                        expressionFixed = true
                                    } else {
                                        addLog("Missing expression removed: $exprFile")
                                        removedExpr++
                                    }
                                }
                            } else {
                                validExpressions.put(exprObj)
                            }
                        }
                    }
                    if (removedExpr > 0 || expressionFixed) {
                        fileRefs.put(expressionsKey, validExpressions)
                        needsSave = true
                        if (removedExpr > 0) {
                            addLog("Removed $removedExpr missing expression(s), ${validExpressions.length()} remaining")
                        }
                        if (expressionFixed) {
                            addLog("Fixed expression path(s)")
                        }
                    }
                }
            }

            val discoveredExprFiles = modelDir.walkTopDown()
                .filter { it.isFile && (it.name.endsWith(".exp3.json") || it.name.endsWith(".exp.json")) }
                .filter { it.name !in referencedExprFiles }
                .toList()
            if (discoveredExprFiles.isNotEmpty()) {
                addLog("Discovered ${discoveredExprFiles.size} unreferenced expression file(s)")
                val exprKey = expressionsKey ?: "Expressions"
                val existingArr = fileRefs.optJSONArray(exprKey) ?: org.json.JSONArray()
                for (exprFile in discoveredExprFiles) {
                    val relPath = exprFile.relativeTo(modelDir).path.replace("\\", "/")
                    val exprName = exprFile.name.removeSuffix(".exp3.json").removeSuffix(".exp.json")
                    val exprObj = org.json.JSONObject().apply {
                        put("Name", exprName)
                        put("File", relPath)
                    }
                    existingArr.put(exprObj)
                    addLog("Auto-added expression: $exprName -> $relPath")
                }
                fileRefs.put(exprKey, existingArr)
                needsSave = true
            }

            val referencedMotionFiles = mutableSetOf<String>()
            val motionsObj = fileRefs.optJSONObject("Motions") ?: fileRefs.optJSONObject("motions")
            if (motionsObj != null) {
                val motionKeys = motionsObj.keys()
                while (motionKeys.hasNext()) {
                    val groupKey = motionKeys.next()
                    val motionArr = motionsObj.optJSONArray(groupKey)
                    if (motionArr != null) {
                        val validMotions = org.json.JSONArray()
                        var removedMotions = 0
                        var motionFixed = false
                        for (i in 0 until motionArr.length()) {
                            val motionObj = motionArr.optJSONObject(i)
                            if (motionObj != null) {
                                var motionValid = true
                                val motionFile = motionObj.optString("File", motionObj.optString("file", ""))
                                if (motionFile.isNotEmpty()) {
                                    referencedMotionFiles.add(File(motionFile).name)
                                    val motionPath = File(modelDir, motionFile)
                                    if (!motionPath.exists()) {
                                        val found = findFileByName(motionPath.name, modelDir)
                                        if (found != null) {
                                            val newPath = found.relativeTo(modelDir).path.replace("\\", "/")
                                            val fileKeyName = if (motionObj.has("File")) "File" else "file"
                                            motionObj.put(fileKeyName, newPath)
                                            fixedRefs.add("$motionFile -> $newPath")
                                            motionFixed = true
                                        } else {
                                            addLog("Missing motion removed: $motionFile in group $groupKey")
                                            removedMotions++
                                            motionValid = false
                                        }
                                    }
                                }

                                if (motionValid) {
                                    val soundFile = motionObj.optString("Sound", motionObj.optString("sound", ""))
                                    if (soundFile.isNotEmpty()) {
                                        val soundPath = File(modelDir, soundFile)
                                        if (!soundPath.exists()) {
                                            val found = findFileByName(soundPath.name, modelDir)
                                            if (found != null) {
                                                val newPath = found.relativeTo(modelDir).path.replace("\\", "/")
                                                val soundKeyName = if (motionObj.has("Sound")) "Sound" else "sound"
                                                motionObj.put(soundKeyName, newPath)
                                                fixedRefs.add("$soundFile -> $newPath")
                                                motionFixed = true
                                            } else {
                                                addLog("Missing sound file (non-critical): $soundFile in group $groupKey")
                                            }
                                        }
                                    }
                                    validMotions.put(motionObj)
                                }
                            }
                        }
                        if (removedMotions > 0 || motionFixed) {
                            motionsObj.put(groupKey, validMotions)
                            needsSave = true
                            if (removedMotions > 0) {
                                addLog("Removed $removedMotions missing motion(s) in group $groupKey, ${validMotions.length()} remaining")
                            }
                            if (motionFixed) {
                                addLog("Fixed motion path(s) in group $groupKey")
                            }
                        }
                    }
                }
            }

            val discoveredMotionFiles = modelDir.walkTopDown()
                .filter { it.isFile && (it.name.endsWith(".motion3.json") || it.name.endsWith(".motion.json")) }
                .filter { it.name !in referencedMotionFiles }
                .toList()
            if (discoveredMotionFiles.isNotEmpty()) {
                addLog("Discovered ${discoveredMotionFiles.size} unreferenced motion file(s)")
                val motionsKey = if (json.optJSONObject("FileReferences")?.has("Motions") == true) "Motions" else "motions"
                val existingMotions = fileRefs.optJSONObject(motionsKey) ?: org.json.JSONObject()
                val idleArr = existingMotions.optJSONArray("Idle") ?: org.json.JSONArray()
                for ((idx, motionFile) in discoveredMotionFiles.withIndex()) {
                    val relPath = motionFile.relativeTo(modelDir).path.replace("\\", "/")
                    val motionName = motionFile.name.removeSuffix(".motion3.json").removeSuffix(".motion.json")
                    val motionObj = org.json.JSONObject().apply {
                        put("File", relPath)
                        put("FadeInTime", 0.5)
                        put("FadeOutTime", 0.5)
                    }
                    if (idx == 0) {
                        idleArr.put(motionObj)
                        addLog("Auto-added idle motion: $motionName -> $relPath")
                    } else {
                        val tapArr = existingMotions.optJSONArray("TapBody") ?: org.json.JSONArray()
                        tapArr.put(motionObj)
                        existingMotions.put("TapBody", tapArr)
                        addLog("Auto-added tap motion: $motionName -> $relPath")
                    }
                }
                existingMotions.put("Idle", idleArr)
                fileRefs.put(motionsKey, existingMotions)
                needsSave = true
            }

            if (needsSave || missingFiles.isNotEmpty()) {
                modelJsonFile.writeText(json.toString(2))
                addLog("Model JSON saved with fixes")
            }

            if (fixedRefs.isNotEmpty()) {
                addLog("Fixed refs: ${fixedRefs.joinToString(", ")}")
            }

            if (missingFiles.isNotEmpty()) {
                addLog("Missing critical files: ${missingFiles.joinToString(", ")}")
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

    private fun recoverTextureFromSystem(texFileName: String, modelDir: File): String? {
        return null
    }

    private fun recoverAllTexturesFromSystem(modelDir: File): List<String> {
        return emptyList()
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

            val cachedModelJson = findModelJsonRecursive(localDir)
            if (cachedModelJson == null) {
                addLog("ERROR: model JSON not found in cache after asset copy")
                onModelLoaded?.invoke(false)
                return
            }

            val modelDirFile = cachedModelJson.parentFile ?: localDir
            modelBasePath = modelDirFile.absolutePath
            textureCache.clear()

            validateAndFixModel(cachedModelJson, modelDirFile)

            val modelJsonFile = modelJsonPath.substringAfterLast("/")
            val html = buildLive2DHtml(modelJsonFile)
            val htmlFile = File(modelDirFile, "view.html")
            htmlFile.writeText(html, Charsets.UTF_8)

            ensureServerRunning()
            if (serverPort > 0) {
                addLog("Loading from assets via HTTP: http://127.0.0.1:$serverPort/view.html")
                post {
                    if (!isDestroyed) {
                        loadUrl("about:blank")
                        postDelayed({
                            if (!isDestroyed) {
                                loadUrl("http://127.0.0.1:$serverPort/view.html")
                            }
                        }, 100)
                    }
                }
            } else {
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
            }
        } catch (e: Exception) {
            addLog("Exception: ${e.message}")
            onModelLoaded?.invoke(false)
        }
    }

    private fun checkAndCompressTextures(modelDir: File) {
        try {
            addLog("Preprocessing textures in: ${modelDir.name}, maxTextureSize=$maxTextureSize")

            val textureFiles = modelDir.walkTopDown()
                .filter { it.isFile }
                .filter { it.extension.equals("png", ignoreCase = true) || it.extension.equals("jpg", ignoreCase = true) || it.extension.equals("jpeg", ignoreCase = true) }
                .filter { !it.absolutePath.contains("_backup") && !it.absolutePath.contains("_tex_backup") }
                .toList()

            addLog("Found ${textureFiles.size} texture file(s)")

            val needsResize = mutableListOf<File>()
            for ((index, file) in textureFiles.withIndex()) {
                try {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.absolutePath, options)
                    if (options.outWidth <= 0 || options.outHeight <= 0) {
                        addLog("Cannot decode bounds: ${file.name}, skipping")
                        continue
                    }
                    val w = options.outWidth
                    val h = options.outHeight
                    val tooLarge = w > maxTextureSize || h > maxTextureSize
                    addLog("Texture[$index]: ${file.name} ${w}x${h} tooLarge=$tooLarge max=$maxTextureSize")
                    if (tooLarge) needsResize.add(file)
                } catch (e: Throwable) {
                    addLog("Bounds error ${file.name}: ${e.message}")
                }
            }

            if (needsResize.isEmpty()) {
                addLog("No textures need resizing")
                return
            }

            addLog("${needsResize.size} texture(s) need resizing to fit maxTextureSize=$maxTextureSize")

            val backupDir = File(modelDir, "_tex_backup")
            backupDir.mkdirs()

            for (file in needsResize) {
                try {
                    val backupFile = File(backupDir, file.relativeTo(modelDir).path.replace("\\", "/").replace("/", "_"))
                    backupFile.parentFile?.mkdirs()
                    file.inputStream().use { input -> backupFile.outputStream().use { output -> input.copyTo(output) } }
                    addLog("Backed up: ${file.name} -> ${backupFile.name}")
                } catch (e: Throwable) {
                    addLog("Backup FAILED for ${file.name}: ${e.message}, skipping compression")
                    continue
                }

                try {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.absolutePath, options)
                    val w = options.outWidth
                    val h = options.outHeight

                    var targetW = minOf(w, maxTextureSize)
                    var targetH = minOf(h, maxTextureSize)
                    if (w > maxTextureSize || h > maxTextureSize) {
                        val scale = minOf(maxTextureSize.toFloat() / w, maxTextureSize.toFloat() / h)
                        targetW = (w * scale).toInt()
                        targetH = (h * scale).toInt()
                    }

                    val maxPixels = 2048 * 2048
                    if (targetW * targetH > maxPixels) {
                        val extraScale = Math.sqrt((targetW * targetH).toDouble() / maxPixels)
                        targetW = (targetW / extraScale).toInt().coerceAtLeast(256)
                        targetH = (targetH / extraScale).toInt().coerceAtLeast(256)
                        addLog("Capped to ${targetW}x${targetH} to avoid OOM")
                    }

                    val inSampleSize = calculateInSampleSize(options, targetW, targetH)
                    val decodeOpts = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                        this.inSampleSize = inSampleSize
                    }

                    val bitmap = try {
                        BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
                    } catch (e: OutOfMemoryError) {
                        addLog("OOM decoding ${file.name}, retrying...")
                        System.gc()
                        Thread.sleep(300)
                        try {
                            decodeOpts.inSampleSize = inSampleSize * 2
                            BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
                        } catch (e2: OutOfMemoryError) {
                            null
                        }
                    }

                    if (bitmap == null) {
                        addLog("Decode failed: ${file.name}, restoring from backup")
                        restoreFromBackup(file, modelDir, backupDir)
                        continue
                    }

                    val scaled = try {
                        if (bitmap.width != targetW || bitmap.height != targetH) {
                            val result = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
                            if (bitmap !== result) bitmap.recycle()
                            result
                        } else {
                            bitmap
                        }
                    } catch (e: OutOfMemoryError) {
                        addLog("OOM scaling ${file.name}, using decoded size")
                        bitmap
                    }

                    val tempFile = File(file.parentFile, file.name + ".tmp")
                    var compressSuccess = false
                    try {
                        tempFile.outputStream().use { fos ->
                            compressSuccess = scaled.compress(Bitmap.CompressFormat.PNG, 100, fos)
                        }
                    } catch (e: Throwable) {
                        addLog("Compress error ${file.name}: ${e.message}")
                        compressSuccess = false
                    }

                    scaled.recycle()

                    if (compressSuccess && tempFile.exists() && tempFile.length() > 0) {
                        var validOutput = false
                        try {
                            val verifyOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeFile(tempFile.absolutePath, verifyOpts)
                            validOutput = verifyOpts.outWidth > 0 && verifyOpts.outHeight > 0
                        } catch (_: Throwable) {}

                        if (validOutput) {
                            try {
                                if (file.delete()) {
                                    if (tempFile.renameTo(file)) {
                                        addLog("Compressed: ${file.name} ${w}x${h} -> ${targetW}x${targetH}")
                                    } else {
                                        addLog("Rename temp failed for ${file.name}, restoring from backup")
                                        restoreFromBackup(file, modelDir, backupDir)
                                    }
                                } else {
                                    addLog("Delete original failed for ${file.name}, restoring from backup")
                                    restoreFromBackup(file, modelDir, backupDir)
                                }
                            } catch (e: Throwable) {
                                addLog("Replace error ${file.name}: ${e.message}, restoring from backup")
                                restoreFromBackup(file, modelDir, backupDir)
                            }
                        } else {
                            addLog("Compressed file invalid: ${file.name}, restoring from backup")
                            restoreFromBackup(file, modelDir, backupDir)
                        }
                    } else {
                        addLog("Compress failed: ${file.name}, restoring from backup")
                        restoreFromBackup(file, modelDir, backupDir)
                    }

                    if (tempFile.exists()) {
                        try { tempFile.delete() } catch (_: Exception) {}
                    }

                    System.gc()
                    Thread.sleep(200)

                } catch (e: Throwable) {
                    addLog("Texture error ${file.name}: ${e.message}, restoring from backup")
                    restoreFromBackup(file, modelDir, backupDir)
                }
            }

            try {
                backupDir.deleteRecursively()
                addLog("Backup directory cleaned up")
            } catch (_: Exception) {}

            addLog("Texture preprocessing done")
        } catch (e: Throwable) {
            addLog("Texture check error: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun restoreFromBackup(originalFile: File, modelDir: File, backupDir: File) {
        try {
            val backupName = originalFile.relativeTo(modelDir).path.replace("\\", "/").replace("/", "_")
            val backupFile = File(backupDir, backupName)
            if (backupFile.exists() && backupFile.length() > 0) {
                if (originalFile.exists()) originalFile.delete()
                backupFile.inputStream().use { input ->
                    originalFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                addLog("Restored from backup: ${originalFile.name}")
            } else {
                addLog("No backup available for ${originalFile.name}")
            }
        } catch (e: Throwable) {
            addLog("Restore failed for ${originalFile.name}: ${e.message}")
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
            <script src="/js/live2dcubismcore.min.js"></script>
            <script src="/js/live2d.min.js"></script>
            <script src="/js/pixi.min.js"></script>
            <script src="/js/pixi-live2d-display.min.js"></script>
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

                        try {
                            var gl = canvas.getContext('webgl') || canvas.getContext('experimental-webgl');
                            if (gl) {
                                var maxTex = gl.getParameter(gl.MAX_TEXTURE_SIZE);
                                step('GL maxTextureSize=' + maxTex);
                            }
                        } catch(e) { step('GL probe failed: ' + e.message); }

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
                                            tReq.open('GET', tex, false);
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

        val defaultPath = context.filesDir.path + "/live2d/PurpleBird/PurpleBird.model3.json"
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
        val defaultPath = context.filesDir.path + "/live2d/PurpleBird/PurpleBird.model3.json"
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
