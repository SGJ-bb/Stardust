
---

## ✅ TLS 版 adb 通道打通（2026-10-24 16:20，同一台红米）

结果：`uid=2000(shell)`，`id`/`getprop`/`ls` 等命令 exit=0；缓存端口 7ms 命中，230ms 建立 TLS 通道，
**全程零用户操作、不需要电脑、不需要 Shizuku**。

### 完整时序（AOSP 源码 + 抓包 + adbd 日志三方对齐）

```
客户端                                    adbd
─────────────────────────────────────────────────────────
TCP 连接 127.0.0.1:<无线调试端口>
CNXN(version, maxdata, "host::features=...")
                              ──────►    handle_new_connection: use_tls → send_tls_request
                              ◄──────    A_STLS(arg0=A_STLS_VERSION=0x01000000)
A_STLS(arg0=0x01000000)                   （客户端回一条，设备收到后停读线程）
                              ──────►    handle_packet(A_STLS) → adbd_auth_tls_handshake
TLS 1.3 就地握手（客户端证书=配对那把 AdbKey）
                              ◄──────    握手成功 → adbd_wifi_secure_connect →
                                         handle_online + send_connect
                              ◄──────    CNXN(设备横幅)      ← 客户端**只等，不发 CNXN**
OPEN("shell:...")             ──────►    case A_OPEN → create_local_service_socket
                              ◄──────    OKAY → WRTE… → CLSE
```

### 卡了半天的真凶（三个，全部来自 AOSP 源码）

1. **客户端的 ClientHello 必须用系统 JSSE（Conscrypt=BoringSSL）**。
   BouncyCastle 发出的 ClientHello 与官方 adb 抓包逐字段一致（1303/1301/1302 + 同样 5 个扩展），
   adbd 仍回 `handshake_failure(40)`；换系统 SSLSocket 立刻成功。

2. **`A_STLS` 的 arg0 是 `0x01000000`（adb 的 1.0.0 编码），不是 `1`**。传 1 时服务端把它当非法包，
   继续按明文解析 ClientHello → 表现成"握手失败"或"静默超时"。

3. **TLS 建立后客户端绝不能再发 CNXN**（最费时的一个）。
   `adb/client/auth.cpp` 的注释就是 `"Handshake succeeded. Waiting for CNXN packet..."`。
   多发一条 CNXN → adbd `handle_new_connection` 再走一遍 → `handle_offline` → 回一条 A_STLS →
   `t->online` 被清掉 → 之后所有 `OPEN` 命中 `if (!t->online) break;` **被静默丢弃**
   （既不回 OKAY 也不回 CLSE，看起来就像服务端不理你）。

> 定位手段：`adb shell setprop log.tag.adbd VERBOSE`，然后看 `logcat -s adbd`。
> 设备端日志直接把 `Matched auth_key=… aicompanion@android` → `Handshake succeeded` →
> `adbd_wifi_secure_connect: connected` → `host-N: offline` 全打出来，比抓包更快。

### 实现要点

| 文件 | 作用 |
|---|---|
| `adb/AdbTls.kt` | 用 AdbKey 现造自签证书（JCA 签名，绕开 BC provider 冲突），SSLSocket 就地升级 |
| `adb/AdbPortFinder.kt` | 端口发现：缓存 → 邻近 240 个 → 回环全量扫；探测方式=`明文 CNXN`看是否回 A_STLS/CNXN |
| `adb/AdbConnection.kt` | STLS 状态机 + TLS 升级 + exec（TLS 下用看门狗代替 soTimeout 轮询） |
| `adb/AdbSelf.kt` | `autoConnect()`：找端口→连接→缓存；`run()` 里没有连接时自动建，上层完全无感 |

界面上「一键连接」按钮 = `AdbSelf.autoConnect()`，用户只需按一次（甚至可以完全不按：
任何需要 shell 的能力都会自动建连）。

### 关于 Wi-Fi

- 系统要求「无线调试」开关必须在**连着 Wi-Fi** 时才能打开（AOSP 的开关检查），无法绕过；
- 但通道本身连的是 `127.0.0.1`，不走网络、不耗流量、不需要同一路由器；
- 开关状态会持久化，重启后仍在，因此**重启后的用户操作 = 0**（只要 Wi-Fi 开着）。
