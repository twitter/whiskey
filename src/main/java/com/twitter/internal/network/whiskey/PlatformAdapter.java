package com.twitter.internal.network.whiskey;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

abstract class PlatformAdapter {

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
