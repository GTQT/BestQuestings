package betterquesting.api2.storage;

import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class SimpleDatabase<T> extends AbstractDatabase<T> {

    private final LongSet idMap = new LongSet() {
        @Override
        public int size() {
            return 0;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public boolean contains(Object o) {
            return false;
        }

        @Override
        public LongIterator iterator() {
            return null;
        }

        @NotNull
        @Override
        public Object[] toArray() {
            return new Object[0];
        }

        @Override
        public LongIterator longIterator() {
            return null;
        }

        @Override
        public <T> T[] toArray(T[] a) {
            return null;
        }

        @Override
        public boolean add(Long aLong) {
            return false;
        }

        @Override
        public boolean remove(Object o) {
            return false;
        }

        @Override
        public boolean containsAll(@NotNull Collection<?> c) {
            return false;
        }

        @Override
        public boolean addAll(@NotNull Collection<? extends Long> c) {
            return false;
        }

        @Override
        public boolean removeAll(@NotNull Collection<?> c) {
            return false;
        }

        @Override
        public boolean retainAll(@NotNull Collection<?> c) {
            return false;
        }

        @Override
        public void clear() {

        }

        @Override
        public boolean equals(Object o) {
            return false;
        }

        @Override
        public int hashCode() {
            return 0;
        }

        @Override
        public boolean contains(long key) {
            return false;
        }

        @Override
        public long[] toLongArray() {
            return new long[0];
        }

        @Override
        public long[] toLongArray(long[] a) {
            return new long[0];
        }

        @Override
        public long[] toArray(long[] a) {
            return new long[0];
        }

        @Override
        public boolean add(long key) {
            return false;
        }

        @Override
        public boolean rem(long key) {
            return false;
        }

        @Override
        public boolean addAll(LongCollection c) {
            return false;
        }

        @Override
        public boolean containsAll(LongCollection c) {
            return false;
        }

        @Override
        public boolean removeAll(LongCollection c) {
            return false;
        }

        @Override
        public boolean retainAll(LongCollection c) {
            return false;
        }

        @Override
        public boolean remove(long k) {
            return false;
        }
    };

    @Override
    public synchronized int nextID() {
        int id = 0;
        while (idMap.contains(id)) {
            id++;
        }
        return id;
    }

    @Override
    public synchronized DBEntry<T> add(int id, T value) {
        DBEntry<T> result = super.add(id, value);
        // Don't add when an exception is thrown
        idMap.add(id);
        return result;
    }

    @Override
    public synchronized boolean removeID(int key) {
        boolean result = super.removeID(key);
        if (result) idMap.remove(key);
        return result;
    }

    @Override
    public synchronized void reset() {
        super.reset();
        idMap.clear();
    }

}
