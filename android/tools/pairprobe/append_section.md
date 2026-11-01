
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
