# P1：配对码配对（免电脑）——实现规格与阻塞点

> 目标：手机「开发者选项 → 无线调试 → 使用配对码配对」弹 6 位码 → 应用里输一次 → 建立信任。
> 之后靠 mDNS 自动找端口直连，**不再需要电脑，也不需要 Shizuku**。
>
> 当前状态：**未实现，但已解除"无源码"阻塞**（参考实现在 `reference/adb_pairing/`）。剩纯工程量，见 §3.2。

## 1. 完整协议流程（Android 11+）

```
① 用户打开「无线调试」，点「使用配对码配对设备」
   → 屏幕显示：6 位码 + IP:配对端口（形如 192.168.1.7:37xxx）
   → 系统同时通过 mDNS 广播 _adb-tls-pairing._tcp

② 应用连 TCP 到配对端口（明文），启动 adb pairing 会话

③ SPAKE2（SPAKE25519）
   双方用 6 位码作为口令，各发一个 "message"（curve25519 上的点）
   收到对方 message 后本地计算 → 得到 32 字节共享密钥 PSK
   任何一步的点/哈希/名字对不上 ⇒ PSK 不同 ⇒ 第四步 TLS 握手失败

④ TLS-PSK 握手（cipher: TLS_PSK_WITH_AES_128_GCM_SHA256）
   用上一步的 PSK 建立加密通道

⑤ 通道内交换 PairingPacket
   client → PeerInfo{ type=adb.rsa.publickey, data=<我们的公钥(adb 格式)> }
   server → PeerInfo{ type=adb.rsa.publickey, data=<adbd 公钥> }
   成功：adbd 把我们的公钥写进自己的信任列表 ⇒ 后续 AUTH 不再弹窗

⑥ 关闭配对连接；用 mDNS 找 _adb-tls-connect._tcp 的端口
   → 走正常的 CNXN/AUTH（这部分已实现，见 AdbConnection）
```

## 2. 已经具备的部分

| 部分 | 状态 |
|---|---|
| adb 线协议（CNXN/AUTH/OPEN/WRTE/CLSE） | ✅ 已实现并实测 |
| 密钥与 adb 公钥编码（mincrypt RSAPublicKey） | ✅ 已实测被 adbd 接受 |
| mDNS 端口发现（`AdbDiscovery`） | ✅ 已实现（`_adb-tls-connect._tcp` / `_adb-tls-pairing._tcp`） |
| 配对后的能力层（settings/pm/input/screencap） | ✅ 实测五项全过 |

也就是说：**除了 ③④⑤，其余全通了**。

## 3. 阻塞点现状（2026-10-23 更新：源码已拿到）

### 3.1 参考源码已落地

通过 `ghproxy.net` 镜像（GitHub 直连不通）已下载到 `reference/adb_pairing/`：

| 文件 | 大小 | 作用 |
|---|---|---|
| `pairing_auth.cpp` | 9.6 KB | SPAKE2 封装：角色、双方名字、密钥派生 |
| `pairing_connection.cpp` | 17.9 KB | 连接层：TLS 握手 + `PairingPacketHeader` 读写 |
| `spake25519.cc` | 24 KB | BoringSSL 的 SPAKE25519 实现（curve25519 + hash-to-curve） |
| `spake25519_test.cc` | 4.9 KB | 单测（可作为对齐参考） |

**顺带证实了两件事**：

1. `static const uint8_t kClientName[] = "adb pair client";` / `kServerName = "adb pair server"` ——
   双方名字参与 transcript，`sizeof()` 含结尾 `\0`（16 字节）。这就是"差一个字节就失败"的典型位置。
2. 配对连接**是 TLS**（`#include <adb/tls/tls_connection.h>`），PSK 由 SPAKE2 派生；
   TLS 内交换的包是 `PairingPacketHeader{ version=1, type, payload_size }`（**packed，6 字节**）+ protobuf。

> 结论：这不是"逆向"，AOSP 的 adb 与 BoringSSL 都是 Apache-2.0 / OpenSSL 许可，源码公开。
> Shizuku 做的是移植或直接调用，任何人（包括我们）都可以照着实现。

### 3.2 剩下的真实工作量

| 工作 | 说明 | 预估 |
|---|---|---|
| 移植 SPAKE25519 到 Kotlin | curve25519 域运算 + Elligator2 hash-to-curve + SHA-512 transcript，约 800~1200 行；用 `spake25519_test.cc` 的向量做单测 | 0.5~1 天 |
| TLS-PSK 通道 | Java JSSE **不支持** `TLS_PSK_*`，需 BouncyCastle 或自实现；还要用我们的 RSA 密钥生成自签客户端证书 | 0.5 天 |
| PairingPacket 编解码 | 6 字节 packed header + protobuf(PeerInfo{type,data}) | 0.5 天 |
| 真机联调 | 每改一版要真机输配对码验证；失败无报错，只能逐段打印中间值比对 | 0.5~1 天 |

