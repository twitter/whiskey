package com.twitter.internal.network.whiskey;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.nio.ByteBuffer;
import java.security.cert.X509Certificate;

public final class SSLSocketTest {

    public static void main(String[] args) throws Exception {

        RunLoop runLoop = new RunLoop();
        runLoop.startThread();

        // TODO(bgallagher)
        TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return null; }
                public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                public void checkServerTrusted(X509Certificate[] certs, String authType) { }
            }
        };

        SSLContext sslCtx = SSLContext.getInstance("TLS");
        sslCtx.init(null, trustAllCerts, null);

        SSLEngine sslEngine = sslCtx.createSSLEngine();

        Origin origin = new Origin("https", "127.0.0.1", 4433);
        Socket s = new SSLSocket(origin, runLoop, sslEngine);
        Socket.ConnectFuture connectFuture = s.connect();

        connectFuture.get();

        byte[] bytesSent = "abcdefghijklmnopqrstuvwxyz".getBytes("US-ASCII");
        ByteBuffer message = ByteBuffer.wrap(bytesSent);
        Socket.WriteFuture writeFuture = s.write(message);

        writeFuture.get();

        Socket.ReadFuture readFuture = s.read();
        ByteBuffer readBB = readFuture.get();

        byte[] b = new byte[readBB.remaining()];
        readBB.get(b);

        String response = new String(b, "US-ASCII");
    }
}
