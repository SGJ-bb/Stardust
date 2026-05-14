/** 离线语音识别引擎: 语音转文字输入(离线模式) */
package com.aicompanion.voice

import android.content.Context

class OfflineASREngine(context: Context) {

    fun initialize(): Boolean {
        return false
    }

    fun startListening(callback: (String) -> Unit) {
        callback("")
    }

    fun stopListening() {
    }

    fun release() {
    }
}