## 4. 建议的推进方式（按可行性排序）

| 方案 | 代价 | 结果 |
|---|---|---|
| **A. 拿到 AOSP 源码再移植**（`packages/modules/adb/pairing_auth/*` + `boringssl/crypto/curve25519/spake25519.c`） | 1~2 天 + 真机联调 | 真正免电脑、免第三方 App |
| **B. 首信任用一次 Shizuku**（它已经把这套做完了），之后我们自己连 | 装一次 | 之后可删掉 Shizuku，靠 mDNS + 已信任密钥继续用 |
| **C. 首信任用一次 `adb tcpip`（脚本已支持）** | 一次电脑 | 重启后要重做 |

**我的建议：先走 B 或 C 把功能跑起来，A 单独排期。** 因为 A 的 90% 时间花在"密码学对齐"上，
而它对用户可见的差别只有"首次是否要装一次东西"。

## 5. 如果决定做 A，落地步骤

1. 拿到 `spake25519.c/h`、`pairing_auth.cpp`、`pairing_connection.cpp`、`adb_wifi.cpp`（配对部分）；
2. 先做一个**纯 JVM 单元测试**：用参考实现的一组中间值（M/N 点、两个 message、最终 PSK）做断言，
   循环到 byte-for-byte 一致 —— **这一步不通过，不要碰 Android 代码**；
3. TLS-PSK：用 BouncyCastle 建 `SSLEngine`，`PSKIdentity`/`PSKKeyManager` 传第 2 步的 PSK；
4. 通道内按 `PairingPacket` 的 protobuf 结构发 `PeerInfo`；
5. 真机联调：`adb pair` 的命令行行为可作为对照（同一台机器上 `adb pair ip:port code` 能成功，
   说明端口/码是对的，失败就只可能是我们的 ③④）。

## 6. 进度（2026-10-23 晚）

### ✅ 已完成：SPAKE25519 的 Kotlin 实现

`app/src/main/java/com/aicompanion/adb/Spake25519.kt`（纯 Kotlin + BigInteger，无新依赖），
严格按 `spake25519.cc` 移植：

- M/N 常量直接取源码注释里的 encoded 值；
- 私钥 = `sc_reduce(random64)` 再左移 3 位；
- 密码标量 = `sc_reduce(SHA512(password))`，并按低 3 位补 `L / 2L / 4L`（BoringSSL 的历史修补，**必须照做**）；
- 双方名字 = `"adb pair client"` / `"adb pair server"`（含 `\0`，16 字节）；
- 最终 key = SHA512(名字长度前缀 + 名字 + 双方消息 + DH 点 + 密码哈希)。

**单元测试 7/7 通过**（`app/src/test/java/com/aicompanion/adb/Spake25519Test.kt`，JVM 上跑）：

```
✓ basePointHasOrderL        L·B = 单位元（证明曲线运算/d 常数正确）
✓ basePointRoundTrip        基点解码→编码往返一致
✓ doubleEqualsAddToSelf     add(B,B) == 2·B
✓ maskPointsRoundTrip       M / N 编解码往返一致且互不相同
✓ aliceAndBobDeriveSameKey  同口令 → 双方 PSK 相同（64 字节）
✓ wrongPasswordProducesDifferentKey  错口令 → PSK 不同
✓ rejectsInvalidPoint       非法点被拒绝
```

> 注意：这只能证明"实现自洽 + 编码正确"。**是否与 adbd 真正兼容，必须真机配对验证。**

### ⏳ 下一步

| 步骤 | 做法 | 依赖 |
|---|---|---|
| TLS-PSK 通道 | BouncyCastle `org.bouncycastle.tls`（已确认含 `TlsPSKKeyExchange` / `TlsPSKIdentity` / `PskKeyExchangeMode`） | 引入 `bctls-jdk18on` |
| PairingPacket | 6 字节 packed header + protobuf（`PeerInfo{type,data}`，可以用极简手写编码，不必上 protobuf 运行时） | 无 |
| 接线 | 配对成功后把公钥交给 adbd，随后用 mDNS 找端口 + TLS 连接 | 已有 `AdbDiscovery` |
| 真机联调 | 备用机 USB 连电脑，`adb logcat` + 逐段打印中间值比对 | 用户提供设备 |

---

## ✅ 真机打通（2026-10-24，红米 / HyperOS，Android 13）

真机一次跑通：`TLS 1.3 → EKM(64) → SPAKE2 → AES-128-GCM → 双向 PeerInfo → ok=true`，
并正确读出对端设备公钥前缀 `adb-SOQ8NNCQGITOSOFU-…`。

