package com.twitter.internal.network.whiskey;

import java.nio.ByteBuffer;

public interface BodyFuture extends ObservableFuture<ByteBuffer, ByteBuffer> {
}
