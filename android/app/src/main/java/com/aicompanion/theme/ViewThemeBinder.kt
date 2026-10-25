/**
 * 视图树主题绑定器 —— 让 XML 布局页面跟随 Compose 侧的 12 套主题
 *
 * 背景：XML 页面里的颜色来自两处
 *   1) 语义化颜色资源 @color/text_primary、@color/bg_base…（约 250 处引用）
 *   2) 直接写死的色值 android:textColor="#e0e0f0"（约 224 处）
 * 这两者都是**静态**的，只随 DayNight（亮/暗）变化，不随 12 套主题色变化，
 * 于是切主题时 Compose 页面立刻变、XML 页面纹丝不动。
 *
 * 本类的做法：不要求改 XML，而是在视图树挂载时按"当前色值"反查它属于哪个语义令牌，
 * 再替换为当前主题对应的颜色。
 *   - 反查表由 colors.xml（亮色）与 values-night/colors.xml（暗色）自动生成，
 *     所以无论当前是亮色还是暗色模式都能命中；
 *   - 用 setTag(R.id.st_token_*) 记住"这个视图属于哪个令牌"，二次上色直接按令牌重算，
 *     既避免了反复反查导致的颜色漂移，也保证切换主题时能换成新主题的颜色；
 *   - 只处理"能确定语义"的颜色：命中不了反查表的一律原样保留（品牌绿、成功红等不动）。
 */
package com.aicompanion.theme

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import androidx.recyclerview.widget.RecyclerView
import com.aicompanion.R
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.MaterialShapeDrawable

/** 主题语义令牌：每个令牌对应 [StradustColors] 里的一个颜色槽位 */
enum class ThemeToken {
    BACKGROUND, SURFACE, SURFACE_CONTAINER, SURFACE_CONTAINER_LOW,
    SURFACE_CONTAINER_HIGH, SURFACE_CONTAINER_HIGHEST, TOOLBAR,
    PRIMARY, ON_PRIMARY, SECONDARY, TERTIARY, TERTIARY_CONTAINER,
    OUTLINE, OUTLINE_VARIANT,
    TEXT_PRIMARY, TEXT_SECONDARY, TEXT_MUTED, TEXT_DISABLED, TEXT_LINK,
    USER_BUBBLE_START, USER_BUBBLE_END, AI_BUBBLE;

    /** 取该令牌在当前主题下的实际色值 */
    fun value(c: StradustColors): Int = (when (this) {
        BACKGROUND -> c.background
        SURFACE -> c.surface
        SURFACE_CONTAINER -> c.surfaceContainer
        SURFACE_CONTAINER_LOW -> c.surfaceContainerLow
        SURFACE_CONTAINER_HIGH -> c.surfaceContainerHigh
        SURFACE_CONTAINER_HIGHEST -> c.surfaceContainerHighest
        TOOLBAR -> c.toolbar
        PRIMARY -> c.primary
        ON_PRIMARY -> c.onPrimary
        SECONDARY -> c.secondary
        TERTIARY -> c.tertiary
        TERTIARY_CONTAINER -> c.tertiaryContainer
        OUTLINE -> c.outline
        OUTLINE_VARIANT -> c.outlineVariant
        TEXT_PRIMARY -> c.textPrimary
        TEXT_SECONDARY -> c.textSecondary
        TEXT_MUTED -> c.textMuted
        TEXT_DISABLED -> c.textDisabled
        TEXT_LINK -> c.textLink
        USER_BUBBLE_START -> c.userBubbleStart
        USER_BUBBLE_END -> c.userBubbleEnd
        AI_BUBBLE -> c.aiBubble
    }).toArgb()
}

object ViewThemeBinder {

    // =====================================================================
    // 反查表：色值 → 语义令牌
    // 由脚本从 res/values/colors.xml（亮色）与 res/values-night/colors.xml（暗色）
    // 连同布局中的高频硬编码色值一并生成，两套值都在表内，故明暗模式均可命中。
    // =====================================================================

