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
 * 用 JSSE 做 adb-TLS 客户端（安卓上 JSSE = Conscrypt = BoringSSL，与官方 adb 同源）。
 * 需要：客户端证书（可选，服务端要求时提供）+ 信任任意服务端证书。
 *
 * java JsseProbe <host> <port> <privateKeyPem> [alpn]
 */
public class JsseProbe {

    static PrivateKey priv;
    static X509Certificate cert;

    public static void main(String[] a) throws Exception {
        String host = a[0];
        int port = Integer.parseInt(a[1]);
        String keyPath = a[2];
        String alpn = a.length > 3 ? a[3] : null;

        byte[] pkcs8 = readPem(keyPath);
        priv = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        // 自签证书：用 JSSE 侧生成（只需公钥与私钥匹配）
        cert = (X509Certificate) org.bouncycastle.cert.jcajce.JcaX509CertificateConverter.class
            .getMethod("getCertificate", org.bouncycastle.cert.X509CertificateHolder.class)
            .invoke(org.bouncycastle.cert.jcajce.JcaX509CertificateConverter.class
                .getDeclaredConstructor().newInstance(),
                buildCert());
        ks.setKeyEntry("client", priv, "x".toCharArray(), new java.security.cert.Certificate[]{cert});
        kmf.init(ks, "x".toCharArray());

        SSLContext ctx = SSLContext.getInstance("TLSv1.3");
        ctx.init(kmf.getKeyManagers(), new TrustManager[]{new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] c, String t) {}
            public void checkServerTrusted(X509Certificate[] c, String t) {}
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        }}, new SecureRandom());

        Socket raw = new Socket();
        raw.connect(new InetSocketAddress(host, port), 3000);
        raw.setSoTimeout(4000);
        SSLSocket ssl = (SSLSocket) ctx.getSocketFactory().createSocket(raw, host, port, true);
        if (alpn != null) {
            SSLParameters p = ssl.getSSLParameters();
            p.setApplicationProtocols(new String[]{alpn});
            ssl.setSSLParameters(p);
        }
        try {
            ssl.startHandshake();
            System.out.println("✅ 握手成功 protocol=" + ssl.getSession().getProtocol()
                + " cipher=" + ssl.getSession().getCipherSuite()
                + " alpn=" + (ssl.getApplicationProtocol() == null ? "-" : ssl.getApplicationProtocol()));
        } catch (Throwable t) {
            System.out.println("❌ " + t);
        }
        ssl.close();
        System.exit(0);
    }

    static byte[] readPem(String path) throws Exception {
        String text = new String(Files.readAllBytes(Paths.get(path)));
        String b64 = text.replaceAll("-----[^-]+-----", "").replaceAll("\\s", "");
        return Base64.getDecoder().decode(b64);
    }

    /** 用 BC 造一张自签证书，再转成 JCA 证书对象 */
    static Object buildCert() throws Exception {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        java.security.PublicKey pub = KeyFactory.getInstance("RSA").generatePublic(
            new java.security.spec.RSAPublicKeySpec(
                ((java.security.interfaces.RSAPrivateCrtKey) priv).getModulus(),
                ((java.security.interfaces.RSAPrivateCrtKey) priv).getPublicExponent()));
        org.bouncycastle.asn1.x500.X500Name name =
            new org.bouncycastle.asn1.x500.X500Name("CN=aicompanion-adb");
        long now = System.currentTimeMillis();
        org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder b =
            new org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(
                name, java.math.BigInteger.valueOf(now),
                new java.util.Date(now - 86400000L),
                new java.util.Date(now + 3650L * 86400000L), name, pub);
        return b.build(new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256withRSA")
            .setProvider("BC").build(priv));
    }
}
