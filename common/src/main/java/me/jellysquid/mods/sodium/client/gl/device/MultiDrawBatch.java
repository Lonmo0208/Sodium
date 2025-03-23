package me.jellysquid.mods.sodium.client.gl.device;

import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Pointer;

public final class MultiDrawBatch {
    public final long pElementPointer;
    public final long pElementCount;
    public final long pBaseVertex;

    private final int capacity;
    public int size;

    public MultiDrawBatch(int capacity) {
        this.pElementPointer = MemoryUtil.nmemAlignedAlloc(32, (long) capacity * Pointer.POINTER_SIZE);
        MemoryUtil.memSet(this.pElementPointer, 0x0, (long) capacity * Pointer.POINTER_SIZE);
        this.pElementCount = MemoryUtil.nmemAlignedAlloc(32, (long) capacity * Integer.BYTES);
        this.pBaseVertex = MemoryUtil.nmemAlignedAlloc(32, (long) capacity * Integer.BYTES);
        this.capacity = capacity;
    }

    public int size() {
        return this.size;
    }

    public int capacity() {
        return this.capacity;
    }

    public void clear() {
        this.size = 0;
    }

    public void delete() {
        MemoryUtil.nmemAlignedFree(this.pElementPointer);
        MemoryUtil.nmemAlignedFree(this.pElementCount);
        MemoryUtil.nmemAlignedFree(this.pBaseVertex);
    }

    public boolean isEmpty() {
        return this.size <= 0;
    }

    public int getIndexBufferSize() {
        int elements = 0;
        for (int i = 0; i < this.size; i++) {
            elements = Math.max(elements, MemoryUtil.memGetInt(this.pElementCount + ((long) i * Integer.BYTES)));
        }
        return elements;
    }

    public void addDraw(int elementCount, int baseVertex) {
        MemoryUtil.memPutInt(this.pElementCount + ((long) this.size * Integer.BYTES), elementCount);
        MemoryUtil.memPutInt(this.pBaseVertex + ((long) this.size * Integer.BYTES), baseVertex);
        this.size++;
    }
}