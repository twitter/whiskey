package com.twitter.internal.network.whiskey;

import junit.framework.TestCase;
import org.junit.After;
import org.junit.Before;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;

public final class SocketTest extends TestCase {

    private RunLoop runLoop;
    private EchoServer echoServer;

    @Before
    public void setUp() throws IOException {
        runLoop = new RunLoop();
        runLoop.startThread();

        echoServer = new EchoServer(1234);
        echoServer.start();
    }

    @After
    public void tearDown() throws InterruptedException, IOException {
        echoServer.stop();

        runLoop.stopThread();
    }

    public void testEcho() throws ExecutionException, InterruptedException, IOException {

        Origin origin = new Origin("http", "localhost", 1234);
        Socket s = new Socket(origin, runLoop);
        Socket.ConnectFuture connectFuture = s.connect();

        connectFuture.get();

        byte[] bytesSent = "abcdefghijklmnopqrstuvwxyz".getBytes("US-ASCII");
        ByteBuffer message = ByteBuffer.wrap(bytesSent);
        Socket.WriteFuture writeFuture = s.write(message);
        writeFuture.get();

        Socket.ReadFuture readFuture = s.read();

        byte[] b = new byte[bytesSent.length];
        int nread = 0;

        while (nread < b.length) {
            ByteBuffer bb = readFuture.get();
            bb.flip();
            int remaining = bb.remaining();
            bb.get(b);
            for (int i = 0; i < remaining; i++) {
                assertEquals(b[i], bytesSent[nread++]);
            }
        }
    }

}
