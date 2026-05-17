package com.aicompanion.localmodel

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs

data class DeviceProfile(
    val totalRamMB: Int,
    val availableRamMB: Int,
    val apiLevel: Int,
    val isLowRamDevice: Boolean,
    val gpuSupport: Boolean,
    val availableStorageMB: Long,
    val recommendedTier: ModelTier,
    val deviceInfo: String,
    val cpuAbi: String
)

object DeviceProfiler {

    fun profile(context: Context): DeviceProfile {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val totalRamMB = (memInfo.totalMem / (1024 * 1024)).toInt()
        val availableRamMB = (memInfo.availMem / (1024 * 1024)).toInt()
        val apiLevel = Build.VERSION.SDK_INT
        val isLowRamDevice = am.isLowRamDevice
        val gpuSupport = checkGpuSupport(am)
        val availableStorageMB = getAvailableStorage(context)
        val cpuAbi = Build.SUPPORTED_ABIS?.firstOrNull() ?: "unknown"

        val recommendedTier = when {
            totalRamMB >= 6144 && gpuSupport -> ModelTier.PRO
            totalRamMB >= 4096 -> ModelTier.STANDARD
            else -> ModelTier.LITE
        }

        val deviceInfo = buildString {
            append("${Build.MANUFACTURER} ${Build.MODEL}")
            append(" | Android ${Build.VERSION.RELEASE}")
            append(" | ${totalRamMB}MB RAM")
            append(" | $cpuAbi")
            if (gpuSupport) append(" | GPU✓")
        }

        return DeviceProfile(
            totalRamMB = totalRamMB,
            availableRamMB = availableRamMB,
            apiLevel = apiLevel,
            isLowRamDevice = isLowRamDevice,
            gpuSupport = gpuSupport,
            availableStorageMB = availableStorageMB,
            recommendedTier = recommendedTier,
            deviceInfo = deviceInfo,
            cpuAbi = cpuAbi
        )
    }

    fun canRunModel(profile: DeviceProfile, model: ModelInfo): Boolean {
        if (profile.totalRamMB < model.minRamMB) return false
        if (model.gpuRequired && !profile.gpuSupport) return false
        if (model.sizeBytes > 0 && profile.availableStorageMB < model.sizeBytes / (1024 * 1024) + 50) return false
        return true
    }

    fun getIncompatibilityReason(profile: DeviceProfile, model: ModelInfo): String? {
        val reasons = mutableListOf<String>()
        if (profile.totalRamMB < model.minRamMB) {
            reasons.add("需要${model.minRamMB}MB RAM，当前${profile.totalRamMB}MB")
        }
        if (model.gpuRequired && !profile.gpuSupport) {
            reasons.add("需要GPU加速，当前设备不支持")
        }
        val requiredMB = if (model.sizeBytes > 0) model.sizeBytes / (1024 * 1024) + 50 else 0
        if (requiredMB > 0 && profile.availableStorageMB < requiredMB) {
            reasons.add("需要${requiredMB}MB存储，当前可用${profile.availableStorageMB}MB")
        }
        return if (reasons.isEmpty()) null else reasons.joinToString("；")
    }

    private fun checkGpuSupport(am: ActivityManager): Boolean {
        return !am.isLowRamDevice
    }

    private fun getAvailableStorage(context: Context): Long {
        return try {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.availableBytes / (1024 * 1024)
        } catch (_: Exception) {
            0L
        }
    }
}
