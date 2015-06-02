/*
 * Copyright (c) 2015 Twitter, Inc. All rights reserved.
 * Licensed under the Apache License v2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.twitter.whiskey.demo;

import com.twitter.whiskey.net.Header;
import com.twitter.whiskey.net.Headers;
import com.twitter.whiskey.net.Request;
import com.twitter.whiskey.net.ResponseFuture;
import com.twitter.whiskey.net.WhiskeyClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.SocketPermission;
import java.net.URL;
import java.nio.ByteBuffer;
import java.security.Permission;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocketFactory;

/**
 * Implementation of Http(s)UrlConnection using Whiskey.
 *
 * Please note that using WhiskeyClient directly to issue requests affords
 * the user a significantly more flexible API than this legacy interface
 * supports. This implementation is provided as a demonstration of the
 * libary's ability to support a wide variety of interfaces.
 *
 * @author Michael Schore
 */
public class HttpUrlConnectionImpl extends HttpURLConnection {

    private final AtomicBoolean submitted = new AtomicBoolean(false);
    private final WhiskeyClient client;
    private final Request.Builder requestBuilder;
    private Request request;
    private ResponseFuture responseFuture;

    /**
     * Constructor for the HttpURLConnection.
     *
     * @param url the URL
     */
    public HttpUrlConnectionImpl(URL url, WhiskeyClient client) {
        super(url);
        requestBuilder = new Request.Builder(url);
        if (!getFollowRedirects()) requestBuilder.maxRedirects(0);
        this.client = client;
    }

    @Override
    public void disconnect() {
    }

    @Override
    public void setConnectTimeout(int timeout) {
    }

    @Override
    public int getConnectTimeout() {
        return -1;
    }

    @Override
    public void setReadTimeout(int timeout) {
    }

    @Override
    public int getReadTimeout() {
        return -1;
    }

    @Override
    public void setFixedLengthStreamingMode (int contentLength) {
        requestBuilder.replaceHeader("content-length", String.valueOf(contentLength));
    }

    public void setFixedLengthStreamingMode(long contentLength) {
        requestBuilder.replaceHeader("content-length", String.valueOf(contentLength));
    }

    @Override
    public void setChunkedStreamingMode(int chunklen) {
        requestBuilder.removeHeader("content-length");
    }

    @Override
    public void setInstanceFollowRedirects(boolean followRedirects) {

        super.setInstanceFollowRedirects(followRedirects);
        requestBuilder.maxRedirects(followRedirects ? 20 : 0);
    }

    @Override
    public void setRequestMethod(String method) throws ProtocolException {

        super.setRequestMethod(method);
        try {
            requestBuilder.method(Request.Method.fromString(method));
        } catch (IllegalArgumentException e) {
            throw new ProtocolException("Invalid HTTP method: " + method);
        }
    }

    @Override
    public void setRequestProperty(String key, String value) {

        if (value == null) {
            requestBuilder.removeHeader(key);
            return;
        }
        requestBuilder.replaceHeader(key, value);
    }

    @Override
    public void addRequestProperty(String key, String value) {
        requestBuilder.addHeader(key, value);
    }

    @Override
    public void setIfModifiedSince(long ifmodifiedsince) {

        requestBuilder.removeHeader("if-modified-since");
        requestBuilder.addHeader(new Header("if-modified-since", new Date(ifmodifiedsince)));
    }

    @Override
    public OutputStream getOutputStream() throws IOException {

        PipedOutputStream out = new PipedOutputStream();
        PipedInputStream in = new PipedInputStream();
        in.connect(out);
        requestBuilder.body(in);
        return out;
    }

    @Override
    public Map<String, List<String>> getRequestProperties() {
        return request.getHeaders().map();
    }

    @Override
    public String getRequestProperty(String key) {
        return request.getHeaders().getFirst(key);
    }

    @Override
    public Permission getPermission() throws IOException {
        return new SocketPermission(url.getHost() + ":" + url.getPort(), "connect, resolve");
    }

