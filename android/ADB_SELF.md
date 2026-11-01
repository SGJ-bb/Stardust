# 自建 ADB 通道（Path 2）：不依赖 Shizuku

> 目标：把 Shizuku 的能力用自己的代码实现 —— 应用直接连上 adbd，以 shell 身份执行命令、
> 调系统服务，不装任何第三方 App。
>
> 本文是**架构 + 分阶段实现计划 + 安全/UX 约定**；当前进度见文末「阶段状态」。

## 1. 为什么可行、难点在哪

Shizuku 的本质是「让 adbd fork 一个 shell 进程，再把这个进程的能力用 binder 代理给客户端应用」。
我们能做的等价物是：**应用自己充当 adb 客户端**，连上本机的 adbd（`127.0.0.1:5555` 或无线调试端口），
通过 `shell` 服务执行命令 —— 身份同样是 `shell(2000)`，因为没有经过别人的代理，**链路还更短**。

难点只有一块：**Android 11+ 的「无线调试」需要 SPAKE2 配对**（Curve25519 上的 PAKE + TLS-PSK）。
它不是普通密码校验，写错了只会表现为「配对失败」，没有明确报错。所以把它单独隔离成一个阶段。

## 2. 分层

```
┌───────────────────────────────────────────────┐
│ UI（设置 → 无障碍 → 自建 ADB 通道）            │
├───────────────────────────────────────────────┤
│ AdbSelf（门面：状态机、串行化、审计日志）        │
├───────────────────────────────────────────────┤
│ AdbCapabilities（settings/pm/input/screencap…）│
├───────────────────────────────────────────────┤
│ AdbConnection（CNXN/AUTH/OPEN/WRTE/CLSE/OKAY） │
├───────────────────────────────────────────────┤
│ AdbKey（RSA-2048、签名、adb 公钥编码）          │
├───────────────────────────────────────────────┤
│ 传输：TCP（本机 5555 / 无线调试端口）            │
└───────────────────────────────────────────────┘
   ↑ 未做：AdbPairing（SPAKE2 + TLS-PSK），只在无线调试路径需要
```

## 3. 协议要点（实现时容易错的几处）

| 项 | 值 / 说明 |
|---|---|
| 包头 | 24 字节小端：`command, arg0, arg1, data_length, data_check, magic(=command^0xffffffff)` |
| 命令码 | `CNXN/AUTH/OPEN/OKAY/WRTE/CLSE/STLS` |
| CNXN | `arg0=version(0x01000000)`，`arg1=maxdata`，payload `"host::features=cmd,shell_v2\0"` |
| AUTH | `arg0`: `1=TOKEN / 2=SIGNATURE / 3=RSAPUBLICKEY`；签名算法 `SHA1withRSA`（PKCS#1 v1.5） |
| 公钥格式 | **不是 PEM**：`len(64) + n0inv + n[64] + rr[64] + exponent`，全部 32 位小端；base64 后接 ` user@host` |
| shell | `OPEN` 的 data = `"shell,v2,raw:<命令>\0"`；输出 `WRTE` 的 payload 第 1 字节是流 id（1=stdout/2=stderr/3=exit） |
| 流控 | 每收一个 `WRTE` 回一个 `OKAY`，否则大输出会卡住 |

## 4. 安全设计（这部分比代码更重要）

1. **密钥只存本机**：RSA-2048 私钥放 app 私有目录（`filesDir/adb/adb_key.pem`），不联网、不上传；
2. **白名单命令**：`AdbCapabilities` 只暴露固定动作（`settings put`、`pm grant`、`input`、`screencap`…），
   不把「任意 shell」交给模型；真需要时走显式开关 + 二次确认；
3. **审计日志**：每次执行记录「时间/命令/耗时/退出码/调用来源（UI 或 AI）」，本地保留 N 条，UI 可查；
4. **权限边界**：即使用户授权，shell 也读不到别的应用 `/data/data`、改不了 `/system`；
5. **可撤销**：删掉 `/data/misc/adb/adb_keys` 里那行公钥 → 立即失效；应用侧「断开」只关连接；
6. **失败即降级**：连不上就回落到 Shizuku → 无障碍，绝不静默重试刷日志。

