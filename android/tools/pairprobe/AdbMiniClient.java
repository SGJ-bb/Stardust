import javax.net.ssl.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * 最小 adb 无线调试客户端（电脑侧，用于快速迭代协议细节）。
 *
 * 流程严格照 AOSP：
 *   1. 明文 CNXN
 *   2. 收到 A_STLS
 *   3. 回 A_STLS
 *   4. 在同一条 socket 上做 TLS（客户端证书 = adbkey）
 *   5. **等设备发 CNXN**（不要自己发）
 *   6. 发 OPEN
 *
 * 用法：java AdbMiniClient <host> <port> <privateKeyPem> [openArg1] [service]
 */
public class AdbMiniClient {

    static final int A_CNXN = 0x4e584e43, A_AUTH = 0x48545541, A_OPEN = 0x4e45504f,
        A_OKAY = 0x59414b4f, A_CLSE = 0x45534c43, A_WRTE = 0x45545257, A_STLS = 0x534c5453;
    static final int A_VERSION = 0x01000000, A_STLS_VERSION = 0x01000000, MAX_PAYLOAD = 4096;

    static PrivateKey priv;
    static byte[] certDer;

    public static void main(String[] a) throws Exception {
        String host = a[0];
        int port = Integer.parseInt(a[1]);
        priv = KeyFactory.getInstance("RSA")
            .generatePrivate(new PKCS8EncodedKeySpec(readPem(a[2])));
        int openArg1 = a.length > 3 ? Integer.parseInt(a[3]) : 0;
        String service = a.length > 4 ? a[4] : "shell:id";

        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        certDer = selfSigned();

        Socket raw = new Socket();
        raw.connect(new InetSocketAddress(host, port), 4000);
        raw.setSoTimeout(4000);
        DataInputStream in = new DataInputStream(raw.getInputStream());
        OutputStream out = raw.getOutputStream();

        send(out, A_CNXN, A_VERSION, MAX_PAYLOAD, "host::features=cmd,shell_v2,delayed_ack\u0000".getBytes("UTF-8"));
        Msg m = read(in);
        System.out.println("1) 收到 " + name(m.cmd) + " arg0=" + m.arg0);
        if (m.cmd != A_STLS) { System.out.println("期望 A_STLS，退出"); return; }

        send(out, A_STLS, A_STLS_VERSION, 0, new byte[0]);
        System.out.println("2) 已回 A_STLS，开始 TLS");

        // 就地 TLS
        SSLSocket ssl = tls(raw, host, port);
        if (ssl == null) { System.out.println("TLS 失败"); return; }
        System.out.println("3) TLS OK " + ssl.getSession().getProtocol());
        in = new DataInputStream(ssl.getInputStream());
        out = ssl.getOutputStream();

        // 等设备的 CNXN
        Msg cn = read(in);
        System.out.println("4) 收到 " + name(cn.cmd) + " arg0=" + cn.arg0 + " arg1=" + cn.arg1);
        if (cn.cmd == A_STLS) { cn = read(in); System.out.println("4b) 又收到 " + name(cn.cmd)); }

        // 发 OPEN
        send(out, A_OPEN, 1, openArg1, (service + "\u0000").getBytes("UTF-8"));
        System.out.println("5) 已发 OPEN(" + service + ", arg1=" + openArg1 + ")，等响应…");

        long deadline = System.currentTimeMillis() + 5000;
        StringBuilder sb = new StringBuilder();
        while (System.currentTimeMillis() < deadline) {
            try {
                Msg r = read(in);
                System.out.println("   <- " + name(r.cmd) + " arg0=" + r.arg0 + " arg1=" + r.arg1
                    + " len=" + r.data.length + (r.data.length > 0 && r.data.length < 200
                    ? "  [" + new String(r.data, "UTF-8").replace("\n", "\\n") + "]" : ""));
                if (r.cmd == A_WRTE) {
                    sb.append(new String(r.data, "UTF-8"));
                    send(out, A_OKAY, 1, r.arg0, new byte[0]);
                }
                if (r.cmd == A_CLSE) break;
            } catch (Exception e) {
                System.out.println("   读结束: " + e);
                break;
            }
        }
        System.out.println("=== 累计输出 ===\n" + sb);
        System.exit(0);
    }

