package com.aicompanion.agent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Agent 活动总线：把「AI 正在干什么」变成界面可见的状态。
 *
 * 设计约束：
 * - 不改聊天消息流（那是 RecyclerView/LazyColumn 里的正式内容），
 *   活动只作为一条独立的状态条 + 可展开的列表，避免污染对话。
 * - 工具执行在 IO 线程，UI 在主线，所以内部用线程安全容器 + StateFlow。
 * - 单例：工具执行入口在 AIActionManager，UI 在 ChatScreen，两边不需要互相持有引用。
 */
object AgentBus {

    enum class Status { RUNNING, OK, FAIL, WAIT_CONFIRM, DENIED }

    data class Event(
        val id: String,
        val tool: String,
        /** 人话描述，例如「点按屏幕 (540, 1200)」 */
        val summary: String,
        val status: Status,
        /** 结果摘要，失败时是原因 */
        val detail: String = "",
        val startedAt: Long = System.currentTimeMillis(),
        val costMs: Long = 0L,
    ) {
        val isRunning: Boolean get() = status == Status.RUNNING
        val isBad: Boolean get() = status == Status.FAIL || status == Status.DENIED
    }

    /** 待用户确认的动作 */
    data class PendingConfirm(
        val id: String,
        val summary: String,
        val reason: String,
    )

    private const val MAX_KEEP = 30

    private val _events = MutableStateFlow<List<Event>>(emptyList())
    val events: StateFlow<List<Event>> = _events.asStateFlow()

    private val _pending = MutableStateFlow<PendingConfirm?>(null)
    val pending: StateFlow<PendingConfirm?> = _pending.asStateFlow()

    /** 当前正在跑的那条（状态条只显示它） */
    private val _current = MutableStateFlow<Event?>(null)
    val current: StateFlow<Event?> = _current.asStateFlow()

    private val latches = ConcurrentHashMap<String, CountDownLatch>()
    private val decisions = ConcurrentHashMap<String, Boolean>()

    // ---------- 执行记录 ----------

    fun begin(tool: String, summary: String): String {
        val ev = Event(
            id = UUID.randomUUID().toString().take(8),
            tool = tool,
            summary = summary,
            status = Status.RUNNING,
        )
        _events.value = (_events.value + ev).takeLast(MAX_KEEP)
        _current.value = ev
        return ev.id
    }

    fun finish(id: String, ok: Boolean, detail: String, costMs: Long) {
        val status = if (ok) Status.OK else Status.FAIL
        update(id, status, detail, costMs)
        if (_current.value?.id == id) _current.value = null
    }

    fun markStatus(id: String, status: Status, detail: String = "") {
        update(id, status, detail, 0L)
    }

    private fun update(id: String, status: Status, detail: String, costMs: Long) {
        _events.value = _events.value.map {
            if (it.id != id) it else
                it.copy(
                    status = status,
                    detail = detail.take(200),
                    costMs = if (costMs > 0) costMs else it.costMs,
                )
        }
        if (_current.value?.id == id) {
            _current.value = _events.value.firstOrNull { it.id == id }
        }
    }

    fun clear() {
        _events.value = emptyList()
        _current.value = null
    }

    // ---------- 危险动作确认 ----------

    /**
     * 请求用户确认，阻塞等待（工具执行线程），最多 [timeoutMs]。
     * 超时按"拒绝"处理 —— 宁可少做，不要背着用户做掉不可逆的事。
     */
    fun requestConfirm(summary: String, reason: String, timeoutMs: Long = 60_000): Boolean {
        val id = UUID.randomUUID().toString().take(8)
        val latch = CountDownLatch(1)
        latches[id] = latch
        _pending.value = PendingConfirm(id, summary, reason)
        return try {
            val answered = latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            val allow = answered && (decisions[id] ?: false)
            appendNote(
                summary = summary,
                status = if (allow) Status.OK else Status.DENIED,
                detail = if (answered) (if (allow) "你已允许" else "你已拒绝") else "等待超时，已按拒绝处理",
            )
            allow
        } catch (_: InterruptedException) {
            false
        } finally {
            latches.remove(id)
            decisions.remove(id)
            if (_pending.value?.id == id) _pending.value = null
        }
    }

    /** UI 侧回填决定 */
    fun answerConfirm(id: String, allow: Boolean) {
        decisions[id] = allow
        latches[id]?.countDown()
    }

    private fun appendNote(summary: String, status: Status, detail: String) {
        val ev = Event(
            id = UUID.randomUUID().toString().take(8),
            tool = "confirm",
            summary = summary,
            status = status,
            detail = detail,
        )
        _events.value = (_events.value + ev).takeLast(MAX_KEEP)
    }
}
