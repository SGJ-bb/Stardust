package com.aicompanion.graph

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aicompanion.databinding.ActivityDiaryGraphBinding
import com.aicompanion.diary.DiaryManager
import com.aicompanion.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 日记关系图谱Activity
 *
 * 显示日记之间的链接关系图谱,支持:
 * - 本地图谱视图(以指定日记为中心)
 * - 全局图谱视图
 * - 多维过滤(深度/类型/搜索)
 * - 超链接点击跳转
 * - 节点拖拽和画布平移
 */
class DiaryGraphActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "DiaryGraphActivity"
        const val EXTRA_CENTER_NODE_ID = "center_node_id"
        const val EXTRA_MODE = "mode"
        const val MODE_LOCAL = "local"
        const val MODE_GLOBAL = "global"

        /**
         * 启动本地图谱视图
         */
        fun startLocal(context: Context, centerNodeId: String) {
            val intent = Intent(context, DiaryGraphActivity::class.java).apply {
                putExtra(EXTRA_CENTER_NODE_ID, centerNodeId)
                putExtra(EXTRA_MODE, MODE_LOCAL)
            }
            context.startActivity(intent)
        }

        /**
         * 启动全局图谱视图
         */
        fun startGlobal(context: Context) {
            val intent = Intent(context, DiaryGraphActivity::class.java).apply {
                putExtra(EXTRA_MODE, MODE_GLOBAL)
            }
            context.startActivity(intent)
        }
    }

    private lateinit var binding: ActivityDiaryGraphBinding
    private lateinit var graphManager: DiaryGraphManager
    private lateinit var diaryManager: DiaryManager

    private var currentMode = MODE_LOCAL
    private var centerNodeId: String? = null
    private var currentDepth = 1
    private var currentGraphData: GraphData = GraphData.empty()

    // 过滤状态
    private var filterTypes: Set<NodeType>? = null  // null表示不过滤
    private var searchQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDiaryGraphBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 解析参数
        currentMode = intent.getStringExtra(EXTRA_MODE) ?: MODE_LOCAL
        centerNodeId = intent.getStringExtra(EXTRA_CENTER_NODE_ID)

        // 初始化管理器
        val personaId = "default"  // TODO: 从全局配置获取
        graphManager = DiaryGraphManager(this, personaId)
        diaryManager = DiaryManager(this, personaId)

        setupUI()
        loadData()
    }

    private fun setupUI() {
        // 设置标题
        binding.toolbar.title = if (currentMode == MODE_LOCAL) "日记关系图谱" else "全局图谱"
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // 深度选择器
        binding.depthSelector.setOnCheckedChangeListener { _, checkedId ->
            currentDepth = when (checkedId) {
                binding.depth1.id -> 1
                binding.depth2.id -> 2
                binding.depth3.id -> 3
                else -> 1
            }
            loadGraphData()
        }

        // 类型过滤按钮
        binding.btnFilterType.setOnClickListener {
            showTypeFilterMenu()
        }

        // 搜索框
        binding.searchInput.setOnEditorActionListener { _, _, _ ->
            searchQuery = binding.searchInput.text.toString().trim()
            applyFilters()
            true
        }

        // 重置视图按钮
        binding.btnResetView.setOnClickListener {
            binding.graphView.resetView()
        }

        // 设置节点点击回调
        binding.graphView.onNodeClickListener = { node ->
            onNodeClick(node)
        }

        // 设置节点悬停回调
        binding.graphView.onNodeHoverListener = { node ->
            if (node != null) {
                showNodeTooltip(node)
            } else {
                hideTooltip()
            }
        }

        // 空状态提示
        binding.emptyState.visibility = View.GONE
    }

    /**
     * 加载数据(先加载索引,再加载图谱)
     */
    private fun loadData() {
        binding.loadingProgress.visibility = View.VISIBLE
        binding.graphView.visibility = View.GONE

        lifecycleScope.launch {
            try {
                // 初始化图谱管理器
                graphManager.initialize()

                // 如果索引为空,从日记构建
                val stats = graphManager.getStats()
                if (stats.nodeCount == 0) {
                    AppLogger.i(TAG, "索引为空,从日记构建")
                    val diaries = withContext(Dispatchers.IO) {
                        diaryManager.getAllDiaries()
                    }
                    graphManager.buildIndexFromDiaries(diaries)
                }

                // 加载图谱数据
                loadGraphData()

            } catch (e: Exception) {
                AppLogger.e(TAG, "加载数据失败: ${e.message}")
                showError("加载数据失败: ${e.message}")
            } finally {
                binding.loadingProgress.visibility = View.GONE
            }
        }
    }

    /**
     * 加载图谱数据
     */
    private fun loadGraphData() {
        lifecycleScope.launch {
            val graphData = withContext(Dispatchers.Default) {
                when (currentMode) {
                    MODE_LOCAL -> {
                        val centerId = centerNodeId ?: run {
                            // 如果没有指定中心节点,使用最近的日记
                            val diaries = diaryManager.getAllDiaries()
                            if (diaries.isNotEmpty()) {
                                "日记:${diaries.last().date}"
                            } else {
                                null
                            }
                        }
                        if (centerId != null) {
                            graphManager.getLocalGraph(centerId, currentDepth)
                        } else {
                            GraphData.empty()
                        }
                    }
                    MODE_GLOBAL -> {
                        graphManager.getGlobalGraph()
                    }
                    else -> GraphData.empty()
                }
            }

            currentGraphData = graphData
            applyFilters()
        }
    }

    /**
     * 应用过滤条件
     */
    private fun applyFilters() {
        var filteredData = currentGraphData

        // 类型过滤
        if (filterTypes != null && filterTypes!!.isNotEmpty()) {
            filteredData = filteredData.filterByNodeTypes(filterTypes!!.toList())
        }

        // 搜索高亮
        if (searchQuery.isNotEmpty()) {
            filteredData = filteredData.searchHighlight(searchQuery)
        }

        // 更新视图
        if (filteredData.nodes.isEmpty()) {
            binding.graphView.visibility = View.GONE
            binding.emptyState.visibility = View.VISIBLE
            binding.emptyStateText.text = if (currentGraphData.nodes.isEmpty()) {
                "暂无日记链接数据\n写一些日记来开始吧!"
            } else {
                "过滤后没有匹配的节点"
            }
        } else {
            binding.graphView.visibility = View.VISIBLE
            binding.emptyState.visibility = View.GONE
            binding.graphView.setGraphData(filteredData)
        }

        // 更新统计信息
        val stats = filteredData.getStats()
        binding.textStats.text = "${stats.nodeCount}个节点 · ${stats.edgeCount}条连接"
    }

    /**
     * 节点点击事件
     */
    private fun onNodeClick(node: GraphNode) {
        AppLogger.d(TAG, "节点点击: ${node.id} (${node.type.displayName})")

        when (node.type) {
            NodeType.DIARY -> {
                // 跳转到日记详情
                val date = node.id.substringAfter(":")
                try {
                    // 尝试通过Intent打开日记详情
                    val intent = Intent("com.aicompanion.ACTION_VIEW_DIARY").apply {
                        putExtra("diary_date", date)
                        setPackage(packageName)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    AppLogger.w(TAG, "无法打开日记详情,改为重建图谱: ${e.message}")
                    // 如果没有DiaryDetailActivity,以该节点为中心重建图谱
                    centerNodeId = node.id
                    currentMode = MODE_LOCAL
                    binding.toolbar.title = "图谱: ${node.label}"
                    loadGraphData()
                }
            }
            NodeType.TOPIC, NodeType.PERSON, NodeType.LOCATION, NodeType.EVENT -> {
                // 以该节点为中心重建图谱
                centerNodeId = node.id
                currentMode = MODE_LOCAL
                binding.toolbar.title = "图谱: ${node.label}"
                loadGraphData()
            }
        }
    }

    /**
     * 显示类型过滤菜单
     */
    private fun showTypeFilterMenu() {
        val popup = PopupMenu(this, binding.btnFilterType)
        popup.menuInflater.inflate(com.aicompanion.R.menu.menu_graph_filter, popup.menu)

        // 设置当前选中状态
        val allTypes = listOf(NodeType.DIARY, NodeType.TOPIC, NodeType.PERSON, NodeType.LOCATION)
        if (filterTypes == null) {
            // 全部选中
            popup.menu.findItem(com.aicompanion.R.id.filter_all)?.isChecked = true
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                com.aicompanion.R.id.filter_all -> {
                    filterTypes = null
                    applyFilters()
                    true
                }
                com.aicompanion.R.id.filter_diary -> {
                    filterTypes = setOf(NodeType.DIARY)
                    applyFilters()
                    true
                }
                com.aicompanion.R.id.filter_topic -> {
                    filterTypes = setOf(NodeType.TOPIC)
                    applyFilters()
                    true
                }
                com.aicompanion.R.id.filter_person -> {
                    filterTypes = setOf(NodeType.PERSON)
                    applyFilters()
                    true
                }
                com.aicompanion.R.id.filter_location -> {
                    filterTypes = setOf(NodeType.LOCATION)
                    applyFilters()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    /**
     * 显示节点tooltip
     */
    private fun showNodeTooltip(node: GraphNode) {
        binding.tooltipCard.visibility = View.VISIBLE
        binding.tooltipTitle.text = node.label
        binding.tooltipType.text = node.type.displayName

        // 显示连接数量
        val connCount = currentGraphData.getNodeConnectionCount(node.id)
        binding.tooltipConnections.text = "连接: $connCount"

        // 显示反向链接数量
        val backLinks = graphManager.getBackLinks(node.id)
        binding.tooltipBacklinks.text = "被引用: ${backLinks.size}次"
    }

    /**
     * 隐藏tooltip
     */
    private fun hideTooltip() {
        binding.tooltipCard.visibility = View.GONE
    }

    /**
     * 显示错误信息
     */
    private fun showError(message: String) {
        binding.graphView.visibility = View.GONE
        binding.emptyState.visibility = View.VISIBLE
        binding.emptyStateText.text = message
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.graphView.cleanup()
        graphManager.cleanup()
    }
}