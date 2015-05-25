/*
 * Copyright (c) 2015 Twitter, Inc. All rights reserved.
 * Licensed under the Apache License v2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.twitter.whiskey.nio;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.ServerSocket;
import java.security.GeneralSecurityException;

/**
 * @author Bill Gallagher
 */
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