### 三个"必踩"的坑（都是 BouncyCastle 与 BoringSSL 的行为差异）

| # | 现象 | 根因 | 修法 |
|---|---|---|---|
| 1 | TLS 握手立刻 `internal_error(80)`，栈在 `Certificate.encode` → `IllegalStateException` | `Certificate(TlsCertificate[])` 构造把 `certificateRequestContext` 置 **null**，而 BC 的 `encode()` 断言 `(context != null) == isTLSv13` | 用 `Certificate(ByteArray(0), arrayOf(CertificateEntry(cert, null)))`（握手期 context 是空 opaque8） |
| 2 | 握手过证书后仍 `internal_error(80)`，栈在 `TlsUtils.generate13CertificateVerify` | 凭证签名器的 `SignatureAndHashAlgorithm` 传了 `null`；TLS 1.3 下 RSA **只能用 PSS**，PKCS#1 v1.5 非法 | 传 `SignatureAndHashAlgorithm.rsa_pss_rsae_sha256`（BC 内部走 `BcTlsRSAPSSSigner`） |
| 3 | 握手成功但 `exportKeyingMaterial` 抛 `IllegalStateException: Export of key material only available from notifyHandshakeComplete()` | BC 在 `completeHandshake()` 后**销毁** exporter master secret（`SecurityParameters.getExporterMasterSecret()` 变 null） | 在 `TlsClient.notifyHandshakeComplete()` 回调里取 EKM 并存字段，握手返回后再用 |

> 排查方法：`tools/pairprobe/EkmProbe.java` 在电脑本地起一个 BC 的 TLS 1.3 服务端，
> 用**同一套客户端代码**复现，能直接打出真实异常信息 —— 不用真机重装、不用重新要配对码。
> 在 Windows 上编译运行见该目录 `run.ps1`（或直接看文件头注释）。

### 配对端口怎么找（mDNS 不可靠）

- adbd 的配对服务**只在「使用配对码配对设备」对话框打开时**才监听，关掉即撤；
- 该 socket 的 uid 是 **1000（system）**，本地地址 `::`（全地址，回环也能连），
  而无线调试连接口是 uid 2000 的 `5555`/随机口 —— 这是区分依据；
- 这台机器上 **mDNS (`_adb-tls-pairing._tcp`) 三轮都拿不到广播**，`NSD` 不可依赖；
- 应用进程**读不到** `/proc/net/tcp`（SELinux 拒绝），所以 in-app 自动发现只能靠：
  1. 读配对对话框上的「IP 地址和端口」文本（自己的无障碍服务就能拿到），或
  2. 回环端口扫描后逐个尝试 TLS 握手（配对口接受任意证书，连接口会拒）。
- 对话框显示的 `192.168.88.x:port` 只是给电脑看的地址，本机用 `127.0.0.1:port` 同样能连。

### 调用方式（调试入口）

```bash
adb shell "am startservice -n com.aicompanion/.screen.UiProbeService --es pair '123456:127.0.0.1:46771'"
adb logcat -d | grep -E "UiProbe : PAIR|AdbPairing"
```

---

## TLS 版 adb 通道（无线调试端口）——进展与卡点（2026-10-24 15:40）

### 已打通的部分

| 环节 | 状态 | 关键点 |
|---|---|---|
| 端口发现 | ✅ | 缓存 → 邻近试探 → 回环全量扫描（64 线程，约 11s）。探测方式=`明文 CNXN`→回 `A_STLS`/`CNXN` 即为 adb 口 |
| 无线调试口识别 | ✅ | 该口不能"裸 TLS 先聊"：直接发 ClientHello 会被 adbd 当成非法包头（`handshake_failure(40)`） |
| TLS 1.3 握手 | ✅ | **必须用系统 JSSE（Conscrypt=BoringSSL）**，BC 的 ClientHello 与官方 adb 抓包结构完全一致仍被拒 |
| 客户端证书认证 | ✅ | 证书必须用**配对时那把 AdbKey** 现造（自签、SHA256withRSA、JCA 直接签，绕开 BC provider 冲突）。握手后 adbd 直接给 CNXN 横幅，**不需要 AUTH 签名** |
| 服务端 AUTH | 免了 | 说明 TLS 层的证书已被信任名单接受 |

### 卡点：`A_STLS` 与 `OPEN` 的交互

握手后 adbd 会发两条：CNXN（设备横幅）+ 一条 `A_STLS`（arg0=`0x01000000`）。实测：

