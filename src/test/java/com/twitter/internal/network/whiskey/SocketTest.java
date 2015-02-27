package com.twitter.internal.network.whiskey;

import junit.framework.TestCase;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ExecutionException;

public final class SocketTest extends TestCase {

    public void testConnect() throws ExecutionException, InterruptedException, IOException {

        ServerSocket serverSocket = new ServerSocket(1234);

        try {
            RunLoop runLoop = RunLoop.instance();

            Origin origin = new Origin("http", "localhost", 1234);
            Socket s = new Socket(origin, runLoop);
            Socket.ConnectFuture connectFuture = s.connect();

            runLoop.loop();

            serverSocket.accept();

            connectFuture.get();
        } finally {
            serverSocket.close();
        }
    }

}
