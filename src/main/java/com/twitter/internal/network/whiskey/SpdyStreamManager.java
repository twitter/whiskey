package com.twitter.internal.network.whiskey;

import android.support.annotation.NonNull;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SpdyStreamManager implements Set<SpdyStream> {

    private static final int PRIORITIES = 8;

    private final List<List<SpdyStream>> streamLists = new ArrayList<List<SpdyStream>>() {{
        for (int i = 0; i < PRIORITIES; i++) {
            add(new ArrayList<SpdyStream>());
        }
    }};
    private final Map<Integer, SpdyStream> streamIdMap = new HashMap<>();
    private final Set<SpdyStream> streamSet = new HashSet<>();

    private int localSize = 0;
    private int remoteSize = 0;

    SpdyStreamManager() {
    }

    @Override
    public boolean add(SpdyStream stream) {
        if (!streamSet.add(stream)) {
            return false;
        }

        final int priority = stream.getPriority();
        assert priority >= 0 && priority < PRIORITIES;
        streamLists.get(priority).add(stream);

        final int streamId = stream.getStreamId();
        if (streamId > 0) {
            streamIdMap.put(streamId, stream);
        }

        if (stream.isLocal()) {
            localSize++;
        } else {
            remoteSize++;
        }

        return true;
    }

    @Override
    public boolean addAll(Collection<? extends SpdyStream> collection) {

        boolean streamAdded = false;
        for (SpdyStream stream : collection) {
            if (add(stream)) {
                streamAdded = true;
            }
        }
        return streamAdded;
    }

    @Override
    public void clear() {

        for (List streamList : streamLists) {
            streamList.clear();
        }
        streamIdMap.clear();
        streamSet.clear();
        localSize = 0;
        remoteSize = 0;
    }

    @Override
    public boolean contains(Object object) {
        return streamSet.contains(object);
    }

    @Override
    public boolean containsAll(@NonNull Collection<?> collection) {
        return streamSet.containsAll(collection);
    }

    @Override
    public boolean isEmpty() {
        return localSize == 0 && remoteSize == 0;
    }

    public boolean containsKey(Integer streamId) {
        return streamIdMap.containsKey(streamId);
    }

    public SpdyStream get(Integer streamId) {
        return streamIdMap.get(streamId);
    }

    @NonNull
    @Override
    public Iterator<SpdyStream> iterator() {
        if (localSize > 0 || remoteSize > 0) {
            return new SpdyStreamIterator(streamLists);
        }

        return Collections.<SpdyStream>emptyList().iterator();
    }

    @Override
    public boolean remove(Object object) {

        if (!streamSet.remove(object)) {
            return false;
        }
        SpdyStream stream = (SpdyStream)object;

        final int priority = stream.getPriority();
        assert priority >= 0 && priority < PRIORITIES;
        streamLists.get(priority).remove(stream);

        final int streamId = stream.getStreamId();
        if (streamId > 0) {
            streamIdMap.remove(streamId);
        }

        if (stream.isLocal()) {
            localSize--;
        } else {
            remoteSize--;
        }

        return true;
    }

    @Override
    public boolean removeAll(@NonNull Collection<?> collection) {

        boolean streamRemoved = false;
        for (Object o : collection) {
            if (remove(o)) {
                streamRemoved = true;
            }
        }
        return streamRemoved;
    }

    @Override
    public boolean retainAll(@NonNull Collection<?> collection) {

        boolean streamRemoved = false;
        for (SpdyStream stream : this) {
            if (!collection.contains(stream)) {
                remove(stream);
                streamRemoved = true;
            }
        }
        return streamRemoved;
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

    @NonNull
    @Override
    public Object[] toArray() {

        SpdyStream[] array = new SpdyStream[localSize + remoteSize];

        int i = 0;
        for (SpdyStream stream : this) {
            array[i++] = stream;
        }
        return array;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(@NonNull T[] array) {

        int size = localSize + remoteSize;
        if (array.length < size) {
            array = (T[])Array.newInstance(array.getClass().getComponentType(), size);
        } else if (array.length > size) {
            array[size] = null;
        }

        int i = 0;
        for (SpdyStream stream : this) {
            array[i++] = (T)stream;
        }
        return array;
    }

    private final class SpdyStreamIterator implements Iterator<SpdyStream> {
        private final Iterator<List<SpdyStream>> listIterator;
        private Iterator<SpdyStream> streamIterator;

        SpdyStreamIterator(List<List<SpdyStream>> streamLists) {

            listIterator = streamLists.iterator();
            streamIterator = listIterator.next().iterator();
        }

        @Override
        public boolean hasNext() {

            if (streamIterator.hasNext()) {
                return true;
            }

            while (listIterator.hasNext()) {
                streamIterator = listIterator.next().iterator();
                if (streamIterator.hasNext()) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public SpdyStream next() {

            if (hasNext()) {
                return streamIterator.next();
            }

            return null;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
