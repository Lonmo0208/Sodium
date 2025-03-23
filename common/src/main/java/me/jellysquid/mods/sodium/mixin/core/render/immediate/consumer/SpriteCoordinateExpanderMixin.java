package me.jellysquid.mods.sodium.mixin.core.render.immediate.consumer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.caffeinemc.mods.sodium.api.vertex.attributes.CommonVertexAttribute;
import net.caffeinemc.mods.sodium.api.vertex.attributes.common.TextureAttribute;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.api.vertex.format.VertexFormatDescription;
import net.minecraft.client.renderer.SpriteCoordinateExpander;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpriteCoordinateExpander.class)
public class SpriteCoordinateExpanderMixin implements VertexBufferWriter {
    @Shadow @Final private VertexConsumer delegate;
    @Unique private boolean canUseIntrinsics;
    @Unique private float minU, minV, maxU, maxV, w, h;
@Inject(method = "<init>", at = @At("RETURN"))
private void onInit(VertexConsumer delegate, TextureAtlasSprite sprite, CallbackInfo ci) {
    this.minU = sprite.getU0();
    this.minV = sprite.getV0();
    this.maxU = sprite.getU1();
    this.maxV = sprite.getV1();
    this.w = maxU - minU;
    this.h = maxV - minV;
    this.canUseIntrinsics = VertexBufferWriter.tryOf(this.delegate) != null;
}

@Override public boolean canUseIntrinsics() { return this.canUseIntrinsics; }

@Override
public void push(MemoryStack stack, long ptr, int count, VertexFormatDescription format) {
    transform(ptr, count, format, minU, minV, w, h);
    VertexBufferWriter.of(this.delegate).push(stack, ptr, count, format);
}

@Unique
private static void transform(long ptr, int count, VertexFormatDescription format,
                              float minU, float minV, float w, float h) {
    final long stride = format.stride();
    final long offset = format.getElementOffset(CommonVertexAttribute.TEXTURE);
    final long end = ptr + count * stride;

    while (ptr < end) {
        float u = TextureAttribute.getU(ptr + offset);
        float v = TextureAttribute.getV(ptr + offset);
        TextureAttribute.put(ptr + offset, minU + w * u, minV + h * v);
        ptr += stride;
    }
}
}