    private val TEXT_TOKENS: Map<Int, ThemeToken> = buildMap {
        put(0xFF1A1A2E.toInt(), ThemeToken.TEXT_PRIMARY)
        put(0xFF333333.toInt(), ThemeToken.TEXT_PRIMARY)
        put(0xFF3355AA.toInt(), ThemeToken.PRIMARY)
        put(0xFF374151.toInt(), ThemeToken.TEXT_PRIMARY)
        put(0xFF4A5070.toInt(), ThemeToken.TEXT_DISABLED)
        put(0xFF4A5568.toInt(), ThemeToken.TEXT_SECONDARY)
        put(0xFF4B5563.toInt(), ThemeToken.TEXT_SECONDARY)
        put(0xFF5555AA.toInt(), ThemeToken.PRIMARY)
        put(0xFF556677.toInt(), ThemeToken.TEXT_MUTED)
        put(0xFF556688.toInt(), ThemeToken.TEXT_MUTED)
        put(0xFF5C6BC0.toInt(), ThemeToken.PRIMARY)
        put(0xFF64FFDA.toInt(), ThemeToken.SECONDARY)
        put(0xFF666666.toInt(), ThemeToken.TEXT_SECONDARY)
        put(0xFF667788.toInt(), ThemeToken.TEXT_SECONDARY)
        put(0xFF667EEA.toInt(), ThemeToken.PRIMARY)
        put(0xFF6B7280.toInt(), ThemeToken.TEXT_MUTED)
        put(0xFF7BA4F7.toInt(), ThemeToken.TEXT_LINK)
        put(0xFF7C4DFF.toInt(), ThemeToken.PRIMARY)
        put(0xFF7C5CFC.toInt(), ThemeToken.PRIMARY)
        put(0xFF8888AA.toInt(), ThemeToken.TEXT_MUTED)
        put(0xFF8899AA.toInt(), ThemeToken.TEXT_SECONDARY)
        put(0xFF8899BB.toInt(), ThemeToken.TEXT_SECONDARY)
        put(0xFF8899CC.toInt(), ThemeToken.TEXT_SECONDARY)
        put(0xFF8899DD.toInt(), ThemeToken.TEXT_SECONDARY)
        put(0xFF8B9CF7.toInt(), ThemeToken.PRIMARY)
        put(0xFF9098B0.toInt(), ThemeToken.TEXT_SECONDARY)
        put(0xFF999999.toInt(), ThemeToken.TEXT_MUTED)
        put(0xFF99AACC.toInt(), ThemeToken.TEXT_SECONDARY)
        put(0xFF99AAEE.toInt(), ThemeToken.PRIMARY)
        put(0xFF9AA4C0.toInt(), ThemeToken.TEXT_SECONDARY)
        put(0xFF9C7CFF.toInt(), ThemeToken.TERTIARY)
        put(0xFF9CA3AF.toInt(), ThemeToken.TEXT_DISABLED)
        put(0xFFA78BFA.toInt(), ThemeToken.TERTIARY)
        put(0xFFAAAAAA.toInt(), ThemeToken.TEXT_DISABLED)
        put(0xFFAAAACC.toInt(), ThemeToken.TEXT_MUTED)
        put(0xFFAABBCC.toInt(), ThemeToken.TEXT_MUTED)
        put(0xFFC4B5FD.toInt(), ThemeToken.TERTIARY)
        put(0xFFC8C8E0.toInt(), ThemeToken.TEXT_PRIMARY)
        put(0xFFD0D0E0.toInt(), ThemeToken.TEXT_PRIMARY)
        put(0xFFD0D0E8.toInt(), ThemeToken.TEXT_PRIMARY)
        put(0xFFE040FB.toInt(), ThemeToken.TERTIARY)
        put(0xFFE0E0F0.toInt(), ThemeToken.TEXT_PRIMARY)
        put(0xFFE8E8E8.toInt(), ThemeToken.TEXT_PRIMARY)
        put(0xFFE8E8F0.toInt(), ThemeToken.TEXT_PRIMARY)
        put(0xFFE8E8F8.toInt(), ThemeToken.TEXT_PRIMARY)
        put(0xFFECECF4.toInt(), ThemeToken.TEXT_PRIMARY)
        put(0xFFFF6B9D.toInt(), ThemeToken.TERTIARY)
        put(0xFFFFFFFF.toInt(), ThemeToken.ON_PRIMARY)
        // 仅以硬编码形式出现在布局中、但语义明确的补充项
        put(0xFF000000.toInt(), ThemeToken.TEXT_PRIMARY)
        put(0xFF1A1A38.toInt(), ThemeToken.TEXT_PRIMARY)
        put(0xFFCCCCCC.toInt(), ThemeToken.TEXT_DISABLED)
        put(0xFFE5E7EB.toInt(), ThemeToken.TEXT_MUTED)
    }