## 5. UX 设计

- **首次**：设置页一张卡，三步引导（① 一次性开启端口 ② 授权公钥 ③ 连接测试），每步有「已完成 ✓」；
- **日常**：无感。连接在后台维持，断了自动重连（指数退避，最多 3 次），UI 只显示一个状态点；
- **失败**：给具体原因（端口未开 / 密钥未授权 / 被系统拒绝），不甩异常堆栈；
- **不打扰**：不在通知栏常驻，不申请额外前台服务；仅在设置页展示。

## 6. 分阶段计划

| 阶段 | 内容 | 状态 |
|---|---|---|
| **P0** | 密钥生成/持久化、adb 公钥编码、CNXN+AUTH 握手、shell 执行、`id` 验证 | ✅ 已实测通过 |
| **P1** | 无线调试配对（SPAKE2 + TLS-PSK），实现免电脑启动 | ⏳ 待做 |
| **P2** | 能力层：settings/pm/input/screencap/force-stop 封装 + 白名单 + 审计 | ✅ 本轮（基础动作） |
| **P3** | 稳定性：自动重连、端口探测、SELinux 兼容、机型回归 | ⏳ 待做 |

## 7. 一次性授权（P0 阶段怎么用）

P0 走「USB 起一次 tcpip + 授权公钥」，两条命令，之后应用自己连：

```bash
# ① 让 adbd 监听 TCP（重启后失效，需要重来）
adb tcpip 5555

# ② 把应用生成的公钥加进信任列表（把 <KEY> 换成应用导出的那一行）
adb root
adb shell "echo '<KEY>' >> /data/misc/adb/adb_keys"
adb shell "setprop ctl.restart adbd"
```

应用侧导出：设置 → 无障碍 → 自建 ADB 通道 →「复制公钥」（同时写一份到
`/sdcard/Android/data/com.aicompanion/files/adb_pubkey.txt`）。

P1 完成后，这一步会变成「无线调试配对码」一次输入，且 Android 13+ 可信 Wi-Fi 下可开机自启。

## 8. 实测记录与踩坑（2026-10-22，模拟器 x86_64 / Android 14）

### 验证结果

```
AdbConnection: 已连接：device::ro.product.name=sdk_phone64_x86_64;...features=shell_v2,cmd,...
AdbSelfTest:   state=(connected=true, uid=uid=0(root) gid=0(root) groups=0(root),...)
AdbSelfTest:   probe=exit=0 null      # settings get secure enabled_accessibility_services
```

- **公钥编码一次通过**：应用生成 720 字符的 adb 公钥，写进 `/data/misc/adb/adb_keys` 后
  AUTH 签名直接被接受，没有弹窗 —— 说明 mincrypt `RSAPublicKey` 布局（len/n0inv/n/rr/exponent，
  全 32 位小端）拼对了；
- **注意**：日志里是 `uid=0(root)`，因为验证时对模拟器执行过 `adb root`（adbd 以 root 跑）。
  真机未 root 时这里会是 `uid=2000(shell)`。

### 两个必须记住的协议坑

1. **流 id 的方向**：消息里 `arg0 = 发送方自己的 id`，`arg1 = 对端（我们）的 id`。
   判断"这条消息属于我这条流"必须用 `arg1 == localId`；写成 `arg0` 会把所有响应滤掉，
   表现为命令瞬间超时/空输出。而且上一条流的 `CLSE` 会残留，不过滤就会误判为"执行结束"。
2. **shell v2 的分帧不可靠**：实测 adbd 返回的 `exit` 包是 6 字节（不是 2 字节的 `[id][code]`），
   用 `[id:1][data]` 或 `[id:1][len:4][data]` 两种解析都拼不出干净的 stdout。
   **最终方案：用 legacy `shell:` 服务（负载就是纯 stdout），退出码靠
   `({ cmd ; } 2>&1; echo "__ADB_EXIT__$?")` 带回来**。行为确定、不依赖 adbd 版本。

