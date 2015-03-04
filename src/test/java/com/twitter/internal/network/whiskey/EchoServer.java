package com.twitter.internal.network.whiskey;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

final class EchoServer {

    private final static int NUM_THREADS = 5;

    private final ServerSocket serverSocket;
    private final ExecutorService executor;

    EchoServer(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        executor = Executors.newFixedThreadPool(NUM_THREADS);
    }

    void start() {
        executor.execute(new AcceptTask());
    }

    void stop() throws IOException {
        executor.shutdownNow();
        serverSocket.close();
    }

    private final class AcceptTask implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {
                    java.net.Socket socket = serverSocket.accept();
                    executor.execute(new EchoTask(socket));
                } catch (IOException | RejectedExecutionException ioe) {
                    break;
                }
            }
        }
    }

    private final class EchoTask implements Runnable {

        private final InputStream in;
        private final OutputStream out;

        EchoTask(java.net.Socket socket) throws IOException {
            this.in = socket.getInputStream();
            this.out = socket.getOutputStream();
        }

        @Override
        public void run() {
            byte[] buf = new byte[4096];
            while (true) {
                try {
                    int nread;
                    while ((nread = in.read(buf)) != -1) {
                        out.write(buf, 0, nread);
                    }
                } catch (IOException ioe) {
                    throw new AssertionError(ioe);
                }
            }
        }
    }
}

