/**
 * 无线调试端口发现（mDNS / NsdManager）。
 *
 * 为什么需要它：Android 11+ 的「无线调试」每次开关/重启都会换端口，
 * 手抄一次还行，天天抄就不现实。系统会通过 mDNS 广播两个服务：
 *   · `_adb-tls-pairing._tcp`  —— 配对端口（弹 6 位码时才有）
 *   · `_adb-tls-connect._tcp`  —— 连接端口（配对完成后再连这个）
 * 用 NsdManager 把它们找出来，用户就只需要"输一次配对码"，之后端口自己填。
 *
 * 这是"免电脑"链条里除 SPAKE2 之外唯一需要写的东西，且不涉及密码学，可独立验证。
 */
package com.aicompanion.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import com.aicompanion.util.AppLogger
import java.net.NetworkInterface
import java.util.Collections

object AdbDiscovery {

    private const val TAG = "AdbDiscovery"

    const val SERVICE_PAIRING = "_adb-tls-pairing._tcp"
    const val SERVICE_CONNECT = "_adb-tls-connect._tcp"

    data class Service(val name: String, val host: String, val port: Int) {
        override fun toString(): String = "$host:$port"
    }

    /** 本机所有网卡地址（含回环）：用来判断扫到的服务是不是"自己这台" */
    fun ownAddresses(): Set<String> {
        val out = mutableSetOf("127.0.0.1", "::1")
        runCatching {
            for (nif in NetworkInterface.getNetworkInterfaces()) {
                for (addr in nif.inetAddresses) {
                    val h = addr.hostAddress ?: continue
                    out.add(h.substringBefore('%'))
                }
            }
        }
        return out
    }

    /**
     * 只返回"本机"的服务。
     * 局域网里可能有多台开了无线调试的手机，mDNS 会把别人的也扫出来 ——
     * 不过滤就会出现"扫到别人端口 / 连不上"。
     */
    fun discoverOwn(
        context: Context,
        serviceType: String = SERVICE_CONNECT,
        timeoutMs: Long = 5000,
        onResult: (List<Service>, List<Service>) -> Unit,
    ) {
        val mine = ownAddresses()
        discover(context, serviceType, timeoutMs) { all ->
            val own = all.filter { it.host in mine }
            val others = all.filter { it.host !in mine }
            onResult(own, others)
        }
    }

    /**
     * 扫描指定服务类型。
     * @param onResult 扫描结束（或超时）时回调，没有发现就返回空列表
     */
    fun discover(
        context: Context,
        serviceType: String = SERVICE_CONNECT,
        timeoutMs: Long = 5000,
        onResult: (List<Service>) -> Unit,
    ) {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
        if (nsd == null) {
            onResult(emptyList())
            return
        }
        val found = Collections.synchronizedList(ArrayList<Service>())
        val main = Handler(Looper.getMainLooper())
        var finished = false

        fun finish() {
            if (finished) return
            finished = true
            main.post { onResult(found.toList()) }
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                AppLogger.i(TAG, "开始扫描 $serviceType")
            }

            override fun onServiceFound(info: NsdServiceInfo) {
                // 解析拿 IP/端口；同一服务可能被多次回调，靠 host:port 去重
                @Suppress("DEPRECATION")
                nsd.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        AppLogger.w(TAG, "resolve 失败 $errorCode")
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        val host = serviceInfo.host?.hostAddress ?: return
                        val port = serviceInfo.port
                        if (port <= 0) return
                        if (found.none { it.host == host && it.port == port }) {
                            found.add(Service(serviceInfo.serviceName ?: "", host, port))
                            AppLogger.i(TAG, "发现 ${serviceInfo.serviceName} → $host:$port")
                        }
                    }
                })
            }

            override fun onServiceLost(info: NsdServiceInfo) {}

            override fun onDiscoveryStopped(serviceType: String) {
                finish()
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                AppLogger.w(TAG, "扫描失败 $errorCode")
                finish()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                finish()
            }
        }

        try {
            nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            AppLogger.e(TAG, "discoverServices 异常: ${e.message}", e)
            onResult(emptyList())
            return
        }
        main.postDelayed({
            runCatching { nsd.stopServiceDiscovery(discoveryListener) }
            finish()
        }, timeoutMs)
    }
}
