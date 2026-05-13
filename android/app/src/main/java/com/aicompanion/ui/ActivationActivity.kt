package com.aicompanion.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.app.Activity
import com.aicompanion.R

class ActivationActivity : Activity() {

    companion object {
        private const val PREFS_NAME = "app_prefs"
        private const val KEY_ACTIVATED = "is_activated"
        private const val KEY_ANNOUNCEMENT_SHOWN = "announcement_shown"
        private const val SECRET_PHRASE = "时光机大人宇宙无敌超级厉害"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ACTIVATED, false)) {
            startMain()
            return
        }

        setContentView(R.layout.activity_activation)

        val input = findViewById<EditText>(R.id.activationInput)
        val errorText = findViewById<TextView>(R.id.errorText)
        val activateBtn = findViewById<Button>(R.id.activateBtn)

        activateBtn.setOnClickListener {
            val text = input.text.toString().trim()
            if (text == SECRET_PHRASE) {
                prefs.edit().putBoolean(KEY_ACTIVATED, true).apply()
                Toast.makeText(this, "激活成功！欢迎~", Toast.LENGTH_SHORT).show()
                showAnnouncement()
            } else {
                errorText.visibility = TextView.VISIBLE
                input.setBackgroundColor(0x33FF6B6B.toInt())
                input.postDelayed({
                    input.setBackgroundColor(0xFF1A1A2E.toInt())
                }, 1000)
            }
        }
    }

    private fun showAnnouncement() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        android.app.AlertDialog.Builder(this)
            .setTitle("📢 公告")
            .setMessage("此项目纯心血来潮的vibe coding，没有添加任何非法收集个人信息的功能，项目在GitHub上有源码，不放心的可以审计源码或是逆向app，都可以哦~\n\n🐛 Bug反馈：如果使用过程中有遇到什么问题，或者哪里不足的可以给作者发私信反馈\n📺 B站：搜索 UID 1523985433\n🎵 抖音ID：31991565756")
            .setPositiveButton("我知道了") { _, _ ->
                prefs.edit().putBoolean(KEY_ANNOUNCEMENT_SHOWN, true).apply()
                startMain()
            }
            .setCancelable(false)
            .show()
    }

    private fun startMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
