/*
 * Copyright (c) 2015 Twitter, Inc. All rights reserved.
 * Licensed under the Apache License v2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.twitter.whiskey.nio;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public final class RunLoopTest {

    private RunLoop runLoop;
    private TestClock clock;

    class Counter {
        volatile int count = 0;
    }

    @Before
    public void setup() {
        clock = new TestClock();
        runLoop = new RunLoop(clock);
    }

    @After
    public void tearDown() throws InterruptedException {
        runLoop.stopThread();
    }

    @Test
    public void testExecute() throws InterruptedException {

        final CountDownLatch latch = new CountDownLatch(1);
        runLoop.startThread();

        runLoop.execute(
            new Runnable() {
                public void run() {
                    latch.countDown();
                }
            }
        );

        Assert.assertTrue(latch.await(10, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testSchedule_whileRunning() throws InterruptedException {

        final CountDownLatch latch = new CountDownLatch(2);
        final Thread current = Thread.currentThread();
        runLoop.startThread();

        runLoop.schedule(
            new Runnable() {
                @Override
                public void run() {
                    latch.countDown();
                    LockSupport.unpark(current);
                }
            }, 100, TimeUnit.MILLISECONDS
        );
        runLoop.schedule(
            new Runnable() {
                @Override
                public void run() {
                    latch.countDown();
                    LockSupport.unpark(current);
                }
            }, 200, TimeUnit.MILLISECONDS
        );

        clock.tick(100, TimeUnit.MILLISECONDS);
        runLoop.wake();
        LockSupport.park();

        clock.tick(50, TimeUnit.MILLISECONDS);
        runLoop.wake();
        clock.tick(50, TimeUnit.MILLISECONDS);
        runLoop.wake();
        LockSupport.park();

        Assert.assertTrue(latch.await(10, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testSchedule_manually() throws InterruptedException {

        final CountDownLatch latch = new CountDownLatch(2);

        runLoop.schedule(
            new Runnable() {
                @Override
                public void run() {
                    latch.countDown();
                }
            }, 100, TimeUnit.MILLISECONDS
        );
        runLoop.schedule(
            new Runnable() {
                @Override
                public void run() {
                    latch.countDown();
                }
            }, 200, TimeUnit.MILLISECONDS
        );

        runLoop.run(false);
        clock.tick(100, TimeUnit.MILLISECONDS);
        runLoop.run(false);
        clock.tick(50, TimeUnit.MILLISECONDS);
        runLoop.run(false);
        clock.tick(50, TimeUnit.MILLISECONDS);
        runLoop.run(false);

        Assert.assertTrue(latch.await(10, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testScheduled_withInterleavedDelays() throws InterruptedException {

        final CountDownLatch latch = new CountDownLatch(3);
        final Thread current = Thread.currentThread();
        runLoop.startThread();

        runLoop.schedule(
            new Runnable() {
                @Override
                public void run() {
                    latch.countDown();
                    LockSupport.unpark(current);
                }
            }, 200, TimeUnit.MILLISECONDS
        );
        runLoop.schedule(
            new Runnable() {
                @Override
                public void run() {
                    latch.countDown();
                    LockSupport.unpark(current);
                }
            }, 100, TimeUnit.MILLISECONDS
        );

        clock.tick(100, TimeUnit.MILLISECONDS);
        runLoop.wake();
        LockSupport.park();

        runLoop.schedule(
            new Runnable() {
                @Override
                public void run() {
                    latch.countDown();
                    LockSupport.unpark(current);
                }
            }, 50, TimeUnit.MILLISECONDS
        );

        clock.tick(50, TimeUnit.MILLISECONDS);
        runLoop.wake();
        LockSupport.park();
        clock.tick(50, TimeUnit.MILLISECONDS);
        runLoop.wake();
        LockSupport.park();

        Assert.assertTrue(latch.await(10, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testSchedule_withSetTolerance() throws InterruptedException {

        final Counter events = new Counter();

        runLoop.schedule(
            new Runnable() {
                @Override
                public void run() {
                    events.count++;
                }
            }, 100, 10, TimeUnit.MILLISECONDS
        );

        // Event should be missed
        runLoop.run(false);
        clock.tick(200, TimeUnit.MILLISECONDS);
        runLoop.run(false);

        runLoop.schedule(
            new Runnable() {
                @Override
                public void run() {
                    events.count++;
                }
            }, 100, 10, TimeUnit.MILLISECONDS
        );

        // Event should be missed again
        runLoop.run(false);
        clock.tick(110, TimeUnit.MILLISECONDS);
        runLoop.run(false);

        runLoop.schedule(
            new Runnable() {
                @Override
                public void run() {
                    events.count++;
                }
            }, 100, 10, TimeUnit.MILLISECONDS
        );

        // Event should fire
        runLoop.run(false);
        clock.tick(109, TimeUnit.MILLISECONDS);
        runLoop.run(false);

        Assert.assertEquals(1, events.count);
    }

    @Test
    public void testSchedule_withUnlimitedTolerance() throws InterruptedException {

        final Counter events = new Counter();

        runLoop.schedule(
            new Runnable() {
                @Override
                public void run() {
                    events.count++;
                }
            }, 100, 0, TimeUnit.MILLISECONDS
        );

        // Event should never be missed
        runLoop.run(false);
        clock.tick(Integer.MAX_VALUE, TimeUnit.MILLISECONDS);
        runLoop.run(false);

        Assert.assertEquals(1, events.count);
    }
}
