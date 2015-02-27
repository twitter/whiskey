package com.twitter.internal.network.whiskey;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class RunLoopTest {

    @Test
    public void testExecute() throws InterruptedException {

        RunLoop runLoop = RunLoop.instance();
        runLoop.startThread();

        final CountDownLatch latch = new CountDownLatch(1);

        runLoop.execute(
            new Runnable() {
                public void run() {
                    latch.countDown();
                }
            }
        );

        latch.await(1, TimeUnit.SECONDS);
    }

}
