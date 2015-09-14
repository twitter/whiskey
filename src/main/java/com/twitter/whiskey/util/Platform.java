/*
 * Copyright (c) 2015 Twitter, Inc. All rights reserved.
 * Licensed under the Apache License v2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.twitter.whiskey.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;

// TODO: use to acquire instances of specific utility objects, e.g. Clock

/**
 * @author Bill Gallagher
 */
public abstract class Platform {

    private static final Platform INSTANCE = establishPlatform();

    public static final Clock CLOCK = new DefaultClock();
    public static final Logger LOGGER = new Logger() {
        @Override public void fatal(String s) {
            INSTANCE.log(Level.SEVERE, s, null);
        }
        @Override public void error(String s) {
            INSTANCE.log(Level.SEVERE, s, null);
        }
        @Override public void warn(String s) {
            INSTANCE.log(Level.WARNING, s, null);
        }
        @Override public void info(String s) {
            INSTANCE.log(Level.INFO, s, null);
        }
        @Override public void debug(String s) {
            INSTANCE.log(Level.FINE, s, null);
        }
        @Override public void trace(String s) {
            INSTANCE.log(Level.FINER, s, null);
        }
        @Override public void exception(String s, Throwable t) {
            INSTANCE.log(Level.WARNING, s, t);
        }
    };

    protected abstract void log(Level level, String message, Throwable throwable);

    public static Platform instance() {
        return INSTANCE;
    }

    abstract public long timestamp();

    private static Platform establishPlatform() {

        try {
            Class.forName("android.app.Application");
            return new Android();
        } catch (ClassNotFoundException e) {
            return new JDKPlatform();
        }
    }

    private static class JDKPlatform extends Platform {

        private final java.util.logging.Logger logger;

        JDKPlatform() {
            logger = java.util.logging.Logger.getLogger("whiskey");
        }

        @Override
        protected void log(Level level, String message, Throwable throwable) {
            logger.log(level, message, throwable);
        }

        @Override
        public long timestamp() {
            return System.nanoTime() / 1000;
        }
    }

    private static class Android extends Platform {

        private Method now;

        Android() {

            try {
                now = Class.forName("android.os.SystemClock").getDeclaredMethod("elapsedRealtime");
                now.setAccessible(true);
            } catch (ClassNotFoundException | NoSuchMethodException | SecurityException e) {
                now = null;
            }
        }

        @Override
        protected void log(Level level, String message, Throwable throwable) {
        }

        @Override
        public long timestamp() {
            if (now != null) {
                try {
                    return (Long) now.invoke(null);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    now = null;
                }
            }

            return System.nanoTime() / 1000;
        }
    }
}