    private val BG_TOKENS: Map<Int, ThemeToken> = buildMap {
        put(0xB316162A.toInt(), ThemeToken.SURFACE_CONTAINER)
        put(0xCC0A0A1A.toInt(), ThemeToken.SURFACE_CONTAINER)
        put(0xFF0A0A1A.toInt(), ThemeToken.BACKGROUND)
        put(0xFF0C0C1E.toInt(), ThemeToken.BACKGROUND)
        put(0xFF0D0D1A.toInt(), ThemeToken.BACKGROUND)
        put(0xFF0D0D22.toInt(), ThemeToken.BACKGROUND)
        put(0xFF0D1117.toInt(), ThemeToken.BACKGROUND)
        put(0xFF12122E.toInt(), ThemeToken.BACKGROUND)
        put(0xFF14142A.toInt(), ThemeToken.SURFACE)
        put(0xFF16162A.toInt(), ThemeToken.TOOLBAR)
        put(0xFF16162E.toInt(), ThemeToken.SURFACE_CONTAINER_LOW)
        put(0xFF161B22.toInt(), ThemeToken.SURFACE)
        put(0xFF1A1A2E.toInt(), ThemeToken.SURFACE)
        put(0xFF1A1A38.toInt(), ThemeToken.SURFACE_CONTAINER)
        put(0xFF1A1A3A.toInt(), ThemeToken.AI_BUBBLE)
        put(0xFF1A1A3E.toInt(), ThemeToken.BACKGROUND)
        put(0xFF1A2244.toInt(), ThemeToken.OUTLINE_VARIANT)
        put(0xFF1E1E3A.toInt(), ThemeToken.SURFACE_CONTAINER_HIGH)
        put(0xFF1E1E44.toInt(), ThemeToken.SURFACE_CONTAINER_HIGH)
        put(0xFF21262D.toInt(), ThemeToken.SURFACE_CONTAINER)
        put(0xFF242450.toInt(), ThemeToken.SURFACE_CONTAINER_HIGHEST)
        put(0xFF2A1F55.toInt(), ThemeToken.TERTIARY_CONTAINER)
        put(0xFF2A2A4A.toInt(), ThemeToken.OUTLINE_VARIANT)
        put(0xFF2A2A5A.toInt(), ThemeToken.OUTLINE)
        put(0xFF3A3A5A.toInt(), ThemeToken.SURFACE_CONTAINER_HIGH)
        put(0xFF3A3A6E.toInt(), ThemeToken.OUTLINE)
        put(0xFF4A4A8E.toInt(), ThemeToken.OUTLINE)
        put(0xFF64FFDA.toInt(), ThemeToken.SECONDARY)
        put(0xFF667EEA.toInt(), ThemeToken.PRIMARY)
        put(0xFF764BA2.toInt(), ThemeToken.USER_BUBBLE_END)
        put(0xFF8B9CF7.toInt(), ThemeToken.PRIMARY)
        put(0xFF9C7CFF.toInt(), ThemeToken.TERTIARY)
        put(0xFF9CA2B4.toInt(), ThemeToken.OUTLINE)
        put(0xFFB8BCC8.toInt(), ThemeToken.OUTLINE)
        put(0xFFC4B5FD.toInt(), ThemeToken.TERTIARY)
        put(0xFFCCCCCC.toInt(), ThemeToken.OUTLINE_VARIANT)
        put(0xFFCFCFCF.toInt(), ThemeToken.OUTLINE_VARIANT)
        put(0xFFD4D7DE.toInt(), ThemeToken.OUTLINE)
        put(0xFFE040FB.toInt(), ThemeToken.TERTIARY)
        put(0xFFE0E3EC.toInt(), ThemeToken.SURFACE_CONTAINER_HIGHEST)
        put(0xFFE4E7F0.toInt(), ThemeToken.BACKGROUND)
        put(0xFFE5E7EB.toInt(), ThemeToken.OUTLINE_VARIANT)
        put(0xFFE8E0FF.toInt(), ThemeToken.TERTIARY_CONTAINER)
        put(0xFFE8EAEF.toInt(), ThemeToken.OUTLINE_VARIANT)
        put(0xFFEAEBED.toInt(), ThemeToken.SURFACE_CONTAINER_HIGHEST)
        put(0xFFEDEDED.toInt(), ThemeToken.OUTLINE_VARIANT)
        put(0xFFEEF0F6.toInt(), ThemeToken.BACKGROUND)
        put(0xFFF0F1F5.toInt(), ThemeToken.SURFACE_CONTAINER_HIGH)
        put(0xFFF5F5F5.toInt(), ThemeToken.SURFACE_CONTAINER)
        put(0xFFF5F6FA.toInt(), ThemeToken.BACKGROUND)
        put(0xFFF8F9FA.toInt(), ThemeToken.BACKGROUND)
        put(0xFFF8F9FC.toInt(), ThemeToken.BACKGROUND)
        put(0xFFFAFBFC.toInt(), ThemeToken.BACKGROUND)
        put(0xFFFF6B9D.toInt(), ThemeToken.TERTIARY)
        put(0xFFFFFFFF.toInt(), ThemeToken.SURFACE)
        // 仅以硬编码形式出现在布局中、但语义明确的补充项
        put(0xFF3355AA.toInt(), ThemeToken.PRIMARY)
        put(0xFF5C6BC0.toInt(), ThemeToken.PRIMARY)
        put(0xFF7C4DFF.toInt(), ThemeToken.PRIMARY)
        put(0xFFA78BFA.toInt(), ThemeToken.TERTIARY)
    }

