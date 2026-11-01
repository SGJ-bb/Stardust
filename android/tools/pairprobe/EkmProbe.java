import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.tls.*;
import org.bouncycastle.tls.crypto.TlsCryptoParameters;
import org.bouncycastle.tls.crypto.impl.bc.BcDefaultTlsCredentialedSigner;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Date;

/**
 * 目的：在电脑上复现「TLS 1.3 握手成功后却取不到 ExportKeyingMaterial」的问题，
 * 打印真实异常信息，避免每改一版都要真机重装 + 让用户重新开配对对话框给码。
 *
 * 用法：java -cp "bc*.jar;." EkmProbe
 */
public class EkmProbe {

    static byte[] certDer;
    static AsymmetricKeyParameter privKey;
    static BcTlsCrypto crypto = new BcTlsCrypto(new SecureRandom());

    /** 自签证书（桌面 JVM 上 BC provider 正常，可直接生成） */
    static byte[] selfSigned(String cn, KeyPair kp) throws Exception {
        long now = System.currentTimeMillis();
        X500Name name = new X500Name(cn);
        JcaX509v3CertificateBuilder b = new JcaX509v3CertificateBuilder(
                name, BigInteger.valueOf(now), new Date(now - 86400000L),
                new Date(now + 3650L * 86400000L), name, kp.getPublic());
        return b.build(new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider("BC").build(kp.getPrivate())).getEncoded();
    }

    static AsymmetricKeyParameter keyParam(PrivateKey pk) throws Exception {
        return PrivateKeyFactory.createKey(pk.getEncoded());
    }

    static Certificate chain(byte[] der) throws Exception {
        return new Certificate(ByteArray(0), new CertificateEntry[]{new CertificateEntry(crypto.createCertificate(der), null)});
    }

    static byte[] ByteArray(int n) { return new byte[n]; }

    // ---------------------------------------------------------------- 服务端
    static class ProbeServer extends DefaultTlsServer {
        final AsymmetricKeyParameter key;
        final Certificate chain;

        ProbeServer(AsymmetricKeyParameter key, Certificate chain) {
            super(crypto);
            this.key = key;
            this.chain = chain;
        }

        @Override
        public ProtocolVersion[] getSupportedVersions() {
            return new ProtocolVersion[]{ProtocolVersion.TLSv13};
        }

        @Override
        public TlsCredentials getCredentials() throws IOException {
            return new BcDefaultTlsCredentialedSigner(
                    new TlsCryptoParameters(context), crypto, key, chain,
                    SignatureAndHashAlgorithm.rsa_pss_rsae_sha256);
        }
    }

    // ---------------------------------------------------------------- 客户端
    static class ProbeClient extends DefaultTlsClient {
        final AsymmetricKeyParameter key;
        final Certificate chain;

        ProbeClient(AsymmetricKeyParameter key, Certificate chain) {
            super(crypto);
            this.key = key;
            this.chain = chain;
        }

        @Override
        public ProtocolVersion[] getSupportedVersions() {
            return new ProtocolVersion[]{ProtocolVersion.TLSv13};
        }

        byte[] ekmInCallback = null;

        @Override
        public void notifyHandshakeComplete() throws IOException {
            try {
                ekmInCallback = context.exportKeyingMaterial("adb-label\u0000", new byte[0], 64);
                System.out.println("[client] EKM inside notifyHandshakeComplete OK len="
                        + (ekmInCallback == null ? -1 : ekmInCallback.length)
                        + " first8=" + hex(ekmInCallback, 8));
            } catch (Throwable t) {
                System.out.println("[client] EKM inside callback FAILED: " + t);
            }
        }

        @Override
        public TlsAuthentication getAuthentication() {
            return new TlsAuthentication() {
                public void notifyServerCertificate(TlsServerCertificate sc) { }

                public TlsCredentials getClientCredentials(CertificateRequest cr) {
                    return new BcDefaultTlsCredentialedSigner(
                            new TlsCryptoParameters(context), crypto, key, chain,
                            SignatureAndHashAlgorithm.rsa_pss_rsae_sha256);
                }
            };
        }
    }

