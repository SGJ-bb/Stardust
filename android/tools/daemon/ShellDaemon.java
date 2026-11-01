import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * 常驻命令服务（用 app_process 以 shell 身份运行，独立于 adb 连接）。
 *
 * 为什么需要它：无线调试的 adbd 通道会随 Wi-Fi 开关消失、端口每次重启都变。
 * 而一旦拿到一次 shell，就可以在这里常驻一个进程（uid 2000），
 * 之后所有命令走本地 socket —— 不依赖 adb、不依赖 Wi-Fi，直到下次重启。
 * 这就是 Shizuku 的做法，只是我们不需要装任何第三方应用。
 *
 * 启动方式（由应用侧执行）：
 *   CLASSPATH=/sdcard/Android/data/<pkg>/files/stradust_daemon.dex \
 *   app_process /system/bin --nice-name=stradust_shell ShellDaemon <port> <token>
 *
 * 协议（全部大端长度前缀）：
 *   客户端 → "AUTH <token>\n"
 *   服务端 → "OK\n" 或直接断开
 *   客户端 → [4B len][命令 UTF-8]
 *   服务端 → [4B len][响应 UTF-8]，响应格式："EXIT <code>\n<输出>"
 */
public class ShellDaemon {

    private static String token = "";
    private static volatile boolean running = true;

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("usage: ShellDaemon <port> <token>");
            return;
        }
        int port = Integer.parseInt(args[0]);
        token = args[1];

        ServerSocket server = null;
        try {
            server = new ServerSocket(port, 8, InetAddress.getByName("127.0.0.1"));
            System.out.println("listening " + port);
        } catch (Exception e) {
            System.err.println("bind failed: " + e);
            return;
        }

        // 父进程（adb shell）退出后别把我们一起带走
        try { Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            public void run() { running = false; }
        })); } catch (Throwable ignored) { }

        while (running) {
            Socket s = null;
            try {
                s = server.accept();
                handle(s);
            } catch (Exception e) {
                // 单连接异常不影响服务
            } finally {
                if (s != null) { try { s.close(); } catch (Exception ignored) {} }
            }
        }
    }

    private static void handle(Socket s) {
        try {
            s.setSoTimeout(15000);
            DataInputStream in = new DataInputStream(s.getInputStream());
            DataOutputStream out = new DataOutputStream(s.getOutputStream());

            String auth = readLine(in);
            if (auth == null || !auth.equals("AUTH " + token)) {
                return;   // 静默断开，不给探测者任何信息
            }
            out.write("OK\n".getBytes("UTF-8"));
            out.flush();

            s.setSoTimeout(0);
            while (true) {
                int len;
                try { len = in.readInt(); } catch (Exception eof) { return; }
                if (len <= 0 || len > 256 * 1024) return;
                byte[] buf = new byte[len];
                in.readFully(buf);
                String cmd = new String(buf, "UTF-8");

                String result = exec(cmd);
                byte[] resp = result.getBytes("UTF-8");
                out.writeInt(resp.length);
                out.write(resp);
                out.flush();
            }
        } catch (Exception ignored) {
        }
    }

    private static String readLine(DataInputStream in) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            int c;
            while ((c = in.read()) >= 0) {
                if (c == '\n') break;
                bos.write(c);
                if (bos.size() > 512) return null;
            }
            return new String(bos.toByteArray(), "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    /** 执行命令：/system/bin/sh -c，合并 stderr，带超时 */
    private static String exec(String cmd) {
        Process p = null;
        try {
            p = new ProcessBuilder("/system/bin/sh", "-c", cmd).redirectErrorStream(true).start();
            final Process fp = p;
            Thread killer = new Thread(new Runnable() {
                public void run() {
                    try { Thread.sleep(25000); } catch (InterruptedException ignored) {}
                    try { fp.destroy(); } catch (Exception ignored) {}
                }
            });
            killer.setDaemon(true);
            killer.start();

            InputStream is = p.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = is.read(b)) > 0) {
                bos.write(b, 0, n);
                if (bos.size() > 512 * 1024) break;    // 防止把内存打爆
            }
            int code = p.waitFor();
            killer.interrupt();
            String text = new String(bos.toByteArray(), "UTF-8");
            return "EXIT " + code + "\n" + text;
        } catch (Exception e) {
            return "EXIT -1\n" + e;
        } finally {
            if (p != null) { try { p.destroy(); } catch (Exception ignored) {} }
        }
    }
}
