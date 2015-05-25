/*
 * Copyright (c) 2015 Twitter, Inc. All rights reserved.
 * Licensed under the Apache License v2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.twitter.whiskey.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

// TODO: use to acquire instances of specific utility objects, e.g. Clock

/**
 * @author Bill Gallagher
 */
public abstract class PlatformAdapter {

    private static final PlatformAdapter INSTANCE = establishPlatform();

    public static PlatformAdapter instance() {
        return INSTANCE;
    }

    abstract public long timestamp();

    private static PlatformAdapter establishPlatform() {

        try {
            Class.forName("android.app.Application");
            return new AndroidAdapter();
        } catch (ClassNotFoundException e) {
            return new JDKPlatformAdapter();
        }
    }

    private static class JDKPlatformAdapter extends PlatformAdapter {

        JDKPlatformAdapter() {
        }

        @Override
        public long timestamp() {
            return System.nanoTime() / 1000;
        }
    }

    private static class AndroidAdapter extends PlatformAdapter {

        private Method now;

        AndroidAdapter() {

            try {
                now = Class.forName("android.os.SystemClock").getDeclaredMethod("elapsedRealtime");
                now.setAccessible(true);
            } catch (ClassNotFoundException | NoSuchMethodException | SecurityException e) {
                now = null;
            }
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
