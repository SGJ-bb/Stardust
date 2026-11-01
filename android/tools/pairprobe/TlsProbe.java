import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.tls.*;
import org.bouncycastle.tls.crypto.TlsCryptoParameters;
import org.bouncycastle.tls.crypto.impl.bc.BcDefaultTlsCredentialedSigner;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto;

import java.io.*;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.Hashtable;
import java.util.Vector;

/**
 * 探测设备上的 adb-TLS 端口到底要求什么：
 *   java TlsProbe <host> <port> <privateKeyPem> [alpn1,alpn2]
 *
 * 用来区分三种失败原因：不是 TLS 端口 / 证书被拒 / ALPN 不匹配（handshake_failure）。
 */
public class TlsProbe {

    static BcTlsCrypto crypto = new BcTlsCrypto(new SecureRandom());
    static boolean noCert = false;
    static boolean pkcs1 = false;
    static boolean allowTls12 = false;
    static PrivateKey priv;
    static byte[] certDer;

    public static void main(String[] a) throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        String host = a[0];
        int port = Integer.parseInt(a[1]);
        String keyPath = a.length > 2 ? a[2] : null;
        String[] alpns = a.length > 3 ? a[3].split(",") : null;
        for (String opt : a) {
            if (opt.equals("nocert")) noCert = true;
            if (opt.equals("pkcs1")) pkcs1 = true;
            if (opt.equals("tls12")) allowTls12 = true;
            if (opt.equals("stls")) stls = true;
            if (opt.equals("stls0")) { stls = true; stls0 = true; }
        }

        KeyPair kp;
        if (keyPath != null) {
            byte[] pkcs8 = readPem(keyPath);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            priv = kf.generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
            java.security.PublicKey pubKey = KeyFactory.getInstance("RSA")
                .generatePublic(new java.security.spec.RSAPublicKeySpec(
                    ((java.security.interfaces.RSAPrivateCrtKey) priv).getModulus(),
                    ((java.security.interfaces.RSAPrivateCrtKey) priv).getPublicExponent()));
            certDer = selfSigned("CN=aicompanion-adb", priv, pubKey);
        } else {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            kp = kpg.generateKeyPair();
            priv = kp.getPrivate();
            certDer = selfSigned("CN=probe", priv, kp.getPublic());
        }

