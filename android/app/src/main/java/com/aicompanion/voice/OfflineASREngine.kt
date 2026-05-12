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
