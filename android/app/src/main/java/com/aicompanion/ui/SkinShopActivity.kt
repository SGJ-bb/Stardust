package com.aicompanion.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aicompanion.R
import com.aicompanion.anim.AnimeUtils
import com.aicompanion.theme.BubbleSkinManager

data class SkinItem(val id: String, val name: String, val assetPath: String?)

class SkinShopActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_skin_shop)

        findViewById<View>(R.id.btn_back).setOnClickListener {
            AnimeUtils.pulse(it)
            finish()
        }

        val tabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.tab_layout)
        val viewPager = findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.view_pager)

        tabLayout.addTab(tabLayout.newTab().setText("💬 气泡皮肤"))
        tabLayout.addTab(tabLayout.newTab().setText("🖼️ AI头像框"))
        tabLayout.addTab(tabLayout.newTab().setText("🖼️ 我的头像框"))

        val pagerAdapter = SkinPagerAdapter(this)
        viewPager.adapter = pagerAdapter

        tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                viewPager.currentItem = tab.position
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
        })

        viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                tabLayout.selectTab(tabLayout.getTabAt(position))
            }
        })

        val defaultTab = intent.getIntExtra("tab", 0).coerceIn(0, 2)
        viewPager.currentItem = defaultTab
    }

    private inner class SkinPagerAdapter(val activity: SkinShopActivity) : RecyclerView.Adapter<SkinPagerAdapter.PagerViewHolder>() {
        override fun getItemCount() = 3
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PagerViewHolder {
            val rv = RecyclerView(activity).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                layoutManager = GridLayoutManager(activity, 3)
                clipToPadding = false
                setPadding(8, 8, 8, 8)
            }
            return PagerViewHolder(rv)
        }
        override fun onBindViewHolder(holder: PagerViewHolder, position: Int) {
            val rv = holder.itemView as RecyclerView
            when (position) {
                0 -> {
                    val items = mutableListOf<SkinItem>()
                    items.add(SkinItem("none", "不使用图片", null))
                    BubbleSkinManager.loadImageBubbles(activity).forEach {
                        items.add(SkinItem(it.id, it.name, it.assetFile))
                    }
                    rv.adapter = BubbleSkinAdapter(items, activity)
                }
                1 -> {
                    val items = mutableListOf<SkinItem>()
                    items.add(SkinItem("none", "不使用图片", null))
                    BubbleSkinManager.loadImageFrames(activity).forEach {
                        items.add(SkinItem(it.id, it.name, it.assetFile))
                    }
                    rv.adapter = AiFrameAdapter(items, activity)
                }
                2 -> {
                    val items = mutableListOf<SkinItem>()
                    items.add(SkinItem("none", "不使用图片", null))
                    BubbleSkinManager.loadImageFrames(activity).forEach {
                        items.add(SkinItem(it.id, it.name, it.assetFile))
                    }
                    rv.adapter = UserFrameAdapter(items, activity)
                }
            }
        }

        inner class PagerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
    }
}

class BubbleSkinAdapter(
    private val items: List<SkinItem>,
    private val activity: SkinShopActivity
) : RecyclerView.Adapter<BubbleSkinAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val ivPreview: ImageView = view.findViewById(R.id.iv_skin_preview)
        val tvName: TextView = view.findViewById(R.id.tv_skin_name)
        val tvStatus: TextView = view.findViewById(R.id.tv_skin_status)
    }

    override fun getItemCount() = items.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_skin_shop, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvName.text = item.name

        val activeImageBubble = BubbleSkinManager.getActiveImageBubble(activity)
        val isActive = if (item.id == "none") activeImageBubble == null else activeImageBubble?.id == item.id

        if (item.assetPath != null) {
            val bmp = BubbleSkinManager.loadBitmapFromAsset(activity, item.assetPath)
            if (bmp != null) {
                holder.ivPreview.setImageBitmap(bmp)
                holder.ivPreview.scaleType = ImageView.ScaleType.FIT_CENTER
            }
        } else {
            val previewDrawable = GradientDrawable().apply {
                setColor(Color.parseColor("#1a1a38"))
                cornerRadius = 18f
                setStroke(1, Color.parseColor("#33c4b5fd"))
            }
            holder.ivPreview.setImageDrawable(previewDrawable)
        }

        if (isActive) {
            holder.tvStatus.visibility = View.VISIBLE
            holder.tvStatus.text = "✓ 使用中"
        } else {
            holder.tvStatus.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            AnimeUtils.pulse(it)
            if (item.id == "none") {
                BubbleSkinManager.setActiveImageBubble(activity, null)
            } else {
                BubbleSkinManager.setActiveImageBubble(activity, item.id)
            }
            notifyDataSetChanged()
        }
    }
}

class AiFrameAdapter(
    private val items: List<SkinItem>,
    private val activity: SkinShopActivity
) : RecyclerView.Adapter<AiFrameAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val ivPreview: ImageView = view.findViewById(R.id.iv_frame_preview)
        val tvName: TextView = view.findViewById(R.id.tv_frame_name)
        val tvStatus: TextView = view.findViewById(R.id.tv_frame_status)
    }

    override fun getItemCount() = items.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_frame_shop, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvName.text = item.name

        val activeFrame = BubbleSkinManager.getActiveAiImageFrame(activity)
        val isActive = if (item.id == "none") activeFrame == null else activeFrame?.id == item.id

        if (item.assetPath != null) {
            val bmp = BubbleSkinManager.loadBitmapFromAsset(activity, item.assetPath)
            if (bmp != null) {
                holder.ivPreview.setImageBitmap(bmp)
                holder.ivPreview.visibility = View.VISIBLE
            }
        } else {
            holder.ivPreview.visibility = View.GONE
        }

        if (isActive) {
            holder.tvStatus.visibility = View.VISIBLE
            holder.tvStatus.text = "✓ 使用中"
        } else {
            holder.tvStatus.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            AnimeUtils.pulse(it)
            if (item.id == "none") {
                BubbleSkinManager.setActiveAiImageFrame(activity, null)
            } else {
                BubbleSkinManager.setActiveAiImageFrame(activity, item.id)
            }
            notifyDataSetChanged()
        }
    }
}

class UserFrameAdapter(
    private val items: List<SkinItem>,
    private val activity: SkinShopActivity
) : RecyclerView.Adapter<UserFrameAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val ivPreview: ImageView = view.findViewById(R.id.iv_frame_preview)
        val tvName: TextView = view.findViewById(R.id.tv_frame_name)
        val tvStatus: TextView = view.findViewById(R.id.tv_frame_status)
    }

    override fun getItemCount() = items.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_frame_shop, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvName.text = item.name

        val activeFrame = BubbleSkinManager.getActiveUserImageFrame(activity)
        val isActive = if (item.id == "none") activeFrame == null else activeFrame?.id == item.id

        if (item.assetPath != null) {
            val bmp = BubbleSkinManager.loadBitmapFromAsset(activity, item.assetPath)
            if (bmp != null) {
                holder.ivPreview.setImageBitmap(bmp)
                holder.ivPreview.visibility = View.VISIBLE
            }
        } else {
            holder.ivPreview.visibility = View.GONE
        }

        if (isActive) {
            holder.tvStatus.visibility = View.VISIBLE
            holder.tvStatus.text = "✓ 使用中"
        } else {
            holder.tvStatus.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            AnimeUtils.pulse(it)
            if (item.id == "none") {
                BubbleSkinManager.setActiveUserImageFrame(activity, null)
            } else {
                BubbleSkinManager.setActiveUserImageFrame(activity, item.id)
            }
            notifyDataSetChanged()
        }
    }
}
