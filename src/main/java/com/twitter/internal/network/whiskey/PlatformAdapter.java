package com.twitter.internal.network.whiskey;

import java.lang.Override;

public abstract class PlatformAdapter {

  private static PlatformAdapter INSTANCE = new JDKPlatformAdapter();

  public static PlatformAdapter get() {
    return INSTANCE;
  }

  abstract public long timestamp();

  private static class JDKPlatformAdapter extends PlatformAdapter {

    @Override
    public long timestamp() {
      return System.currentTimeMillis();
    }
  }
}
