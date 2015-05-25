/*
 * Copyright (c) 2015 Twitter, Inc. All rights reserved.
 * Licensed under the Apache License v2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.twitter.whiskey.net;

import android.support.annotation.NonNull;

import com.twitter.whiskey.util.LinkedHashDeque;

import java.util.AbstractCollection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import static com.twitter.whiskey.net.SpdyConstants.PRIORITY_LEVELS;

/**
 * @author Michael Schore
 */
final class SpdyStreamManager extends AbstractCollection<SpdyStream> implements Set<SpdyStream> {

    @SuppressWarnings("unchecked")
    private static final LinkedHashDeque<SpdyStream>[] streamSets = new LinkedHashDeque[PRIORITY_LEVELS];
    static {
        for (int i = 0; i < PRIORITY_LEVELS; i++) {
            streamSets[i] = new LinkedHashDeque<>();
        }
    }

    private final Map<Integer, SpdyStream> streamMap = new HashMap<>();

    private volatile int mutations = 0;
    private int localSize = 0;
    private int remoteSize = 0;

    SpdyStreamManager() {
    }

    @Override
    public boolean add(SpdyStream stream) {

        final int priority = stream.getPriority();
        assert priority >= 0 && priority < PRIORITY_LEVELS;
        streamSets[priority].add(stream);

        final int streamId = stream.getStreamId();
        assert streamId > 0;
        streamMap.put(streamId, stream);

        if (stream.isLocal()) {
            localSize++;
        } else {
            remoteSize++;
        }

        mutations++;
        return true;
    }

    @Override
    public void clear() {

        for (LinkedHashDeque<SpdyStream> streamSet : streamSets) {
            streamSet.clear();
        }
        streamMap.clear();
        mutations = 0;
        localSize = 0;
        remoteSize = 0;
    }

    @Override
    public boolean contains(Object object) {
        return streamMap.containsKey(((SpdyStream) object).getStreamId());
    }

    @Override
    public boolean isEmpty() {
        return localSize == 0 && remoteSize == 0;
    }

    public SpdyStream get(Integer streamId) {
        return streamMap.get(streamId);
    }

    @NonNull
    @Override
    public Iterator<SpdyStream> iterator() {

        if (localSize > 0 || remoteSize > 0) {
            return new SpdyStreamIterator();
        }

        return Collections.<SpdyStream>emptyList().iterator();
    }

    @Override
    public boolean remove(Object object) {

        final SpdyStream stream = (SpdyStream)object;
        if (!streamSets[stream.getPriority()].remove(stream)) {
            return false;
        }

        final int streamId = stream.getStreamId();
        if (streamId > 0) {
            streamMap.remove(streamId);
        }

        if (stream.isLocal()) {
            localSize--;
        } else {
            remoteSize--;
        }

        mutations++;
        return true;
    }

    @Override
    public int size() {
        return localSize + remoteSize;
    }

    public int getLocalSize() {
        return localSize;
    }

    public int getRemoteSize() {
        return remoteSize;
    }

    private final class SpdyStreamIterator implements Iterator<SpdyStream> {
        private Iterator<SpdyStream> streamIterator;
        private SpdyStream removeable;
        private int setIndex;
        private int sentinel;

        SpdyStreamIterator() {

            sentinel = mutations;
            setIndex = 0;
            streamIterator = streamSets[setIndex].iterator();
        }

        @Override
        public boolean hasNext() {

            if (sentinel != mutations) throw new ConcurrentModificationException();
            if (streamIterator.hasNext()) return true;

            while (++setIndex < PRIORITY_LEVELS) {
                streamIterator = streamSets[setIndex].iterator();
                if (streamIterator.hasNext()) return true;
            }

            return false;
        }

        @Override
        public SpdyStream next() {

            if (hasNext()) {
                removeable = streamIterator.next();
                return removeable;
            }

            throw new NoSuchElementException();
        }

        @Override
        public void remove() {

            if (sentinel != mutations) throw new ConcurrentModificationException();
            if (removeable == null) throw new IllegalStateException();

            streamIterator.remove();
            final int streamId = removeable.getStreamId();
            if (streamId > 0) {
                streamMap.remove(streamId);
            }

            if (removeable.isLocal()) {
                localSize--;
            } else {
                remoteSize--;
            }

            removeable = null;
        }
    }
}
