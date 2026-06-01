package com.aicompanion.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.aicompanion.capsule.TimeCapsule
import com.aicompanion.capsule.TimeCapsuleManager
import com.aicompanion.util.AppLogger
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.*

class TimeCapsuleActivity : AppCompatActivity() {

    private lateinit var capsuleManager: TimeCapsuleManager
    private lateinit var scrollView: ScrollView
    private lateinit var capsuleContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        capsuleManager = TimeCapsuleManager(this)
        buildUI()
        loadCapsules()
    }

    private fun buildUI() {
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.parseColor("#0D0D2B"))
        }

        val toolbar = MaterialToolbar(this).apply {
            title = "时光胶囊"
            setBackgroundColor(android.graphics.Color.parseColor("#1A1A3E"))
            setTitleTextColor(android.graphics.Color.WHITE)
            setNavigationIcon(android.R.drawable.ic_menu_revert)
            setNavigationOnClickListener { finish() }
        }
        rootLayout.addView(toolbar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (56 * resources.displayMetrics.density).toInt()
        ))

        scrollView = ScrollView(this)
        capsuleContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 80)
        }
        scrollView.addView(capsuleContainer)
        rootLayout.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        val fab = FloatingActionButton(this).apply {
            setImageResource(android.R.drawable.ic_input_add)
            backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#667eea"))
            setOnClickListener { showCreateDialog() }
        }
        val fabParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            marginEnd = (24 * resources.displayMetrics.density).toInt()
            bottomMargin = (24 * resources.displayMetrics.density).toInt()
        }

        val frameLayout = FrameLayout(this).apply {
            addView(rootLayout, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
            addView(fab, fabParams)
        }
        setContentView(frameLayout)
    }

    private fun loadCapsules() {
        capsuleContainer.removeAllViews()
        val capsules = capsuleManager.loadCapsules().sortedByDescending { it.createdAt }
        val density = resources.displayMetrics.density

        if (capsules.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = "还没有时光胶囊\n点击 + 给未来的自己写一封信吧"
                setTextColor(android.graphics.Color.parseColor("#667788"))
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, (60 * density).toInt(), 0, 0)
            }
            capsuleContainer.addView(emptyText)
            return
        }

        capsules.forEach { capsule ->
            val card = createCapsuleCard(capsule, density)
            capsuleContainer.addView(card)
        }
    }

    private fun createCapsuleCard(capsule: TimeCapsule, density: Float): LinearLayout {
        val isDue = !capsule.isOpened && capsule.openDate <= System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (12 * density).toInt())
            val bgColor = if (capsule.isOpened) "#1A1A3E" else if (isDue) "#1A2A3E" else "#141430"
            setBackgroundColor(android.graphics.Color.parseColor(bgColor))

            val icon = if (capsule.isOpened) "📭" else if (isDue) "🎁" else "🔒"
            val titleText = TextView(context).apply {
                text = "$icon ${capsule.title}"
                setTextColor(if (isDue) android.graphics.Color.parseColor("#667eea") else android.graphics.Color.WHITE)
                textSize = 16f
            }
            addView(titleText)

            val dateText = TextView(context).apply {
                text = "写给 ${dateFormat.format(Date(capsule.openDate))} 的自己  ·  ${dateFormat.format(Date(capsule.createdAt))}"
                setTextColor(android.graphics.Color.parseColor("#667788"))
                textSize = 12f
            }
            addView(dateText)

            setOnClickListener {
                if (isDue) {
                    openCapsule(capsule)
                } else if (capsule.isOpened) {
                    showCapsuleContent(capsule)
                } else {
                    Toast.makeText(context, "还没到开启时间哦~ ${dateFormat.format(Date(capsule.openDate))}", Toast.LENGTH_SHORT).show()
                }
            }

            setOnLongClickListener {
                AlertDialog.Builder(context)
                    .setTitle("删除胶囊")
                    .setMessage("确定要删除「${capsule.title}」吗？")
                    .setPositiveButton("删除") { _, _ ->
                        capsuleManager.deleteCapsule(capsule.id)
                        loadCapsules()
                    }
                    .setNegativeButton("取消", null)
                    .show()
                true
            }
        }
    }

    private fun openCapsule(capsule: TimeCapsule) {
        val message = capsuleManager.getOpeningMessage(capsule)
        capsuleManager.markOpened(capsule.id)
        AlertDialog.Builder(this)
            .setTitle("🎉 时光胶囊开启了！")
            .setMessage(message)
            .setPositiveButton("好") { _, _ -> loadCapsules() }
            .show()
    }

    private fun showCapsuleContent(capsule: TimeCapsule) {
        val dateFormat = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())
        AlertDialog.Builder(this)
            .setTitle(capsule.title)
            .setMessage("${dateFormat.format(Date(capsule.createdAt))}写下的：\n\n${capsule.content}")
            .setPositiveButton("好", null)
            .show()
    }

    private fun showCreateDialog() {
        val density = resources.displayMetrics.density
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * density).toInt(), (16 * density).toInt(), (24 * density).toInt(), (8 * density).toInt())
        }

        val titleInput = TextInputEditText(this).apply {
            hint = "胶囊标题"
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.parseColor("#667788"))
        }
        layout.addView(TextInputLayout(this).apply {
            addView(titleInput)
            boxBackgroundMode = 2
        })

        val contentInput = TextInputEditText(this).apply {
            hint = "给未来的自己写点什么..."
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.parseColor("#667788"))
            minLines = 4
            gravity = Gravity.TOP
        }
        layout.addView(TextInputLayout(this).apply {
            addView(contentInput)
            boxBackgroundMode = 2
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (8 * density).toInt() })

        var selectedDate = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 30) }
        val dateBtn = Button(this).apply {
            text = "开启日期：${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedDate.time)}"
            setTextColor(android.graphics.Color.parseColor("#667eea"))
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setOnClickListener {
                DatePickerDialog(this@TimeCapsuleActivity,
                    { _, year, month, day ->
                        selectedDate = Calendar.getInstance().apply { set(year, month, day) }
                        text = "开启日期：${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedDate.time)}"
                    },
                    selectedDate.get(Calendar.YEAR),
                    selectedDate.get(Calendar.MONTH),
                    selectedDate.get(Calendar.DAY_OF_MONTH)
                ).show()
            }
        }
        layout.addView(dateBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (8 * density).toInt() })

        AlertDialog.Builder(this)
            .setTitle("✨ 创建时光胶囊")
            .setView(layout)
            .setPositiveButton("封存") { _, _ ->
                val title = titleInput.text?.toString()?.trim() ?: ""
                val content = contentInput.text?.toString()?.trim() ?: ""
                if (title.isBlank() || content.isBlank()) {
                    Toast.makeText(this, "请填写标题和内容", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (selectedDate.timeInMillis <= System.currentTimeMillis()) {
                    Toast.makeText(this, "开启日期必须在今天之后", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                capsuleManager.createCapsule(title, content, selectedDate.timeInMillis)
                Toast.makeText(this, "时光胶囊已封存 ✨", Toast.LENGTH_SHORT).show()
                loadCapsules()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
