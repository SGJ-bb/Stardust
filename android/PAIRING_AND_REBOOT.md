# 配对、重启、华为：三个问题的准确结论

> 2026-10-24 10:30~11:00，基于 AOSP 源码核对 + 真机（Redmi / Android 14 / HyperOS）实测

## 一、TLS-PSK 是干什么的（以及：现在其实不用它）

**先回答概念**：配对时要开一条加密通道，但双方没有任何共同信任的证书（没有 CA），
所以需要用一个"双方都知道的密码"来协商密钥 —— 这就是 PSK（Pre-Shared Key）。
配对码（6 位数字）就是这个密码的来源：用 SPAKE2 把它变成高熵共享密钥，再拿去做加密/认证。
Java 标准 TLS 栈不支持 `TLS_PSK_*` 套件，所以早前我说"必须引 BouncyCastle 或移植 bssl"。

**但核对 AOSP `tls_connection.cpp` / `pairing_connection.cpp` 后必须更正**：
当前实现**不是** PSK 套件，而是下面这套：

```
1. TLS 1.3 握手（双方各自自签证书，且【接受任意对端证书】——证书只用来加密，不用来认证）
2. 从 TLS 导出 64 字节密钥：ExportKeyingMaterial(kExportedKeySize = 64)
3. 口令 = 6 位配对码 “+” 这 64 字节（追加到 pswd_ 末尾）   ← 防止连接被劫持
4. 用这个口令跑 SPAKE2（SPAKE25519），交换 SPAKE2_MSG
5. 用 SPAKE2 的结果初始化 AES-128-GCM，加密交换 PeerInfo（里面是我们的 adb 公钥）
```

包头只有 6 字节（`packed`）：

```c
struct PairingPacketHeader {
    uint8_t  version;   // = 1
    uint8_t  type;      // PairingPacket.Type: SPAKE2_MSG=0, PEER_INFO=1
    uint32_t payload;   // 长度，网络字节序（htonl）
} __attribute__((packed));
```

`pairing.proto` 全文只有：

```proto
message PairingPacket { enum Type { SPAKE2_MSG = 0; PEER_INFO = 1; } }
```

**结论：不需要 TLS-PSK 套件了。** 只要能做到"TLS 1.3 + 取导出密钥"，剩下的就是 SPAKE2 + AES-GCM。
而 BouncyCastle 恰好提供 `exportKeyingMaterial`（已核实存在于
`org.bouncycastle.tls.TlsContext` / `AbstractTlsContext` / JSSE `ProvSSLConnection`）。

## 二、免电脑配对：可行性重新评估

| 环节 | 现状 | 难度 |
|---|---|---|
| mDNS 发现 `_adb-tls-pairing._tcp` | ✅ 已有 `AdbDiscovery` | 完成 |
| TLS 1.3（自签证书 + 信任任意对端） | BouncyCastle 可做 | 中 |
| `ExportKeyingMaterial(64)` | BC 支持 | 低 |
| SPAKE25519（口令 = 码 + EKM） | ✅ 已实现 + 7 条单测（`Spake25519.kt`） | 完成 |
| 口令要"码 + EKM"拼接 | **新发现，之前漏了** | 低 |
| AES-128-GCM 加密 PeerInfo | 需照 `aes_128_gcm.h` 对齐 nonce/计数 | 中 |
| PeerInfo 定长结构（8192 字节，非 protobuf） | 需按 `sizeof(PeerInfo)` 打包 | 中 |
| 真机联调 | 需要**你屏幕上的 6 位配对码**，失败无报错只能逐段打中间值 | 高 |

**必须诚实说明**：这套东西不能"写完就成"。adbd 失败时只回"配对失败"，不给原因；
所以只能靠逐段打印（EKM 前 8 字节、SPAKE 消息长度、PeerInfo 密文长度）跟 `adb pair` 的成功日志比对。
**这需要你在旁边配合输入配对码，可能往返几轮。**

## 三、"每次重启都要弄"——真相与已做的修复