    @Override
    public boolean usingProxy() {
        return false;
    }

    @Override
    public void connect() throws IOException {

        getResponseFuture();
        connected = true;
    }

    public ResponseFuture getResponseFuture() {

        if (submitted.compareAndSet(false, true)) {
            request = requestBuilder.create();
            responseFuture = client.submit(request);
        }
        return responseFuture;
    }

    @Override
    public InputStream getErrorStream() {
        return null;
    }

    @Override
    public int getResponseCode() throws IOException {
        try {
            responseCode = getResponseFuture().get().getStatusCode();
            return responseCode;
        } catch (InterruptedException | ExecutionException e) {
            throw new IOException(e);
        }
    }

    public Header getHeader(int n) {

        try {
            int i = 0;
            for (Header header : getResponseFuture().getHeadersFuture().get().entries()) {
                if (i++ == n) return header;
            }
        } catch (InterruptedException | ExecutionException ignored) {
        }

        return null;
    }

    @Override
    public String getHeaderField(String name) {

        try {
            return getResponseFuture().getHeadersFuture().get().getFirst(name);
        } catch (InterruptedException | ExecutionException e) {
            return null;
        }
    }

    @Override
    public String getHeaderFieldKey(int n) {

        Header header = getHeader(n);
        return header == null ? null : header.getKey();
    }

    @Override
    public String getHeaderField(int n) {

        Header header = getHeader(n);
        return header == null ? null : header.getValue();
    }

    @Override
    public Map<String, List<String>> getHeaderFields() {

        final Headers headers = getHeaders();
        return headers == null ? Collections.<String, List<String>>emptyMap() : headers.map();
    }

    public Headers getHeaders() {

        try {
            return getResponseFuture().getHeadersFuture().get();
        } catch (InterruptedException | ExecutionException e) {
            return null;
        }
    }

    @Override
    public InputStream getInputStream() throws IOException {

        if (!doInput) throw new UnsupportedOperationException();

        return new InputStream() {
            Iterator<ByteBuffer> iterator = getResponseFuture().getBodyFuture().iterator();
            private ByteBuffer currentBuffer;
            @Override
            public int read() throws IOException {
                try {
                    if (currentBuffer != null && currentBuffer.remaining() > 0) {
                        return currentBuffer.get();
                    }

                    while (iterator.hasNext()) {
                        currentBuffer = iterator.next();
                        if (currentBuffer != null && currentBuffer.remaining() > 0) {
                            return currentBuffer.get();
                        }
                    }

                    return -1;
                } catch (RuntimeException e) {
                    throw new IOException(e);
                }
            }
        };
    }

    public ByteBuffer getBody() {

        try {
            return getResponseFuture().getBodyFuture().get();
        } catch (InterruptedException | ExecutionException e) {
            return null;
        }
    }


//    @Override
//    public String getCipherSuite() {
//        return null;
//    }
//
//    @Override
//    public Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
//        return null;
//    }
//
//    @Override
//    public Principal getLocalPrincipal() {
//        return null;
//    }
//
//    @Override
//    public void setHostnameVerifier(HostnameVerifier hostnameVerifier) {
//    }
//
//    @Override
//    public HostnameVerifier getHostnameVerifier() {
//        return null;
//    }
//
//    @Override
//    public void setSSLSocketFactory(SSLSocketFactory sslSocketFactory) {
//        throw new UnsupportedOperationException("use SSLContext via WhiskeyClient configuration to set TLS parameters");
//    }
//
//    @Override
//    public SSLSocketFactory getSSLSocketFactory() {
//        return null;
//    }
//
//    @Override
//    public Certificate[] getLocalCertificates() {
//        return new Certificate[0];
//    }
//
//    @Override
//    public Certificate[] getServerCertificates() throws SSLPeerUnverifiedException {
//        return new Certificate[0];
//    }
}