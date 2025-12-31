package net.caffeinemc.mods.sodium.client.gl.arena;

import java.util.Map;
import java.util.TreeMap;

/**
 * Stores a set of sized objects that typically don't collide but may.
 *
 * @param <V> the type of sized object stored in the map
 */
public class SizedTreeMap<V extends SizedTreeMap.Sized> extends TreeMap<Long, V> {
    private Map.Entry<Long, V> cachedHighestEntry = null;

    public SizedTreeMap() {
        super(Long::compareUnsigned);
    }

    interface Sized {
        long getSize();
        long getIdentifier();

        default long makeKey() {
            return (getSize() << 32) | (getIdentifier() & 0xFFFFFFFFL);
        }
    }

    public void addSized(V value) {
        var key = value.makeKey();
        super.put(key, value);
        if (this.cachedHighestEntry != null && Long.compareUnsigned(key, this.cachedHighestEntry.getKey()) > 0) {
            this.cachedHighestEntry = null;
        }
    }

    public V removeSized(V value) {
        var removed = super.remove(value.makeKey());
        if (this.cachedHighestEntry != null && this.cachedHighestEntry.getValue() == removed) {
            this.cachedHighestEntry = null;
        }
        return removed;
    }

    public V getFirstFitting(long requiredSize) {
        var tailMap = this.tailMap(requiredSize << 32);
        return tailMap.isEmpty() ? null : tailMap.firstEntry().getValue();
    }

    public V removeFirstFitting(long requiredSize) {
        var tailMap = this.tailMap(requiredSize << 32);
        if (tailMap.isEmpty()) {
            return null;
        }
        var removed = tailMap.pollFirstEntry().getValue();
        if (this.cachedHighestEntry != null && this.cachedHighestEntry.getValue() == removed) {
            this.cachedHighestEntry = null;
        }
        return removed;
    }

    public Map.Entry<Long, V> getHighestEntry() {
        if (this.cachedHighestEntry == null && !this.isEmpty()) {
            this.cachedHighestEntry = this.lastEntry();
        }
        return this.cachedHighestEntry;
    }

    public V getHighestFitting(long requiredSize) {
        var entry = this.getHighestEntry();
        if (entry != null) {
            long key = entry.getKey();
            long size = key >>> 32;
            if (Long.compareUnsigned(size, requiredSize) >= 0) {
                return entry.getValue();
            }
        }
        return null;
    }

    public V getHighest() {
        var entry = this.getHighestEntry();
        return entry != null ? entry.getValue() : null;
    }

    public long getHighestSize() {
        var entry = this.getHighestEntry();
        return entry != null ? entry.getKey() >>> 32 : 0;
    }
}