    static class ExposedClient extends TlsClientProtocol {
        ExposedClient(InputStream in, OutputStream out) { super(in, out); }

        TlsContext ctx() { return getContext(); }
    }

    public static void main(String[] args) throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair skp = kpg.generateKeyPair();
        KeyPair ckp = kpg.generateKeyPair();
        byte[] sCert = selfSigned("CN=probe-server", skp);
        certDer = selfSigned("CN=probe-client", ckp);
        privKey = keyParam(ckp.getPrivate());

        ServerSocket ss = new ServerSocket();
        ss.bind(new InetSocketAddress("127.0.0.1", 0));
        int port = ss.getLocalPort();
        System.out.println("[server] listening 127.0.0.1:" + port);

        Thread st = new Thread(() -> {
            try {
                Socket s = ss.accept();
                TlsServerProtocol sp = new TlsServerProtocol(s.getInputStream(), s.getOutputStream());
                sp.accept(new ProbeServer(keyParam(skp.getPrivate()), chain(sCert)));
                System.out.println("[server] handshake done, server-side EKM:");
                tryEkm(sp, "server");
                Thread.sleep(3000);
                sp.close();
            } catch (Exception e) {
                System.out.println("[server] ERROR " + e);
                e.printStackTrace();
            }
        });
        st.setDaemon(true);
        st.start();

        Socket sock = new Socket("127.0.0.1", port);
        sock.setSoTimeout(10000);
        ExposedClient cp = new ExposedClient(sock.getInputStream(), sock.getOutputStream());
        cp.connect(new ProbeClient(privKey, chain(certDer)));
        System.out.println("[client] handshake done");

        // 诊断：连接态参数是否就绪
        try {
            Method m = cp.ctx().getClass().getMethod("getSecurityParametersConnection");
            Object sp = m.invoke(cp.ctx());
            System.out.println("[client] getSecurityParametersConnection() = " + sp);
            if (sp != null) {
                Object ems = sp.getClass().getMethod("isExtendedMasterSecret").invoke(sp);
                Object ver = sp.getClass().getMethod("getNegotiatedVersion").invoke(sp);
                Object ms = sp.getClass().getMethod("getMasterSecret").invoke(sp);
                Object ex = sp.getClass().getMethod("getExporterMasterSecret").invoke(sp);
                System.out.println("[client]   EMS=" + ems + " version=" + ver
                        + " masterSecret=" + ms + " exporterMasterSecret=" + ex);
            }
        } catch (Exception e) {
            System.out.println("[client] introspect failed: " + e);
        }

        tryEkm(cp, "client");

        Thread.sleep(500);
        System.exit(0);
    }

    static void tryEkm(TlsProtocol p, String who) {
        try {
            Method g = TlsProtocol.class.getDeclaredMethod("getContext");
            g.setAccessible(true);
            TlsContext ctx = (TlsContext) g.invoke(p);
            System.out.println("[" + who + "] context=" + (ctx == null ? "null" : ctx.getClass().getName()));
            if (ctx == null) return;
            byte[] ekm = ctx.exportKeyingMaterial("adb-label\u0000", new byte[0], 64);
            System.out.println("[" + who + "] EKM OK len=" + (ekm == null ? -1 : ekm.length)
                    + (ekm != null && ekm.length >= 8 ? " first8=" + hex(ekm, 8) : ""));
        } catch (Throwable t) {
            System.out.println("[" + who + "] EKM FAILED: " + t);
            t.printStackTrace(System.out);
        }
    }

    static String hex(byte[] b, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(n, b.length); i++) sb.append(String.format("%02x", b[i]));
        return sb.toString();
    }
}
