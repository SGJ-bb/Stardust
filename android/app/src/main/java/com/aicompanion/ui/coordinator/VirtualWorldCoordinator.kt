package com.aicompanion.ui.coordinator

import android.content.Context
import com.aicompanion.util.AppLogger
import com.aicompanion.virtualworld.VirtualWorldManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VirtualWorldCoordinator(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    private val memoryScope: CoroutineScope
) : BaseCoordinator(context, lifecycleOwner) {

    companion object {
        private const val TAG = "VirtualWorldCoordinator"
    }

    private var virtualWorldRunnable: Runnable? = null

    fun scheduleTick() {
        virtualWorldRunnable = Runnable {
            if (!isAtLeast(androidx.lifecycle.Lifecycle.State.CREATED)) return@Runnable

            memoryScope.launch {
                try {
                    val worldsToTick = mutableListOf<VirtualWorldManager>()

                    val globalVw = VirtualWorldManager(context, "")
                    if (globalVw.isEnabled && globalVw.isRunning) {
                        worldsToTick.add(globalVw)
                    }

                    try {
                        val gcManager = com.aicompanion.groupchat.GroupChatManager(context)
                        gcManager.load()
                        for (group in gcManager.getAllGroups()) {
                            val groupVw = VirtualWorldManager(context, group.id)
                            if (groupVw.isEnabled && groupVw.isRunning) {
                                worldsToTick.add(groupVw)
                            }
                        }
                    } catch (e: Exception) { AppLogger.e(TAG, "scheduleTick: ${e.message}") }

                    for (vwManager in worldsToTick) {
                        if (vwManager.shouldTick()) {
                            try { vwManager.runSimulationTick() }
                            catch (e: Exception) { AppLogger.e(TAG, "virtualWorldTick: ${e.message}") }
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "scheduleVirtualWorldTick: ${e.message}")
                }

                withContext(Dispatchers.Main) {
                    if (isAtLeast(androidx.lifecycle.Lifecycle.State.CREATED) && virtualWorldRunnable != null) {
                        handler.postDelayed(virtualWorldRunnable!!, 60000L)
                    }
                }
            }
        }
        handler.postDelayed(virtualWorldRunnable!!, 60000L)
    }

    fun findActiveVirtualWorld(): VirtualWorldManager? {
        val globalVw = VirtualWorldManager(context, "")
        if (globalVw.isEnabled) return globalVw

        try {
            val gcManager = com.aicompanion.groupchat.GroupChatManager(context)
            gcManager.load()
            for (group in gcManager.getAllGroups()) {
                val groupVw = VirtualWorldManager(context, group.id)
                if (groupVw.isEnabled) return groupVw
            }
        } catch (e: Exception) { AppLogger.e(TAG, "findActiveVirtualWorld: ${e.message}") }

        return null
    }

    override fun onPause() {
        virtualWorldRunnable?.let { handler.removeCallbacks(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        virtualWorldRunnable?.let { handler.removeCallbacks(it) }
    }
}
