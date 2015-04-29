package com.twitter.whiskey.net;

import com.twitter.whiskey.futures.ObservableFuture;

import java.nio.ByteBuffer;

public interface BodyFuture extends ObservableFuture<ByteBuffer, ByteBuffer> {
}
