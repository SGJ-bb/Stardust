import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

/**
 * 透明 TCP 代理，把前 N 字节按十六进制落盘，用来抓官方 adb 的 ClientHello。
 * java Proxy <listenPort> <targetHost> <targetPort> <outPrefix>
 */
public class Proxy {
    public static void main(String[] a) throws Exception {
        int lport = Integer.parseInt(a[0]);
        String thost = a[1];
        int tport = Integer.parseInt(a[2]);
        String prefix = a[3];
        ServerSocket ss = new ServerSocket(lport, 5, InetAddress.getByName("127.0.0.1"));
        System.out.println("listening " + lport + " -> " + thost + ":" + tport);
        while (true) {
            Socket c = ss.accept();
            Socket t = new Socket();
            t.connect(new InetSocketAddress(thost, tport), 3000);
            pump(c, t, prefix + "-c2s.txt");
            pump(t, c, prefix + "-s2c.txt");
        }
    }

    static void pump(Socket from, Socket to, String out) {
        new Thread(() -> {
            byte[] buf = new byte[8192];
            ByteArrayOutputStream first = new ByteArrayOutputStream();
            try {
                InputStream in = from.getInputStream();
                OutputStream os = to.getOutputStream();
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (first.size() < 4000) first.write(buf, 0, Math.min(n, 4000 - first.size()));
                    os.write(buf, 0, n);
                    os.flush();
                }
            } catch (Exception ignored) {
            } finally {
                try {
                    StringBuilder sb = new StringBuilder();
                    byte[] b = first.toByteArray();
                    for (int i = 0; i < b.length; i++) {
                        sb.append(String.format("%02x", b[i]));
                        if (i % 32 == 31) sb.append('\n');
                    }
                    Files.write(Paths.get(out), sb.toString().getBytes());
                    try { from.close(); } catch (Exception ignored2) {}
                    try { to.close(); } catch (Exception ignored2) {}
                } catch (Exception ignored3) {}
            }
        }).start();
    }
}
