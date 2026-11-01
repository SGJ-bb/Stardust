
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