### 与 Shizuku 的剩余差距

| 项 | 现状 |
|---|---|
| 改系统设置 / 授权 / 截图 / 点击 | ✅ 已具备（能力层已封装） |
| 免电脑启动（无线调试配对） | ❌ 待做 P1（SPAKE2 + TLS-PSK） |
| 每次重启后 | 需重新 `adb tcpip 5555`（P1 完成后改为重新输入配对码） |
| 首次授权 | 两条 adb 命令（P1 完成后改为一次配对码） |

## 9. 批判性自查与修复（2026-10-23）

把已写的代码当别人的代码审了一遍，抓到 4 个真问题（不是风格问题，是会出错的那种）：

| # | 问题 | 后果 | 修法 |
|---|---|---|---|
| 1 | `DataInputStream.readFully` + 200~300ms `soTimeout` | 超时打断在帧中间 ⇒ 半个包头被吃掉 ⇒ 之后**所有帧错位**、命令偶发空输出 | 手写 `readExact(deadline)`：超时只代表"还没数据"，继续等；到 deadline 才放弃 |
| 2 | 握手总超时 8 秒 | 非 root 真机靠**弹窗授权**，人来不及点就超时 ⇒ 真机上永远连不上 | 一旦走 `AUTH_RSAPUBLICKEY` 分支，把等待放宽到 60s，并打印"请在设备上点允许" |
| 3 | 唤醒服务只看通知，不看引擎 | 采集线程异常退出后服务仍显示"监听中"（假状态） | 加 30s 看门狗：`engine == null || !engine.isRunning` 就重建 |
| 4 | 截图没校验内容 | 半截文件也当成功 | 校验 PNG 魔数（`89 50 4E 47`）+ 最小长度 |

顺带做了两件"接上链路"的事：

- `ShellExecutor` 新增 `Mode.ADB`，`AUTO` 顺序改为 **shizuku → adb(自建) → root → app**，
  `ShellPlugin` 的 mode 枚举也加了 `adb` —— 也就是说 **AI 现在能用到自建通道了**，不用等 Shizuku；
- `adb_elevate.bat` 增加第 7 步 `adb tcpip 5555`，非 root 用户不用手敲。

### 本轮能力自检结果（模拟器实跑，五项全过）

```
cap connect:        connected=true uid=uid=0(root)
cap settings put:   exit=0
cap settings get:   exit=0 value=42        ← 写入后读回，证明真改了
cap settings delete: exit=0
cap screencap:      ok 427447 bytes        ← PNG 魔数校验通过
cap input tap:      exit=0
```

## 10. 真机操作清单（未 root，请按顺序）

1. 手机开「开发者选项 → USB 调试」，连电脑，手机上点允许；
2. 双击 `tools/adb_elevate.bat`（会顺带 `adb tcpip 5555`）；
3. 打开应用 → 设置 → 无障碍 → 「自建 ADB 通道」→ **连接并测试**；
4. **手机会弹「允许 USB 调试吗？」→ 点允许**（这一步只有非 root 才有；60 秒内点完）；
5. 状态变成 `已连接 · uid=2000(shell)` 即成功（**这里应该是 shell，不是 root**）；
6. 想验证能力：`adb logcat -s AdbSelfTest`，或用应用的连接测试看五项。

### 已知限制（真机上会遇到的）

- **重启后失效**：`adb tcpip` 不持久，重启要重跑脚本（P1 配对完成前无解）；
- **不要清应用数据**：会重新生成密钥，需要重新授权一次；
- 部分 ROM 关闭「USB 调试(安全设置)」后会拒绝 `input` 注入，属系统限制；
- 若弹窗一直不出现：确认手机已解锁、USB 模式不是"仅充电"。
