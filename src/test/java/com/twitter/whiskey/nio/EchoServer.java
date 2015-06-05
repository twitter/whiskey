/*
 * Copyright (c) 2015 Twitter, Inc. All rights reserved.
 * Licensed under the Apache License v2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.twitter.whiskey.nio;

import com.twitter.whiskey.util.Platform;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * @author Bill Gallagher
 */
class EchoServer {

    private final static int NUM_THREADS = 5;

    private final int port;
    private final ExecutorService executor;

    private ServerSocket serverSocket;
    private List<Task> tasks = new ArrayList<>();

    EchoServer(int port) throws IOException {
        this.port = port;
        this.executor = Executors.newFixedThreadPool(NUM_THREADS);
    }

    void addTask(Task task) {
        synchronized (tasks) {
            tasks.add(task);
        }
    }

    ServerSocket createServerSocket(int port) throws Exception {
        return new ServerSocket(port);
    }

    void start() throws Exception {
        serverSocket = createServerSocket(port);
        executor.execute(new AcceptTask(serverSocket, executor));
    }

    void stop() throws IOException {
        executor.shutdownNow();
        serverSocket.close();
    }

    private final class AcceptTask implements Runnable {

        private final ServerSocket serverSocket;
        private final Executor executor;

        private AcceptTask(ServerSocket serverSocket, Executor executor) {
            this.serverSocket = serverSocket;
            this.executor = executor;
        }

        @Override
        public void run() {
            try {
                final java.net.Socket socket = serverSocket.accept();

                executor.execute(new Runnable() {

                    @Override
                    public void run() {
                        synchronized (tasks) {
                            try {
                                for (EchoServer.Task task : tasks) {
                                    task.execute(serverSocket, socket);
                                }
                            } catch (IOException ioe) {
                                throw new AssertionError(ioe);
                            }
                        }
                    }

                });
            } catch (IOException | RejectedExecutionException ioe) {
                Platform.LOGGER.debug("IOE: " + ioe);
            }
        }
    }

    interface Task {

        public void execute(ServerSocket serverSocket, java.net.Socket socket) throws IOException;

    }

    final static class EchoTask implements Task {

        @Override
        public void execute(ServerSocket serverSocket, java.net.Socket socket) throws IOException {
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

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

