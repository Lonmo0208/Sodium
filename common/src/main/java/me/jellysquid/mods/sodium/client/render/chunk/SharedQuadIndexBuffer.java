package me.jellysquid.mods.sodium.client.render.chunk;

import me.jellysquid.mods.sodium.client.gl.buffer.GlBuffer;
import me.jellysquid.mods.sodium.client.gl.buffer.GlBufferMapFlags;
import me.jellysquid.mods.sodium.client.gl.buffer.GlBufferUsage;
import me.jellysquid.mods.sodium.client.gl.buffer.GlMutableBuffer;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlIndexType;
import me.jellysquid.mods.sodium.client.gl.util.EnumBitField;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.EnumMap;

public class SharedQuadIndexBuffer {
    private static final int ELEMENTS_PER_PRIMITIVE = 6;
    private static final int VERTICES_PER_PRIMITIVE = 4;

    private final GlMutableBuffer buffer;
    private final IndexType indexType;

    private int maxPrimitives;

    public SharedQuadIndexBuffer(CommandList commandList, IndexType indexType) {
        this.buffer = commandList.createMutableBuffer();
        this.indexType = indexType;
    }

    public void ensureCapacity(CommandList commandList, int elementCount) {
        if (elementCount > this.indexType.getMaxElementCount()) {
            throw new IllegalArgumentException("Tried to reserve storage for more vertices in this buffer than it can hold");
        }

        int primitiveCount = elementCount / ELEMENTS_PER_PRIMITIVE;

        if (primitiveCount > this.maxPrimitives) {
            this.grow(commandList, this.getNextSize(primitiveCount));
        }
    }

    private int getNextSize(int primitiveCount) {
        return Math.min(Math.max(this.maxPrimitives * 2, primitiveCount + 16384), this.indexType.getMaxPrimitiveCount());
    }

    private void grow(CommandList commandList, int primitiveCount) {
        var bufferSize = primitiveCount * this.indexType.getBytesPerElement() * ELEMENTS_PER_PRIMITIVE;

        commandList.allocateStorage(this.buffer, bufferSize, GlBufferUsage.STATIC_DRAW);

        var mapped = commandList.mapBuffer(this.buffer, 0, bufferSize, EnumBitField.of(GlBufferMapFlags.INVALIDATE_BUFFER, GlBufferMapFlags.WRITE, GlBufferMapFlags.UNSYNCHRONIZED));
        this.indexType.createIndexBuffer(mapped.getMemoryBuffer(), primitiveCount);

        commandList.unmap(mapped);

        this.maxPrimitives = primitiveCount;
    }

    public GlBuffer getBufferObject() {
        return this.buffer;
    }

    public void delete(CommandList commandList) {
        commandList.deleteBuffer(this.buffer);
    }

    public GlIndexType getIndexFormat() {
        return this.indexType.getFormat();
    }

    public IndexType getIndexType() {
        return this.indexType;
    }

    public enum IndexType {
        SHORT(GlIndexType.UNSIGNED_SHORT, 64 * 1024) {
            @Override
            public void createIndexBuffer(ByteBuffer byteBuffer, int primitiveCount) {
                byteBuffer.order(ByteOrder.nativeOrder());
                for (int primitiveIndex = 0; primitiveIndex < primitiveCount; primitiveIndex++) {
                    int indexOffset = primitiveIndex * ELEMENTS_PER_PRIMITIVE * 2;
                    int vertexOffset = primitiveIndex * VERTICES_PER_PRIMITIVE;

                    byteBuffer.putShort(indexOffset, (short) (vertexOffset + 0));
                    byteBuffer.putShort(indexOffset + 2, (short) (vertexOffset + 1));
                    byteBuffer.putShort(indexOffset + 4, (short) (vertexOffset + 2));

                    byteBuffer.putShort(indexOffset + 6, (short) (vertexOffset + 2));
                    byteBuffer.putShort(indexOffset + 8, (short) (vertexOffset + 3));
                    byteBuffer.putShort(indexOffset + 10, (short) (vertexOffset + 0));
                }
            }
        },
        INTEGER(GlIndexType.UNSIGNED_INT, Integer.MAX_VALUE) {
            @Override
            public void createIndexBuffer(ByteBuffer byteBuffer, int primitiveCount) {
                byteBuffer.order(ByteOrder.nativeOrder());
                for (int primitiveIndex = 0; primitiveIndex < primitiveCount; primitiveIndex++) {
                    int indexOffset = primitiveIndex * ELEMENTS_PER_PRIMITIVE * 4;
                    int vertexOffset = primitiveIndex * VERTICES_PER_PRIMITIVE;

                    byteBuffer.putInt(indexOffset, vertexOffset + 0);
                    byteBuffer.putInt(indexOffset + 4, vertexOffset + 1);
                    byteBuffer.putInt(indexOffset + 8, vertexOffset + 2);

                    byteBuffer.putInt(indexOffset + 12, vertexOffset + 2);
                    byteBuffer.putInt(indexOffset + 16, vertexOffset + 3);
                    byteBuffer.putInt(indexOffset + 20, vertexOffset + 0);
                }
            }
        };

        private static final EnumMap<IndexType, IndexType> VALUES = new EnumMap<>(IndexType.class);

        static {
            for (IndexType type : IndexType.values()) {
                VALUES.put(type, type);
            }
        }

        private final GlIndexType format;
        private final int maxElementCount;

        IndexType(GlIndexType format, int maxElementCount) {
            this.format = format;
            this.maxElementCount = maxElementCount;
        }

        public abstract void createIndexBuffer(ByteBuffer buffer, int primitiveCount);

        public int getBytesPerElement() {
            return this.format.getStride();
        }

        public GlIndexType getFormat() {
            return this.format;
        }

        public int getMaxPrimitiveCount() {
            return this.maxElementCount / 4;
        }

        public int getMaxElementCount() {
            return this.maxElementCount;
        }
    }
}