### 真相：重启本身不需要重配

| 东西 | 重启后 | 说明 |
|---|---|---|
| 无障碍服务开关 | ✅ **自动恢复** | 系统记住 `enabled_accessibility_services`，重启自动重绑 |
| `WRITE_SECURE_SETTINGS` 等 `pm grant` 的权限 | ✅ **保留** | 一次性授予，持久生效 |
| 应用设置 / 密钥 | ✅ 保留 | 存 files/ 与 prefs |
| **`adb tcpip 5555`** | ❌ **丢失** | adbd 回 USB 模式，端口关闭 —— 这才是"要重弄"的真正原因 |
| 自建 ADB 的授权 | ❌ 需重来 | 端口重开后要重新信任密钥 |

所以：**只要不用 shell 通道，重启后什么都不用做。** 之前"每次都要弄"是因为我们一直在测 adb 通道。

### 已做的三处修复（真机验证）

1. **开机自愈**：`BootReceiver` 在开机 8 秒后用 `WRITE_SECURE_SETTINGS` 把无障碍开关写回去，
   并清掉历史误记。实测（故意关掉再触发）：

   ```
   I A11ySelfHeal: 自愈成功：com.aicompanion/com.google.android.accessibility.selecttospeak.SelectToSpeakService
   I UiProbe: HEAL before=false result=HEALED after=true canHeal=true
   ```

2. **改系统设置不再需要 shell**：`ShellExecutor` 增加快路径 —— 命中
   `settings put <secure|system|global> <key> <value>` 且具备 `WRITE_SECURE_SETTINGS` 时，
   **由应用直接写**，不经过 Shizuku/ADB。实测：

   ```
   SHELL[settings put global stra_test 42] exit=0 mode=app-settings
   读回=[42]
   ```

3. **伪装服务成为默认**（微信节点树能读）＋ 深度自动升级，见 `WEIXIN_A11Y.md` §11。

### 还剩什么必须靠 shell（现在基本可忽略）

`pm force-stop`、`input text`（中文）、装/卸应用。日常"看界面、点按、改设置、截屏"都不需要了。

## 四、华为那台（Mate 10 / Android 10）

**结论：没有可靠解法，建议放弃 adb 通道，改用"无障碍 + WRITE_SECURE_SETTINGS"。**

依据（当时实测）：
- USB 传输：`state=4/7`，`adb devices` 正常，能授权；
- 应用走 TCP（127.0.0.1:5555）：弹「允许」，点「始终允许」后系统记录 `state=2, alwaysAllow=true`，
  但 **adbd 之后不再发任何报文**，客户端只能等到超时；
- 公钥编码已与官方 `adbkey.pub` **逐字节比对一致**，签名算法也正确 —— 不是我们写错；
- 华为对"网络传输"有独立授权策略，批准不落 `adb_keys`。

能试但收益不大的三条：
1. **root 后直接写 `/data/misc/adb/adb_keys`** —— 最彻底，但要有 root；
2. **装 Shizuku**（用配对启动服务，绕开 adbd 的 TCP 授权问题）—— 可行，但 Android 10 首次仍需电脑跑一次；
3. **换机型** —— 红米上同样代码直接通过，说明纯属 OEM 策略差异。

**好消息**：按现在的方案，华为那台不需要 adb 也能干活（树能读、能点、能用 WRITE_SECURE_SETTINGS 改设置）。
前提是伪装的类名在华为的 ROM 上也生效——这点**还没在那台机上验证过**（需要装机实测）。

## 五、下一步（需要你配合的事）

免电脑配对（P1）我按上面的流程做，但验证必须两个条件同时满足：

1. 你手机打开 **开发者选项 → 无线调试 → 使用配对码配对设备**；
2. 把屏幕上的 **6 位配对码 + 配对端口** 告诉我（端口每次变，用我加的「扫描本机端口」能自动读到）。

有这两样，我就能一轮轮对着日志调；没有配对码，只能盲写，我不会拿"看起来完整"的东西糊你。
