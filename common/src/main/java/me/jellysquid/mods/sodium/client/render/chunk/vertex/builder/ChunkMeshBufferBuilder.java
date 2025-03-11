package me.jellysquid.mods.sodium.client.render.chunk.vertex.builder;

import me.jellysquid.mods.sodium.client.render.chunk.terrain.material.Material;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public class ChunkMeshBufferBuilder {
    private final ChunkVertexEncoder encoder;
    private final int stride;

    private final int initialCapacity;

    private ByteBuffer buffer;
    private int count;
    private int capacity;
    private int sectionIndex;

    public ChunkMeshBufferBuilder(ChunkVertexType vertexType, int initialCapacity) {
        this.encoder = vertexType.getEncoder();
        this.stride = vertexType.getVertexFormat().getStride();

        this.buffer = null;

        this.capacity = initialCapacity;
        this.initialCapacity = initialCapacity;
    }

    public void push(ChunkVertexEncoder.Vertex[] vertices, Material material) {
        int vertexCount = vertices.length;

        this.ensureCapacity(vertexCount);

        long ptr = MemoryUtil.memAddress(this.buffer, this.count * this.stride);

        ptr = this.encoder.write(ptr, material, vertices, this.sectionIndex);

        this.count += vertexCount;
    }

    private void ensureCapacity(int required) {
        if (this.count + required > this.capacity) {
            this.grow(required);
        }
    }

    private void grow(int required) {
        int newCapacity = Math.max(this.capacity * 2, this.capacity + required);
        this.setBufferSize(newCapacity);
    }

    private void setBufferSize(int newCapacity) {
        this.buffer = MemoryUtil.memRealloc(this.buffer, newCapacity * this.stride);
        this.capacity = newCapacity;
    }

    public void start(int sectionIndex) {
        this.count = 0;
        this.sectionIndex = sectionIndex;

        this.setBufferSize(this.initialCapacity);
    }

    public void destroy() {
        if (this.buffer != null) {
            MemoryUtil.memFree(this.buffer);
        }

        this.buffer = null;
    }

    public boolean isEmpty() {
        return this.count == 0;
    }

    public ByteBuffer slice() {
        if (this.isEmpty()) {
            throw new IllegalStateException("No vertex data in buffer");
        }

        return MemoryUtil.memSlice(this.buffer, 0, this.stride * this.count);
    }

    public int count() {
        return this.count;
    }
}