    // ---------------------------------------------------------------- 工具

    static class Msg { int cmd, arg0, arg1; byte[] data; }

    static Msg read(DataInputStream in) throws IOException {
        byte[] h = new byte[24];
        in.readFully(h);
        Msg m = new Msg();
        m.cmd = le32(h, 0); m.arg0 = le32(h, 4); m.arg1 = le32(h, 8);
        int len = le32(h, 12);
        m.data = new byte[len];
        if (len > 0) in.readFully(m.data);
        return m;
    }

    static void send(OutputStream out, int cmd, int arg0, int arg1, byte[] data) throws IOException {
        byte[] h = new byte[24];
        put(h, 0, cmd); put(h, 4, arg0); put(h, 8, arg1); put(h, 12, data.length);
        int sum = 0; for (byte b : data) sum += b & 0xFF;
        put(h, 16, sum); put(h, 20, cmd ^ -1);
        out.write(h); out.write(data); out.flush();
    }

    static int le32(byte[] b, int o) {
        return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8) | ((b[o + 2] & 0xFF) << 16) | ((b[o + 3] & 0xFF) << 24);
    }

    static void put(byte[] b, int o, int v) {
        b[o] = (byte) v; b[o + 1] = (byte) (v >> 8); b[o + 2] = (byte) (v >> 16); b[o + 3] = (byte) (v >> 24);
    }

    static String name(int c) {
        switch (c) {
            case A_CNXN: return "CNXN"; case A_AUTH: return "AUTH"; case A_OPEN: return "OPEN";
            case A_OKAY: return "OKAY"; case A_CLSE: return "CLSE"; case A_WRTE: return "WRTE";
            case A_STLS: return "STLS"; default: return "0x" + Integer.toHexString(c);
        }
    }

    static byte[] readPem(String p) throws Exception {
        String t = new String(Files.readAllBytes(Paths.get(p)));
        return Base64.getDecoder().decode(t.replaceAll("-----[^-]+-----", "").replaceAll("\\s", ""));
    }

    static byte[] selfSigned() throws Exception {
        KeyFactory kf = KeyFactory.getInstance("RSA");
        java.security.PublicKey pub = kf.generatePublic(new java.security.spec.RSAPublicKeySpec(
            ((java.security.interfaces.RSAPrivateCrtKey) priv).getModulus(),
            ((java.security.interfaces.RSAPrivateCrtKey) priv).getPublicExponent()));
        org.bouncycastle.asn1.x500.X500Name n = new org.bouncycastle.asn1.x500.X500Name("CN=aicompanion-adb");
        long now = System.currentTimeMillis();
        return new org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(
            n, java.math.BigInteger.valueOf(now), new java.util.Date(now - 86400000L),
            new java.util.Date(now + 3650L * 86400000L), n, pub)
            .build(new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256withRSA")
                .setProvider("BC").build(priv)).getEncoded();
    }

    static SSLSocket tls(Socket raw, String host, int port) {
        try {
            X509Certificate cert = (X509Certificate) java.security.cert.CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(certDer));
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(null, null);
            ks.setKeyEntry("k", priv, "x".toCharArray(), new java.security.cert.Certificate[]{cert});
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, "x".toCharArray());
            SSLContext ctx = SSLContext.getInstance("TLSv1.3");
            ctx.init(kmf.getKeyManagers(), new TrustManager[]{new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] c, String t) {}
                public void checkServerTrusted(X509Certificate[] c, String t) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }}, new SecureRandom());
            SSLSocket ssl = (SSLSocket) ctx.getSocketFactory().createSocket(raw, host, port, true);
            ssl.setSoTimeout(4000);
            ssl.startHandshake();
            ssl.setSoTimeout(0);
            return ssl;
        } catch (Exception e) {
            System.out.println("TLS 异常: " + e);
            return null;
        }
    }
}