    // =====================================================================
    // 对外 API
    // =====================================================================

    /** 遍历整棵视图树并按当前主题上色。可安全重复调用。 */
    fun bindTree(root: View?, context: Context) {
        if (root == null) return
        val colors = safeColors(context) ?: return
        try {
            walk(root, colors)
        } catch (_: Throwable) {
            // 单个页面着色失败不应影响业务
        }
    }

    /** 对单个视图上色（inflate 回调中用） */
    fun bindView(view: View, context: Context) {
        val colors = safeColors(context) ?: return
        try {
            apply(view, colors)
        } catch (_: Throwable) {}
    }

    /**
     * 包装 Activity 的 LayoutInflater，使**之后** inflate 出来的视图自动上色。
     * 覆盖 Activity 视图树之外的部分：Dialog、PopupWindow、动态 inflate 的条目等。
     * delegate 保留原有 Factory2（AppCompat 的 ViewInflater），不干扰组件替换逻辑。
     */
    fun installInflaterHook(activity: Activity) {
        try {
            val inflater = activity.layoutInflater
            val current = inflater.factory2
            if (current is ThemedFactory2) return
            inflater.factory2 = ThemedFactory2(current)
        } catch (_: Throwable) {}
    }

    private fun safeColors(context: Context): StradustColors? = try {
        ThemeBridge.resolveColors(context)
    } catch (_: Throwable) {
        null
    }

    // =====================================================================
    // 内部实现
    // =====================================================================

    private fun walk(v: View, colors: StradustColors) {
        apply(v, colors)
        if (v is ViewGroup) {
            if (v is RecyclerView) hookRecyclerView(v)
            for (i in 0 until v.childCount) {
                walk(v.getChildAt(i), colors)
            }
        }
    }

    private fun apply(v: View, colors: StradustColors) {
        applyBackground(v, colors)
        if (v is TextView) applyText(v, colors)
        applyTint(v, colors)
        if (v is MaterialCardView) applyMaterialCard(v, colors)
    }

    /**
     * MaterialCardView 的卡片底色与描边不经过 view.background（由 MaterialShapeDrawable 自绘），
     * 而布局里 app:cardBackgroundColor / app:strokeColor 合计近百处，必须单独接。
     */
    private fun applyMaterialCard(v: MaterialCardView, colors: StradustColors) {
        // 卡片底色
        val bgCached = v.getTag(R.id.st_token_bg) as? Int
        val bgToken = if (bgCached != null) {
            ThemeToken.entries.getOrNull(bgCached)
        } else {
            val found = v.cardBackgroundColor?.defaultColor?.let { BG_TOKENS[it] }
            if (found != null) v.setTag(R.id.st_token_bg, found.ordinal)
            found
        }
        if (bgToken != null) {
            try { v.setCardBackgroundColor(bgToken.value(colors)) } catch (_: Throwable) {}
        }
        // 描边：MaterialCardView.getStrokeColor() 在当前 material 版本无公开 getter，
        // 改为从背景 drawable 上读（MaterialCardView 的底色/描边都存在 MaterialShapeDrawable 里）
        val sCached = v.getTag(R.id.st_token_stroke) as? Int
        val sToken = if (sCached != null) {
            ThemeToken.entries.getOrNull(sCached)
        } else {
            val cs = findShapeDrawable(v.background)?.getStrokeColor()
            val found = cs?.defaultColor?.let { BG_TOKENS[it] }
            if (found != null) v.setTag(R.id.st_token_stroke, found.ordinal)
            found
        }
        if (sToken != null) {
            try { v.setStrokeColor(sToken.value(colors)) } catch (_: Throwable) {}
        }
    }

