
package me.jellysquid.mods.sodium.mixin.core.render.immediate.consumer;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultedVertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import me.jellysquid.mods.sodium.client.render.vertex.buffer.ExtendedBufferBuilder;
import me.jellysquid.mods.sodium.client.render.vertex.buffer.SodiumBufferBuilder;
import net.caffeinemc.mods.sodium.api.memory.MemoryIntrinsics;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.api.vertex.format.VertexFormatDescription;
import net.caffeinemc.mods.sodium.api.vertex.format.VertexFormatRegistry;
import net.caffeinemc.mods.sodium.api.vertex.serializer.VertexSerializerRegistry;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;

@Mixin(BufferBuilder.class)
public abstract class BufferBuilderMixin extends DefaultedVertexConsumer implements VertexBufferWriter, ExtendedBufferBuilder {
    @Shadow protected abstract void ensureCapacity(int size);
    @Shadow private ByteBuffer buffer;
    @Shadow private int vertices;
    @Shadow private int nextElementByte;
    @Shadow private VertexFormat.Mode mode;
    @Unique private VertexFormatDescription formatDescription;
    @Unique private int vertexStride;
    @Unique private SodiumBufferBuilder SodiumBufferBuilder;
    @Unique private VertexSerializerRegistry serializerRegistry;

    @Inject(
            method = "switchFormat",
            at = @At(
                    value = "FIELD",
                    target = "Lcom/mojang/blaze3d/vertex/BufferBuilder;format:Lcom/mojang/blaze3d/vertex/VertexFormat;",
                    opcode = Opcodes.PUTFIELD
            )
    )
    private void onFormatChanged(VertexFormat format, CallbackInfo ci) {
        this.formatDescription = VertexFormatRegistry.instance().get(format);
        this.vertexStride = this.formatDescription.stride();
        this.SodiumBufferBuilder = this.formatDescription.isSimpleFormat() ? new SodiumBufferBuilder(this) : null;
        this.serializerRegistry = VertexSerializerRegistry.instance();
    }

    @Inject(method = { "discard", "reset", "begin" }, at = @At("RETURN"))
    private void resetDelegate(CallbackInfo ci) {
        if (this.SodiumBufferBuilder != null) this.SodiumBufferBuilder.reset();
    }

    @Override public ByteBuffer sodium$getBuffer() { return this.buffer; }
    @Override public int sodium$getElementOffset() { return this.nextElementByte; }
    @Override public VertexFormatDescription sodium$getFormatDescription() { return this.formatDescription; }
    @Override public SodiumBufferBuilder sodium$getDelegate() { return this.SodiumBufferBuilder; }

    @Override
    public void sodium$moveToNextVertex() {
        this.vertices++;
        this.nextElementByte += this.vertexStride;
        this.ensureCapacity(this.vertexStride);
        if (this.shouldDuplicateVertices()) duplicateVertex();
    }

    @Override public boolean sodium$hasDefaultColor() { return this.defaultColorSet; }

    @Unique private boolean shouldDuplicateVertices() {
        return this.mode == VertexFormat.Mode.LINES || this.mode == VertexFormat.Mode.LINE_STRIP;
    }

    @Unique private void duplicateVertex() {
        long srcAddr = MemoryUtil.memAddress(this.buffer, this.nextElementByte - this.vertexStride);
        long dstAddr = MemoryUtil.memAddress(this.buffer, this.nextElementByte);
        MemoryIntrinsics.copyMemory(srcAddr, dstAddr, this.vertexStride);
        this.nextElementByte += this.vertexStride;
        this.vertices++;
        this.ensureCapacity(this.vertexStride);
    }

    @Override public boolean canUseIntrinsics() {
        return this.formatDescription != null && this.formatDescription.isSimpleFormat();
    }

    @Override
    public void push(MemoryStack stack, long src, int count, VertexFormatDescription format) {
        int length = count * this.vertexStride;
        this.ensureCapacity(length + this.vertexStride);
        long dst = MemoryUtil.memAddress(this.buffer, this.nextElementByte);

        if (format == this.formatDescription) {
            MemoryIntrinsics.copyMemory(src, dst, length);
        } else {
            this.serializerRegistry.get(format, this.formatDescription).serialize(src, dst, count);
        }

        this.vertices += count;
        this.nextElementByte += length;
    }
}