package com.aicompanion.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aicompanion.R

class SettingsAdapter(
    val items: List<SettingsItem>,
    private val onItemBound: ((View, Int) -> Unit)? = null
) : RecyclerView.Adapter<SettingsAdapter.SettingsViewHolder>() {

    companion object {
        const val TYPE_APPEARANCE = 0
        const val TYPE_SEARCH = 1
        const val TYPE_LLM = 2
        const val TYPE_SCREEN = 3
        const val TYPE_ASR = 4
        const val TYPE_TTS = 5
        const val TYPE_USER = 6
        const val TYPE_DIARY = 7
        const val TYPE_AI_FEATURES = 8
        const val TYPE_SAFETY = 9
        const val TYPE_MEMORY = 10
        const val TYPE_STYLE = 11
        const val TYPE_FOOTER = 12
    }

    data class SettingsItem(val type: Int)

    class SettingsViewHolder(val view: View) : RecyclerView.ViewHolder(view)

    override fun getItemViewType(position: Int) = items[position].type

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SettingsViewHolder {
        val layoutRes = when (viewType) {
            TYPE_APPEARANCE -> R.layout.item_settings_appearance
            TYPE_SEARCH -> R.layout.item_settings_search
            TYPE_LLM -> R.layout.item_settings_llm
            TYPE_SCREEN -> R.layout.item_settings_screen
            TYPE_ASR -> R.layout.item_settings_asr
            TYPE_TTS -> R.layout.item_settings_tts
            TYPE_USER -> R.layout.item_settings_user
            TYPE_DIARY -> R.layout.item_settings_diary
            TYPE_AI_FEATURES -> R.layout.item_settings_ai_features
            TYPE_SAFETY -> R.layout.item_settings_safety
            TYPE_MEMORY -> R.layout.item_settings_memory
            TYPE_STYLE -> R.layout.item_settings_style
            TYPE_FOOTER -> R.layout.settings_footer_stub
            else -> R.layout.item_settings_appearance
        }
        val view = LayoutInflater.from(parent.context)
            .inflate(layoutRes, parent, false)
        return SettingsViewHolder(view)
    }

    override fun onBindViewHolder(holder: SettingsViewHolder, position: Int) {
        onItemBound?.invoke(holder.view, items[position].type)
    }

    override fun getItemCount() = items.size

    override fun onViewRecycled(holder: SettingsViewHolder) {
        super.onViewRecycled(holder)
    }
}