    /** 背景可能被 RippleDrawable / LayerDrawable 包裹，逐层剥出 MaterialShapeDrawable */
    private fun findShapeDrawable(d: android.graphics.drawable.Drawable?): MaterialShapeDrawable? = when {
        d == null -> null
        d is MaterialShapeDrawable -> d
        d is LayerDrawable -> (0 until d.numberOfLayers)
            .mapNotNull { findShapeDrawable(d.getDrawable(it)) }
            .firstOrNull()
        else -> null
    }

    /** 背景：只处理纯色（ColorDrawable）与无渐变的 GradientDrawable，圆角/渐变/水波纹一概不碰 */
    private fun applyBackground(v: View, colors: StradustColors) {
        val bg = v.background ?: return
        val cached = v.getTag(R.id.st_token_bg) as? Int
        val token: ThemeToken? = if (cached != null) {
            ThemeToken.entries.getOrNull(cached)
        } else {
            val found = when (bg) {
                is ColorDrawable -> BG_TOKENS[bg.color]
                is GradientDrawable -> solidGradientToken(bg)
                else -> null
            }
            if (found != null) v.setTag(R.id.st_token_bg, found.ordinal)
            found
        }
        if (token == null) return
        val argb = token.value(colors)
        try {
            if (bg is GradientDrawable) {
                bg.mutate()
                bg.setColor(argb)
            } else {
                v.setBackgroundColor(argb)
            }
        } catch (_: Throwable) {}
    }

