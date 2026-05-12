package com.aicompanion.util

object AppConstants {
    object Network {
        const val DEFAULT_SERVER_URL = "http://192.168.1.8:8000"
        const val DEFAULT_PORT = 8000
        const val CONNECT_TIMEOUT_MS = 10_000L
        const val READ_TIMEOUT_MS = 30_000L
        const val WRITE_TIMEOUT_MS = 15_000L
        const val MAX_RETRIES = 3
        const val MAX_RESPONSE_LENGTH = 10_000
    }

    object Live2D {
        const val DEFAULT_MODEL_PATH = "vtuber/小恶魔.model3.json"
        const val MAX_TEXTURE_SIZE = 2048
        const val DEFAULT_SCALE = 1.0f
        const val DEFAULT_OFFSET_X = 0.0f
        const val DEFAULT_OFFSET_Y = 0.0f
    }

    object Storage {
        const val PREFS_APP = "app_prefs"
        const val PREFS_WAKEUP = "wakeup_settings"
        const val DIR_LIVE2D_CACHE = "live2d_cache"
        const val DIR_BACKGROUND = "backgrounds"
        const val FILE_CURRENT_BG = "current_bg.jpg"
    }

    object Intent {
        const val EXTRA_AUTO_WAKEUP = "auto_wakeup"
        const val REQUEST_CODE_OVERLAY = 2001
        const val REQUEST_CODE_PICK_BG = 1
    }

    object Notifications {
        const val CHANNEL_OVERLAY = "overlay_service_channel"
        const val CHANNEL_WAKEUP = "ai_wakeup_channel"
        const val ID_OVERLAY = 1001
        const val ID_WAKEUP = 1002
    }

    object Permissions {
        const val REQUEST_CODE_OVERLAY = 2001
    }
}
