package com.aicompanion.ui

import android.app.Activity
import android.os.Bundle
import android.widget.*
import com.aicompanion.R
import com.aicompanion.live2d.ModelManager
import com.aicompanion.models.TextureQuality

class ModelSettingsActivity : Activity() {

    private var modelManager: ModelManager? = null
    private var modelId: String = ""

    private var radioTextureQuality: RadioGroup? = null
    private var radioFPS: RadioGroup? = null
    private var btnSave: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_model_settings)

        modelId = intent.getStringExtra("model_id") ?: ""
        if (modelId.isEmpty()) {
            Toast.makeText(this, "模型ID无效", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        modelManager = ModelManager(this)

        initViews()
        setupClickListeners()
    }

    private fun initViews() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        radioTextureQuality = findViewById(R.id.radio_texture_quality)
        radioFPS = findViewById(R.id.radio_fps)
        btnSave = findViewById(R.id.btn_save_model_settings)
    }

    private fun setupClickListeners() {
        btnSave?.setOnClickListener {
            val mm = modelManager ?: return@setOnClickListener
            val quality = when (radioTextureQuality?.checkedRadioButtonId) {
                R.id.radio_tex_low -> TextureQuality.LOW
                R.id.radio_tex_medium -> TextureQuality.MEDIUM
                else -> TextureQuality.HIGH
            }
            val fps = when (radioFPS?.checkedRadioButtonId) {
                R.id.radio_fps_20 -> 20
                else -> 30
            }
            mm.updateModelSettings(modelId, quality, fps)
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