    /** GradientDrawable 只有在 <solid> 形态下 getColor() 才返回颜色，渐变形态返回 null，正好用来过滤 */
    private fun solidGradientToken(g: GradientDrawable): ThemeToken? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null
        return try {
            g.color?.defaultColor?.let { BG_TOKENS[it] }
        } catch (_: Throwable) {
            null
        }
    }

    private fun applyText(v: TextView, colors: StradustColors) {
        // 正文颜色
        val cached = v.getTag(R.id.st_token_text) as? Int
        if (cached != null) {
            ThemeToken.entries.getOrNull(cached)?.let {
                try { v.setTextColor(it.value(colors)) } catch (_: Throwable) {}
            }
        } else {
            val c = v.textColors?.defaultColor
            if (c != null) {
                TEXT_TOKENS[c]?.let {
                    v.setTag(R.id.st_token_text, it.ordinal)
                    try { v.setTextColor(it.value(colors)) } catch (_: Throwable) {}
                }
            }
        }
        // 提示文字颜色
        val cachedHint = v.getTag(R.id.st_token_hint) as? Int
        if (cachedHint != null) {
            ThemeToken.entries.getOrNull(cachedHint)?.let {
                try { v.setHintTextColor(it.value(colors)) } catch (_: Throwable) {}
            }
        } else if (v.hint != null) {
            val hc = v.hintTextColors?.defaultColor
            if (hc != null) {
                TEXT_TOKENS[hc]?.let {
                    v.setTag(R.id.st_token_hint, it.ordinal)
                    try { v.setHintTextColor(it.value(colors)) } catch (_: Throwable) {}
                }
            }
        }
    }

    private fun applyTint(v: View, colors: StradustColors) {
        // app:backgroundTint —— MaterialButton / Chip / 各类卡片沿用此属性（约 32 处）
        bindTint(v, R.id.st_token_bgtint, colors,
            get = { v.backgroundTintList }, set = { v.backgroundTintList = it })
        when (v) {
            is CompoundButton -> bindTint(v, R.id.st_token_tint, colors,
                get = { v.buttonTintList }, set = { v.buttonTintList = it })
            is ImageView -> bindTint(v, R.id.st_token_tint, colors,
                get = { v.imageTintList }, set = { v.imageTintList = it })
            is ProgressBar -> {
                bindTint(v, R.id.st_token_tint, colors,
                    get = { v.progressTintList }, set = { v.progressTintList = it })
                bindTint(v, R.id.st_token_tint2, colors,
                    get = { v.indeterminateTintList }, set = { v.indeterminateTintList = it })
            }
        }
    }

    private inline fun bindTint(
        view: View,
        keyId: Int,
        colors: StradustColors,
        get: () -> ColorStateList?,
        set: (ColorStateList?) -> Unit,
    ) {
        val cached = view.getTag(keyId) as? Int
        val token = if (cached != null) {
            ThemeToken.entries.getOrNull(cached)
        } else {
            val c = get()?.defaultColor ?: return
            val found = BG_TOKENS[c] ?: return
            view.setTag(keyId, found.ordinal)
            found
        }
        if (token == null) return
        try {
            set(ColorStateList.valueOf(token.value(colors)))
        } catch (_: Throwable) {}
    }

    /** RecyclerView 的子项是滚动时动态挂载的，挂一次监听保证新条目也跟随主题 */
    private fun hookRecyclerView(rv: RecyclerView) {
        if (rv.getTag(R.id.st_rv_hooked) != null) return
        rv.setTag(R.id.st_rv_hooked, true)
        rv.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(child: View) {
                bindTree(child, child.context)
            }

            override fun onChildViewDetachedFromWindow(child: View) {}
        })
    }

    /**
     * 保留原有 Factory2 链，在视图创建完成后追加一次主题上色。
     *
     * AppCompat 的 ViewInflater 只认短标签与 android.widget.*，遇到全限定名
     * （com.google.android.material.*、androidx.*）会返回 null —— 而本项目布局里这类标签有
     * 140 多处，且 Dialog 中的视图不属于 Activity 视图树、遍历够不着。所以这里按系统规则
     * 补建一次；建不出来就返回 null，交回 LayoutInflater 自身的反射兜底，不改变原有行为。
     */
    private class ThemedFactory2(
        private val delegate: LayoutInflater.Factory2?,
    ) : LayoutInflater.Factory2 {

        private val ctorCache = HashMap<String, java.lang.reflect.Constructor<*>?>()

        /** 这些标签由 LayoutInflater / Activity 的 privateFactory 特殊处理，不自行创建 */
        private val specialTags = setOf("view", "fragment", "include", "merge", "blink", "tag", "requestFocus")

        override fun onCreateView(
            parent: View?,
            name: String,
            context: Context,
            attrs: AttributeSet,
        ): View? {
            val delegated = try {
                delegate?.onCreateView(parent, name, context, attrs)
            } catch (_: Throwable) {
                null
            }
            val view = delegated ?: createByReflection(context, name, attrs) ?: return null
            try {
                bindView(view, context)
            } catch (_: Throwable) {}
            return view
        }

        override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? =
            onCreateView(null, name, context, attrs)

        private fun createByReflection(context: Context, name: String, attrs: AttributeSet): View? {
            if (name in specialTags) return null
            val candidates = if (name.contains('.')) {
                listOf(name)
            } else {
                listOf("android.widget.$name", "android.webkit.$name", "android.app.$name")
            }
            for (full in candidates) {
                val ctor = ctorCache.getOrPut(full) {
                    try {
                        Class.forName(full)
                            .getConstructor(Context::class.java, AttributeSet::class.java)
                    } catch (_: Throwable) {
                        null
                    }
                } ?: continue
                if (!View::class.java.isAssignableFrom(ctor.declaringClass)) continue
                try {
                    return ctor.newInstance(themedContext(context, attrs), attrs) as? View
                } catch (_: Throwable) {
                    // 换下一个候选名
                }
            }
            return null
        }

        /** 复刻 LayoutInflater 对 android:theme 的处理，避免自行创建时丢掉该属性 */
        private fun themedContext(context: Context, attrs: AttributeSet): Context = try {
            val ta = context.obtainStyledAttributes(attrs, intArrayOf(android.R.attr.theme))
            val res = ta.getResourceId(0, 0)
            ta.recycle()
            if (res != 0) android.view.ContextThemeWrapper(context, res) else context
        } catch (_: Throwable) {
            context
        }
    }
}
