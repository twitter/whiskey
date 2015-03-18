package com.twitter.internal.network.whiskey;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.ServerSocket;
import java.security.GeneralSecurityException;

final class SSLEchoServer extends EchoServer {

    SSLEchoServer(int port) throws IOException {
        super(port);
    }

    @Override
    ServerSocket createServerSocket(int port) throws IOException, GeneralSecurityException {
        SSLContext sslCtx = SSLTestUtil.createTestSSLContext();
        ServerSocketFactory factory = sslCtx.getServerSocketFactory();
        return factory.createServerSocket(port);
    }

}