        System.out.println("ALPN=" + (alpns == null ? "(none)" : String.join(",", alpns)));
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), 3000);
        s.setSoTimeout(3000);
        if (stls) {
            // adb 无线调试口的正确开场：明文 CNXN → 读 24 字节包头看是不是 A_STLS
            java.io.OutputStream o = s.getOutputStream();
            java.io.DataInputStream di0 = new java.io.DataInputStream(s.getInputStream());
            if (stls0) {
                s.setSoTimeout(600);
                try {
                    byte[] h0 = new byte[24];
                    di0.readFully(h0);
                    int c0 = le32b(h0, 0);
                    int l0 = le32b(h0, 12);
                    if (l0 > 0 && l0 < 65536) { byte[] b0 = new byte[l0]; di0.readFully(b0); }
                    System.out.println("  · 不发 CNXN，服务端主动发了 cmd=0x" + Integer.toHexString(c0));
                } catch (Exception e) {
                    System.out.println("  · 不发 CNXN 时服务端无主动消息（" + e.getClass().getSimpleName() + "）");
                }
                s.setSoTimeout(4000);
            }
            byte[] banner = ("host::features=shell_v2,cmd,stat_v2,ls_v2,fixed_push_mkdir,apex,abb,"
                + "fixed_push_symlink_timestamp,abb_exec,remount_shell,track_app,"
                + "sendrecv_v2,sendrecv_v2_brotli,sendrecv_v2_lz4,sendrecv_v2_zstd,"
                + "open_screen_mdns,devicetracker_proto_format,devraw,app_info,server_status,track_mdns\u0000")
                .getBytes("UTF-8");
            o.write(le32(0x4e584e43));
            o.write(le32(0x01000000));
            o.write(le32(4096));
            o.write(le32(banner.length));
            o.write(le32(checksum(banner)));
            o.write(le32(0x4e584e43 ^ -1));
            o.write(banner);
            java.io.DataInputStream di = new java.io.DataInputStream(s.getInputStream());
            byte[] hdr = new byte[24];
            di.readFully(hdr);
            int cmd = le32b(hdr, 0);
            int len = le32b(hdr, 12);
            if (len > 0 && len < 65536) { byte[] body = new byte[len]; di.readFully(body); }
            System.out.println("  · 明文首包 cmd=0x" + Integer.toHexString(cmd)
                + (cmd == 0x534c5453 ? " (A_STLS 要 TLS —— 正确)" : " (不是 A_STLS)"));
            // 把服务端在 TLS 之前可能还挂着的消息排空（否则会被当成 TLS 记录）
            s.setSoTimeout(200);
            for (int k = 0; k < 4; k++) {
                try {
                    byte[] h2 = new byte[24];
                    di.readFully(h2);
                    int c2 = le32b(h2, 0);
                    int l2 = le32b(h2, 12);
                    if (l2 > 0 && l2 < 65536) { byte[] b2 = new byte[l2]; di.readFully(b2); }
                    System.out.println("  · 额外消息 cmd=0x" + Integer.toHexString(c2));
                } catch (Exception e) { break; }
            }
            s.setSoTimeout(4000);
        }
        try {
            TlsClientProtocol p = new TlsClientProtocol(s.getInputStream(), s.getOutputStream());
            ProbeClient client = new ProbeClient(alpns);
            p.connect(client);
            System.out.println("  ✅ 握手成功 version=" + client.negotiatedVersion
                + " alpn=" + client.negotiatedAlpn);
            try {
                byte[] ekm = client.ekm;
                System.out.println("  EKM=" + (ekm == null ? "n/a" : ekm.length + "B"));
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            System.out.println("  ❌ " + t);
        } finally {
            try { s.close(); } catch (Exception ignored) {}
        }
        System.exit(0);
    }

    static boolean stls = false;
    static boolean stls0 = false;

    static byte[] le32(int v) {
        return new byte[]{(byte) (v & 0xFF), (byte) ((v >> 8) & 0xFF), (byte) ((v >> 16) & 0xFF), (byte) ((v >> 24) & 0xFF)};
    }

    static int le32b(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8) | ((b[off + 2] & 0xFF) << 16) | ((b[off + 3] & 0xFF) << 24);
    }

    static int checksum(byte[] d) {
        int s = 0;
        for (byte x : d) s += x & 0xFF;
        return s;
    }

    static byte[] readPem(String path) throws Exception {
        String text = new String(Files.readAllBytes(Paths.get(path)));
        String b64 = text.replaceAll("-----[^-]+-----", "").replaceAll("\\s", "");
        return Base64.getDecoder().decode(b64);
    }

    static byte[] selfSigned(String cn, PrivateKey key, PublicKey pub) throws Exception {
        long now = System.currentTimeMillis();
        X500Name name = new X500Name(cn);
        JcaX509v3CertificateBuilder b = new JcaX509v3CertificateBuilder(
            name, BigInteger.valueOf(now), new Date(now - 86400000L),
            new Date(now + 3650L * 86400000L), name, pub);
        return b.build(new JcaContentSignerBuilder("SHA256withRSA")
            .setProvider("BC").build(key)).getEncoded();
    }

    static class ProbeClient extends DefaultTlsClient {
        final String[] alpns;
        String negotiatedVersion;
        String negotiatedAlpn;
        byte[] ekm;

        ProbeClient(String[] alpns) {
            super(crypto);
            this.alpns = alpns;
        }

        @Override
        protected Vector getSupportedSignatureAlgorithms() {
            Vector v = super.getSupportedSignatureAlgorithms();
            if (pkcs1 && v != null) {
                v = new Vector(v);
                for (SignatureAndHashAlgorithm a : new SignatureAndHashAlgorithm[]{
                    SignatureAndHashAlgorithm.getInstance(HashAlgorithm.sha256, SignatureAlgorithm.rsa),
                    SignatureAndHashAlgorithm.getInstance(HashAlgorithm.sha384, SignatureAlgorithm.rsa),
                    SignatureAndHashAlgorithm.getInstance(HashAlgorithm.sha512, SignatureAlgorithm.rsa),
                }) {
                    if (!v.contains(a)) v.add(a);
                }
                System.out.println("  · sigalgs(含PKCS1)=" + v.size());
            }
            return v;
        }

        @Override
        protected Vector getSupportedSignatureAlgorithmsCert() {
            return getSupportedSignatureAlgorithms();
        }

        @Override
        public ProtocolVersion[] getSupportedVersions() {
            if (allowTls12) return new ProtocolVersion[]{ProtocolVersion.TLSv13, ProtocolVersion.TLSv12};
            return new ProtocolVersion[]{ProtocolVersion.TLSv13};
        }

        @Override
        public Hashtable getClientExtensions() throws IOException {
            Hashtable ext = super.getClientExtensions();
            if (alpns != null && alpns.length > 0) {
                if (ext == null) ext = new Hashtable();
                // ALPN: 2字节列表长度 + 每项(1字节长度+名字)
                int total = 0;
                for (String n : alpns) total += 1 + n.length();
                byte[] v = new byte[2 + total];
                v[0] = (byte) ((total >> 8) & 0xFF);
                v[1] = (byte) (total & 0xFF);
                int off = 2;
                for (String n : alpns) {
                    v[off++] = (byte) n.length();
                    for (byte c : n.getBytes("UTF-8")) v[off++] = c;
                }
                ext.put(ExtensionType.application_layer_protocol_negotiation, v);
            }
            return ext;
        }

        @Override
        public void notifyServerVersion(ProtocolVersion v) throws IOException {
            negotiatedVersion = String.valueOf(v);
            System.out.println("  · ServerHello version=" + v);
        }

        @Override
        public void notifySelectedCipherSuite(int s) {}

        @Override
        public void processServerExtensions(Hashtable exts) {
            System.out.println("  · server extensions=" + (exts == null ? "null" : exts.keySet()));
            if (exts != null && exts.containsKey(ExtensionType.application_layer_protocol_negotiation)) {
                byte[] v = (byte[]) exts.get(ExtensionType.application_layer_protocol_negotiation);
                if (v != null && v.length >= 3) {
                    int len = v[0] & 0xFF;
                    negotiatedAlpn = new String(v, 1, len);
                }
            }
        }

        @Override
        public void notifyHandshakeComplete() throws IOException {
            try {
                ekm = context.exportKeyingMaterial("adb-label\u0000", new byte[0], 64);
            } catch (Throwable t) {
                ekm = null;
            }
        }

        @Override
        public TlsAuthentication getAuthentication() {
            return new TlsAuthentication() {
                public void notifyServerCertificate(TlsServerCertificate sc) {}

                public TlsCredentials getClientCredentials(CertificateRequest cr) {
                    System.out.println("  · 服务端请求客户端证书 sigAlgs=" + cr.getSupportedSignatureAlgorithms());
                    if (noCert) { System.out.println("  · [nocert] 不提供客户端证书"); return null; }
                    org.bouncycastle.crypto.params.AsymmetricKeyParameter kp;
                    try {
                        kp = PrivateKeyFactory.createKey(priv.getEncoded());
                    } catch (IOException e) {
                        return null;
                    }
                    try {
                        return new BcDefaultTlsCredentialedSigner(
                            new TlsCryptoParameters(context), crypto, kp,
                            new org.bouncycastle.tls.Certificate(ByteArray(0),
                                new CertificateEntry[]{new CertificateEntry(
                                    crypto.createCertificate(certDer), null)}),
                            SignatureAndHashAlgorithm.rsa_pss_rsae_sha256);
                    } catch (IOException e) {
                        return null;
                    }
                }
            };
        }

        private byte[] ByteArray(int n) { return new byte[n]; }
    }
}
