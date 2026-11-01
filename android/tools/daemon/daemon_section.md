
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
