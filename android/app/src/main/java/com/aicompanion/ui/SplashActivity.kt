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

    private val easeInCubic = android.view.animation.Interpolator { t -> t * t * t }
}
