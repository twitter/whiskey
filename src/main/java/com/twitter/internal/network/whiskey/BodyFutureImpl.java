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

    // TODO: set this
    void setExpectedLength(int expectedLength) {
        this.expectedLength = expectedLength;
    }

    @Override
    void accumulate(ByteBuffer element) {

        if (!element.hasRemaining()) return;
        if (body == null) {
            body = ByteBuffer.allocate(Math.max(expectedLength, element.remaining()));
            System.err.println("allocated " + body.capacity());
        }

        if (body.remaining() < element.remaining()) {
            int required = body.position() + element.remaining();
            // Allocate nearest power of 2 higher than the total required space
            assert(required < Integer.MAX_VALUE >> 1);
            ByteBuffer expanded = ByteBuffer.allocate(Integer.highestOneBit(required) << 1);
            body.flip();
            expanded.put(body);
            expanded.put(element);
            body = expanded;
            System.err.println("grew buffer to " + body.capacity());
        } else {
            body.put(element);
        }
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
        body.flip();
        return set(body);
    }
}