| 我们对 A_STLS 的处理 | 结果 |
|---|---|
| 不理它，直接 `OPEN shell:` | `OPEN` 无任何响应，看门狗超时 |
| 回一条 A_STLS | 服务端立刻断连（`Socket closed`） |
| 把 A_STLS 放在 TLS 里第一条发 | 立刻断连 |
| 明文阶段先 A_STLS 再 CNXN | 立刻断连（`EOF at 7/24`） |
| 先收 STLS 再发 CNXN | 收不到横幅，后续无响应 |
| `exec:` 服务替代 `shell:` | 同样无响应 |
| CNXN 版本改 `0x01000001`（同官方 adb） | 无变化 |

**结论：TLS 之后 adb 服务端处于一种"只认 CNXN、不处理 OPEN"的状态，且对客户端的 A_STLS 极敏感。**
在官方 adb 上同一端口一切正常（抓包可见其 ClientHello 与客户端证书），所以差异在**升级后的消息时序**，不在加密层。

### 抓包方法（复现用）

`tools/pairprobe/Proxy.java` 是透明 TCP 代理，把客户端/服务端前 600 字节落盘成 hex：

```bash
java -cp . Proxy 44000 192.168.88.247 41837 F:/stradust/android/tools/pairprobe/cap
adb connect 127.0.0.1:44000      # 让官方 adb 走代理，抓它的真实字节
```

`tools/pairprobe/TlsProbe.java` 可在电脑上试任意开关（`stls` / `pkcs1` / `tls12` / `nocert` + ALPN），
`tools/pairprobe/JsseProbe.java` 用于验证 JSSE 行为。三个都能直连设备端口，秒级迭代。

### 下一步可选

1. **原生移植 AOSP `transport.cpp` 的 STLS 状态机**（Shizuku 同款思路）——最稳，代价是要编 BoringSSL + 三套 ABI。
2. **继续用抓包做时序比对**：把官方 adb 的 TLS 记录**长度序列**与我们的对照，定位它到底发了几条、什么顺序（加密内容看不到，但条数/大小能对上）。
3. 退化方案：配对完成后，仍走一次 `adb tcpip 5555` + 明文通道（需要一次电脑），TLS 留作后续。

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

---

## ✅ 常驻 shell 服务（不依赖 Wi-Fi / adb，Shizuku 同款形态，2026-10-24 17:38）

### 为什么需要它

adbd 通道有两个硬伤：**Wi-Fi 一关就消失**、**端口每次重启都变**。
Shizuku 的做法是「用一次 shell 启动一个常驻进程」，之后所有操作走本地 socket，adbd 完成使命就退场。
我们照做，但不需要用户装任何第三方应用。

### 实现

| 环节 | 做法 |
|---|---|
| 守护进程本体 | `tools/daemon/ShellDaemon.java` → d8 编成 5KB dex → 打包进 `assets/stradust_daemon.dex` |
| 启动方式 | 解出 dex 到 `/sdcard/Android/data/<pkg>/files/`，用 `setsid env CLASSPATH=<dex> app_process /system/bin --nice-name=stradust_shell ShellDaemon <port> <token>` 拉起 |
| 权限 | app_process 继承调用者身份 → **uid 2000 (shell)**，与 `adb shell` 等价 |
| 通信 | TCP 127.0.0.1 + 每次安装随机端口 + 32 字节 token；握手不过直接断开，无回显 |
| 协议 | `AUTH <token>\n` → `OK\n` → 循环 `[4B len][命令]` / `[4B len]"EXIT <code>\n<输出>"` |

### 踩到的坑（关键）

**非 PTY 的 shell 起不了 app_process。**
我们原来的 adb 客户端用 `shell:<cmd>`（非 PTY）执行，`app_process` 会**静默失败**——
连重定向的日志文件都不会写出内容，`ps` 里也看不到进程。换成 `shell,pty:<cmd>` 后立刻正常。
（AOSP 的 `adb shell` 默认就走 PTY，所以手工测试一直是好的，很难联想到。）

另外两个细节：`setsid` 必须加（否则 adb 会话结束会把守护进程一起带走）；
`app_process` 首次启动要加载 dex，实测 4~5 秒，等待窗口要给到 8 秒以上。

### 实测结果

```
（Wi-Fi 已关闭，adbd 无线调试端口已消失）
device info      → 通道：常驻服务（不依赖 adb/Wi-Fi）
                   uid=2000(shell) …
device settings  → global/adb_wifi_enabled = 0
device app_list  → package:com.aicompanion …
```

### 分工与局限

- **首次/重启后**：需要一次引导（无线调试开着即可自动完成），因为常驻进程随重启消失；
- **日常**：全部走常驻服务，Wi-Fi、无线调试、adb 都不参与；
- 若守护进程被系统杀掉：下一次命令会自动重新拉起（前提是 adb 通道当时可用）。
