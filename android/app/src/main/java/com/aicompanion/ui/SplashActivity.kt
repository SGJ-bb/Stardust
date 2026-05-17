package com.aicompanion.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import com.aicompanion.R

class SplashActivity : android.app.Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val ageConfirmed = prefs.getBoolean("age_confirmed", false)

        if (!ageConfirmed) {
            showAgeConfirmation()
            return
        }

        showSplash()
    }

    private fun showAgeConfirmation() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("📋 使用须知")
            .setMessage("1. 本应用仅供娱乐和创意交流使用。\n\n2. AI生成的内容不代表开发者立场，用户需自行判断和负责。\n\n3. 用户使用本应用产生的任何行为和后果，均与开发者无关。\n\n4. 请确认您已年满18岁。\n\n⚠️ 继续使用即表示您同意以上条款。")
            .setCancelable(false)
            .setPositiveButton("我已满18岁，同意以上条款") { _, _ ->
                getSharedPreferences("app_prefs", MODE_PRIVATE)
                    .edit().putBoolean("age_confirmed", true).apply()
                showSplash()
            }
            .setNegativeButton("不同意") { _, _ ->
                finish()
            }
            .show()
    }

    private fun showSplash() {
        val imageView = ImageView(this)
        imageView.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
        imageView.setImageResource(R.drawable.tubiao)
        setContentView(imageView)

        window.decorView.systemUiVisibility = (
            android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
            or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        imageView.alpha = 0f
        imageView.scaleX = 0.8f
        imageView.scaleY = 0.8f
        imageView.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(800)
            .setInterpolator(com.aicompanion.anim.AnimeInterpolators.easeOutBack)
            .start()

        Handler(Looper.getMainLooper()).postDelayed({
            imageView.animate()
                .alpha(0f)
                .scaleX(1.05f)
                .scaleY(1.05f)
                .setDuration(400)
                .setInterpolator(com.aicompanion.anim.AnimeInterpolators.easeInCubic)
                .withEndAction {
                    val intent = Intent(this, HomeActivity::class.java)
                    startActivity(intent)
                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
                    finish()
                }
                .start()
        }, 1500)
    }
}
