package com.twitter.internal.network.whiskey;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Michael Schore
 */
class BodyFutureImpl extends ReactiveFuture<ByteBuffer, ByteBuffer> implements BodyFuture {

    private ByteBuffer body;
    private LinkedList<Integer> boundaries = new LinkedList<>();
    private int expectedLength = 0;

    void setExpectedLength(int expectedLength) {
        this.expectedLength = expectedLength;
    }

    @Override
    void accumulate(ByteBuffer element) {

        if (element.limit() == 0) return;
        if (body == null) {
            body = ByteBuffer.allocate(expectedLength);
        }
        body.put(element);
        boundaries.add(body.position());
    }

    @Override
    Iterable<ByteBuffer> drain() {

        List<ByteBuffer> chunks = new ArrayList<>(boundaries.size());
        body.flip();
        for (int limit : boundaries) {
            body.limit(limit);
            chunks.add(body.slice().asReadOnlyBuffer());
            body.position(limit);
        }
        body = null;
        return chunks;
    }

    @Override
    boolean complete() {
        return set(body);
    }
}
