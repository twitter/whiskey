package com.twitter.whiskey.nio;

import com.twitter.whiskey.util.Origin;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Random;
import java.util.concurrent.ExecutionException;

@RunWith(Parameterized.class)
public class SocketTest {

    private static final int TEST_PORT = 1234;

    private final boolean ssl;

    private RunLoop runLoop;
    private EchoServer echoServer;
    private Socket socket;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] { { false } , { true } });
    }
    public SocketTest(boolean ssl) {
        this.ssl = ssl;
    }

    @Before
    public void setUp() throws Exception {
        runLoop = new RunLoop();
        runLoop.startThread();

        echoServer = ssl ? new SSLEchoServer(TEST_PORT) : new EchoServer(TEST_PORT);
        echoServer.start();

        socket = ssl ? createSSLSocket() : createSocket();
    }

    @After
    public void tearDown() throws InterruptedException, IOException {
        echoServer.stop();

        runLoop.stopThread();
    }

    private Socket createSocket() {
        Origin origin = new Origin("http", "localhost", TEST_PORT);
        return new Socket(origin, runLoop);
    }

    private Socket createSSLSocket() throws IOException, GeneralSecurityException {
        SSLContext sslCtx = SSLTestUtil.createTestSSLContext();
        SSLEngine sslEngine = sslCtx.createSSLEngine();

        Origin origin = new Origin("https", "127.0.0.1", TEST_PORT);
        return new SSLSocket(origin, runLoop, sslEngine);
    }

    private void readFully(Socket socket, ByteBuffer buf) throws ExecutionException, InterruptedException {
        while (buf.hasRemaining()) {
            Socket.ReadFuture readFuture = socket.read();
            ByteBuffer r = readFuture.get();
            buf.put(r);
        }
        buf.flip();
    }

    private void expectRead(Socket socket, ByteBuffer expected) throws ExecutionException, InterruptedException {
        ByteBuffer actual = ByteBuffer.allocate(expected.remaining());
        readFully(socket, actual);
        Assert.assertTrue(expected.compareTo(actual) == 0);
    }

   @Test
    public void testEcho() throws Exception {

        echoServer.addTask(new EchoServer.EchoTask());

        Socket.ConnectFuture connectFuture = socket.connect();
        connectFuture.get();

        ByteBuffer expected = createTestMessage(100);
        Socket.WriteFuture writeFuture = socket.write(expected);
        writeFuture.get();

        expected.flip();

        expectRead(socket, expected);
    }

    @Test
    public void testConnectFail() throws Exception {
        echoServer.stop();

        Socket.ConnectFuture connectFuture = socket.connect();

        try {
            connectFuture.get();
            Assert.fail("connection should have failed");
        } catch (ExecutionException ee) {
            Assert.assertTrue(ee.getCause() instanceof ConnectException);
            ConnectException ce = (ConnectException)ee.getCause();
            Assert.assertEquals("Connection refused", ce.getMessage());
        }
    }

    @Test
    public void testWriteBeforeConnect() throws Exception {

        echoServer.addTask(new EchoServer.EchoTask());

        ByteBuffer expected = createTestMessage("abcdefghijklmnopqrstuvwxyz");
        Socket.WriteFuture writeFuture = socket.write(expected);

        Socket.ConnectFuture connectFuture = socket.connect();
        connectFuture.get();

        writeFuture.get();

        expected.flip();

        expectRead(socket, expected);
    }

    @Test
    public void testReadBeforeConnect() throws Exception {

        final byte[] msg = "testReadBeforeConnect".getBytes("US-ASCII");

        echoServer.addTask(new EchoServer.Task() {

            @Override
            public void execute(ServerSocket serverSocket, java.net.Socket socket) throws IOException {
                socket.getOutputStream().write(msg);
                socket.getOutputStream().flush();
            }
        });

        Socket.ReadFuture readFuture = socket.read();

        Socket.ConnectFuture connectFuture = socket.connect();
        connectFuture.get();

        ByteBuffer buf = readFuture.get();

        ByteBuffer expected = ByteBuffer.wrap(msg);
        Assert.assertTrue(expected.compareTo(buf) == 0);
    }

   @Test
    public void testLargeTransfer() throws Exception {

        echoServer.addTask(new EchoServer.EchoTask());

        int messageSize = 2 * 1024 * 1024;

        Socket.ConnectFuture connectFuture = socket.connect();
        connectFuture.get();

        ByteBuffer expected = createTestMessage(messageSize);
        Socket.WriteFuture writeFuture = socket.write(expected);

        ByteBuffer actual = ByteBuffer.allocate(messageSize);
        readFully(socket, actual);

        expected.flip();
        Assert.assertTrue(expected.compareTo(actual) == 0);

        writeFuture.get();
    }

    @Test
    public void testManySmallWrites() throws Exception {

        echoServer.addTask(new EchoServer.EchoTask());

        Socket.ConnectFuture connectFuture = socket.connect();
        connectFuture.get();

        for (int i = 0; i < 1000; i++) {
            ByteBuffer expected = createTestMessage(10);
            Socket.WriteFuture writeFuture = socket.write(expected);
            writeFuture.get();

            expected.flip();

            expectRead(socket, expected);
        }
    }

    @Test
    public void testClose() throws ExecutionException, InterruptedException {

        echoServer.addTask(new EchoServer.Task() {
            @Override
            public void execute(ServerSocket serverSocket, java.net.Socket socket) throws IOException {
                socket.close();
            }
        });

        try {
            Socket.ConnectFuture connectFuture = socket.connect();
            connectFuture.get();

            socket.read().get();
        } catch (ExecutionException ee) {
            Assert.assertTrue(ee.getCause() instanceof IOException);
            IOException ioe = (IOException)ee.getCause();
            Assert.assertEquals("connection closed", ioe.getMessage());
        }
    }

    private static ByteBuffer createTestMessage(int size) {
        byte[] randomData = new byte[size];
        Random random = new Random();
        random.nextBytes(randomData);
        return ByteBuffer.wrap(randomData);
    }

    private static ByteBuffer createTestMessage(String msg) throws UnsupportedEncodingException {
        byte[] bytesSent = msg.getBytes("US-ASCII");
        return ByteBuffer.wrap(bytesSent);
    }

}
