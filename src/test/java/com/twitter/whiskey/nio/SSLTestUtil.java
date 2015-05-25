/*
 * Copyright (c) 2015 Twitter, Inc. All rights reserved.
 * Licensed under the Apache License v2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.twitter.whiskey.nio;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

/**
 * @author Bill Gallagher
 */
final class SSLTestUtil {

    private static final String KEY_STORE_PATH = "/certs/cert.ks";
    private static final String KEY_STORE_PASSWORD = "password";

    private static KeyStore loadKeyStore(String path, String password) throws IOException, GeneralSecurityException {
        KeyStore ks = KeyStore.getInstance("JKS");
        InputStream is = SSLTestUtil.class.getResourceAsStream(path);
        try {
            ks.load(is, password.toCharArray());
        } finally {
            is.close();
        }
        return ks;
    }

    private static KeyManager[] getKeyManagers(KeyStore ks, String password) throws GeneralSecurityException {
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, password.toCharArray());
        return kmf.getKeyManagers();
    }

    private static TrustManager[] getTrustManagers(KeyStore ks) throws GeneralSecurityException {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);
        return tmf.getTrustManagers();
    }

    private static SSLContext createSSLContext(String keyStorePath, String password)
        throws IOException, GeneralSecurityException
    {
        KeyStore ks = SSLTestUtil.loadKeyStore(keyStorePath, password);

        SSLContext sslCtx = SSLContext.getInstance("TLS");
        sslCtx.init(getKeyManagers(ks, "test"), getTrustManagers(ks), null);

        return sslCtx;
    }

    static SSLContext createTestSSLContext() throws IOException, GeneralSecurityException {
        return createSSLContext(KEY_STORE_PATH, KEY_STORE_PASSWORD);
    }
}
